package com.brian.grokbar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ElectricBlue = Color(0xFF1A5EFF)
val GaugeBackground = Color(0xFF0A0A0A)
val GaugeSurface = Color(0xFF141414)
val GaugeTrack = Color(0xFF2A2A2A)
val GaugeMuted = Color(0xFF9A9A9A)
val GaugeText = Color(0xFFF5F5F5)
val GaugeWarn = Color(0xFFF5A524)
val GaugeDanger = Color(0xFFE5484D)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF80CBC4),
    background = GaugeBackground,
    surface = GaugeSurface,
    onBackground = GaugeText,
    onSurface = GaugeText,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = GaugeMuted
)

@Composable
fun GrokBarTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
