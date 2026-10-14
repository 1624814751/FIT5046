package com.example.movesmart.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

// Weather API: Request hourly forecasts
interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,wind_speed_10m",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("wind_speed_unit") windUnit: String = "kmh",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 16
    ): WeatherDto
}
