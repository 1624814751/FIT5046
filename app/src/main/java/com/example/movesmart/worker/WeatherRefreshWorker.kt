package com.example.movesmart.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.movesmart.data.WeatherProvider
import com.example.movesmart.data.remote.WeatherError
import com.example.movesmart.data.remote.checkWeather
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.WeatherQuery
import com.example.movesmart.shared.WeatherRepository
import java.time.Instant

class WeatherRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = updateWeather(
        WeatherProvider.getRepository(applicationContext), readWeatherQuery(inputData)
    )
}

// Ordinary tests call this with a fake repository and a fixed time.
internal suspend fun updateWeather(
    repository: WeatherRepository,
    query: WeatherQuery?,
    now: Instant = Instant.now()
): ListenableWorker.Result {
    if (query == null) return ListenableWorker.Result.failure()
    try {
        checkWeather(query, now)
    } catch (_: WeatherError) {
        return ListenableWorker.Result.failure()
    }
    val result = repository.refresh(query)
    val weather = result.getOrNull()
    if (weather != null) {
        // A saved fallback is useful to a screen, but the network update still needs retry.
        return if (weather.source == DataSource.LIVE) ListenableWorker.Result.success()
        else ListenableWorker.Result.retry()
    }
    val error = result.exceptionOrNull()
    return if (error is WeatherError && !error.retryable) ListenableWorker.Result.failure()
    else ListenableWorker.Result.retry()
}
