package com.example.movesmart

import com.example.movesmart.data.local.WeatherCache
import com.example.movesmart.data.remote.*
import com.example.movesmart.shared.*
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal val weatherNow: Instant = Instant.parse("2026-10-11T01:00:00Z")
internal val weatherQuery = WeatherQuery(GeoLocation("Melbourne", -37.8136, 144.9631), LocalDate.parse("2026-10-12"))
internal fun weatherDto() = WeatherDto("Australia/Melbourne", WeatherUnits("°C", "%", "km/h"),
    WeatherHours(listOf("2026-10-11T12:00", "2026-10-12T12:00"), listOf(12.0, 21.5), listOf(10, 65), listOf(8.0, 19.2)))
internal fun liveWeather() = weatherDto().toWeather(weatherQuery, weatherNow)

internal class MemoryWeatherCache : WeatherCache {
    val saved = MutableStateFlow<WeatherContext?>(null)
    override fun observe(query: WeatherQuery): Flow<WeatherContext?> =
        saved.map { weather -> weather?.takeIf { it.query == query } }
    override suspend fun loadWeather(query: WeatherQuery) = saved.value?.takeIf { it.query == query }
    override suspend fun saveWeather(weather: WeatherContext) { saved.value = weather }
}

internal class FakeWeatherApi : WeatherApi {
    var error: Exception? = null
    var response = weatherDto()
    var calls = 0
    override suspend fun getWeather(latitude: Double, longitude: Double, hourly: String,
        temperatureUnit: String, windUnit: String, timezone: String, forecastDays: Int): WeatherDto {
        calls++
        assertEquals(-37.8136, latitude, 0.0)
        assertEquals(144.9631, longitude, 0.0)
        assertEquals("temperature_2m,precipitation_probability,wind_speed_10m", hourly)
        assertEquals("celsius", temperatureUnit)
        assertEquals("kmh", windUnit)
        assertEquals("auto", timezone)
        assertEquals(16, forecastDays)
        error?.let { throw it }
        return response
    }
}

class WeatherRepositoryTest {
    private val api = FakeWeatherApi()
    private val cache = MemoryWeatherCache()
    private val repository = OpenMeteoWeatherRepository(api, cache, Clock.fixed(weatherNow, ZoneOffset.UTC))

    @Test fun mapsSelectedDateAndUnits() {
        val weather = liveWeather()
        assertEquals(21.5, weather.temperatureC, 0.0)
        assertEquals(65, weather.rainProbabilityPercent)
        assertEquals(19.2, weather.windSpeedKmh!!, 0.0)
        assertEquals(weatherQuery, weather.query)
        assertEquals(weatherNow, weather.fetchedAt)
        assertEquals(DataSource.LIVE, weather.source)
        assertFalse(weather.isStale)
    }

    @Test fun gsonReadsApiFieldNames() {
        val json = """{"timezone":"Australia/Melbourne","hourly_units":{"temperature_2m":"°C","precipitation_probability":"%","wind_speed_10m":"km/h"},"hourly":{"time":["2026-10-12T12:00"],"temperature_2m":[21.5],"precipitation_probability":[65],"wind_speed_10m":[19.2]}}"""
        assertEquals(liveWeather(), Gson().fromJson(json, WeatherDto::class.java).toWeather(weatherQuery, weatherNow))
    }

    @Test fun missingOptionalValuesStayNull() {
        val dto = weatherDto().copy(hourly = WeatherHours(listOf("2026-10-12T12:00"), listOf(20.0)))
        val result = dto.toWeather(weatherQuery, weatherNow)
        assertNull(result.rainProbabilityPercent)
        assertNull(result.windSpeedKmh)
    }

    @Test fun missingTemperatureFails() {
        val dto = weatherDto().copy(hourly = WeatherHours(listOf("2026-10-12T12:00")))
        assertThrows(WeatherError::class.java) { dto.toWeather(weatherQuery, weatherNow) }
    }

    @Test fun invalidUnitsFail() {
        for (units in listOf(WeatherUnits("°F", "%", "km/h"), WeatherUnits("°C", "fraction", "km/h"),
            WeatherUnits("°C", "%", "mph"))) {
            assertThrows(WeatherError::class.java) { weatherDto().copy(units = units).toWeather(weatherQuery, weatherNow) }
        }
    }

    @Test fun invalidValuesFail() {
        for (hours in listOf(weatherDto().hourly!!.copy(rain = listOf(10, 101)),
            weatherDto().hourly!!.copy(wind = listOf(8.0, -1.0)),
            weatherDto().hourly!!.copy(temperature = listOf(12.0, Double.NaN)))) {
            assertThrows(WeatherError::class.java) { weatherDto().copy(hourly = hours).toWeather(weatherQuery, weatherNow) }
        }
    }

