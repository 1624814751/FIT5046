package com.example.movesmart

import androidx.lifecycle.ViewModelStore
import com.example.movesmart.shared.*
import com.example.movesmart.ui.preferences.PreferencesViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun lateSaveCannotReplaceAnotherAccountsPreferences() = runTest {
        val auth = PreferenceTestAuth()
        val repository = PreferenceTestRepository()
        val model = PreferencesViewModel(auth, repository)
        val owner = ViewModelStore().apply { put("preferences", model) }
        runCurrent()
        model.save("Easy", listOf("Walking"), "Stay Active", 90, "Outdoor")
        runCurrent()
        assertTrue(model.state.value.isSaving)
        auth.currentUser.value = UserAccount("b", "B", "b@example.com")
        repository.values.value = preferenceFor("b", 210)
        runCurrent()
        repository.saveResult.complete(Result.success(Unit))
        runCurrent()
        assertEquals("b", model.state.value.preferences?.userId)
        assertEquals(210, model.state.value.preferences?.weeklyGoalMinutes)
        assertFalse(model.state.value.isSaved)
        assertNull(model.state.value.savedPreferences)
        owner.clear()
    }

    @Test fun logoutAndReturnToSameUidRejectsPreviousSessionSave() = runTest {
        val auth = PreferenceTestAuth()
        val repository = PreferenceTestRepository()
        val model = PreferencesViewModel(auth, repository)
        val owner = ViewModelStore().apply { put("preferences", model) }
        runCurrent()
        model.save("Easy", emptyList(), "Stay Active", 90, "Outdoor")
        runCurrent()
        auth.currentUser.value = null
        runCurrent()
        assertNull(model.state.value.preferences)
        auth.currentUser.value = UserAccount("a", "A", "a@example.com")
        runCurrent()
        repository.saveResult.complete(Result.success(Unit))
        runCurrent()
        assertEquals(150, model.state.value.preferences?.weeklyGoalMinutes)
        assertFalse(model.state.value.isSaved)
        owner.clear()
    }

    @Test fun observationDoesNotUnlockSavingOrEraseSavedSnapshot() = runTest {
        val repository = PreferenceTestRepository()
        val model = PreferencesViewModel(PreferenceTestAuth(), repository)
        val owner = ViewModelStore().apply { put("preferences", model) }
        runCurrent()
        model.save("Easy", listOf("Walking"), "Stay Active", 90, "Outdoor")
        runCurrent()
        repository.values.value = preferenceFor("a", 90)
        runCurrent()
        assertTrue(model.state.value.isSaving)
        model.save("High", emptyList(), "Stay Active", 210, "Indoor")
        runCurrent()
        assertEquals(1, repository.saveCalls)
        repository.saveResult.complete(Result.success(Unit))
        runCurrent()
        repository.values.value = preferenceFor("a", 180)
        runCurrent()
        assertTrue(model.state.value.isSaved)
        assertEquals(90, model.state.value.savedPreferences?.weeklyGoalMinutes)
        assertEquals(180, model.state.value.preferences?.weeklyGoalMinutes)
        model.clearSaved()
        assertFalse(model.state.value.isSaved)
        assertNull(model.state.value.savedPreferences)
        owner.clear()
    }

    @Test fun failedSaveKeepsLastPersistedPreferences() = runTest {
        val repository = PreferenceTestRepository()
        val model = PreferencesViewModel(PreferenceTestAuth(), repository)
        val owner = ViewModelStore().apply { put("preferences", model) }
        runCurrent()
        model.save("High", emptyList(), "Stay Active", 210, "Indoor")
        runCurrent()
        repository.saveResult.complete(Result.failure(IllegalStateException("Storage unavailable")))
        runCurrent()
        assertEquals(150, model.state.value.preferences?.weeklyGoalMinutes)
        assertEquals("Storage unavailable", model.state.value.errorMessage)
        assertFalse(model.state.value.isSaving)
        assertFalse(model.state.value.isSaved)
        owner.clear()
    }

    @Test fun cancelledSaveDoesNotBecomeErrorOrSuccess() = runTest {
        val repository = PreferenceTestRepository()
        val model = PreferencesViewModel(PreferenceTestAuth(), repository)
        val owner = ViewModelStore().apply { put("preferences", model) }
        runCurrent()
        model.save("Easy", emptyList(), "Stay Active", 90, "Outdoor")
        runCurrent()
        repository.saveResult.complete(Result.failure(CancellationException()))
        runCurrent()
        assertNull(model.state.value.errorMessage)
        assertFalse(model.state.value.isSaved)
        assertFalse(model.state.value.isSaving)
        owner.clear()
    }

    @Test fun loadingFailureDoesNotStopNextAccountLoading() = runTest {
        val auth = PreferenceTestAuth()
        val repository = object : PreferencesRepository {
            override fun observe(userId: String): Flow<UserPreferences?> = flow {
                if (userId == "a") error("Cannot read preferences")
                emit(preferenceFor(userId, 210))
            }
            override suspend fun save(preferences: UserPreferences) = Result.success(Unit)
        }
        val model = PreferencesViewModel(auth, repository)
        val owner = ViewModelStore().apply { put("preferences", model) }
        runCurrent()
        assertEquals("Cannot read preferences", model.state.value.errorMessage)
        auth.currentUser.value = UserAccount("b", "B", "b@example.com")
        runCurrent()
        assertEquals("b", model.state.value.preferences?.userId)
        assertNull(model.state.value.errorMessage)
        owner.clear()
    }
}

private fun preferenceFor(id: String, minutes: Int) =
    UserPreferences(id, "Stay Active", Intensity.LOW, minutes, listOf("Walking"), Environment.OUTDOOR)

private class PreferenceTestRepository : PreferencesRepository {
    val values = MutableStateFlow<UserPreferences?>(preferenceFor("a", 150))
    val saveResult = CompletableDeferred<Result<Unit>>()
    var saveCalls = 0
    override fun observe(userId: String): Flow<UserPreferences?> = values
    override suspend fun save(preferences: UserPreferences): Result<Unit> {
        saveCalls++
        return withContext(NonCancellable) { saveResult.await() }
    }
}

private class PreferenceTestAuth : AuthRepository {
    override val currentUser = MutableStateFlow<UserAccount?>(UserAccount("a", "A", "a@example.com"))
    override suspend fun signIn(email: String, password: String) = Result.success(requireNotNull(currentUser.value))
    override suspend fun signUp(displayName: String, email: String, password: String) = signIn(email, password)
    override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
    override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
}
