package com.example.movesmart.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.GeoLocation
import com.example.movesmart.shared.WeatherContext
import com.example.movesmart.shared.WeatherQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

interface WeatherCache {
    fun observe(query: WeatherQuery): Flow<WeatherContext?>
    suspend fun loadWeather(query: WeatherQuery): WeatherContext?
    suspend fun saveWeather(weather: WeatherContext)
}

// Save one forecast in its own file. Member A's user preferences stay in DataStore.
class StoredWeatherCache(context: Context) : WeatherCache {
    private val prefs = context.applicationContext.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    private fun readWeather(query: WeatherQuery): WeatherContext? {
        return try {
            val json = JSONObject(prefs.getString("weather", null) ?: return null)
            val savedQuery = WeatherQuery(
                GeoLocation(json.getString("label"), json.getDouble("latitude"), json.getDouble("longitude")),
                LocalDate.parse(json.getString("date"))
            )
            if (savedQuery != query) return null
            val temperature = json.getDouble("temperature")
            val rain = if (json.isNull("rain")) null else json.getInt("rain")
            val wind = if (json.isNull("wind")) null else json.getDouble("wind")
            if (!temperature.isFinite() || (rain != null && rain !in 0..100) ||
                (wind != null && (!wind.isFinite() || wind < 0))) return null
            WeatherContext(savedQuery, temperature, rain, wind,
                Instant.parse(json.getString("fetchedAt")), DataSource.CACHE, false)
        } catch (_: Exception) {
            null // A broken cache must not become fake weather.
        }
    }

    override suspend fun loadWeather(query: WeatherQuery): WeatherContext? =
        withContext(Dispatchers.IO) { readWeather(query) }

    override fun observe(query: WeatherQuery): Flow<WeatherContext?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "weather" || key == null) trySend(readWeather(query))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(readWeather(query))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    @SuppressLint("UseKtx") // Check the commit result; the KTX edit function does not return it.
    override suspend fun saveWeather(weather: WeatherContext) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("label", weather.query.location.label)
            put("latitude", weather.query.location.latitude)
            put("longitude", weather.query.location.longitude)
            put("date", weather.query.date.toString())
            put("temperature", weather.temperatureC)
            put("rain", weather.rainProbabilityPercent ?: JSONObject.NULL)
            put("wind", weather.windSpeedKmh ?: JSONObject.NULL)
            put("fetchedAt", weather.fetchedAt.toString())
        }
        check(prefs.edit().putString("weather", json.toString()).commit()) {
            "Could not save weather on this device."
        }
    }
}
