package io.github.randomtools3377.fitness.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable
enum class Sex(val title: String) {
    FEMALE("Female"),
    MALE("Male"),
}

/** How active the user is, from "mostly sitting" to "very active", with its activity factor. */
@Serializable
enum class Activity(val title: String, val detail: String, val factor: Double) {
    SEDENTARY("Mostly sitting", "Desk job, little exercise", 1.2),
    LIGHT("Lightly active", "On your feet some of the day, or exercise 1–3 days a week", 1.375),
    MODERATE("Moderately active", "Exercise 3–5 days a week", 1.55),
    ACTIVE("Active", "Hard exercise 6–7 days a week, or a physical job", 1.725),
    VERY_ACTIVE("Very active", "Hard exercise every day and a physical job", 1.9),
}

@Serializable
enum class WeightGoal(val title: String) {
    LOSE("Lose weight"),
    KEEP("Keep my weight"),
    GAIN("Gain weight"),
}

/** The body details the calorie target is worked out from. */
@Serializable
data class Profile(
    val sex: Sex,
    /** Year of birth, so the age used stays right as the years pass. */
    val birthYear: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activity: Activity,
    val goal: WeightGoal = WeightGoal.KEEP,
    /** Kilograms a week to lose or gain: 0.25, 0.5 or 0.75. Ignored when keeping. */
    val kgPerWeek: Double = 0.5,
    /** The weight aimed for, if any, for the stats (0.4.0). */
    val goalWeightKg: Double? = null,
)

/** The worked-out daily calorie target and how it was reached, for showing the sums. */
data class TargetResult(
    /** Resting burn, by the Mifflin–St Jeor formula. */
    val restingKcal: Int,
    /** Resting burn times the activity factor: what keeps the weight the same. */
    val maintenanceKcal: Int,
    /** Taken off (losing) or added (gaining) each day. */
    val adjustmentKcal: Int,
    /** Maintenance plus the adjustment, before the minimum is applied. */
    val sumsKcal: Int,
    /** The suggested target. */
    val targetKcal: Int,
    /** True when the sums came out below the lowest target the app suggests, and it was raised. */
    val raisedToMinimum: Boolean,
    val minimumKcal: Int,
) {
    /** Why the target was raised, in plain words, or null when it wasn't. */
    val minimumReason: String?
        get() = if (!raisedToMinimum) null else
            "The sums came to $sumsKcal kcal, " +
                "but the app never suggests less than $minimumKcal kcal a day, because eating less than that " +
                "makes it hard to get enough nutrients. Losing more slowly keeps you above it."
}

object Targets {
    val RATES = listOf(0.25, 0.5, 0.75)

    /** About 550 kcal a day for each 0.5 kg a week. */
    const val KCAL_PER_KG_PER_WEEK = 1100.0

    const val MIN_FEMALE = 1200
    const val MIN_MALE = 1500

    /** Change in the worked-out target that is big enough to offer the new number. */
    const val OFFER_THRESHOLD = 50

    /** Mifflin–St Jeor: 10 × kg + 6.25 × cm − 5 × age, + 5 for men, − 161 for women. */
    fun restingKcal(sex: Sex, age: Int, heightCm: Double, weightKg: Double): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return base + if (sex == Sex.MALE) 5.0 else -161.0
    }

    fun minimum(sex: Sex): Int = if (sex == Sex.MALE) MIN_MALE else MIN_FEMALE

    /** Age in whole years during [year], for someone born in [birthYear]. */
    fun age(birthYear: Int, year: Int): Int = year - birthYear

    /** The target for [p], in [year], at the weight [weightKg] (the latest weigh-in). */
    fun calculate(p: Profile, year: Int, weightKg: Double = p.weightKg): TargetResult {
        val resting = restingKcal(p.sex, age(p.birthYear, year), p.heightCm, weightKg)
        val maintenance = resting * p.activity.factor
        val adjustment = when (p.goal) {
            WeightGoal.KEEP -> 0.0
            WeightGoal.LOSE -> -p.kgPerWeek * KCAL_PER_KG_PER_WEEK
            WeightGoal.GAIN -> p.kgPerWeek * KCAL_PER_KG_PER_WEEK
        }
        val sums = roundTo10(maintenance + adjustment)
        val min = minimum(p.sex)
        return TargetResult(
            restingKcal = resting.roundToInt(),
            maintenanceKcal = roundTo10(maintenance),
            adjustmentKcal = adjustment.roundToInt(),
            sumsKcal = sums,
            targetKcal = maxOf(sums, min),
            raisedToMinimum = sums < min,
            minimumKcal = min,
        )
    }

    /**
     * Whether a new worked-out target is far enough from the last one ([lastWorkedOut]) to offer
     * it to the user. The app never changes the target by itself.
     */
    fun shouldOffer(lastWorkedOut: Int?, newWorkedOut: Int): Boolean =
        lastWorkedOut != null && abs(newWorkedOut - lastWorkedOut) >= OFFER_THRESHOLD

    fun problems(age: Int?, heightCm: Double?, weightKg: Double?): List<String> {
        val out = mutableListOf<String>()
        if (age == null || age < 13 || age > 110) out += "Type an age between 13 and 110."
        if (heightCm == null || heightCm < 100 || heightCm > 250) out += "Type a height between 100 and 250 cm (3 ft 3 in to 8 ft 2 in)."
        if (weightKg == null || weightKg < 30 || weightKg > 350) out += "Type a weight between 30 and 350 kg (66 to 770 lb)."
        return out
    }

    private fun roundTo10(x: Double): Int = (x / 10.0).roundToInt() * 10
}

