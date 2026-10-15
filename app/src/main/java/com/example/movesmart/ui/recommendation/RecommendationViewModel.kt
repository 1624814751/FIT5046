package com.example.movesmart.ui.recommendation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movesmart.data.local.DataStoreRecommendationStore
import com.example.movesmart.recommendation.SimpleRecommendationEngine
import com.example.movesmart.shared.AuthRepository
import com.example.movesmart.shared.ContextDecision
import com.example.movesmart.shared.ContextInput
import com.example.movesmart.shared.MotionContext
import com.example.movesmart.shared.Intensity
import com.example.movesmart.shared.PlanningInput
import com.example.movesmart.shared.PreferencesRepository
import com.example.movesmart.shared.RecommendationEngine
import com.example.movesmart.shared.RecommendationStore
import com.example.movesmart.shared.UiState
import com.example.movesmart.shared.UserPreferences
import com.example.movesmart.shared.WeatherContext
import com.example.movesmart.ui.auth.MemberAService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock

data class RecommendationSelection(
    val userId: String,
    val recommendationId: String,
    val activityName: String,
    val minutes: Int,
    val intensity: Intensity
)

// Recommendations: Manage recommendation results
class RecommendationViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    private val engine: RecommendationEngine,
    private val storeForUser: (String) -> RecommendationStore,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {
    private val stateFlow = MutableStateFlow<UiState<ContextDecision?>>(UiState.Idle)
    val state = stateFlow.asStateFlow()
    private val selectionFlow = MutableStateFlow<RecommendationSelection?>(null)
    val selection = selectionFlow.asStateFlow()

    // Check the synchronous auth value as well as the collector's owner. A click or
    // recomposition can arrive before the account-change coroutine has run.
    fun stateForUser(id: String?): UiState<ContextDecision?> =
        if (id != null && id == userId && id == authRepository.currentUser.value?.userId) stateFlow.value
        else UiState.Idle

    fun selectionForUser(id: String?): RecommendationSelection? =
        selectionFlow.value?.takeIf {
            id != null && it.userId == id && id == userId && id == authRepository.currentUser.value?.userId
        }

    // Guard during composition as well as in the asynchronous invalidation effect.
    fun stateForContext(id: String, plan: PlanningInput?, weather: WeatherContext?): UiState<ContextDecision?> =
        if (lastPlan != null && (lastPlan != plan || lastWeather != weather)) UiState.Idle else stateForUser(id)

    fun syncContext(plan: PlanningInput?, weather: WeatherContext?) {
        if (lastPlan != null && (lastPlan != plan || lastWeather != weather)) invalidatePlan()
    }

    fun selectRecommendation(recommendationId: String): Boolean {
        val id = authRepository.currentUser.value?.userId ?: return false
        val decision = (stateForUser(id) as? UiState.Success)?.data ?: return false
        val recommendation = decision.recommendation
        if (recommendation.id != recommendationId) return false
        selectionFlow.value = RecommendationSelection(id, recommendation.id,
            recommendation.activityName, recommendation.minutes, recommendation.intensity)
        return true
    }

    fun invalidatePlan() {
        version++
        resultJob?.cancel()
        selectionFlow.value = null
        lastPlan = null
        lastWeather = null
        lastMotion = null
        lastRecommendationId = null
        if (preferencesLoaded && preferences != null) stateFlow.value = UiState.Idle
    }
    private var userId: String? = null
    private var preferences: UserPreferences? = null
    private var preferencesLoaded = false
    private var preferencesError: String? = null
    private var store: RecommendationStore? = null
    private var preferencesJob: Job? = null
    private var resultJob: Job? = null
    private var version = 0
    private var lastPlan: PlanningInput? = null
    private var lastWeather: WeatherContext? = null
    private var lastMotion: MotionContext? = null
    private var lastRecommendationId: String? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.map { it?.userId }.distinctUntilChanged().collect { id ->
                userId = id
                store = null
                lastPlan = null
                lastWeather = null
                lastMotion = null
                lastRecommendationId = null
                observePreferences()
            }
        }
    }

    private fun observePreferences() {
        selectionFlow.value = null
        version++
        resultJob?.cancel()
        preferencesJob?.cancel()
        preferences = null
        preferencesLoaded = false
        preferencesError = null
        val id = userId
        if (id == null) {
            stateFlow.value = UiState.Idle
            return
        }
        stateFlow.value = UiState.Loading
        preferencesJob = viewModelScope.launch {
            try {
                preferencesRepository.observe(id).collect { saved ->
                    currentCoroutineContext().ensureActive()
                    if (authRepository.currentUser.value?.userId == id && userId == id) {
                        require(saved == null || saved.userId == id) { "Preferences do not belong to this account." }
                        if (!preferencesLoaded || preferences != saved) {
                            selectionFlow.value = null
                            version++
                            resultJob?.cancel()
                            preferences = saved
                            preferencesLoaded = true
                            stateFlow.value = if (saved == null) {
                                UiState.Error("Save your exercise preferences before requesting a recommendation.", false)
                            } else UiState.Idle
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (authRepository.currentUser.value?.userId == id && userId == id) {
                    selectionFlow.value = null
                    version++
                    resultJob?.cancel()
                    preferences = null
                    preferencesLoaded = false
                    preferencesError = error.message ?: "Could not load preferences."
                    stateFlow.value = UiState.Error(requireNotNull(preferencesError), true)
                }
            }
        }
    }

    fun generateRecommendation(plan: PlanningInput, weather: WeatherContext?, motion: MotionContext? = null) {
        selectionFlow.value = null
        lastPlan = plan
        lastWeather = weather
        lastMotion = motion
        lastRecommendationId = null
        val id = readyUser() ?: return
        val saved = preferences
        if (saved == null) {
            stateFlow.value = UiState.Error("Save your exercise preferences before requesting a recommendation.", false)
            return
        }
        resultJob?.cancel()
        val requestVersion = ++version
        stateFlow.value = UiState.Loading
        resultJob = viewModelScope.launch {
            try {
                val decision = engine.evaluate(ContextInput(saved, plan, weather, motion, clock.instant()))
                currentCoroutineContext().ensureActive()
                if (!isCurrent(id, requestVersion)) return@launch
                val userStore = store ?: storeForUser(id).also { store = it }
                userStore.save(decision.recommendation).getOrThrow()
                currentCoroutineContext().ensureActive()
                if (isCurrent(id, requestVersion)) stateFlow.value = UiState.Success(decision)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(id, requestVersion)) {
                    stateFlow.value = UiState.Error(error.message ?: "Could not generate or save a recommendation.",
                        error !is IllegalArgumentException)
                }
            } finally {
                if (isCurrent(id, requestVersion) && stateFlow.value == UiState.Loading) stateFlow.value = UiState.Idle
            }
        }
    }

    fun loadRecommendation(recommendationId: String) {
        selectionFlow.value = null
        lastRecommendationId = recommendationId
        lastPlan = null
        lastWeather = null
        lastMotion = null
        val id = readyUser() ?: return
        resultJob?.cancel()
        val requestVersion = ++version
        stateFlow.value = UiState.Loading
        resultJob = viewModelScope.launch {
            try {
                val userStore = store ?: storeForUser(id).also { store = it }
                userStore.observe(recommendationId).collect { recommendation ->
                    currentCoroutineContext().ensureActive()
                    if (isCurrent(id, requestVersion)) {
                        selectionFlow.value = null
                        stateFlow.value = UiState.Success(recommendation?.let { ContextDecision(it, null) })
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(id, requestVersion)) {
                    stateFlow.value = UiState.Error(error.message ?: "Could not load the recommendation.", true)
                }
            } finally {
                if (isCurrent(id, requestVersion) && stateFlow.value == UiState.Loading) stateFlow.value = UiState.Idle
            }
        }
    }

    fun retry() {
        if (!preferencesLoaded || preferencesError != null) {
            observePreferences()
            return
        }
        val recommendationId = lastRecommendationId
        val plan = lastPlan
        if (recommendationId != null) loadRecommendation(recommendationId)
        else if (plan != null) generateRecommendation(plan, lastWeather, lastMotion)
    }

    private fun readyUser(): String? {
        val id = authRepository.currentUser.value?.userId
        if (id == null) {
            stateFlow.value = UiState.Error("Sign in before requesting a recommendation.", false)
            return null
        }
        if (id != userId || !preferencesLoaded) {
            stateFlow.value = UiState.Error(preferencesError ?: "Your preferences are still loading. Please retry.", true)
            return null
        }
        return id
    }

    private fun isCurrent(id: String, requestVersion: Int): Boolean =
        version == requestVersion && userId == id && authRepository.currentUser.value?.userId == id

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val application = context.applicationContext
            val service = MemberAService.get(application)
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(RecommendationViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return RecommendationViewModel(service.authRepository, service.preferencesRepository,
                        SimpleRecommendationEngine(), { id -> DataStoreRecommendationStore(application, id) }) as T
                }
            }
        }
    }
}
