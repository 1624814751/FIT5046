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
import java.io.File

/** Navigation/state regressions and genuine emulator captures for the A2 report. */
class PrototypeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
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
        scrollTap("New here? Sign up")
        compose.onNodeWithText("Display name").assertExists()
        capture("02-signup")
        back()
        demo()
        compose.onNodeWithText("Find your next move").assertIsDisplayed()
        capture("03-home")
        scrollTap("View workout plan")
        capture("04-workout-plan")
        compose.onNodeWithContentDescription("Simulate rainy weather").performClick()
        compose.onNodeWithText("Indoor Mobility").assertExists()
        scrollTap("Start workout")
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
        scrollTap("Edit preferences")
        capture("11-preferences")
        back()
        scrollTap("Reminder settings · On")
        capture("12-reminders")
        compose.onNode(hasText("Save reminders") and hasClickAction()).performScrollTo()
        capture("13-reminders-form-bottom")
        tap("Save reminders")
    }

    @Test fun disabledReminderSurvivesRecreationAndNavigation() {
        demo()
        compose.onNodeWithContentDescription("Reminder settings").performClick()
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
        compose.onNodeWithContentDescription("Reminder settings").performClick()
        scrollTap("Change")
        compose.onNodeWithText("Reminder time").assertIsDisplayed()
        capture("16-time-input-dark-large-text")
        tap("Cancel")
        compose.onNodeWithText("Reminder time").assertDoesNotExist()
        scrollTap("Save reminders")
        compose.onNodeWithText("Reminder settings saved for this demo.").assertExists()
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
        compose.onNodeWithText("Move well. Feel better.").assertExists()
    }
}
