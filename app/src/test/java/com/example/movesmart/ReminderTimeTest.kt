package com.example.movesmart

import com.example.movesmart.reminder.nextReminderTime
import com.example.movesmart.reminder.validationError
import com.example.movesmart.shared.ReminderSettings
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ReminderTimeTest {
    private val settings = ReminderSettings("user-a", true, "Evening walk", 18, 30, setOf(1, 3, 5), true, "Australia/Melbourne")
    private fun next(after: String, value: ReminderSettings = settings) = nextReminderTime(value, Instant.parse(after))

    @Test fun sameDayBeforeSelectedTime() {
        assertEquals(Instant.parse("2026-10-05T07:30:00Z"), next("2026-10-05T06:00:00Z"))
    }
    @Test fun passedTimeMovesToNextSelectedWeekday() {
        assertEquals(Instant.parse("2026-10-07T07:30:00Z"), next("2026-10-05T08:00:00Z"))
    }
    @Test fun equalTriggerIsNotDeliveredTwice() {
        assertEquals(Instant.parse("2026-10-07T07:30:00Z"), next("2026-10-05T07:30:00Z"))
    }
    @Test fun sundayWrapsIntoNextWeek() {
        assertEquals(Instant.parse("2026-10-11T07:30:00Z"), next("2026-10-04T07:30:00Z", settings.copy(days = setOf(7))))
    }
    @Test fun localMidnightCanBelongToPreviousUtcDate() {
        assertEquals(Instant.parse("2026-10-04T13:00:00Z"), next("2026-10-04T12:59:59Z", settings.copy(hour = 0, minute = 0, days = setOf(1))))
    }
    @Test fun springDstGapMovesToFirstValidLocalOffset() {
        assertEquals(Instant.parse("2026-10-03T16:30:00Z"), next("2026-10-03T13:00:00Z", settings.copy(hour = 2, minute = 30, days = setOf(7))))
    }
    @Test fun autumnOverlapUsesFirstOccurrenceOnly() {
        val sunday = settings.copy(hour = 2, minute = 30, days = setOf(7))
        assertEquals(Instant.parse("2026-04-04T15:30:00Z"), next("2026-04-04T14:00:00Z", sunday))
        assertEquals(Instant.parse("2026-04-11T16:30:00Z"), next("2026-04-04T15:30:00Z", sunday))
    }
    @Test fun recurrenceKeepsWallClockTimeAcrossDst() {
        val sunday = settings.copy(days = setOf(7))
        assertEquals(Instant.parse("2026-10-04T07:30:00Z"), next("2026-09-27T08:30:00Z", sunday))
    }
    @Test fun disabledReminderHasNoNextOccurrence() {
        assertNull(next("2026-10-05T00:00:00Z", settings.copy(enabled = false, days = emptySet(), label = "")))
    }
    @Test fun rejectsInvalidWeekdaysAndClockAndZone() {
        listOf(settings.copy(days = emptySet()), settings.copy(days = setOf(0, 8)),
            settings.copy(hour = 24), settings.copy(minute = -1), settings.copy(zoneId = "not-a-zone"),
            settings.copy(userId = ""), settings.copy(label = " "), settings.copy(label = "x".repeat(41))).forEach {
            assertNotNull(it.validationError())
        }
    }
    @Test fun storedZoneIsIndependentOfDeviceZone() {
        val utc = settings.copy(zoneId = "UTC")
        assertEquals(Instant.parse("2026-10-05T18:30:00Z"), next("2026-10-05T06:00:00Z", utc))
    }
}
