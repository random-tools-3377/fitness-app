package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MicrosTest {
    @Test
    fun microsAddUpAndScale() {
        val beer = Nutrients(43.0, micros = mapOf("alcohol" to 3.9))
        val coffee = Nutrients(2.0, micros = mapOf("caffeine" to 40.0, "potassium" to 49.0))
        val day = beer * 5.0 + coffee * 2.5
        assertEquals(19.5, day.micro(Micro.ALCOHOL)!!, 1e-9)
        assertEquals(100.0, day.micro(Micro.CAFFEINE)!!, 1e-9)
        assertNull(day.micro(Micro.IRON))
        assertEquals(2.0, Micros.drinks(19.5, 10.0))
        assertEquals(25, Micros.percent(Micro.IRON, 3.5))
        assertNull(Micros.percent(Micro.ALCOHOL, 10.0))
    }

    @Test
    fun builtInFoodsReadTheMicroColumns() {
        val line = "S1\tBeer\t43\t0.5\t3.6\t0\t\t0\t0\t0\t0.01\t3.9\t\t0.02\t4"
        val f = BuiltInFoods.parse(line).single()
        assertEquals(3.9, f.per100.micro(Micro.ALCOHOL))
        assertNull(f.per100.micro(Micro.CAFFEINE))
        assertEquals(0.02, f.per100.micro(Micro.IRON))
        assertEquals(4.0, f.per100.micro(Micro.CALCIUM))
    }

    @Test
    fun theDaysTarget() {
        val monday = LocalDate(2026, 11, 9)
        val days = DayTargets(weekdayKcal = mapOf(6 to 2400), trainingExtraKcal = 200)
        assertEquals(1800, DayTarget.forDay(1800, monday, days, null, 2300, trainingDay = false))
        assertEquals(2000, DayTarget.forDay(1800, monday, days, null, 2300, trainingDay = true))
        assertEquals(2400, DayTarget.forDay(1800, LocalDate(2026, 11, 14), days, null, 2300, trainingDay = false))
        val brk = DietBreak(monday, 2)
        assertEquals(LocalDate(2026, 11, 22), brk.end)
        assertEquals(2300, DayTarget.forDay(1800, monday, days, brk, 2300, trainingDay = true))
        assertEquals(1800, DayTarget.forDay(1800, LocalDate(2026, 11, 23), days, brk, 2300, trainingDay = false))
    }

    @Test
    fun theOnTargetStreakUsesEachDaysTarget() {
        val d1 = LocalDate(2026, 11, 8)
        val d2 = LocalDate(2026, 11, 9)
        val s = Streaks.all(mapOf(d1 to 2400.0, d2 to 1800.0), 1800, emptyMap(), 2000, emptyList(), 3, d2) { if (it == d1) 2400 else 1800 }
        assertEquals(2, s.onTarget.current)
    }
}
