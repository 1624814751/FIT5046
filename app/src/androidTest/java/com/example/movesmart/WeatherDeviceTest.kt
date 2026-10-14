package com.example.movesmart

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.movesmart.data.WeatherProvider
import com.example.movesmart.data.local.StoredWeatherCache
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.GeoLocation
import com.example.movesmart.shared.WeatherContext
import com.example.movesmart.shared.WeatherQuery
import com.example.movesmart.worker.WeatherRefreshWorker
import com.example.movesmart.worker.weatherQueryData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class WeatherDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var savedCache: String? = null

    @Before fun keepCache() {
        savedCache = context.getSharedPreferences("weather_cache", 0).getString("weather", null)
    }

    @After fun restoreCache() {
        context.getSharedPreferences("weather_cache", 0).edit().putString("weather", savedCache).commit()
    }

    @Test fun savedWeatherRestoresFromNewStore() = runBlocking {
        val query = WeatherQuery(GeoLocation("Melbourne", -37.8136, 144.9631), LocalDate.now())
        val weather = WeatherContext(query, 20.0, 30, 10.0, Instant.now(), DataSource.LIVE, false)
        StoredWeatherCache(context).saveWeather(weather)
        assertEquals(weather.copy(source = DataSource.CACHE), StoredWeatherCache(context).loadWeather(query))
    }

    @Test fun missingWorkerInputFailsSafely() = runBlocking {
        val worker = TestListenableWorkerBuilder<WeatherRefreshWorker>(context).build()
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    @Test fun realForecastAndWorkManagerUpdate() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("weatherLiveTest") == "true")
        val query = WeatherQuery(GeoLocation("Melbourne", -37.8136, 144.9631),
            LocalDate.now(ZoneId.of("Australia/Melbourne")))
        val weather = WeatherProvider.getRepository(context).refresh(query).getOrThrow()
        assertEquals(DataSource.LIVE, weather.source)
        assertEquals(query, weather.query)
        assertTrue(weather.temperatureC.isFinite())
        val cache = StoredWeatherCache(context)
        assertEquals(weather.copy(source = DataSource.CACHE), cache.loadWeather(query))

        val manager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<WeatherRefreshWorker>()
            .setInputData(weatherQueryData(query))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        try {
            manager.enqueueUniqueWork("weather-device-test", ExistingWorkPolicy.REPLACE, request)
                .result.get(10, TimeUnit.SECONDS)
            val info = withTimeout(60_000) {
                manager.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished == true }
            }
            assertEquals(WorkInfo.State.SUCCEEDED, info!!.state)
            assertTrue(cache.loadWeather(query)!!.fetchedAt > weather.fetchedAt)
        } finally {
            manager.cancelUniqueWork("weather-device-test").result.get(10, TimeUnit.SECONDS)
        }
    }
}
