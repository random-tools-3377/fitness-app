package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverviewTest {
    @Test
    fun aMonthIsWholeWeeksFromMonday() {
        val weeks = Calendar.weeks(LocalDate(2026, 11, 15))
        // November 2026 starts on a Sunday: six empty days first.
        assertEquals(List(6) { null } + LocalDate(2026, 11, 1), weeks[0])
        assertEquals(LocalDate(2026, 11, 30), weeks.last()[0])
        assertEquals(6, weeks.size)
        assertEquals(DayMark.ON_TARGET, Calendar.mark(1900.0, 2000))
        assertEquals(DayMark.UNDER, Calendar.mark(1500.0, 2000))
        assertEquals(DayMark.OVER, Calendar.mark(2500.0, 2000))
        assertEquals(DayMark.LOGGED, Calendar.mark(2500.0, null))
        assertEquals(DayMark.NOTHING, Calendar.mark(0.0, 2000))
    }

    @Test
    fun aYearInReview() {
        val squat = Exercises.BUILT_IN.first { it.kind == ExerciseKind.WEIGHTS }
        val run = Exercises.BUILT_IN.first { it.kind == ExerciseKind.CARDIO }
        val e = { d: Int, k: Double -> DiaryEntry(day = LocalDate(2026, 3, d), meal = Meal.LUNCH, foodId = 1, name = "Soup", amount = Amount(100.0), nutrients = Nutrients(k)) }
        val entries = listOf(e(1, 2000.0), e(2, 1800.0), e(3, 3000.0), e(10, 2000.0))
        val workouts = listOf(
            Workout(name = "Legs", day = LocalDate(2026, 3, 1), startedAt = 0, endedAt = 1, exercises = listOf(
                WorkoutExercise(squat, listOf(WorkoutSet(60.0, 5, done = true), WorkoutSet(40.0, 10, done = true, warmUp = true))),
                WorkoutExercise(run, listOf(WorkoutSet(seconds = 1800, distanceKm = 5.0, done = true))),
            )),
        )
        val weighIns = listOf(WeighIn(day = LocalDate(2026, 1, 5), kg = 80.0), WeighIn(day = LocalDate(2026, 6, 5), kg = 76.5))
        val r = YearReviews.of(2026, entries, workouts, weighIns) { 2000 }
        assertEquals(4, r.daysLogged)
        assertEquals(2200, r.averageKcal)
        assertEquals(3, r.daysOnTarget)
        assertEquals(300.0, r.totalLiftedKg)
        assertEquals(5.0, r.cardioKm)
        assertEquals(30, r.cardioMinutes)
        assertEquals(-3.5, r.weightChangeKg!!, 1e-9)
        assertEquals(3, r.longestFoodStreak)
        assertEquals(listOf(2026), YearReviews.years(emptyList(), emptyList(), emptyList(), 2026))
        assertNull(YearReviews.of(2025, entries, workouts, weighIns) { 2000 }.weightChangeKg)
    }
}
