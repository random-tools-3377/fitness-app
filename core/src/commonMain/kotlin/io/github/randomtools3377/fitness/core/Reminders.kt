package io.github.randomtools3377.fitness.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/** A time of day and the weekdays it applies on (all seven unless chosen). */
@Serializable
data class ReminderTime(
    /** Minutes after midnight. */
    val minute: Int,
    /** ISO weekdays, 1 = Monday … 7 = Sunday. */
    val days: Set<Int> = (1..7).toSet(),
    val on: Boolean = true,
) {
    val time: LocalTime get() = LocalTime(minute / 60, minute % 60)
}

/** What kind of reminder, for its words and where it opens. */
enum class ReminderKind { MEAL, WATER, WORKOUT, WEIGH_IN }

/** One reminder due: what, and when. */
data class DueReminder(val kind: ReminderKind, val at: LocalDateTime, val meal: Meal? = null) {
    val text: String
        get() = when (kind) {
            ReminderKind.MEAL -> "Time to log ${meal?.title?.lowercase() ?: "a meal"}"
            ReminderKind.WATER -> "Time for a glass of water"
            ReminderKind.WORKOUT -> "Time for a workout"
            ReminderKind.WEIGH_IN -> "Time to weigh in"
        }
}

/**
 * Optional reminders, scheduled on the phone: one for each meal, water every few hours during
 * the day, workouts and weigh-ins on chosen days. All off to start.
 */
@Serializable
data class ReminderSettings(
    val meals: Map<Meal, ReminderTime> = emptyMap(),
    val waterOn: Boolean = false,
    val waterEveryHours: Int = 2,
    /** Water reminders between these minutes of the day. */
    val waterFrom: Int = 9 * 60,
    val waterTo: Int = 21 * 60,
    val workout: ReminderTime? = null,
    val weighIn: ReminderTime? = null,
) {
    /** Every reminder due from [now] (exclusive) to [until] (inclusive), soonest first. */
    fun due(now: LocalDateTime, until: LocalDateTime): List<DueReminder> {
        val out = mutableListOf<DueReminder>()
        var d: LocalDate = now.date
        while (d <= until.date) {
            fun at(minute: Int) = LocalDateTime(d, LocalTime(minute / 60, minute % 60))
            fun include(rt: ReminderTime, kind: ReminderKind, meal: Meal? = null) {
                if (rt.on && iso(d.dayOfWeek) in rt.days) out += DueReminder(kind, at(rt.minute), meal)
            }
            for ((meal, rt) in meals) include(rt, ReminderKind.MEAL, meal)
            if (waterOn && waterEveryHours > 0) {
                var m = waterFrom
                while (m <= waterTo) {
                    out += DueReminder(ReminderKind.WATER, at(m))
                    m += waterEveryHours * 60
                }
            }
            workout?.let { include(it, ReminderKind.WORKOUT) }
            weighIn?.let { include(it, ReminderKind.WEIGH_IN) }
            d = d.plus(DatePeriod(days = 1))
        }
        return out.filter { it.at > now && it.at <= until }.sortedBy { it.at }
    }

    /** The next reminder after [now], within a week, or null if none are on. */
    fun next(now: LocalDateTime): DueReminder? =
        due(now, LocalDateTime(now.date.plus(DatePeriod(days = 8)), LocalTime(0, 0))).firstOrNull()

    val anyOn: Boolean get() = meals.values.any { it.on } || waterOn || workout?.on == true || weighIn?.on == true

    companion object {
        fun iso(d: DayOfWeek): Int = d.ordinal + 1

        /** Usual times to start from when a meal reminder is turned on. */
        val MEAL_TIMES = mapOf(Meal.BREAKFAST to 8 * 60, Meal.LUNCH to 12 * 60 + 30, Meal.DINNER to 19 * 60, Meal.SNACKS to 16 * 60)
    }
}
