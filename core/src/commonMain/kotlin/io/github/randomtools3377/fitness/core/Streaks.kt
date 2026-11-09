package io.github.randomtools3377.fitness.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs

/** A streak: how long it is now, and the longest it has been. */
data class Streak(val current: Int, val best: Int)

object Streaks {
    /** Within this share of the calorie target, either side, a day is on target. */
    const val ON_TARGET = 0.10

    const val DEFAULT_WEEKLY_WORKOUTS = 3

    /**
     * Days in a row that count, ending today. Today doesn't break a streak before it is over:
     * if today doesn't count yet, the streak runs up to yesterday.
     */
    fun days(counting: Set<LocalDate>, today: LocalDate): Streak {
        var current = 0
        var d = if (today in counting) today else today.minus(DatePeriod(days = 1))
        while (d in counting) {
            current++
            d = d.minus(DatePeriod(days = 1))
        }
        return Streak(current, maxOf(current, longestRun(counting)))
    }

    private fun longestRun(days: Set<LocalDate>): Int {
        var best = 0
        for (d in days) {
            if (d.minus(DatePeriod(days = 1)) in days) continue
            var n = 0
            var x = d
            while (x in days) { n++; x = x.plus(DatePeriod(days = 1)) }
            best = maxOf(best, n)
        }
        return best
    }

    /** The Monday a day's week starts on. */
    fun weekOf(d: LocalDate): LocalDate = d.minus(DatePeriod(days = d.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal))

    /**
     * Weeks in a row with at least [perWeek] workouts, so rest days never break it. This week
     * counts once it has enough; until then the streak runs up to last week.
     */
    fun weeks(workoutDays: List<LocalDate>, perWeek: Int, today: LocalDate): Streak {
        val counts = workoutDays.groupingBy { weekOf(it) }.eachCount()
        val good = counts.filter { it.value >= perWeek.coerceAtLeast(1) }.keys
        val thisWeek = weekOf(today)
        var current = 0
        var w = if (thisWeek in good) thisWeek else thisWeek.minus(DatePeriod(days = 7))
        while (w in good) {
            current++
            w = w.minus(DatePeriod(days = 7))
        }
        var best = 0
        for (s in good) {
            if (s.minus(DatePeriod(days = 7)) in good) continue
            var n = 0
            var x = s
            while (x in good) { n++; x = x.plus(DatePeriod(days = 7)) }
            best = maxOf(best, n)
        }
        return Streak(current, maxOf(current, best))
    }

    /** Whether a day's calories are within 10% of the target, either side. */
    fun onTarget(kcal: Double, targetKcal: Int): Boolean = targetKcal > 0 && abs(kcal - targetKcal) <= targetKcal * ON_TARGET

    /** All four streaks from the diary, water and workouts. */
    fun all(
        dayKcal: Map<LocalDate, Double>,
        targetKcal: Int?,
        waterByDay: Map<LocalDate, Int>,
        waterTargetMl: Int,
        workoutDays: List<LocalDate>,
        workoutsPerWeek: Int,
        today: LocalDate,
        /** The day's own target (0.11.0), when targets differ by day; otherwise [targetKcal]. */
        targetFor: ((LocalDate) -> Int?)? = null,
    ): AllStreaks = AllStreaks(
        foodLogged = days(dayKcal.filterValues { it > 0 }.keys, today),
        onTarget = days(dayKcal.filter { (d, k) -> (targetFor?.invoke(d) ?: targetKcal)?.let { onTarget(k, it) } == true }.keys, today),
        workoutWeeks = weeks(workoutDays, workoutsPerWeek, today),
        water = days(waterByDay.filter { it.value >= waterTargetMl }.keys, today),
    )
}

data class AllStreaks(val foodLogged: Streak, val onTarget: Streak, val workoutWeeks: Streak, val water: Streak)
