package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtrasExportTest {
    @Test
    fun stepsDuringALoggedWorkoutDontCountTwice() {
        val records = listOf(StepCount(0, 3_600_000, 4000), StepCount(3_600_000, 7_200_000, 6000))
        assertEquals(10000, Steps.countable(records, emptyList()))
        assertEquals(4000, Steps.countable(records, listOf(3_700_000L to 5_000_000L)))
        assertEquals(350.0, Steps.kcal(10000, 70.0))
        assertEquals(175.0, Steps.kcal(10000, 70.0, half = true))
    }

    @Test
    fun theDiaryAsASpreadsheet() {
        val day = LocalDate(2026, 11, 9)
        val e = DiaryEntry(day = day, meal = Meal.LUNCH, foodId = 1, name = "Soup, \"tomato\", hot", amount = Amount(250.0), nutrients = Nutrients(120.0, 3.0, 15.0, 5.0, salt = 1.2))
        val planned = e.copy(name = "Later", planned = true)
        val csv = CsvExport.diary(listOf(e, planned))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("2026-11-09,Lunch,\"Soup, \"\"tomato\"\", hot\",250 g,120,3,15,5,,,,1.2"))
    }

    @Test
    fun workoutsOneRowPerSet() {
        val squat = Exercises.BUILT_IN.first { it.kind == ExerciseKind.WEIGHTS }
        val w = Workout(
            name = "Legs", day = LocalDate(2026, 11, 9), startedAt = 0, endedAt = 1,
            exercises = listOf(WorkoutExercise(squat, listOf(WorkoutSet(40.0, 10, done = true, warmUp = true), WorkoutSet(60.0, 5, done = true)))),
        )
        val lines = CsvExport.workouts(listOf(w), TimeZone.UTC).trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains(",1,yes,yes,40,10,,"))
        assertEquals(5, CsvExport.all(emptyList(), emptyList(), emptyList(), listOf(w), emptyList(), TimeZone.UTC).size)
    }
}
