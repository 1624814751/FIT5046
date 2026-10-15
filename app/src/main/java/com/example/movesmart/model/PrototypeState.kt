package com.example.movesmart.model

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Transient prototype UI state only. Workout records live in Room, not the saved-state bundle. */
@Stable
class PrototypeState(val today: LocalDate = LocalDate.now()) {
    var reminder by mutableStateOf(ReminderSettings())
    var preferences by mutableStateOf(Preferences())
    var activePlannedMinutes by mutableStateOf(30)
    var activeIntensity by mutableStateOf("Medium")
    var activeWorkout by mutableStateOf("Outdoor Walk")

    companion object {
        val StateSaver = Saver<PrototypeState, String>(
            save = { state ->
                JSONObject().apply {
                    put("today", state.today.toString())
                    put("reminder", JSONObject().apply {
                        put("enabled", state.reminder.enabled); put("label", state.reminder.label)
                        put("hour", state.reminder.hour); put("minute", state.reminder.minute)
                        put("days", JSONArray(state.reminder.days.sorted())); put("sound", state.reminder.sound)
                    })
                    put("goal", state.preferences.goal); put("intensity", state.preferences.intensity)
                    put("weeklyMinutes", state.preferences.weeklyMinutes)
                    put("activities", JSONArray(state.preferences.activities))
                    put("activePlannedMinutes", state.activePlannedMinutes); put("activeIntensity", state.activeIntensity)
                    put("activeWorkout", state.activeWorkout)
                }.toString()
            },
            restore = { encoded ->
                val json = JSONObject(encoded)
                PrototypeState(LocalDate.parse(json.getString("today"))).apply {
                    val settings = json.getJSONObject("reminder")
                    val days = settings.getJSONArray("days")
                    reminder = ReminderSettings(settings.getBoolean("enabled"), settings.getString("label"), settings.getInt("hour"), settings.getInt("minute"),
                        (0 until days.length()).map { days.getInt(it) }.toSet(), settings.getBoolean("sound"))
                    val activities = json.optJSONArray("activities")
                    preferences = Preferences(json.getString("goal"), json.getString("intensity"), json.getInt("weeklyMinutes"),
                        activities?.let { (0 until it.length()).map(it::getString) } ?: Preferences().activities)
                    activePlannedMinutes = json.optInt("activePlannedMinutes", 30); activeIntensity = json.optString("activeIntensity", "Medium")
                    activeWorkout = json.getString("activeWorkout")
                }
            }
        )
    }
}
