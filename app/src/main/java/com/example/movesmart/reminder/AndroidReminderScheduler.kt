package com.example.movesmart.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.movesmart.shared.ReminderScheduler
import com.example.movesmart.shared.ReminderSettings
import java.time.Instant

class AndroidReminderScheduler(
    private val context: Context,
    private val store: ReminderStore,
    private val notifications: ReminderNotifications,
    private val now: () -> Instant = Instant::now
) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private fun intent(userId: String) = Intent(context, ReminderReceiver::class.java)
        .setAction(ACTION_FIRE).setData(Uri.Builder().scheme("movesmart").authority("reminder").appendPath(userId).build())
        .putExtra(EXTRA_USER, userId)

    override suspend fun schedule(settings: ReminderSettings): Result<Unit> = runCatching {
        require(settings.validationError() == null) { settings.validationError().orEmpty() }
        val next = nextReminderTime(settings, now()) ?: run { cancel(settings.userId).getOrThrow(); return@runCatching }
        arm(settings, next.toEpochMilli())
    }

    /** Re-arm the persisted occurrence after reboot/startup without advancing past a late alarm. */
    suspend fun restore(settings: ReminderSettings): Result<Unit> = runCatching {
        require(settings.validationError() == null) { settings.validationError().orEmpty() }
        if (!settings.enabled) { cancel(settings.userId).getOrThrow(); return@runCatching }
        val trigger = store.scheduledAt(settings.userId).takeIf { it > 0 }
            ?: nextReminderTime(settings, now())!!.toEpochMilli()
        arm(settings, trigger)
    }

    private fun arm(settings: ReminderSettings, trigger: Long) {
        check(store.activeUser == settings.userId) { "Sign in before scheduling a reminder." }
        check(notifications.canNotify(settings.sound)) { "Enable workout notifications in system settings, then save again." }
        val pending = PendingIntent.getBroadcast(context, 4101, intent(settings.userId).putExtra(EXTRA_TIME, trigger),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        store.setScheduledAt(settings.userId, trigger)
        try {
            // A fitness nudge tolerates delay; no special exact-alarm access is required.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } catch (failure: Exception) {
            pending.cancel()
            // Keep the occurrence persisted so a later restore can retry it.
            throw failure
        }
        Log.i(TAG, "Alarm scheduled for ${Instant.ofEpochMilli(trigger)}")
    }

    override suspend fun cancel(userId: String): Result<Unit> = runCatching {
        PendingIntent.getBroadcast(context, 4101, intent(userId), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarms.cancel(it); it.cancel()
        }
        store.setScheduledAt(userId, 0)
        store.setPendingDelivery(userId, 0)
        notifications.cancel(userId)
        Log.i(TAG, "Alarm cancelled")
    }

    companion object {
        const val TAG = "MoveSmartReminder"
        const val ACTION_FIRE = "com.example.movesmart.FIRE_REMINDER"
        const val EXTRA_USER = "reminder_user"
        const val EXTRA_TIME = "reminder_time"
    }
}
