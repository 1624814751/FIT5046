package com.example.movesmart.data

import android.content.Context
import com.example.movesmart.data.local.StoredWeatherCache
import com.example.movesmart.data.remote.OpenMeteoWeatherRepository
import com.example.movesmart.data.remote.RetrofitClient
import com.example.movesmart.shared.WeatherRepository

object WeatherProvider {
    @Volatile private var repository: WeatherRepository? = null

    fun getRepository(context: Context): WeatherRepository = synchronized(this) {
        repository ?: OpenMeteoWeatherRepository(
            RetrofitClient.weatherApi, StoredWeatherCache(context.applicationContext)
        ).also { repository = it }
    }
}
