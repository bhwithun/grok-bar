package com.brian.grokbar.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.brian.grokbar.data.ProductShare
import com.brian.grokbar.data.UsageProduct
import com.brian.grokbar.data.UsageSnapshot
import com.brian.grokbar.util.WeekProgress

object GaugeBitmapRenderer {
    private val background = 0xFF0A0A0A.toInt()
    private val track = 0xFF2A2A2A.toInt()
    private val weekFill = 0xFF8A8A8A.toInt()
    private val label = 0xFFF5F5F5.toInt()
    private const val usageLabel = "Grok Token Use"
    private const val weekLabel = "time to reset"
    private val extraColors = intArrayOf(
        0xFFFFB020.toInt(),
        0xFFFF7A3D.toInt(),
        0xFF5AD0E6.toInt(),
        0xFFE8E050.toInt()
    )

    fun render(snapshot: UsageSnapshot, width: Int, height: Int, pxPerDp: Float = 3f): Bitmap {
        val w = width.coerceAtLeast(200)
        val h = height.coerceAtLeast(80)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(background)

        val padH = (w * 0.04f).coerceIn(12f, 28f)
        val padV = (h * 0.10f).coerceIn(8f, 18f)
        val innerW = w - padH * 2f
        val innerH = (h - padV * 2f).coerceAtLeast(16f)
        val gap = (innerH * 0.18f).coerceIn(6f, 14f)
        val barHeight = ((innerH - gap) / 2f).coerceAtLeast(8f)
        val barsTop = padV + (innerH - (barHeight * 2f + gap)) / 2f

        val usageRect = RectF(padH, barsTop, padH + innerW, barsTop + barHeight)
        val weekRect = RectF(
            padH,
            usageRect.bottom + gap,
            padH + innerW,
            usageRect.bottom + gap + barHeight
        )
        drawSegmentedBar(canvas, snapshot, usageRect)
        drawWeekBar(canvas, snapshot, weekRect)
        drawCenteredLabel(canvas, usageRect, usageLabel, pxPerDp)
        drawCenteredLabel(canvas, weekRect, weekLabel, pxPerDp)
        return bmp
    }

    private fun orderedProducts(snapshot: UsageSnapshot): List<ProductShare> {
        val byId = snapshot.products.associateBy { it.id }
        val known = UsageProduct.gaugeOrder.map { product ->
            byId[product.id] ?: ProductShare(product.id, product.label, 0f)
        }
        val extras = snapshot.products.filter { p -> UsageProduct.fromId(p.id) == null }
        return known + extras
    }

    private fun usedSegments(snapshot: UsageSnapshot): List<ProductShare> {
        return orderedProducts(snapshot)
            .filter { it.percent > 0.15f }
            .ifEmpty {
                if (snapshot.usedPercent > 0.15f) {
                    listOf(ProductShare(-1, "Used", snapshot.usedPercent))
                } else {
                    emptyList()
                }
            }
    }

    private fun colorForProduct(id: Int): Int {
        return when (id) {
            UsageProduct.BUILD.id -> 0xFF1A5EFF.toInt()
            UsageProduct.CHAT.id -> 0xFF00D4FF.toInt()
            UsageProduct.VOICE.id -> 0xFFB455FF.toInt()
            UsageProduct.IMAGINE.id -> 0xFFFF4D8D.toInt()
            UsageProduct.AUTOMATIONS.id -> 0xFF3DDC97.toInt()
            0 -> extraColors[0]
            1 -> extraColors[1]
            -1 -> 0xFF1A5EFF.toInt()
            else -> extraColors[kotlin.math.abs(id) % extraColors.size]
        }
    }

    private fun drawBar(canvas: Canvas, rect: RectF, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRect(rect, paint)
    }

    private fun drawSegmentedBar(canvas: Canvas, snapshot: UsageSnapshot, rect: RectF) {
        drawBar(canvas, rect, track)

        val segments = usedSegments(snapshot)
        val gap = 2f
        var x = rect.left
        val total = rect.width()
        segments.forEach { share ->
            val width = total * (share.percent / 100f)
            if (width <= 1f) {
                x += width
                return@forEach
            }
            val right = (x + width - gap).coerceAtMost(rect.right)
            drawBar(canvas, RectF(x, rect.top, right, rect.bottom), colorForProduct(share.id))
            x += width
        }
    }

    private fun drawWeekBar(canvas: Canvas, snapshot: UsageSnapshot, rect: RectF) {
        drawBar(canvas, rect, track)
        val percent = WeekProgress.elapsedPercent(snapshot.resetAtEpochMs)
        val fillWidth = rect.width() * (percent / 100f)
        if (fillWidth <= 1f) return
        val fill = RectF(rect.left, rect.top, (rect.left + fillWidth).coerceAtMost(rect.right), rect.bottom)
        drawBar(canvas, fill, weekFill)
    }

    private fun drawCenteredLabel(canvas: Canvas, rect: RectF, text: String, pxPerDp: Float) {
        val maxHeight = rect.height() * 0.55f
        val maxWidth = rect.width() * 0.94f
        if (maxHeight < 16f || maxWidth < 24f) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = label
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        var size = (13f * pxPerDp).coerceAtMost(maxHeight)
        paint.textSize = size
        while (paint.measureText(text) > maxWidth && size > 16f) {
            size -= 1f
            paint.textSize = size
        }
        if (paint.measureText(text) > maxWidth) return

        val shadow = (size * 0.10f).coerceIn(3f, 8f)
        paint.setShadowLayer(shadow, 0f, shadow * 0.25f, 0xE6000000.toInt())
        val fm = paint.fontMetrics
        val y = rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, rect.centerX(), y, paint)
    }
}