    @Test fun missingDateDoesNotUseToday() {
        assertThrows(WeatherError::class.java) {
            weatherDto().toWeather(weatherQuery.copy(date = LocalDate.parse("2026-10-13")), weatherNow)
        }
    }

    @Test fun missingOrInvalidTimeZoneFails() {
        for (zone in listOf(null, "unknown/zone")) {
            assertThrows(WeatherError::class.java) { weatherDto().copy(timezone = zone).toWeather(weatherQuery, weatherNow) }
        }
    }

    @Test fun invalidLocationDoesNotCallApi() = runTest {
        for (location in listOf(GeoLocation("", 0.0, 0.0), GeoLocation("Bad", 91.0, 0.0),
            GeoLocation("Bad", 0.0, -181.0), GeoLocation("Bad", Double.NaN, 0.0),
            GeoLocation("Bad", 0.0, Double.POSITIVE_INFINITY))) {
            assertTrue(repository.refresh(weatherQuery.copy(location = location)).isFailure)
        }
        assertEquals(0, api.calls)
    }

    @Test fun unsupportedDatesFailBeforeNetwork() = runTest {
        assertTrue(repository.refresh(weatherQuery.copy(date = LocalDate.parse("2026-11-01"))).isFailure)
        assertTrue(repository.refresh(weatherQuery.copy(date = LocalDate.parse("2026-09-01"))).isFailure)
        assertEquals(0, api.calls)
    }

    @Test fun exactDateRangeUsesLocationTimeZone() {
        for (date in listOf("2026-10-10", "2026-10-27")) {
            assertThrows(WeatherError::class.java) {
                weatherDto().toWeather(weatherQuery.copy(date = LocalDate.parse(date)), weatherNow)
            }
        }
        val localMidnight = Instant.parse("2026-10-10T13:00:00Z")
        assertEquals(LocalDate.parse("2026-10-11"), weatherDto().toWeather(
            weatherQuery.copy(date = LocalDate.parse("2026-10-11")), localMidnight).query.date)
    }

    @Test fun savesSuccessfulWeather() = runTest {
        assertEquals(liveWeather(), repository.refresh(weatherQuery).getOrThrow())
        assertEquals(liveWeather(), cache.loadWeather(weatherQuery))
    }

    @Test fun offlineWithoutCacheReturnsError() = runTest {
        api.error = IOException("offline")
        val error = repository.refresh(weatherQuery).exceptionOrNull() as WeatherError
        assertTrue(error.retryable)
        assertTrue(error.message!!.contains("connection"))
    }

    @Test fun timeoutHasClearError() = runTest {
        api.error = SocketTimeoutException()
        assertTrue(repository.refresh(weatherQuery).exceptionOrNull()!!.message!!.contains("timed out"))
    }

    @Test fun recentCacheIsClearlyMarked() = runTest {
        cache.saveWeather(liveWeather())
        api.error = IOException()
        val result = repository.refresh(weatherQuery).getOrThrow()
        assertEquals(DataSource.CACHE, result.source)
        assertFalse(result.isStale)
    }

    @Test fun hourOldAndFutureCacheAreStale() = runTest {
        api.error = IOException()
        for (time in listOf(weatherNow.minusSeconds(3600), weatherNow.plusSeconds(1))) {
            cache.saveWeather(liveWeather().copy(fetchedAt = time))
            assertTrue(repository.refresh(weatherQuery).getOrThrow().isStale)
        }
    }

    @Test fun differentLocationOrDateCannotUseCache() = runTest {
        api.error = IOException()
        for (query in listOf(weatherQuery.copy(date = weatherQuery.date.plusDays(1)),
            weatherQuery.copy(location = weatherQuery.location.copy(latitude = -38.0)))) {
            cache.saveWeather(liveWeather().copy(query = query))
            assertTrue(repository.refresh(weatherQuery).isFailure)
        }
    }

    @Test fun cancellationIsNotHiddenByCache() = runTest {
        cache.saveWeather(liveWeather())
        api.error = CancellationException()
        try {
            repository.refresh(weatherQuery)
            fail("Cancellation must be thrown")
        } catch (_: CancellationException) { }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun observedCacheBecomesStaleWithoutNewSave() = runTest {
        cache.saveWeather(liveWeather())
        val values = mutableListOf<WeatherContext?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observe(weatherQuery).collect { values.add(it) }
        }
        assertFalse(values.last()!!.isStale)
        advanceTimeBy(3_600_000)
        runCurrent()
        assertTrue(values.last()!!.isStale)
    }

    @Test fun unsupportedResponseDateDoesNotReturnCache() = runTest {
        cache.saveWeather(liveWeather())
        api.response = weatherDto().copy(hourly = null)
        val error = repository.refresh(weatherQuery).exceptionOrNull() as WeatherError
        assertFalse(error.retryable)
    }
}
