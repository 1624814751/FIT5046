package com.example.movesmart.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.movesmart.shared.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Base64

private val Context.recommendationDataStore by preferencesDataStore(name = "member_a_recommendations")

// Recommendations: Save and load results for each user
class DataStoreRecommendationStore(
    private val dataStore: DataStore<Preferences>,
    userId: String
) : RecommendationStore {
    constructor(context: Context, userId: String) : this(context.applicationContext.recommendationDataStore, userId)

    init {
        require(userId.isNotBlank()) { "A user ID is required." }
    }

    private val prefix = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(userId.toByteArray(Charsets.UTF_8)) + "."

    override fun observe(id: String): Flow<Recommendation?> {
        if (id.isBlank()) return flowOf(null)
        val key = stringPreferencesKey(prefix + id)
        return dataStore.data.map { values ->
            readRecommendation(values[key], id)
        }.distinctUntilChanged()
    }

    override suspend fun save(recommendation: Recommendation): Result<Unit> {
        return try {
            checkRecommendation(recommendation)
            val value = writeRecommendation(recommendation)
            dataStore.edit { values ->
                values[stringPreferencesKey(prefix + recommendation.id)] = value
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun writeRecommendation(value: Recommendation): String {
        return JSONObject().apply {
            put("id", value.id)
            put("activityName", value.activityName)
            put("minutes", value.minutes)
            put("intensity", value.intensity.name)
            put("environment", value.environment.name)
            put("plannedDate", value.plannedDate.toString())
            put("label", value.location.label)
            put("latitude", value.location.latitude)
            put("longitude", value.location.longitude)
            put("reasons", JSONArray(value.reasons))
        }.toString()
    }

    private fun readRecommendation(value: String?, id: String): Recommendation? {
        if (value == null) return null
        return try {
            val json = JSONObject(value)
            val list = json.getJSONArray("reasons")
            val reasons = mutableListOf<String>()
            for (index in 0 until list.length()) reasons.add(list.getString(index))
            val recommendation = Recommendation(
                json.getString("id"), json.getString("activityName"), json.getInt("minutes"),
                Intensity.valueOf(json.getString("intensity")),
                Environment.valueOf(json.getString("environment")),
                LocalDate.parse(json.getString("plannedDate")),
                GeoLocation(json.getString("label"), json.getDouble("latitude"), json.getDouble("longitude")),
                reasons
            )
            checkRecommendation(recommendation)
            if (recommendation.id == id) recommendation else null
        } catch (_: Exception) {
            null
        }
    }

    private fun checkRecommendation(value: Recommendation) {
        require(value.id.isNotBlank()) { "A recommendation ID is required." }
        require(value.activityName.isNotBlank() && value.minutes > 0) { "The activity or duration is not valid." }
        require(value.environment != Environment.ANY) { "Choose an indoor or outdoor activity." }
        require(value.reasons.isNotEmpty() && value.reasons.all { it.isNotBlank() }) { "Activity reasons are required." }
        require(value.location.label.isNotBlank() &&
            value.location.latitude.isFinite() && value.location.latitude in -90.0..90.0 &&
            value.location.longitude.isFinite() && value.location.longitude in -180.0..180.0) {
            "The activity location is not valid."
        }
    }
}
