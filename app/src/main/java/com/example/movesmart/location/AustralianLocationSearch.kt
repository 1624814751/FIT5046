package com.example.movesmart.location

import com.example.movesmart.shared.GeoLocation
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

internal val australianStates = mapOf("ACT" to "Australian Capital Territory", "NSW" to "New South Wales",
    "NT" to "Northern Territory", "QLD" to "Queensland", "SA" to "South Australia",
    "TAS" to "Tasmania", "VIC" to "Victoria", "WA" to "Western Australia")

internal fun stateCode(value: String?): String? = australianStates.entries.firstOrNull {
    it.key.equals(value?.trim(), true) || it.value.equals(value?.trim(), true)
}?.key

internal data class AustralianQuery(val term: String, val state: String?, val postcode: Boolean)

internal fun parseAustralianQuery(input: String): AustralianQuery {
    val clean = input.trim().replace(Regex("\\s+"), " ")
    require(clean.length in 2..100) { "Enter at least two letters or a four-digit Australian postcode." }
    val suffix = (australianStates.keys + australianStates.values).sortedByDescending { it.length }
        .firstOrNull { clean.endsWith(" $it", true) || clean.endsWith(",$it", true) }
    val term = if (suffix != null) clean.dropLast(suffix.length).trim().trimEnd(',').trim() else clean
    require(term.length >= 2 && !term.contains(',')) { "Enter a suburb, postcode, or suburb and Australian state (for example Clayton VIC)." }
    val postcode = term.all { it.isDigit() }
    require(!postcode || term.matches(Regex("[0-9]{4}"))) { "Australian postcodes contain four digits." }
    return AustralianQuery(term, stateCode(suffix), postcode)
}

data class GeocodingResponse(val results: List<GeocodingPlace>? = null)
data class GeocodingPlace(
    val name: String? = null, val latitude: Double? = null, val longitude: Double? = null,
    @SerializedName("country_code") val countryCode: String? = null,
    val country: String? = null, val admin1: String? = null, val postcodes: List<String>? = null,
    @SerializedName("feature_code") val featureCode: String? = null
)
data class PostcodeResponse(
    @SerializedName("post code") val postcode: String? = null,
    @SerializedName("country abbreviation") val countryCode: String? = null,
    val country: String? = null, val places: List<PostcodePlace>? = null
)
data class PostcodePlace(
    @SerializedName("place name") val name: String? = null,
    val latitude: String? = null, val longitude: String? = null, val state: String? = null,
    @SerializedName("state abbreviation") val stateAbbreviation: String? = null
)

interface GeocodingApi {
    @GET("v1/search") suspend fun search(@Query("name") name: String, @Query("countryCode") country: String = "AU",
        @Query("count") count: Int = 100, @Query("language") language: String = "en"): GeocodingResponse
}
interface PostcodeApi { @GET("au/{postcode}") suspend fun search(@Path("postcode") postcode: String): PostcodeResponse }

/** Open-Meteo lacks Australian postcode coverage; postcode lookups use Zippopotam.us/GeoNames. */
class AustralianLocationSearch(private val suburbs: GeocodingApi, private val postcodes: PostcodeApi) : LocationSearch {
    override suspend fun search(input: String): List<LocationMatch> {
        try {
            val query = parseAustralianQuery(input)
            val matches = if (query.postcode) {
                val response = try { postcodes.search(query.term) } catch (error: HttpException) {
                    if (error.code() == 404) return emptyList() else throw error
                }
                require(response.countryCode == "AU" && response.postcode == query.term &&
                    (response.country == null || response.country == "Australia")) { "The postcode service returned an invalid country or postcode." }
                response.places.orEmpty().mapNotNull { place ->
                    val state = stateCode(place.state) ?: return@mapNotNull null
                    if (place.stateAbbreviation != null && stateCode(place.stateAbbreviation) != state) return@mapNotNull null
                    if (query.state != null && query.state != state) return@mapNotNull null
                    match(place.name, state, listOf(query.term), place.latitude?.toDoubleOrNull(),
                        place.longitude?.toDoubleOrNull(), "Zippopotam.us / GeoNames")
                }
            } else {
                val name = query.term + (query.state?.let { ", ${australianStates.getValue(it)}" } ?: "")
                suburbs.search(name).results.orEmpty().mapNotNull { place ->
                    if (place.countryCode != "AU" || (place.country != null && place.country != "Australia")) return@mapNotNull null
                    val state = stateCode(place.admin1) ?: return@mapNotNull null
                    if (query.state != null && query.state != state) return@mapNotNull null
                    if (place.featureCode?.startsWith("PPL") != true) return@mapNotNull null
                    if (place.name?.startsWith(query.term, true) != true) return@mapNotNull null
                    match(place.name, state, place.postcodes.orEmpty(), place.latitude, place.longitude, "Open-Meteo / GeoNames")
                }
            }
            return matches.distinctBy { it.location }.sortedBy { it.location.label }
        } catch (error: CancellationException) { throw error
        } catch (error: SocketTimeoutException) {
            throw LocationFailure(LocationProblem.TIMEOUT, "Location search timed out. Please retry.")
        } catch (error: IOException) {
            throw LocationFailure(LocationProblem.NETWORK, "Cannot reach the location service. Check your connection and retry.")
        } catch (error: HttpException) {
            throw LocationFailure(LocationProblem.SERVICE, "Location service error (${error.code()}). Please retry later.")
        } catch (error: IllegalArgumentException) {
            throw LocationFailure(LocationProblem.INVALID_INPUT, error.message ?: "Invalid location search.")
        }
    }

    private fun match(name: String?, state: String, postcodes: List<String>, lat: Double?, lon: Double?, source: String): LocationMatch? {
        if (name.isNullOrBlank() || name.none { it.isLetter() } || lat == null || lon == null ||
            postcodes.any { !it.matches(Regex("[0-9]{4}")) }) return null
        val label = "${name.trim()}, $state" + if (postcodes.isEmpty()) "" else " ${postcodes.joinToString(" / ")}"
        val location = GeoLocation(label, lat, lon)
        return if (location.hasValidCoordinates()) LocationMatch(location, source) else null
    }

    companion object {
        fun create(): AustralianLocationSearch {
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
            fun retrofit(url: String) = Retrofit.Builder().baseUrl(url).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build()
            return AustralianLocationSearch(retrofit("https://geocoding-api.open-meteo.com/").create(GeocodingApi::class.java),
                retrofit("https://api.zippopotam.us/").create(PostcodeApi::class.java))
        }
    }
}
