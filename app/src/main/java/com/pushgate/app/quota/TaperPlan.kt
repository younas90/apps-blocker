package com.pushgate.app.quota

import com.pushgate.app.data.prefs.Settings
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns "60 minutes today, 10 minutes by day 7" into a concrete per-day budget.
 *
 * The curve is deliberately linear rather than exponential: a habit taper works when each day is
 * a small, obviously-survivable step down from the one before. An exponential curve front-loads
 * all the pain into days 1-2, which is where people quit.
 */
object TaperPlan {

    data class Day(val index: Int, val date: LocalDate, val minutes: Int, val isToday: Boolean)

    /** Zero-based day index within the plan. Negative means the plan has not started yet. */
    fun dayIndex(settings: Settings, today: LocalDate): Long {
        if (settings.planStartEpochDay < 0L) return 0L
        return today.toEpochDay() - settings.planStartEpochDay
    }

    /** Budget in minutes for a given zero-based day index. */
    fun minutesForDayIndex(settings: Settings, index: Long): Int {
        val start = settings.startMinutesPerDay
        val end = settings.endMinutesPerDay
        val days = max(2, settings.planDays)

        return when {
            index <= 0L -> start
            // Past the end of the plan the user holds at the maintenance level indefinitely.
            index >= (days - 1).toLong() -> end
            else -> {
                val t = index.toDouble() / (days - 1).toDouble()
                (start + (end - start) * t).roundToInt()
            }
        }.coerceAtLeast(0)
    }

    fun minutesToday(settings: Settings, today: LocalDate): Int =
        minutesForDayIndex(settings, dayIndex(settings, today))

    fun budgetMillisToday(settings: Settings, today: LocalDate): Long =
        minutesToday(settings, today) * 60_000L

    /**
     * Per-app budget: a custom override wins, otherwise the app shares the plan budget.
     * Note that the plan budget is *per app*, not pooled — pooling makes one relapse eat the
     * whole day and turns the taper into an all-or-nothing bet.
     */
    fun budgetMillisFor(settings: Settings, today: LocalDate, customBudgetMinutes: Int?): Long {
        val minutes = customBudgetMinutes ?: minutesToday(settings, today)
        return minutes * 60_000L
    }

    /** The whole curve, for the onboarding preview and the stats screen. */
    fun preview(settings: Settings, today: LocalDate, extraDays: Int = 0): List<Day> {
        val startDate = if (settings.planStartEpochDay >= 0L) {
            LocalDate.ofEpochDay(settings.planStartEpochDay)
        } else {
            today
        }
        val count = max(2, settings.planDays) + extraDays
        return (0 until count).map { i ->
            val date = startDate.plusDays(i.toLong())
            Day(
                index = i,
                date = date,
                minutes = minutesForDayIndex(settings, i.toLong()),
                isToday = date == today
            )
        }
    }

    /**
     * Push-up price for the next earned unlock. Each unlock bought today makes the next one
     * dearer, so "just five more push-ups" stops being an infinite loophole.
     */
    fun repsForNextUnlock(settings: Settings, earnedUnlocksToday: Int): Int {
        val multiplier = 1f + settings.escalation * earnedUnlocksToday
        return (settings.baseReps * multiplier).roundToInt().coerceAtLeast(1)
    }

    /** Minutes granted for completing that price. Held flat so the cost/benefit curve steepens. */
    fun minutesForNextUnlock(settings: Settings): Int = settings.baseUnlockMinutes.coerceAtLeast(1)
}
