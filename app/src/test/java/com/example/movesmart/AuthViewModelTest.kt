package com.example.movesmart

import android.app.Activity
import androidx.lifecycle.ViewModelStore
import com.example.movesmart.data.remote.GoogleLoginCancelledException
import com.example.movesmart.data.remote.GoogleLoginProvider
import com.example.movesmart.data.remote.SignupProfileException
import com.example.movesmart.shared.AuthRepository
import com.example.movesmart.shared.UiState
import com.example.movesmart.shared.UserAccount
import com.example.movesmart.ui.auth.AuthViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test fun externalLogoutClearsOldSuccess() = runTest {
        val repository = FakeAuthRepository()
        val model = AuthViewModel(repository, FakeGoogleLoginProvider())
        val owner = ViewModelStore().apply { put("auth", model) }
        model.login("alex@example.com", "password")
        runCurrent()
        assertTrue(model.state.value is UiState.Success)
        repository.setUser(null)
        runCurrent()
        assertEquals(UiState.Idle, model.state.value)
        owner.clear()
    }

    @Test fun delayedLoginCannotPublishSuccessForAnotherSession() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository().apply { loginGate = gate; publishLogin = false }
        val model = AuthViewModel(repository, FakeGoogleLoginProvider())
        val owner = ViewModelStore().apply { put("auth", model) }
        model.login("alex@example.com", "password")
        runCurrent()
        repository.setUser(UserAccount("user-b", "B", "b@example.com"))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals("user-b", model.currentUser.value?.userId)
        assertEquals(UiState.Idle, model.state.value)
        owner.clear()
    }

    @Test fun delayedFailureIsIgnoredAfterSessionChangesBack() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository().apply {
            loginGate = gate
            loginResult = Result.failure(IllegalStateException("Old login failure"))
        }
        val model = AuthViewModel(repository, FakeGoogleLoginProvider())
        val owner = ViewModelStore().apply { put("auth", model) }
        model.login("alex@example.com", "password")
        runCurrent()
        repository.setUser(UserAccount("user-b", "B", "b@example.com"))
        runCurrent()
        repository.setUser(null)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(UiState.Idle, model.state.value)
        owner.clear()
    }

    @Test fun credentialCleanupCannotNavigateOutOfANewSession() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository()
        val provider = FakeGoogleLoginProvider().apply { clearGate = gate }
        val model = AuthViewModel(repository, provider)
        val owner = ViewModelStore().apply { put("auth", model) }
        model.login("alex@example.com", "password")
        runCurrent()
        var navigated = false
        model.logout { navigated = true }
        runCurrent()
        repository.setUser(UserAccount("user-b", "B", "b@example.com"))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertFalse(navigated)
        assertEquals("user-b", model.currentUser.value?.userId)
        assertFalse(model.state.value is UiState.Error)
        owner.clear()
    }

    @Test fun coroutineCancellationDoesNotBecomeAuthenticationError() = runTest {
        val repository = FakeAuthRepository().apply { loginResult = Result.failure(CancellationException()) }
        val model = AuthViewModel(repository, FakeGoogleLoginProvider())
        val owner = ViewModelStore().apply { put("auth", model) }
        model.login("alex@example.com", "password")
        runCurrent()
        assertEquals(UiState.Idle, model.state.value)
        owner.clear()
    }

    @Test fun partialSignupFailureExplainsThatAccountAlreadyExists() = runTest {
        val repository = FakeAuthRepository().apply { signupFailure = SignupProfileException(IllegalStateException("Offline")) }
        val model = AuthViewModel(repository, FakeGoogleLoginProvider())
        val owner = ViewModelStore().apply { put("auth", model) }
        model.signup("Alex", "alex@example.com", "long-password", "long-password")
        runCurrent()
        assertNull(model.currentUser.value)
        assertTrue((model.state.value as UiState.Error).message.contains("Your account was created"))
        owner.clear()
    }

    @Test fun validationCannotUnlockAnUnfinishedLogin() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository().apply { loginGate = gate }
        val model = AuthViewModel(repository, FakeGoogleLoginProvider())
        val owner = ViewModelStore().apply { put("auth", model) }
        model.login("alex@example.com", "password")
        runCurrent()
        model.login("", "")
        assertEquals(UiState.Loading, model.state.value)
        gate.complete(Unit)
        runCurrent()
        assertTrue(model.state.value is UiState.Success)
        owner.clear()
    }

    @Test
    fun loginErrorIsShown() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAuthRepository().apply {
            loginResult = Result.failure(IllegalStateException("Wrong email or password."))
        }
        val viewModel = AuthViewModel(repository, FakeGoogleLoginProvider())

        viewModel.login("alex@example.com", "password")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Error)
        assertEquals("Wrong email or password.", (state as UiState.Error).message)
    }

    @Test
    fun successfulLoginUpdatesCurrentUser() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository, FakeGoogleLoginProvider())

        viewModel.login("alex@example.com", "password")
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Success)
        assertEquals("user-a", viewModel.currentUser.value?.userId)
    }

    @Test
    fun successfulSignupUpdatesCurrentUser() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = AuthViewModel(FakeAuthRepository(), FakeGoogleLoginProvider())

        viewModel.signup("Alex", "alex@example.com", "long-password", "long-password")
        advanceUntilIdle()

        assertEquals("Alex", viewModel.currentUser.value?.displayName)
        assertTrue(viewModel.state.value is UiState.Success)
    }

    @Test
    fun logoutClearsCurrentUser() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAuthRepository()
        val googleProvider = FakeGoogleLoginProvider()
        val viewModel = AuthViewModel(repository, googleProvider)
        viewModel.login("alex@example.com", "password")
        advanceUntilIdle()
        var signedOut = false

        viewModel.logout { signedOut = true }
        advanceUntilIdle()

        assertNull(viewModel.currentUser.value)
        assertTrue(signedOut)
        assertEquals(1, repository.signOutCalls)
        assertEquals(1, googleProvider.clearCalls)
        assertEquals(UiState.Idle, viewModel.state.value)
    }

    @Test
    fun logoutErrorKeepsUserAndEndsLoading() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAuthRepository().apply {
            signOutResult = Result.failure(IllegalStateException("Could not sign out."))
        }
        val viewModel = AuthViewModel(repository, FakeGoogleLoginProvider())
        viewModel.login("alex@example.com", "password")
        advanceUntilIdle()
        var signedOut = false

        viewModel.logout { signedOut = true }
        advanceUntilIdle()

        assertEquals("user-a", viewModel.currentUser.value?.userId)
        assertFalse(signedOut)
        assertTrue(viewModel.state.value is UiState.Error)
        assertEquals("Could not sign out.", (viewModel.state.value as UiState.Error).message)
    }

    @Test
    fun userCanLoginAgainAfterLogout() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = AuthViewModel(FakeAuthRepository(), FakeGoogleLoginProvider())
        viewModel.login("alex@example.com", "password")
        advanceUntilIdle()
        viewModel.logout()
        advanceUntilIdle()

        viewModel.login("alex@example.com", "password")
        advanceUntilIdle()

        assertEquals("user-a", viewModel.currentUser.value?.userId)
        assertTrue(viewModel.state.value is UiState.Success)
    }

    @Test
    fun repeatedLogoutRequestRunsOnlyOnce() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository, FakeGoogleLoginProvider())
        viewModel.login("alex@example.com", "password")
        advanceUntilIdle()

        viewModel.logout()
        viewModel.logout()
        advanceUntilIdle()

        assertEquals(1, repository.signOutCalls)
    }

    @Test
    fun credentialClearErrorStillFinishesSecureLogout() = runTest(mainDispatcherRule.dispatcher) {
        val provider = FakeGoogleLoginProvider(
            clearResult = Result.failure(IllegalStateException("Credential clear failed."))
        )
        val viewModel = AuthViewModel(FakeAuthRepository(), provider)
        viewModel.login("alex@example.com", "password")
        advanceUntilIdle()
        var signedOut = false

        viewModel.logout { signedOut = true }
        advanceUntilIdle()

        assertNull(viewModel.currentUser.value)
        assertTrue(signedOut)
        assertTrue(viewModel.state.value is UiState.Error)
        assertEquals(
            "You are signed out, but the Google sign-in session could not be cleared.",
            (viewModel.state.value as UiState.Error).message
        )
    }

    @Test
    fun googleCancellationClearsLoadingState() = runTest(mainDispatcherRule.dispatcher) {
        val provider = FakeGoogleLoginProvider(
            Result.failure(GoogleLoginCancelledException())
        )
        val viewModel = AuthViewModel(FakeAuthRepository(), provider)

        viewModel.loginWithGoogle(Activity())
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Error)
        assertEquals("Google login was cancelled.", (viewModel.state.value as UiState.Error).message)
    }
}

