package com.example.movesmart.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.movesmart.data.WeatherProvider
import com.example.movesmart.data.remote.WeatherError
import com.example.movesmart.data.remote.checkWeather
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.WeatherQuery
import com.example.movesmart.shared.WeatherRepository
import kotlinx.coroutines.CancellationException
import java.time.Instant

// Weather worker: Refresh weather data
class WeatherRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.i(WEATHER_LOG_TAG, "Weather worker started; attempt=$runAttemptCount")
        return try {
            updateWeather(WeatherProvider.getRepository(applicationContext), readWeatherQuery(inputData))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(WEATHER_LOG_TAG, "Weather worker failed with an unexpected exception")
            throw error
        }
    }
}

internal suspend fun updateWeather(
    repository: WeatherRepository,
    query: WeatherQuery?,
    now: Instant = Instant.now()
): ListenableWorker.Result {
    if (query == null) {
        Log.w(WEATHER_LOG_TAG, "Weather refresh failed: missing or invalid input")
        return ListenableWorker.Result.failure()
    }
    try {
        checkWeather(query, now)
    } catch (_: WeatherError) {
        Log.w(WEATHER_LOG_TAG, "Weather refresh failed: invalid location or date")
        return ListenableWorker.Result.failure()
    }
    val result = repository.refresh(query)
    val weather = result.getOrNull()
    if (weather != null) {
        return if (weather.source == DataSource.LIVE) {
            Log.i(WEATHER_LOG_TAG, "Live weather refresh succeeded and cache was saved")
            ListenableWorker.Result.success()
        } else {
            Log.w(WEATHER_LOG_TAG, "Weather cache fallback; live refresh will retry")
            ListenableWorker.Result.retry()
        }
    }
    val error = result.exceptionOrNull()
    return if (error is WeatherError && !error.retryable) {
        Log.w(WEATHER_LOG_TAG, "Weather refresh failed; this attempt will not retry")
        ListenableWorker.Result.failure()
    } else {
        Log.w(WEATHER_LOG_TAG, "Weather refresh failed; retry requested")
        ListenableWorker.Result.retry()
    }
}
