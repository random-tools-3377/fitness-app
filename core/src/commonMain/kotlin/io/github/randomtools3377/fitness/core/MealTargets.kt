package io.github.randomtools3377.fitness.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * A calorie target for each meal: the day's target split between them. Off until turned on.
 * In [MacroMode.PERCENT] the shares are percentages of the day; in [MacroMode.GRAMS] they are
 * kcal (the same switch as for protein, carbs and fat).
 */
@Serializable
data class MealSplit(
    val enabled: Boolean = false,
    val mode: MacroMode = MacroMode.PERCENT,
    val shares: Map<Meal, Double> = DEFAULT,
) {
    /** Each meal's target: calories, and protein, carbs and fat following the same split. */
    fun targets(day: Nutrients): Map<Meal, Nutrients> {
        val fractions: Map<Meal, Double> = when (mode) {
            MacroMode.PERCENT, MacroMode.BODY -> Meal.entries.associateWith { (shares[it] ?: 0.0) / 100.0 }
            MacroMode.GRAMS -> Meal.entries.associateWith { if (day.kcal > 0) (shares[it] ?: 0.0) / day.kcal else 0.0 }
        }
        return fractions.mapValues { (_, f) -> day * f }
    }

    fun problem(dayKcal: Int): String? {
        val sum = Meal.entries.sumOf { shares[it] ?: 0.0 }
        return when {
            Meal.entries.any { (shares[it] ?: 0.0) < 0 } -> "Numbers can't be below zero."
            mode == MacroMode.PERCENT && abs(sum - 100.0) > 0.5 -> "The four shares add up to ${formatAmount(sum)}%. They need to add up to 100%."
            mode == MacroMode.GRAMS && abs(sum - dayKcal) > 1 -> "The four meals add up to ${formatAmount(sum)} kcal, but the day's target is $dayKcal kcal."
            else -> null
        }
    }

    companion object {
        val DEFAULT = mapOf(Meal.BREAKFAST to 25.0, Meal.LUNCH to 35.0, Meal.DINNER to 30.0, Meal.SNACKS to 10.0)
    }
}

/** A fasting plan: hours fasting, out of 24. */
@Serializable
data class FastPlan(val hours: Double) {
    val title: String get() = if (hours == hours.toInt().toDouble() && hours.toInt() in PRESETS) "${hours.toInt()}:${24 - hours.toInt()}" else "${formatAmount(hours)} hours"

    companion object {
        val PRESETS = listOf(16, 18, 20, 23)
    }
}

/** A fast: when it started, how long it was meant to be, and when it ended (null while going). */
@Serializable
data class Fast(
    val id: Long = 0,
    val startedAt: Long,
    val plannedHours: Double,
    val endedAt: Long? = null,
) {
    val plannedMs: Long get() = (plannedHours * 3_600_000).toLong()

    fun elapsedMs(now: Long): Long = ((endedAt ?: now) - startedAt).coerceAtLeast(0)
    fun leftMs(now: Long): Long = (plannedMs - elapsedMs(now)).coerceAtLeast(0)
    fun endsAt(): Long = startedAt + plannedMs

    /** Ended, and lasted at least as long as planned. */
    val finished: Boolean get() = endedAt != null && endedAt - startedAt >= plannedMs
}

object Fasting {
    /** The days on which a planned fast was finished, for the streak (the day it ended). */
    fun finishedDays(fasts: List<Fast>, dayOf: (Long) -> kotlinx.datetime.LocalDate): Set<kotlinx.datetime.LocalDate> =
        fasts.filter { it.finished }.map { dayOf(it.endedAt!!) }.toSet()

    /** "11:32" from milliseconds: hours and minutes. */
    fun hoursMinutes(ms: Long): String {
        val minutes = ms / 60_000
        return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    }
}
