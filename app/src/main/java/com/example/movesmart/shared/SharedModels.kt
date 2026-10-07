package com.example.movesmart.shared

import java.time.Instant
import java.time.LocalDate

// Phase 1 contracts only. These types do not implement authentication, storage or sensing.
enum class Intensity { LOW, MEDIUM, HIGH }
enum class Environment { INDOOR, OUTDOOR, ANY }
enum class MovementBand { UNKNOWN, LOW, MODERATE, HIGH }
enum class DataSource { DEMO, LIVE, CACHE }

data class UserAccount(val userId: String, val displayName: String, val email: String)

data class UserPreferences(
    val userId: String,
    val goal: String,
    val preferredIntensity: Intensity,
    val weeklyGoalMinutes: Int,
    val activityNames: List<String>,
    val environment: Environment
)

data class GeoLocation(val label: String, val latitude: Double, val longitude: Double)

data class PlanningInput(
    val date: LocalDate,
    val availableMinutes: Int,
    val intensity: Intensity,
    val environment: Environment,
    val location: GeoLocation
)

data class WeatherQuery(val location: GeoLocation, val date: LocalDate)

data class WeatherContext(
    val query: WeatherQuery,
    val temperatureC: Double,
    val rainProbabilityPercent: Int?,
    val windSpeedKmh: Double?,
    val fetchedAt: Instant,
    val source: DataSource,
    val isStale: Boolean
)

// Values retain the selected dataset's documented units, not assumed SI units.
// Accelerometer / gyroscope values are not a heart-rate measurement.
data class MotionContext(
    val datasetId: String,
    val sampleIndex: Long,
    val elapsedMilliseconds: Long,
    val accelerometer: List<Double>,
    val gyroscope: List<Double>,
    val accelerometerUnit: String,
    val gyroscopeUnit: String,
    val activityLabel: String?,
    val movementBand: MovementBand,
    val source: DataSource
)

data class Recommendation(
    val id: String,
    val activityName: String,
    val minutes: Int,
    val intensity: Intensity,
    val environment: Environment,
    val plannedDate: LocalDate,
    val location: GeoLocation,
    val reasons: List<String>
)

data class ContextInput(
    val preferences: UserPreferences,
    val plan: PlanningInput,
    val weather: WeatherContext?,
    val motion: MotionContext?,
    val evaluatedAt: Instant
)

data class ContextDecision(val recommendation: Recommendation, val safetyPrompt: String?)

// id=0 means not yet inserted. Room allocates a positive Int identifier.
data class WorkoutRecord(
    val id: Int,
    val userId: String,
    val title: String,
    val minutes: Int,
    val date: LocalDate,
    val startedAt: Instant?,
    val source: DataSource
)

// Days use ISO numbering: Monday=1, Sunday=7. zoneId is an IANA identifier.
data class ReminderSettings(
    val userId: String,
    val enabled: Boolean,
    val label: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val sound: Boolean,
    val zoneId: String
)

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val retryable: Boolean) : UiState<Nothing>
}
