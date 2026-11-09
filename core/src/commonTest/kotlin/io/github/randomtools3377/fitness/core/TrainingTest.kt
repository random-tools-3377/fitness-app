package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingTest {
    private val squat = Exercises.BUILT_IN.first { it.kind == ExerciseKind.WEIGHTS }
    private val pushUp = Exercises.BUILT_IN.first { it.kind == ExerciseKind.BODYWEIGHT }

    @Test
    fun platesForEachSide() {
        val p = PlateSettings()
        val l = Plates.load(102.5, p.barKg, p.platesKg)!!
        assertEquals(listOf(25.0, 15.0, 1.25), l.perSide)
        assertTrue(l.exact)
        assertEquals("Nothing: just the bar", Plates.describe(Plates.load(20.0, 20.0, p.platesKg)!!))
        assertNull(Plates.load(15.0, 20.0, p.platesKg))
        val odd = Plates.load(21.0, 20.0, p.platesKg)!!
        assertFalse(odd.exact)
        assertEquals(1.0, odd.left, 1e-9)
        assertEquals(listOf(45.0, 25.0), Plates.load(185.0, p.barLb, p.platesLb)!!.perSide)
    }

    @Test
    fun aHintWhenEverySetWasDone() {
        val last = listOf(
            WorkoutSet(weightKg = 40.0, reps = 10, done = true, warmUp = true),
            WorkoutSet(weightKg = 60.0, reps = 8, done = true),
            WorkoutSet(weightKg = 60.0, reps = 8, done = true),
        )
        val h = Progression.hint(squat, last, WeightUnit.KG)!!
        assertEquals(62.5, h.weightKg)
        assertTrue("2 sets" in h.text)
        assertNull(Progression.hint(squat, last.dropLast(1) + last.last().copy(done = false), WeightUnit.KG))
        assertEquals(9, Progression.hint(pushUp, listOf(WorkoutSet(reps = 8, done = true), WorkoutSet(reps = 7, done = true)), WeightUnit.KG)!!.reps)
        val we = WorkoutExercise(squat, last.map { it.copy(done = false) })
        val applied = Progression.apply(we, h)
        assertEquals(listOf(40.0, 62.5, 62.5), applied.sets.map { it.weightKg })
    }

    @Test
    fun warmUpsAreNotBests() {
        val day = LocalDate(2026, 11, 9)
        val w = Workout(
            name = "Legs", day = day, startedAt = 0, endedAt = 1,
            exercises = listOf(WorkoutExercise(squat, listOf(WorkoutSet(weightKg = 100.0, reps = 1, done = true, warmUp = true), WorkoutSet(weightKg = 60.0, reps = 5, done = true)))),
        )
        val bests = Bests.all(listOf(w))
        assertTrue(bests.none { it.value >= 100.0 && it.kind == BestKind.HEAVIEST })
    }

    @Test
    fun supersetsRestOnlyAfterTheLastExercise() {
        var w = Workout(name = "Arms", day = LocalDate(2026, 11, 9), startedAt = 0, exercises = listOf(squat, pushUp, squat).map { WorkoutExercise(it, listOf(WorkoutSet())) })
        w = Supersets.joinWithNext(w, 0)
        assertEquals(w.exercises[0].superset, w.exercises[1].superset)
        assertNotNull(w.exercises[0].superset)
        assertFalse(Supersets.restAfter(w, 0))
        assertTrue(Supersets.restAfter(w, 1))
        assertTrue(Supersets.restAfter(w, 2))
        w = Supersets.leave(w, 1)
        assertTrue(w.exercises.all { it.superset == null })
    }

    @Test
    fun theProgrammeSaysTodaysWorkout() {
        val push = SavedWorkout(1, "Push", emptyList())
        val legs = SavedWorkout(2, "Legs", emptyList())
        val prog = mapOf(1 to 1L, 5 to 2L)
        assertEquals(push, Programme.today(prog, listOf(push, legs), LocalDate(2026, 11, 9))) // a Monday
        assertNull(Programme.today(prog, listOf(push, legs), LocalDate(2026, 11, 10)))
        assertEquals("Mon: Push · Fri: Legs", Programme.describe(prog, listOf(push, legs)))
    }
}
