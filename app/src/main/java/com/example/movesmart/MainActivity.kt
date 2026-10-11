package com.example.movesmart

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.movesmart.reminder.ReminderNotifications
import com.example.movesmart.navigation.MoveSmartApp
import com.example.movesmart.ui.theme.MoveSmartTheme

class MainActivity : ComponentActivity() {
    private var reminderRequest by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        reminderRequest = savedInstanceState?.getInt("reminder_request")
            ?: if (intent.action == ReminderNotifications.ACTION_OPEN_REMINDERS) 1 else 0
        setContent { MoveSmartTheme { MoveSmartApp(reminderRequest) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ReminderNotifications.ACTION_OPEN_REMINDERS) reminderRequest++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("reminder_request", reminderRequest)
        super.onSaveInstanceState(outState)
    }
}
