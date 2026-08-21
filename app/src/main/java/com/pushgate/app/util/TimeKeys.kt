package com.pushgate.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * All "days" in PushGate roll over at a user-defined hour (default 04:00 local) rather than at
 * midnight, because a 1am scroll belongs to the previous day's budget in every way that matters.
 */
object TimeKeys {

    const val DEFAULT_ROLLOVER_HOUR = 4

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun dateFor(epochMillis: Long, rolloverHour: Int = DEFAULT_ROLLOVER_HOUR): LocalDate {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone())
        return if (local.hour < rolloverHour) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    fun keyFor(epochMillis: Long, rolloverHour: Int = DEFAULT_ROLLOVER_HOUR): String =
        dateFor(epochMillis, rolloverHour).toString()

    fun todayKey(rolloverHour: Int = DEFAULT_ROLLOVER_HOUR): String =
        keyFor(System.currentTimeMillis(), rolloverHour)

    /** Milliseconds until the next rollover boundary, used to schedule the daily reset. */
    fun millisUntilNextRollover(nowMillis: Long, rolloverHour: Int = DEFAULT_ROLLOVER_HOUR): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone())
        var next = now.toLocalDate().atStartOfDay(zone()).plusHours(rolloverHour.toLong())
        if (!next.toInstant().isAfter(now.toInstant())) next = next.plusDays(1)
        return next.toInstant().toEpochMilli() - nowMillis
    }

    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "0m"
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> String.format(Locale.US, "%dh %02dm", h, m)
            m > 0 -> String.format(Locale.US, "%dm %02ds", m, s)
            else -> String.format(Locale.US, "%ds", s)
        }
    }

    fun formatClock(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L)) / 1000
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}
