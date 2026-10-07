package com.example.movesmart.reminder

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.movesmart.model.ReminderSettings as FormSettings
import com.example.movesmart.shared.ReminderSettings
import com.example.movesmart.ui.screens.ReminderSettingsScreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun ReminderSettings.toForm() = FormSettings(enabled, label, hour, minute, days, sound)

@Composable
fun ReminderRoute(userId: String) {
    val context = LocalContext.current
    val service = remember(context) { ReminderService.get(context) }
    val flow = remember(service, userId) { service.store.observe(userId) }
    val saved by flow.collectAsState(initial = service.store.read(userId))
    val form = saved?.toForm() ?: FormSettings(enabled = false)
    val zone = saved?.zoneId ?: ZoneId.systemDefault().id
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var notificationAllowed by remember { mutableStateOf(service.notifications.canNotify(form.sound)) }
    var nextMillis by remember { mutableLongStateOf(service.store.scheduledAt(userId)) }
    fun refresh() {
        notificationAllowed = service.notifications.canNotify(saved?.sound ?: true)
        nextMillis = service.store.scheduledAt(userId)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh()
        error = !granted
        status = if (granted) "Notifications allowed. Save your reminder to schedule it."
            else "Notifications are blocked. You can enable them in system settings."
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, userId) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            scope.launch { service.restore(); refresh() }
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(saved) { refresh() }

    ReminderSettingsScreen(saved = form, isSaving = saving, timeZoneLabel = zone, statusContent = {
        Text("Saved on this device. Android may delay reminders to save battery.", style = MaterialTheme.typography.bodySmall)
        if (!notificationAllowed) {
            Text("Workout notifications are blocked.", color = MaterialTheme.colorScheme.error)
            OutlinedButton({ permission.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.fillMaxWidth()) {
                Text("Allow notifications")
            }
        }
        OutlinedButton({
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, Modifier.fillMaxWidth()) { Text("Notification settings") }
        if (saved?.enabled == true && notificationAllowed && nextMillis > 0) {
            val next = Instant.ofEpochMilli(nextMillis).atZone(ZoneId.of(zone))
            Text("Next reminder: ${next.format(DateTimeFormatter.ofPattern("EEE, d MMM, HH:mm"))} ($zone)",
                style = MaterialTheme.typography.bodyMedium)
        } else if (saved?.enabled == true) {
            Text("Settings saved, but no reminder is currently scheduled. Check notifications and save again.", color = MaterialTheme.colorScheme.error)
        }
    }, saveFeedback = {
        status?.let { Text(it, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }, onSave = { draft ->
        if (draft.enabled && !service.notifications.canNotify(draft.sound)) {
            error = true
            status = "Enable notifications, then save again. This reminder has not been scheduled."
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            saving = true
            scope.launch {
                val result = service.save(ReminderSettings(userId, draft.enabled, draft.label, draft.hour,
                    draft.minute, draft.days, draft.sound, zone))
                error = result.isFailure
                status = result.fold(
                    onSuccess = { if (draft.enabled) "Reminder saved and scheduled." else "Reminders are off. Scheduled notifications cancelled." },
                    onFailure = { it.message ?: "Could not schedule your reminder. Please try again." })
                saving = false
                refresh()
            }
        }
    })
}
