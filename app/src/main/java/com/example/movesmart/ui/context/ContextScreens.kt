@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.movesmart.ui.context

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movesmart.shared.*
import com.example.movesmart.ui.weather.WeatherViewModel
import com.example.movesmart.ui.recommendation.RecommendationViewModel
import com.example.movesmart.ui.recommendation.RecommendationSelection
import java.time.Instant
import java.time.ZoneOffset

// Member B's screens use the application's shared theme and navigation callbacks.
private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Brand: Color @Composable get() = MaterialTheme.colorScheme.primary
private val BrandSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val Hero = Color(0xFF25272B)
private val Lime = Color(0xFFD9FF66)
private val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun HomeScreen(workoutViewModel: WorkoutViewModel, displayName: String, goal: String,
    onFindActivity: () -> Unit, onPreferences: () -> Unit,
    memberAContent: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Hello, $displayName", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text("You have time.\nLet's make it count.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        if (memberAContent != null) {
            Button(onClick = onFindActivity, modifier = Modifier.fillMaxWidth()) { Text("Find an activity") }
            memberAContent()
        } else {
            ContextRow(workoutViewModel)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Hero), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("BEST MATCH RIGHT NOW", color = Lime, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(workoutViewModel.recommendation.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    Text("Why this matches you", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("${workoutViewModel.weatherSummary}  •  ${workoutViewModel.selectedDuration} available  •  ${workoutViewModel.selectedIntensity} intensity", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${workoutViewModel.recommendation.minutes} min · ${workoutViewModel.selectedLocation}", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onFindActivity, modifier = Modifier.background(Lime, RoundedCornerShape(20.dp))) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Find an activity", tint = Hero)
                        }
                    }
                }
            }
        }
        if (memberAContent == null) Button(onClick = onFindActivity, modifier = Modifier.fillMaxWidth()) { Text("Find an activity") }
        SectionTitle("Your preferences", onPreferences)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PreferenceCard("Intensity", workoutViewModel.selectedIntensity, true, Modifier.weight(1f))
            PreferenceCard("Goal", goal, false, Modifier.weight(1f))
            PreferenceCard("Activity", workoutViewModel.selectedPreference, false, Modifier.weight(1f))
        }
        if (memberAContent == null) {
            SectionTitle("Safety status")
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SafetyRow("Sample weather", workoutViewModel.weatherSummary)
                    SafetyRow("Sample activity context", "Low movement · No wearable connected.")
                    Text("Weather and movement values are simulated for this demo.", color = Muted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun ContextRow(workoutViewModel: WorkoutViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ContextChip(workoutViewModel.weatherSummary); ContextChip(workoutViewModel.selectedLocation); ContextChip(workoutViewModel.selectedDuration)
    }
}

@Composable private fun ContextChip(text: String) {
    Text(text, color = Ink, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 8.dp))
}

@Composable private fun SectionTitle(title: String, onAdjust: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.weight(1f))
        if (onAdjust != null) TextButton(onClick = onAdjust) { Text("Adjust") }
    }
}

@Composable private fun PreferenceCard(label: String, value: String, selected: Boolean, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = if (selected) BrandSoft else MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) BrandSoft else Line), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.height(10.dp))
            Text(value, color = if (selected) Brand else Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun SafetyRow(title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("✓", color = Color(0xFF247A4D), modifier = Modifier.background(Color(0xFFE2F5E9), RoundedCornerShape(10.dp)).padding(8.dp), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Column { Text(title, color = Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold); Text(description, color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun ActivityRecommendationScreen(workoutViewModel: WorkoutViewModel,
    memberAContent: (@Composable () -> Unit)? = null, onShowDetails: () -> Unit) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = workoutViewModel.selectedDateMillis)
    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = { datePickerState.selectedDateMillis?.let(workoutViewModel::updateDate); showDatePicker = false }) { Text("Confirm") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = datePickerState) }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Find an activity", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text("Tell us what works today. MoveSmart uses this context to recommend a realistic activity.", color = Muted)
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Date: ${workoutViewModel.formattedDate()}") }
        SelectorMenu("Available time", workoutViewModel.selectedDuration, listOf("15 min", "30 min", "45 min", "60 min"), workoutViewModel::updateDuration)
        SelectorMenu("Intensity", workoutViewModel.selectedIntensity, listOf("Easy", "Medium", "High"), workoutViewModel::updateIntensity)
        if (memberAContent == null) SelectorMenu("Location", workoutViewModel.selectedLocation, listOf("Chadstone", "Home", "Campus"), workoutViewModel::updateLocation)
        SelectorMenu("Activity preference", workoutViewModel.selectedPreference, listOf("Outdoor", "Indoor", "No preference"), workoutViewModel::updatePreference)
        if (memberAContent != null) memberAContent() else {
            Card(colors = CardDefaults.cardColors(containerColor = BrandSoft), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Demo context: ${workoutViewModel.weatherSummary}, ${workoutViewModel.selectedLocation}. ${workoutViewModel.activityContext}.", modifier = Modifier.padding(16.dp), color = Ink, style = MaterialTheme.typography.bodyMedium)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Simulate rainy weather", modifier = Modifier.weight(1f))
                Switch(checked = workoutViewModel.rainy, onCheckedChange = workoutViewModel::updateRainy,
                    modifier = Modifier.semantics { contentDescription = "Simulate rainy weather" })
            }
        }
        if (memberAContent != null) Text("The activity details below are a separate sample activity.", color = Muted)
        Button(onClick = onShowDetails, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("See recommendation") }
    }
}

