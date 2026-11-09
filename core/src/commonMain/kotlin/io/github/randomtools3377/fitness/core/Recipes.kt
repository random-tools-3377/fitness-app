package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** A food in a recipe, with how much of it. Its nutrients are kept with it, like a diary entry. */
@Serializable
data class RecipeItem(
    val foodId: Long? = null,
    val builtInId: String? = null,
    val name: String,
    val amount: Amount,
    val unit: BaseUnit = BaseUnit.GRAM,
    /** Grams (or ml) of it in the recipe. */
    val grams: Double,
    val nutrients: Nutrients,
) {
    val foodKey: String? get() = builtInId?.let { "b:$it" } ?: foodId?.let { "u:$it" }
}

/**
 * A recipe made from several foods, split into servings. If the finished dish was weighed,
 * [cookedGrams] lets it be logged by weight, which allows for water lost or gained in cooking.
 */
@Serializable
data class Recipe(
    val id: Long = 0,
    val name: String,
    val servings: Double,
    val items: List<RecipeItem>,
    val cookedGrams: Double? = null,
    val notes: String = "",
    val createdAt: Long = 0,
) {
    val total: Nutrients get() = items.map { it.nutrients }.total()
    val rawGrams: Double get() = items.sumOf { it.grams }
    val perServing: Nutrients get() = total * (1.0 / servings.coerceAtLeast(0.01))

    /** The whole dish's weight: as weighed, or the weight of its foods. */
    val dishGrams: Double get() = cookedGrams?.takeIf { it > 0 } ?: rawGrams

    /**
     * As a food to log: per 100 g of the finished dish, with "1 serving" as a portion. Its key is
     * "b:R" and the recipe's id. Changing the recipe later doesn't change what was logged.
     */
    fun toFood(): Food {
        val grams = dishGrams.coerceAtLeast(0.01)
        return Food(
            builtInId = PREFIX + id,
            name = name,
            unit = BaseUnit.GRAM,
            per100 = total * (100.0 / grams),
            portions = listOf(Portion("1 serving", grams / servings.coerceAtLeast(0.01))),
        )
    }

    fun problems(): List<String> = buildList {
        if (name.isBlank()) add("Give the recipe a name.")
        if (items.isEmpty()) add("Add the foods that go in it.")
        if (servings <= 0) add("Say how many servings it makes.")
        if (cookedGrams != null && cookedGrams <= 0) add("The weighed dish must weigh more than 0.")
    }

    companion object {
        const val PREFIX = "R"

        fun item(food: Food, amount: Amount): RecipeItem = RecipeItem(
            foodId = if (food.builtInId == null) food.id else null,
            builtInId = food.builtInId,
            name = food.displayName(),
            amount = amount,
            unit = food.unit,
            grams = amount.baseAmount(food),
            nutrients = food.nutrientsFor(amount),
        )
    }
}

/** A line on the shopping list: a food and how much, in g or ml; or the user's own item. */
@Serializable
data class ShoppingItem(
    val id: Long = 0,
    val name: String,
    val grams: Double? = null,
    val unit: BaseUnit = BaseUnit.GRAM,
    val ticked: Boolean = false,
    /** Typed in by the user, like "Washing-up liquid", rather than made from the plan. */
    val own: Boolean = false,
)

object Plan {
    /** A planned item for a food: like a diary entry, but not eaten yet. */
    fun plan(food: Food, amount: Amount, day: LocalDate, meal: Meal, now: Long): DiaryEntry =
        Diary.entry(food, amount, day, meal, now).copy(planned = true)

    /** Ticking a planned item logs it as eaten, now. */
    fun eat(e: DiaryEntry, now: Long): DiaryEntry = e.copy(planned = false, createdAt = now)

    /** The planned calories and protein, carbs and fat for a day. */
    fun planned(entries: List<DiaryEntry>): Nutrients = entries.filter { it.planned }.map { it.nutrients }.total()

    /** Copies of a day's or a week's plan [days] days later, still planned. */
    fun copy(entries: List<DiaryEntry>, days: Int, now: Long): List<DiaryEntry> =
        entries.filter { it.planned }.sortedBy { it.createdAt }.mapIndexed { i, e ->
            e.copy(id = 0, day = LocalDate.fromEpochDays(e.day.toEpochDays() + days), createdAt = now + i)
        }

    /**
     * The shopping list for planned items: the same food added up across the days, and recipes
     * broken into their foods (scaled to the amount planned). Quick adds are left out. Sorted by
     * name.
     */
    fun shopping(planned: List<DiaryEntry>, recipes: Map<Long, Recipe>, grams: (DiaryEntry) -> Double?): List<ShoppingItem> {
        val totals = LinkedHashMap<String, ShoppingItem>()
        fun add(key: String, name: String, g: Double, unit: BaseUnit) {
            val old = totals[key]
            totals[key] = ShoppingItem(name = name, grams = (old?.grams ?: 0.0) + g, unit = unit)
        }
        for (e in planned.filter { it.planned }) {
            val recipeId = e.builtInId?.takeIf { it.startsWith(Recipe.PREFIX) }?.drop(1)?.toLongOrNull()
            val recipe = recipeId?.let { recipes[it] }
            if (recipe != null) {
                val g = grams(e) ?: continue
                val share = g / recipe.dishGrams.coerceAtLeast(0.01)
                for (item in recipe.items) add(item.foodKey ?: "n:${item.name}", item.name, item.grams * share, item.unit)
            } else {
                val key = e.foodKey ?: continue
                add(key, e.name, grams(e) ?: continue, e.unit)
            }
        }
        return totals.values.sortedBy { it.name.lowercase() }
    }
}

/** Stores a recipe's foods as text in the database, in the same form as the backup file. */
object RecipeText {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val serializer = kotlinx.serialization.builtins.ListSerializer(RecipeItem.serializer())

    fun encode(items: List<RecipeItem>): String = json.encodeToString(serializer, items)
    fun decode(text: String): List<RecipeItem> = if (text.isBlank()) emptyList() else json.decodeFromString(serializer, text)
}
