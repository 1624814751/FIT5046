package com.example.movesmart

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movesmart.reminder.*
import com.example.movesmart.shared.ReminderSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.ZoneId

/** Runs against Android's real SharedPreferences, AlarmManager, receiver and notification service. */
class ReminderIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val service = ReminderService.get(context)
    private val settings = ReminderSettings("reminder-test", true, "Test walk", 18, 30, setOf(1, 3, 5), true, ZoneId.systemDefault().id)

    @Before fun prepare() = runBlocking {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        service.logout().getOrThrow()
        context.getSharedPreferences("workout_reminders", Context.MODE_PRIVATE).edit().clear().commit()
        service.activate(settings.userId).getOrThrow()
    }
    @After fun cleanup() = runBlocking {
        service.logout().getOrThrow()
        context.getSharedPreferences("workout_reminders", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }
    private fun alarmIntent() = Intent(context, ReminderReceiver::class.java)
        .setAction(AndroidReminderScheduler.ACTION_FIRE)
        .setData(Uri.Builder().scheme("movesmart").authority("reminder").appendPath(settings.userId).build())
        .putExtra(AndroidReminderScheduler.EXTRA_USER, settings.userId)

    @Test fun settingsSurviveNewRepositoryAndAreUserScoped() = runBlocking {
        service.save(settings).getOrThrow()
        val reloaded = ReminderStore(context)
        assertEquals(settings, reloaded.read(settings.userId))
        assertNull(reloaded.read("another-user"))
        assertTrue(reloaded.scheduledAt(settings.userId) > System.currentTimeMillis())
    }

    @Test fun editingReplacesScheduleAndDisablingCancelsIt() = runBlocking {
        service.save(settings).getOrThrow()
        val oldTime = service.store.scheduledAt(settings.userId)
        service.save(settings.copy(hour = 19)).getOrThrow()
        assertNotEquals(oldTime, service.store.scheduledAt(settings.userId))
        service.save(settings.copy(enabled = false)).getOrThrow()
        assertEquals(0L, service.store.scheduledAt(settings.userId))
        assertNull(PendingIntent.getBroadcast(context, 4101, alarmIntent(), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
    }

    @Test fun logoutStopsOldUserAndSigningInRestoresSavedSettings() = runBlocking {
        service.save(settings).getOrThrow()
        service.activate("another-user").getOrThrow()
        assertEquals(0L, service.store.scheduledAt(settings.userId))
        service.activate(settings.userId).getOrThrow()
        assertTrue(service.store.scheduledAt(settings.userId) > System.currentTimeMillis())
        service.logout().getOrThrow()
        assertNull(service.store.activeUser)
        assertEquals(0L, service.store.scheduledAt(settings.userId))
        assertEquals(settings, service.store.read(settings.userId))
    }

    @Test fun bootRestorePathRecreatesMissingAlarm() = runBlocking {
        service.save(settings).getOrThrow()
        val pending = PendingIntent.getBroadcast(context, 4101, alarmIntent(), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)!!
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
        service.store.setScheduledAt(settings.userId, 0)
        service.restore().getOrThrow()
        assertTrue(service.store.scheduledAt(settings.userId) > System.currentTimeMillis())
        assertNotNull(PendingIntent.getBroadcast(context, 4101, alarmIntent(), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
    }

    @Test fun explicitReceiverDeliveryPostsNotificationAndRearms() = runBlocking {
        service.save(settings).getOrThrow()
        // Send the production broadcast directly: this tests the receiver without waiting
        // for Android's inexact alarm scheduling policy. Real alarm delivery is a manual check.
        val trigger = System.currentTimeMillis()
        service.store.setScheduledAt(settings.userId, trigger)
        val pending = PendingIntent.getBroadcast(context, 4101,
            alarmIntent().putExtra(AndroidReminderScheduler.EXTRA_TIME, trigger),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        pending.send()
        val manager = context.getSystemService(NotificationManager::class.java)
        // This bounds asynchronous receiver/notification processing, not alarm lateness.
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (manager.activeNotifications.none { it.tag == settings.userId } && SystemClock.elapsedRealtime() < deadline) {
            kotlinx.coroutines.delay(100)
        }
        val notification = manager.activeNotifications.firstOrNull { it.tag == settings.userId }
        assertNotNull("Explicit receiver delivery did not post a notification", notification)
        assertEquals("Test walk", notification!!.notification.extras.getString("android.title"))
        assertTrue(service.store.scheduledAt(settings.userId) > trigger)
        val newTime = service.store.scheduledAt(settings.userId)
        service.deliver(settings.userId, trigger) // Duplicate old broadcast is ignored.
        assertEquals(newTime, service.store.scheduledAt(settings.userId))
    }

    @Test fun silentReminderUsesSeparateChannel() = runBlocking {
        service.save(settings.copy(sound = false)).getOrThrow()
        assertTrue(service.notifications.show(settings.copy(sound = false)))
        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = manager.activeNotifications.first { it.tag == settings.userId }
        assertEquals(ReminderNotifications.SILENT_CHANNEL, posted.notification.channelId)
        assertNull(manager.getNotificationChannel(ReminderNotifications.SILENT_CHANNEL).sound)
    }
}
