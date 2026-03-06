package com.lumen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LumenLightColorScheme,
        content = content,
    )
}
