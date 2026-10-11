package com.example.movesmart.reminder

import android.content.Context
import android.util.Log
import com.example.movesmart.shared.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes save, logout, restore and delivery so an obsolete alarm cannot beat an edit. */
class ReminderService private constructor(context: Context) {
    val store = ReminderStore(context)
    val notifications = ReminderNotifications(context)
    private val scheduler = AndroidReminderScheduler(context, store, notifications)
    private val mutex = Mutex()

    suspend fun activate(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                require(userId.isNotBlank())
                store.activeUser?.takeIf { it != userId }?.let { scheduler.cancel(it).getOrThrow() }
                store.activeUser = userId
                store.read(userId)?.let { scheduler.schedule(it).getOrThrow() }
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
        mutex.withLock { runCatching { store.activeUser?.let { id ->
            val settings = store.read(id)
            if (settings == null) scheduler.cancel(id).getOrThrow() else scheduler.schedule(settings).getOrThrow()
        }; Unit } }
    }

    suspend fun deliver(userId: String, scheduledAt: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (store.activeUser != userId || scheduledAt <= 0 || store.scheduledAt(userId) != scheduledAt) return@withLock
            val settings = store.read(userId) ?: return@withLock
            if (!settings.enabled) return@withLock
            store.setScheduledAt(userId, 0) // Consume once, including duplicate broadcasts.
            Log.i(AndroidReminderScheduler.TAG, "Alarm triggered")
            // Schedule the next calendar occurrence before posting; cancel() also removes old notices.
            val result = scheduler.schedule(settings)
            val posted = notifications.show(settings)
            Log.i(AndroidReminderScheduler.TAG, if (posted) "Notification sent" else "Notification blocked by system settings")
            result.exceptionOrNull()?.let { Log.w(AndroidReminderScheduler.TAG, "Next reminder could not be scheduled", it) }
        }
    }

    companion object {
        @Volatile private var instance: ReminderService? = null
        fun get(context: Context): ReminderService = instance ?: synchronized(this) {
            instance ?: ReminderService(context.applicationContext).also { instance = it }
        }
    }
}
