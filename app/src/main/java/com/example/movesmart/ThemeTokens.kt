package com.example.movesmart

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Member A's styling names resolve to the shared light/dark Material theme.
val AppPrimary: Color @Composable get() = MaterialTheme.colorScheme.primary
val AppAccent: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val AppBackground: Color @Composable get() = MaterialTheme.colorScheme.background
val AppSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val TextInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val AccentInk: Color @Composable get() = MaterialTheme.colorScheme.onSecondaryContainer
val ButtonShape = RoundedCornerShape(28.dp)
val CardShape = RoundedCornerShape(20.dp)
