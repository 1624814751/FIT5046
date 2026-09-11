package com.example.movesmart.ui.context

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.movesmart.model.contextRecommendation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Member B's shared form state, now retained across navigation and recreation. A2 demo only. */
class WorkoutViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    var selectedDateMillis by mutableStateOf(savedState.get<Long>("date") ?: todayMillis())
        private set
    var selectedDuration by mutableStateOf(savedState.get<String>("duration") ?: "30 min")
        private set
    var selectedIntensity by mutableStateOf(savedState.get<String>("intensity") ?: "Medium")
        private set
    var selectedLocation by mutableStateOf(savedState.get<String>("location") ?: "Chadstone")
        private set
    var selectedPreference by mutableStateOf(savedState.get<String>("preference") ?: "Outdoor")
        private set
    var rainy by mutableStateOf(savedState.get<Boolean>("rainy") ?: false)
        private set

    val weatherSummary get() = if (rainy) "Light rain · 16°C" else "Clear · 18°C"
    val activityContext = "Low movement (sample)"
    val recommendation get() = contextRecommendation(selectedDuration.removeSuffix(" min").toInt(), selectedIntensity,
        selectedLocation, selectedPreference, rainy)

    fun updateDate(value: Long) { selectedDateMillis = value; savedState["date"] = value }
    fun updateDuration(value: String) { selectedDuration = value; savedState["duration"] = value }
    fun updateIntensity(value: String) { selectedIntensity = value; savedState["intensity"] = value }
    fun updateLocation(value: String) { selectedLocation = value; savedState["location"] = value }
    fun updatePreference(value: String) { selectedPreference = value; savedState["preference"] = value }
    fun updateRainy(value: Boolean) { rainy = value; savedState["rainy"] = value }
    fun formattedDate(): String = Instant.ofEpochMilli(selectedDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .format(DateTimeFormatter.ofPattern("EEE, dd MMM"))
    fun reset() {
        updateDate(todayMillis()); updateDuration("30 min"); updateIntensity("Medium")
        updateLocation("Chadstone"); updatePreference("Outdoor"); updateRainy(false)
    }
    private fun todayMillis() = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
