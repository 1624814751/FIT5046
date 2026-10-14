package com.example.movesmart.data.remote

import com.example.movesmart.shared.AuthRepository
import com.example.movesmart.shared.UserAccount
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Authentication: Manage Firebase accounts
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
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = requireNotNull(result.user) { "Firebase did not return a user." }
            Result.success(currentAccount(user))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun signUp(displayName: String, email: String, password: String): Result<UserAccount> {
        val user = try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            requireNotNull(result.user) { "Firebase did not return a user." }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return Result.failure(error)
        }
        return try {
            currentAccount(user)
            val profile = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build()
            user.updateProfile(profile).await()
            user.reload().await()
            Result.success(currentAccount(user))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (firebaseAuth.currentUser?.uid == user.uid) {
                firebaseAuth.signOut()
                userState.value = null
            }
            Result.failure(SignupProfileException(error))
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<UserAccount> {
        return try {
            require(idToken.isNotBlank()) { "Google did not return an ID token." }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = requireNotNull(result.user) { "Firebase did not return a user." }
            Result.success(currentAccount(user))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return runCatching {
            firebaseAuth.signOut()
            userState.value = null
        }
    }

    private fun currentAccount(user: FirebaseUser): UserAccount {
        val current = firebaseAuth.currentUser
        check(current != null && current.uid == user.uid) { "The authentication session changed. Please log in again." }
        return current.toUserAccount().also { userState.value = it }
    }

    private fun FirebaseUser.toUserAccount(fallbackName: String = "MoveSmart user"): UserAccount {
        return UserAccount(
            userId = uid,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
            email = email.orEmpty()
        )
    }

}

class SignupProfileException(cause: Exception) : IllegalStateException(
    "Your account was created, but profile setup could not finish. Please log in instead of signing up again.", cause
)

private suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
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
