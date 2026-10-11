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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class PreferencesUiState(
    val isLoading: Boolean = false,
    val preferences: UserPreferences? = null,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)

class PreferencesViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    val currentUser: StateFlow<UserAccount?> = authRepository.currentUser

    private val stateFlow = MutableStateFlow(PreferencesUiState())
    val state: StateFlow<PreferencesUiState> = stateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { account ->
                if (account == null) {
                    stateFlow.value = PreferencesUiState()
                    return@collectLatest
                }
                stateFlow.value = PreferencesUiState(isLoading = true)
                preferencesRepository.observe(account.userId)
                    .catch { error ->
                        stateFlow.value = PreferencesUiState(
                            errorMessage = error.message ?: "Could not load preferences."
                        )
                    }
                    .collect { preferences ->
                        stateFlow.value = PreferencesUiState(preferences = preferences)
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
        if (stateFlow.value.isLoading) {
            return
        }
        val account = authRepository.currentUser.value
        if (account == null) {
            stateFlow.value = PreferencesUiState(errorMessage = "Sign in before saving preferences.")
            return
        }
        val preferences = makePreferences(
            userId = account.userId,
            intensity = intensity,
            activities = activities,
            goal = goal,
            weeklyMinutes = weeklyMinutes,
            environment = environment
        ).getOrElse { error ->
            stateFlow.value = PreferencesUiState(errorMessage = error.message ?: "Preferences are not valid.")
            return
        }

        stateFlow.value = stateFlow.value.copy(isLoading = true, errorMessage = null, isSaved = false)
        viewModelScope.launch {
            val result = preferencesRepository.save(preferences)
            stateFlow.value = if (result.isSuccess) {
                PreferencesUiState(preferences = preferences, isSaved = true)
            } else {
                PreferencesUiState(
                    preferences = preferences,
                    errorMessage = result.exceptionOrNull()?.message ?: "Could not save preferences."
                )
            }
        }
    }

    fun clearSaved() {
        stateFlow.value = stateFlow.value.copy(isSaved = false)
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
