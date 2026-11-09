package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreaksTest {
    // Monday 9 November 2026.
    private val today = LocalDate(2026, 11, 9)
    private fun d(day: Int) = LocalDate(2026, 11, day)

    @Test
    fun todayDoesNotBreakAStreakBeforeItIsOver() {
        assertEquals(Streak(3, 3), Streaks.days(setOf(d(6), d(7), d(8)), today))
        assertEquals(Streak(4, 4), Streaks.days(setOf(d(6), d(7), d(8), d(9)), today))
        // A missed day yesterday ends it.
        assertEquals(Streak(0, 2), Streaks.days(setOf(d(6), d(7)), today))
    }

    @Test
    fun theBestStreakIsKept() {
        val days = setOf(d(1), d(2), d(3), d(4), d(5), d(8), d(9))
        assertEquals(Streak(2, 5), Streaks.days(days, today))
    }

    @Test
    fun weeksCountFromMonday() {
        assertEquals(d(9), Streaks.weekOf(d(9))) // a Monday
        assertEquals(d(9), Streaks.weekOf(d(15))) // the Sunday after
        assertEquals(d(2), Streaks.weekOf(d(8)))
    }

    @Test
    fun restDaysDontBreakTheWorkoutStreak() {
        // Three workouts in each of the last two weeks; this week only one so far.
        val days = listOf(LocalDate(2026, 10, 26), LocalDate(2026, 10, 28), LocalDate(2026, 10, 30), d(2), d(4), d(6), d(9))
        assertEquals(Streak(2, 2), Streaks.weeks(days, 3, today))
        // Once this week has three, it counts too.
        assertEquals(Streak(3, 3), Streaks.weeks(days + listOf(d(10), d(11)), 3, d(11)))
        assertEquals(Streak(0, 2), Streaks.weeks(days, 3, d(16)))
    }

    @Test
    fun onTargetIsWithinTenPercentEitherSide() {
        assertTrue(Streaks.onTarget(1800.0, 2000))
        assertTrue(Streaks.onTarget(2200.0, 2000))
        assertFalse(Streaks.onTarget(1799.0, 2000))
        assertFalse(Streaks.onTarget(2201.0, 2000))
        assertFalse(Streaks.onTarget(0.0, 0))
    }

    @Test
    fun allFourStreaksTogether() {
        val s = Streaks.all(
            dayKcal = mapOf(d(7) to 2050.0, d(8) to 1500.0, d(9) to 1950.0),
            targetKcal = 2000,
            waterByDay = mapOf(d(8) to 2000, d(9) to 1000),
            waterTargetMl = 2000,
            workoutDays = emptyList(),
            workoutsPerWeek = 3,
            today = today,
        )
        assertEquals(Streak(3, 3), s.foodLogged)
        assertEquals(Streak(1, 1), s.onTarget)
        assertEquals(Streak(1, 1), s.water) // today's water isn't done yet, so yesterday's counts
        assertEquals(Streak(0, 0), s.workoutWeeks)
    }
}
