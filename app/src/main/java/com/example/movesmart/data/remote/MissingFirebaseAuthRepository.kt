package com.example.movesmart.data.remote

import com.example.movesmart.shared.AuthRepository
import com.example.movesmart.shared.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MissingFirebaseAuthRepository : AuthRepository {
    override val currentUser: StateFlow<UserAccount?> = MutableStateFlow(null)

    override suspend fun signIn(email: String, password: String): Result<UserAccount> = missingConfig()

    override suspend fun signUp(
        displayName: String,
        email: String,
        password: String
    ): Result<UserAccount> = missingConfig()

    override suspend fun signInWithGoogle(idToken: String): Result<UserAccount> = missingConfig()

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)

    private fun <T> missingConfig(): Result<T> {
        return Result.failure(
            IllegalStateException("Firebase is not configured. Add google-services.json and enable Authentication.")
        )
    }
}
