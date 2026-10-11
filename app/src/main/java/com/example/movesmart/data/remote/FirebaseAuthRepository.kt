package com.example.movesmart.data.remote

import com.example.movesmart.shared.AuthRepository
import com.example.movesmart.shared.UserAccount
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {
    private val userState = MutableStateFlow(firebaseAuth.currentUser?.toUserAccount())

    override val currentUser: StateFlow<UserAccount?> = userState.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        userState.value = auth.currentUser?.toUserAccount()
    }

    init {
        firebaseAuth.addAuthStateListener(authListener)
    }

    override suspend fun signIn(email: String, password: String): Result<UserAccount> {
        return runCatching {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = requireNotNull(result.user) { "Firebase did not return a user." }
            user.toUserAccount().also { userState.value = it }
        }
    }

    override suspend fun signUp(displayName: String, email: String, password: String): Result<UserAccount> {
        return runCatching {
            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = requireNotNull(result.user) { "Firebase did not return a user." }
            val profile = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build()
            user.updateProfile(profile).await()
            user.reload().await()
            val account = firebaseAuth.currentUser?.toUserAccount() ?: user.toUserAccount(displayName.trim())
            userState.value = account
            account
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<UserAccount> {
        return runCatching {
            require(idToken.isNotBlank()) { "Google did not return an ID token." }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = requireNotNull(result.user) { "Firebase did not return a user." }
            user.toUserAccount().also { userState.value = it }
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return runCatching {
            firebaseAuth.signOut()
            userState.value = null
        }
    }

    private fun FirebaseUser.toUserAccount(fallbackName: String = "MoveSmart user"): UserAccount {
        return UserAccount(
            userId = uid,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
            email = email.orEmpty()
        )
    }

}

private suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(task.result as T)
            } else {
                val error = task.exception ?: IllegalStateException("Firebase request failed.")
                continuation.resumeWithException(error)
            }
        }
    }
}
