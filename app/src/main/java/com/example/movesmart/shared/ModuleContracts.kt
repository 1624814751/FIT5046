package com.example.movesmart.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// Member A implements these interfaces. Never save passwords in shared models or logs.
interface AuthRepository {
    val currentUser: StateFlow<UserAccount?>
    suspend fun signIn(email: String, password: String): Result<UserAccount>
    suspend fun signUp(displayName: String, email: String, password: String): Result<UserAccount>
    suspend fun signInWithGoogle(idToken: String): Result<UserAccount>
    suspend fun signOut(): Result<Unit>
}

interface PreferencesRepository {
    fun observe(userId: String): Flow<UserPreferences?>
    suspend fun save(preferences: UserPreferences): Result<Unit>
}

interface WeatherRepository {
    fun observe(query: WeatherQuery): Flow<WeatherContext?>
    suspend fun refresh(query: WeatherQuery): Result<WeatherContext>
}

interface RecommendationEngine {
    fun evaluate(input: ContextInput): ContextDecision
}

// Recommendations may be cached in memory for this phase; workouts must be persisted later.
interface RecommendationStore {
    fun observe(id: String): Flow<Recommendation?>
    suspend fun save(recommendation: Recommendation): Result<Unit>
}

// Member C implements these interfaces. Every query must be scoped to the signed-in user.
interface WorkoutRepository {
    fun observe(userId: String): Flow<List<WorkoutRecord>>
    suspend fun get(userId: String, id: Int): Result<WorkoutRecord?>
    suspend fun upsert(record: WorkoutRecord): Result<Int>
    suspend fun delete(userId: String, id: Int): Result<Unit>
}

interface SensorRepository {
    // Cold Flow: collection starts playback; cancellation stops it.
    fun playback(datasetId: String): Flow<MotionContext>
}

// Member D implements these interfaces. Saving a preference is not proof an alarm was set.
interface ReminderRepository {
    fun observe(userId: String): Flow<ReminderSettings?>
    suspend fun save(settings: ReminderSettings): Result<Unit>
}

interface ReminderScheduler {
    suspend fun schedule(settings: ReminderSettings): Result<Unit>
    suspend fun cancel(userId: String): Result<Unit>
}
