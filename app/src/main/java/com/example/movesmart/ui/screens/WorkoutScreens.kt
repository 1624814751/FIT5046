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
import com.example.movesmart.sensor.LiveWorkoutState
import com.example.movesmart.ui.components.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.ceil

// Member C screens extracted from MainActivity and connected through callbacks.
@Composable
fun LiveActivityScreen(
    activity: String,
    plannedMinutes: Int,
    state: LiveWorkoutState,
    isSaving: Boolean = false,
    saveError: String? = null,
    onTogglePause: () -> Unit,
    onRetryPlayback: () -> Unit,
    onFinish: () -> Unit
) {
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    val measuredMinutes = ceil(state.elapsedMilliseconds / 60_000.0)
        .toInt()
        .coerceIn(1, 1_440)
    val progress = if (plannedMinutes <= 0) {
        0f
    } else {
        (state.elapsedMilliseconds / (plannedMinutes * 60_000f)).coerceIn(0f, 1f)
    }

    ScreenColumn {
        SectionHeading(activity, "Live sensor playback · $plannedMinutes min plan")
        InfoCard(highlighted = true) {
            Text(
                formatElapsed(state.elapsedMilliseconds),
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(if (state.paused) "Paused · Active time" else "Elapsed active time")
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }

        when {
            state.playbackError != null -> InfoCard {
                Text("Sensor playback unavailable", style = MaterialTheme.typography.titleMedium)
                Text(state.playbackError, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onRetryPlayback, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry sensor playback")
                }
            }
            state.loading -> InfoCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Loading UCI HAPT sensor playback…")
                }
            }
            state.latestMotion != null -> SensorReadingCard(state.latestMotion)
            else -> InfoCard { Text("Waiting for the first sensor sample…") }
        }

        state.safetyPrompt?.let { prompt ->
            InfoCard {
                Text("Movement check", style = MaterialTheme.typography.titleMedium)
                Text(prompt)
                Text(
                    "This is a general activity prompt, not medical advice.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedButton(
            onTogglePause,
            enabled = state.playbackError == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text(if (state.paused) "Resume workout" else "Pause workout")
        }
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(
            if (isSaving) "Saving…" else "Finish workout",
            { confirmFinish = true },
            enabled = !isSaving
        )
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Save this session?") },
            text = {
                Text("Add this $measuredMinutes-minute measured session to History and Statistics.")
            },
            confirmButton = {
                TextButton({ confirmFinish = false; onFinish() }) { Text("Save session") }
            },
            dismissButton = {
                TextButton({ confirmFinish = false }) { Text("Keep moving") }
            }
        )
    }
}

@Composable
private fun SensorReadingCard(motion: com.example.movesmart.shared.MotionContext) {
    InfoCard {
        Text("Dataset activity: ${motion.activityLabel}", style = MaterialTheme.typography.titleMedium)
        Text("Movement: ${motion.movementBand.name}")
        Text(
            "Accelerometer (${motion.accelerometerUnit}): ${motion.accelerometer.formatAxes()}"
        )
        Text(
            "Gyroscope (${motion.gyroscopeUnit}): ${motion.gyroscope.formatAxes()}"
        )
        Text(
            "Source: UCI HAPT public dataset · Demo playback",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun List<Double>.formatAxes(): String = joinToString(", ") { value ->
    String.format(Locale.US, "%.3f", value)
}

private fun formatElapsed(elapsedMilliseconds: Long): String {
    val totalSeconds = elapsedMilliseconds.coerceAtLeast(0) / 1_000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

@Composable
fun HistoryScreen(records: List<WorkoutRecord>, onAdd: () -> Unit, onEdit: (Int) -> Unit, onDelete: (Int) -> Unit, onSearch: () -> Unit, onStatistics: () -> Unit, isSaving: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0]
    var deleting by remember { mutableStateOf<WorkoutRecord?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionHeading("Every session counts", "Your movement, all in one place.") }
        item { Text("Saved on this device · Your account", style = MaterialTheme.typography.labelMedium) }
        item { PrimaryButton("Add workout", onAdd, enabled = !isSaving) }
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
                    TextButton({ onEdit(record.id) }, enabled = !isSaving) { Icon(Icons.Outlined.Edit, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Edit") }
                    Spacer(Modifier.weight(1f))
                    TextButton({ deleting = record }, enabled = !isSaving) { Icon(Icons.Outlined.DeleteOutline, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Delete") }
                }
            }
        }
    }
    deleting?.let { record ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this workout?") },
            text = { Text("${record.title}, ${record.minutes} min will be removed from your saved history and statistics.") },
            confirmButton = { TextButton({ onDelete(record.id); deleting = null }, enabled = !isSaving) { Text("Delete") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(record: WorkoutRecord?, today: LocalDate, isSaving: Boolean = false, errorMessage: String? = null, onCancel: (() -> Unit)? = null, onSave: (String, Int, LocalDate) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var title by rememberSaveable(record?.id) { mutableStateOf(record?.title ?: "Outdoor Walk") }
    var minutes by rememberSaveable(record?.id) { mutableStateOf(record?.minutes?.toString() ?: "30") }
    var dateText by rememberSaveable(record?.id) { mutableStateOf((record?.date ?: today).toString()) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.parse(dateText)
    val invalidMinutes = minutes.toIntOrNull()?.let { it !in 1..1440 } ?: true
    ScreenColumn {
        SectionHeading(if (record == null) "Record your movement" else "Update your session", "Your changes also update Statistics.")
        ChoiceField("Activity", title, listOf("Outdoor Walk", "Outdoor Jogging", "Indoor Cycling", "Yoga", "Mobility", "Indoor Mobility"), { if (!isSaving) title = it })
        OutlinedTextField(minutes, { minutes = it }, label = { Text("Duration (minutes)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = attempted && invalidMinutes,
            supportingText = { Text("Enter a whole number from 1 to 1440.") }, enabled = !isSaving, modifier = Modifier.fillMaxWidth())
        Text("Workout date", style = MaterialTheme.typography.titleMedium)
        OutlinedButton({ picker = true }, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CalendarToday, contentDescription = null); Spacer(Modifier.width(12.dp)); Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))) }
        if (attempted && date.isAfter(today)) Text("Choose today or an earlier date.", color = MaterialTheme.colorScheme.error)
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(if (isSaving) "Saving…" else "Save workout", { attempted = true; if (!invalidMinutes && !date.isAfter(today)) onSave(title, minutes.toInt(), date) }, enabled = !isSaving)
        onCancel?.let { OutlinedButton(it, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) { Text("Cancel") } }
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
