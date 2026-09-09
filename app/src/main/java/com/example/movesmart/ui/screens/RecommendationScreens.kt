package com.example.movesmart.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import com.example.movesmart.model.*
import com.example.movesmart.ui.components.*

// Integration skeletons for member B; weather/sensor scenarios are explicit fixtures.
@Composable
fun HomeScreen(preferences: Preferences, rainy: Boolean, onPlan: () -> Unit, onSearch: () -> Unit, onReminders: () -> Unit) {
    ScreenColumn {
        SectionHeading("Find your next move", "A little movement can make a better day.")
        SampleDataNote()
        InfoCard(highlighted = true) {
            Text("YOUR CONTEXT", style = MaterialTheme.typography.labelMedium)
            Text(if (rainy) "16°C · Rainy" else "21°C · Clear skies", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("4,320 steps · 78 bpm at rest")
            Text("Goal: ${preferences.goal}")
        }
        InfoCard {
            Text("Recommended for you", style = MaterialTheme.typography.titleLarge)
            Text(if (rainy) "Indoor mobility" else "Outdoor walk", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${if (rainy) "20" else "30"} min · ${preferences.intensity} intensity")
            Text(if (rainy) "A sheltered option for a wet day." else "Clear weather and your preferences make a walk a good fit.")
            PrimaryButton("View workout plan", onPlan)
        }
        OutlinedButton(onSearch, modifier = Modifier.fillMaxWidth()) { Text("Find an activity") }
        TextButton(onReminders, modifier = Modifier.fillMaxWidth()) { Text("Set a workout reminder") }
    }
}

@Composable
fun RecommendationScreen(preferences: Preferences, rainy: Boolean, onContextChange: (Boolean) -> Unit, onStart: (String) -> Unit) {
    val title = if (rainy) "Indoor Mobility" else "Outdoor Walk"
    ScreenColumn {
        SectionHeading("A plan for right now", "See why it fits, then make it yours.")
        SampleDataNote()
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Try a weather change", fontWeight = FontWeight.Bold)
                    Text(if (rainy) "Rainy · Plan adjusted indoors" else "Clear · Outdoor plan", style = MaterialTheme.typography.bodySmall)
                }
                Switch(rainy, onContextChange, modifier = Modifier.semantics { contentDescription = "Simulate rainy weather" })
            }
        }
        InfoCard(highlighted = true) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${if (rainy) 20 else 30} minutes · ${preferences.intensity} intensity")
            Text("Why this plan?", style = MaterialTheme.typography.titleMedium)
            Text(if (rainy) "Rain changes the plan to an indoor session. Your movement goal and preferred intensity stay in view." else "Clear weather, a light activity level and your preferences point to an outdoor session.")
        }
        InfoCard {
            Text("Your session", style = MaterialTheme.typography.titleLarge)
            Text("1. Warm up · 5 min")
            Text(if (rainy) "2. Gentle mobility · 10 min" else "2. Comfortable walk · 20 min")
            Text("3. Cool down · 5 min")
        }
        Text("Choose a pace that feels comfortable. You can pause or finish at any point.", style = MaterialTheme.typography.bodyMedium)
        PrimaryButton("Start workout", { onStart(title) })
    }
}
