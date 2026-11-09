package io.github.randomtools3377.fitness.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntervalsTest {
    @Test
    fun tabataRunsItsRounds() {
        val p = IntervalPlan.TABATA
        assertEquals(230, p.totalSeconds) // 8 × 20 + 7 × 10
        assertEquals(IntervalPhase(1, true, 20), Intervals.phaseAt(p, 0))
        assertEquals(IntervalPhase(1, true, 1), Intervals.phaseAt(p, 19))
        assertEquals(IntervalPhase(1, false, 10), Intervals.phaseAt(p, 20))
        assertEquals(IntervalPhase(2, true, 20), Intervals.phaseAt(p, 30))
        assertEquals(IntervalPhase(8, true, 1), Intervals.phaseAt(p, 229))
        assertTrue(Intervals.phaseAt(p, 230).done)
        assertNull(p.problem())
        assertNotNull(IntervalPlan(2, 10, 8).problem())
    }

    @Test
    fun theRepTableComesBackToTheBest() {
        val oneRm = Bests.oneRepMax(100.0, 5)!!
        val table = RepTable.of(oneRm)
        assertEquals(12, table.size)
        assertEquals(oneRm, table[0].second)
        assertEquals(100.0, table[4].second, 1e-9) // 5 reps
    }
}
