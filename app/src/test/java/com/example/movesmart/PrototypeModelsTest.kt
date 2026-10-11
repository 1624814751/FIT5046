package com.example.movesmart

import com.example.movesmart.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PrototypeModelsTest {
    private val today = LocalDate.of(2026, 9, 9)

    @Test fun weeklyBucketsExcludeAdjacentWeeksAndAggregateSameDay() {
        val rows = listOf(
            WorkoutRecord(1, "Walk", 20, LocalDate.of(2026, 9, 6)),
            WorkoutRecord(2, "Walk", 30, LocalDate.of(2026, 9, 7)),
            WorkoutRecord(3, "Yoga", 15, LocalDate.of(2026, 9, 7)),
            WorkoutRecord(4, "Cycle", 60, LocalDate.of(2026, 9, 13)),
            WorkoutRecord(5, "Walk", 90, LocalDate.of(2026, 9, 14))
        )
        val summary = weekSummary(rows, today)
        assertEquals(listOf(45, 0, 0, 0, 0, 0, 60), summary.dailyMinutes)
        assertEquals(105, summary.totalMinutes)
        assertEquals(2, summary.activeDays)
        assertEquals(3, summary.sessions)
        assertEquals(20, weekSummary(rows, today, 1).totalMinutes)
    }

    @Test fun emptyWeekHasSevenZeroBarsAndNoProgress() {
        val week = weekSummary(emptyList(), today)
        assertEquals(List(7) { 0 }, week.dailyMinutes)
        assertEquals(0f, week.progress(150))
        assertEquals(0, week.sessions)
    }

    @Test fun goalProgressNeverExceedsOneOrDividesByZero() {
        val week = WeekSummary(today, listOf(180, 0, 0, 0, 0, 0, 0), 1)
        assertEquals(1f, week.progress(150))
        assertEquals(0f, week.progress(0))
    }

    @Test fun reminderRequiresANameAndAtLeastOneDayWhenEnabled() {
        assertNotNull(ReminderSettings(label = "   ").validationError())
        assertNotNull(ReminderSettings(days = emptySet()).validationError())
        assertNotNull(ReminderSettings(label = "x".repeat(41)).validationError())
        assertNull(ReminderSettings().validationError())
    }

    @Test fun disabledReminderCanBeSavedWithoutACompletedForm() {
        assertNull(ReminderSettings(enabled = false, label = "", days = emptySet()).validationError())
    }

    @Test fun reminderAcceptsMidnightButRejectsInvalidClockValues() {
        assertNull(ReminderSettings(hour = 0, minute = 0).validationError())
        assertNotNull(ReminderSettings(hour = 24).validationError())
        assertNotNull(ReminderSettings(minute = 60).validationError())
    }

    @Test fun fixturesContainNoFutureWorkoutsAndHaveUniqueIds() {
        val rows = sampleRecords(today)
        assertTrue(rows.none { it.date.isAfter(today) || it.minutes <= 0 })
        assertEquals(rows.size, rows.map { it.id }.distinct().size)
    }
}
