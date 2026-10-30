package com.brian.grokbar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.brian.grokbar.data.UsageProduct

val ElectricBlue = Color(0xFF1A5EFF)
val GaugeBackground = Color(0xFF0A0A0A)
val GaugeSurface = Color(0xFF141414)
val GaugeTrack = Color(0xFF2A2A2A)
val GaugeMuted = Color(0xFF9A9A9A)
val GaugeText = Color(0xFFF5F5F5)
val GaugeWarn = Color(0xFFF5A524)
val WeekRing = Color(0xFFFFC44D)
val GaugeDanger = Color(0xFFE5484D)

/** One saturated hue per product so neighboring segments stay easy to tell apart. */
fun productColor(id: Int): Color = when (id) {
    UsageProduct.UNSPECIFIED.id -> Color(0xFFA1A1AA)
    UsageProduct.API.id -> Color(0xFFFF7A00)
    UsageProduct.BUILD.id -> Color(0xFF2979FF)
    UsageProduct.PLUGINS.id -> Color(0xFF00C853)
    UsageProduct.CHAT.id -> Color(0xFF00E5FF)
    UsageProduct.IMAGINE.id -> Color(0xFFFF2D95)
    UsageProduct.VOICE.id -> Color(0xFFD500F9)
    UsageProduct.APP_BUILDER.id -> Color(0xFFFFC400)
    UsageProduct.TASKS.id -> Color(0xFF1DE9B6)
    UsageProduct.BOT.id -> Color(0xFFC6FF00)
    UsageProduct.CURSOR.id -> Color(0xFF7C4DFF)
    UsageProduct.BUGBOT.id -> Color(0xFFFF3B30)
    -1 -> ElectricBlue
    else -> Color(0xFFE0E0E0)
}

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
