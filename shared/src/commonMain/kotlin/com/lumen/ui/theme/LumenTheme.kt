package com.lumen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object ThemeState {
    var mode by mutableStateOf("system")
}

private val LumenLightColorScheme = lightColorScheme(
    primary = Color(0xFF3366CC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF556A8E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E3FF),
    onSecondaryContainer = Color(0xFF122449),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
)

private val LumenDarkColorScheme = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF1A4D99),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBBC7E4),
    onSecondary = Color(0xFF253A5E),
    secondaryContainer = Color(0xFF3C5176),
    onSecondaryContainer = Color(0xFFD9E3FF),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
)

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    val darkTheme = when (ThemeState.mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) LumenDarkColorScheme else LumenLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
