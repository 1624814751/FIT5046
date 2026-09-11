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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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
    onSave: (String, List<String>, String, Int) -> Unit
) {
    // Draft belongs to this destination. Popping it without Save discards changes.
    var intensity by rememberSaveable { mutableStateOf(savedIntensity) }
    var activities by rememberSaveable { mutableStateOf(ArrayList(savedActivities)) }
    var weeklyMinutes by rememberSaveable { mutableStateOf(savedWeeklyMinutes.toString()) }
    var goal by rememberSaveable { mutableStateOf(savedGoal) }
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
                                        activities = ArrayList(
                                            if (option in activities) activities - option else activities + option
                                        )
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
        Button(
            onClick = { onSave(intensity, activities.toList(), goal, weeklyMinutes.toInt()) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = AccentInk)
        ) {
            Text("Save Preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
