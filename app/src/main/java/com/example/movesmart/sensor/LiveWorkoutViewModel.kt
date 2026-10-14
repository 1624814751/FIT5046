package com.example.movesmart.sensor

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movesmart.shared.MotionContext
import com.example.movesmart.shared.MovementBand
import com.example.movesmart.shared.SensorRepository
import java.time.Instant
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LiveWorkoutState(
    val loading: Boolean = true,
    val paused: Boolean = false,
    val elapsedMilliseconds: Long = 0,
    val latestMotion: MotionContext? = null,
    val safetyPrompt: String? = null,
    val playbackError: String? = null
)

data class LiveWorkoutSummary(
    val minutes: Int,
    val elapsedMilliseconds: Long,
    val startedAt: Instant
)

fun interface MonotonicClock {
    fun nowMilliseconds(): Long
}

fun interface WallClock {
    fun now(): Instant
}

fun interface TimerPacer {
    suspend fun wait(milliseconds: Long)
}

class LiveWorkoutViewModel(
    private val repository: SensorRepository,
    private val datasetId: String,
    private val monotonicClock: MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() },
    wallClock: WallClock = WallClock { Instant.now() },
    private val timerPacer: TimerPacer = TimerPacer { milliseconds -> delay(milliseconds) }
) : ViewModel() {
    private val _state = MutableStateFlow(LiveWorkoutState())
    val state: StateFlow<LiveWorkoutState> = _state.asStateFlow()

    private val playbackAllowed = MutableStateFlow(true)
    private val startedAt = wallClock.now()
    private var lastMonotonicMilliseconds = monotonicClock.nowMilliseconds()
    private var accumulatedActiveMilliseconds = 0L
    private var playbackJob: Job? = null

    init {
        startPlayback()
        viewModelScope.launch {
            while (isActive) {
                timerPacer.wait(TIMER_TICK_MILLISECONDS)
                updateElapsedToNow()
            }
        }
    }

    fun togglePause() {
        updateElapsedToNow()
        val paused = !_state.value.paused
        playbackAllowed.value = !paused
        _state.update { current -> current.copy(paused = paused) }
    }

    fun retryPlayback() {
        playbackJob?.cancel()
        _state.update { current ->
            current.copy(
                loading = true,
                latestMotion = null,
                safetyPrompt = null,
                playbackError = null
            )
        }
        startPlayback()
    }

    fun summary(): LiveWorkoutSummary {
        updateElapsedToNow()
        val elapsed = _state.value.elapsedMilliseconds
        val minutes = ceil(elapsed / MILLISECONDS_PER_MINUTE.toDouble())
            .toInt()
            .coerceIn(MINIMUM_SAVED_MINUTES, MAXIMUM_SAVED_MINUTES)
        return LiveWorkoutSummary(
            minutes = minutes,
            elapsedMilliseconds = elapsed,
            startedAt = startedAt
        )
    }

    private fun startPlayback() {
        playbackJob = viewModelScope.launch {
            try {
                repository.playback(datasetId).collect { motion ->
                    playbackAllowed.filter { allowed -> allowed }.first()
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            latestMotion = motion,
                            safetyPrompt = if (motion.movementBand == MovementBand.HIGH) {
                                HIGH_MOVEMENT_PROMPT
                            } else {
                                null
                            },
                            playbackError = null
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.update { current ->
                    current.copy(
                        loading = false,
                        playbackError = error.message ?: "Sensor playback failed."
                    )
                }
            }
        }
    }

    private fun updateElapsedToNow() {
        val now = monotonicClock.nowMilliseconds()
        val delta = (now - lastMonotonicMilliseconds).coerceAtLeast(0)
        if (!_state.value.paused) {
            accumulatedActiveMilliseconds += delta
        }
        lastMonotonicMilliseconds = now
        _state.update { current ->
            current.copy(elapsedMilliseconds = accumulatedActiveMilliseconds)
        }
    }

    companion object {
        const val HIGH_MOVEMENT_PROMPT =
            "Your movement is high. Consider taking a short break."

        private const val TIMER_TICK_MILLISECONDS = 250L
        private const val MILLISECONDS_PER_MINUTE = 60_000L
        private const val MINIMUM_SAVED_MINUTES = 1
        private const val MAXIMUM_SAVED_MINUTES = 1_440
    }
}
