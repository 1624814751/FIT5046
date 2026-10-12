package com.example.movesmart.data.local

import com.example.movesmart.shared.WorkoutRecord
import java.time.LocalDate

fun workoutValidationError(record: WorkoutRecord, today: LocalDate): String? = when {
    record.id < 0 -> "Invalid workout ID."
    record.userId.isBlank() -> "A user ID is required."
    record.title.isBlank() -> "Choose an activity."
    record.minutes !in 1..1440 -> "Enter a whole number from 1 to 1440 minutes."
    record.date.isAfter(today) -> "Choose today or an earlier date."
    else -> null
}
