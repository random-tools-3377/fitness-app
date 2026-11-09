package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** The four kinds of exercise. */
@Serializable
enum class ExerciseKind(val title: String) {
    /** Sets of reps with a weight. */
    WEIGHTS("Weights"),

    /** Sets of reps with the body's own weight, like push-ups. */
    BODYWEIGHT("Bodyweight"),

    /** Held or done for a time, like a plank. */
    TIMED("Timed"),

    /** Time and, if known, distance, typed in. */
    CARDIO("Cardio"),
}

/** What kind of cardio, for working out its effort from its speed. */
@Serializable
enum class CardioType { RUN, WALK, CYCLE, OTHER }

/**
 * An exercise: built in (with a [key] like "b:squat") or the user's own ("u:12"). [met] is its
 * standard activity value (MET), used for the calories it burns.
 */
@Serializable
data class Exercise(
    val key: String,
    val name: String,
    val kind: ExerciseKind,
    val met: Double,
    val muscle: String = "",
    val cardio: CardioType = CardioType.OTHER,
)

/**
 * Activity values (METs) from the Compendium of Physical Activities (Ainsworth and others), the
 * standard table of how hard activities are compared with sitting still.
 */
object Mets {
    /** Weight training, several exercises, a good effort. */
    const val WEIGHTS = 5.0

    /** Calisthenics (push-ups, sit-ups, pull-ups, lunges), moderate effort. */
    const val BODYWEIGHT = 3.8

    /** Running at a speed in km/h, from the Compendium's running speeds. */
    fun running(kmh: Double): Double = byMph(kmh, RUN)

    /** Walking at a speed in km/h. */
    fun walking(kmh: Double): Double = byMph(kmh, WALK)

    /** Cycling at a speed in km/h. */
    fun cycling(kmh: Double): Double = byMph(kmh, CYCLE)

    // (miles an hour, MET), slowest first. Between two speeds, the MET is read off the line between them.
    private val RUN = listOf(4.0 to 6.0, 5.0 to 8.3, 6.0 to 9.8, 7.0 to 11.0, 8.0 to 11.8, 9.0 to 12.8, 10.0 to 14.5, 11.0 to 16.0, 12.0 to 19.0, 13.0 to 19.8, 14.0 to 23.0)
    private val WALK = listOf(2.0 to 2.8, 2.5 to 3.0, 3.0 to 3.5, 3.5 to 4.3, 4.0 to 5.0, 4.5 to 7.0)
    private val CYCLE = listOf(5.5 to 3.5, 9.4 to 5.8, 11.0 to 6.8, 13.0 to 8.0, 15.0 to 10.0, 17.5 to 12.0, 20.0 to 15.8)

    private fun byMph(kmh: Double, table: List<Pair<Double, Double>>): Double {
        val mph = kmh / Units.KM_PER_MILE
        if (mph <= table.first().first) return table.first().second
        if (mph >= table.last().first) return table.last().second
        for (i in 1 until table.size) {
            val (s1, m1) = table[i - 1]
            val (s2, m2) = table[i]
            if (mph <= s2) return m1 + (m2 - m1) * (mph - s1) / (s2 - s1)
        }
        return table.last().second
    }
}

/** The built-in list of common exercises. */
object Exercises {
    private fun w(id: String, name: String, muscle: String) = Exercise("b:$id", name, ExerciseKind.WEIGHTS, Mets.WEIGHTS, muscle)
    private fun b(id: String, name: String, muscle: String) = Exercise("b:$id", name, ExerciseKind.BODYWEIGHT, Mets.BODYWEIGHT, muscle)
    private fun t(id: String, name: String, muscle: String, met: Double = Mets.BODYWEIGHT) = Exercise("b:$id", name, ExerciseKind.TIMED, met, muscle)
    private fun c(id: String, name: String, met: Double, type: CardioType = CardioType.OTHER) = Exercise("b:$id", name, ExerciseKind.CARDIO, met, "Cardio", type)

