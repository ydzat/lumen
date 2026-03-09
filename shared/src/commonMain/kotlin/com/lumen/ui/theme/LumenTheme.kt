package com.lumen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

object ThemeState {
    var mode by mutableStateOf("system")
}

// VSCode-inspired Light theme
private val LumenLightColorScheme = lightColorScheme(
    primary = Color(0xFF0066B8),               // VSCode blue (slightly deeper for contrast)
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF4F6272),              // Muted blue-gray
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E4F9),
    onSecondaryContainer = Color(0xFF0B1D2C),
    tertiary = Color(0xFF6B5778),               // Subtle purple accent
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251432),
    error = Color(0xFFCD3131),                  // VSCode error red
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410001),
    background = Color(0xFFFFFFFF),             // VSCode light background
    onBackground = Color(0xFF1F1F1F),           // VSCode light text
    surface = Color(0xFFF8F8F8),                // VSCode editor background
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE8E8E8),         // VSCode sidebar
    onSurfaceVariant = Color(0xFF616161),       // VSCode secondary text
    outline = Color(0xFFD4D4D4),                // VSCode border
    outlineVariant = Color(0xFFE5E5E5),
    surfaceContainerHighest = Color(0xFFECECEC),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainer = Color(0xFFF3F3F3),       // VSCode title bar
    surfaceContainerLow = Color(0xFFF8F8F8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

// VSCode-inspired Dark theme
private val LumenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF75BEFF),                // VSCode link blue (light for dark bg)
    onPrimary = Color(0xFF003060),
    primaryContainer = Color(0xFF004788),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFB6CAD9),              // Muted blue-gray
    onSecondary = Color(0xFF213342),
    secondaryContainer = Color(0xFF374959),
    onSecondaryContainer = Color(0xFFD2E4F9),
    tertiary = Color(0xFFD5BEE3),               // Subtle purple accent
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF524060),
    onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFF48771),                  // VSCode error (warm red)
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF1E1E1E),             // VSCode dark background
    onBackground = Color(0xFFD4D4D4),           // VSCode dark text
    surface = Color(0xFF252526),                // VSCode editor background
    onSurface = Color(0xFFD4D4D4),
    surfaceVariant = Color(0xFF333333),          // VSCode sidebar
    onSurfaceVariant = Color(0xFF858585),        // VSCode secondary text
    outline = Color(0xFF474747),                // VSCode border
    outlineVariant = Color(0xFF3C3C3C),
    surfaceContainerHighest = Color(0xFF3C3C3C),
    surfaceContainerHigh = Color(0xFF333333),
    surfaceContainer = Color(0xFF2D2D2D),       // VSCode panels
    surfaceContainerLow = Color(0xFF252526),
    surfaceContainerLowest = Color(0xFF1E1E1E),
)

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    val darkTheme = when (ThemeState.mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) LumenDarkColorScheme else LumenLightColorScheme
    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            content()
        }
    }
}
