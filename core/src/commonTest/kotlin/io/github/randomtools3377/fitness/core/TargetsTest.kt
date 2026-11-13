package io.github.randomtools3377.fitness.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetsTest {
    private val man = Profile(Sex.MALE, birthYear = 1996, heightCm = 180.0, weightKg = 80.0, activity = Activity.MODERATE)
    private val woman = Profile(Sex.FEMALE, birthYear = 2001, heightCm = 165.0, weightKg = 60.0, activity = Activity.SEDENTARY)

    @Test
    fun restingBurnFollowsMifflinStJeor() {
        // 10 × 80 + 6.25 × 180 − 5 × 30 + 5
        assertEquals(1780.0, Targets.restingKcal(Sex.MALE, 30, 180.0, 80.0), 1e-9)
        // 10 × 60 + 6.25 × 165 − 5 × 25 − 161
        assertEquals(1345.25, Targets.restingKcal(Sex.FEMALE, 25, 165.0, 60.0), 1e-9)
    }

    @Test
    fun keepingWeightIsRestingBurnTimesActivity() {
        val r = Targets.calculate(man, 2026)
        assertEquals(1780, r.restingKcal)
        assertEquals(2760, r.maintenanceKcal) // 1780 × 1.55 = 2759, to the nearest 10
        assertEquals(2760, r.targetKcal)
        assertEquals(0, r.adjustmentKcal)
        assertFalse(r.raisedToMinimum)
        assertNull(r.minimumReason)
    }

    @Test
    fun losingHalfAKiloAWeekTakesOff550ADay() {
        val r = Targets.calculate(man.copy(goal = WeightGoal.LOSE, kgPerWeek = 0.5), 2026)
        assertEquals(-550, r.adjustmentKcal)
        assertEquals(2210, r.targetKcal) // 2759 − 550 = 2209
    }

    @Test
    fun gainingAQuarterKiloAddsAbout275() {
        val r = Targets.calculate(man.copy(goal = WeightGoal.GAIN, kgPerWeek = 0.25), 2026)
        assertEquals(275, r.adjustmentKcal)
        assertEquals(3030, r.targetKcal) // 2759 + 275 = 3034
    }

    @Test
    fun theTargetNeverGoesBelowTheMinimumAndSaysWhy() {
        val r = Targets.calculate(woman.copy(goal = WeightGoal.LOSE, kgPerWeek = 0.75), 2026)
        // 1345.25 × 1.2 − 825 = 789
        assertEquals(790, r.sumsKcal)
        assertEquals(1200, r.targetKcal)
        assertTrue(r.raisedToMinimum)
        assertNotNull(r.minimumReason)
        assertTrue(r.minimumReason!!.contains("1200"))

        val m = Targets.calculate(man.copy(activity = Activity.SEDENTARY, weightKg = 55.0, goal = WeightGoal.LOSE, kgPerWeek = 0.75), 2026)
        assertEquals(1500, m.targetKcal)
        assertTrue(m.raisedToMinimum)
    }

    @Test
    fun theLatestWeighInIsUsedWhenGiven() {
        val r = Targets.calculate(man, 2026, weightKg = 75.0)
        assertEquals(1730, r.restingKcal)
    }

    @Test
    fun theAgeMovesOnWithTheYear() {
        val now = Targets.calculate(man, 2026).restingKcal
        val later = Targets.calculate(man, 2027).restingKcal
        assertEquals(5, now - later)
    }

    @Test
    fun aNewNumberIsOfferedOnlyWhenItMoves50OrMore() {
        assertFalse(Targets.shouldOffer(2000, 2040))
        assertFalse(Targets.shouldOffer(2000, 1960))
        assertTrue(Targets.shouldOffer(2000, 2050))
        assertTrue(Targets.shouldOffer(2000, 1950))
        assertFalse(Targets.shouldOffer(null, 1500))
    }

    @Test
    fun macroTargetsCanBe25_50_25PercentOfTheCalories() {
        val g = MacroTargets.OLD_DEFAULT.grams(2000)
        assertEquals(125.0, g.protein, 1e-9) // 500 kcal / 4
        assertEquals(250.0, g.carbs, 1e-9) // 1000 kcal / 4
        assertEquals(55.56, g.fat, 0.01) // 500 kcal / 9
        assertNull(MacroTargets().problem())
    }

    @Test
    fun proteinStartsFromBodyWeightAndActivity() {
        // 70 kg, mostly sitting: 0.9 g a kg = 63 g; fat 25% of 2,000 kcal; carbs the rest.
        val sitting = MacroTargets().grams(2000, 70.0, Activity.SEDENTARY)
        assertEquals(63.0, sitting.protein, 1e-9)
        assertEquals(55.56, sitting.fat, 0.01)
        assertEquals((2000 - 63 * 4 - 500) / 4.0, sitting.carbs, 1e-9)
        assertEquals(98.0, MacroTargets().grams(2000, 70.0, Activity.LIGHT).protein, 1e-9) // 1.4 g a kg
        assertEquals(140.0, MacroTargets().grams(2000, 70.0, Activity.VERY_ACTIVE).protein, 1e-9) // 2.0 g a kg
        // The user's own number per kg wins.
        assertEquals(84.0, MacroTargets(proteinPerKg = 1.2).grams(2000, 70.0, Activity.VERY_ACTIVE).protein, 1e-9)
        // Without a weight, a quarter of the calories.
        assertEquals(125.0, MacroTargets().grams(2000).protein, 1e-9)
        assertNotNull(MacroTargets(proteinPerKg = 5.0).problem())
        // Switching to percentages keeps the same grams, near enough.
        val pc = MacroTargets().switchTo(MacroMode.PERCENT, 2000, 70.0, Activity.SEDENTARY)
        assertEquals(13.0, pc.protein)
        assertEquals(25.0, pc.fat)
        assertEquals(62.0, pc.carbs)
    }

    @Test
    fun macroTargetsCanBeGramsAndSwitchBothWays() {
        val grams = MacroTargets.OLD_DEFAULT.switchTo(MacroMode.GRAMS, 2000)
        assertEquals(MacroTargets(MacroMode.GRAMS, 125.0, 250.0, 56.0), grams)
        assertEquals(125.0, grams.grams(3000).protein, 1e-9) // grams don't follow the calories
        val back = grams.switchTo(MacroMode.PERCENT, 2000)
        assertEquals(25.0, back.protein)
        assertEquals(50.0, back.carbs)
        assertEquals(25.0, back.fat)
    }

    @Test
    fun percentagesMustAddUpTo100() {
        assertNotNull(MacroTargets(MacroMode.PERCENT, 30.0, 50.0, 30.0).problem())
        assertNull(MacroTargets(MacroMode.PERCENT, 30.0, 40.0, 30.0).problem())
        assertNull(MacroTargets(MacroMode.GRAMS, 150.0, 200.0, 70.0).problem())
    }

    @Test
    fun bodyDetailsAreChecked() {
        assertEquals(0, Targets.problems(30, 180.0, 80.0).size)
        assertEquals(3, Targets.problems(5, 20.0, null).size)
    }
}
