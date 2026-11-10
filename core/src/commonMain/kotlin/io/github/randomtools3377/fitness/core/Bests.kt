package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** The kinds of personal best. */
enum class BestKind(val title: String) {
    HEAVIEST("Heaviest weight"),
    REPS_AT_WEIGHT("Most reps at a weight"),
    ONE_REP_MAX("Estimated one-rep max"),
    MOST_REPS("Most reps in a set"),
    LONGEST_TIME("Longest time"),
    LONGEST_DISTANCE("Longest distance"),
    FASTEST("Fastest"),
}

/**
 * A personal best: [value] is kg (heaviest, one-rep max), reps, seconds, km or km/h (fastest).
 * For [BestKind.REPS_AT_WEIGHT], [atKg] is the weight.
 */
data class Best(
    val exercise: Exercise,
    val kind: BestKind,
    val value: Double,
    val atKg: Double? = null,
    val day: LocalDate,
) {
    fun describe(u: UnitSettings): String = when (kind) {
        BestKind.HEAVIEST, BestKind.ONE_REP_MAX -> Units.weight(value, u.weight)
        BestKind.REPS_AT_WEIGHT -> "${value.roundToInt()} reps at ${Units.weight(atKg ?: 0.0, u.weight)}"
        BestKind.MOST_REPS -> "${value.roundToInt()} reps"
        BestKind.LONGEST_TIME -> Workouts.clock(value.roundToInt())
        BestKind.LONGEST_DISTANCE -> Units.distance(value, u.distance)
        BestKind.FASTEST -> pace(value, u)
    }

    companion object {
        /** A speed in km/h as a pace: "5:42 per km", "9:10 per mile". */
        fun pace(kmh: Double, u: UnitSettings): String {
            if (kmh <= 0) return "—"
            val perUnit = if (u.distance == DistanceUnit.KM) 1.0 else Units.KM_PER_MILE
            val seconds = (3600.0 * perUnit / kmh).roundToInt()
            return "${Workouts.clock(seconds)} per ${if (u.distance == DistanceUnit.KM) "km" else "mile"}"
        }
    }
}

/** A best just beaten: what it was and what it is now. */
data class BestBeaten(val kind: BestKind, val old: Best, val new: Best)

object Bests {
    /** Epley's estimate of the most that could be lifted once: weight × (1 + reps / 30), for 1 to 12 reps. */
    fun oneRepMax(kg: Double, reps: Int): Double? = when {
        reps < 1 || reps > 12 || kg <= 0 -> null
        reps == 1 -> kg
        else -> kg * (1 + reps / 30.0)
    }

    /** Every best a single set reaches, before comparing with the history. */
    fun of(e: Exercise, s: WorkoutSet, day: LocalDate): List<Best> {
        if (!s.done || s.warmUp) return emptyList()
        val out = mutableListOf<Best>()
        // From what the set has (0.20.0), whatever the exercise's tag.
        val kg = s.weightKg?.takeIf { it > 0 }
        val reps = s.reps?.takeIf { it > 0 }
        if (kg != null && reps != null) {
            out += Best(e, BestKind.HEAVIEST, kg, day = day)
            out += Best(e, BestKind.REPS_AT_WEIGHT, reps.toDouble(), atKg = kg, day = day)
            oneRepMax(kg, reps)?.let { out += Best(e, BestKind.ONE_REP_MAX, it, day = day) }
        } else if (reps != null && Track.WEIGHT !in e.tracks) {
            out += Best(e, BestKind.MOST_REPS, reps.toDouble(), day = day)
        }
        val sec = s.seconds ?: 0
        if (sec > 0) out += Best(e, BestKind.LONGEST_TIME, sec.toDouble(), day = day)
        val km = s.distanceKm
        if (km != null && km > 0) {
            out += Best(e, BestKind.LONGEST_DISTANCE, km, day = day)
            if (sec > 0) out += Best(e, BestKind.FASTEST, km / (sec / 3600.0), day = day)
        }
        return out
    }

    private fun sameSlot(a: Best, b: Best) = a.kind == b.kind && (a.kind != BestKind.REPS_AT_WEIGHT || a.atKg == b.atKg)

    /** The current bests for each exercise in [history], best first within each kind. */
    fun all(history: List<Workout>): List<Best> {
        val out = mutableListOf<Best>()
        for (w in history.sortedBy { it.startedAt }) for (we in w.exercises) for (s in we.sets) {
            for (b in of(we.exercise, s, w.day)) {
                val i = out.indexOfFirst { it.exercise.key == b.exercise.key && sameSlot(it, b) }
                if (i < 0) out += b else if (b.value > out[i].value) out[i] = b
            }
        }
        return out
    }

    /**
     * The bests a set just ticked beats, compared with everything before it. A first time is
     * not counted as beating anything, so the very first workout isn't one celebration after
     * another. Reps at a weight are only celebrated at the heaviest weight lifted for reps.
     */
    fun beaten(before: List<Workout>, e: Exercise, s: WorkoutSet, day: LocalDate): List<BestBeaten> {
        val old = all(before).filter { it.exercise.key == e.key }
        return of(e, s, day).mapNotNull { b ->
            val prev = old.firstOrNull { sameSlot(it, b) } ?: return@mapNotNull null
            if (b.value > prev.value + 1e-9) BestBeaten(b.kind, prev, b) else null
        }.filter { it.kind != BestKind.REPS_AT_WEIGHT || old.none { o -> o.kind == BestKind.HEAVIEST && o.value > (it.new.atKg ?: 0.0) } }
    }
}
