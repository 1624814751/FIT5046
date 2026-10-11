package com.example.movesmart.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.movesmart.model.WorkoutRecord
import com.example.movesmart.model.sampleSessionMinutes
import com.example.movesmart.ui.components.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration

// Member C screens extracted from MainActivity and connected through callbacks.
@Composable
fun LiveActivityScreen(activity: String, intensity: String, plannedMinutes: Int, onFinish: () -> Unit) {
    val sessionMinutes = sampleSessionMinutes(activity, plannedMinutes)
    var paused by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    ScreenColumn {
        SectionHeading(activity, "$intensity intensity · Sample session")
        InfoCard(highlighted = true) {
            Text("$sessionMinutes:00", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(if (paused) "Paused · Demo timer" else "Elapsed time · Demo timer")
            LinearProgressIndicator(progress = { sessionMinutes.toFloat() / plannedMinutes }, modifier = Modifier.fillMaxWidth())
        }
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = null)
                Column {
                    Text("112 bpm", style = MaterialTheme.typography.titleLarge)
                    Text("Sample heart rate · No sensor connected", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Listen to your body. Pause whenever you need a break.")
        OutlinedButton({ paused = !paused }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (paused) "Resume workout" else "Pause workout") }
        PrimaryButton("Finish workout", { confirmFinish = true })
    }
    if (confirmFinish) AlertDialog(onDismissRequest = { confirmFinish = false }, title = { Text("Save this session?") },
        text = { Text("Add this $sessionMinutes-minute sample session to History and Statistics.") },
        confirmButton = { TextButton({ confirmFinish = false; onFinish() }) { Text("Save session") } },
        dismissButton = { TextButton({ confirmFinish = false }) { Text("Keep moving") } })
}

@Composable
fun HistoryScreen(records: List<WorkoutRecord>, onAdd: () -> Unit, onEdit: (Int) -> Unit, onDelete: (Int) -> Unit, onSearch: () -> Unit, onStatistics: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var deleting by remember { mutableStateOf<WorkoutRecord?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionHeading("Every session counts", "Your movement, all in one place.") }
        item { SampleDataNote() }
        item { PrimaryButton("Add workout", onAdd) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onSearch, Modifier.weight(1f)) { Text("Search") }
            OutlinedButton(onStatistics, Modifier.weight(1f)) { Text("Statistics") }
        } }
        if (records.isEmpty()) item { InfoCard { Text("No workouts yet", style = MaterialTheme.typography.titleLarge); Text("Add your first session to begin your history.") } }
        items(records.sortedWith(compareByDescending<WorkoutRecord> { it.date }.thenByDescending { it.id }), key = { it.id }) { record ->
            InfoCard {
                Text(record.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${record.minutes} min · ${record.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))}", style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton({ onEdit(record.id) }) { Icon(Icons.Outlined.Edit, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Edit") }
                    Spacer(Modifier.weight(1f))
                    TextButton({ deleting = record }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Delete") }
                }
            }
        }
    }
    deleting?.let { record ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this workout?") },
            text = { Text("${record.title}, ${record.minutes} min will be removed from this demo's history and statistics.") },
            confirmButton = { TextButton({ onDelete(record.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(record: WorkoutRecord?, today: LocalDate, onSave: (String, Int, LocalDate) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var title by rememberSaveable(record?.id) { mutableStateOf(record?.title ?: "Outdoor Walk") }
    var minutes by rememberSaveable(record?.id) { mutableStateOf(record?.minutes?.toString() ?: "30") }
    var dateText by rememberSaveable(record?.id) { mutableStateOf((record?.date ?: today).toString()) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.parse(dateText)
    val invalidMinutes = minutes.toIntOrNull()?.let { it !in 1..600 } ?: true
    ScreenColumn {
        SectionHeading(if (record == null) "Record your movement" else "Update your session", "Your changes also update Statistics.")
        ChoiceField("Activity", title, listOf("Outdoor Walk", "Outdoor Jogging", "Indoor Cycling", "Yoga", "Mobility", "Indoor Mobility"), { title = it })
        OutlinedTextField(minutes, { minutes = it }, label = { Text("Duration (minutes)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = attempted && invalidMinutes,
            supportingText = { Text("Enter a whole number from 1 to 600.") }, modifier = Modifier.fillMaxWidth())
        Text("Workout date", style = MaterialTheme.typography.titleMedium)
        OutlinedButton({ picker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CalendarToday, contentDescription = null); Spacer(Modifier.width(12.dp)); Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))) }
        if (attempted && date.isAfter(today)) Text("Choose today or an earlier date.", color = MaterialTheme.colorScheme.error)
        PrimaryButton("Save workout", { attempted = true; if (!invalidMinutes && !date.isAfter(today)) onSave(title, minutes.toInt(), date) })
    }
    if (picker) {
        // Material DatePicker represents calendar dates at UTC midnight.
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
                override fun isSelectableYear(year: Int) = year <= today.year
            }
        )
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = {
            TextButton({ dateState.selectedDateMillis?.let { dateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; picker = false }, enabled = dateState.selectedDateMillis != null) { Text("Set date") }
        }, dismissButton = { TextButton({ picker = false }) { Text("Cancel") } }) { DatePicker(dateState) }
    }
}

@Composable
fun SearchScreen(records: List<WorkoutRecord>, onRecord: (Int) -> Unit, onStart: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var history by rememberSaveable { mutableStateOf(false) }
    val activities = listOf("Outdoor Walk", "Indoor Cycling", "Yoga", "Mobility")
    val matchingActivities = activities.filter { it.contains(query.trim(), ignoreCase = true) }
    val matchingRecords = records.filter { it.title.contains(query.trim(), ignoreCase = true) }.sortedByDescending { it.date }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeading("Find your movement", "Search activities or your recorded sessions.") }
        item { OutlinedTextField(query, { query = it }, label = { Text("Search activities or history") }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") } }, modifier = Modifier.fillMaxWidth()) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!history, { history = false }, label = { Text("Activities") })
            FilterChip(history, { history = true }, label = { Text("My history") })
        } }
        if (history) {
            if (matchingRecords.isEmpty()) item { Text("No sessions found. Try another activity name.") }
            items(matchingRecords, key = { it.id }) { record -> InfoCard {
                Text(record.title, style = MaterialTheme.typography.titleMedium)
                Text("${record.minutes} min · ${record.date}")
                TextButton({ onRecord(record.id) }) { Text("View or edit session") }
            } }
        } else {
            if (matchingActivities.isEmpty()) item { Text("No activities found. Try walking, cycling or yoga.") }
            items(matchingActivities) { title -> InfoCard {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("Sample activity · Move at your own pace")
                TextButton({ onStart(title) }) { Text("Start sample workout") }
            } }
        }
    }
}
