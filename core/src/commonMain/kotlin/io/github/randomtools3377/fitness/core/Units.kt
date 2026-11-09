package io.github.randomtools3377.fitness.core

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class WeightUnit(val short: String) { KG("kg"), LB("lb") }

@Serializable
enum class HeightUnit(val short: String) { CM("cm"), FT_IN("ft/in") }

@Serializable
enum class EnergyUnit(val short: String) { KCAL("kcal"), KJ("kJ") }

@Serializable
enum class VolumeUnit(val short: String) { ML("ml"), FL_OZ("fl oz") }

@Serializable
enum class DistanceUnit(val short: String) { KM("km"), MILES("miles") }

/**
 * The units things are shown and typed in. Everything is stored in kg, cm, kcal, ml and km;
 * these only change what the user sees.
 */
@Serializable
data class UnitSettings(
    val weight: WeightUnit = WeightUnit.KG,
    val height: HeightUnit = HeightUnit.CM,
    val energy: EnergyUnit = EnergyUnit.KCAL,
    val volume: VolumeUnit = VolumeUnit.ML,
    val distance: DistanceUnit = DistanceUnit.KM,
) {
    companion object {
        /** The usual units for the phone's region (an ISO country code like "US"). */
        fun forRegion(country: String): UnitSettings = when (country.uppercase()) {
            // The US, Liberia and Myanmar use pounds, feet and miles.
            "US", "LR", "MM" -> UnitSettings(WeightUnit.LB, HeightUnit.FT_IN, EnergyUnit.KCAL, VolumeUnit.FL_OZ, DistanceUnit.MILES)
            // The UK mostly gives heights in feet and inches and distances in miles.
            "GB" -> UnitSettings(WeightUnit.KG, HeightUnit.FT_IN, EnergyUnit.KCAL, VolumeUnit.ML, DistanceUnit.MILES)
            // Food labels in Australia and New Zealand lead with kilojoules.
            "AU", "NZ" -> UnitSettings(energy = EnergyUnit.KJ)
            else -> UnitSettings()
        }
    }
}

object Units {
    const val KG_PER_LB = 0.45359237
    const val CM_PER_IN = 2.54
    const val KJ_PER_KCAL = 4.184
    const val ML_PER_FL_OZ = 29.5735295625
    const val KM_PER_MILE = 1.609344

    fun kgToShown(kg: Double, u: WeightUnit) = if (u == WeightUnit.KG) kg else kg / KG_PER_LB
    fun shownToKg(x: Double, u: WeightUnit) = if (u == WeightUnit.KG) x else x * KG_PER_LB

    fun kcalToShown(kcal: Double, u: EnergyUnit) = if (u == EnergyUnit.KCAL) kcal else kcal * KJ_PER_KCAL
    fun shownToKcal(x: Double, u: EnergyUnit) = if (u == EnergyUnit.KCAL) x else x / KJ_PER_KCAL

    fun mlToShown(ml: Double, u: VolumeUnit) = if (u == VolumeUnit.ML) ml else ml / ML_PER_FL_OZ
    fun shownToMl(x: Double, u: VolumeUnit) = if (u == VolumeUnit.ML) x else x * ML_PER_FL_OZ

    /** Feet and inches for a height in cm, the inches rounded and carried into feet at 12. */
    fun cmToFeetInches(cm: Double): Pair<Int, Int> {
        val totalIn = (cm / CM_PER_IN).roundToInt()
        return totalIn / 12 to totalIn % 12
    }

    fun feetInchesToCm(feet: Int, inches: Double): Double = (feet * 12 + inches) * CM_PER_IN

    /** "72.5 kg", "159.8 lb". */
    fun weight(kg: Double, u: WeightUnit): String = "${oneDecimal(kgToShown(kg, u))} ${u.short}"

    /** "178 cm", "5 ft 10 in". */
    fun height(cm: Double, u: HeightUnit): String = when (u) {
        HeightUnit.CM -> "${cm.roundToInt()} cm"
        HeightUnit.FT_IN -> cmToFeetInches(cm).let { (f, i) -> "$f ft $i in" }
    }

    /** "1,850 kcal", "7,740 kJ". */
    fun energy(kcal: Double, u: EnergyUnit): String = "${grouped(kcalToShown(kcal, u).roundToInt())} ${u.short}"

    /** "750 ml", "25.4 fl oz". */
    fun volume(ml: Double, u: VolumeUnit): String = when (u) {
        VolumeUnit.ML -> "${grouped(ml.roundToInt())} ml"
        VolumeUnit.FL_OZ -> "${oneDecimal(mlToShown(ml, u))} fl oz"
    }

    fun kmToShown(km: Double, u: DistanceUnit) = if (u == DistanceUnit.KM) km else km / KM_PER_MILE
    fun shownToKm(x: Double, u: DistanceUnit) = if (u == DistanceUnit.KM) x else x * KM_PER_MILE

    /** "5 km", "3.1 miles". */
    fun distance(km: Double, u: DistanceUnit): String = "${oneDecimal(kmToShown(km, u))} ${u.short}"

    /** Grams, rounded to whole grams, or one decimal below 10 g: "32 g", "4.5 g". */
    fun grams(g: Double): String = if (g < 9.95) "${oneDecimal(g)} g" else "${g.roundToInt()} g"

    fun oneDecimal(x: Double): String {
        val tenths = (x * 10).roundToInt()
        val sign = if (tenths < 0) "-" else ""
        val a = kotlin.math.abs(tenths)
        return if (a % 10 == 0) "$sign${a / 10}" else "$sign${a / 10}.${a % 10}"
    }

    /** 1850 → "1,850". */
    fun grouped(n: Int): String {
        val s = kotlin.math.abs(n).toString()
        val out = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) out.append(',')
            out.append(c)
        }
        return (if (n < 0) "-" else "") + out
    }

    /** Reads a number the user typed, with a dot or a comma for decimals. Null if it isn't one. */
    fun parse(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        val x = t.toDoubleOrNull() ?: return null
        return if (x.isNaN() || x.isInfinite()) null else x
    }
}
