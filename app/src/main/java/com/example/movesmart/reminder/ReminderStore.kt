package com.example.movesmart.reminder

import android.content.Context
import android.content.SharedPreferences
import com.example.movesmart.shared.ReminderRepository
import com.example.movesmart.shared.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Small per-user settings store. Workout records remain Member C's Room responsibility. */
class ReminderStore(context: Context) : ReminderRepository {
    private val prefs = context.applicationContext.getSharedPreferences("workout_reminders", Context.MODE_PRIVATE)
    private fun key(userId: String) = "settings:$userId"

    fun read(userId: String): ReminderSettings? = prefs.getString(key(userId), null)?.let { raw ->
        runCatching {
            val json = JSONObject(raw)
            val days = json.getJSONArray("days")
            ReminderSettings(userId, json.getBoolean("enabled"), json.getString("label"),
                json.getInt("hour"), json.getInt("minute"),
                (0 until days.length()).map { days.getInt(it) }.toSet(),
                json.getBoolean("sound"), json.getString("zoneId"))
                .also { require(it.validationError() == null) }
        }.getOrNull()
    }

    override fun observe(userId: String): Flow<ReminderSettings?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key(userId) || changed == null) trySend(read(userId))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(userId))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override suspend fun save(settings: ReminderSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.validationError() == null) { settings.validationError().orEmpty() }
            val json = JSONObject().apply {
                put("enabled", settings.enabled); put("label", settings.label.trim())
                put("hour", settings.hour); put("minute", settings.minute)
                put("days", JSONArray(settings.days.sorted())); put("sound", settings.sound)
                put("zoneId", settings.zoneId)
            }
            check(prefs.edit().putString(key(settings.userId), json.toString()).commit()) { "Could not save reminder settings." }
        }
    }

    var activeUser: String?
        get() = prefs.getString("active_user", null)
        set(value) { check(prefs.edit().putString("active_user", value).commit()) { "Could not save reminder session." } }

    fun scheduledAt(userId: String): Long = prefs.getLong("scheduled:$userId", 0)
    fun pendingDelivery(userId: String): Long = prefs.getLong("pending_delivery:$userId", 0)
    fun setPendingDelivery(userId: String, millis: Long) {
        check(prefs.edit().putLong("pending_delivery:$userId", millis).commit()) { "Could not save pending reminder." }
    }
    fun setScheduledAt(userId: String, millis: Long) {
        check(prefs.edit().putLong("scheduled:$userId", millis).commit()) { "Could not save reminder schedule." }
    }
}
