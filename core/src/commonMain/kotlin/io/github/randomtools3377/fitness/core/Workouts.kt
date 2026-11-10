package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * The four tags an exercise can have (0.20.0: only a tag, shown in the list and used for the
 * usual activity value; what is logged for it is its [Track]s, which the user chooses).
 */
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

/** What can be logged for each set of an exercise (0.20.0). Times are kept in seconds either way. */
@Serializable
enum class Track(val title: String) {
    WEIGHT("Weight"),
    REPS("Reps"),
    SECONDS("Time in seconds"),
    MINUTES("Time in minutes"),
    DISTANCE("Distance"),
    ;

    companion object {
        /** What each tag logged before 0.20.0, and still logs until the user changes it. */
        fun usual(kind: ExerciseKind): List<Track> = when (kind) {
            ExerciseKind.WEIGHTS -> listOf(WEIGHT, REPS)
            ExerciseKind.BODYWEIGHT -> listOf(REPS)
            ExerciseKind.TIMED -> listOf(SECONDS)
            ExerciseKind.CARDIO -> listOf(MINUTES, DISTANCE)
        }

        /** "WEIGHT,REPS", for the database. */
        fun encode(t: List<Track>?): String? = t?.joinToString(",") { it.name }

        fun decode(s: String?): List<Track>? = s?.split(',')?.mapNotNull { n -> entries.firstOrNull { it.name == n.trim() } }?.takeIf { it.isNotEmpty() }

        /** In the usual order, so the boxes are always in the same places. */
        fun ordered(t: Collection<Track>): List<Track> = entries.filter { it in t }
    }
}

/** What kind of cardio, for working out its effort from its speed. */
@Serializable
enum class CardioType { RUN, WALK, CYCLE, OTHER }

/**
 * An exercise: built in (with a [key] like "b:squat") or the user's own ("u:12"). [met] is its
 * standard activity value (MET), used for the calories it burns. [track] is what the user chose
 * to log for it (null: the usual for its tag), and [secondsPerRep] how long one rep takes, for
 * the calories of sets counted in reps.
 */
@Serializable
data class Exercise(
    val key: String,
    val name: String,
    val kind: ExerciseKind,
    val met: Double,
    val muscle: String = "",
    val cardio: CardioType = CardioType.OTHER,
    val track: List<Track>? = null,
    val secondsPerRep: Double = Exercises.SECONDS_PER_REP,
) {
    /** What is logged for each set: the user's choice, or else the usual for this exercise. */
    val tracks: List<Track> get() = track?.takeIf { it.isNotEmpty() } ?: Exercises.usualTrack(this)

    /** Logged in minutes, like a run: no rest timer after each set. */
    val long: Boolean get() = Track.MINUTES in tracks
}

/**
 * Activity values (METs) from the Compendium of Physical Activities (Ainsworth and others), the
 * standard table of how hard activities are compared with sitting still.
 */
object Mets {
    /** Weight training, several exercises, a good effort. */
    const val WEIGHTS = 5.0

    /** Calisthenics (push-ups, sit-ups, pull-ups, lunges), moderate effort. */
    const val BODYWEIGHT = 3.8

    /** Calisthenics (push-ups, sit-ups, pull-ups, jumping jacks), vigorous effort. */
    const val VIGOROUS = 8.0

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
    private fun b(id: String, name: String, muscle: String, met: Double = Mets.BODYWEIGHT, perRep: Double = SECONDS_PER_REP) =
        Exercise("b:$id", name, ExerciseKind.BODYWEIGHT, met, muscle, secondsPerRep = perRep)
    private fun t(id: String, name: String, muscle: String, met: Double = Mets.BODYWEIGHT) = Exercise("b:$id", name, ExerciseKind.TIMED, met, muscle)
    private fun c(id: String, name: String, met: Double, type: CardioType = CardioType.OTHER) = Exercise("b:$id", name, ExerciseKind.CARDIO, met, "Cardio", type)

