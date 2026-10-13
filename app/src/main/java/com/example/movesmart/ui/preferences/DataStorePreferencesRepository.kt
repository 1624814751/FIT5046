package com.example.movesmart.ui.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.movesmart.shared.Environment
import com.example.movesmart.shared.Intensity
import com.example.movesmart.shared.PreferencesRepository
import com.example.movesmart.shared.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.util.Base64

private val Context.memberAPreferencesDataStore by preferencesDataStore(name = "member_a_preferences")

class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    constructor(context: Context) : this(context.applicationContext.memberAPreferencesDataStore)

    override fun observe(userId: String): Flow<UserPreferences?> {
        require(userId.isNotBlank()) { "A user ID is required." }
        return dataStore.data.map { values -> readUser(values, userId) }.distinctUntilChanged()
    }

    override suspend fun save(preferences: UserPreferences): Result<Unit> {
        return runCatching {
            require(preferences.userId.isNotBlank()) { "A user ID is required." }
            require(preferences.goal.isNotBlank()) { "An exercise goal is required." }
            require(preferences.weeklyGoalMinutes in 1..10080) { "The weekly goal is not valid." }

            val keys = keys(preferences.userId)
            dataStore.edit { values ->
                values[keys.goal] = preferences.goal
                values[keys.intensity] = preferences.preferredIntensity.name
                values[keys.weeklyMinutes] = preferences.weeklyGoalMinutes
                values[keys.activities] = encodeActivities(preferences.activityNames)
                values[keys.environment] = preferences.environment.name
            }
        }
    }

    private fun readUser(values: Preferences, userId: String): UserPreferences? {
        val keys = keys(userId)
        val goal = values[keys.goal] ?: return null
        val intensity = values[keys.intensity] ?: return null
        val weeklyMinutes = values[keys.weeklyMinutes] ?: return null
        val environment = values[keys.environment] ?: return null

        return UserPreferences(
            userId = userId,
            goal = goal,
            preferredIntensity = Intensity.valueOf(intensity),
            weeklyGoalMinutes = weeklyMinutes,
            activityNames = decodeActivities(values[keys.activities].orEmpty()),
            environment = Environment.valueOf(environment)
        )
    }

    private fun keys(userId: String): UserKeys {
        val prefix = "user.$userId."
        return UserKeys(
            goal = stringPreferencesKey(prefix + "goal"),
            intensity = stringPreferencesKey(prefix + "intensity"),
            weeklyMinutes = intPreferencesKey(prefix + "weekly_minutes"),
            activities = stringPreferencesKey(prefix + "activities"),
            environment = stringPreferencesKey(prefix + "environment")
        )
    }

    private data class UserKeys(
        val goal: Preferences.Key<String>,
        val intensity: Preferences.Key<String>,
        val weeklyMinutes: Preferences.Key<Int>,
        val activities: Preferences.Key<String>,
        val environment: Preferences.Key<String>
    )

    private fun encodeActivities(activities: List<String>): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return activities.joinToString(",") { activity ->
            encoder.encodeToString(activity.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun decodeActivities(value: String): List<String> {
        if (value.isBlank()) {
            return emptyList()
        }
        val decoder = Base64.getUrlDecoder()
        return value.split(",").map { saved ->
            String(decoder.decode(saved), StandardCharsets.UTF_8)
        }
    }
}
