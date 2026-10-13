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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {
    private val stateFlow = MutableStateFlow<UiState<WeatherContext>>(UiState.Idle)
    val state = stateFlow.asStateFlow()
    private var query: WeatherQuery? = null
    private var loadJob: Job? = null

    fun loadWeather(value: WeatherQuery) {
        query = value
        loadJob?.cancel()
        stateFlow.value = UiState.Loading
        loadJob = viewModelScope.launch {
            val result = repository.refresh(value)
            val weather = result.getOrNull()
            if (weather == null) {
                val error = result.exceptionOrNull()
                stateFlow.value = UiState.Error(error?.message ?: "Could not load weather.",
                    (error as? WeatherError)?.retryable ?: true)
                return@launch
            }
            stateFlow.value = UiState.Success(weather)
            repository.observe(value).collect { saved ->
                if (saved != null) {
                    // Keep LIVE for this fetch; show later worker updates and old data as CACHE.
                    val shown = if (saved.fetchedAt == weather.fetchedAt && !saved.isStale) weather else saved
                    stateFlow.value = UiState.Success(shown)
                }
            }
        }
    }

    fun stopObserving() { loadJob?.cancel(); stateFlow.value = UiState.Idle }

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
