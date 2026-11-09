package io.github.randomtools3377.fitness.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Alcohol, caffeine, and vitamins and minerals (0.11.0), kept on a food or entry as a map from
 * [key] to an amount in [unit] per 100 g (or for the amount eaten). [reference] is the EU label
 * reference value (Regulation 1169/2011, Annex XIII), for showing a share of it; it is not a
 * personal need.
 */
enum class Micro(val key: String, val title: String, val unit: String, val reference: Double?) {
    ALCOHOL("alcohol", "Alcohol", "g", null),
    CAFFEINE("caffeine", "Caffeine", "mg", null),
    IRON("iron", "Iron", "mg", 14.0),
    CALCIUM("calcium", "Calcium", "mg", 800.0),
    VITAMIN_D("vitD", "Vitamin D", "µg", 5.0),
    VITAMIN_C("vitC", "Vitamin C", "mg", 80.0),
    POTASSIUM("potassium", "Potassium", "mg", 2000.0),
    MAGNESIUM("magnesium", "Magnesium", "mg", 375.0),
    VITAMIN_B12("b12", "Vitamin B12", "µg", 2.5),
    FOLATE("folate", "Folate", "µg", 200.0),
    ZINC("zinc", "Zinc", "mg", 10.0),
    VITAMIN_A("vitA", "Vitamin A", "µg", 800.0),
    ;

    companion object {
        /** The vitamins and minerals, in the order shown. */
        val VITAMINS_AND_MINERALS = entries.filter { it.reference != null }

        /** The order of the extra columns in foods.tsv, after salt. */
        val COLUMNS = entries.toList()

        fun of(key: String): Micro? = entries.firstOrNull { it.key == key }
    }
}

object Micros {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val serializer = kotlinx.serialization.builtins.MapSerializer(
        kotlinx.serialization.builtins.serializer<String>(), kotlinx.serialization.builtins.serializer<Double>(),
    )

    /** For the database: the map as JSON, or null when empty. */
    fun encode(m: Map<String, Double>): String? = if (m.isEmpty()) null else json.encodeToString(serializer, m)

    fun decode(text: String?): Map<String, Double> =
        if (text.isNullOrBlank()) emptyMap() else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyMap())

    /** Adds two maps; a key only one has is kept as it is. */
    fun add(a: Map<String, Double>, b: Map<String, Double>): Map<String, Double> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val out = a.toMutableMap()
        for ((k, v) in b) out[k] = (out[k] ?: 0.0) + v
        return out
    }

    fun times(a: Map<String, Double>, factor: Double): Map<String, Double> = if (a.isEmpty()) a else a.mapValues { it.value * factor }

    /** Grams of alcohol as standard drinks, to one decimal. */
    fun drinks(alcoholGrams: Double, gramsPerDrink: Double): Double = (alcoholGrams / gramsPerDrink.coerceAtLeast(1.0) * 10).roundToInt() / 10.0

    /** The share of the EU reference value, in whole percent, or null if the micro has none. */
    fun percent(m: Micro, amount: Double): Int? = m.reference?.let { (amount / it * 100).roundToInt() }

    /** Reads the extra columns of foods.tsv from [start], in [Micro.COLUMNS] order. Empty is not known. */
    fun fromColumns(f: List<String>, start: Int): Map<String, Double> {
        if (f.size <= start) return emptyMap()
        val out = HashMap<String, Double>()
        Micro.COLUMNS.forEachIndexed { i, m ->
            f.getOrNull(start + i)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.let { out[m.key] = it }
        }
        return out
    }
}

/** A standard drink in grams of alcohol, by region: 14 g in the US, 8 g (a unit) in the UK, else 10 g. */
fun standardDrinkGrams(region: String): Double = when (region.uppercase()) {
    "US" -> 14.0
    "GB" -> 8.0
    else -> 10.0
}

/** Calories by weekday, and more on training days (0.11.0). Both off to start. */
@Serializable
data class DayTargets(
    /** 1 = Monday … 7 = Sunday, to the day's kcal; a day not here uses the usual target. */
    val weekdayKcal: Map<Int, Int> = emptyMap(),
    /** Extra kcal on a training day: one with a finished workout, or a programme day. */
    val trainingExtraKcal: Int? = null,
)

/** A planned stretch at maintenance calories (0.11.0). */
@Serializable
data class DietBreak(val start: LocalDate, val weeks: Int) {
    val end: LocalDate get() = start.plus(DatePeriod(days = weeks * 7 - 1))
    fun covers(day: LocalDate): Boolean = day >= start && day <= end
}

object DayTarget {
    /**
     * The calorie target for [day]: during a diet break, [maintenanceKcal]; otherwise the
     * weekday's own target or the usual one, plus the training-day extra on a training day.
     */
    fun forDay(
        base: Int?,
        day: LocalDate,
        days: DayTargets,
        dietBreak: DietBreak?,
        maintenanceKcal: Int?,
        trainingDay: Boolean,
    ): Int? {
        if (dietBreak != null && dietBreak.covers(day) && maintenanceKcal != null) return maintenanceKcal
        val usual = days.weekdayKcal[day.dayOfWeek.ordinal + 1] ?: base ?: return null
        return usual + if (trainingDay) days.trainingExtraKcal ?: 0 else 0
    }

    /** Why the day's target is what it is, in plain words, or null when it's the usual one. */
    fun why(base: Int?, day: LocalDate, days: DayTargets, dietBreak: DietBreak?, maintenanceKcal: Int?, trainingDay: Boolean): String? = when {
        dietBreak != null && dietBreak.covers(day) && maintenanceKcal != null -> "Diet break until ${dietBreak.end}: maintenance calories"
        days.weekdayKcal.containsKey(day.dayOfWeek.ordinal + 1) && trainingDay && (days.trainingExtraKcal ?: 0) != 0 -> "This weekday's target, plus the training-day extra"
        days.weekdayKcal.containsKey(day.dayOfWeek.ordinal + 1) -> "This weekday's own target"
        trainingDay && (days.trainingExtraKcal ?: 0) != 0 -> "A training day: ${days.trainingExtraKcal} kcal more"
        else -> null
    }

    /** A training day: one with a finished workout, or a day the weekly programme has a workout. */
    fun isTrainingDay(s: AppSettings, workoutDays: Set<LocalDate>, day: LocalDate): Boolean =
        day in workoutDays || s.programme.containsKey(day.dayOfWeek.ordinal + 1)

    /** Each day's target from the settings, the latest weight and the days with a workout. */
    fun resolver(s: AppSettings, weightKg: Double?, workoutDays: Set<LocalDate>, year: Int): (LocalDate) -> Int? {
        val maintenance = s.profile?.let { Targets.calculate(it, year, weightKg ?: it.weightKg).maintenanceKcal }
        return { d -> forDay(s.targetKcal, d, s.dayTargets, s.dietBreak, maintenance, isTrainingDay(s, workoutDays, d)) }
    }

    fun whyFor(s: AppSettings, weightKg: Double?, workoutDays: Set<LocalDate>, year: Int, day: LocalDate): String? {
        val maintenance = s.profile?.let { Targets.calculate(it, year, weightKg ?: it.weightKg).maintenanceKcal }
        return why(s.targetKcal, day, s.dayTargets, s.dietBreak, maintenance, isTrainingDay(s, workoutDays, day))
    }
}
