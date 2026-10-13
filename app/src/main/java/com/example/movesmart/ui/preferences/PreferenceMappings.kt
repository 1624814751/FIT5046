package com.example.movesmart.ui.preferences

import com.example.movesmart.shared.Environment
import com.example.movesmart.shared.Intensity
import com.example.movesmart.shared.UserPreferences

fun intensityFromText(value: String): Intensity? {
    return when (value) {
        "Easy" -> Intensity.LOW
        "Medium" -> Intensity.MEDIUM
        "High" -> Intensity.HIGH
        else -> null
    }
}

fun intensityToText(value: Intensity): String {
    return when (value) {
        Intensity.LOW -> "Easy"
        Intensity.MEDIUM -> "Medium"
        Intensity.HIGH -> "High"
    }
}

fun environmentFromText(value: String): Environment? {
    return when (value) {
        "Indoor" -> Environment.INDOOR
        "Outdoor" -> Environment.OUTDOOR
        "No preference" -> Environment.ANY
        else -> null
    }
}

fun environmentToText(value: Environment): String {
    return when (value) {
        Environment.INDOOR -> "Indoor"
        Environment.OUTDOOR -> "Outdoor"
        Environment.ANY -> "No preference"
    }
}

fun makePreferences(
    userId: String,
    intensity: String,
    activities: List<String>,
    goal: String,
    weeklyMinutes: Int,
    environment: String
): Result<UserPreferences> {
    return runCatching {
        require(userId.isNotBlank()) { "A user ID is required." }
        require(goal.isNotBlank()) { "Choose an exercise goal." }
        require(weeklyMinutes in 1..10080) { "Choose a valid weekly goal." }
        val savedIntensity = requireNotNull(intensityFromText(intensity)) { "Choose a valid intensity." }
        val savedEnvironment = requireNotNull(environmentFromText(environment)) { "Choose a valid environment." }
        UserPreferences(
            userId = userId,
            goal = goal,
            preferredIntensity = savedIntensity,
            weeklyGoalMinutes = weeklyMinutes,
            activityNames = activities.distinct(),
            environment = savedEnvironment
        )
    }
}
