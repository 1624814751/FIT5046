package com.example.movesmart.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = MoveViolet, onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E7FF), onPrimaryContainer = Color(0xFF24276B),
    secondary = Color(0xFF46651C), secondaryContainer = MoveLime,
    onSecondaryContainer = MoveInk,
    background = MoveBackground, onBackground = MoveInk,
    surface = Color.White, onSurface = MoveInk,
    surfaceVariant = Color(0xFFF0F0F4), onSurfaceVariant = MoveMuted,
    outline = Color(0xFF74777F)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC2C1FF), onPrimary = Color(0xFF262971),
    primaryContainer = Color(0xFF3D409B), onPrimaryContainer = Color(0xFFE8E7FF),
    secondary = MoveLime, secondaryContainer = Color(0xFF344C0B),
    onSecondaryContainer = MoveLime,
    background = Color(0xFF121417), onBackground = Color(0xFFE7E8ED),
    surface = Color(0xFF1D2025), onSurface = Color(0xFFE7E8ED),
    surfaceVariant = Color(0xFF292D34), onSurfaceVariant = Color(0xFFBFC3CD)
)

@Composable
fun MoveSmartTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