private class FakeGoogleLoginProvider(
    private val result: Result<String> = Result.success("test-token"),
    private val clearResult: Result<Unit> = Result.success(Unit)
) : GoogleLoginProvider {
    var clearCalls = 0
    var clearGate: CompletableDeferred<Unit>? = null

    override suspend fun getIdToken(activity: Activity): Result<String> = result
    override suspend fun clear(): Result<Unit> {
        clearCalls += 1
        clearGate?.await()
        return clearResult
    }
}

private class FakeAuthRepository : AuthRepository {
    private val userFlow = MutableStateFlow<UserAccount?>(null)
    override val currentUser: StateFlow<UserAccount?> = userFlow
    var loginResult: Result<UserAccount> = Result.success(
        UserAccount("user-a", "Alex", "alex@example.com")
    )
    var signOutResult: Result<Unit> = Result.success(Unit)
    var signOutCalls = 0
    var loginGate: CompletableDeferred<Unit>? = null
    var publishLogin = true
    var signupFailure: Exception? = null
    fun setUser(account: UserAccount?) { userFlow.value = account }

    override suspend fun signIn(email: String, password: String): Result<UserAccount> {
        loginGate?.await()
        if (publishLogin) loginResult.getOrNull()?.let { userFlow.value = it }
        return loginResult
    }

    override suspend fun signUp(
        displayName: String,
        email: String,
        password: String
    ): Result<UserAccount> {
        val account = UserAccount("user-a", displayName, email)
        userFlow.value = account
        signupFailure?.let {
            yield()
            userFlow.value = null
            return Result.failure(it)
        }
        return Result.success(account)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<UserAccount> {
        return signIn("alex@example.com", "google")
    }

    override suspend fun signOut(): Result<Unit> {
        signOutCalls += 1
        if (signOutResult.isSuccess) {
            userFlow.value = null
        }
        return signOutResult
    }
}