/** How the protein, carbs and fat targets are set: as shares of the calories, or in grams. */
@Serializable
enum class MacroMode { PERCENT, GRAMS }

/**
 * Protein, carbs and fat targets. In [MacroMode.PERCENT] the three numbers are percentages of
 * the calorie target; in [MacroMode.GRAMS] they are grams a day.
 */
@Serializable
data class MacroTargets(
    val mode: MacroMode = MacroMode.PERCENT,
    val protein: Double = 25.0,
    val carbs: Double = 50.0,
    val fat: Double = 25.0,
) {
    /** Grams a day for a calorie target. Protein and carbs have 4 kcal a gram, fat 9. */
    fun grams(kcalTarget: Int): Nutrients = when (mode) {
        MacroMode.GRAMS -> Nutrients(kcalTarget.toDouble(), protein, carbs, fat)
        MacroMode.PERCENT -> Nutrients(
            kcal = kcalTarget.toDouble(),
            protein = kcalTarget * protein / 100.0 / KCAL_PROTEIN,
            carbs = kcalTarget * carbs / 100.0 / KCAL_CARBS,
            fat = kcalTarget * fat / 100.0 / KCAL_FAT,
        )
    }

    /** The same targets in the other mode, for the same calories, so switching keeps the numbers. */
    fun switchTo(newMode: MacroMode, kcalTarget: Int): MacroTargets {
        if (newMode == mode) return this
        return when (newMode) {
            MacroMode.GRAMS -> grams(kcalTarget).let {
                MacroTargets(MacroMode.GRAMS, it.protein.roundToInt().toDouble(), it.carbs.roundToInt().toDouble(), it.fat.roundToInt().toDouble())
            }
            MacroMode.PERCENT -> {
                val k = kcalTarget.toDouble().coerceAtLeast(1.0)
                MacroTargets(
                    MacroMode.PERCENT,
                    (protein * KCAL_PROTEIN * 100 / k).roundToInt().toDouble(),
                    (carbs * KCAL_CARBS * 100 / k).roundToInt().toDouble(),
                    (fat * KCAL_FAT * 100 / k).roundToInt().toDouble(),
                )
            }
        }
    }

    /** In percent mode the three must add up to 100. */
    fun problem(): String? = when {
        protein < 0 || carbs < 0 || fat < 0 -> "Numbers can't be below zero."
        mode == MacroMode.PERCENT && abs(protein + carbs + fat - 100.0) > 0.5 ->
            "The three percentages add up to ${formatAmount(protein + carbs + fat)}%. They need to add up to 100%."
        else -> null
    }

    companion object {
        const val KCAL_PROTEIN = 4.0
        const val KCAL_CARBS = 4.0
        const val KCAL_FAT = 9.0
    }
}

/** The daily water target and the sizes of the two quick buttons, all in millilitres. */
@Serializable
data class WaterSettings(
    val targetMl: Int = 2000,
    val glassMl: Int = 250,
    val bottleMl: Int = 500,
)

/**
 * Daily targets in grams for fibre (at least) and sugar, saturated fat and salt (at most), each
 * off (null) until the user sets it (0.7.0).
 */
@kotlinx.serialization.Serializable
data class ExtraTargets(
    val fibre: Double? = null,
    val sugar: Double? = null,
    val satFat: Double? = null,
    val salt: Double? = null,
    /** Daily limits for alcohol, in standard drinks, and caffeine, in mg (0.11.0). */
    val alcoholDrinks: Double? = null,
    val caffeineMg: Double? = null,
)