    val BUILT_IN: List<Exercise> = listOf(
        w("squat", "Squat", "Legs"),
        w("front-squat", "Front squat", "Legs"),
        w("deadlift", "Deadlift", "Back"),
        w("romanian-deadlift", "Romanian deadlift", "Legs"),
        w("leg-press", "Leg press", "Legs"),
        w("lunge", "Lunge (with weights)", "Legs"),
        w("leg-extension", "Leg extension", "Legs"),
        w("leg-curl", "Leg curl", "Legs"),
        w("calf-raise", "Calf raise", "Legs"),
        w("hip-thrust", "Hip thrust", "Legs"),
        w("bench-press", "Bench press", "Chest"),
        w("incline-bench-press", "Incline bench press", "Chest"),
        w("dumbbell-press", "Dumbbell bench press", "Chest"),
        w("chest-fly", "Chest fly", "Chest"),
        w("overhead-press", "Overhead press", "Shoulders"),
        w("lateral-raise", "Lateral raise", "Shoulders"),
        w("face-pull", "Face pull", "Shoulders"),
        w("barbell-row", "Barbell row", "Back"),
        w("dumbbell-row", "Dumbbell row", "Back"),
        w("lat-pulldown", "Lat pulldown", "Back"),
        w("cable-row", "Seated cable row", "Back"),
        w("biceps-curl", "Biceps curl", "Arms"),
        w("hammer-curl", "Hammer curl", "Arms"),
        w("triceps-pushdown", "Triceps pushdown", "Arms"),
        w("skull-crusher", "Skull crusher", "Arms"),
        w("kettlebell-swing", "Kettlebell swing", "Full body"),
        b("push-up", "Push-up", "Chest"),
        b("pull-up", "Pull-up", "Back"),
        b("chin-up", "Chin-up", "Back"),
        b("dip", "Dip", "Arms"),
        b("bodyweight-squat", "Bodyweight squat", "Legs"),
        b("bodyweight-lunge", "Lunge", "Legs"),
        b("sit-up", "Sit-up", "Core"),
        b("crunch", "Crunch", "Core"),
        b("leg-raise", "Hanging leg raise", "Core"),
        b("burpee", "Burpee", "Full body"),
        b("glute-bridge", "Glute bridge", "Legs"),
        t("plank", "Plank", "Core"),
        t("side-plank", "Side plank", "Core"),
        t("wall-sit", "Wall sit", "Legs"),
        t("dead-hang", "Dead hang", "Back"),
        t("jump-rope", "Jump rope", "Cardio", met = 11.8),
        c("run", "Running", 8.0, CardioType.RUN),
        c("treadmill", "Treadmill run", 8.0, CardioType.RUN),
        c("walk", "Walking", 3.5, CardioType.WALK),
        c("cycle", "Cycling", 7.5, CardioType.CYCLE),
        c("exercise-bike", "Exercise bike, moderate", 6.8),
        c("rowing-machine", "Rowing machine, moderate", 7.0),
        c("elliptical", "Elliptical trainer", 5.0),
        c("swim", "Swimming laps, moderate", 5.8),
        c("stair-climber", "Stair climber", 9.0),
        c("hiit", "Circuit or HIIT class", 8.0),
        c("yoga", "Yoga", 2.5),
        c("dance", "Dancing", 5.0),
        c("hike", "Hiking", 6.0, CardioType.WALK),
    )

    private val byKey = BUILT_IN.associateBy { it.key }

    fun builtIn(key: String): Exercise? = byKey[key]

    /** A new exercise of the user's own, with the usual activity value for its kind. */
    fun own(id: Long, name: String, kind: ExerciseKind): Exercise = Exercise(
        "u:$id", name.trim(), kind,
        when (kind) {
            ExerciseKind.WEIGHTS -> Mets.WEIGHTS
            ExerciseKind.BODYWEIGHT, ExerciseKind.TIMED -> Mets.BODYWEIGHT
            ExerciseKind.CARDIO -> 6.0
        },
    )

    /** Exercises whose name has every word typed at the start of one of its words. */
    fun search(query: String, all: List<Exercise>): List<Exercise> {
        val q = FoodSearch.words(query)
        if (q.isEmpty()) return all.sortedBy { it.name.lowercase() }
        return all.filter { e -> val w = FoodSearch.words(e.name + " " + e.muscle); q.all { p -> w.any { it.startsWith(p) } } }
            .sortedBy { it.name.lowercase() }
    }

    const val DEFAULT_REST_SECONDS = 90
}

/**
 * One set. Weights use [weightKg] and [reps]; bodyweight uses [reps]; timed uses [seconds];
 * cardio uses [seconds] and, if known, [distanceKm].
 */
@Serializable
data class WorkoutSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val distanceKm: Double? = null,
    val done: Boolean = false,
    /** Epoch milliseconds when it was ticked. */
    val doneAt: Long? = null,
)

/** An exercise as done in one workout: its sets in order. */
@Serializable
data class WorkoutExercise(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
)

/**
 * A workout. While it is in progress [endedAt] is null; it is kept as it goes, so closing the
 * app loses nothing. [burnedKcal] is worked out when it is finished.
 */
@Serializable
data class Workout(
    val id: Long = 0,
    val name: String,
    val day: LocalDate,
    val startedAt: Long,
    val endedAt: Long? = null,
    val exercises: List<WorkoutExercise> = emptyList(),
    val savedWorkoutId: Long? = null,
    val burnedKcal: Double = 0.0,
) {
    val finished: Boolean get() = endedAt != null

    /** Minutes from start to finish (or to [now] while in progress). */
    fun minutes(now: Long = endedAt ?: startedAt): Double = ((endedAt ?: now) - startedAt).coerceAtLeast(0) / 60_000.0
}

/** A saved workout, like "Leg day": its exercises and how many sets of each. */
@Serializable
data class SavedWorkout(
    val id: Long = 0,
    val name: String,
    val items: List<SavedWorkoutItem>,
    val createdAt: Long = 0,
)

@Serializable
data class SavedWorkoutItem(val exercise: Exercise, val sets: Int)