/** Labels alone are not locations. The caller must supply coordinates from a validated source. */
internal fun memberAPlan(form: WorkoutViewModel, location: GeoLocation?): PlanningInput? {
    if (location == null || location.label != form.selectedLocation || location.label.isBlank() ||
        !location.latitude.isFinite() || location.latitude !in -90.0..90.0 ||
        !location.longitude.isFinite() || location.longitude !in -180.0..180.0) return null
    val minutes = form.selectedDuration.removeSuffix(" min").toIntOrNull()?.takeIf { it > 0 } ?: return null
    val intensity = when (form.selectedIntensity) {
        "Easy" -> Intensity.LOW
        "Medium" -> Intensity.MEDIUM
        "High" -> Intensity.HIGH
        else -> return null
    }
    val environment = when (form.selectedPreference) {
        "Indoor" -> Environment.INDOOR
        "Outdoor" -> Environment.OUTDOOR
        "No preference" -> Environment.ANY
        else -> return null
    }
    return PlanningInput(Instant.ofEpochMilli(form.selectedDateMillis).atZone(ZoneOffset.UTC).toLocalDate(),
        minutes, intensity, environment, location)
}

@Composable
fun MemberAContextRoute(userId: String, plan: PlanningInput?, weather: WeatherViewModel,
    recommendations: RecommendationViewModel, onSelected: (String) -> Unit = {}) {
    val weatherState by weather.state.collectAsStateWithLifecycle()
    val recommendationState by recommendations.state.collectAsStateWithLifecycle()
    val selection by recommendations.selection.collectAsStateWithLifecycle()
    val query = plan?.let { WeatherQuery(it.location, it.date) }
    LaunchedEffect(query) { if (query != null) weather.loadWeather(query) else weather.clearLocation() }
    // Reading collected values triggers recomposition; guarded access also rejects a
    // previous account's state before its asynchronous auth collector catches up.
    val visibleWeather = weatherState.let { weather.stateForQuery(query) }
    val matchingWeather = (visibleWeather as? UiState.Success)?.data?.takeIf { it.query == query }
    LaunchedEffect(userId, plan, matchingWeather) { recommendations.syncContext(plan, matchingWeather) }
    val visibleResult = recommendationState.let { recommendations.stateForContext(userId, plan, matchingWeather) }
    val visibleSelection = selection.let { if (visibleResult is UiState.Success) recommendations.selectionForUser(userId) else null }
    MemberAContextContent(plan, visibleWeather, visibleResult, visibleSelection,
        onWeatherRetry = weather::retryWeather, onRecommendationRetry = recommendations::retry,
        onGenerate = {
            if (plan != null) recommendations.generateRecommendation(plan,
                (weather.state.value as? UiState.Success)?.data?.takeIf { it.query == query })
        }, onSelect = { if (recommendations.selectRecommendation(it)) onSelected(it) })
}

