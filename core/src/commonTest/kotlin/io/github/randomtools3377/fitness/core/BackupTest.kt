package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
        val newer = Backup.encode(sample()).replace("\"format\": ${Backup.FORMAT}", "\"format\": 99")
        assertTrue(assertFailsWith<BackupError> { Backup.decode(newer) }.message!!.contains("newer version"))
    }

    @Test
    fun aFileFromVersion0_1WithoutWorkoutsStillReads() {
        val json = Json.parseToJsonElement(Backup.encode(sample())).jsonObject
        val old = JsonObject(json - setOf("exercises", "workouts", "savedWorkouts", "restSeconds") + ("format" to JsonPrimitive(1))).toString()
        assertTrue(!old.contains("savedWorkouts"))
        val b = Backup.decode(old)
        assertEquals(1, b.format)
        assertEquals(emptyList(), b.workouts)
        assertEquals(sample().entries, b.entries)
    }

    @Test
    fun workoutsComeBackTheSame() {
        val squat = Exercises.builtIn("b:squat")!!
        val w = Workout(
            id = 3, name = "Leg day", day = day, startedAt = 1000, endedAt = 61000, savedWorkoutId = 2, burnedKcal = 290.0,
            exercises = listOf(WorkoutExercise(squat, listOf(WorkoutSet(80.0, 5, done = true, doneAt = 5000)))),
        )
        val b = sample().copy(
            exercises = listOf(Exercises.own(1, "Sled push", ExerciseKind.WEIGHTS)),
            workouts = listOf(w),
            savedWorkouts = listOf(SavedWorkout(2, "Leg day", listOf(SavedWorkoutItem(squat, 3)), 1)),
            restSeconds = mapOf("b:squat" to 120),
        )
        assertEquals(b, Backup.decode(Backup.encode(b)))
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
