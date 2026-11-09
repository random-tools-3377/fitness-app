package io.github.randomtools3377.fitness.core

import kotlinx.serialization.Serializable

/** An interval workout (0.13.0): rounds of work then rest, in seconds. */
@Serializable
data class IntervalPlan(val workSeconds: Int, val restSeconds: Int, val rounds: Int) {
    val totalSeconds: Int get() = rounds * workSeconds + (rounds - 1).coerceAtLeast(0) * restSeconds

    fun problem(): String? = when {
        workSeconds !in 5..3600 -> "Work is between 5 seconds and an hour."
        restSeconds !in 0..3600 -> "Rest is up to an hour."
        rounds !in 1..100 -> "Between 1 and 100 rounds."
        else -> null
    }

    companion object {
        val TABATA = IntervalPlan(20, 10, 8)
        val HIIT = IntervalPlan(40, 20, 10)
    }
}

/** Where an interval workout is: which round, work or rest, and the seconds left of it. */
data class IntervalPhase(val round: Int, val work: Boolean, val secondsLeft: Int, val done: Boolean = false)

object Intervals {
    /** The phase [elapsedSeconds] after the start. The rest after the last round is left out. */
    fun phaseAt(plan: IntervalPlan, elapsedSeconds: Int): IntervalPhase {
        if (elapsedSeconds >= plan.totalSeconds) return IntervalPhase(plan.rounds, work = false, secondsLeft = 0, done = true)
        val cycle = plan.workSeconds + plan.restSeconds
        val round = elapsedSeconds / cycle + 1
        val into = elapsedSeconds % cycle
        return if (into < plan.workSeconds) {
            IntervalPhase(round, work = true, secondsLeft = plan.workSeconds - into)
        } else {
            IntervalPhase(round, work = false, secondsLeft = cycle - into)
        }
    }

    /** Minutes of work, for calories burned: the work time only. */
    fun workMinutes(plan: IntervalPlan): Double = plan.rounds * plan.workSeconds / 60.0
}

/** The weights for 1 to 12 reps from an estimated one-rep max (0.13.0), by the Epley formula the bests use. */
object RepTable {
    fun of(oneRepMaxKg: Double): List<Pair<Int, Double>> = (1..12).map { reps ->
        reps to if (reps == 1) oneRepMaxKg else oneRepMaxKg / (1 + reps / 30.0)
    }
}
