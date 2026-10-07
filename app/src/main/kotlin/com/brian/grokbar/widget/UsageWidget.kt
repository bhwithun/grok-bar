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
import com.brian.grokbar.util.Formatters

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
    val widthPx = (size.width.value * density * 2f).toInt().coerceIn(160, 1600)
    val heightPx = (size.height.value * density * 2f).toInt().coerceIn(160, 1600)
    val pxPerDp = widthPx / size.width.value
    val screenPx = size.width.value * density
    val bitmapPxPerScreenPx = if (screenPx > 0f) widthPx / screenPx else 1f
    val bitmap = GaugeBitmapRenderer.render(
        snapshot,
        widthPx,
        heightPx,
        pxPerDp,
        bitmapPxPerScreenPx
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(18.dp)
            .background(ColorProvider(Color(0xFF101014)))
            .clickable(openApp)
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Grok usage ${snapshot.usedPercent.toInt()} percent used, " +
                Formatters.timeLeftLabel(snapshot.resetAtEpochMs),
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )
    }
}

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}
