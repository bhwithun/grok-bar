package com.brian.grokbar.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val resetFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.US)

    fun usedPercentLabel(value: Float): String {
        val rounded = kotlin.math.round(value * 10f) / 10f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else String.format(Locale.US, "%.1f", rounded)
    }

    fun resetLabel(epochMs: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (epochMs == null || epochMs <= 0L) return "Reset date unknown"
        val text = Instant.ofEpochMilli(epochMs).atZone(zone).format(resetFormat)
        return "Resets $text"
    }

    fun relativeReset(epochMs: Long?): String? {
        if (epochMs == null || epochMs <= 0L) return null
        val minutes = ((epochMs - System.currentTimeMillis()) / 60000L).toInt()
        if (minutes <= 0) return "Resetting soon"
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        val mins = minutes % 60
        return when {
            days > 0 -> "in ${days}d ${hours}h"
            hours > 0 -> "in ${hours}h ${mins}m"
            else -> "in ${mins}m"
        }
    }

    fun lastUpdated(epochMs: Long): String {
        if (epochMs <= 0L) return "Not updated yet"
        val minutes = ((System.currentTimeMillis() - epochMs) / 60000L).toInt().coerceAtLeast(0)
        return when {
            minutes < 1 -> "Updated just now"
            minutes < 60 -> "Updated ${minutes}m ago"
            else -> "Updated ${minutes / 60}h ago"
        }
    }
}
