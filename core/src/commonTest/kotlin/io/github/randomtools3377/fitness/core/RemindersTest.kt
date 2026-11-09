package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RemindersTest {
    // Monday 9 November 2026, 10:00.
    private val now = LocalDateTime(LocalDate(2026, 11, 9), LocalTime(10, 0))

    @Test
    fun nothingIsDueUntilSomethingIsTurnedOn() {
        assertNull(ReminderSettings().next(now))
        assertFalse(ReminderSettings().anyOn)
    }

    @Test
    fun theNextMealReminderIsFound() {
        val s = ReminderSettings(meals = mapOf(Meal.BREAKFAST to ReminderTime(8 * 60), Meal.LUNCH to ReminderTime(12 * 60 + 30)))
        val next = s.next(now)!!
        assertEquals(Meal.LUNCH, next.meal)
        assertEquals(LocalTime(12, 30), next.at.time)
        assertEquals("Time to log lunch", next.text)
        // After lunch, tomorrow's breakfast.
        assertEquals(LocalDateTime(LocalDate(2026, 11, 10), LocalTime(8, 0)), s.next(LocalDateTime(now.date, LocalTime(13, 0)))!!.at)
    }

    @Test
    fun waterRemindsEveryFewHoursDuringTheDay() {
        val s = ReminderSettings(waterOn = true, waterEveryHours = 3, waterFrom = 9 * 60, waterTo = 21 * 60)
        val today = s.due(now, LocalDateTime(now.date, LocalTime(23, 59))).map { it.at.time }
        assertEquals(listOf(LocalTime(12, 0), LocalTime(15, 0), LocalTime(18, 0), LocalTime(21, 0)), today)
    }

    @Test
    fun workoutsAndWeighInsOnlyOnTheirDays() {
        // Weigh in on Mondays and Thursdays at 7:00; it's Monday 10:00, so Thursday is next.
        val s = ReminderSettings(weighIn = ReminderTime(7 * 60, setOf(1, 4)), workout = ReminderTime(18 * 60, setOf(2), on = false))
        val next = s.next(now)!!
        assertEquals(ReminderKind.WEIGH_IN, next.kind)
        assertEquals(LocalDate(2026, 11, 12), next.at.date)
    }
}
