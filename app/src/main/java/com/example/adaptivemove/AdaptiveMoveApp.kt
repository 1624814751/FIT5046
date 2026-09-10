@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.adaptivemove

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private val Ink = Color(0xFF25272B)
private val Brand = Color(0xFF5B5FEF)
private val BrandSoft = Color(0xFFECEBFF)
private val Lime = Color(0xFFD9FF66)
private val Page = Color(0xFFF7F7F5)
private val Line = Color(0xFFE5E5E2)
private val Muted = Color(0xFF686C72)

enum class MoveSmartScreen(val route: String, val label: String) {
    Home("home", "Home"),
    Discover("discover", "Discover"),
    Plan("plan", "Plan")
}

/** Assessment 2 prototype for member B. APIs and sensors are intentionally static until A4. */
@Composable
fun AdaptiveMoveApp(workoutViewModel: WorkoutViewModel = viewModel()) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        containerColor = Page,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                MoveSmartScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(if (screen == MoveSmartScreen.Home) "●" else "○") },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MoveSmartScreen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MoveSmartScreen.Home.route) {
                HomeScreen(onFindActivity = { navController.navigate(MoveSmartScreen.Discover.route) })
            }
            composable(MoveSmartScreen.Discover.route) {
                ActivityRecommendationScreen(
                    workoutViewModel = workoutViewModel,
                    onShowDetails = { navController.navigate(MoveSmartScreen.Plan.route) }
                )
            }
            composable(MoveSmartScreen.Plan.route) {
                ActivityDetailsScreen(workoutViewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun HomeScreen(onFindActivity: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("↗", color = Brand, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("MoveSmart", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.weight(1f))
            Text("◯", color = Ink, style = MaterialTheme.typography.titleLarge)
        }
        Text("Good afternoon, Alex", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text("You have time.\nLet's make it count.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        ContextRow()
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("BEST MATCH RIGHT NOW", color = Lime, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Outdoor walk", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Text("Why this matches you", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text("Clear weather  •  60 min available  •  Medium intensity", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("45 min   ·   0.8 km away", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text("→", color = Ink, textAlign = TextAlign.Center,
                        modifier = Modifier.background(Lime, RoundedCornerShape(20.dp)).clickable(onClick = onFindActivity).padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        SectionTitle("Your preferences", "Adjust")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PreferenceCard("Intensity", "Medium", true, Modifier.weight(1f))
            PreferenceCard("Goal", "Stay active", false, Modifier.weight(1f))
            PreferenceCard("Activity", "Outdoor", false, Modifier.weight(1f))
        }
        SectionTitle("Safety status")
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SafetyRow("Weather safe", "No rain or strong wind expected.")
                SafetyRow("Heart rate normal", "Wearable monitoring is active.")
                Text("No alerts right now", color = Muted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun ContextRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ContextChip("☀ 18°C"); ContextChip("⌖ Chadstone"); ContextChip("◷ 60 min")
    }
}

@Composable private fun ContextChip(text: String) {
    Text(text, color = Ink, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(Color.White, RoundedCornerShape(20.dp)).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 8.dp))
}

@Composable private fun SectionTitle(title: String, action: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.weight(1f))
        if (action != null) Text(action, color = Brand, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun PreferenceCard(label: String, value: String, selected: Boolean, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = if (selected) BrandSoft else Color.White),
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
fun ActivityRecommendationScreen(workoutViewModel: WorkoutViewModel, onShowDetails: () -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
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
        SelectorMenu("Intensity", workoutViewModel.selectedIntensity, listOf("Low", "Medium", "High"), workoutViewModel::updateIntensity)
        SelectorMenu("Location", workoutViewModel.selectedLocation, listOf("Chadstone", "Home", "Campus"), workoutViewModel::updateLocation)
        SelectorMenu("Activity preference", workoutViewModel.selectedPreference, listOf("Outdoor", "Indoor", "No preference"), workoutViewModel::updatePreference)
        Card(colors = CardDefaults.cardColors(containerColor = BrandSoft), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Demo context: 18°C, clear weather, Chadstone", modifier = Modifier.padding(16.dp), color = Ink, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onShowDetails, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("See recommendation") }
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
fun ActivityDetailsScreen(workoutViewModel: WorkoutViewModel, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your best match", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        Card(colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("RECOMMENDED ACTIVITY", color = Lime, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                Text("Outdoor walk", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                Text("45 min  ·  Medium intensity  ·  0.8 km away", color = Color.White.copy(alpha = 0.8f))
            }
        }
        Text("Why this activity?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        ReasonCard("Weather suitability", "Clear, mild weather is suitable for an outdoor activity.")
        ReasonCard("Time available", "It fits within your ${workoutViewModel.selectedDuration.lowercase()} window.")
        ReasonCard("Your preferences", "It matches ${workoutViewModel.selectedIntensity.lowercase()} intensity and ${workoutViewModel.selectedPreference.lowercase()} preference.")
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
            Text("Safety note: Stop and rest if you feel unwell. Wearable and weather alerts are simulated in this A2 prototype.", modifier = Modifier.padding(16.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Edit") }
            Button(onClick = workoutViewModel::acceptRecommendation, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("Start activity") }
        }
        if (workoutViewModel.recommendationAccepted) Text("Plan saved. Live Activity will be connected during integration.", color = Brand, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable private fun ReasonCard(title: String, copy: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp))
            Text(copy, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
