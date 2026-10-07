package com.brian.grokbar.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.brian.grokbar.data.ProductShare
import com.brian.grokbar.data.UsageProduct
import com.brian.grokbar.data.UsageSnapshot
import com.brian.grokbar.util.Formatters
import com.brian.grokbar.util.WeekProgress
import kotlin.math.min

object GaugeBitmapRenderer {
    private val background = 0xFF101014.toInt()
    private val track = 0xFF1A1A20.toInt()
    private val trackEdge = 0xFF2C2C36.toInt()
    private val weekFill = 0xFFFFC44D.toInt()
    private val label = 0xFFFFFFF5.toInt()
    private val tipFill = 0xFFFFFFFF.toInt()
    private val tipEdge = 0xFF000000.toInt()
    private val tick = 0xFFF2F2F6.toInt()
    private val extraColors = intArrayOf(
        0xFFFFB020.toInt(),
        0xFFFF7A3D.toInt(),
        0xFF5AD0E6.toInt(),
        0xFFE8E050.toInt()
    )

    fun render(
        snapshot: UsageSnapshot,
        width: Int,
        height: Int,
        pxPerDp: Float = 3f,
        bitmapPxPerScreenPx: Float = 1f
    ): Bitmap {
        val w = width.coerceAtLeast(64)
        val h = height.coerceAtLeast(64)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(background)

        val captionLines = Formatters.timeLeftLines(snapshot.resetAtEpochMs)
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = label
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val preferredCaption = min(w, h) * 0.064f * 1.75f
        val landscape = w > h
        val cx: Float
        val cy: Float
        val ringSide: Float
        if (landscape) {
            val pad = h * 0.04f
            val textSize = fitBlockTextSize(
                captionLines,
                preferredCaption,
                maxWidth = w * 0.42f,
                maxHeight = h - pad * 2f,
                min = 12f
            )
            captionPaint.textSize = textSize
            val column = captionLines.maxOf { captionPaint.measureText(it) }
            val gap = h * 0.03f
            ringSide = min(h - pad * 2f, w - pad * 2f - gap - column).coerceAtLeast(32f)
            val groupLeft = (w - (ringSide + gap + column)) / 2f
            cx = groupLeft + ringSide / 2f
            cy = h / 2f
            drawRings(canvas, snapshot, cx, cy, ringSide, pxPerDp, bitmapPxPerScreenPx)
            drawCaptionBlock(
                canvas,
                captionLines,
                groupLeft + ringSide + gap + column / 2f,
                cy,
                captionPaint
            )
        } else {
            val caption = captionLines.joinToString(" ")
            captionPaint.textSize = fitTextSize(caption, preferredCaption, w * 0.92f, 12f)
            val captionFm = captionPaint.fontMetrics
            val captionHeight = captionFm.descent - captionFm.ascent
            val bottomPad = h * 0.045f
            val captionGap = h * 0.018f
            val topPad = h * 0.04f
            val ringAreaTop = topPad
            val ringAreaBottom = h - bottomPad - captionHeight - captionGap
            ringSide = min(w.toFloat() * 0.98f, (ringAreaBottom - ringAreaTop).coerceAtLeast(32f))
            cx = w / 2f
            cy = ringAreaTop + (ringAreaBottom - ringAreaTop) / 2f
            drawRings(canvas, snapshot, cx, cy, ringSide, pxPerDp, bitmapPxPerScreenPx)
            canvas.drawText(caption, cx, h - bottomPad - captionFm.descent, captionPaint)
        }
        return bmp
    }

