package com.example.movesmart

import androidx.lifecycle.ViewModelStore
import com.example.movesmart.sensor.LiveWorkoutViewModel
import com.example.movesmart.sensor.MonotonicClock
import com.example.movesmart.sensor.TimerPacer
import com.example.movesmart.sensor.WallClock
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.MotionContext
import com.example.movesmart.shared.MovementBand
import com.example.movesmart.shared.SensorRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveWorkoutViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun movesFromLoadingToActiveAndOnlyHighMovementShowsPrompt() = runTest(mainDispatcherRule.dispatcher) {
        val motion = MutableSharedFlow<MotionContext>()
        val owner = ViewModelStore()
        val model = createModel(repository = repository(motion), owner = owner)
        runCurrent()
        assertTrue(model.state.value.loading)

        motion.emit(sample(MovementBand.MODERATE))
        runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals(MovementBand.MODERATE, model.state.value.latestMotion?.movementBand)
        assertNull(model.state.value.safetyPrompt)

        motion.emit(sample(MovementBand.HIGH, index = 1))
        runCurrent()
        assertEquals(
            "Your movement is high. Consider taking a short break.",
            model.state.value.safetyPrompt
        )
        owner.clear()
    }

    @Test
    fun monotonicTimerExcludesPausedTimeAndResumes() = runTest(mainDispatcherRule.dispatcher) {
        val owner = ViewModelStore()
        val model = createModel(repository = repository(MutableSharedFlow()), owner = owner)
        runCurrent()

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1_000L, model.state.value.elapsedMilliseconds)

        model.togglePause()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1_000L, model.state.value.elapsedMilliseconds)
        assertTrue(model.state.value.paused)

        model.togglePause()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2_000L, model.state.value.elapsedMilliseconds)
        assertFalse(model.state.value.paused)
        owner.clear()
    }

    @Test
    fun pauseAppliesBackPressureToSensorPlayback() = runTest(mainDispatcherRule.dispatcher) {
        val releaseSecondSample = CompletableDeferred<Unit>()
        var secondEmissionReturned = false
        val motion = flow {
            emit(sample(MovementBand.LOW, index = 0))
            releaseSecondSample.await()
            emit(sample(MovementBand.HIGH, index = 1))
            secondEmissionReturned = true
            awaitCancellation()
        }
        val owner = ViewModelStore()
        val model = createModel(repository = repository(motion), owner = owner)
        try {
            runCurrent()
            assertEquals(0L, model.state.value.latestMotion?.sampleIndex)
            model.togglePause()

            releaseSecondSample.complete(Unit)
            runCurrent()
            assertFalse(secondEmissionReturned)
            assertEquals(0L, model.state.value.latestMotion?.sampleIndex)

            model.togglePause()
            runCurrent()
            assertTrue(secondEmissionReturned)
            assertEquals(1L, model.state.value.latestMotion?.sampleIndex)
        } finally {
            owner.clear()
        }
    }

    @Test
    fun playbackFailureIsShownAndRetryStartsANewColdFlow() = runTest(mainDispatcherRule.dispatcher) {
        var attempts = 0
        val repository = object : SensorRepository {
            override fun playback(datasetId: String): Flow<MotionContext> = flow {
                attempts += 1
                if (attempts == 1) error("CSV unavailable")
                emit(sample(MovementBand.LOW))
            }
        }
        val owner = ViewModelStore()
        val model = createModel(repository, owner)
        runCurrent()
        assertEquals("CSV unavailable", model.state.value.playbackError)

        model.retryPlayback()
        runCurrent()
        assertEquals(2, attempts)
        assertNull(model.state.value.playbackError)
        assertNotNull(model.state.value.latestMotion)
        owner.clear()
    }

    @Test
    fun clearingViewModelCancelsPlayback() = runTest(mainDispatcherRule.dispatcher) {
        var cancelled = false
        val repository = object : SensorRepository {
            override fun playback(datasetId: String): Flow<MotionContext> = flow {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        val owner = ViewModelStore()
        createModel(repository, owner)
        runCurrent()

        owner.clear()
        runCurrent()

        assertTrue(cancelled)
    }

    @Test
    fun summaryUsesMeasuredActiveTimeAndOriginalStartInstant() = runTest(mainDispatcherRule.dispatcher) {
        val owner = ViewModelStore()
        val model = createModel(repository(MutableSharedFlow()), owner)
        runCurrent()
        assertEquals(1, model.summary().minutes)

        advanceTimeBy(60_001)
        runCurrent()
        val summary = model.summary()

        assertEquals(2, summary.minutes)
        assertEquals(60_001L, summary.elapsedMilliseconds)
        assertEquals(STARTED_AT, summary.startedAt)
        owner.clear()
    }

    private fun createModel(
        repository: SensorRepository,
        owner: ViewModelStore
    ): LiveWorkoutViewModel {
        val model = LiveWorkoutViewModel(
            repository = repository,
            datasetId = DATASET_ID,
            monotonicClock = MonotonicClock { mainDispatcherRule.dispatcher.scheduler.currentTime },
            wallClock = WallClock { STARTED_AT },
            timerPacer = TimerPacer { milliseconds -> delay(milliseconds) }
        )
        owner.put("live", model)
        return model
    }

    private fun repository(flow: Flow<MotionContext>) = object : SensorRepository {
        override fun playback(datasetId: String): Flow<MotionContext> {
            assertEquals(DATASET_ID, datasetId)
            return flow
        }
    }

    private fun sample(band: MovementBand, index: Long = 0) = MotionContext(
        datasetId = DATASET_ID,
        sampleIndex = index,
        elapsedMilliseconds = index * 20,
        accelerometer = listOf(0.1, 0.2, 0.9),
        gyroscope = listOf(0.01, 0.02, 0.03),
        accelerometerUnit = "g",
        gyroscopeUnit = "rad/s",
        activityLabel = "WALKING",
        movementBand = band,
        source = DataSource.DEMO
    )

    companion object {
        private const val DATASET_ID = "test-dataset"
        private val STARTED_AT = Instant.parse("2026-10-14T06:00:00Z")
    }
}
