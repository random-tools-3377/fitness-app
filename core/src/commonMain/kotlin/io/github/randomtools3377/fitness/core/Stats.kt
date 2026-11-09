package io.github.randomtools3377.fitness.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Week, month or year, ending on a day. */
enum class StatsPeriod(val title: String, val days: Int) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    YEAR("Year", 365),
}

/** A summary of a period's eating, drinking and training. */
data class PeriodStats(
    val from: LocalDate,
    val to: LocalDate,
    /** Calories and protein, carbs and fat eaten each day of the period that has any food. */
    val byDay: Map<LocalDate, Nutrients>,
    /** The average over the days with food logged. */
    val average: Nutrients,
    val daysLogged: Int,
    val daysOnTarget: Int,
    /** The foods that gave the most calories, most first: name and kcal. */
    val topFoods: List<Pair<String, Double>>,
    val averageWaterMl: Int,
    val workouts: Int,
    val burnedKcal: Double,
)

/** One week's training: workouts done and the total lifted (sets × reps × weight). */
data class TrainingWeek(val weekStart: LocalDate, val workouts: Int, val liftedKg: Double)

object Stats {
    fun range(period: StatsPeriod, end: LocalDate): Pair<LocalDate, LocalDate> = end.minus(DatePeriod(days = period.days - 1)) to end

    fun period(
        period: StatsPeriod,
        end: LocalDate,
        entries: List<DiaryEntry>,
        targetKcal: Int?,
        water: List<WaterEntry>,
        workouts: List<Workout>,
    ): PeriodStats {
        val (from, to) = range(period, end)
        val inRange = entries.filter { it.day in from..to }
        val byDay = inRange.groupBy { it.day }.mapValues { (_, list) -> list.map { it.nutrients }.total() }.filterValues { it.kcal > 0 }
        val logged = byDay.size
        val avg = if (logged == 0) Nutrients.ZERO else byDay.values.total() * (1.0 / logged)
        val onTarget = if (targetKcal == null) 0 else byDay.values.count { Streaks.onTarget(it.kcal, targetKcal) }
        val top = inRange.groupBy { it.name }.mapValues { (_, l) -> l.sumOf { it.nutrients.kcal } }
            .entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        val waterDays = water.filter { it.day in from..to }.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.ml } }
        val days = (to.toEpochDays() - from.toEpochDays() + 1).coerceAtLeast(1)
        val w = workouts.filter { it.finished && it.day in from..to }
        return PeriodStats(
            from, to, byDay, avg, logged, onTarget, top,
            averageWaterMl = (waterDays.values.sum().toDouble() / days).roundToInt(),
            workouts = w.size,
            burnedKcal = w.sumOf { it.burnedKcal },
        )
    }

    /** Sets × reps × weight of ticked weights sets. */
    fun lifted(we: WorkoutExercise): Double =
        if (we.exercise.kind != ExerciseKind.WEIGHTS) 0.0
        else we.sets.filter { it.done && !it.warmUp }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

    /** Workouts and the total lifted for each of the last [weeks] weeks, oldest first. */
    fun trainingWeeks(workouts: List<Workout>, weeks: Int, today: LocalDate): List<TrainingWeek> {
        val thisWeek = Streaks.weekOf(today)
        return (weeks - 1 downTo 0).map { i ->
            val start = thisWeek.minus(DatePeriod(days = 7 * i))
            val end = start.plus(DatePeriod(days = 6))
            val w = workouts.filter { it.finished && it.day in start..end }
            TrainingWeek(start, w.size, w.sumOf { x -> x.exercises.sumOf { lifted(it) } })
        }
    }

    /** The total lifted per exercise, or per muscle group, over the workouts given, most first. */
    fun liftedByExercise(workouts: List<Workout>): List<Pair<String, Double>> =
        workouts.filter { it.finished }.flatMap { it.exercises }.groupBy { it.exercise.name }
            .mapValues { (_, l) -> l.sumOf { lifted(it) } }.filterValues { it > 0 }.entries.sortedByDescending { it.value }.map { it.key to it.value }

    fun liftedByMuscle(workouts: List<Workout>): List<Pair<String, Double>> =
        workouts.filter { it.finished }.flatMap { it.exercises }.groupBy { it.exercise.muscle.ifBlank { "Other" } }
            .mapValues { (_, l) -> l.sumOf { lifted(it) } }.filterValues { it > 0 }.entries.sortedByDescending { it.value }.map { it.key to it.value }

    /**
     * The weight trend, in kg a day, from the daily weights of the last [days] days (a straight
     * line through them). Null with fewer than two weigh-ins on different days.
     */
    fun weightTrend(weighIns: List<WeighIn>, today: LocalDate, days: Int = 28): Double? {
        val from = today.minus(DatePeriod(days = days - 1))
        val pts = Weight.daily(weighIns).filter { it.first in from..today }
        if (pts.size < 2) return null
        val xs = pts.map { it.first.toEpochDays().toDouble() }
        val ys = pts.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        if (sxx == 0.0) return null
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx
    }

    /**
     * The day the goal weight would be reached if the trend carried on: an estimate. Null when
     * the trend isn't heading toward the goal, or it would take more than two years.
     */
    fun goalDate(currentKg: Double, goalKg: Double, kgPerDay: Double?, today: LocalDate): LocalDate? {
        if (kgPerDay == null || kgPerDay == 0.0) return null
        val gap = goalKg - currentKg
        if (gap == 0.0) return today
        if (gap / kgPerDay < 0) return null
        val days = ceil(gap / kgPerDay).toInt()
        if (days > 730) return null
        return today.plus(DatePeriod(days = days))
    }
}