    /** Quick cardio moves, counted in reps or seconds (0.20.0). */
    private fun q(id: String, name: String, track: Track, perRep: Double = 1.0) =
        Exercise("b:$id", name, ExerciseKind.CARDIO, Mets.VIGOROUS, "Cardio", track = listOf(track), secondsPerRep = perRep)

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
        w("goblet-squat", "Goblet squat", "Legs"),
        w("bulgarian-split-squat", "Bulgarian split squat", "Legs"),
        w("sumo-deadlift", "Sumo deadlift", "Legs"),
        w("shrug", "Shrug", "Shoulders"),
        w("arnold-press", "Arnold press", "Shoulders"),
        w("overhead-triceps-extension", "Overhead triceps extension", "Arms"),
        b("push-up", "Push-up", "Chest"),
        b("pull-up", "Pull-up", "Back"),
        b("chin-up", "Chin-up", "Back"),
        b("dip", "Dip", "Arms"),
        b("bodyweight-squat", "Bodyweight squat", "Legs"),
        b("bodyweight-lunge", "Lunge", "Legs"),
        b("sit-up", "Sit-up", "Core"),
        b("crunch", "Crunch", "Core"),
        b("leg-raise", "Hanging leg raise", "Core"),
        b("burpee", "Burpee", "Full body", met = Mets.VIGOROUS, perRep = 4.0),
        b("glute-bridge", "Glute bridge", "Legs"),
        // 0.20.0: more bodyweight moves.
        b("knee-push-up", "Knee push-up", "Chest"),
        b("incline-push-up", "Incline push-up", "Chest"),
        b("decline-push-up", "Decline push-up", "Chest"),
        b("wide-push-up", "Wide push-up", "Chest"),
        b("diamond-push-up", "Diamond push-up", "Arms"),
        b("pike-push-up", "Pike push-up", "Shoulders"),
        b("archer-push-up", "Archer push-up", "Chest"),
        b("wall-push-up", "Wall push-up", "Chest"),
        b("bench-dip", "Bench dip", "Arms"),
        b("inverted-row", "Inverted row", "Back"),
        b("superman", "Superman", "Back"),
        b("back-extension", "Back extension", "Back"),
        b("jump-squat", "Jump squat", "Legs", met = Mets.VIGOROUS, perRep = 2.0),
        b("jump-lunge", "Jump lunge", "Legs", met = Mets.VIGOROUS, perRep = 2.0),
        b("box-jump", "Box jump", "Legs", met = Mets.VIGOROUS),
        b("step-up", "Step-up", "Legs"),
        b("side-lunge", "Side lunge", "Legs"),
        b("bodyweight-calf-raise", "Calf raise (bodyweight)", "Legs", perRep = 2.0),
        b("single-leg-glute-bridge", "Single-leg glute bridge", "Legs"),
        b("lying-leg-raise", "Lying leg raise", "Core"),
        b("bicycle-crunch", "Bicycle crunch", "Core", perRep = 2.0),
        b("russian-twist", "Russian twist", "Core", perRep = 2.0),
        b("v-up", "V-up", "Core"),
        b("dead-bug", "Dead bug", "Core"),
        b("bird-dog", "Bird dog", "Core"),
        b("shoulder-tap", "Plank shoulder tap", "Core", perRep = 2.0),
        t("plank", "Plank", "Core"),
        t("side-plank", "Side plank", "Core"),
        t("wall-sit", "Wall sit", "Legs"),
        t("dead-hang", "Dead hang", "Back"),
        t("hollow-hold", "Hollow hold", "Core"),
        t("l-sit", "L-sit", "Core"),
        t("flutter-kicks", "Flutter kicks", "Core"),
        q("jumping-jacks", "Jumping jacks", Track.REPS),
        q("mountain-climbers", "Mountain climbers", Track.REPS),
        q("high-knees", "High knees", Track.SECONDS),
        q("butt-kicks", "Butt kicks", Track.SECONDS),
        q("skater-jumps", "Skater jumps", Track.REPS, perRep = 1.5),
        q("tuck-jumps", "Tuck jumps", Track.REPS, perRep = 2.0),
        q("bear-crawl", "Bear crawl", Track.SECONDS),
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

    /** How long one rep takes, for the calories of sets counted in reps (0.20.0). */
    const val SECONDS_PER_REP = 3.0

    /** What [e] logs unless the user changes it: its built-in choice, or the usual for its tag. */
    fun usualTrack(e: Exercise): List<Track> = (if (e.key.startsWith("b:")) builtIn(e.key)?.track else null) ?: Track.usual(e.kind)

    /** [e] with what the user chose to log for it, if they changed it. */
    fun withTrack(e: Exercise, chosen: Map<String, List<Track>>): Exercise = chosen[e.key]?.let { e.copy(track = it) } ?: e
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
    /** A warm-up set (0.9.0): in neither personal bests nor the total lifted. */
    val warmUp: Boolean = false,
    /** The rest after this set, if it has its own (0.20.0); otherwise the exercise's. */
    val restSeconds: Int? = null,
)

