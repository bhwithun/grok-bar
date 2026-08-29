package com.brian.grokbar.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.brian.grokbar.data.ProductShare
import com.brian.grokbar.data.UsageProduct
import com.brian.grokbar.data.UsageSnapshot
import com.brian.grokbar.util.WeekProgress

object GaugeBitmapRenderer {
    private val background = 0xFF101014.toInt()
    private val well = 0xFF1A1A20.toInt()
    private val wellInset = 0xFF0C0C10.toInt()
    private val outline = 0xFF3A3A48.toInt()
    private val weekFill = 0xFFFFC44D.toInt()
    private val playhead = 0xFFFFF6D8.toInt()
    private val label = 0xFFFFFFF5.toInt()
    private val chip = 0xB2000000.toInt()
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

        val padH = (w * 0.045f).coerceIn(10f, 24f)
        val padV = (h * 0.12f).coerceIn(8f, 16f)
        val innerW = w - padH * 2f
        val innerH = (h - padV * 2f).coerceAtLeast(16f)
        val gap = (innerH * 0.14f).coerceIn(8f, 16f)
        val barHeight = ((innerH - gap) / 2f).coerceAtLeast(8f)
        val barsTop = padV + (innerH - (barHeight * 2f + gap)) / 2f

        val usageRect = RectF(padH, barsTop, padH + innerW, barsTop + barHeight)
        val weekRect = RectF(
            padH,
            usageRect.bottom + gap,
            padH + innerW,
            usageRect.bottom + gap + barHeight
        )
        drawSegmentedBar(canvas, snapshot, usageRect, pxPerDp)
        drawWeekBar(canvas, snapshot, weekRect, pxPerDp)
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
            UsageProduct.BUILD.id -> 0xFF3D7BFF.toInt()
            UsageProduct.CHAT.id -> 0xFF00E5FF.toInt()
            UsageProduct.VOICE.id -> 0xFFD06BFF.toInt()
            UsageProduct.IMAGINE.id -> 0xFFFF5BA3.toInt()
            UsageProduct.AUTOMATIONS.id -> 0xFF3DFFB0.toInt()
            0 -> extraColors[0]
            1 -> extraColors[1]
            -1 -> 0xFF3D7BFF.toInt()
            else -> extraColors[kotlin.math.abs(id) % extraColors.size]
        }
    }

    private fun drawWell(canvas: Canvas, rect: RectF, stroke: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = well }
        canvas.drawRect(rect, fill)
        val inset = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wellInset }
        canvas.drawRect(RectF(rect.left, rect.top, rect.right, rect.top + stroke), inset)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outline
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        canvas.drawRect(
            RectF(
                rect.left + stroke / 2f,
                rect.top + stroke / 2f,
                rect.right - stroke / 2f,
                rect.bottom - stroke / 2f
            ),
            edge
        )
    }

    private fun drawCandy(canvas: Canvas, rect: RectF, color: Int) {
        if (rect.width() <= 0.5f || rect.height() <= 0.5f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.left,
                rect.bottom,
                intArrayOf(mix(color, Color.WHITE, 0.32f), color, mix(color, Color.BLACK, 0.22f)),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(rect, paint)
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x33FFFFFF }
        val shineH = (rect.height() * 0.18f).coerceAtLeast(1f)
        canvas.drawRect(RectF(rect.left, rect.top, rect.right, rect.top + shineH), shine)
    }

    private fun drawPlayhead(canvas: Canvas, bar: RectF, fillRight: Float, pxPerDp: Float) {
        val x = fillRight.coerceIn(bar.left, bar.right)
        if (x <= bar.left + 1f) return
        val w = (2.2f * pxPerDp).coerceIn(3f, 8f)
        val left = (x - w).coerceAtLeast(bar.left)
        drawCandy(canvas, RectF(left, bar.top, x.coerceAtMost(bar.right), bar.bottom), playhead)
    }

    private fun drawSegmentedBar(
        canvas: Canvas,
        snapshot: UsageSnapshot,
        rect: RectF,
        pxPerDp: Float
    ) {
        val stroke = (1f * pxPerDp).coerceIn(1.5f, 3f)
        drawWell(canvas, rect, stroke)
        val inner = inset(rect, stroke)
        val segments = usedSegments(snapshot)
        val gap = (1.6f * pxPerDp).coerceIn(2f, 5f)
        var x = inner.left
        val total = inner.width()
        var lastRight = inner.left
        segments.forEach { share ->
            val width = total * (share.percent / 100f)
            if (width <= 1f) {
                x += width
                return@forEach
            }
            val right = (x + width - gap).coerceAtMost(inner.right)
            drawCandy(canvas, RectF(x, inner.top, right, inner.bottom), colorForProduct(share.id))
            lastRight = right
            x += width
        }
        if (lastRight > inner.left) {
            drawPlayhead(canvas, inner, lastRight, pxPerDp)
        }
    }

    private fun drawWeekBar(
        canvas: Canvas,
        snapshot: UsageSnapshot,
        rect: RectF,
        pxPerDp: Float
    ) {
        val stroke = (1f * pxPerDp).coerceIn(1.5f, 3f)
        drawWell(canvas, rect, stroke)
        val inner = inset(rect, stroke)
        drawWeekTicks(canvas, inner, pxPerDp)
        val percent = WeekProgress.elapsedPercent(snapshot.resetAtEpochMs)
        val fillWidth = inner.width() * (percent / 100f)
        if (fillWidth <= 1f) return
        val fillRight = (inner.left + fillWidth).coerceAtMost(inner.right)
        drawCandy(canvas, RectF(inner.left, inner.top, fillRight, inner.bottom), weekFill)
        drawPlayhead(canvas, inner, fillRight, pxPerDp)
    }

    private fun drawWeekTicks(canvas: Canvas, rect: RectF, pxPerDp: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x28FFFFFF
            strokeWidth = (1f * pxPerDp).coerceIn(1f, 2.5f)
        }
        val insetY = rect.height() * 0.18f
        for (i in 1..6) {
            val x = rect.left + rect.width() * (i / 7f)
            canvas.drawLine(x, rect.top + insetY, x, rect.bottom - insetY, paint)
        }
    }

    private fun drawCenteredLabel(canvas: Canvas, rect: RectF, text: String, pxPerDp: Float) {
        val maxHeight = rect.height() * 0.48f
        val maxWidth = rect.width() * 0.90f
        if (maxHeight < 16f || maxWidth < 24f) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = label
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.04f
        }
        var size = (13f * pxPerDp).coerceAtMost(maxHeight)
        paint.textSize = size
        while (paint.measureText(text) > maxWidth && size > 16f) {
            size -= 1f
            paint.textSize = size
        }
        if (paint.measureText(text) > maxWidth) return

        val fm = paint.fontMetrics
        val y = rect.centerY() - (fm.ascent + fm.descent) / 2f
        val textW = paint.measureText(text)
        val padH = size * 0.42f
        val padV = size * 0.28f
        val chipRect = RectF(
            rect.centerX() - textW / 2f - padH,
            y + fm.ascent - padV,
            rect.centerX() + textW / 2f + padH,
            y + fm.descent + padV
        )
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chip }
        val radius = (size * 0.35f).coerceAtLeast(4f)
        canvas.drawRoundRect(chipRect, radius, radius, chipPaint)
        canvas.drawText(text, rect.centerX(), y, paint)
    }

    private fun inset(rect: RectF, amount: Float): RectF {
        return RectF(
            rect.left + amount,
            rect.top + amount,
            rect.right - amount,
            rect.bottom - amount
        )
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val ta = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * ta).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * ta).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * ta).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * ta).toInt()
        )
    }
}
