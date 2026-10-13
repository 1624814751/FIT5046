package com.example.movesmart.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.movesmart.data.remote.checkWeather
import com.example.movesmart.shared.GeoLocation
import com.example.movesmart.shared.WeatherQuery
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

const val WEATHER_WORK_NAME = "weather-refresh"

fun weatherQueryData(query: WeatherQuery): Data = workDataOf(
    "label" to query.location.label,
    "latitude" to query.location.latitude,
    "longitude" to query.location.longitude,
    "date" to query.date.toString()
)

fun weatherWorkRequest(query: WeatherQuery, now: Instant = Instant.now()): PeriodicWorkRequest {
    checkWeather(query, now)
    return PeriodicWorkRequestBuilder<WeatherRefreshWorker>(60, TimeUnit.MINUTES)
        .setInitialDelay(60, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(weatherQueryData(query))
        .addTag(WEATHER_WORK_NAME)
        .build()
}

fun readWeatherQuery(data: Data): WeatherQuery? = try {
    WeatherQuery(
        GeoLocation(data.getString("label") ?: "", data.getDouble("latitude", Double.NaN),
            data.getDouble("longitude", Double.NaN)),
        LocalDate.parse(data.getString("date") ?: "")
    )
} catch (_: Exception) {
    null
}

class WeatherWork(context: Context) {
    private val manager = WorkManager.getInstance(context.applicationContext)

    fun startWeatherWork(query: WeatherQuery) = manager.enqueueUniquePeriodicWork(
        WEATHER_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, weatherWorkRequest(query)
    )

    fun stopWeatherWork() = manager.cancelUniqueWork(WEATHER_WORK_NAME)
}