    private fun drawRings(
        canvas: Canvas,
        snapshot: UsageSnapshot,
        cx: Float,
        cy: Float,
        ringSide: Float,
        pxPerDp: Float,
        bitmapPxPerScreenPx: Float
    ) {
        val tickLen = ringSide * 0.040f
        val margin = tickLen + ringSide * 0.028f
        val outerStroke = ringSide * 0.112f
        val innerStroke = ringSide * 0.074f
        val ringGap = ringSide * 0.028f
        val outerRadius = ringSide / 2f - margin - outerStroke / 2f
        val innerRadius = outerRadius - outerStroke / 2f - ringGap - innerStroke / 2f

        drawTrack(canvas, cx, cy, outerRadius, outerStroke)
        drawUsageRing(canvas, snapshot, cx, cy, outerRadius, outerStroke, bitmapPxPerScreenPx)
        drawTrack(canvas, cx, cy, innerRadius, innerStroke)
        drawResetRing(canvas, snapshot, cx, cy, innerRadius, innerStroke)
        drawDayTicks(canvas, cx, cy, outerRadius + outerStroke / 2f, tickLen, pxPerDp)
        drawGrokMark(canvas, cx, cy, (innerRadius - innerStroke / 2f) * 1.55f)
    }

    private fun drawCaptionBlock(
        canvas: Canvas,
        lines: List<String>,
        centerX: Float,
        centerY: Float,
        paint: Paint
    ) {
        val fm = paint.fontMetrics
        val lineHeight = fm.descent - fm.ascent
        val gap = lineHeight * 0.18f
        val block = lines.size * lineHeight + (lines.size - 1) * gap
        var baseline = centerY - block / 2f - fm.ascent
        for (line in lines) {
            canvas.drawText(line, centerX, baseline, paint)
            baseline += lineHeight + gap
        }
    }

    private fun orderedProducts(snapshot: UsageSnapshot): List<ProductShare> {
        val byId = snapshot.products.associateBy { it.id }
        val known = UsageProduct.gaugeOrder.map { product ->
            byId[product.id] ?: ProductShare(product.id, product.label, 0f)
        }
        val extras = snapshot.products.filter { UsageProduct.fromId(it.id) == null }
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

    private fun drawTrack(canvas: Canvas, cx: Float, cy: Float, radius: Float, stroke: Float) {
        val edge = ringPaint(trackEdge, stroke + stroke * 0.12f)
        canvas.drawCircle(cx, cy, radius, edge)
        canvas.drawCircle(cx, cy, radius, ringPaint(track, stroke))
    }

    private fun drawUsageRing(
        canvas: Canvas,
        snapshot: UsageSnapshot,
        cx: Float,
        cy: Float,
        radius: Float,
        stroke: Float,
        bitmapPxPerScreenPx: Float
    ) {
        val oval = ringOval(cx, cy, radius)
        val segments = usedSegments(snapshot)
        var start = -90f
        var endOfColor = start
        val boundaries = ArrayList<Float>(segments.size)
        segments.forEachIndexed { index, share ->
            val sweep = 360f * (share.percent / 100f)
            if (sweep > 0.15f) {
                canvas.drawArc(oval, start, sweep, false, ringPaint(colorForProduct(share.id), stroke))
                endOfColor = start + sweep
            }
            val boundary = start + sweep
            if (index < segments.lastIndex) boundaries += boundary
            start = boundary
        }
        boundaries.forEach { angle ->
            drawSegmentSeparator(canvas, cx, cy, radius, stroke, angle, bitmapPxPerScreenPx)
        }
        if (endOfColor > -90f + 1f) {
            drawTip(canvas, cx, cy, radius, endOfColor, stroke)
        }
    }

    /** Track-colored radial cut, two screen pixels wide. */
    private fun drawSegmentSeparator(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        stroke: Float,
        angleDeg: Float,
        bitmapPxPerScreenPx: Float
    ) {
        val paint = Paint().apply {
            color = track
            style = Paint.Style.STROKE
            strokeWidth = (bitmapPxPerScreenPx * 2f).coerceAtLeast(2f)
            strokeCap = Paint.Cap.BUTT
            isAntiAlias = false
        }
        val rad = Math.toRadians(angleDeg.toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        val inner = radius - stroke / 2f
        val outer = radius + stroke / 2f
        canvas.drawLine(
            cx + cos * inner,
            cy + sin * inner,
            cx + cos * outer,
            cy + sin * outer,
            paint
        )
    }

    private fun drawResetRing(
        canvas: Canvas,
        snapshot: UsageSnapshot,
        cx: Float,
        cy: Float,
        radius: Float,
        stroke: Float
    ) {
        val elapsed = WeekProgress.elapsedPercent(snapshot.resetAtEpochMs)
        val sweep = 360f * (elapsed / 100f)
        if (sweep <= 0.8f) return
        val oval = ringOval(cx, cy, radius)
        canvas.drawArc(oval, -90f, sweep.coerceAtMost(359.8f), false, ringPaint(weekFill, stroke))
        if (sweep < 359.5f) {
            drawTip(canvas, cx, cy, radius, -90f + sweep, stroke)
        }
    }

    private fun drawDayTicks(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        outerEdge: Float,
        tickLen: Float,
        pxPerDp: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tick
            strokeWidth = (1.15f * pxPerDp).coerceIn(2f, 4.5f)
            strokeCap = Paint.Cap.ROUND
        }
        for (day in 0 until 7) {
            val rad = Math.toRadians(-90.0 + 360.0 * day / 7.0)
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()
            val start = outerEdge - tickLen * 0.15f
            val end = outerEdge + tickLen
            canvas.drawLine(cx + cos * start, cy + sin * start, cx + cos * end, cy + sin * end, paint)
        }
    }

    private fun drawGrokMark(canvas: Canvas, cx: Float, cy: Float, diameter: Float) {
        if (diameter < 16f) return
        val ringRadius = diameter * 0.30f
        val ringStroke = diameter * 0.135f
        canvas.drawCircle(cx, cy, ringRadius, ringPaint(label, ringStroke))

        canvas.save()
        canvas.rotate(-48f, cx, cy)
        val gap = ringPaint(background, ringStroke * 1.65f)
        val reach = diameter * 0.62f
        canvas.drawLine(cx - reach, cy, cx + reach, cy, gap)

        val len = diameter * 0.50f
        val thick = diameter * 0.078f
        val blade = Path().apply {
            moveTo(cx - len, cy)
            quadTo(cx, cy - thick, cx + len, cy)
            quadTo(cx, cy + thick, cx - len, cy)
            close()
        }
        canvas.drawPath(blade, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = label })
        canvas.restore()
    }

