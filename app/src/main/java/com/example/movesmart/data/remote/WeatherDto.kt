package com.example.movesmart.data.remote

import com.google.gson.annotations.SerializedName

data class WeatherDto(
    val timezone: String? = null,
    @SerializedName("hourly_units") val units: WeatherUnits? = null,
    val hourly: WeatherHours? = null
)

data class WeatherUnits(
    @SerializedName("temperature_2m") val temperature: String? = null,
    @SerializedName("precipitation_probability") val rain: String? = null,
    @SerializedName("wind_speed_10m") val wind: String? = null
)

data class WeatherHours(
    val time: List<String?>? = null,
    @SerializedName("temperature_2m") val temperature: List<Double?>? = null,
    @SerializedName("precipitation_probability") val rain: List<Int?>? = null,
    @SerializedName("wind_speed_10m") val wind: List<Double?>? = null
)
