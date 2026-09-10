package com.example.adaptivemove

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared UI state for B's three screens.
 *
 * A2 uses demo values only. In A4, the weather and activity values can be
 * replaced by Retrofit data and simulated sensor data.
 */
class WorkoutViewModel : ViewModel() {
    var selectedDateMillis by mutableStateOf(System.currentTimeMillis())
        private set

    var selectedDuration by mutableStateOf("30 min")
        private set

    var selectedIntensity by mutableStateOf("Medium")
        private set

    var selectedLocation by mutableStateOf("Chadstone")
        private set

    var selectedPreference by mutableStateOf("Outdoor")
        private set

    var recommendationAccepted by mutableStateOf(false)
        private set

    // Static A2 demo context. These values will be dynamic in Assessment 4.
    val weatherSummary = "Light rain · 16°C"
    val activityContext = "Low activity"

    fun updateDate(dateMillis: Long) {
        selectedDateMillis = dateMillis
    }

    fun updateDuration(duration: String) {
        selectedDuration = duration
    }

    fun updateIntensity(intensity: String) {
        selectedIntensity = intensity
    }

    fun updateLocation(location: String) {
        selectedLocation = location
    }

    fun updatePreference(preference: String) {
        selectedPreference = preference
    }

    fun acceptRecommendation() {
        recommendationAccepted = true
    }

    fun formattedDate(): String {
        return SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
            .format(Date(selectedDateMillis))
    }
}
