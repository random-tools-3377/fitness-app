package io.github.randomtools3377.fitness.core

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Calories (kcal) and grams of protein, carbs and fat. */
@Serializable
data class Nutrients(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(other: Nutrients) =
        Nutrients(kcal + other.kcal, protein + other.protein, carbs + other.carbs, fat + other.fat)

    operator fun times(factor: Double) = Nutrients(kcal * factor, protein * factor, carbs * factor, fat * factor)

    companion object {
        val ZERO = Nutrients()
    }
}

fun Iterable<Nutrients>.total(): Nutrients = fold(Nutrients.ZERO) { a, b -> a + b }

/** What a food's numbers are measured in: grams for solids, millilitres for drinks. */
@Serializable
enum class BaseUnit(val short: String) {
    GRAM("g"),
    MILLILITRE("ml"),
}

/** A food's own portion, like "1 slice" weighing 30 g. [amount] is in the food's [BaseUnit]. */
@Serializable
data class Portion(val label: String, val amount: Double)

/**
 * A food. Its nutrients are per 100 g (or per 100 ml). A food the user typed in has an [id];
 * a built-in one has a [builtInId] like "S171705" (its FoodData Central id).
 */
@Serializable
data class Food(
    val id: Long = 0,
    val builtInId: String? = null,
    val name: String,
    val brand: String = "",
    val unit: BaseUnit = BaseUnit.GRAM,
    val per100: Nutrients,
    val portions: List<Portion> = emptyList(),
    /** A typed-in food the user deleted: off the lists, but kept for the entries that used it. */
    val hidden: Boolean = false,
    /** The barcode of a typed-in food, so scanning it again finds it (0.3.0). */
    val barcode: String? = null,
) {
    /** The same food is the same key, whether typed in or built in. */
    val key: String get() = builtInId?.let { "b:$it" } ?: "u:$id"

    fun nutrientsFor(baseAmount: Double): Nutrients = per100 * (baseAmount / 100.0)

    fun nutrientsFor(amount: Amount): Nutrients = nutrientsFor(amount.baseAmount(this))

    fun displayName(): String = if (brand.isBlank()) name else "$name ($brand)"
}

/**
 * How much was eaten: [quantity] of a [portion] ("2 × 1 slice"), or, with no portion, that many
 * grams or millilitres.
 */
@Serializable
data class Amount(val quantity: Double, val portion: String? = null) {
    fun baseAmount(food: Food): Double {
        val p = portion ?: return quantity
        val size = food.portions.firstOrNull { it.label == p }?.amount
            ?: throw IllegalArgumentException("${food.name} has no portion \"$p\"")
        return quantity * size
    }

    /** "150 g", "2 × 1 slice", "1 cup". */
    fun describe(unit: BaseUnit): String {
        val q = formatAmount(quantity)
        return when {
            portion == null -> "$q ${unit.short}"
            quantity == 1.0 -> portion
            else -> "$q × $portion"
        }
    }
}

/** Up to two decimals, without trailing zeros: 1, 1.5, 0.25. */
fun formatAmount(x: Double): String {
    val hundredths = (x * 100).roundToInt()
    val whole = hundredths / 100
    val rest = hundredths % 100
    return when {
        rest == 0 -> whole.toString()
        rest % 10 == 0 -> "$whole.${rest / 10}"
        else -> "$whole.${rest.toString().padStart(2, '0')}"
    }
}

/** The numbers from a food label, which may be per 100 g or per serving. */
object Label {
    /** Per-serving values turned into per-100 values. A serving of 0 is rejected. */
    fun per100(perServing: Nutrients, servingSize: Double): Nutrients {
        require(servingSize > 0) { "The serving size must be more than 0." }
        return perServing * (100.0 / servingSize)
    }

    /**
     * Problems with a typed-in food, in plain words; empty when it can be saved. Carbs, protein
     * and fat can't weigh more than the food itself.
     */
    fun problems(name: String, per100: Nutrients, unit: BaseUnit = BaseUnit.GRAM): List<String> {
        val out = mutableListOf<String>()
        if (name.isBlank()) out += "Give the food a name."
        if (per100.kcal < 0 || per100.protein < 0 || per100.carbs < 0 || per100.fat < 0) {
            out += "Numbers can't be below zero."
        }
        // Only by weight: 100 ml of syrup can hold more than 100 g of sugar.
        if (unit == BaseUnit.GRAM && per100.protein + per100.carbs + per100.fat > 100.5) {
            out += "Protein, carbs and fat add up to more than the food weighs. Check the numbers and the serving size."
        }
        if (unit == BaseUnit.GRAM && per100.kcal > 950) out += "That is more calories than pure fat has. Check the numbers and the serving size."
        return out
    }
}

/** Stores a food's portions as text in the database, in the same form as the backup file. */
object PortionText {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val serializer = kotlinx.serialization.builtins.ListSerializer(Portion.serializer())

    fun encode(portions: List<Portion>): String = json.encodeToString(serializer, portions)
    fun decode(text: String): List<Portion> = if (text.isBlank()) emptyList() else json.decodeFromString(serializer, text)
}
