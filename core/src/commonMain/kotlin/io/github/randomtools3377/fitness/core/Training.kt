package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.round

/** The bar and the plates in the gym, in kg and in lb, both changeable (0.9.0). */
@Serializable
data class PlateSettings(
    val barKg: Double = 20.0,
    val platesKg: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
    val barLb: Double = 45.0,
    val platesLb: List<Double> = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5),
)

/** What to load on each side of the bar. [left] is what can't be made with the plates there are. */
data class Loading(val perSide: List<Double>, val left: Double) {
    val exact: Boolean get() = abs(left) < 1e-6
}

object Plates {
    /**
     * The plates for each side of the bar to make [total], heaviest first, in the same unit as
     * [bar] and [plates] (kg or lb). Null if [total] is less than the bar.
     */
    fun load(total: Double, bar: Double, plates: List<Double>): Loading? {
        if (total < bar - 1e-6) return null
        var side = (total - bar) / 2.0
        val out = mutableListOf<Double>()
        for (p in plates.filter { it > 0 }.sortedDescending()) {
            while (side >= p - 1e-6) {
                out += p
                side -= p
            }
        }
        return Loading(out, round(side * 2 * 1000) / 1000)
    }

    /** "20 + 10 + 2.5" for one side, or "nothing: just the bar". */
    fun describe(l: Loading): String = if (l.perSide.isEmpty()) "Nothing: just the bar" else l.perSide.joinToString(" + ") { formatAmount(it) }

    fun problem(bar: Double?, plates: List<Double>): String? = when {
        bar == null || bar < 0 -> "Type the bar's weight."
        plates.isEmpty() || plates.any { it <= 0 } -> "Type the plate weights, separated by commas."
        else -> null
    }
}

/** A suggestion for the next session of an exercise, from the last one (0.9.0). */
data class Hint(val text: String, val weightKg: Double? = null, val reps: Int? = null)

object Progression {
    const val STEP_KG = 2.5
    const val STEP_LB = 5.0

    /**
     * When every working set last time was ticked with its reps, a little more: +2.5 kg (or
     * +5 lb) for weights, one more rep for bodyweight. Null when there's nothing to suggest.
     */
    fun hint(e: Exercise, last: List<WorkoutSet>?, unit: WeightUnit): Hint? {
        val working = last?.filter { !it.warmUp } ?: return null
        if (working.isEmpty() || working.any { !it.done }) return null
        return when (e.kind) {
            ExerciseKind.WEIGHTS -> {
                val top = working.mapNotNull { it.weightKg }.maxOrNull() ?: return null
                if (working.any { it.reps == null || it.reps <= 0 }) return null
                val stepKg = if (unit == WeightUnit.KG) STEP_KG else STEP_LB * Units.KG_PER_LB
                val next = top + stepKg
                val reps = working.map { it.reps!! }
                Hint(
                    "Last time all ${working.size} sets were done (${reps.joinToString("/")} reps at ${Units.weight(top, unit)}). " +
                        "Try ${Units.weight(next, unit)}.",
                    weightKg = next,
                )
            }
            ExerciseKind.BODYWEIGHT -> {
                val reps = working.mapNotNull { it.reps }
                if (reps.size != working.size) return null
                val most = reps.max()
                Hint("Last time all ${working.size} sets were done, up to $most reps. Try ${most + 1}.", reps = most + 1)
            }
            else -> null
        }
    }

    /** The exercise's working sets with the hint applied; warm-ups stay as they are. */
    fun apply(we: WorkoutExercise, h: Hint): WorkoutExercise = we.copy(
        sets = we.sets.map { s ->
            if (s.warmUp || s.done) s else s.copy(weightKg = h.weightKg ?: s.weightKg, reps = h.reps ?: s.reps)
        },
    )
}

object Supersets {
    /** Joins exercise [index] with the next one into a superset, or adds the next one to its superset. */
    fun joinWithNext(w: Workout, index: Int): Workout {
        if (index !in 0 until w.exercises.lastIndex) return w
        val group = w.exercises[index].superset ?: w.exercises[index + 1].superset ?: ((w.exercises.mapNotNull { it.superset }.maxOrNull() ?: 0) + 1)
        return w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i == index || i == index + 1) e.copy(superset = group) else e })
    }

    /** Takes exercise [index] out of its superset; a superset left with one exercise ends. */
    fun leave(w: Workout, index: Int): Workout {
        val group = w.exercises.getOrNull(index)?.superset ?: return w
        var list = w.exercises.mapIndexed { i, e -> if (i == index) e.copy(superset = null) else e }
        if (list.count { it.superset == group } < 2) list = list.map { if (it.superset == group) it.copy(superset = null) else it }
        return w.copy(exercises = list)
    }

    /**
     * Whether ticking a set of exercise [index] should start the rest timer: in a superset, only
     * after the last exercise of the round, so the others follow straight on.
     */
    fun restAfter(w: Workout, index: Int): Boolean {
        val group = w.exercises.getOrNull(index)?.superset ?: return true
        val last = w.exercises.indices.last { w.exercises[it].superset == group }
        return index == last
    }
}

/** The weekly programme (0.9.0): a saved workout for some days of the week, 1 = Monday. */
object Programme {
    fun today(programme: Map<Int, Long>, saved: List<SavedWorkout>, day: LocalDate): SavedWorkout? {
        val id = programme[day.dayOfWeek.ordinal + 1] ?: return null
        return saved.firstOrNull { it.id == id }
    }

    /** "Mon: Push · Wed: Pull · Fri: Legs". */
    fun describe(programme: Map<Int, Long>, saved: List<SavedWorkout>): String {
        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return (1..7).mapNotNull { d -> programme[d]?.let { id -> saved.firstOrNull { it.id == id }?.let { "${names[d - 1]}: ${it.name}" } } }
            .joinToString(" · ")
    }
}
