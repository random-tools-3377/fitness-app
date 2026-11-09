package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsTest {
    private val today = LocalDate(2026, 11, 9)
    private fun d(day: Int) = LocalDate(2026, 11, day)
    private fun e(day: Int, name: String, kcal: Double, protein: Double = 0.0) =
        DiaryEntry(day = d(day), meal = Meal.LUNCH, name = name, nutrients = Nutrients(kcal, protein))

    @Test
    fun aWeekIsAveragedOverTheDaysWithFood() {
        val entries = listOf(e(3, "Oats", 1800.0, 90.0), e(5, "Pasta", 2100.0, 60.0), e(9, "Oats", 2400.0, 120.0), e(1, "Old", 5000.0))
        val water = listOf(WaterEntry(day = d(9), ml = 1400), WaterEntry(day = d(8), ml = 700))
        val s = Stats.period(StatsPeriod.WEEK, today, entries, 2000, water, emptyList())
        assertEquals(d(3), s.from)
        assertEquals(3, s.daysLogged)
        assertEquals(2100.0, s.average.kcal, 1e-9)
        assertEquals(90.0, s.average.protein, 1e-9)
        assertEquals(2, s.daysOnTarget) // 1,800 and 2,100 are within 10%; 2,400 isn't
        assertEquals(listOf("Oats" to 4200.0, "Pasta" to 2100.0), s.topFoods)
        assertEquals(300, s.averageWaterMl) // 2,100 ml over 7 days
    }

    @Test
    fun liftedIsSetsTimesRepsTimesWeight() {
        val squat = Exercises.builtIn("b:squat")!!
        val pushUp = Exercises.builtIn("b:push-up")!!
        val w = Workout(
            name = "Legs", day = d(9), startedAt = 0, endedAt = 1,
            exercises = listOf(
                WorkoutExercise(squat, listOf(WorkoutSet(80.0, 5, done = true), WorkoutSet(80.0, 5, done = true), WorkoutSet(100.0, 5))),
                WorkoutExercise(pushUp, listOf(WorkoutSet(reps = 20, done = true))),
            ),
        )
        assertEquals(800.0, Stats.lifted(w.exercises[0]), 1e-9)
        assertEquals(listOf("Squat" to 800.0), Stats.liftedByExercise(listOf(w)))
        assertEquals(listOf("Legs" to 800.0), Stats.liftedByMuscle(listOf(w)))
        val weeks = Stats.trainingWeeks(listOf(w), 2, today)
        assertEquals(listOf(0, 1), weeks.map { it.workouts })
        assertEquals(800.0, weeks.last().liftedKg, 1e-9)
    }

    @Test
    fun theGoalDateFollowsTheTrend() {
        // 0.1 kg a day down over ten days.
        val w = (0..10).map { WeighIn(day = LocalDate(2026, 10, 30 + 0).let { s -> LocalDate.fromEpochDays(s.toEpochDays() + it) }, kg = 80.0 - it * 0.1, at = it.toLong()) }
        val trend = Stats.weightTrend(w, today)!!
        assertEquals(-0.1, trend, 1e-9)
        assertEquals(d(29), Stats.goalDate(79.0, 77.0, trend, today)) // 2 kg at 0.1 a day: 20 days
        assertNull(Stats.goalDate(79.0, 81.0, trend, today)) // heading the other way
        assertNull(Stats.weightTrend(w.take(1), today))
    }
}