@Composable
fun MemberAContextContent(plan: PlanningInput?, weather: UiState<WeatherContext>,
    recommendation: UiState<ContextDecision?>, selection: RecommendationSelection?,
    onWeatherRetry: () -> Unit, onRecommendationRetry: () -> Unit,
    onGenerate: () -> Unit, onSelect: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = BrandSoft),
        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Weather", style = MaterialTheme.typography.titleMedium)
            if (plan == null) {
                Text("Weather unavailable: this location has no verified coordinates.")
            } else when (weather) {
                UiState.Idle -> Text("Weather is not available yet.")
                UiState.Loading -> { CircularProgressIndicator(); Text("Loading weather…") }
                is UiState.Error -> {
                    Text(weather.message, color = MaterialTheme.colorScheme.error)
                    if (weather.retryable) TextButton(onWeatherRetry) { Text("Retry weather") }
                }
                is UiState.Success -> {
                    val value = weather.data
                    Text("${value.temperatureC}°C · ${value.query.location.label}")
                    Text("Noon forecast · ${value.query.date}")
                    Text("Rain: ${value.rainProbabilityPercent?.let { "$it%" } ?: "Unavailable"} · " +
                        "Wind: ${value.windSpeedKmh?.let { "$it km/h" } ?: "Unavailable"}")
                    Text(when (value.source) {
                        DataSource.CACHE -> "Saved weather"
                        DataSource.LIVE -> "Forecast from weather service"
                        DataSource.DEMO -> "Sample weather"
                    })
                    Text("Fetched: ${value.fetchedAt}")
                    if (value.isStale) Text("Old weather — refresh before going outside.")
                    TextButton(onWeatherRetry) { Text("Refresh weather") }
                }
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Hero),
        shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("YOUR RECOMMENDATION", color = Lime, style = MaterialTheme.typography.labelSmall)
            when (recommendation) {
                UiState.Idle -> Text("No recommendation yet. Choose your plan to get started.", color = Color.White)
                UiState.Loading -> { CircularProgressIndicator(color = Lime); Text("Loading recommendation…", color = Color.White) }
                is UiState.Error -> {
                    Text(recommendation.message, color = Color.White)
                    if (recommendation.retryable) TextButton(onRecommendationRetry) { Text("Retry recommendation", color = Lime) }
                }
                is UiState.Success -> {
                    val decision = recommendation.data
                    if (decision == null) Text("No saved recommendation was found.", color = Color.White)
                    else {
                        val activity = decision.recommendation
                        Text(activity.activityName, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Text("${activity.minutes} min · ${activity.intensity.name.lowercase()} intensity", color = Color.White)
                        activity.reasons.forEach { Text(it, color = Color.White) }
                        decision.safetyPrompt?.let { Text(it, color = Lime) }
                        if (selection?.recommendationId == activity.id) Text("Selected for this session", color = Lime)
                        else Button({ onSelect(activity.id) }) { Text("Select recommendation") }
                    }
                }
            }
            if (plan == null) Text("A location with verified coordinates is needed to generate a recommendation.", color = Color.White)
            if (plan != null && weather !is UiState.Success && weather != UiState.Loading) {
                Text("Weather unavailable: recommendations can use your preferences and time without weather. Outdoor conditions are not verified.", color = Color.White)
            }
            Button(onGenerate, enabled = plan != null && weather != UiState.Loading && recommendation != UiState.Loading) { Text("Generate recommendation") }
        }
    }
}

@Composable fun SelectorMenu(label: String, selectedOption: String, options: List<String>, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = selectedOption, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onOptionSelected(option); expanded = false }) }
        }
    }
}

@Composable
fun ActivityDetailsScreen(workoutViewModel: WorkoutViewModel, onBack: () -> Unit, onReminder: () -> Unit, onStart: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your best match", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        Card(colors = CardDefaults.cardColors(containerColor = Hero), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("RECOMMENDED ACTIVITY", color = Lime, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                Text(workoutViewModel.recommendation.title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                Text("${workoutViewModel.recommendation.minutes} min · ${workoutViewModel.selectedIntensity} intensity · ${workoutViewModel.selectedLocation}", color = Color.White.copy(alpha = 0.8f))
            }
        }
        Text("Why this activity?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        ReasonCard("Weather suitability", workoutViewModel.recommendation.weatherReason)
        ReasonCard("Time available", "It fits within your ${workoutViewModel.selectedDuration.lowercase()} window.")
        ReasonCard("Your preferences", "${workoutViewModel.selectedIntensity} intensity. ${workoutViewModel.recommendation.preferenceReason}")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
            Text("Safety note: Stop and rest if you feel unwell. Wearable and weather alerts are simulated in this A2 prototype.", modifier = Modifier.padding(16.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Edit") }
            Button(onClick = onStart, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("Start activity") }
        }
        OutlinedButton(onClick = onReminder, modifier = Modifier.fillMaxWidth()) { Text("Set reminder") }
    }
}

@Composable private fun ReasonCard(title: String, copy: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp))
            Text(copy, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
