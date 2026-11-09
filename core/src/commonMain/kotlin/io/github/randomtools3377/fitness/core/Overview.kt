package io.github.randomtools3377.fitness.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/** How a day went, for the calendar (0.14.0). */
enum class DayMark { NOTHING, UNDER, ON_TARGET, OVER, LOGGED }

data class CalendarDay(val day: LocalDate, val mark: DayMark, val kcal: Double, val workout: Boolean)

object Calendar {
    /** The weeks of [month] (any day in it), Monday first, with null for days outside the month. */
    fun weeks(month: LocalDate): List<List<LocalDate?>> {
        val first = LocalDate(month.year, month.month, 1)
        val lead = first.dayOfWeek.ordinal
        val days = mutableListOf<LocalDate?>()
        repeat(lead) { days += null }
        var d = first
        while (d.month == month.month) {
            days += d
            d = d.plus(DatePeriod(days = 1))
        }
        while (days.size % 7 != 0) days += null
        return days.chunked(7)
    }

    /** The day's mark: on target within 10% either side, under, over; or just logged when there's no target. */
    fun mark(kcal: Double, target: Int?): DayMark = when {
        kcal <= 0 -> DayMark.NOTHING
        target == null -> DayMark.LOGGED
        Streaks.onTarget(kcal, target) -> DayMark.ON_TARGET
        kcal < target -> DayMark.UNDER
        else -> DayMark.OVER
    }

    fun month(month: LocalDate, dayKcal: Map<LocalDate, Double>, workoutDays: Set<LocalDate>, targetFor: (LocalDate) -> Int?): List<CalendarDay> =
        weeks(month).flatten().filterNotNull().map { d ->
            val k = dayKcal[d] ?: 0.0
            CalendarDay(d, mark(k, targetFor(d)), k, d in workoutDays)
        }
}

/** A year's totals and bests (0.14.0). */
data class YearReview(
    val year: Int,
    val daysLogged: Int,
    val averageKcal: Int?,
    val daysOnTarget: Int,
    val workouts: Int,
    val totalLiftedKg: Double,
    val cardioKm: Double,
    val cardioMinutes: Int,
    val weightStartKg: Double?,
    val weightEndKg: Double?,
    val bestsSet: Int,
    val longestFoodStreak: Int,
    val topFoods: List<Pair<String, Int>>,
) {
    val weightChangeKg: Double? get() = if (weightStartKg != null && weightEndKg != null) weightEndKg - weightStartKg else null
    val empty: Boolean get() = daysLogged == 0 && workouts == 0 && weightStartKg == null
}

object YearReviews {
    fun of(
        year: Int,
        entries: List<DiaryEntry>,
        workouts: List<Workout>,
        weighIns: List<WeighIn>,
        targetFor: (LocalDate) -> Int?,
    ): YearReview {
        val eaten = entries.filter { !it.planned && it.day.year == year }
        val byDay = eaten.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.nutrients.kcal } }.filterValues { it > 0 }
        val onTarget = byDay.count { (d, k) -> targetFor(d)?.let { Streaks.onTarget(k, it) } == true }
        val ws = workouts.filter { it.finished && it.day.year == year }
        var lifted = 0.0
        var km = 0.0
        var seconds = 0
        for (w in ws) for (we in w.exercises) for (s in we.sets) {
            if (!s.done || s.warmUp) continue
            if (we.exercise.kind == ExerciseKind.WEIGHTS) lifted += (s.weightKg ?: 0.0) * (s.reps ?: 0)
            if (we.exercise.kind == ExerciseKind.CARDIO) {
                km += s.distanceKm ?: 0.0
                seconds += s.seconds ?: 0
            }
        }
        val daily = Weight.daily(weighIns).filter { it.first.year == year }
        // Bests set this year: those whose day is in the year, counting every workout up to then.
        val bests = Bests.all(workouts.filter { it.finished && it.day.year <= year }).count { it.day.year == year }
        val longest = longestRun(byDay.keys)
        val top = eaten.groupBy { it.name }.mapValues { (_, l) -> l.sumOf { it.nutrients.kcal }.roundToInt() }
            .entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        return YearReview(
            year = year,
            daysLogged = byDay.size,
            averageKcal = if (byDay.isEmpty()) null else (byDay.values.sum() / byDay.size).roundToInt(),
            daysOnTarget = onTarget,
            workouts = ws.size,
            totalLiftedKg = lifted,
            cardioKm = km,
            cardioMinutes = seconds / 60,
            weightStartKg = daily.firstOrNull()?.second,
            weightEndKg = daily.lastOrNull()?.second,
            bestsSet = bests,
            longestFoodStreak = longest,
            topFoods = top,
        )
    }

    private fun longestRun(days: Set<LocalDate>): Int {
        var best = 0
        for (d in days) {
            if (d.plus(DatePeriod(days = -1)) in days) continue
            var n = 0
            var x = d
            while (x in days) { n++; x = x.plus(DatePeriod(days = 1)) }
            best = maxOf(best, n)
        }
        return best
    }

    /** The years with anything logged, newest first. */
    fun years(entries: List<DiaryEntry>, workouts: List<Workout>, weighIns: List<WeighIn>, thisYear: Int): List<Int> =
        (entries.map { it.day.year } + workouts.map { it.day.year } + weighIns.map { it.day.year } + thisYear).distinct().sortedDescending()
}
