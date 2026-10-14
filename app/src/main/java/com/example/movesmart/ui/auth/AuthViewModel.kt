package com.example.movesmart.ui.auth

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movesmart.data.remote.GoogleLoginProvider
import com.example.movesmart.data.remote.SignupProfileException
import com.example.movesmart.shared.AuthRepository
import com.example.movesmart.shared.UiState
import com.example.movesmart.shared.UserAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Authentication: Manage login and logout state
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val googleLoginProvider: GoogleLoginProvider
) : ViewModel() {
    private val startState: UiState<UserAccount> = authRepository.currentUser.value?.let {
        UiState.Success(it)
    } ?: UiState.Idle

    private val stateFlow = MutableStateFlow(startState)
    val state: StateFlow<UiState<UserAccount>> = stateFlow.asStateFlow()
    val currentUser: StateFlow<UserAccount?> = authRepository.currentUser
    private var sessionVersion = 0
    private var lastUserId = currentUser.value?.userId
    private var operationJob: Job? = null

    init {
        viewModelScope.launch {
            currentUser.collect { account ->
                if (lastUserId != account?.userId) {
                    sessionVersion++
                    lastUserId = account?.userId
                }
                val success = stateFlow.value as? UiState.Success
                if (success != null) {
                    stateFlow.value = if (success.data.userId == account?.userId) {
                        UiState.Success(account)
                    } else UiState.Idle
                }
            }
        }
    }

    fun login(email: String, password: String) {
        if (operationJob?.isActive == true) return
        val error = checkLogin(email, password)
        if (error != null) {
            stateFlow.value = UiState.Error(error, retryable = false)
            return
        }
        startLogin { authRepository.signIn(email.trim(), password) }
    }

    fun signup(name: String, email: String, password: String, confirmPassword: String) {
        if (operationJob?.isActive == true) return
        val error = checkSignup(name, email, password, confirmPassword)
        if (error != null) {
            stateFlow.value = UiState.Error(error, retryable = false)
            return
        }
        startLogin { authRepository.signUp(name.trim(), email.trim(), password) }
    }

    fun loginWithGoogle(activity: Activity) {
        val userId = currentUser.value?.userId
        val version = sessionVersion
        startLogin {
            val token = googleLoginProvider.getIdToken(activity).getOrThrow()
            currentCoroutineContext().ensureActive()
            if (version != sessionVersion || currentUser.value?.userId != userId) {
                throw CancellationException("The account changed during Google login.")
            }
            authRepository.signInWithGoogle(token)
        }
    }

    fun logout(onSignedOut: () -> Unit = {}) {
        if (operationJob?.isActive == true) {
            return
        }
        stateFlow.value = UiState.Loading
        val version = sessionVersion
        val userId = currentUser.value?.userId
        operationJob = viewModelScope.launch {
            try {
                authRepository.signOut().getOrThrow()
                currentCoroutineContext().ensureActive()
                if (currentUser.value != null || sessionVersion > version + 1) return@launch
                var clearFailed = false
                try {
                    googleLoginProvider.clear().getOrThrow()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    clearFailed = true
                }
                currentCoroutineContext().ensureActive()
                if (currentUser.value != null || sessionVersion > version + 1) return@launch
                stateFlow.value = if (clearFailed) {
                    UiState.Error(
                        "You are signed out, but the Google sign-in session could not be cleared.",
                        retryable = true
                    )
                } else UiState.Idle
                onSignedOut()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (currentUser.value?.userId == userId && sessionVersion == version) {
                    stateFlow.value = UiState.Error(error.message ?: "Sign out failed.", retryable = true)
                }
            } finally {
                if (stateFlow.value == UiState.Loading) stateFlow.value = UiState.Idle
            }
        }
    }

    fun clearError() {
        if (stateFlow.value is UiState.Error) {
            stateFlow.value = UiState.Idle
        }
    }

    private fun startLogin(request: suspend () -> Result<UserAccount>) {
        if (operationJob?.isActive == true) {
            return
        }
        stateFlow.value = UiState.Loading
        val version = sessionVersion
        val userId = currentUser.value?.userId
        operationJob = viewModelScope.launch {
            try {
                val account = request().getOrThrow()
                currentCoroutineContext().ensureActive()
                if (currentUser.value?.userId == account.userId && sessionVersion <= version + 1) {
                    stateFlow.value = UiState.Success(requireNotNull(currentUser.value))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val signupProfileFailed = error is SignupProfileException && currentUser.value == null && userId == null
                if (signupProfileFailed || (currentUser.value?.userId == userId && sessionVersion == version)) {
                    stateFlow.value = UiState.Error(error.message ?: "Login failed.", retryable = true)
                }
            } finally {
                if (stateFlow.value == UiState.Loading) stateFlow.value = UiState.Idle
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val service = MemberAService.get(context)
            return factory(service.authRepository, service.googleLoginProvider)
        }

        fun factory(
            authRepository: AuthRepository,
            googleLoginProvider: GoogleLoginProvider
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(authRepository, googleLoginProvider) as T
                }
            }
        }
    }
}
