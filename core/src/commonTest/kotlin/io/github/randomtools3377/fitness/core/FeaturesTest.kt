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

    @Test
    fun betterLabelReadingBringsLabelReadingWithIt() {
        val on = Features.switch(Features.STARTS_ON, Feature.AI_LABELS, true)
        assertTrue(Feature.LABEL_PHOTO in on && Feature.AI_LABELS in on)
        val off = Features.switch(on, Feature.LABEL_PHOTO, false)
        assertTrue(Feature.LABEL_PHOTO !in off && Feature.AI_LABELS !in off)
    }

    @Test
    fun aiPartsStartOffAndAreMarked() {
        val ai = Feature.entries.filter { it.ai }
        assertTrue(ai.isNotEmpty())
        assertTrue(ai.none { it in Features.STARTS_ON })
        assertTrue(ai.none { it in Features.firstChoice(AppSettings(), FeatureUse(hasWorkouts = true)) })
    }

    @Test
    fun aiPartsBringWhatTheyNeed() {
        val voice = Features.switch(Features.STARTS_ON, Feature.AI_VOICE, true)
        assertTrue(Feature.AI_MEALS in voice)
        assertTrue(Feature.AI_VOICE !in Features.switch(voice, Feature.AI_MEALS, false))
        assertTrue(Feature.PLANNING in Features.switch(Features.STARTS_ON, Feature.AI_RECIPES, true))
        assertTrue(Feature.entries.filter { it.ai }.all { it.models.isNotEmpty() })
    }
}
