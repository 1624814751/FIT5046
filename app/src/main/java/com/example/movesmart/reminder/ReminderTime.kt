package com.example.movesmart.reminder

import com.example.movesmart.shared.ReminderSettings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Shared contract: ISO weekdays and the timezone selected when the reminder is saved. */
fun ReminderSettings.validationError(): String? = when {
    userId.isBlank() -> "A user is required."
    runCatching { ZoneId.of(zoneId) }.isFailure -> "Choose a valid time zone."
    hour !in 0..23 || minute !in 0..59 -> "Choose a valid time."
    days.any { it !in 1..7 } -> "Choose valid repeat days."
    !enabled -> null
    label.isBlank() || label.trim().length > 40 -> "Use a reminder name of 1 to 40 characters."
    days.isEmpty() -> "Choose at least one valid repeat day."
    else -> null
}

/** Calendar-based recurrence, never a fixed 24-hour interval (which drifts across DST). */
fun nextReminderTime(settings: ReminderSettings, after: Instant): Instant? {
    require(settings.validationError() == null) { settings.validationError().orEmpty() }
    if (!settings.enabled) return null
    val zone = ZoneId.of(settings.zoneId)
    val date = after.atZone(zone).toLocalDate()
    return (0L..7L).asSequence().map { date.plusDays(it) }
        .filter { it.dayOfWeek.value in settings.days }
        .map { it.atTime(LocalTime.of(settings.hour, settings.minute)).atZone(zone).toInstant() }
        .first { it.isAfter(after) }
}
