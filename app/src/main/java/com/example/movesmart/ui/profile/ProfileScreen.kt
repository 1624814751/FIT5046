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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movesmart.AccentInk
import com.example.movesmart.AppAccent
import com.example.movesmart.AppBackground
import com.example.movesmart.AppPrimary
import com.example.movesmart.AppSurface
import com.example.movesmart.ButtonShape
import com.example.movesmart.CardShape
import com.example.movesmart.TextInk
import com.example.movesmart.TextMuted
import com.example.movesmart.shared.UiState
import com.example.movesmart.ui.auth.AuthViewModel
import com.example.movesmart.ui.preferences.PreferencesViewModel
import com.example.movesmart.ui.preferences.environmentToText
import com.example.movesmart.ui.preferences.intensityToText

// Profile screen: Display user information
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
    onLogout: () -> Unit,
    isDemoMode: Boolean,
    authViewModel: AuthViewModel,
    preferencesViewModel: PreferencesViewModel
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val account by authViewModel.currentUser.collectAsStateWithLifecycle()
    val preferencesState by preferencesViewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by remember(account?.userId, isDemoMode) { mutableStateOf(false) }

    val shownName = if (isDemoMode) displayName else account?.displayName ?: "Signed out"
    val shownEmail = if (isDemoMode) email else account?.email.orEmpty()
    val savedPreferences = preferencesState.preferences?.takeIf {
        !isDemoMode && account != null && it.userId == account?.userId
    }
    val shownIntensity = if (isDemoMode) intensity else savedPreferences?.let { intensityToText(it.preferredIntensity) }
    val shownActivities = if (isDemoMode) activities else savedPreferences?.activityNames
    val shownGoal = if (isDemoMode) goal else savedPreferences?.goal
    val shownWeeklyMinutes = if (isDemoMode) weeklyMinutes else savedPreferences?.weeklyGoalMinutes
    val isAuthLoading = !isDemoMode && authState == UiState.Loading
    val preferencesLoading = !isDemoMode && account != null &&
        (preferencesState.isLoading || preferencesState.userId != account?.userId)
    val preferencesError = preferencesState.errorMessage.takeIf {
        !isDemoMode && account != null && preferencesState.userId == account?.userId
    }
    val isLoading = isAuthLoading || preferencesLoading
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
                        Text(shownName.take(1).uppercase(), color = AccentInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(shownName, color = TextInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(shownEmail, color = TextMuted, fontSize = 14.sp)
                    }
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
                if (!isDemoMode && (savedPreferences == null || preferencesLoading || preferencesError != null)) {
                    Text(when {
                        account == null -> "Sign in to view your preferences."
                        preferencesLoading -> "Loading preferences..."
                        preferencesError != null -> "Could not load preferences."
                        else -> "No preferences saved yet."
                    }, color = TextMuted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Preferred intensity", color = TextMuted, fontSize = 14.sp)
                        Text(shownIntensity.orEmpty(), color = TextInk, fontSize = 16.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Preferred activities", color = TextMuted, fontSize = 14.sp)
                        Text(shownActivities.orEmpty().joinToString(", ").ifEmpty { "None selected" }, color = TextInk, fontSize = 16.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Goal", color = TextMuted, fontSize = 14.sp)
                        Text(shownGoal.orEmpty(), color = TextInk, fontSize = 16.sp)
                    }
                    savedPreferences?.let {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Environment", color = TextMuted, fontSize = 14.sp)
                            Text(environmentToText(it.environment), color = TextInk, fontSize = 16.sp)
                        }
                    }
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
        if (shownWeeklyMinutes != null && !preferencesLoading && preferencesError == null) {
            Text("Weekly goal: $shownWeeklyMinutes minutes", color = TextMuted)
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
        val errorMessage = if (isDemoMode) null else (authState as? UiState.Error)?.message ?: preferencesError
        if (errorMessage != null) {
            Text(errorMessage, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = onReminders, modifier = Modifier.fillMaxWidth()) {
            Text("Reminder settings · ${if (reminderEnabled) "On" else "Off"}")
        }
        OutlinedButton(onClick = onStatistics, modifier = Modifier.fillMaxWidth()) { Text("View statistics") }
        OutlinedButton(
            onClick = {
                confirmLogout = true
            },
            enabled = !isAuthLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppPrimary)
        ) {
            Text("Log out", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
        title = { Text(if (isDemoMode) "Leave the demo?" else "Log out?") },
        text = {
            Text(
                if (isDemoMode) "Your demo edits will be reset. You can explore again from the login screen."
                else "Your saved preferences will stay on this device for your account."
            )
        },
        confirmButton = { TextButton(onClick = {
            confirmLogout = false
            if (isDemoMode) {
                onLogout()
            } else {
                authViewModel.logout(onSignedOut = onLogout)
            }
        }) { Text("Log out") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } })
}
