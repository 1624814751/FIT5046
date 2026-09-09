package com.example.movesmart.ui.screens

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import com.example.movesmart.model.Preferences
import com.example.movesmart.model.ReminderSettings
import com.example.movesmart.ui.components.*

// Integration skeletons for member A. No credentials are stored or sent.
@Composable
fun AccountScreen(signUp: Boolean, onContinue: () -> Unit, onAlternate: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val badEmail = !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val badPassword = if (signUp) password.length < 12 else password.isBlank()
    ScreenColumn {
        SectionHeading(if (signUp) "Start your MoveSmart journey" else "Move well. Feel better.", "Movement that fits your day, your surroundings and you.")
        InfoCard(highlighted = true) {
            Text("A routine that adapts to you", style = MaterialTheme.typography.titleLarge)
            Text("Explore the sample experience. Use a made-up email and password; this preview has no real accounts.")
        }
        if (signUp) OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true,
            isError = attempted && name.isBlank(), supportingText = { if (attempted && name.isBlank()) Text("Enter a display name.") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            isError = attempted && badEmail, supportingText = { if (attempted && badEmail) Text("Use an email like alex@example.com.") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = { IconButton({ visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (visible) "Hide password" else "Show password")
            } },
            supportingText = { Text(if (signUp) "Use a memorable passphrase of at least 12 characters." else if (attempted && badPassword) "Enter your demo password." else "Your password is hidden. Use the eye to check it.") },
            isError = attempted && badPassword, modifier = Modifier.fillMaxWidth())
        PrimaryButton(if (signUp) "Create demo account" else "Log in to demo", {
            attempted = true
            if (!badEmail && !badPassword && (!signUp || name.isNotBlank())) onContinue()
        })
        if (!signUp) OutlinedButton(onContinue, modifier = Modifier.fillMaxWidth()) { Text("Explore demo") }
        TextButton(onAlternate, modifier = Modifier.fillMaxWidth()) { Text(if (signUp) "Already have an account? Log in" else "New here? Sign up") }
    }
}

@Composable
fun PreferencesScreen(saved: Preferences, onSave: (Preferences) -> Unit) {
    var goal by rememberSaveable(saved) { mutableStateOf(saved.goal) }
    var intensity by rememberSaveable(saved) { mutableStateOf(saved.intensity) }
    var minutes by rememberSaveable(saved) { mutableStateOf(saved.weeklyMinutes.toString()) }
    ScreenColumn {
        SectionHeading("Build your routine", "Choose a starting point. You can change it any time.")
        ChoiceField("Your goal", goal, listOf("Build a routine", "Improve endurance", "Stay active"), { goal = it })
        ChoiceField("Preferred intensity", intensity, listOf("Gentle", "Moderate", "Vigorous"), { intensity = it })
        ChoiceField("Weekly movement goal (minutes)", minutes, listOf("90", "120", "150", "180", "210"), { minutes = it })
        InfoCard(highlighted = true) { Text("Make it achievable"); Text("Your weekly target appears in Statistics. Your intensity is used in your workout recommendation.") }
        PrimaryButton("Save preferences", { onSave(Preferences(goal, intensity, minutes.toInt())) })
    }
}

@Composable
fun ProfileScreen(preferences: Preferences, reminder: ReminderSettings, onPreferences: () -> Unit, onReminders: () -> Unit, onStatistics: () -> Unit, onLogout: () -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    ScreenColumn {
        SectionHeading("Your profile", "Make MoveSmart work for you.")
        InfoCard(highlighted = true) {
            Text("Demo member", style = MaterialTheme.typography.headlineSmall)
            Text("${preferences.goal} · ${preferences.weeklyMinutes} min / week")
        }
        PrimaryButton("Edit preferences", onPreferences)
        OutlinedButton(onReminders, modifier = Modifier.fillMaxWidth()) { Text("Reminder settings · ${if (reminder.enabled) "On" else "Off"}") }
        OutlinedButton(onStatistics, modifier = Modifier.fillMaxWidth()) { Text("View statistics") }
        Text("Changes are kept for this demo session. No account or workout data is uploaded.", style = MaterialTheme.typography.bodySmall)
        TextButton({ confirmLogout = true }, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("Leave the demo?") },
        text = { Text("Your demo edits will be reset. You can explore again from the login screen.") },
        confirmButton = { TextButton({ confirmLogout = false; onLogout() }) { Text("Log out") } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("Cancel") } })
}
