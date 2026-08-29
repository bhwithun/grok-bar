package com.brian.grokbar.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.unit.ColorProvider
import com.brian.grokbar.MainActivity
import com.brian.grokbar.data.UsageRepository
import com.brian.grokbar.util.WeekProgress

class UsageWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = UsageRepository.get(context).load()
        val openApp = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        provideContent {
            GlanceTheme {
                WidgetContent(context = context, snapshot = snapshot, openApp = openApp)
            }
        }
    }
}

@Composable
private fun WidgetContent(
    context: android.content.Context,
    snapshot: com.brian.grokbar.data.UsageSnapshot,
    openApp: androidx.glance.action.Action
) {
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val widthPx = (size.width.value * density * 2f).toInt().coerceIn(280, 1400)
    val heightPx = (size.height.value * density * 2f).toInt().coerceIn(80, 900)
    val pxPerDp = widthPx / size.width.value
    val bitmap = GaugeBitmapRenderer.render(snapshot, widthPx, heightPx, pxPerDp)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(ColorProvider(Color(0xFF0A0A0A)))
            .clickable(openApp)
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Grok usage ${snapshot.usedPercent.toInt()} percent used, " +
                "week ${WeekProgress.elapsedPercent(snapshot.resetAtEpochMs).toInt()} percent elapsed",
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )
    }
}

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}
