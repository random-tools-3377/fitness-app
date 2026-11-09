package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable

/** A weigh-in, always stored in kilograms. */
@Serializable
data class WeighIn(
    val id: Long = 0,
    val day: LocalDate,
    val kg: Double,
    /** Epoch milliseconds. */
    val at: Long = 0,
)

/** Water drunk, in millilitres. */
@Serializable
data class WaterEntry(
    val id: Long = 0,
    val day: LocalDate,
    val ml: Int,
    val at: Long = 0,
)

object Weight {
    const val AVERAGE_DAYS = 7

    /** The latest weigh-in: the one used for the calorie target. */
    fun latest(weighIns: List<WeighIn>): WeighIn? =
        weighIns.maxWithOrNull(compareBy<WeighIn> { it.day }.thenBy { it.at })

    /**
     * One weight per day that has a weigh-in: the last one of the day, when there are several.
     * Sorted by day.
     */
    fun daily(weighIns: List<WeighIn>): List<Pair<LocalDate, Double>> =
        weighIns.groupBy { it.day }
            .map { (day, list) -> day to list.maxBy { it.at }.kg }
            .sortedBy { it.first }

    /**
     * The 7-day average for each day that has a weigh-in: the mean of the daily weights from
     * that day and the 6 days before it. Day-to-day weight jumps around; this line shows the trend.
     */
    fun sevenDayAverage(weighIns: List<WeighIn>): List<Pair<LocalDate, Double>> {
        val days = daily(weighIns)
        return days.map { (day, _) ->
            val from = day.minus(DatePeriod(days = AVERAGE_DAYS - 1))
            val window = days.filter { it.first >= from && it.first <= day }.map { it.second }
            day to window.average()
        }
    }
}

object Water {
    fun total(entries: List<WaterEntry>, day: LocalDate): Int = entries.filter { it.day == day }.sumOf { it.ml }
}
