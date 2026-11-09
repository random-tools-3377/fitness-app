package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WellbeingTest {
    private val monday = LocalDate(2026, 11, 16)
    private fun daysAgo(n: Int) = monday.minus(DatePeriod(days = n))
    private val profile = Profile(Sex.FEMALE, 1990, 165.0, 70.0, Activity.LIGHT, WeightGoal.LOSE, 0.5)

    @Test
    fun measurementsKeepTheLatestAndTheChange() {
        val all = listOf(
            Measurement(1, daysAgo(14), MeasureKind.WAIST, 84.0),
            Measurement(2, daysAgo(0), MeasureKind.WAIST, 81.5),
            Measurement(3, daysAgo(3), MeasureKind.BODY_FAT, 28.0),
        )
        assertEquals(81.5, Measurements.latest(all).getValue(MeasureKind.WAIST).value)
        assertEquals(-2.5, Measurements.change(all, MeasureKind.WAIST))
        assertNull(Measurements.change(all, MeasureKind.BODY_FAT))
        assertNotNull(Measurements.problem(MeasureKind.BODY_FAT, 80.0))
        assertNull(Measurements.problem(MeasureKind.WAIST, 80.0))
    }

    @Test
    fun aHabitStreakCountsDaysInARow() {
        val h = Habit(1, "Stretch")
        val ticks = listOf(HabitTick(1, daysAgo(1)), HabitTick(1, daysAgo(2)), HabitTick(1, daysAgo(4)))
        assertEquals(2, Habits.streaks(listOf(h), ticks, monday).getValue(1).current)
        assertNotNull(Habits.problem("stretch", listOf(h)))
        assertNull(Habits.problem("Walk", listOf(h)))
    }

    @Test
    fun losingSlowerThanPlannedOffersALowerTarget() {
        // Last week averaged 80.0 kg, this week 79.9: 0.1 kg lost, 0.5 planned.
        val w = listOf(
            WeighIn(day = daysAgo(13), kg = 80.0), WeighIn(day = daysAgo(10), kg = 80.0),
            WeighIn(day = daysAgo(6), kg = 79.9), WeighIn(day = daysAgo(2), kg = 79.9),
        )
        val c = CheckIns.of(w, profile, 1800, monday)!!
        assertEquals(-0.1, c.changeKg, 1e-9)
        assertEquals(-0.5, c.plannedKg)
        assertEquals(1360, c.suggestedKcal) // 0.4 kg a week too slow is 440 kcal a day
    }

    @Test
    fun onPlanOffersNothingAndTheMinimumHolds() {
        val onPlan = listOf(
            WeighIn(day = daysAgo(13), kg = 80.0), WeighIn(day = daysAgo(9), kg = 80.0),
            WeighIn(day = daysAgo(5), kg = 79.5), WeighIn(day = daysAgo(1), kg = 79.5),
        )
        assertNull(CheckIns.of(onPlan, profile, 1800, monday)!!.suggestedKcal)
        val gaining = onPlan.map { if (it.day >= daysAgo(6)) it.copy(kg = 80.5) else it }
        val c = CheckIns.of(gaining, profile, 1300, monday)!!
        assertEquals(1200, c.suggestedKcal)
        assertNotNull(c.minimumReason)
    }

    @Test
    fun tooFewWeighInsGiveNoCheckIn() {
        assertNull(CheckIns.of(listOf(WeighIn(day = daysAgo(1), kg = 80.0)), profile, 1800, monday))
        assertNull(CheckIns.of(emptyList(), null, 1800, monday))
    }
}
