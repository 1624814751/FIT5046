package com.example.movesmart

import androidx.lifecycle.ViewModelStore
import com.example.movesmart.data.remote.WeatherError
import com.example.movesmart.shared.*
import com.example.movesmart.ui.weather.WeatherViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun loadingSuccessAndCachedInfo() = runTest(mainDispatcherRule.dispatcher) {
        val repository = TestRepository()
        val model = WeatherViewModel(repository)
        assertEquals(UiState.Idle, model.state.value)
        model.loadWeather(weatherQuery)
        assertEquals(UiState.Loading, model.state.value)
        runCurrent()
        assertEquals(UiState.Success(liveWeather()), model.state.value)
        repository.result = Result.success(liveWeather().copy(source = DataSource.CACHE, isStale = true))
        model.retryWeather()
        runCurrent()
        assertTrue((model.state.value as UiState.Success).data.isStale)
    }

    @Test fun errorAndRetryUseSameQuery() = runTest(mainDispatcherRule.dispatcher) {
        val repository = TestRepository()
        repository.result = Result.failure(WeatherError("Offline", true))
        val model = WeatherViewModel(repository)
        model.loadWeather(weatherQuery)
        runCurrent()
        assertEquals(UiState.Error("Offline", true), model.state.value)
        repository.result = Result.success(liveWeather())
        model.retryWeather()
        runCurrent()
        assertEquals(weatherQuery, repository.lastQuery)
        assertEquals(2, repository.calls)
        assertTrue(model.state.value is UiState.Success)
    }

    @Test fun newQueryCancelsOldRequest() = runTest(mainDispatcherRule.dispatcher) {
        val first = CompletableDeferred<WeatherContext>()
        val repository = object : WeatherRepository {
            override fun observe(query: WeatherQuery) = emptyFlow<WeatherContext?>()
            override suspend fun refresh(query: WeatherQuery): Result<WeatherContext> =
                if (query == weatherQuery) Result.success(first.await())
                else Result.success(liveWeather().copy(query = query))
        }
        val model = WeatherViewModel(repository)
        val store = ViewModelStore()
        store.put("weather", model)
        model.loadWeather(weatherQuery)
        runCurrent()
        val next = weatherQuery.copy(date = weatherQuery.date.plusDays(1))
        model.loadWeather(next)
        runCurrent()
        first.complete(liveWeather())
        runCurrent()
        assertEquals(next, (model.state.value as UiState.Success).data.query)
        store.clear()
    }

    @Test fun laterCacheUpdatesReachState() = runTest(mainDispatcherRule.dispatcher) {
        val saved = MutableStateFlow<WeatherContext?>(null)
        val repository = object : WeatherRepository {
            override fun observe(query: WeatherQuery) = saved
            override suspend fun refresh(query: WeatherQuery) = Result.success(liveWeather())
        }
        val model = WeatherViewModel(repository)
        val store = ViewModelStore()
        store.put("weather", model)
        model.loadWeather(weatherQuery)
        runCurrent()
        val next = liveWeather().copy(fetchedAt = weatherNow.plusSeconds(60), source = DataSource.CACHE)
        saved.value = next
        runCurrent()
        assertEquals(UiState.Success(next), model.state.value)
        store.clear()
    }

    @Test fun retryBeforeFirstQueryDoesNothing() = runTest(mainDispatcherRule.dispatcher) {
        val repository = TestRepository()
        WeatherViewModel(repository).retryWeather()
        runCurrent()
        assertEquals(0, repository.calls)
    }

    private class TestRepository : WeatherRepository {
        var result = Result.success(liveWeather())
        var calls = 0
        var lastQuery: WeatherQuery? = null
        override fun observe(query: WeatherQuery) = emptyFlow<WeatherContext?>()
        override suspend fun refresh(query: WeatherQuery): Result<WeatherContext> {
            calls++
            lastQuery = query
            return result
        }
    }
}
