package io.github.randomtools3377.fitness.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** What can be measured (0.8.0). Lengths are stored in centimetres, body fat in percent. */
@Serializable
enum class MeasureKind(val title: String, val percent: Boolean = false) {
    WAIST("Waist"),
    HIPS("Hips"),
    CHEST("Chest"),
    ARM("Arm"),
    THIGH("Thigh"),
    NECK("Neck"),
    BODY_FAT("Body fat", percent = true),
}

/** One body measurement on a day. */
@Serializable
data class Measurement(
    val id: Long = 0,
    val day: LocalDate,
    val kind: MeasureKind,
    /** Centimetres, or percent for body fat. */
    val value: Double,
    val at: Long = 0,
)

object Measurements {
    /** The latest of each kind, for the summary. */
    fun latest(all: List<Measurement>): Map<MeasureKind, Measurement> =
        all.groupBy { it.kind }.mapValues { (_, list) -> list.maxWith(compareBy<Measurement> { it.day }.thenBy { it.at }) }

    /** The change in a kind since its first measurement, or null with fewer than two. */
    fun change(all: List<Measurement>, kind: MeasureKind): Double? {
        val list = all.filter { it.kind == kind }.sortedWith(compareBy<Measurement> { it.day }.thenBy { it.at })
        if (list.size < 2) return null
        return list.last().value - list.first().value
    }

    fun problem(kind: MeasureKind, value: Double?): String? = when {
        value == null -> "Type a number."
        kind.percent && (value <= 0 || value >= 75) -> "Body fat is a percentage between 0 and 75."
        !kind.percent && (value <= 0 || value > 300) -> "That doesn't look like a measurement in centimetres."
        else -> null
    }
}

/** Sleep and mood on a day (0.8.0): both optional. Mood is 1 (low) to 5 (great). */
@Serializable
data class DayNote(
    val day: LocalDate,
    val sleepHours: Double? = null,
    val mood: Int? = null,
    val text: String = "",
) {
    val isEmpty: Boolean get() = sleepHours == null && mood == null && text.isBlank()

    companion object {
        val MOODS = listOf("Low", "Meh", "OK", "Good", "Great")

        fun problem(sleepHours: Double?, mood: Int?): String? = when {
            sleepHours != null && (sleepHours < 0 || sleepHours > 24) -> "Hours slept are between 0 and 24."
            mood != null && mood !in 1..5 -> "Mood is 1 to 5."
            else -> null
        }
    }
}

/** A habit of the user's own (0.8.0), ticked each day it's done. */
@Serializable
data class Habit(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = 0,
    /** Stopped: off the daily list, kept with its history. */
    val archived: Boolean = false,
)

/** A habit done on a day. */
@Serializable
data class HabitTick(val habitId: Long, val day: LocalDate)

object Habits {
    /** Each habit's streak: days in a row it was ticked, today allowed to be still to do. */
    fun streaks(habits: List<Habit>, ticks: List<HabitTick>, today: LocalDate): Map<Long, Streak> {
        val byHabit = ticks.groupBy { it.habitId }
        return habits.associate { h -> h.id to Streaks.days(byHabit[h.id].orEmpty().map { it.day }.toSet(), today) }
    }

    fun problem(name: String, existing: List<Habit>): String? = when {
        name.isBlank() -> "Give the habit a name."
        existing.any { !it.archived && it.name.equals(name.trim(), ignoreCase = true) } -> "There is already a habit called that."
        else -> null
    }
}

/**
 * The weekly check-in (0.8.0): the average weight of the last 7 days against the 7 before, and
 * how that compares with the plan. If the target looks off by [Targets.OFFER_THRESHOLD] kcal or
 * more, a new one is offered; the app never changes the target by itself.
 */
data class CheckIn(
    val weekEnding: LocalDate,
    val averageKg: Double,
    val previousAverageKg: Double,
    /** Kilograms a week, negative for a loss. */
    val changeKg: Double,
    /** What the plan aims for, in kilograms a week, negative for a loss. */
    val plannedKg: Double,
    val currentKcal: Int,
    /** The target offered, or null if the current one looks right. */
    val suggestedKcal: Int?,
    /** Why the suggestion stops where it does, if it does. */
    val minimumReason: String?,
)

object CheckIns {
    /** Check-ins come on Mondays, looking back at the week before. */
    val DAY: DayOfWeek = DayOfWeek.MONDAY

    /** Weigh-ins needed in each of the two weeks for a fair comparison. */
    const val MIN_WEIGH_INS = 2

    fun plannedKgPerWeek(p: Profile): Double = when (p.goal) {
        WeightGoal.LOSE -> -p.kgPerWeek
        WeightGoal.GAIN -> p.kgPerWeek
        WeightGoal.KEEP -> 0.0
    }

    /**
     * The check-in for the week ending the day before [today], or null when there aren't enough
     * weigh-ins in both weeks, or no profile or target to compare with.
     */
    fun of(weighIns: List<WeighIn>, profile: Profile?, targetKcal: Int?, today: LocalDate): CheckIn? {
        if (profile == null || targetKcal == null) return null
        val end = today.minus(DatePeriod(days = 1))
        val startThis = end.minus(DatePeriod(days = 6))
        val endPrev = startThis.minus(DatePeriod(days = 1))
        val startPrev = endPrev.minus(DatePeriod(days = 6))
        val daily = Weight.daily(weighIns)
        val thisWeek = daily.filter { it.first in startThis..end }.map { it.second }
        val prevWeek = daily.filter { it.first in startPrev..endPrev }.map { it.second }
        if (thisWeek.size < MIN_WEIGH_INS || prevWeek.size < MIN_WEIGH_INS) return null
        val avg = thisWeek.average()
        val prev = prevWeek.average()
        val change = avg - prev
        val planned = plannedKgPerWeek(profile)
        // Gaining 0.1 kg a week more than planned is about 110 kcal a day too many.
        val adjust = ((change - planned) * Targets.KCAL_PER_KG_PER_WEEK).roundToInt()
        var suggested: Int? = null
        var reason: String? = null
        if (abs(adjust) >= Targets.OFFER_THRESHOLD) {
            val min = Targets.minimum(profile.sex)
            val raw = ((targetKcal - adjust) / 10.0).roundToInt() * 10
            suggested = if (raw < min) {
                reason = "The app doesn't suggest less than $min kcal a day."
                min
            } else {
                raw
            }
            if (suggested == targetKcal) suggested = null
        }
        return CheckIn(end, avg, prev, change, planned, targetKcal, suggested, reason)
    }
}
