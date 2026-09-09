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
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onBack: () -> Unit,
    onEditPreferences: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground)
            .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = AppPrimary)) {
            Text("Back")
        }
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
                        Text("A", color = TextInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Alex", color = TextInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("alex@example.com", color = TextMuted, fontSize = 14.sp)
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
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = TextInk)
        ) {
            Text("Edit Preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppPrimary)
        ) {
            Text("Log Out", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
