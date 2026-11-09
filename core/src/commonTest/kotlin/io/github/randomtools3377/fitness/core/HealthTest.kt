package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthTest {
    @Test
    fun theNavyFormula() {
        // A man of 180 cm with a 85 cm waist and 38 cm neck: about 17-18%.
        val m = BodyFat.navy(Sex.MALE, 180.0, 85.0, 38.0, null)!!
        assertTrue(m in 16.0..20.0, "was $m")
        // A woman of 165 cm, waist 72, hips 98, neck 32: about 26-28%.
        val f = BodyFat.navy(Sex.FEMALE, 165.0, 72.0, 98.0.let { 32.0 }, 98.0)!!
        assertTrue(f in 22.0..32.0, "was $f")
        assertNull(BodyFat.navy(Sex.FEMALE, 165.0, 72.0, 32.0, null))
        assertNull(BodyFat.navy(Sex.MALE, 180.0, 30.0, 38.0, null))
    }

    @Test
    fun theCycleIsAveragedAndTheNextOneEstimated() {
        val periods = listOf(
            Period(1, LocalDate(2026, 8, 1), LocalDate(2026, 8, 5)),
            Period(2, LocalDate(2026, 8, 29), LocalDate(2026, 9, 2)),
            Period(3, LocalDate(2026, 9, 28), LocalDate(2026, 10, 2)),
        )
        val s = Cycle.summary(periods, LocalDate(2026, 10, 10))
        assertEquals(29, s.averageLength) // 28 and 30 days
        assertEquals(LocalDate(2026, 10, 27), s.nextStart)
        assertEquals(13, s.dayOfCycle)
        val days = Cycle.periodDays(periods, LocalDate(2026, 10, 10))
        assertEquals(15, days.size)
        assertNotNull(Cycle.problem(LocalDate(2026, 10, 11), null, LocalDate(2026, 10, 10)))
        assertNull(Cycle.summary(periods.take(1), LocalDate(2026, 8, 3)).nextStart)
    }
}
