package com.example.movesmart.ui.weather

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movesmart.data.WeatherProvider
import com.example.movesmart.data.remote.WeatherError
import com.example.movesmart.shared.UiState
import com.example.movesmart.shared.WeatherContext
import com.example.movesmart.shared.WeatherQuery
import com.example.movesmart.shared.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Weather: Manage forecast loading state
class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {
    private val stateFlow = MutableStateFlow<UiState<WeatherContext>>(UiState.Idle)
    val state = stateFlow.asStateFlow()
    private var query: WeatherQuery? = null
    private var loadJob: Job? = null
    private var observeJob: Job? = null
    private var queryVersion = 0

    fun loadWeather(value: WeatherQuery) {
        query = value
        queryVersion++
        val version = queryVersion
        loadJob?.cancel()
        observeJob?.cancel()
        stateFlow.value = UiState.Loading
        var fetchedWeather: WeatherContext? = null
        observeJob = viewModelScope.launch {
            try {
                repository.observe(value).collect { saved ->
                    currentCoroutineContext().ensureActive()
                    if (version == queryVersion && saved != null && saved.query == value) {
                        val fetched = fetchedWeather
                        val shown = if (fetched != null && saved.fetchedAt == fetched.fetchedAt && !saved.isStale) {
                            fetched
                        } else saved
                        stateFlow.value = UiState.Success(shown)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (version == queryVersion) showError(error)
            }
        }
        loadJob = viewModelScope.launch {
            try {
                val weather = repository.refresh(value).getOrThrow()
                currentCoroutineContext().ensureActive()
                if (version == queryVersion) {
                    fetchedWeather = weather
                    stateFlow.value = UiState.Success(weather)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (version == queryVersion) showError(error)
            }
        }
    }

    private fun showError(error: Exception) {
        stateFlow.value = UiState.Error(error.message ?: "Could not load weather.",
            (error as? WeatherError)?.retryable ?: true)
    }

    fun stopObserving() {
        queryVersion++
        loadJob?.cancel()
        observeJob?.cancel()
        query = null
        stateFlow.value = UiState.Idle
    }

    fun retryWeather() {
        val value = query ?: return
        loadWeather(value)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            factory(WeatherProvider.getRepository(context))

        fun factory(repository: WeatherRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(WeatherViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return WeatherViewModel(repository) as T
                }
            }
        }
    }
}
