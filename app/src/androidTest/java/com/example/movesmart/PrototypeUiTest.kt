package com.example.movesmart

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.movesmart.navigation.MoveSmartApp
import com.example.movesmart.ui.theme.MoveSmartTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import kotlinx.coroutines.runBlocking
import com.example.movesmart.reminder.ReminderService
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.ViewModelStore
import com.example.movesmart.data.remote.GoogleLoginProvider
import com.example.movesmart.shared.*
import com.example.movesmart.ui.auth.AuthViewModel
import com.example.movesmart.ui.preferences.PreferencesViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Navigation/state regressions and genuine emulator captures for the A2 report. */
class PrototypeUiTest {
    @get:Rule val compose = createAndroidComposeRule<PrototypeTestActivity>()
    private val accountModels = ViewModelStore()
    @After fun clearAccountModels() { compose.runOnIdle { accountModels.clear() } }
    @Before fun clearPersistedReminders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { ReminderService.get(context).logout().getOrThrow() }
        context.getSharedPreferences("workout_reminders", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun tap(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun scrollTap(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun signInFixture() {
        compose.runOnIdle { runBlocking { compose.activity.testAuth.signIn("test@example.com", "test-only").getOrThrow() } }
    }
    private fun back() = compose.onNodeWithContentDescription("Back").performClick()

    private fun startSensorPlayback() {
        compose.onNodeWithContentDescription("Search").performClick()
        val start = compose.onAllNodesWithText("Start sample workout")[0]
        start.performScrollTo()
        compose.waitForIdle()
        // Member C's continuous CSV stream needs the same manual clock as its
        // dedicated sensor integration test; it is no longer a static A2 timer.
        compose.mainClock.autoAdvance = false
        start.performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Source: UCI HAPT public dataset · Demo playback").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun finishSensorPlayback() {
        val repository = compose.activity.workoutRepository
        val previousCount = runBlocking { repository.observe("test-authenticated-user").first().size }
        tap("Finish workout")
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(hasText("Save session") and hasAnyAncestor(isDialog())).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(15_000) { runBlocking { repository.observe("test-authenticated-user").first().size == previousCount + 1 } }
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // UI assertions synchronize composition; allow the emulator compositor to present the frame.
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "a2-screenshots").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun allMajorScreensAndReturnPaths() {
        capture("01-login")
        scrollTap("Don't have an account? Sign Up")
        compose.onNodeWithText("Name").assertExists()
        capture("02-signup")
        back()
        signInFixture()
        compose.onNodeWithText("Hi, Test User").assertIsDisplayed()
        capture("03-home")
        scrollTap("Find an activity")
        capture("04-find-activity")
        compose.onNodeWithText("Load weather").assertExists()
        compose.onNodeWithText("Show activity details").assertIsNotEnabled()
        back()
        startSensorPlayback()
        capture("05-live-workout")
        finishSensorPlayback()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Every session counts").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Edit").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Every session counts").assertIsDisplayed()
        android.os.SystemClock.sleep(4500) // Let the save-feedback snackbar finish before report capture.
        capture("06-history")
        tap("Add workout")
        capture("07-add-workout")
        back()
        compose.onAllNodes(hasText("Edit") and hasClickAction()).onFirst().performScrollTo().performClick()
        capture("14-edit-workout")
        back()
        compose.onNodeWithContentDescription("Search").performClick()
        capture("08-search")
        back()
        // Two Statistics buttons exist in History; choose the navigation item.
        compose.onNode(hasText("Statistics") and isSelectable()).performClick()
        compose.onNodeWithText("Your week in motion").assertIsDisplayed()
        capture("09-statistics")
        tap("Profile")
        capture("10-profile")
        scrollTap("Edit Preferences")
        capture("11-preferences")
        back()
        scrollTap("Reminder settings · Off")
        capture("12-reminders")
        compose.onNode(hasText("Save reminders") and hasClickAction()).performScrollTo()
        capture("13-reminders-form-bottom")
        tap("Save reminders")
    }

    @Test fun disabledReminderSurvivesRecreationAndNavigation() {
        signInFixture()
        compose.onNodeWithContentDescription("Reminder settings").performClick()
        compose.onNodeWithContentDescription("Workout reminders").performClick()
        compose.onNodeWithContentDescription("Workout reminders").performClick()
        scrollTap("Save reminders")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Workout reminders").assertIsOff()
        back()
        tap("Profile")
        compose.onNodeWithText("Reminder settings · Off").assertExists()
    }

    @Test fun reminderRejectsAnEmptyRepeatSchedule() {
        signInFixture()
        compose.onNodeWithContentDescription("Reminder settings").performClick()
        compose.onNodeWithContentDescription("Workout reminders").performScrollTo().performClick()
        listOf("Monday", "Wednesday", "Friday").forEach { day ->
            compose.onNodeWithContentDescription(day).performScrollTo().performClick()
        }
        scrollTap("Save reminders")
        compose.onNodeWithText("Choose at least one repeat day.").assertIsDisplayed()
    }

    @Test fun darkThemeAndLargeTextKeepReminderControlsReachable() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    MoveSmartTheme(darkTheme = true) { MoveSmartApp(workoutRepository = activity.workoutRepository) }
                }
            }
        }
        signInFixture()
        tap("Statistics")
        compose.onNodeWithText("Your week in motion").assertExists()
        capture("15-statistics-dark-large-text")
        tap("Home")
        capture("18-home-dark-large-text")
        compose.onNodeWithContentDescription("Reminder settings").performClick()
        compose.onNodeWithContentDescription("Workout reminders").performScrollTo().performClick()
        scrollTap("Change")
        compose.onNodeWithText("Reminder time").assertIsDisplayed()
        capture("16-time-input-dark-large-text")
        tap("Cancel")
        compose.onNodeWithText("Reminder time").assertDoesNotExist()
        compose.onNodeWithContentDescription("Workout reminders").performScrollTo().performClick()
        scrollTap("Save reminders")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Reminders are off. Scheduled notifications cancelled.").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun searchEmptyStateAndLogoutReset() {
        signInFixture()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Search activities or history").performTextInput("zzzzzz")
        compose.onNodeWithText("No activities found. Try walking, cycling or yoga.").assertExists()
        compose.onNodeWithContentDescription("Clear search").performClick()
        back()
        tap("Profile")
        scrollTap("Log out")
        // Confirmation dialog is the only node carrying this exact text inside a dialog.
        compose.onNode(hasText("Log out") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Move smarter. Stay active.").assertExists()
    }

    @Test fun contextSelectionSurvivesRecreationAndUpdatesHistoryAndStatistics() {
        signInFixture()
        tap("Statistics")
        val today = java.time.LocalDate.now()
        // This Activity owns a fresh test file; no production records or A2 fixtures.
        val saved = runBlocking {
            compose.activity.workoutRepository.observe("test-authenticated-user").first()
        }.map { com.example.movesmart.model.WorkoutRecord(it.id, it.title, it.minutes, it.date) }
        val baseline = com.example.movesmart.model.weekSummary(saved, today).totalMinutes
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Weekly goal: $baseline of 150 minutes").fetchSemanticsNodes().isNotEmpty() }
        tap("Home")
        scrollTap("Find an activity")
        compose.onNodeWithText("Available time").performScrollTo().performClick()
        tap("15 min")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("15 min").assertExists()
        compose.onNodeWithText("Show activity details").assertIsNotEnabled()
        back()
        startSensorPlayback()
        compose.onNodeWithText("Source: UCI HAPT public dataset · Demo playback").assertExists()
        finishSensorPlayback()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Every session counts").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Edit").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Outdoor Walk").assertExists()
        compose.onNode(hasText("Statistics") and isSelectable()).performClick()
        val measured = runBlocking { compose.activity.workoutRepository.observe("test-authenticated-user").first().single() }
        assertEquals(DataSource.DEMO, measured.source)
        assertTrue(measured.startedAt != null && measured.minutes >= 1)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Weekly goal: ${baseline + measured.minutes} of 150 minutes").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun memberAOnboardingPreferencesReachBAndD() {
        val auth = OnboardingTestAuth()
        val preferences = OnboardingTestPreferences()
        val google = object : GoogleLoginProvider {
            override suspend fun getIdToken(activity: android.app.Activity) =
                Result.failure<String>(UnsupportedOperationException("Not used in email onboarding"))
            override suspend fun clear() = Result.success(Unit)
        }
        compose.activityRule.scenario.onActivity { activity ->
            val authModel = AuthViewModel(auth, google)
            val preferencesModel = PreferencesViewModel(auth, preferences)
            accountModels.put("auth", authModel)
            accountModels.put("preferences", preferencesModel)
            activity.setContent {
                MoveSmartTheme {
                    MoveSmartApp(workoutRepository = activity.workoutRepository,
                        authViewModelOverride = authModel, preferencesViewModelOverride = preferencesModel)
                }
            }
        }
        scrollTap("Don't have an account? Sign Up")
        compose.onNodeWithText("Name").performTextInput("Integration Tester")
        compose.onNodeWithText("Email").performTextInput("tester@example.com")
        compose.onNodeWithText("Password", substring = false).performScrollTo().performTextInput("short")
        compose.onNodeWithText("Confirm Password").performScrollTo().performTextInput("short")
        scrollTap("Create Account")
        compose.onNodeWithText("Use a memorable passphrase of at least 12 characters.").assertExists()
        compose.onNodeWithText("Password", substring = false).performScrollTo().performTextReplacement("demo passphrase 123")
        compose.onNodeWithText("Confirm Password").performScrollTo().performTextReplacement("demo passphrase 123")
        scrollTap("Create Account")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Exercise Preferences").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Find an activity").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Exercise Preferences").fetchSemanticsNodes().isEmpty()) {
            scrollTap("Preferences")
        }
        compose.onNodeWithText("Exercise Preferences").assertExists()
        scrollTap("High")
        compose.onNodeWithText("Weekly movement goal (minutes)").performScrollTo().performClick()
        tap("180")
        scrollTap("Save Preferences")
        compose.waitUntil(5_000) { preferences.saved.value?.weeklyGoalMinutes == 180 }
        assertEquals("onboarding-test-user", preferences.saved.value?.userId)
        assertEquals(Intensity.HIGH, preferences.saved.value?.preferredIntensity)
        scrollTap("Find an activity")
        compose.onNodeWithText("High").assertExists()
        compose.onNodeWithText("Load weather").assertExists()
        // Real weather delivery is validated separately; this test remains offline.
        compose.onNodeWithText("Show activity details").assertIsNotEnabled()
        tap("Profile")
        compose.onNodeWithText("Integration Tester").assertExists()
        compose.onNodeWithText("tester@example.com").assertExists()
        scrollTap("View statistics")
        compose.onNodeWithText("Your week in motion").assertExists()
        compose.onNodeWithContentDescription("Weekly goal: 0 of 180 minutes").assertExists()
        assertTrue(runBlocking { compose.activity.workoutRepository.observe("test-authenticated-user").first() }.isEmpty())
        assertTrue(runBlocking { compose.activity.workoutRepository.observe("onboarding-test-user").first() }.isEmpty())
    }

}

private class OnboardingTestAuth : AuthRepository {
    override val currentUser = MutableStateFlow<UserAccount?>(null)
    override suspend fun signUp(displayName: String, email: String, password: String): Result<UserAccount> {
        val account = UserAccount("onboarding-test-user", displayName, email)
        currentUser.value = account
        return Result.success(account)
    }
    override suspend fun signIn(email: String, password: String) =
        Result.failure<UserAccount>(UnsupportedOperationException("Use signup in this fixture"))
    override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
    override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
}

private class OnboardingTestPreferences : PreferencesRepository {
    val saved = MutableStateFlow<UserPreferences?>(null)
    override fun observe(userId: String) = saved
    override suspend fun save(preferences: UserPreferences): Result<Unit> {
        saved.value = preferences
        return Result.success(Unit)
    }
}
