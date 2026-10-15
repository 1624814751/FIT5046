package com.example.movesmart.ui.planning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movesmart.shared.*
import com.example.movesmart.ui.components.*
import com.example.movesmart.ui.context.WorkoutViewModel
import com.example.movesmart.ui.context.memberAPlan
import com.example.movesmart.ui.preferences.intensityToText
import com.example.movesmart.ui.preferences.environmentToText
import com.example.movesmart.ui.weather.WeatherViewModel
import com.example.movesmart.worker.WeatherWork
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun ConnectedHome(name: String, preferences: UserPreferences?, loading: Boolean, error: String?, weather: UiState<WeatherContext>,
    onPlan: () -> Unit, onPreferences: () -> Unit, contextContent: (@Composable () -> Unit)? = null) {
    ScreenColumn {
        SectionHeading("Hi, $name", "Plan an activity using your preferences and the forecast.")
        InfoCard {
            when {
                loading -> { CircularProgressIndicator(); Text("Loading your preferences…") }
                error != null -> Text(error)
                preferences == null -> Text("Save your exercise preferences to get started.")
                else -> {
                    Text(preferences.goal)
                    Text("${intensityToText(preferences.preferredIntensity)} intensity · ${preferences.weeklyGoalMinutes} min weekly goal")
                    Text(environmentToText(preferences.environment))
                }
            }
        }
        if (contextContent == null) InfoCard {
            Text("Weather", style = MaterialTheme.typography.titleMedium)
            when (weather) {
                is UiState.Success -> {
                    Text("${weather.data.query.location.label} · ${weather.data.query.date}")
                    Text("${weather.data.temperatureC}°C · ${weather.data.source.name}${if (weather.data.isStale) " · STALE" else ""}")
                }
                is UiState.Error -> Text(weather.message)
                UiState.Loading -> Text("Loading weather…")
                UiState.Idle -> Text("Choose a date in Find an activity to load its forecast.")
            }
        }
        PrimaryButton("Find an activity", onPlan, enabled = preferences != null && !loading && error == null)
        OutlinedButton(onPreferences, Modifier.fillMaxWidth()) { Text("Preferences") }
        contextContent?.invoke()
        Text("Your plan uses the forecast for the selected location and date. Movement feedback is available when a sensor session is connected.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(planning: PlanningViewModel, weather: WeatherViewModel, preferences: UserPreferences?,
    form: WorkoutViewModel, location: GeoLocation?, contextContent: @Composable () -> Unit,
    onDetails: (String) -> Unit) {
    val context = LocalContext.current
    val weatherState by weather.state.collectAsStateWithLifecycle()
    val saveState by planning.state.collectAsStateWithLifecycle()
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var scheduleError by remember { mutableStateOf<String?>(null) }
    val date = Instant.ofEpochMilli(form.selectedDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val plan = memberAPlan(form, location)
    val query = plan?.let { WeatherQuery(it.location, it.date) }
    val loaded = (weatherState as? UiState.Success)?.data?.takeIf { it.query == query }
    val saving = saveState == UiState.Loading
    LaunchedEffect(saveState) {
        (saveState as? UiState.Success)?.data?.let { id -> planning.consumeNavigation(); onDetails(id) }
    }
    ScreenColumn {
        SectionHeading("Find an activity", "Choose your plan, then load the matching forecast.")
        Text(location?.let { "Location: ${it.label}" } ?: "Choose a verified location below.")
        OutlinedButton({ datePicker = true }, enabled = !saving) { Text("Date: $date") }
        ChoiceField("Available time", form.selectedDuration, listOf("15 min", "30 min", "45 min", "60 min")) { if (!saving) form.updateDuration(it) }
        ChoiceField("Intensity", form.selectedIntensity, Intensity.entries.map(::intensityToText)) { if (!saving) form.updateIntensity(it) }
        ChoiceField("Environment", form.selectedPreference, Environment.entries.map(::environmentToText)) { if (!saving) form.updatePreference(it) }
        contextContent()
        PrimaryButton("Load weather", { query?.let(weather::loadWeather) }, enabled = query != null && !saving && weatherState != UiState.Loading)
        when (val result = weatherState) {
            UiState.Idle -> Text("Load weather to continue.")
            UiState.Loading -> { CircularProgressIndicator(); Text("Loading weather…") }
            is UiState.Error -> {
                Text(result.message)
                if (result.retryable && query != null) TextButton({ weather.loadWeather(query) }) { Text("Retry weather") }
            }
            is UiState.Success -> if (loaded == null) Text("The location or date changed. Load matching weather.") else {
                InfoCard {
                    Text("${loaded.temperatureC}°C · rain ${loaded.rainProbabilityPercent?.let { "$it%" } ?: "unavailable"} · wind ${loaded.windSpeedKmh?.let { "$it km/h" } ?: "unavailable"}")
                    Text("${loaded.source.name} forecast${if (loaded.isStale) " · STALE — check conditions before going outside" else ""}")
                    Text("Updated ${loaded.fetchedAt}. Forecast is for noon at the selected location.")
                }
                LaunchedEffect(loaded.query) {
                    scheduleError = try { WeatherWork(context).startWeatherWork(loaded.query); null }
                    catch (_: Exception) { "Background weather refresh could not be scheduled." }
                }
            }
        }
        scheduleError?.let { Text(it) }
        if (preferences == null) Text("Save your preferences before creating a plan.")
        (saveState as? UiState.Error)?.let { Text(it.message) }
        PrimaryButton(if (saving) "Saving plan…" else "Show activity details", {
            if (preferences != null && loaded != null && plan != null) planning.create(preferences, plan, loaded)
        }, enabled = preferences != null && plan != null && loaded != null && !saving && weatherState != UiState.Loading)
    }
    if (datePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { datePicker = false }, confirmButton = {
            TextButton({ picker.selectedDateMillis?.let(form::updateDate); datePicker = false }) { Text("Apply") }
        }, dismissButton = { TextButton({ datePicker = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
}

@Composable
fun RecommendationDetails(planning: PlanningViewModel, id: String, onBack: () -> Unit,
    onReminder: () -> Unit, onStart: (Recommendation) -> Unit) {
    var attempt by remember(id) { mutableIntStateOf(0) }
    val flow = remember(planning, id, attempt) {
        planning.observe(id).map<Recommendation?, UiState<Recommendation?>> { UiState.Success(it) }
            .catch { emit(UiState.Error("Could not load this plan. Please retry.", true)) }
    }
    val state by flow.collectAsStateWithLifecycle(initialValue = UiState.Loading)
    ScreenColumn {
        SectionHeading("Activity details")
        when (val result = state) {
            UiState.Idle, UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> { Text(result.message); TextButton({ attempt++ }) { Text("Retry") } }
            is UiState.Success -> {
                val recommendation = result.data
                if (recommendation == null) Text("This plan is unavailable for your account. Create a new plan.")
                else {
                    InfoCard {
                        Text(recommendation.activityName, style = MaterialTheme.typography.headlineSmall)
                        Text("${recommendation.minutes} min · ${intensityToText(recommendation.intensity)} · ${environmentToText(recommendation.environment)}")
                        Text("${recommendation.plannedDate} · ${recommendation.location.label}")
                    }
                    Text("Why this activity")
                    recommendation.reasons.forEach { Text("• $it") }
                    PrimaryButton("Start activity", { onStart(recommendation) })
                    OutlinedButton(onReminder, Modifier.fillMaxWidth()) { Text("Set reminder") }
                }
            }
        }
        TextButton(onBack) { Text("Go back") }
    }
}
