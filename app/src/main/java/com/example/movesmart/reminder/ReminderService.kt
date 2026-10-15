package com.example.movesmart.reminder

import android.content.Context
import android.util.Log
import java.time.Instant
import com.example.movesmart.shared.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes save, logout, restore and delivery so an obsolete alarm cannot beat an edit. */
class ReminderService internal constructor(context: Context, private val now: () -> Instant = Instant::now) {
    val store = ReminderStore(context)
    val notifications = ReminderNotifications(context)
    private val scheduler = AndroidReminderScheduler(context, store, notifications, now)
    private val mutex = Mutex()

    suspend fun activate(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                require(userId.isNotBlank())
                store.activeUser?.takeIf { it != userId }?.let { scheduler.cancel(it).getOrThrow() }
                store.activeUser = userId
                restoreLocked()
                Unit
            }
        }
    }

    suspend fun save(settings: ReminderSettings): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                check(store.activeUser == settings.userId) { "Sign in before saving a reminder." }
                require(settings.validationError() == null) { settings.validationError().orEmpty() }
                store.save(settings).getOrThrow()
                // An explicit edit replaces the old occurrence; restore must never do this.
                scheduler.cancel(settings.userId).getOrThrow()
                scheduler.schedule(settings).getOrThrow()
            }
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                // Clear first: even a delivery already queued must no longer notify this user.
                val userId = store.activeUser
                store.activeUser = null
                userId?.let { scheduler.cancel(it).getOrThrow() }
                Unit
            }
        }
    }

    suspend fun restore(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { restoreLocked() } }
    }

    // Caller holds mutex. A stored overdue occurrence is delivered once, not skipped to next week.
    private suspend fun restoreLocked() {
        val id = store.activeUser ?: return
        val settings = store.read(id)
        if (settings == null || !settings.enabled) {
            scheduler.cancel(id).getOrThrow()
            return
        }
        val due = store.scheduledAt(id)
        if (due > 0 && due <= now().toEpochMilli()) {
            deliverLocked(settings, due)
        } else {
            val restored = scheduler.restore(settings)
            postPending(settings)
            restored.getOrThrow()
        }
    }

    suspend fun deliver(userId: String, scheduledAt: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (store.activeUser != userId || scheduledAt <= 0 || store.scheduledAt(userId) != scheduledAt ||
                scheduledAt > now().toEpochMilli()) return@withLock
            val settings = store.read(userId) ?: return@withLock
            if (!settings.enabled) return@withLock
            deliverLocked(settings, scheduledAt)
        }
    }

    private suspend fun deliverLocked(settings: ReminderSettings, scheduledAt: Long) {
        // Persist delivery before consuming the alarm. If the process stops, restore retries the
        // same notification ID instead of silently losing the due event. Missed weeks coalesce.
        store.setPendingDelivery(settings.userId, scheduledAt)
        store.setScheduledAt(settings.userId, 0)
        Log.i(AndroidReminderScheduler.TAG, "Alarm triggered")
        val result = scheduler.schedule(settings)
        postPending(settings)
        result.exceptionOrNull()?.let { Log.w(AndroidReminderScheduler.TAG, "Next reminder could not be scheduled", it) }
    }

    private fun postPending(settings: ReminderSettings) {
        val occurrence = store.pendingDelivery(settings.userId)
        if (occurrence <= 0) return
        val posted = notifications.show(settings, occurrence)
        if (posted) store.setPendingDelivery(settings.userId, 0)
        Log.i(AndroidReminderScheduler.TAG, if (posted) "Notification sent" else "Notification pending system permission")
    }

    companion object {
        @Volatile private var instance: ReminderService? = null
        fun get(context: Context): ReminderService = instance ?: synchronized(this) {
            instance ?: ReminderService(context.applicationContext).also { instance = it }
        }
    }
}
