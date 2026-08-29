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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.grokbar.data.ProductShare
import com.brian.grokbar.data.UsageProduct
import com.brian.grokbar.data.UsageSnapshot
import com.brian.grokbar.ui.theme.ElectricBlue
import com.brian.grokbar.ui.theme.GaugeMuted
import com.brian.grokbar.ui.theme.GaugeSurface
import com.brian.grokbar.ui.theme.GaugeTrack
import com.brian.grokbar.util.Formatters

private val SegmentOpacities = listOf(1f, 0.70f, 0.45f, 0.32f, 0.22f, 0.15f, 0.10f)

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
        val plan = snapshot.planName?.takeIf { it.isNotBlank() } ?: "Grok"
        Text(
            text = if (snapshot.isDemo) "Sample · Weekly $plan limit" else "Weekly $plan limit",
            style = MaterialTheme.typography.labelLarge,
            color = GaugeMuted
        )
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            UsageDonut(segments = usedSegments, usedPercent = snapshot.usedPercent)
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
                    val colorIndex = usedSegments.indexOfFirst { it.id == share.id }
                        .takeIf { it >= 0 } ?: (products.indexOf(share))
                    LegendItem(
                        share = share,
                        color = ElectricBlue.copy(alpha = SegmentOpacities[colorIndex.coerceIn(0, SegmentOpacities.lastIndex)]),
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(220.dp)) {
        val stroke = size.minDimension * 0.10f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = GaugeTrack,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        var start = -90f
        val gap = if (segments.size > 1) 3.2f else 0f
        segments.forEachIndexed { index, share ->
            val sweep = 360f * (share.percent / 100f) - gap
            if (sweep > 0.4f) {
                drawArc(
                    color = ElectricBlue.copy(
                        alpha = SegmentOpacities[index.coerceIn(0, SegmentOpacities.lastIndex)]
                    ),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
            }
            start += 360f * (share.percent / 100f)
        }
        if (segments.isEmpty() && usedPercent > 0.15f) {
            drawArc(
                color = ElectricBlue,
                startAngle = -90f,
                sweepAngle = 360f * (usedPercent / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
        }
    }
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
        toDraw.forEachIndexed { index, share ->
            val width = size.width * (share.percent / 100f)
            val drawWidth = (width - gap).coerceAtLeast(0f)
            if (drawWidth > 1f) {
                drawRect(
                    color = ElectricBlue.copy(
                        alpha = SegmentOpacities[index.coerceIn(0, SegmentOpacities.lastIndex)]
                    ),
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
    val known = UsageProduct.gaugeOrder.map { product ->
        byId[product.id] ?: ProductShare(product.id, product.label, 0f)
    }
    val extras = snapshot.products.filter { UsageProduct.fromId(it.id) == null }
    return known + extras
}
