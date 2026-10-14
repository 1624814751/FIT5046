package com.example.movesmart

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.movesmart.shared.*
import com.example.movesmart.ui.preferences.PreferencesScreen
import com.example.movesmart.ui.preferences.PreferencesViewModel
import com.example.movesmart.ui.theme.MoveSmartTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PreferencesAccountUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val auth = DraftTestAuth()
    private val repository = DraftTestPreferences()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private var callbackPreferences: List<Any>? = null

    private fun showScreen() {
        compose.activityRule.scenario.onActivity { activity ->
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = PreferencesViewModel(auth, repository) as T
            }
            ViewModelProvider(owner, factory)[PreferencesViewModel::class.java]
            activity.setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MoveSmartTheme {
                        PreferencesScreen("High", listOf("Walking"), "Improve Fitness", 90) { intensity, activities, goal, minutes ->
                            callbackPreferences = listOf(intensity, activities, goal, minutes)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun clearModels() { compose.runOnIdle { owner.viewModelStore.clear() } }

    @Test fun switchingToAnAccountWithoutPreferencesResetsTheDraft() {
        showScreen()
        compose.onNodeWithText("High").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Easy").performClick()
        compose.runOnIdle { auth.currentUser.value = UserAccount("b", "B", "b@example.com") }
        compose.onNodeWithText("Medium").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Jogging").performScrollTo().assertIsNotSelected()
        compose.onNodeWithText("Stay Active").performScrollTo().assertIsSelected()
        compose.onNodeWithText("150").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save Preferences").performScrollTo().performClick()
        compose.waitUntil(5_000) { callbackPreferences != null }
        assertEquals("b", repository.lastSaved?.userId)
        assertEquals(150, repository.lastSaved?.weeklyGoalMinutes)
        assertTrue(repository.lastSaved!!.activityNames.isEmpty())
    }

    @Test fun savingLocksInputsAndReturnsTheSavedSnapshot() {
        repository.saveGate = CompletableDeferred()
        showScreen()
        compose.onNodeWithText("Save Preferences").performScrollTo().performClick()
        compose.waitUntil(5_000) { repository.lastSaved != null }
        compose.onNodeWithText("Easy").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Indoor").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Jogging").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Stay Active").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { repository.saveGate!!.complete(Unit) }
        compose.waitUntil(5_000) { callbackPreferences != null }
        assertEquals(listOf("High", listOf("Jogging"), "Improve Fitness", 210), callbackPreferences)
    }

    @Test fun demoKeepsExistingParametersAndCallbackWithoutPersisting() {
        auth.currentUser.value = null
        showScreen()
        compose.onNodeWithText("High").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Walking").performScrollTo().assertIsSelected()
        compose.onNodeWithText("90").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save Preferences").performScrollTo().performClick()
        compose.waitUntil(5_000) { callbackPreferences != null }
        assertEquals(listOf("High", listOf("Walking"), "Improve Fitness", 90), callbackPreferences)
        assertNull(repository.lastSaved)
    }
}

private class DraftTestAuth : AuthRepository {
    override val currentUser = MutableStateFlow<UserAccount?>(UserAccount("a", "A", "a@example.com"))
    override suspend fun signIn(email: String, password: String) = Result.success(requireNotNull(currentUser.value))
    override suspend fun signUp(displayName: String, email: String, password: String) = signIn(email, password)
    override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
    override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
}

private class DraftTestPreferences : PreferencesRepository {
    private val users = mapOf(
        "a" to MutableStateFlow<UserPreferences?>(UserPreferences("a", "Improve Fitness", Intensity.HIGH,
            210, listOf("Jogging"), Environment.OUTDOOR)),
        "b" to MutableStateFlow<UserPreferences?>(null)
    )
    var saveGate: CompletableDeferred<Unit>? = null
    var lastSaved: UserPreferences? = null
    override fun observe(userId: String): Flow<UserPreferences?> = users.getValue(userId)
    override suspend fun save(preferences: UserPreferences): Result<Unit> {
        lastSaved = preferences
        users.getValue(preferences.userId).value = preferences
        saveGate?.await()
        return Result.success(Unit)
    }
}
