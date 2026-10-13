package com.example.movesmart.ui.auth

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movesmart.data.remote.GoogleLoginProvider
import com.example.movesmart.shared.AuthRepository
import com.example.movesmart.shared.UiState
import com.example.movesmart.shared.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun login(email: String, password: String) {
        val error = checkLogin(email, password)
        if (error != null) {
            stateFlow.value = UiState.Error(error, retryable = false)
            return
        }
        startLogin { authRepository.signIn(email.trim(), password) }
    }

    fun signup(name: String, email: String, password: String, confirmPassword: String) {
        val error = checkSignup(name, email, password, confirmPassword)
        if (error != null) {
            stateFlow.value = UiState.Error(error, retryable = false)
            return
        }
        startLogin { authRepository.signUp(name.trim(), email.trim(), password) }
    }

    fun loginWithGoogle(activity: Activity) {
        if (stateFlow.value == UiState.Loading) {
            return
        }
        stateFlow.value = UiState.Loading
        viewModelScope.launch {
            val tokenResult = googleLoginProvider.getIdToken(activity)
            val token = tokenResult.getOrElse { error ->
                stateFlow.value = UiState.Error(error.message ?: "Google login failed.", retryable = true)
                return@launch
            }
            setResult(authRepository.signInWithGoogle(token), "Google login failed.")
        }
    }

    fun logout(onSignedOut: () -> Unit = {}) {
        if (stateFlow.value == UiState.Loading) {
            return
        }
        stateFlow.value = UiState.Loading
        viewModelScope.launch {
            val result = runCatching {
                authRepository.signOut().getOrThrow()
            }
            if (result.isSuccess) {
                val clearResult = runCatching {
                    googleLoginProvider.clear().getOrThrow()
                }
                stateFlow.value = if (clearResult.isSuccess) {
                    UiState.Idle
                } else {
                    UiState.Error(
                        "You are signed out, but the Google sign-in session could not be cleared.",
                        retryable = true
                    )
                }
                onSignedOut()
            } else {
                stateFlow.value = UiState.Error(
                    result.exceptionOrNull()?.message ?: "Sign out failed.",
                    retryable = true
                )
            }
        }
    }

    fun clearError() {
        if (stateFlow.value is UiState.Error) {
            stateFlow.value = UiState.Idle
        }
    }

    private fun startLogin(request: suspend () -> Result<UserAccount>) {
        if (stateFlow.value == UiState.Loading) {
            return
        }
        stateFlow.value = UiState.Loading
        viewModelScope.launch {
            setResult(request(), "Login failed.")
        }
    }

    private fun setResult(result: Result<UserAccount>, defaultMessage: String) {
        val account = result.getOrNull()
        stateFlow.value = if (account != null) {
            UiState.Success(account)
        } else {
            UiState.Error(result.exceptionOrNull()?.message ?: defaultMessage, retryable = true)
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
