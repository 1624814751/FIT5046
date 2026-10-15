package com.example.movesmart.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.movesmart.MainActivity
import com.example.movesmart.R
import com.example.movesmart.shared.ReminderSettings

class ReminderNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannel(NotificationChannel(SOUND_CHANNEL, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(SILENT_CHANNEL, "Quiet workout reminders", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null); enableVibration(false)
        })
    }

    fun canNotify(sound: Boolean): Boolean {
        createChannels()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(if (sound) SOUND_CHANNEL else SILENT_CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(settings: ReminderSettings, occurrence: Long = System.currentTimeMillis()): Boolean {
        if (!canNotify(settings.sound)) return false
        val launch = PendingIntent.getActivity(context, 4102,
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_REMINDERS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, if (settings.sound) SOUND_CHANNEL else SILENT_CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(settings.label)
            .setContentText("Make time for movement. Open MoveSmart to plan your activity.")
            .setContentIntent(launch).setAutoCancel(true)
            .setWhen(occurrence)
            .setOnlyAlertOnce(manager.activeNotifications.any {
                it.tag == settings.userId && it.id == NOTIFICATION_ID && it.notification.`when` == occurrence
            })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        // The permission can be revoked between the check and notify.
        return runCatching { manager.notify(settings.userId, NOTIFICATION_ID, notification); true }.getOrDefault(false)
    }

    fun cancel(userId: String) = manager.cancel(userId, NOTIFICATION_ID)

    companion object {
        const val ACTION_OPEN_REMINDERS = "com.example.movesmart.OPEN_REMINDERS"
        const val SOUND_CHANNEL = "workout_reminders_sound_v1"
        const val SILENT_CHANNEL = "workout_reminders_silent_v1"
        private const val NOTIFICATION_ID = 4102
    }
}
