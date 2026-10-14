package com.example.movesmart.data.remote

import com.example.movesmart.data.local.WeatherCache
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.WeatherContext
import com.example.movesmart.shared.WeatherQuery
import com.example.movesmart.shared.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Duration

// Weather: Load and cache weather forecasts
class OpenMeteoWeatherRepository(
    private val api: WeatherApi,
    private val cache: WeatherCache,
    private val clock: Clock = Clock.systemUTC()
) : WeatherRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(query: WeatherQuery): Flow<WeatherContext?> =
        cache.observe(query).transformLatest { weather ->
            if (weather == null) {
                emit(null)
            } else {
                val saved = cachedWeather(weather)
                emit(saved)
                if (!saved.isStale) {
                    val wait = Duration.between(clock.instant(), saved.fetchedAt.plusSeconds(3600)).toMillis()
                    delay(wait.coerceAtLeast(1))
                    emit(saved.copy(isStale = true))
                }
            }
        }

    override suspend fun refresh(query: WeatherQuery): Result<WeatherContext> {
        try {
            checkWeather(query, clock.instant())
            val response = api.getWeather(query.location.latitude, query.location.longitude)
            val weather = response.toWeather(query, clock.instant())
            cache.saveWeather(weather)
            return Result.success(weather)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failure = weatherError(error)
            if (failure.retryable) {
                try {
                    val saved = cache.loadWeather(query)
                    if (saved != null) return Result.success(cachedWeather(saved))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            }
            return Result.failure(failure)
        }
    }

    private fun cachedWeather(weather: WeatherContext): WeatherContext {
        val age = Duration.between(weather.fetchedAt, clock.instant())
        return weather.copy(source = DataSource.CACHE,
            isStale = age.isNegative || age >= Duration.ofHours(1))
    }
}

private fun weatherError(error: Exception): WeatherError = when (error) {
    is WeatherError -> error
    is SocketTimeoutException -> WeatherError("The weather request timed out. Please retry.", true, error)
    is IOException -> WeatherError("Cannot reach the weather service. Check your connection and retry.", true, error)
    is HttpException -> WeatherError("Weather service error (${error.code()}).",
        error.code() == 429 || error.code() >= 500, error)
    else -> WeatherError("Could not read or save weather. Please retry.", true, error)
}
