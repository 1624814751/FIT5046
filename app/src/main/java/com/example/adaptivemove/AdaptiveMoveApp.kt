@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.adaptivemove

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable

enum class AdaptiveMoveScreen(val route: String, val label: String) {
    Home("home", "Home"),
    Plan("plan", "Plan"),
    Recommendation("recommendation", "Recommendation")
}

@Composable
fun AdaptiveMoveApp(workoutViewModel: WorkoutViewModel = viewModel()) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(title = { Text("AdaptiveMove") })
        },
        bottomBar = {
            NavigationBar {
                AdaptiveMoveScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {},
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AdaptiveMoveScreen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AdaptiveMoveScreen.Home.route) {
                HomeScreen(
                    workoutViewModel = workoutViewModel,
                    onViewPlan = { navController.navigate(AdaptiveMoveScreen.Plan.route) }
                )
            }
            composable(AdaptiveMoveScreen.Plan.route) {
                WorkoutPlanScreen(
                    workoutViewModel = workoutViewModel,
                    onGetRecommendation = {
                        navController.navigate(AdaptiveMoveScreen.Recommendation.route)
                    }
                )
            }
            composable(AdaptiveMoveScreen.Recommendation.route) {
                RecommendationScreen(
                    workoutViewModel = workoutViewModel,
                    onBackToPlan = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    workoutViewModel: WorkoutViewModel,
    onViewPlan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Today’s movement, made achievable.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = workoutViewModel.formattedDate(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ContextCard(
            title = "Melbourne weather",
            value = workoutViewModel.weatherSummary,
            supportingText = "Demo weather data for Assessment 2"
        )
        ContextCard(
            title = "Simulated activity context",
            value = workoutViewModel.activityContext,
            supportingText = "Sensor data simulation will be added in Assessment 4"
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Today’s plan", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Outdoor walk · ${workoutViewModel.selectedDuration} · " +
                        "${workoutViewModel.selectedIntensity} intensity",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Check your plan to see a weather-aware alternative.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = onViewPlan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View workout plan")
        }
    }
}

@Composable
fun ContextCard(title: String, value: String, supportingText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(supportingText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun WorkoutPlanScreen(
    workoutViewModel: WorkoutViewModel,
    onGetRecommendation: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = workoutViewModel.selectedDateMillis
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let(workoutViewModel::updateDate)
                        showDatePicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Plan your workout",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Set a realistic plan. AdaptiveMove will suggest an alternative when context changes.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Date: ${workoutViewModel.formattedDate()}")
        }

        SelectorMenu(
            label = "Duration",
            selectedOption = workoutViewModel.selectedDuration,
            options = listOf("15 min", "30 min", "45 min"),
            onOptionSelected = workoutViewModel::updateDuration
        )
        SelectorMenu(
            label = "Intensity",
            selectedOption = workoutViewModel.selectedIntensity,
            options = listOf("Low", "Medium", "High"),
            onOptionSelected = workoutViewModel::updateIntensity
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Original plan", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Outdoor walk · ${workoutViewModel.selectedDuration} · " +
                        "${workoutViewModel.selectedIntensity} intensity",
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Context: ${workoutViewModel.weatherSummary}")
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onGetRecommendation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get recommendation")
        }
    }
}

@Composable
fun SelectorMenu(
    label: String,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun RecommendationScreen(
    workoutViewModel: WorkoutViewModel,
    onBackToPlan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Your adjusted plan",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "A small change can keep your routine achievable.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Recommended workout", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Indoor stretching",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("15 min · Low intensity")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Why this plan?", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• ${workoutViewModel.weatherSummary}")
                Text("• ${workoutViewModel.activityContext}")
                Text("• A shorter indoor option is easier to complete today.")
            }
        }

        if (workoutViewModel.recommendationAccepted) {
            Text(
                text = "Plan accepted — great choice.",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onBackToPlan,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = workoutViewModel::acceptRecommendation,
                modifier = Modifier.weight(1f)
            ) {
                Text("Accept plan")
            }
        }
    }
}
