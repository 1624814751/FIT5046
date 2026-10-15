package com.example.movesmart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.movesmart.sensor.LiveWorkoutState
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.MotionContext
import com.example.movesmart.shared.MovementBand
import com.example.movesmart.ui.screens.LiveActivityScreen
import com.example.movesmart.ui.theme.MoveSmartTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveActivityScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsUciSensorValuesAndUsesMeasuredTimeForFinish() {
        var state by mutableStateOf(
            LiveWorkoutState(
                loading = false,
                elapsedMilliseconds = 65_000,
                latestMotion = sample(MovementBand.HIGH),
                safetyPrompt = "Your movement is high. Consider taking a short break."
            )
        )
        var finishCalls = 0
        compose.setContent {
            MoveSmartTheme {
                LiveActivityScreen(
                    activity = "Outdoor Walk",
                    plannedMinutes = 30,
                    state = state,
                    onTogglePause = { state = state.copy(paused = !state.paused) },
                    onRetryPlayback = {},
                    onFinish = { finishCalls += 1 }
                )
            }
        }

        compose.onNodeWithText("01:05").assertIsDisplayed()
        compose.onNodeWithText("Dataset activity: WALKING").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Movement: HIGH").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Accelerometer (g): 0.100, 0.200, 0.900").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gyroscope (rad/s): 0.010, 0.020, 0.030").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Source: UCI HAPT public dataset · Demo playback").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your movement is high. Consider taking a short break.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("112 bpm").assertDoesNotExist()

        compose.onNodeWithText("Pause workout").performScrollTo().performClick()
        compose.onNodeWithText("Resume workout").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Finish workout").performScrollTo().performClick()
        compose.onNodeWithText("Save this session?").assertIsDisplayed()
        compose.onNodeWithText("Add this 2-minute measured session to History and Statistics.").assertIsDisplayed()
        compose.onNode(hasText("Save session") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, finishCalls)
    }

    @Test
    fun playbackErrorExplainsFailureAndOffersRetry() {
        var retries = 0
        compose.setContent {
            MoveSmartTheme {
                LiveActivityScreen(
                    activity = "Indoor Cycling",
                    plannedMinutes = 20,
                    state = LiveWorkoutState(
                        loading = false,
                        playbackError = "Sensor CSV could not be read."
                    ),
                    onTogglePause = {},
                    onRetryPlayback = { retries += 1 },
                    onFinish = {}
                )
            }
        }

        compose.onNodeWithText("Sensor CSV could not be read.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Retry sensor playback").performScrollTo().performClick()
        assertEquals(1, retries)
        compose.onNodeWithText("112 bpm").assertDoesNotExist()
    }

    private fun sample(band: MovementBand) = MotionContext(
        datasetId = "uci-hapt-exp01-user01-subset-v1",
        sampleIndex = 50,
        elapsedMilliseconds = 1_000,
        accelerometer = listOf(0.1, 0.2, 0.9),
        gyroscope = listOf(0.01, 0.02, 0.03),
        accelerometerUnit = "g",
        gyroscopeUnit = "rad/s",
        activityLabel = "WALKING",
        movementBand = band,
        source = DataSource.DEMO
    )
}
