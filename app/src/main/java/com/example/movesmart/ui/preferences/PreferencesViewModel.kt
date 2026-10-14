package com.example.movesmart.ui.preferences

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movesmart.shared.AuthRepository
import com.example.movesmart.shared.PreferencesRepository
import com.example.movesmart.shared.UserAccount
import com.example.movesmart.shared.UserPreferences
import com.example.movesmart.ui.auth.MemberAService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PreferencesUiState(
    val isLoading: Boolean = false,
    val preferences: UserPreferences? = null,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val isSaving: Boolean = false,
    val userId: String? = null,
    val savedPreferences: UserPreferences? = null
)

// Preferences: Manage account preference state
class PreferencesViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    val currentUser: StateFlow<UserAccount?> = authRepository.currentUser

    private val stateFlow = MutableStateFlow(PreferencesUiState(
        isLoading = currentUser.value != null, userId = currentUser.value?.userId
    ))
    val state: StateFlow<PreferencesUiState> = stateFlow.asStateFlow()
    private var sessionVersion = 0
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            currentUser.map { it?.userId }.distinctUntilChanged().collectLatest { userId ->
                sessionVersion++
                saveJob?.cancel()
                if (userId == null) {
                    stateFlow.value = PreferencesUiState()
                    return@collectLatest
                }
                val version = sessionVersion
                stateFlow.value = PreferencesUiState(isLoading = true, userId = userId)
                try {
                    preferencesRepository.observe(userId).collect { preferences ->
                        currentCoroutineContext().ensureActive()
                        if (version == sessionVersion && currentUser.value?.userId == userId) {
                            require(preferences == null || preferences.userId == userId) {
                                "Preferences do not belong to this account."
                            }
                            stateFlow.value = stateFlow.value.copy(isLoading = false, preferences = preferences)
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (version == sessionVersion && currentUser.value?.userId == userId) {
                        stateFlow.value = stateFlow.value.copy(isLoading = false,
                            errorMessage = error.message ?: "Could not load preferences.")
                    }
                }
            }
        }
    }

    fun save(
        intensity: String,
        activities: List<String>,
        goal: String,
        weeklyMinutes: Int,
        environment: String
    ) {
        if (stateFlow.value.isLoading || stateFlow.value.isSaving) {
            return
        }
        val account = authRepository.currentUser.value
        if (account == null) {
            stateFlow.value = PreferencesUiState(errorMessage = "Sign in before saving preferences.")
            return
        }
        if (stateFlow.value.userId != account.userId) return
        val preferences = makePreferences(
            userId = account.userId,
            intensity = intensity,
            activities = activities,
            goal = goal,
            weeklyMinutes = weeklyMinutes,
            environment = environment
        ).getOrElse { error ->
            stateFlow.value = stateFlow.value.copy(errorMessage = error.message ?: "Preferences are not valid.")
            return
        }

        val version = sessionVersion
        stateFlow.value = stateFlow.value.copy(isSaving = true, errorMessage = null,
            isSaved = false, savedPreferences = null)
        saveJob = viewModelScope.launch {
            try {
                preferencesRepository.save(preferences).getOrThrow()
                currentCoroutineContext().ensureActive()
                if (version == sessionVersion && currentUser.value?.userId == account.userId) {
                    stateFlow.value = stateFlow.value.copy(preferences = preferences,
                        isSaved = true, savedPreferences = preferences)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (version == sessionVersion && currentUser.value?.userId == account.userId) {
                    stateFlow.value = stateFlow.value.copy(
                        errorMessage = error.message ?: "Could not save preferences.")
                }
            } finally {
                if (version == sessionVersion && currentUser.value?.userId == account.userId) {
                    stateFlow.value = stateFlow.value.copy(isSaving = false)
                }
            }
        }
    }

    fun clearSaved() {
        stateFlow.value = stateFlow.value.copy(isSaved = false, savedPreferences = null)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val service = MemberAService.get(context)
            return factory(service.authRepository, service.preferencesRepository)
        }

        fun factory(
            authRepository: AuthRepository,
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PreferencesViewModel(authRepository, preferencesRepository) as T
                }
            }
        }
    }
}
