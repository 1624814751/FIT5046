package com.example.movesmart.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.movesmart.model.*
import com.example.movesmart.ui.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

// Member D. Charts and History always read the same A2 fixture collection.
@Composable
fun StatisticsScreen(records: List<WorkoutRecord>, weeklyGoal: Int, today: LocalDate, onHistory: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var lastWeek by rememberSaveable { mutableStateOf(false) }
    val summary = weekSummary(records, today, if (lastWeek) 1 else 0)
    val rangeFormat = DateTimeFormatter.ofPattern("d MMM", locale)
    ScreenColumn(spacing = 16.dp) {
        SectionHeading("Your week in motion", "Sample data · See how your movement adds up.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!lastWeek, { lastWeek = false }, label = { Text("This week") })
            FilterChip(lastWeek, { lastWeek = true }, label = { Text("Last week") })
        }
        InfoCard(highlighted = true, contentPadding = 16.dp, spacing = 8.dp) {
            Text("Weekly movement", style = MaterialTheme.typography.titleMedium)
            Text("${summary.sessions} sessions · ${summary.activeDays} of 7 active days", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${summary.totalMinutes}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(" / $weeklyGoal min", modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(progress = { summary.progress(weeklyGoal) }, modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                contentDescription = "Weekly goal: ${summary.totalMinutes} of $weeklyGoal minutes"
            })
            Text(
                if (summary.totalMinutes >= weeklyGoal) "Your weekly goal is complete. Celebrate your consistency!"
                else "${weeklyGoal - summary.totalMinutes} minutes to your weekly goal.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        InfoCard(contentPadding = 16.dp, spacing = 8.dp) {
            Text("Daily activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${summary.start.format(rangeFormat)} – ${summary.start.plusDays(6).format(rangeFormat)} · Minutes", style = MaterialTheme.typography.bodySmall)
            if (summary.sessions == 0) {
                Text("No workouts this week. Add a session in History to start your progress.")
            }
            val scale = maxOf(60, ((summary.dailyMinutes.maxOrNull() ?: 0) + 29) / 30 * 30)
            Text("Scale: 0–$scale min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                summary.dailyMinutes.forEachIndexed { index, amount ->
                    val day = summary.start.plusDays(index.toLong()).dayOfWeek
                    val fullDay = day.getDisplayName(TextStyle.FULL, locale)
                    Column(
                        modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = "$fullDay, $amount minutes" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("$amount", style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().height(88.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
                            if (amount > 0) Box(Modifier.fillMaxWidth().height((88f * amount / scale).dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
                        }
                        Text(day.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        OutlinedButton(onHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("View workout history") }
    }
}
