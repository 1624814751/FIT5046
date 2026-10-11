package com.example.movesmart.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun ProfileScreen(
    intensity: String,
    activities: List<String>,
    goal: String,
    displayName: String,
    email: String,
    weeklyMinutes: Int,
    reminderEnabled: Boolean,
    onReminders: () -> Unit,
    onStatistics: () -> Unit,
    onEditPreferences: () -> Unit,
    onLogout: () -> Unit
) {
    var confirmLogout by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Profile", color = TextInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = AppSurface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier.size(64.dp).background(AppAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(displayName.take(1).uppercase(), color = AccentInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(displayName, color = TextInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(email, color = TextMuted, fontSize = 14.sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Fitness Level", color = TextMuted, fontSize = 14.sp)
                    Text("Beginner", color = TextInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = AppSurface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Exercise Preferences", color = TextInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Preferred intensity", color = TextMuted, fontSize = 14.sp)
                    Text(intensity, color = TextInk, fontSize = 16.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Preferred activities", color = TextMuted, fontSize = 14.sp)
                    Text(activities.joinToString(", ").ifEmpty { "None selected" }, color = TextInk, fontSize = 16.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Goal", color = TextMuted, fontSize = 14.sp)
                    Text(goal, color = TextInk, fontSize = 16.sp)
                }
            }
        }
        Button(
            onClick = onEditPreferences,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = AccentInk)
        ) {
            Text("Edit Preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text("Weekly goal: $weeklyMinutes minutes", color = TextMuted)
        OutlinedButton(onClick = onReminders, modifier = Modifier.fillMaxWidth()) {
            Text("Reminder settings · ${if (reminderEnabled) "On" else "Off"}")
        }
        OutlinedButton(onClick = onStatistics, modifier = Modifier.fillMaxWidth()) { Text("View statistics") }
        OutlinedButton(
            onClick = { confirmLogout = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppPrimary)
        ) {
            Text("Log out", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
        title = { Text("Leave the demo?") },
        text = { Text("Your demo edits will be reset. You can explore again from the login screen.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Log out") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } })
}
