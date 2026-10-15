package com.example.movesmart

import com.example.movesmart.location.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class AustralianLocationSearchTest {
    private val place = GeocodingPlace("Clayton", -37.91667, 145.11667, "AU", "Australia", "Victoria", null, "PPLX")
    private var requestedName: String? = null
    private var response = GeocodingResponse(listOf(place))
    private var failure: Exception? = null
    private var postcode = PostcodeResponse("3168", "AU", "Australia", listOf(
        PostcodePlace("Clayton", "-37.9167", "145.1333", "Victoria", "VIC"),
        PostcodePlace("Notting Hill", "-37.9", "145.1167", "Victoria", "VIC")))
    private val repository = AustralianLocationSearch(object : GeocodingApi {
        override suspend fun search(name: String, country: String, count: Int, language: String): GeocodingResponse {
            assertEquals("AU", country)
            requestedName = name
            failure?.let { throw it }
            return response
        }
    }, object : PostcodeApi {
        override suspend fun search(postcode: String): PostcodeResponse {
            assertEquals("3168", postcode)
            return this@AustralianLocationSearchTest.postcode
        }
    })

    @Test fun suburbAndStateSearchNormalizesSuffixWithoutInventingPostcode() = runBlocking {
        for (query in listOf("Clayton VIC", "Clayton, VIC", "Clayton Victoria")) {
            val result = repository.search(query)
            assertEquals("Clayton, Victoria", requestedName)
            assertEquals("Clayton, VIC", result.single().location.label)
            assertEquals(place.latitude!!, result.single().location.latitude, 0.0)
        }
    }

    @Test fun australianPostcodeReturnsEveryPlausibleSuburbAndPreservesLeadingZero() = runBlocking {
        assertEquals(listOf("Clayton, VIC 3168", "Notting Hill, VIC 3168"), repository.search("3168").map { it.location.label })
        assertTrue(repository.search("3168 NSW").isEmpty())
        assertEquals("0800", parseAustralianQuery("0800 NT").term)
        assertTrue(parseAustralianQuery("0800 NT").postcode)
    }

    @Test fun rejectsCountryStateCoordinateFeatureAndPostcodeMismatches() = runBlocking {
        response = GeocodingResponse(listOf(place.copy(countryCode = "US"), place.copy(country = "Canada"),
            place.copy(admin1 = "Atlantis"), place.copy(latitude = Double.NaN), place.copy(longitude = 181.0),
            place.copy(name = ""), place.copy(postcodes = listOf("abc")), place.copy(featureCode = "DAM"),
            place.copy(admin1 = "Queensland")))
        assertTrue(repository.search("Clayton VIC").isEmpty())
        postcode = postcode.copy(places = listOf(postcode.places!!.first().copy(stateAbbreviation = "NSW")))
        assertTrue(repository.search("3168").isEmpty())
        postcode = postcode.copy(countryCode = "US")
        assertTrue(runCatching { repository.search("3168") }.exceptionOrNull() is LocationFailure)
    }

    @Test fun noResultsMalformedInputAndTransportFailuresRemainExplicit() = runBlocking {
        response = GeocodingResponse()
        assertTrue(repository.search("Nowhere").isEmpty())
        assertTrue(runCatching { repository.search("12345") }.exceptionOrNull() is LocationFailure)
        failure = IOException("offline")
        assertEquals(LocationProblem.NETWORK, (runCatching { repository.search("Clayton") }.exceptionOrNull() as LocationFailure).problem)
        failure = SocketTimeoutException()
        assertEquals(LocationProblem.TIMEOUT, (runCatching { repository.search("Clayton") }.exceptionOrNull() as LocationFailure).problem)
    }
}
