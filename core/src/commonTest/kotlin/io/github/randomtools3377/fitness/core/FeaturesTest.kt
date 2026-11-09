package io.github.randomtools3377.fitness.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeaturesTest {
    @Test
    fun aNewInstallStartsSimple() {
        val on = Features.enabled(AppSettings())
        assertEquals(setOf(Feature.WORKOUTS, Feature.WATER, Feature.WEIGHT, Feature.STATS), on)
        assertFalse(Feature.FASTING in on)
    }

    @Test
    fun anUpdateKeepsWhatIsUsed() {
        val s = AppSettings(stepsOn = true, mealSplit = MealSplit(enabled = true))
        val on = Features.firstChoice(s, FeatureUse(hasFasts = true, hasCountries = true, hasHabits = true))
        assertTrue(Feature.FASTING in on && Feature.BARCODES in on && Feature.HABITS in on)
        assertTrue(Feature.STEPS in on && Feature.MEAL_TARGETS in on)
        assertFalse(Feature.CYCLE in on)
        assertEquals(on, Features.enabled(s.copy(features = Features.encode(on))))
    }
}
