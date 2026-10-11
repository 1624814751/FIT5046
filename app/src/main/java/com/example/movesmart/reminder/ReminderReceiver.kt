package com.example.movesmart.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidReminderScheduler.ACTION_FIRE) return
        val userId = intent.getStringExtra(AndroidReminderScheduler.EXTRA_USER) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderService.get(context).deliver(userId, intent.getLongExtra(AndroidReminderScheduler.EXTRA_TIME, 0))
            } catch (failure: Exception) {
                Log.e(AndroidReminderScheduler.TAG, "Reminder delivery failed", failure)
            } finally { pending.finish() }
        }
    }
}

class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderService.get(context).restore().onFailure {
                    Log.w(AndroidReminderScheduler.TAG, "Reminder restore failed", it)
                }
            } finally { pending.finish() }
        }
    }
}
