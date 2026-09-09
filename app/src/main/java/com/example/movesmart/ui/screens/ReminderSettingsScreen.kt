package com.example.movesmart.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.movesmart.model.ReminderSettings
import com.example.movesmart.ui.components.*
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

// Member D. The saved value belongs to the app; edits remain a draft until Save.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderSettingsScreen(saved: ReminderSettings, onSave: (ReminderSettings) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var enabled by rememberSaveable(saved) { mutableStateOf(saved.enabled) }
    var label by rememberSaveable(saved) { mutableStateOf(saved.label) }
    var hour by rememberSaveable(saved) { mutableIntStateOf(saved.hour) }
    var minute by rememberSaveable(saved) { mutableIntStateOf(saved.minute) }
    var days by rememberSaveable(saved) { mutableStateOf(saved.days.sorted()) }
    var sound by rememberSaveable(saved) { mutableStateOf(saved.sound) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    val draft = ReminderSettings(enabled, label, hour, minute, days.toSet(), sound)
    val error = if (attempted) draft.validationError() else null
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val timeLabel = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))

    ScreenColumn {
        SectionHeading("Make time for movement", "A gentle nudge, on the days that suit you.")
        InfoCard(highlighted = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Workout reminders", fontWeight = FontWeight.Bold)
                    Text(if (enabled) "Your routine, your schedule" else "Reminders are off", style = MaterialTheme.typography.bodySmall)
                }
                Switch(enabled, { enabled = it }, modifier = Modifier.semantics { contentDescription = "Workout reminders" })
            }
        }
        Text("Preview only · No notifications are sent in this prototype.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (enabled) {
            OutlinedTextField(
                label, { label = it }, label = { Text("Reminder name") },
                placeholder = { Text("e.g. Evening walk") }, singleLine = true,
                isError = attempted && (label.isBlank() || label.length > 40),
                supportingText = { Text("Shown in your reminder · ${label.length}/40") },
                modifier = Modifier.fillMaxWidth()
            )
            InfoCard {
                Text("Time", style = MaterialTheme.typography.titleMedium)
                OutlinedButton({ showTime = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(timeLabel, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Text("Change")
                }
                Text("Uses your device's local time.", style = MaterialTheme.typography.bodySmall)
                Text("Repeat on", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..7).forEach { day ->
                        val name = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else (days + day).sorted() },
                            label = { Text(name) }, modifier = Modifier.heightIn(min = 48.dp).semantics {
                                contentDescription = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale)
                            }
                        )
                    }
                }
            }
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Play a sound", fontWeight = FontWeight.SemiBold)
                        Text("Use the default reminder sound", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(sound, { sound = it }, modifier = Modifier.semantics { contentDescription = "Play a sound" })
                }
            }
            Text(
                if (days.isEmpty()) "Select the days you want a reminder." else "Preview: $label · $timeLabel · " + days.joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        PrimaryButton("Save reminders", {
            attempted = true
            if (draft.validationError() == null) onSave(draft.copy(label = label.trim()))
        })
    }
    if (showTime) {
        val timeState = rememberTimePickerState(hour, minute, is24Hour = use24Hour)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Reminder time") },
            text = { TimeInput(timeState) },
            confirmButton = { TextButton({ hour = timeState.hour; minute = timeState.minute; showTime = false }) { Text("Set time") } },
            dismissButton = { TextButton({ showTime = false }) { Text("Cancel") } }
        )
    }
}
