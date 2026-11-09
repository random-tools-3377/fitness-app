package io.github.randomtools3377.fitness.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlin.math.log10
import kotlin.math.roundToInt

/** Body fat estimated from tape measurements by the US Navy formula (0.12.0). Always an estimate. */
object BodyFat {
    /**
     * Percent body fat from height, waist and neck (and hips for women), all in cm; null when a
     * measurement is missing or the numbers can't be right (the waist must be more than the neck).
     */
    fun navy(sex: Sex, heightCm: Double, waistCm: Double?, neckCm: Double?, hipsCm: Double?): Double? {
        if (waistCm == null || neckCm == null || heightCm <= 0) return null
        val pct = when (sex) {
            Sex.MALE -> {
                if (waistCm - neckCm <= 0) return null
                495.0 / (1.0324 - 0.19077 * log10(waistCm - neckCm) + 0.15456 * log10(heightCm)) - 450.0
            }
            Sex.FEMALE -> {
                if (hipsCm == null || waistCm + hipsCm - neckCm <= 0) return null
                495.0 / (1.29579 - 0.35004 * log10(waistCm + hipsCm - neckCm) + 0.22100 * log10(heightCm)) - 450.0
            }
        }
        if (pct < 2 || pct > 70) return null
        return (pct * 10).roundToInt() / 10.0
    }

    /** From the latest measurements and the profile. */
    fun latest(profile: Profile?, measurements: List<Measurement>): Double? {
        val p = profile ?: return null
        val l = Measurements.latest(measurements)
        return navy(p.sex, p.heightCm, l[MeasureKind.WAIST]?.value, l[MeasureKind.NECK]?.value, l[MeasureKind.HIPS]?.value)
    }
}

/** What a daily tick-list item is (0.12.0): one of the user's habits, or a supplement or medicine. */
@Serializable
enum class HabitKind { HABIT, SUPPLEMENT }

/** A period, from its first day to its last (null while it is still going) (0.12.0). */
@Serializable
data class Period(val id: Long = 0, val start: LocalDate, val end: LocalDate? = null) {
    fun covers(day: LocalDate, today: LocalDate): Boolean = day >= start && day <= (end ?: today)
}

/** What the app can say about the cycle from the periods logged; estimates only. */
data class CycleSummary(
    /** The average days from one period's start to the next, from up to the last 6 cycles. */
    val averageLength: Int?,
    /** The next period's estimated start, or null with fewer than two periods logged. */
    val nextStart: LocalDate?,
    /** Day of the cycle today, counting the latest period's first day as day 1. */
    val dayOfCycle: Int?,
)

object Cycle {
    const val CYCLES_AVERAGED = 6

    fun summary(periods: List<Period>, today: LocalDate): CycleSummary {
        val starts = periods.map { it.start }.distinct().sorted()
        val gaps = starts.zipWithNext { a, b -> a.daysUntil(b) }.filter { it in 15..60 }.takeLast(CYCLES_AVERAGED)
        val avg = if (gaps.isEmpty()) null else (gaps.sum().toDouble() / gaps.size).roundToInt()
        val last = starts.lastOrNull()
        val day = last?.let { it.daysUntil(today) + 1 }?.takeIf { it >= 1 }
        val next = if (avg != null && last != null) last.plus(DatePeriod(days = avg)) else null
        return CycleSummary(avg, next, day)
    }

    /** The days to mark on charts: every day of each period. */
    fun periodDays(periods: List<Period>, today: LocalDate): Set<LocalDate> = buildSet {
        for (p in periods) {
            var d = p.start
            val end = p.end ?: today
            var guard = 0
            while (d <= end && guard++ < 15) {
                add(d)
                d = d.plus(DatePeriod(days = 1))
            }
        }
    }

    fun problem(start: LocalDate, end: LocalDate?, today: LocalDate): String? = when {
        start > today -> "A period can't start in the future."
        end != null && end < start -> "The last day can't be before the first."
        end != null && start.daysUntil(end) > 14 -> "That's longer than two weeks. Check the dates."
        else -> null
    }
}