/** An exercise as done in one workout: its sets in order. */
@Serializable
data class WorkoutExercise(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    /** Exercises with the same number are a superset, done in turn (0.9.0). */
    val superset: Int? = null,
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

/**
 * One exercise of a saved workout. [plan] is each set as planned (0.20.0): its numbers, used when
 * the exercise hasn't been done before, and its rest and warm-up mark, used every time.
 */
@Serializable
data class SavedWorkoutItem(
    val exercise: Exercise,
    val sets: Int,
    val superset: Int? = null,
    val plan: List<WorkoutSet> = emptyList(),
) {
    /** The sets as planned, as many as [sets]. */
    fun planned(): List<WorkoutSet> = (0 until maxOf(sets, plan.size, 1)).map { plan.getOrNull(it) ?: plan.lastOrNull()?.copy(warmUp = false) ?: WorkoutSet() }
}

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
            val hasPlan = item.plan.isNotEmpty()
            val sets = item.planned().mapIndexed { i, p ->
                val from = last?.getOrNull(i) ?: last?.lastOrNull()
                if (from == null) p.copy(done = false, doneAt = null)
                else from.copy(
                    done = false, doneAt = null,
                    weightKg = from.weightKg ?: p.weightKg, reps = from.reps ?: p.reps,
                    seconds = from.seconds ?: p.seconds, distanceKm = from.distanceKm ?: p.distanceKm,
                    warmUp = if (hasPlan) p.warmUp else from.warmUp,
                    restSeconds = if (hasPlan) p.restSeconds else from.restSeconds,
                )
            }
            WorkoutExercise(item.exercise, sets, item.superset)
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
            items = w.exercises.map { we ->
                SavedWorkoutItem(we.exercise, we.sets.size.coerceAtLeast(1), we.superset, we.sets.map { it.copy(done = false, doneAt = null) })
            },
            createdAt = now,
        )
    }

    /** "80 kg × 5", "20 reps", "1:15", "5 km in 28:30": whatever the set has. */
    fun describe(set: WorkoutSet, u: UnitSettings): String {
        val bits = mutableListOf<String>()
        val kg = set.weightKg?.takeIf { it > 0 }?.let { Units.weight(it, u.weight) }
        val reps = set.reps?.takeIf { it > 0 }
        when {
            kg != null && reps != null -> bits += "$kg × $reps"
            kg != null -> bits += kg
            reps != null -> bits += "$reps ${if (reps == 1) "rep" else "reps"}"
        }
        val time = set.seconds?.takeIf { it > 0 }?.let { clock(it) }
        val km = set.distanceKm?.takeIf { it > 0 }?.let { Units.distance(it, u.distance) }
        when {
            km != null -> bits += km + (time?.let { " in $it" } ?: "")
            time != null -> bits += time
        }
        return bits.joinToString(", ").ifEmpty { "—" }
    }

    private val planJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val planSerializer = kotlinx.serialization.builtins.ListSerializer(WorkoutSet.serializer())

    /** A saved workout's planned sets as text for the database (0.20.0); null when there are none. */
    fun encodePlan(plan: List<WorkoutSet>): String? = if (plan.isEmpty()) null else planJson.encodeToString(planSerializer, plan)

    fun decodePlan(text: String?): List<WorkoutSet> =
        text?.let { runCatching { planJson.decodeFromString(planSerializer, it) }.getOrNull() }.orEmpty()

    /** 75 → "1:15", 3725 → "1:02:05". */
    fun clock(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
    }
}

/**
 * Calories burned, from standard activity values: MET × body weight (kg) × hours. Two estimates,
 * and the larger counts (0.20.0):
 * - by the workout's length: cardio timed in its sets uses its own time (and its speed when a
 *   distance is given); the rest of the workout's length counts at the hardest other exercise;
 * - by the sets themselves: each ticked set's own time, or its reps × the seconds a rep takes,
 *   so 120 push-ups count as the work they are even when typed in after a few seconds.
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

    /** How long a ticked set took: its own time, or its reps at the exercise's pace. */
    fun activeSeconds(e: Exercise, s: WorkoutSet): Double =
        s.seconds?.takeIf { it > 0 }?.toDouble() ?: s.reps?.takeIf { it > 0 }?.let { it * e.secondsPerRep } ?: 0.0

    private fun timedCardio(e: Exercise, s: WorkoutSet) = e.kind == ExerciseKind.CARDIO && (s.seconds ?: 0) > 0

    /** kcal for [w] at [weightKg]. [half] counts only half, because estimates tend to run high. */
    fun workout(w: Workout, weightKg: Double, now: Long = w.endedAt ?: w.startedAt, half: Boolean = false): Double {
        val kcal = maxOf(byLength(w, weightKg, now), bySets(w, weightKg))
        return if (half) kcal / 2 else kcal
    }

    /** By the workout's length. */
    fun byLength(w: Workout, weightKg: Double, now: Long = w.endedAt ?: w.startedAt): Double {
        var kcal = 0.0
        var cardioMinutes = 0.0
        for (we in w.exercises) for (s in we.sets.filter { it.done && timedCardio(we.exercise, it) }) {
            val sec = s.seconds ?: 0
            kcal += cardioMet(we.exercise, s) * weightKg * sec / 3600.0
            cardioMinutes += sec / 60.0
        }
        val rest = w.exercises.filter { we -> we.sets.any { it.done && !timedCardio(we.exercise, it) } }
        if (rest.isNotEmpty()) {
            // The workout's length, less any time spent on timed cardio in it, at the hardest of the other exercises.
            val minutes = (w.minutes(now) - cardioMinutes).coerceAtLeast(0.0)
            kcal += rest.maxOf { it.exercise.met } * weightKg * minutes / 60.0
        }
        return kcal
    }

    /** By the ticked sets themselves. */
    fun bySets(w: Workout, weightKg: Double): Double = w.exercises.sumOf { we ->
        we.sets.filter { it.done }.sumOf { s ->
            val met = if (we.exercise.kind == ExerciseKind.CARDIO) cardioMet(we.exercise, s) else we.exercise.met
            met * weightKg * activeSeconds(we.exercise, s) / 3600.0
        }
    }

    /** A rough estimate, so to the nearest whole kcal. */
    fun rounded(kcal: Double): Double = kcal.roundToInt().toDouble()
}
