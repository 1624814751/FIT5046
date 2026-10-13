package com.example.movesmart.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.movesmart.shared.WorkoutRecord as SharedWorkoutRecord
import com.example.movesmart.statistics.*
import com.example.movesmart.ui.components.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// A4 boundary: supply records from C's user-scoped WorkoutRepository.observe(userId).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityStatisticsScreen(
    records: List<SharedWorkoutRecord>, userId: String, weeklyGoal: Int, today: LocalDate,
    isDemo: Boolean, onHistory: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    var periodName by rememberSaveable(userId) { mutableStateOf(StatisticsPeriod.WEEK.name) }
    var anchorDay by rememberSaveable(userId) { mutableLongStateOf(today.toEpochDay()) }
    var choosingDate by rememberSaveable(userId) { mutableStateOf(false) }
    val period = StatisticsPeriod.valueOf(periodName)
    val anchor = LocalDate.ofEpochDay(anchorDay)
    val summary = statisticsFor(records, userId, anchor, period)
    val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    val range = if (summary.start == summary.end) summary.start.format(dateFormat)
        else "${summary.start.format(dateFormat)} – ${summary.end.format(dateFormat)}"

    ScreenColumn(spacing = 16.dp) {
        SectionHeading(
            when (period) {
                StatisticsPeriod.DAY -> "Your day in motion"
                StatisticsPeriod.WEEK -> "Your week in motion"
                StatisticsPeriod.MONTH -> "Your month in motion"
            },
            if (isDemo) "Sample data · Changes in History update these totals." else "See how your recorded movement adds up."
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatisticsPeriod.entries.forEach { item ->
                FilterChip(period == item, { periodName = item.name }, label = { Text(item.label) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ anchorDay = shiftStatisticsDate(anchor, period, -1).toEpochDay() }) { Text("Previous") }
            TextButton({ anchorDay = today.toEpochDay() }) { Text("Today") }
            TextButton({ anchorDay = shiftStatisticsDate(anchor, period, 1).toEpochDay() }) { Text("Next") }
        }
        OutlinedButton({ choosingDate = true }, Modifier.fillMaxWidth()) { Text("Choose date") }
        Text(range, style = MaterialTheme.typography.titleMedium)
        if (period == StatisticsPeriod.WEEK) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ anchorDay = today.toEpochDay() }) { Text("This week") }
                TextButton({ anchorDay = today.minusWeeks(1).toEpochDay() }) { Text("Last week") }
            }
        }
        InfoCard(highlighted = true, contentPadding = 16.dp, spacing = 8.dp) {
            Text("${period.label} movement", style = MaterialTheme.typography.titleMedium)
            Text("${summary.sessions} sessions · ${summary.activeDays} of ${summary.daily.size} active days")
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${summary.totalMinutes}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(if (period == StatisticsPeriod.WEEK && weeklyGoal > 0) " / $weeklyGoal min" else " min",
                    modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
            if (period == StatisticsPeriod.WEEK && weeklyGoal > 0) {
                LinearProgressIndicator(progress = { summary.progress(weeklyGoal) }, modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                    contentDescription = "Weekly goal: ${summary.totalMinutes} of $weeklyGoal minutes"
                })
                Text(if (summary.totalMinutes >= weeklyGoal) "Your weekly goal is complete. Celebrate your consistency!"
                    else "${weeklyGoal - summary.totalMinutes} minutes to your weekly goal.")
            } else if (period == StatisticsPeriod.WEEK) {
                Text("Set a weekly movement goal in Preferences.")
            }
        }
        InfoCard(contentPadding = 16.dp, spacing = 8.dp) {
            Text("Daily activity", style = MaterialTheme.typography.titleLarge)
            if (summary.sessions == 0) {
                Text("No workouts in this period. Choose another date or add a session in History.")
            } else {
                if (period == StatisticsPeriod.MONTH) Text("Scroll horizontally to see every day.", style = MaterialTheme.typography.bodySmall)
                val scale = maxOf(1L, summary.daily.maxOf { it.minutes })
                Text("Scale: 0–$scale min", style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    summary.daily.forEach { bucket ->
                        Column(Modifier.width(48.dp).clearAndSetSemantics {
                            contentDescription = "${bucket.date.format(dateFormat)}, ${bucket.minutes} minutes"
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${bucket.minutes}", style = MaterialTheme.typography.labelSmall)
                            Box(Modifier.fillMaxWidth().height(88.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.BottomCenter) {
                                if (bucket.minutes > 0) Box(Modifier.fillMaxWidth().height((88f * bucket.minutes / scale).dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
                            }
                            Text(bucket.date.format(DateTimeFormatter.ofPattern("d MMM", locale)), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        OutlinedButton(onHistory, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("View workout history") }
    }
    if (choosingDate) {
        // Material DatePicker represents calendar dates at UTC midnight, independent of device time zone.
        val picker = rememberDatePickerState(initialSelectedDateMillis = anchor.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { choosingDate = false }, confirmButton = {
            TextButton({
                picker.selectedDateMillis?.let { anchorDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                choosingDate = false
            }, enabled = picker.selectedDateMillis != null) { Text("Apply") }
        }, dismissButton = { TextButton({ choosingDate = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
}
