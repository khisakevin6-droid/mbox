package com.mbox.stream.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MboxRed = Color(0xFFE53935)

private val Dark = darkColorScheme(
    primary = MboxRed,
    onPrimary = Color.White,
    secondary = Color(0xFFFF8A80),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFEDEDF0),
    surface = Color(0xFF16161C),
    onSurface = Color(0xFFEDEDF0),
    surfaceVariant = Color(0xFF22222B),
    onSurfaceVariant = Color(0xFFB4B4BE)
)

private val Light = lightColorScheme(
    primary = Color(0xFFC62828),
    onPrimary = Color.White,
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF111116),
    surface = Color.White,
    onSurface = Color(0xFF111116),
    surfaceVariant = Color(0xFFEBEBF0),
    onSurfaceVariant = Color(0xFF55555F)
)

@Composable
fun MboxTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
