package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** The day's diary is split into these, in this order. */
@Serializable
enum class Meal(val title: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks"),
}

/**
 * One thing eaten. Its nutrients are worked out when it is logged and kept with it, so changing
 * a food later doesn't rewrite what was already eaten. A quick add has no food: just calories.
 */
@Serializable
data class DiaryEntry(
    val id: Long = 0,
    val day: LocalDate,
    val meal: Meal,
    /** The typed-in food it came from, if any. */
    val foodId: Long? = null,
    /** The built-in food it came from, if any. */
    val builtInId: String? = null,
    val name: String,
    val amount: Amount = Amount(1.0),
    val unit: BaseUnit = BaseUnit.GRAM,
    val nutrients: Nutrients,
    /** Epoch milliseconds when it was logged. */
    val createdAt: Long = 0,
    /** Planned ahead and not eaten yet (0.5.0): shown on the plan, not counted in the day. */
    val planned: Boolean = false,
) {
    val isQuickAdd: Boolean get() = foodId == null && builtInId == null

    /** The key of the food it came from, as [Food.key]; null for a quick add. */
    val foodKey: String?
        get() = when {
            builtInId != null -> "b:$builtInId"
            foodId != null -> "u:$foodId"
            else -> null
        }

    fun describeAmount(): String = if (isQuickAdd) "Quick add" else amount.describe(unit)
}

object Diary {
    /** A new entry for [food] eaten in [amount]. */
    fun entry(food: Food, amount: Amount, day: LocalDate, meal: Meal, now: Long): DiaryEntry = DiaryEntry(
        day = day,
        meal = meal,
        foodId = if (food.builtInId == null) food.id else null,
        builtInId = food.builtInId,
        name = food.displayName(),
        amount = amount,
        unit = food.unit,
        nutrients = food.nutrientsFor(amount),
        createdAt = now,
    )

    /** Quick add: just a number of calories, with no food. */
    fun quickAdd(kcal: Double, day: LocalDate, meal: Meal, now: Long, name: String = "Quick add"): DiaryEntry {
        require(kcal > 0) { "Type a number of calories above 0." }
        return DiaryEntry(day = day, meal = meal, name = name.ifBlank { "Quick add" }, nutrients = Nutrients(kcal = kcal), createdAt = now)
    }

    /** The same entry with a new amount, its nutrients scaled to match. */
    fun changeAmount(entry: DiaryEntry, food: Food, amount: Amount): DiaryEntry =
        entry.copy(amount = amount, nutrients = food.nutrientsFor(amount))

    /**
     * Copies of [entries] (a meal, or a whole day) for another day, as new entries. Each keeps its
     * own meal unless [toMeal] is given.
     */
    fun copy(entries: List<DiaryEntry>, toDay: LocalDate, now: Long, toMeal: Meal? = null): List<DiaryEntry> =
        entries.sortedBy { it.createdAt }.mapIndexed { i, e ->
            e.copy(id = 0, day = toDay, meal = toMeal ?: e.meal, createdAt = now + i)
        }
}

/** A day's totals, per meal and all together. */
data class DayTotals(val byMeal: Map<Meal, Nutrients>, val total: Nutrients) {
    companion object {
        fun of(entries: List<DiaryEntry>): DayTotals {
            // Planned items don't count until they are ticked as eaten.
            val eaten = entries.filter { !it.planned }
            val byMeal = Meal.entries.associateWith { m -> eaten.filter { it.meal == m }.map { it.nutrients }.total() }
            return DayTotals(byMeal, eaten.map { it.nutrients }.total())
        }
    }
}

/** A saved meal, like "Usual breakfast": its items are logged together in one go. */
@Serializable
data class SavedMeal(
    val id: Long = 0,
    val name: String,
    val items: List<SavedMealItem>,
    val createdAt: Long = 0,
) {
    val nutrients: Nutrients get() = items.map { it.nutrients }.total()

    /** The meal's items as entries in the diary. */
    fun toEntries(day: LocalDate, meal: Meal, now: Long): List<DiaryEntry> = items.mapIndexed { i, item ->
        DiaryEntry(
            day = day, meal = meal, foodId = item.foodId, builtInId = item.builtInId, name = item.name,
            amount = item.amount, unit = item.unit, nutrients = item.nutrients, createdAt = now + i,
        )
    }

    companion object {
        /** A saved meal made from entries already in the diary. */
        fun from(name: String, entries: List<DiaryEntry>, now: Long): SavedMeal {
            require(name.isNotBlank()) { "Give the meal a name." }
            require(entries.isNotEmpty()) { "There is nothing in this meal to save." }
            return SavedMeal(
                name = name.trim(),
                items = entries.sortedBy { it.createdAt }.map {
                    SavedMealItem(it.foodId, it.builtInId, it.name, it.amount, it.unit, it.nutrients)
                },
                createdAt = now,
            )
        }
    }
}

@Serializable
data class SavedMealItem(
    val foodId: Long? = null,
    val builtInId: String? = null,
    val name: String,
    val amount: Amount = Amount(1.0),
    val unit: BaseUnit = BaseUnit.GRAM,
    val nutrients: Nutrients,
)
