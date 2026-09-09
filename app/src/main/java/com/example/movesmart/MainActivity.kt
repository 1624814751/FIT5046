package com.example.movesmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.movesmart.navigation.MoveSmartApp
import com.example.movesmart.ui.theme.MoveSmartTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MoveSmartTheme { MoveSmartApp() } }
    }
}
