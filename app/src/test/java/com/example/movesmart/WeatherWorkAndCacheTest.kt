package com.example.movesmart

import android.content.Context
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.movesmart.data.local.StoredWeatherCache
import com.example.movesmart.data.remote.OpenMeteoWeatherRepository
import com.example.movesmart.data.remote.WeatherError
import com.example.movesmart.shared.*
import com.example.movesmart.worker.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeatherWorkAndCacheTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun clearCache() {
        context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun cacheRestoresWithNewStoreInstance() = runTest {
        StoredWeatherCache(context).saveWeather(liveWeather())
        assertEquals(liveWeather().copy(source = DataSource.CACHE), StoredWeatherCache(context).loadWeather(weatherQuery))
    }

    @Test fun cacheKeepsNullValuesAndRejectsOtherQuery() = runTest {
        val cache = StoredWeatherCache(context)
        cache.saveWeather(liveWeather().copy(rainProbabilityPercent = null, windSpeedKmh = null))
        assertNull(cache.loadWeather(weatherQuery)!!.rainProbabilityPercent)
        assertNull(cache.loadWeather(weatherQuery)!!.windSpeedKmh)
        assertNull(cache.loadWeather(weatherQuery.copy(date = weatherQuery.date.plusDays(1))))
        assertNull(cache.loadWeather(weatherQuery.copy(location = weatherQuery.location.copy(longitude = 140.0))))
    }

    @Test fun brokenCacheIsIgnored() = runTest {
        context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE).edit().putString("weather", "broken").commit()
        assertNull(StoredWeatherCache(context).loadWeather(weatherQuery))
    }

    @Test fun observerRestoresSavedWeather() = runTest {
        val cache = StoredWeatherCache(context)
        cache.saveWeather(liveWeather())
        assertEquals(DataSource.CACHE, cache.observe(weatherQuery).first()!!.source)
    }

    @Test fun requestHasNetworkIntervalAndSavedQuery() {
        val request = weatherWorkRequest(weatherQuery, weatherNow)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(1), request.workSpec.intervalDuration)
        assertEquals(TimeUnit.HOURS.toMillis(1), request.workSpec.initialDelay)
        assertEquals(weatherQuery, readWeatherQuery(request.workSpec.input))
        assertTrue(request.tags.contains(WEATHER_WORK_NAME))
    }

    @Test fun repeatedStartUpdatesOneUniqueScheduleAndStopCancelsIt() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val scheduler = WeatherWork(context)
        val query = weatherQuery.copy(date = LocalDate.now())
        scheduler.startWeatherWork(query).result.get(10, TimeUnit.SECONDS)
        scheduler.startWeatherWork(query.copy(date = query.date.plusDays(1))).result.get(10, TimeUnit.SECONDS)
        val manager = WorkManager.getInstance(context)
        val active = manager.getWorkInfosForUniqueWork(WEATHER_WORK_NAME).get(10, TimeUnit.SECONDS).filter { !it.state.isFinished }
        assertEquals(1, active.size)
        scheduler.stopWeatherWork().result.get(10, TimeUnit.SECONDS)
        assertTrue(manager.getWorkInfosForUniqueWork(WEATHER_WORK_NAME).get(10, TimeUnit.SECONDS).all { it.state.isFinished })
    }

    @Test fun invalidQueryCannotBeScheduled() {
        assertThrows(WeatherError::class.java) {
            weatherWorkRequest(weatherQuery.copy(location = GeoLocation("Unknown", Double.NaN, Double.NaN)), weatherNow)
        }
    }

    @Test fun missingInputFailsWithoutRequest() = runTest {
        assertNull(readWeatherQuery(Data.EMPTY))
        assertEquals(ListenableWorker.Result.failure(), updateWeather(NoWeatherRepository(), null, weatherNow))
    }

    @Test fun realWorkerHandlesMissingInput() = runTest {
        val worker = TestListenableWorkerBuilder<WeatherRefreshWorker>(context).build()
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    @Test fun expiredInputFailsWithoutRequest() = runTest {
        assertEquals(ListenableWorker.Result.failure(), updateWeather(NoWeatherRepository(),
            weatherQuery.copy(date = weatherQuery.date.minusMonths(1)), weatherNow))
    }

    @Test fun successfulWorkerLogicSavesCache() = runTest {
        val cache = StoredWeatherCache(context)
        val repository = OpenMeteoWeatherRepository(FakeWeatherApi(), cache, Clock.fixed(weatherNow, ZoneOffset.UTC))
        assertEquals(ListenableWorker.Result.success(), updateWeather(repository, weatherQuery, weatherNow))
        assertEquals(liveWeather().copy(source = DataSource.CACHE), cache.loadWeather(weatherQuery))
    }

    @Test fun networkFailureRetriesWithOrWithoutCache() = runTest {
        val cache = StoredWeatherCache(context)
        val api = FakeWeatherApi().apply { error = IOException() }
        val repository = OpenMeteoWeatherRepository(api, cache, Clock.fixed(weatherNow, ZoneOffset.UTC))
        assertEquals(ListenableWorker.Result.retry(), updateWeather(repository, weatherQuery, weatherNow))
        cache.saveWeather(liveWeather())
        assertEquals(ListenableWorker.Result.retry(), updateWeather(repository, weatherQuery, weatherNow))
        assertEquals(weatherNow, cache.loadWeather(weatherQuery)!!.fetchedAt)
    }

    private class NoWeatherRepository : WeatherRepository {
        override fun observe(query: WeatherQuery) = emptyFlow<WeatherContext?>()
        override suspend fun refresh(query: WeatherQuery): Result<WeatherContext> = error("Must not call weather")
    }
}
