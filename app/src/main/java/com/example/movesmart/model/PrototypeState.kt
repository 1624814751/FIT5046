package com.example.movesmart.model

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A2 UI state survives recreation; it is not a database, account or notification scheduler. */
@Stable
class PrototypeState(val today: LocalDate = LocalDate.now()) {
    var records by mutableStateOf(sampleRecords(today))
    var reminder by mutableStateOf(ReminderSettings())
    var preferences by mutableStateOf(Preferences())
    var displayName by mutableStateOf("Alex")
    var email by mutableStateOf("alex@example.com")
    var activePlannedMinutes by mutableStateOf(30)
    var activeIntensity by mutableStateOf("Medium")
    var activeWorkout by mutableStateOf("Outdoor Walk")

    fun saveRecord(record: WorkoutRecord) {
        records = if (records.any { it.id == record.id }) records.map { if (it.id == record.id) record else it }
        else records + record
    }
    fun nextId(): Int = (records.maxOfOrNull { it.id } ?: 0) + 1

    companion object {
        val StateSaver = Saver<PrototypeState, String>(
            save = { state ->
                JSONObject().apply {
                    put("today", state.today.toString())
                    put("records", JSONArray().apply {
                        state.records.forEach { record -> put(JSONObject().apply {
                            put("id", record.id); put("title", record.title)
                            put("minutes", record.minutes); put("date", record.date.toString())
                        }) }
                    })
                    put("reminder", JSONObject().apply {
                        put("enabled", state.reminder.enabled); put("label", state.reminder.label)
                        put("hour", state.reminder.hour); put("minute", state.reminder.minute)
                        put("days", JSONArray(state.reminder.days.sorted())); put("sound", state.reminder.sound)
                    })
                    put("goal", state.preferences.goal); put("intensity", state.preferences.intensity)
                    put("weeklyMinutes", state.preferences.weeklyMinutes)
                    put("activities", JSONArray(state.preferences.activities))
                    put("displayName", state.displayName); put("email", state.email)
                    put("activePlannedMinutes", state.activePlannedMinutes); put("activeIntensity", state.activeIntensity)
                    put("activeWorkout", state.activeWorkout)
                }.toString()
            },
            restore = { encoded ->
                val json = JSONObject(encoded)
                PrototypeState(LocalDate.parse(json.getString("today"))).apply {
                    val rows = json.getJSONArray("records")
                    records = (0 until rows.length()).map { index -> rows.getJSONObject(index).let {
                        WorkoutRecord(it.getInt("id"), it.getString("title"), it.getInt("minutes"), LocalDate.parse(it.getString("date")))
                    } }
                    val settings = json.getJSONObject("reminder")
                    val days = settings.getJSONArray("days")
                    reminder = ReminderSettings(settings.getBoolean("enabled"), settings.getString("label"), settings.getInt("hour"), settings.getInt("minute"),
                        (0 until days.length()).map { days.getInt(it) }.toSet(), settings.getBoolean("sound"))
                    val activities = json.optJSONArray("activities")
                    preferences = Preferences(json.getString("goal"), json.getString("intensity"), json.getInt("weeklyMinutes"),
                        activities?.let { (0 until it.length()).map(it::getString) } ?: Preferences().activities)
                    displayName = json.optString("displayName", "Alex"); email = json.optString("email", "alex@example.com")
                    activePlannedMinutes = json.optInt("activePlannedMinutes", 30); activeIntensity = json.optString("activeIntensity", "Medium")
                    activeWorkout = json.getString("activeWorkout")
                }
            }
        )
    }
}
