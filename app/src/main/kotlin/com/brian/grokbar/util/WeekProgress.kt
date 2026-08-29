package com.brian.grokbar.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object WeekProgress {
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    fun elapsedPercent(
        resetAtEpochMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Float {
        val (startMs, endMs) = periodBounds(resetAtEpochMs, nowMs, zone)
        val duration = (endMs - startMs).coerceAtLeast(1L)
        val elapsed = nowMs - startMs
        return ((elapsed.toDouble() / duration.toDouble()) * 100.0)
            .toFloat()
            .coerceIn(0f, 100f)
    }

    private fun periodBounds(
        resetAtEpochMs: Long?,
        nowMs: Long,
        zone: ZoneId
    ): Pair<Long, Long> {
        if (resetAtEpochMs != null && resetAtEpochMs > 0L) {
            return (resetAtEpochMs - WEEK_MS) to resetAtEpochMs
        }
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val start = now.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return start to (start + WEEK_MS)
    }
}
