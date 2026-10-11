package com.example.movesmart.statistics

import com.example.movesmart.shared.WorkoutRecord
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Use record.date, the recorded activity date, rather than reinterpreting startedAt in a new zone.
enum class StatisticsPeriod(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month") }

data class ActivityBucket(val date: LocalDate, val minutes: Long)
data class ActivityStatistics(
    val start: LocalDate,
    val end: LocalDate,
    val daily: List<ActivityBucket>,
    val sessions: Int
) {
    val totalMinutes: Long get() = daily.sumOf { it.minutes }
    val activeDays: Int get() = daily.count { it.minutes > 0 }
    fun progress(goal: Int): Float = if (goal <= 0) 0f else
        (totalMinutes.toDouble() / goal).coerceIn(0.0, 1.0).toFloat()
}

fun statisticsFor(
    records: List<WorkoutRecord>, userId: String, anchor: LocalDate, period: StatisticsPeriod
): ActivityStatistics {
    val start = when (period) {
        StatisticsPeriod.DAY -> anchor
        StatisticsPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        StatisticsPeriod.MONTH -> anchor.withDayOfMonth(1)
    }
    val end = when (period) {
        StatisticsPeriod.DAY -> start
        StatisticsPeriod.WEEK -> start.plusDays(6)
        StatisticsPeriod.MONTH -> start.with(TemporalAdjusters.lastDayOfMonth())
    }
    // Repository queries must also be user-scoped. Keep this guard at the display boundary.
    val selected = records.filter {
        it.userId == userId && it.minutes > 0 && !it.date.isBefore(start) && !it.date.isAfter(end)
    }
    val totals = selected.groupBy { it.date }.mapValues { (_, items) -> items.sumOf { it.minutes.toLong() } }
    val daily = generateSequence(start) { if (it < end) it.plusDays(1) else null }
        .map { ActivityBucket(it, totals[it] ?: 0L) }.toList()
    return ActivityStatistics(start, end, daily, selected.size)
}

// Move by whole calendar periods; normalize month anchors to avoid Jan 31 -> Feb 28 -> Mar 28 drift.
fun shiftStatisticsDate(anchor: LocalDate, period: StatisticsPeriod, direction: Long): LocalDate = when (period) {
    StatisticsPeriod.DAY -> anchor.plusDays(direction)
    StatisticsPeriod.WEEK -> anchor.plusWeeks(direction)
    StatisticsPeriod.MONTH -> anchor.withDayOfMonth(1).plusMonths(direction)
}
