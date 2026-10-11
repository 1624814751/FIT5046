package com.example.movesmart.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// A2 fixtures and UI state only. Replace with repositories in A4.
data class WorkoutRecord(val id: Int, val title: String, val minutes: Int, val date: LocalDate)

data class ReminderSettings(
    val enabled: Boolean = true,
    val label: String = "Time to move",
    val hour: Int = 18,
    val minute: Int = 30,
    val days: Set<Int> = setOf(1, 3, 5),
    val sound: Boolean = true
) {
    fun validationError(): String? = when {
        !enabled -> null
        label.isBlank() -> "Give your reminder a name."
        label.length > 40 -> "Use 40 characters or fewer."
        days.isEmpty() -> "Choose at least one repeat day."
        hour !in 0..23 || minute !in 0..59 -> "Choose a valid time."
        else -> null
    }
}

data class Preferences(val goal: String = "Stay Active", val intensity: String = "Medium", val weeklyMinutes: Int = 150,
    val activities: List<String> = listOf("Walking", "Swimming", "Badminton"))

data class WeekSummary(val start: LocalDate, val dailyMinutes: List<Int>, val sessions: Int) {
    val totalMinutes: Int get() = dailyMinutes.sum()
    val activeDays: Int get() = dailyMinutes.count { it > 0 }
    fun progress(goal: Int): Float = if (goal <= 0) 0f else (totalMinutes.toFloat() / goal).coerceIn(0f, 1f)
}

fun weekSummary(records: List<WorkoutRecord>, today: LocalDate, weeksAgo: Long = 0): WeekSummary {
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeksAgo)
    val week = records.filter { !it.date.isBefore(start) && it.date.isBefore(start.plusWeeks(1)) }
    return WeekSummary(start, (0..6).map { offset ->
        week.filter { it.date == start.plusDays(offset.toLong()) }.sumOf { it.minutes }
    }, week.size)
}

fun sampleRecords(today: LocalDate): List<WorkoutRecord> {
    val names = listOf("Outdoor Walk", "Indoor Cycling", "Yoga", "Mobility", "Outdoor Walk", "Yoga", "Indoor Cycling")
    val minutes = listOf(30, 25, 45, 20, 0, 35, 30, 20, 0, 25, 30, 20, 15, 30)
    return minutes.mapIndexedNotNull { i, amount ->
        if (amount == 0) null else WorkoutRecord(i + 1, names[i % names.size], amount, today.minusDays(i.toLong()))
    }
}

fun sampleSessionMinutes(activity: String, plannedMinutes: Int = 30): Int = minOf(plannedMinutes, if (activity == "Indoor Mobility") 15 else 25)
