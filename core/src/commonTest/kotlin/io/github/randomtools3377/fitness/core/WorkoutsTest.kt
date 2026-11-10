package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutsTest {
    private val day = LocalDate(2026, 11, 9)
    private val squat = Exercises.builtIn("b:squat")!!
    private val pushUp = Exercises.builtIn("b:push-up")!!
    private val plank = Exercises.builtIn("b:plank")!!
    private val run = Exercises.builtIn("b:run")!!
    private val min = 60_000L

    private fun done(kg: Double?, reps: Int?, seconds: Int? = null, km: Double? = null) =
        WorkoutSet(weightKg = kg, reps = reps, seconds = seconds, distanceKm = km, done = true, doneAt = 1)

    private fun legDay(start: Long, sets: List<WorkoutSet>, minutes: Long = 60) =
        Workout(name = "Leg day", day = day, startedAt = start, endedAt = start + minutes * min, exercises = listOf(WorkoutExercise(squat, sets)))

    @Test
    fun theBuiltInListHasTheFourKindsAndUniqueKeys() {
        val kinds = Exercises.BUILT_IN.map { it.kind }.toSet()
        assertEquals(ExerciseKind.entries.toSet(), kinds)
        assertEquals(Exercises.BUILT_IN.size, Exercises.BUILT_IN.map { it.key }.toSet().size)
        assertTrue(Exercises.search("squat", Exercises.BUILT_IN).map { it.name }.containsAll(listOf("Squat", "Front squat", "Bodyweight squat")))
        assertEquals(listOf("Plank", "Plank shoulder tap", "Side plank"), Exercises.search("plank", Exercises.BUILT_IN).map { it.name })
    }

    @Test
    fun aSavedWorkoutFillsInEachExerciseFromLastTime() {
        val earlier = legDay(0, listOf(done(60.0, 8), done(80.0, 5), WorkoutSet(85.0, 5)))
        val saved = SavedWorkout(1, "Leg day", listOf(SavedWorkoutItem(squat, 3), SavedWorkoutItem(plank, 2)))
        val w = Workouts.start(saved, listOf(earlier), day, now = 10 * min)
        assertEquals(1L, w.savedWorkoutId)
        val sq = w.exercises[0].sets
        // Only the sets ticked last time count; the third set repeats the last one.
        assertEquals(listOf(60.0, 80.0, 80.0), sq.map { it.weightKg })
        assertEquals(listOf(8, 5, 5), sq.map { it.reps })
        assertTrue(sq.none { it.done })
        assertEquals(listOf(WorkoutSet(), WorkoutSet()), w.exercises[1].sets) // never done before
        assertNull(w.endedAt)
    }

    @Test
    fun aWorkoutCanBeSavedForNextTime() {
        val w = legDay(0, listOf(done(60.0, 8), done(80.0, 5)))
        val saved = Workouts.saveAs("Legs", w, 1)
        assertEquals(listOf(SavedWorkoutItem(squat, 2)), saved.items.map { it.copy(plan = emptyList()) })
        assertFailsWith<IllegalArgumentException> { Workouts.saveAs(" ", w, 1) }
    }

    @Test
    fun weightsBurnByTheWorkoutsLength() {
        // 5 MET × 70 kg × 1 hour.
        val w = legDay(0, listOf(done(80.0, 5)))
        assertEquals(350.0, Burned.workout(w, 70.0), 1e-9)
        assertEquals(175.0, Burned.workout(w, 70.0, half = true), 1e-9)
        // Nothing ticked: nothing burned.
        assertEquals(0.0, Burned.workout(legDay(0, listOf(WorkoutSet(80.0, 5))), 70.0), 1e-9)
    }

    @Test
    fun bodyweightUsesItsOwnLowerValue() {
        val w = Workout(name = "Home", day = day, startedAt = 0, endedAt = 30 * min, exercises = listOf(WorkoutExercise(pushUp, listOf(done(null, 20)))))
        assertEquals(3.8 * 70 * 0.5, Burned.workout(w, 70.0), 1e-9)
    }

    @Test
    fun cardioUsesItsSpeedWhenADistanceIsGiven() {
        // 10 km/h is 6.21 mph: between 6 mph (9.8) and 7 mph (11.0).
        assertEquals(9.8 + 1.2 * (10 / 1.609344 - 6.0), Mets.running(10.0), 1e-9)
        assertEquals(6.0, Mets.running(3.0)) // slower than the table: its slowest
        assertEquals(23.0, Mets.running(30.0)) // faster: its fastest
        val five = Workout(name = "Run", day = day, startedAt = 0, endedAt = 30 * min, exercises = listOf(WorkoutExercise(run, listOf(done(null, null, 1800, 5.0)))))
        assertEquals(Mets.running(10.0) * 70 * 0.5, Burned.workout(five, 70.0), 1e-9)
        // No distance: the activity's own value, 8 for running.
        val noDistance = five.copy(exercises = listOf(WorkoutExercise(run, listOf(done(null, null, 1800)))))
        assertEquals(8.0 * 70 * 0.5, Burned.workout(noDistance, 70.0), 1e-9)
    }

    @Test
    fun cardioTimeIsNotCountedTwiceInAMixedWorkout() {
        // 60 minutes in all, 20 of them running at 8 MET, the rest weights at 5.
        val w = Workout(
            name = "Mixed", day = day, startedAt = 0, endedAt = 60 * min,
            exercises = listOf(WorkoutExercise(squat, listOf(done(80.0, 5))), WorkoutExercise(run, listOf(done(null, null, 1200)))),
        )
        assertEquals(8.0 * 70 * (20 / 60.0) + 5.0 * 70 * (40 / 60.0), Burned.workout(w, 70.0), 1e-9)
    }

    @Test
    fun setsAreDescribedPlainly() {
        val u = UnitSettings()
        assertEquals("80 kg × 5", Workouts.describe(done(80.0, 5), u))
        assertEquals("1:15", Workouts.describe(done(null, null, 75), u))
        assertEquals("5 km in 28:30", Workouts.describe(done(null, null, 1710, 5.0), u))
        assertEquals("40 reps", Workouts.describe(done(null, 40), u))
        assertEquals("20 reps, 0:45", Workouts.describe(done(null, 20, 45), u))
        assertEquals("1:02:05", Workouts.clock(3725))
        assertEquals("3.1 miles", Units.distance(5.0, DistanceUnit.MILES))
    }

    @Test
    fun personalBestsAreBeatenOnlyWhenThereWasOneBefore() {
        val first = legDay(0, listOf(done(60.0, 8), done(80.0, 5)))
        assertEquals(emptyList(), Bests.beaten(emptyList(), squat, done(80.0, 5), day))
        val beaten = Bests.beaten(listOf(first), squat, done(82.5, 5), day)
        assertEquals(setOf(BestKind.HEAVIEST, BestKind.ONE_REP_MAX), beaten.map { it.kind }.toSet())
        assertEquals(80.0, beaten.first { it.kind == BestKind.HEAVIEST }.old.value)
        assertEquals(96.25, beaten.first { it.kind == BestKind.ONE_REP_MAX }.new.value, 1e-9)
        // More reps at the heaviest weight is a best; equal is not.
        assertEquals(listOf(BestKind.REPS_AT_WEIGHT, BestKind.ONE_REP_MAX), Bests.beaten(listOf(first), squat, done(80.0, 6), day).map { it.kind })
        assertEquals(emptyList(), Bests.beaten(listOf(first), squat, done(80.0, 5), day))
        // A ticked-off set is needed.
        assertEquals(emptyList(), Bests.beaten(listOf(first), squat, WorkoutSet(100.0, 5), day))
    }

    @Test
    fun theCurrentBestsAreListedPerExercise() {
        val runs = listOf(
            Workout(name = "Run", day = day, startedAt = 0, endedAt = 1, exercises = listOf(WorkoutExercise(run, listOf(done(null, null, 1800, 5.0))))),
            Workout(name = "Run", day = day, startedAt = 2, endedAt = 3, exercises = listOf(WorkoutExercise(run, listOf(done(null, null, 3600, 8.0))))),
        )
        val all = Bests.all(runs)
        assertEquals(8.0, all.first { it.kind == BestKind.LONGEST_DISTANCE }.value)
        assertEquals(10.0, all.first { it.kind == BestKind.FASTEST }.value, 1e-9) // 5 km in 30 minutes
        assertEquals("6:00 per km", Best.pace(10.0, UnitSettings()))
        assertEquals(96.25, Bests.oneRepMax(82.5, 5)!!, 1e-9)
        assertNull(Bests.oneRepMax(50.0, 20))
    }

    @Test
    fun whatIsLoggedIsTheUsersChoiceAndTheTagOnlyATag() {
        assertEquals(listOf(Track.WEIGHT, Track.REPS), squat.tracks)
        val jacks = Exercises.builtIn("b:jumping-jacks")!!
        assertEquals(ExerciseKind.CARDIO, jacks.kind)
        assertEquals(listOf(Track.REPS), jacks.tracks)
        val chosen = mapOf(run.key to listOf(Track.REPS))
        assertEquals(listOf(Track.REPS), Exercises.withTrack(run, chosen).tracks)
        assertEquals(run, Exercises.withTrack(run, emptyMap()))
        assertEquals(listOf(Track.MINUTES, Track.DISTANCE), Track.decode(Track.encode(listOf(Track.MINUTES, Track.DISTANCE))))
        assertNull(Track.decode(null))
        // Cardio counted in reps gets a most-reps best, and a reps hint.
        val repRun = Exercises.withTrack(run, chosen)
        assertEquals(listOf(BestKind.MOST_REPS), Bests.of(repRun, done(null, 50), day).map { it.kind })
        assertEquals(51, Progression.hint(repRun, listOf(done(null, 50)), WeightUnit.KG)?.reps)
        for (name in listOf("Jumping jacks", "Mountain climbers", "Diamond push-up", "Incline push-up")) {
            assertTrue(Exercises.BUILT_IN.any { it.name == name }, name)
        }
    }

    @Test
    fun repsCountTheWorkTheyAreEvenInAShortWorkout() {
        // 120 push-ups typed in 15 seconds after starting: by the sets, 120 × 3 s at 3.8 MET.
        val w = Workout(
            name = "Push-ups", day = day, startedAt = 0, endedAt = 15_000,
            exercises = listOf(WorkoutExercise(pushUp, listOf(done(null, 60), done(null, 60)))),
        )
        assertEquals(3.8 * 70 * 360 / 3600.0, Burned.workout(w, 70.0), 1e-9)
        // A long workout still counts by its length, which is more.
        val long = w.copy(endedAt = 30 * min)
        assertEquals(3.8 * 70 * 0.5, Burned.workout(long, 70.0), 1e-9)
        // Quick moves have their own pace.
        val jacks = Exercises.builtIn("b:jumping-jacks")!!
        val j = w.copy(exercises = listOf(WorkoutExercise(jacks, listOf(done(null, 100)))))
        assertEquals(8.0 * 70 * 100 / 3600.0, Burned.workout(j, 70.0), 1e-9)
    }

    @Test
    fun aPlannedWorkoutUsesItsPlanFirstAndItsRestEveryTime() {
        val plan = listOf(WorkoutSet(reps = 20, restSeconds = 30), WorkoutSet(reps = 20, restSeconds = 60))
        val sw = SavedWorkout(1, "Circuit", listOf(SavedWorkoutItem(pushUp, 2, superset = 1, plan = plan)))
        val first = Workouts.start(sw, emptyList(), day, 0)
        assertEquals(listOf(20, 20), first.exercises[0].sets.map { it.reps })
        assertEquals(listOf(30, 60), first.exercises[0].sets.map { it.restSeconds })
        assertEquals(1, first.exercises[0].superset)
        // After a workout, its numbers come from last time; the rest stays as planned.
        val did = first.copy(endedAt = 10 * min, exercises = listOf(first.exercises[0].copy(sets = listOf(done(null, 22), done(null, 21)))))
        val next = Workouts.start(sw, listOf(did), day, 20 * min)
        assertEquals(listOf(22, 21), next.exercises[0].sets.map { it.reps })
        assertEquals(listOf(30, 60), next.exercises[0].sets.map { it.restSeconds })
        // Saving a workout keeps each set's plan.
        val saved = Workouts.saveAs("Again", did, 0)
        assertEquals(listOf(22, 21), saved.items[0].plan.map { it.reps })
        assertTrue(saved.items[0].plan.none { it.done })
    }

    @Test
    fun movingAnExerciseKeepsOrEndsItsGroup() {
        val a = WorkoutExercise(squat, emptyList(), 1)
        val b = WorkoutExercise(pushUp, emptyList(), 1)
        val c = WorkoutExercise(plank, emptyList())
        // A group moves as one.
        assertEquals(listOf(c, a, b), Supersets.move(listOf(a, b, c), 1, 1))
        assertEquals(listOf(c, a, b), Supersets.move(listOf(a, b, c), 2, -1))
        assertEquals(listOf(a, b, c), Supersets.move(listOf(a, b, c), 0, -1))
        assertEquals(listOf(1, 1, null), Supersets.tidy(listOf(a, b, c)).map { it.superset })
    }
}
