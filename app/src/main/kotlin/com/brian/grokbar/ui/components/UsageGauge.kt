package com.brian.grokbar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.grokbar.data.ProductShare
import com.brian.grokbar.data.UsageProduct
import com.brian.grokbar.data.UsageSnapshot
import com.brian.grokbar.data.sortedForLegend
import com.brian.grokbar.ui.theme.ElectricBlue
import com.brian.grokbar.ui.theme.GaugeMuted
import com.brian.grokbar.ui.theme.GaugeSurface
import com.brian.grokbar.ui.theme.GaugeTrack
import com.brian.grokbar.ui.theme.WeekRing
import com.brian.grokbar.ui.theme.productColor
import com.brian.grokbar.util.Formatters
import com.brian.grokbar.util.WeekProgress
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun UsageGaugeCard(
    snapshot: UsageSnapshot,
    modifier: Modifier = Modifier
) {
    val products = displayProducts(snapshot)
    val usedSegments = products.filter { it.percent > 0.15f }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GaugeSurface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                nowMs = System.currentTimeMillis()
            }
        }
        val weekElapsed = WeekProgress.elapsedPercent(snapshot.resetAtEpochMs, nowMs)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            UsageDonut(
                segments = usedSegments,
                usedPercent = snapshot.usedPercent,
                weekElapsed = weekElapsed
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${Formatters.usedPercentLabel(snapshot.usedPercent)}%",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
                Text(text = "of total", color = GaugeMuted, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        SegmentedUsageBar(
            segments = usedSegments,
            usedPercent = snapshot.usedPercent,
            modifier = Modifier.fillMaxWidth().height(20.dp)
        )
        Spacer(Modifier.height(16.dp))
        products.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEachIndexed { indexInRow, share ->
                    LegendItem(
                        share = share,
                        color = productColor(share.id),
                        modifier = Modifier.weight(1f)
                    )
                    if (indexInRow == 0 && row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val eta = Formatters.relativeReset(snapshot.resetAtEpochMs)
        Text(
            text = buildString {
                append(Formatters.resetLabel(snapshot.resetAtEpochMs))
                if (eta != null) {
                    append("  ·  ")
                    append(eta)
                }
            },
            color = GaugeMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LegendItem(share: ProductShare, color: Color, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = share.label, color = GaugeMuted, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${Formatters.usedPercentLabel(share.percent)}%",
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun UsageDonut(
    segments: List<ProductShare>,
    usedPercent: Float,
    weekElapsed: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(220.dp)) {
        val outerStroke = size.minDimension * 0.10f
        val innerStroke = size.minDimension * 0.066f
        val gap = size.minDimension * 0.028f
        val outerInset = outerStroke / 2f
        val outerSize = Size(size.width - outerStroke, size.height - outerStroke)
        val outerTopLeft = Offset(outerInset, outerInset)
        drawRing(GaugeTrack, 0f, 360f, outerTopLeft, outerSize, outerStroke)
        var start = -90f
        var endOfColor = start
        val segmentGap = if (segments.size > 1) 3.2f else 0f
        segments.forEach { share ->
            val full = 360f * (share.percent / 100f)
            val sweep = full - segmentGap
            if (sweep > 0.4f) {
                drawRing(productColor(share.id), start, sweep, outerTopLeft, outerSize, outerStroke)
                endOfColor = start + sweep
            }
            start += full
        }
        if (segments.isEmpty() && usedPercent > 0.15f) {
            val sweep = 360f * (usedPercent / 100f)
            drawRing(ElectricBlue, -90f, sweep, outerTopLeft, outerSize, outerStroke)
            endOfColor = -90f + sweep
        }

        val outerRadius = (size.minDimension - outerStroke) / 2f
        if (endOfColor > -90f + 1f) {
            drawRingTip(outerRadius, endOfColor, outerStroke)
        }
        val innerRadius = outerRadius - outerStroke / 2f - gap - innerStroke / 2f
        val innerTopLeft = Offset(size.width / 2f - innerRadius, size.height / 2f - innerRadius)
        val innerSize = Size(innerRadius * 2f, innerRadius * 2f)
        drawRing(GaugeTrack, 0f, 360f, innerTopLeft, innerSize, innerStroke)
        val weekSweep = 360f * (weekElapsed / 100f)
        if (weekSweep > 0.8f) {
            val drawn = weekSweep.coerceAtMost(359.8f)
            drawRing(WeekRing, -90f, drawn, innerTopLeft, innerSize, innerStroke)
            if (weekSweep < 359.5f) {
                drawRingTip(innerRadius, -90f + drawn, innerStroke)
            }
        }
    }
}

private fun DrawScope.drawRing(
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    topLeft: Offset,
    arcSize: Size,
    stroke: Float
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Butt)
    )
}

private fun DrawScope.drawRingTip(radius: Float, angleDeg: Float, stroke: Float) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val center = Offset(size.width / 2f, size.height / 2f)
    val dot = stroke * 0.33f
    if (dot < 1f) return
    val at = Offset(
        center.x + radius * cos(rad).toFloat(),
        center.y + radius * sin(rad).toFloat()
    )
    drawCircle(color = Color.Black, radius = dot, center = at)
    drawCircle(color = Color.White, radius = dot * 0.72f, center = at)
}

@Composable
private fun SegmentedUsageBar(
    segments: List<ProductShare>,
    usedPercent: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(50))) {
        drawRect(GaugeTrack)
        var x = 0f
        val gap = 2f
        val toDraw = segments.ifEmpty {
            if (usedPercent > 0.15f) listOf(ProductShare(-1, "Used", usedPercent)) else emptyList()
        }
        toDraw.forEach { share ->
            val width = size.width * (share.percent / 100f)
            val drawWidth = (width - gap).coerceAtLeast(0f)
            if (drawWidth > 1f) {
                drawRect(
                    color = productColor(share.id),
                    topLeft = Offset(x, 0f),
                    size = Size(drawWidth, size.height)
                )
            }
            x += width
        }
    }
}

private fun displayProducts(snapshot: UsageSnapshot): List<ProductShare> {
    val byId = snapshot.products.associateBy { it.id }
    val known = UsageProduct.legendOrder.map { product ->
        ProductShare(
            id = product.id,
            label = product.label,
            percent = byId[product.id]?.percent ?: 0f
        )
    }
    val knownIds = UsageProduct.legendOrder.map { it.id }.toSet()
    val extras = snapshot.products.filter { it.id !in knownIds }
    return (known + extras).sortedForLegend()
}
