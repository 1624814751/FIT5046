package com.example.movesmart

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.movesmart.reminder.ReminderService
import com.example.movesmart.shared.ReminderSettings
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderRecoveryTest {
    private lateinit var app: Application
    private lateinit var service: ReminderService
    private lateinit var manager: NotificationManager
    private var now = Instant.parse("2026-10-16T00:00:00Z")
    private val settings = ReminderSettings("account-a", true, "Recovery test", 1, 0,
        (1..7).toSet(), false, "UTC")

    @Before fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("workout_reminders", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = app.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        service = ReminderService(app) { now }
        service.activate(settings.userId).getOrThrow()
        service.save(settings).getOrThrow()
    }

    @Test fun overdueAlarmIsDeliveredOnRestoreAndOldBroadcastDoesNotDeliverAgain() = runBlocking {
        val due = service.store.scheduledAt(settings.userId)
        now = now.plusSeconds(7200)
        service.restore().getOrThrow()
        assertEquals(1, manager.activeNotifications.size)
        assertTrue(service.store.scheduledAt(settings.userId) > now.toEpochMilli())
        val posted = manager.activeNotifications.single().notification
        service.deliver(settings.userId, due)
        service.restore().getOrThrow()
        assertSame(posted, manager.activeNotifications.single().notification)
        assertEquals(0L, service.store.pendingDelivery(settings.userId))
    }

    @Test fun coldStartActivationKeepsAlreadyDeliveredNotification() = runBlocking {
        val due = service.store.scheduledAt(settings.userId)
        now = Instant.ofEpochMilli(due)
        service.deliver(settings.userId, due)
        val posted = manager.activeNotifications.single().notification
        val restarted = ReminderService(app) { now }
        restarted.activate(settings.userId).getOrThrow()
        assertSame(posted, manager.activeNotifications.single().notification)
    }

    @Test fun coldStartBeforeDelayedBroadcastRecoversDueOccurrence() = runBlocking {
        now = now.plusSeconds(7200)
        val restarted = ReminderService(app) { now }
        restarted.activate(settings.userId).getOrThrow()
        assertEquals(1, manager.activeNotifications.size)
        assertTrue(restarted.store.scheduledAt(settings.userId) > now.toEpochMilli())
    }

    @Test fun futureOccurrenceIsPreservedDuringRestore() = runBlocking {
        val due = service.store.scheduledAt(settings.userId)
        service.restore().getOrThrow()
        assertEquals(due, service.store.scheduledAt(settings.userId))
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test fun pendingDeliverySurvivesProcessRestartAndPermissionRecovery() = runBlocking {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        now = now.plusSeconds(7200)
        service.restore().getOrThrow()
        assertTrue(service.store.pendingDelivery(settings.userId) > 0)
        assertEquals(0, manager.activeNotifications.size)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val restarted = ReminderService(app) { now }
        restarted.restore().getOrThrow()
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(0L, restarted.store.pendingDelivery(settings.userId))
    }

    @Test fun explicitDisableClearsNotificationAndPendingDelivery() = runBlocking {
        now = now.plusSeconds(7200)
        service.restore().getOrThrow()
        service.save(settings.copy(enabled = false)).getOrThrow()
        service.restore().getOrThrow()
        assertEquals(0, manager.activeNotifications.size)
        assertEquals(0L, service.store.pendingDelivery(settings.userId))
        assertEquals(0L, service.store.scheduledAt(settings.userId))
    }

    @Test fun accountChangeCancelsOldDueOccurrenceWithoutLeakingNotification() = runBlocking {
        val due = service.store.scheduledAt(settings.userId)
        now = now.plusSeconds(7200)
        service.activate("account-b").getOrThrow()
        service.deliver(settings.userId, due)
        service.restore().getOrThrow()
        assertEquals(0, manager.activeNotifications.size)
        assertEquals(0L, service.store.pendingDelivery(settings.userId))
    }
}
