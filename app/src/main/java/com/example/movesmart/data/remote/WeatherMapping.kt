package com.example.movesmart.data.remote

import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.WeatherContext
import com.example.movesmart.shared.WeatherQuery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class WeatherError(message: String, val retryable: Boolean, cause: Throwable? = null) :
    Exception(message, cause)

fun checkWeather(query: WeatherQuery, now: Instant = Instant.now()) {
    val location = query.location
    if (location.label.isBlank() || !location.latitude.isFinite() || !location.longitude.isFinite() ||
        location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
        throw WeatherError("Choose a named location with valid latitude and longitude.", false)
    }
    val today = now.atZone(ZoneOffset.UTC).toLocalDate()
    if (query.date < today.minusDays(1) || query.date > today.plusDays(16)) {
        throw WeatherError("Choose a date within the next 16 forecast days.", false)
    }
}

// Weather mapping: Read the selected day's noon forecast
fun WeatherDto.toWeather(query: WeatherQuery, now: Instant): WeatherContext {
    val zone = try {
        ZoneId.of(timezone ?: "")
    } catch (_: Exception) {
        throw WeatherError("The weather response has no valid time zone.", true)
    }
    val today = LocalDate.ofInstant(now, zone)
    if (query.date < today || query.date > today.plusDays(15)) {
        throw WeatherError("The selected date is outside the local 16-day forecast range.", false)
    }
    val index = hourly?.time?.indexOf("${query.date}T12:00") ?: -1
    if (index < 0) throw WeatherError("No noon forecast is available for the selected date.", false)
    val temperature = hourly?.temperature?.getOrNull(index)
    if (temperature == null || !temperature.isFinite() || units?.temperature != "°C") {
        throw WeatherError("The weather response has no valid Celsius temperature.", true)
    }
    val rain = hourly?.rain?.getOrNull(index)
    val wind = hourly?.wind?.getOrNull(index)
    if (rain != null && (rain !in 0..100 || units?.rain != "%")) {
        throw WeatherError("The weather response has an invalid rain probability.", true)
    }
    if (wind != null && (!wind.isFinite() || wind < 0 || units?.wind != "km/h")) {
        throw WeatherError("The weather response has an invalid wind speed.", true)
    }
    return WeatherContext(query, temperature, rain, wind, now, DataSource.LIVE, false)
}
