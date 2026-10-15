package com.example.movesmart

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.example.movesmart.data.remote.GoogleLoginProvider
import com.example.movesmart.navigation.MoveSmartApp
import com.example.movesmart.shared.*
import com.example.movesmart.ui.auth.AuthViewModel
import com.example.movesmart.ui.preferences.PreferencesViewModel
import com.example.movesmart.ui.theme.MoveSmartTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real CSV player and isolated Room database; never changes the real Firebase account. */
class SensorMergeIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<PrototypeTestActivity>()
    private val models = ViewModelStore()
    @After fun clearModels() { compose.runOnIdle { models.clear() } }
    private fun tap(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (compose.mainClock.autoAdvance) node.performScrollTo()
        node.performClick()
    }
    @Test fun authenticatedReplayPauseFinishHistoryStatisticsAndAccountIsolation() {
        val auth = object : AuthRepository {
            override val currentUser = MutableStateFlow<UserAccount?>(UserAccount("firebase-a", "Alice", "a@example.com"))
            override suspend fun signIn(email: String, password: String) = Result.failure<UserAccount>(UnsupportedOperationException())
            override suspend fun signUp(displayName: String, email: String, password: String) = signIn(email, password)
            override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
            override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
        }
        val google = object : GoogleLoginProvider {
            override suspend fun getIdToken(activity: android.app.Activity) = Result.failure<String>(UnsupportedOperationException())
            override suspend fun clear() = Result.success(Unit)
        }
        val prefs = object : PreferencesRepository {
            override fun observe(userId: String) = MutableStateFlow<UserPreferences?>(UserPreferences(userId, "Stay active", Intensity.MEDIUM, 150, emptyList(), Environment.ANY))
            override suspend fun save(preferences: UserPreferences) = Result.success(Unit)
        }
        compose.activityRule.scenario.onActivity { activity ->
            val authVm = AuthViewModel(auth, google)
            val prefsVm = PreferencesViewModel(auth, prefs)
            models.put("auth", authVm); models.put("preferences", prefsVm)
            activity.setContent { MoveSmartTheme { MoveSmartApp(
                workoutRepository = activity.workoutRepository,
                authViewModelOverride = authVm, preferencesViewModelOverride = prefsVm) } }
        }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onAllNodesWithText("Start sample workout")[0].performScrollTo()
        compose.waitForIdle()
        // Continuous 50 Hz playback never settles under automatic clock advancement.
        compose.mainClock.autoAdvance = false
        compose.onAllNodesWithText("Start sample workout")[0].performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Source: UCI HAPT public dataset · Demo playback").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(1000)
        tap("Pause workout")
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Paused · Active time").assertExists()
        tap("Resume workout")
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Elapsed active time").assertExists()
        tap("Finish workout")
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(hasText("Save session") and hasAnyAncestor(isDialog())).performClick()
        compose.mainClock.advanceTimeBy(500)
        val repository = compose.activity.workoutRepository
        compose.waitUntil(15_000) { runBlocking { repository.observe("firebase-a").first().size == 1 } }
        val record = runBlocking { repository.observe("firebase-a").first().single() }
        assertEquals(DataSource.DEMO, record.source)
        assertNotNull(record.startedAt)
        assertTrue(record.minutes >= 1)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Every session counts").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Statistics") and isSelectable()).performClick()
        compose.onNodeWithContentDescription("Weekly goal: ${record.minutes} of 150 minutes").assertExists()
        compose.runOnIdle { auth.currentUser.value = UserAccount("firebase-b", "Bob", "b@example.com") }
        compose.onNode(hasText("History") and isSelectable()).performClick()
        compose.onNodeWithText("No workouts yet").assertExists()
        compose.onNode(hasText("Statistics") and isSelectable()).performClick()
        compose.onNodeWithContentDescription("Weekly goal: 0 of 150 minutes").assertExists()
        assertTrue(runBlocking { repository.observe("firebase-b").first().isEmpty() })
        compose.runOnIdle { auth.currentUser.value = null }
        compose.onNodeWithText("Continue with Google").assertExists()
        compose.onNodeWithText("Explore demo").assertDoesNotExist()
        assertEquals(listOf(record), runBlocking { repository.observe("firebase-a").first() })
    }
}
