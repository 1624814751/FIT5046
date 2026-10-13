package com.example.movesmart.ui.planning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movesmart.data.local.DataStoreRecommendationStore
import com.example.movesmart.recommendation.SimpleRecommendationEngine
import com.example.movesmart.shared.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class PlanningViewModel(
    private val userId: String,
    private val store: RecommendationStore,
    private val engine: RecommendationEngine = SimpleRecommendationEngine(),
    private val now: () -> Instant = Instant::now,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {
    private val mutableState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val state = mutableState.asStateFlow()
    private var saving: Job? = null
    fun observe(id: String) = store.observe(id)
    fun create(preferences: UserPreferences, plan: PlanningInput, weather: WeatherContext) {
        if (saving?.isActive == true) return
        mutableState.value = UiState.Loading
        saving = viewModelScope.launch {
            try {
                require(preferences.userId == userId) { "Your account changed. Reload preferences." }
                require(weather.query == WeatherQuery(plan.location, plan.date)) { "Load weather for this location and date first." }
                require(weather.source != DataSource.DEMO) { "Load real weather before creating a plan." }
                val recommendation = engine.evaluate(ContextInput(preferences, plan, weather, null, now()))
                    .recommendation.copy(id = newId())
                // Each saved plan has its own ID; never overwrite another planning session.
                store.save(recommendation).getOrThrow()
                mutableState.value = UiState.Success(recommendation.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = UiState.Error(error.message ?: "Could not save your plan. Please retry.", true)
            }
        }
    }
    fun consumeNavigation() { mutableState.value = UiState.Idle }
    fun cancelPending() { saving?.cancel(); mutableState.value = UiState.Idle }
    companion object {
        fun factory(context: Context, userId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(PlanningViewModel::class.java))
                return PlanningViewModel(userId, DataStoreRecommendationStore(context, userId)) as T
            }
        }
    }
}