object Workouts {
    /**
     * Starts [saved], filling in each exercise from the last time it was done: the same weights,
     * reps and times, not yet ticked. An exercise never done before gets empty sets.
     */
    fun start(saved: SavedWorkout, history: List<Workout>, day: LocalDate, now: Long): Workout = Workout(
        name = saved.name,
        day = day,
        startedAt = now,
        savedWorkoutId = saved.id,
        exercises = saved.items.map { item ->
            val last = lastTime(item.exercise.key, history)
            val sets = (0 until item.sets.coerceAtLeast(1)).map { i ->
                val from = last?.getOrNull(i) ?: last?.lastOrNull()
                from?.copy(done = false, doneAt = null) ?: WorkoutSet()
            }
            WorkoutExercise(item.exercise, sets)
        },
    )

    /** The sets done of an exercise the last time it was in a finished workout, or null. */
    fun lastTime(exerciseKey: String, history: List<Workout>): List<WorkoutSet>? =
        history.filter { it.finished }.sortedByDescending { it.startedAt }
            .firstNotNullOfOrNull { w -> w.exercises.firstOrNull { it.exercise.key == exerciseKey && it.sets.any { s -> s.done } }?.sets?.filter { it.done } }

    /** A new exercise added to a workout in progress: one set, filled in from last time. */
    fun addExercise(w: Workout, e: Exercise, history: List<Workout>): Workout {
        val last = lastTime(e.key, history)
        val sets = last?.map { it.copy(done = false, doneAt = null) } ?: listOf(WorkoutSet())
        return w.copy(exercises = w.exercises + WorkoutExercise(e, sets))
    }

    /** A saved workout made from the exercises of [w], with as many sets as were done. */
    fun saveAs(name: String, w: Workout, now: Long): SavedWorkout {
        require(name.isNotBlank()) { "Give the workout a name." }
        require(w.exercises.isNotEmpty()) { "There are no exercises to save." }
        return SavedWorkout(
            name = name.trim(),
            items = w.exercises.map { SavedWorkoutItem(it.exercise, it.sets.size.coerceAtLeast(1)) },
            createdAt = now,
        )
    }

    /** "3 × 80 kg × 5", "45 s", "5 km in 28:30". */
    fun describe(set: WorkoutSet, kind: ExerciseKind, u: UnitSettings): String = when (kind) {
        ExerciseKind.WEIGHTS -> "${set.weightKg?.let { Units.weight(it, u.weight) } ?: "—"} × ${set.reps ?: "—"}"
        ExerciseKind.BODYWEIGHT -> "${set.reps ?: "—"} reps"
        ExerciseKind.TIMED -> clock(set.seconds ?: 0)
        ExerciseKind.CARDIO -> (set.distanceKm?.let { Units.distance(it, u.distance) + " in " } ?: "") + clock(set.seconds ?: 0)
    }

    /** 75 → "1:15", 3725 → "1:02:05". */
    fun clock(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
    }
}

/**
 * Calories burned, from standard activity values: MET × body weight (kg) × hours. Weights,
 * bodyweight and timed exercises use the workout's length; cardio uses its own time, and its
 * speed when a distance is given.
 */
object Burned {
    fun cardioMet(e: Exercise, set: WorkoutSet): Double {
        val sec = set.seconds ?: return e.met
        val km = set.distanceKm
        if (km == null || km <= 0 || sec <= 0) return e.met
        val kmh = km / (sec / 3600.0)
        return when (e.cardio) {
            CardioType.RUN -> Mets.running(kmh)
            CardioType.WALK -> Mets.walking(kmh)
            CardioType.CYCLE -> Mets.cycling(kmh)
            CardioType.OTHER -> e.met
        }
    }

    /** kcal for [w] at [weightKg]. [half] counts only half, because estimates tend to run high. */
    fun workout(w: Workout, weightKg: Double, now: Long = w.endedAt ?: w.startedAt, half: Boolean = false): Double {
        var kcal = 0.0
        var cardioMinutes = 0.0
        for (we in w.exercises.filter { it.exercise.kind == ExerciseKind.CARDIO }) {
            for (s in we.sets.filter { it.done }) {
                val sec = s.seconds ?: 0
                kcal += cardioMet(we.exercise, s) * weightKg * sec / 3600.0
                cardioMinutes += sec / 60.0
            }
        }
        val strength = w.exercises.filter { it.exercise.kind != ExerciseKind.CARDIO && it.sets.any { s -> s.done } }
        if (strength.isNotEmpty()) {
            // The workout's length, less any time spent on cardio in it, at the hardest of its strength exercises.
            val minutes = (w.minutes(now) - cardioMinutes).coerceAtLeast(0.0)
            val met = strength.maxOf { it.exercise.met }
            kcal += met * weightKg * minutes / 60.0
        }
        return if (half) kcal / 2 else kcal
    }

    /** A rough estimate, so to the nearest whole kcal. */
    fun rounded(kcal: Double): Double = kcal.roundToInt().toDouble()
}