    private fun drawTip(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        angleDeg: Float,
        stroke: Float
    ) {
        val rad = Math.toRadians(angleDeg.toDouble())
        val x = cx + radius * kotlin.math.cos(rad).toFloat()
        val y = cy + radius * kotlin.math.sin(rad).toFloat()
        val dotRadius = stroke * 0.66f / 2f
        if (dotRadius < 1f) return
        val edge = (dotRadius * 0.28f).coerceIn(1.25f, dotRadius * 0.42f)
        canvas.drawCircle(x, y, dotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tipEdge })
        canvas.drawCircle(x, y, dotRadius - edge, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tipFill })
    }

    private fun fitTextSize(text: String, preferred: Float, maxWidth: Float, min: Float): Float {
        val paint = Paint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = preferred
        }
        var size = preferred
        while (paint.measureText(text) > maxWidth && size > min) {
            size -= 1f
            paint.textSize = size
        }
        return size
    }

    private fun fitBlockTextSize(
        lines: List<String>,
        preferred: Float,
        maxWidth: Float,
        maxHeight: Float,
        min: Float
    ): Float {
        val paint = Paint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        var size = preferred
        while (size > min) {
            paint.textSize = size
            val widest = lines.maxOf { paint.measureText(it) }
            val fm = paint.fontMetrics
            val lineHeight = fm.descent - fm.ascent
            val block = lines.size * lineHeight + (lines.size - 1) * lineHeight * 0.18f
            if (widest <= maxWidth && block <= maxHeight) return size
            size -= 1f
        }
        return min
    }

    private fun ringPaint(color: Int, stroke: Float): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.BUTT
        }
    }

    private fun ringOval(cx: Float, cy: Float, radius: Float): RectF {
        return RectF(cx - radius, cy - radius, cx + radius, cy + radius)
    }
}
