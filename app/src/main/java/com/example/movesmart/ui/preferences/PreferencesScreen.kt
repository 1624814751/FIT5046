package com.example.movesmart.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.movesmart.AccentInk
import com.example.movesmart.AppAccent
import com.example.movesmart.AppBackground
import com.example.movesmart.AppPrimary
import com.example.movesmart.AppSurface
import com.example.movesmart.ButtonShape
import com.example.movesmart.CardShape
import com.example.movesmart.TextInk
import com.example.movesmart.TextMuted

@Composable
fun PreferencesScreen(
    savedIntensity: String,
    savedActivities: List<String>,
    savedGoal: String,
    savedWeeklyMinutes: Int,
    suppliedViewModel: PreferencesViewModel? = null,
    onSave: (String, List<String>, String, Int) -> Unit
) {
    val context = LocalContext.current
    val preferencesViewModel: PreferencesViewModel = suppliedViewModel ?: viewModel(factory = PreferencesViewModel.factory(context))
    val screenState by preferencesViewModel.state.collectAsStateWithLifecycle()
    val currentUser by preferencesViewModel.currentUser.collectAsStateWithLifecycle()
    // Draft belongs to this destination. Popping it without Save discards changes.
    var intensity by rememberSaveable { mutableStateOf(savedIntensity) }
    var activities by rememberSaveable { mutableStateOf(savedActivities.toList()) }
    var weeklyMinutes by rememberSaveable { mutableStateOf(savedWeeklyMinutes.toString()) }
    var goal by rememberSaveable { mutableStateOf(savedGoal) }
    var environment by rememberSaveable { mutableStateOf("No preference") }
    var loadedUserId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(screenState.preferences) {
        val saved = screenState.preferences?.takeIf { it.userId == currentUser?.userId } ?: return@LaunchedEffect
        if (loadedUserId != saved.userId) {
            intensity = intensityToText(saved.preferredIntensity)
            activities = saved.activityNames.toList()
            weeklyMinutes = saved.weeklyGoalMinutes.toString()
            goal = saved.goal
            environment = environmentToText(saved.environment)
            loadedUserId = saved.userId
        }
    }

    LaunchedEffect(screenState.isSaved) {
        if (screenState.isSaved) {
            preferencesViewModel.clearSaved()
            onSave(intensity, activities.toList(), goal, weeklyMinutes.toInt())
        }
    }
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = AppSurface,
        labelColor = TextInk,
        selectedContainerColor = AppAccent,
        selectedLabelColor = AccentInk
    )

    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Exercise Preferences", color = TextInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Choose what works for you.", color = TextMuted, fontSize = 16.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = AppSurface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Preferred intensity", color = TextInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Easy", "Medium", "High").forEach { option ->
                        FilterChip(
                            selected = intensity == option,
                            onClick = { intensity = option },
                            label = { Text(option) },
                            shape = ButtonShape,
                            colors = chipColors
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = AppSurface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Preferred environment", color = TextInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Indoor", "Outdoor", "No preference").forEach { option ->
                        FilterChip(
                            selected = environment == option,
                            onClick = { environment = option },
                            label = { Text(option) },
                            shape = ButtonShape,
                            colors = chipColors
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = AppSurface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Preferred activities", color = TextInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Select any that you enjoy.", color = TextMuted, fontSize = 14.sp)
                listOf("Walking", "Jogging", "Hiking", "Swimming", "Badminton", "Gym")
                    .chunked(2).forEach { options ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.forEach { option ->
                                FilterChip(
                                    selected = option in activities,
                                    onClick = {
                                        activities = if (option in activities) activities - option else activities + option
                                    },
                                    label = { Text(option) },
                                    shape = ButtonShape,
                                    colors = chipColors
                                )
                            }
                        }
                    }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = AppSurface)
        ) {
            Column(
                Modifier.padding(20.dp).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Fitness goal", color = TextInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                listOf("Stay Active", "Improve Fitness", "Exercise More Regularly").forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = goal == option, role = Role.RadioButton, onClick = { goal = option }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = goal == option,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = AppPrimary, unselectedColor = TextMuted)
                        )
                        Text(option, color = TextInk, fontSize = 16.sp)
                    }
                }
            }
        }
        com.example.movesmart.ui.components.ChoiceField("Weekly movement goal (minutes)", weeklyMinutes,
            listOf("90", "120", "150", "180", "210"), { weeklyMinutes = it })
        Text("Your weekly target appears in Statistics.", color = TextMuted)
        if (screenState.errorMessage != null) {
            Text(
                screenState.errorMessage.orEmpty(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        }
        Button(
            onClick = {
                val minutes = weeklyMinutes.toIntOrNull()
                if (minutes == null) {
                    return@Button
                }
                if (currentUser == null) {
                    onSave(intensity, activities.toList(), goal, minutes)
                } else {
                    preferencesViewModel.save(intensity, activities.toList(), goal, minutes, environment)
                }
            },
            enabled = !screenState.isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = AccentInk)
        ) {
            if (screenState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
            } else {
                Text("Save Preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
