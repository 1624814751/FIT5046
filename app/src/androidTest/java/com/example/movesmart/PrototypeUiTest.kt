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

/** Navigation/state regressions and genuine emulator captures for the A2 report. */
class PrototypeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun clearPersistedReminders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { ReminderService.get(context).logout().getOrThrow() }
        context.getSharedPreferences("workout_reminders", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun tap(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun scrollTap(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun demo() = scrollTap("Explore demo")
    private fun back() = compose.onNodeWithContentDescription("Back").performClick()

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
        demo()
        compose.onNodeWithText("You have time.\nLet's make it count.").assertIsDisplayed()
        capture("03-home")
        scrollTap("Find an activity")
        capture("04-find-activity")
        compose.onNodeWithContentDescription("Simulate rainy weather").performClick()
        scrollTap("See recommendation")
        compose.onNodeWithText("Indoor Mobility").assertExists()
        capture("17-activity-details")
        scrollTap("Start activity")
        capture("05-live-workout")
        scrollTap("Finish workout")
        tap("Save session")
        compose.onNodeWithText("Every session counts").assertIsDisplayed()
        android.os.SystemClock.sleep(4500) // Let the save-feedback snackbar finish before report capture.
        capture("06-history")
        tap("Add workout")
        capture("07-add-workout")
        back()
        compose.onAllNodes(hasText("Edit") and hasClickAction()).onFirst().performClick()
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
        demo()
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
        demo()
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
                    MoveSmartTheme(darkTheme = true) { MoveSmartApp() }
                }
            }
        }
        demo()
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
        demo()
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
        demo()
        tap("Statistics")
        val today = java.time.LocalDate.now()
        val baseline = com.example.movesmart.model.weekSummary(com.example.movesmart.model.sampleRecords(today), today).totalMinutes
        compose.onNodeWithContentDescription("Weekly goal: $baseline of 150 minutes").assertExists()
        tap("Home")
        scrollTap("Find an activity")
        compose.onNodeWithText("Available time").performScrollTo().performClick()
        tap("15 min")
        compose.onNodeWithContentDescription("Simulate rainy weather").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("15 min").assertExists()
        compose.onNodeWithContentDescription("Simulate rainy weather").assertIsOn()
        scrollTap("See recommendation")
        compose.onNodeWithText("Indoor Mobility").assertExists()
        compose.onNodeWithText("15 min · Medium intensity · Chadstone").assertExists()
        scrollTap("Start activity")
        compose.onNodeWithText("15:00").assertExists()
        scrollTap("Finish workout")
        tap("Save session")
        compose.onNodeWithText("Indoor Mobility").assertExists()
        compose.onNode(hasText("Statistics") and isSelectable()).performClick()
        compose.onNodeWithContentDescription("Weekly goal: ${baseline + 15} of 150 minutes").assertExists()
    }

    @Test fun memberAOnboardingPreferencesReachBAndD() {
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
        compose.onNodeWithText("Exercise Preferences").assertExists()
        scrollTap("High")
        compose.onNodeWithText("Weekly movement goal (minutes)").performScrollTo().performClick()
        tap("180")
        scrollTap("Save Preferences")
        scrollTap("Find an activity")
        compose.onNodeWithText("High").assertExists()
        scrollTap("See recommendation")
        compose.onNodeWithText("Outdoor Jogging").assertExists()
        tap("Profile")
        compose.onNodeWithText("Integration Tester").assertExists()
        compose.onNodeWithText("tester@example.com").assertExists()
        scrollTap("View statistics")
        compose.onNodeWithText(" / 180 min").assertExists()
    }

}
