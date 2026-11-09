package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupTest {
    private val day = LocalDate(2026, 11, 9)
    private val oats = Food(id = 1, name = "Oats", brand = "Mill", per100 = Nutrients(380.0, 13.0, 60.0, 7.0), portions = listOf(Portion("1 bowl", 40.0)))

    private fun sample() = Backup(
        exportedAt = 123,
        foods = listOf(oats),
        entries = listOf(
            Diary.entry(oats, Amount(1.0, "1 bowl"), day, Meal.BREAKFAST, 10).copy(id = 1),
            Diary.quickAdd(250.0, day, Meal.SNACKS, 11).copy(id = 2),
        ),
        savedMeals = listOf(SavedMeal(1, "Porridge", listOf(SavedMealItem(1, null, "Oats (Mill)", Amount(1.0, "1 bowl"), BaseUnit.GRAM, Nutrients(152.0))), 12)),
        water = listOf(WaterEntry(1, day, 250, 13)),
        weighIns = listOf(WeighIn(1, day, 72.4, 14)),
        settings = AppSettings(
            profile = Profile(Sex.FEMALE, 1990, 170.0, 72.4, Activity.LIGHT, WeightGoal.LOSE, 0.25),
            targetKcal = 1800,
            lastWorkedOutKcal = 1810,
            macros = MacroTargets(MacroMode.GRAMS, 120.0, 180.0, 60.0),
            water = WaterSettings(2500, 300, 750),
            units = UnitSettings(WeightUnit.LB),
        ),
    )

    @Test
    fun everythingComesBackTheSame() {
        val b = sample()
        val text = Backup.encode(b)
        assertTrue(text.contains("\"app\": \"fitness\""))
        assertEquals(b, Backup.decode(text))
    }

    @Test
    fun otherFilesAreRefusedInPlainWords() {
        assertEquals("This file isn't a Fitness App backup, or it is damaged.", assertFailsWith<BackupError> { Backup.decode("hello") }.message)
        val other = Backup.encode(sample()).replace("\"app\": \"fitness\"", "\"app\": \"checklist\"")
        assertEquals("This file isn't a Fitness App backup.", assertFailsWith<BackupError> { Backup.decode(other) }.message)
        val newer = Backup.encode(sample()).replace("\"format\": 1", "\"format\": 99")
        assertTrue(assertFailsWith<BackupError> { Backup.decode(newer) }.message!!.contains("newer version"))
    }

    @Test
    fun entriesMustPointToFoodsInTheFile() {
        val broken = sample().copy(foods = emptyList())
        assertFailsWith<BackupError> { Backup.decode(Backup.encode(broken)) }
    }

    @Test
    fun unknownFieldsFromALaterVersionAreIgnored() {
        val text = Backup.encode(sample()).replaceFirst("{", "{\n  \"workouts\": [],")
        assertEquals(sample(), Backup.decode(text))
    }

    @Test
    fun settingsReadBackAndFallBackToDefaults() {
        val s = sample().settings
        assertEquals(s, AppSettings.decode(AppSettings.encode(s)))
        assertEquals(AppSettings(), AppSettings.decode(null))
        assertEquals(AppSettings(), AppSettings.decode("not json"))
    }
}
