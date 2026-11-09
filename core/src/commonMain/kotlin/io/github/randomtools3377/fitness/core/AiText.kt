package io.github.randomtools3377.fitness.core

import kotlin.math.roundToInt

/**
 * What the optional on-phone AI (0.18.0 onwards) says, turned into foods the user checks. The
 * model only picks words; the numbers always come from the app's own food data.
 */
object AiText {
    /** One food the model named: "2 | piece | boiled egg". */
    data class Item(val amount: Double, val unit: String, val food: String)

    /** The model's lines, in "amount | unit | food" form. Lines that aren't are left out. */
    fun items(text: String): List<Item> = text.lines().mapNotNull { line ->
        val parts = line.split('|').map { it.trim() }
        if (parts.size != 3 || parts[2].isEmpty()) return@mapNotNull null
        val amount = parts[0].replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        Item(amount, parts[1].lowercase(), parts[2])
    }.take(15)

    /** Names, one per line, as the plate-photo model gives them; repeats left out. */
    fun names(text: String): List<String> =
        text.lines().map { it.trim().trim(',', '.', '-', '*').trim() }.filter { it.length >= 2 }
            .distinctBy { it.lowercase() }.take(8)

    /** What the speech model heard, without its "language English<asr_text>" opening. */
    fun heard(text: String): String {
        val at = text.indexOf("<asr_text>")
        val words = if (at >= 0) text.substring(at + "<asr_text>".length) else text.replace(Regex("""^\s*language\s+\S+\s*""", RegexOption.IGNORE_CASE), "")
        return words.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    }

    // Matching a name to a food.

    private fun singular(w: String): String = when {
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && (w.endsWith("oes") || w.endsWith("ches") || w.endsWith("shes")) -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    /** Words that say little about which food it is. */
    private val FILLER = setOf("a", "an", "the", "of", "with", "and", "some", "fresh", "plain", "homemade", "small", "large", "big", "medium")

    /**
     * The foods that fit [name] best, best first: everything [FoodSearch] finds for the whole name,
     * then foods sharing the most of its words, the last word (usually the food itself) counting
     * most. Used before, the user's own and shorter names come first among equals.
     */
    fun candidates(
        name: String,
        foods: List<Food>,
        usages: Map<String, Usage>,
        now: Long,
        favourites: Set<String> = emptySet(),
        limit: Int = 6,
    ): List<Food> {
        val exact = FoodSearch.search(name, foods, usages, now, limit, favourites)
        if (exact.size >= limit) return exact
        val q = FoodSearch.words(name).map(::singular).filter { it !in FILLER }
        if (q.isEmpty()) return exact
        val head = q.last()
        val seen = exact.map { it.key }.toMutableSet()
        val scored = foods.asSequence()
            .filter { !it.hidden && it.key !in seen }
            .mapNotNull { food ->
                val w = FoodSearch.words(food.name + " " + food.brand).map(::singular)
                val hits = q.count { part -> w.any { it == part || (part.length >= 4 && it.startsWith(part)) } }
                if (hits == 0) return@mapNotNull null
                val headHit = w.any { it == head }
                var score = hits * 10.0 + (if (headHit) 25.0 else 0.0)
                if (food.key in favourites) score += 8.0
                usages[food.key]?.let { score += 5.0 + FoodSearch.usageScore(it, now) }
                if (food.builtInId == null) score += 3.0
                score -= w.size * 0.8
                food to score
            }
            .sortedWith(compareByDescending<Pair<Food, Double>> { it.second }.thenBy { it.first.name.length })
            .take(limit - exact.size)
            .map { it.first }
            .toList()
        return exact + scored
    }

    // Turning "2 | slice" into an amount of a food.

    /** Usual sizes, in g or ml, for units a food has no portion for. */
    private val USUAL = mapOf(
        "cup" to 240.0, "tbsp" to 15.0, "tsp" to 5.0, "glass" to 250.0, "can" to 330.0, "bottle" to 500.0,
        "bowl" to 250.0, "handful" to 30.0, "slice" to 30.0, "piece" to 100.0, "serving" to 100.0,
    )

    /** Words in a portion's label that mean each unit. */
    private val UNIT_WORDS = mapOf(
        "cup" to listOf("cup"), "tbsp" to listOf("tbsp", "tablespoon"), "tsp" to listOf("tsp", "teaspoon"),
        "slice" to listOf("slice"), "glass" to listOf("glass"), "can" to listOf("can"), "bottle" to listOf("bottle"),
        "bowl" to listOf("bowl"), "handful" to listOf("handful"),
        "piece" to listOf("piece", "medium", "whole", "large", "small", "egg", "fruit", "item", "each"),
        "serving" to listOf("serving", "portion", "medium", "piece"),
    )

    /** An amount of [food] for "[amount] [unit]", and whether it is a guess the user should look at. */
    data class Fit(val amount: Amount, val guessed: Boolean)

    fun amountFor(food: Food, amount: Double, unit: String): Fit {
        if (unit == "g" || unit == "ml") return Fit(Amount(amount), guessed = unit != food.unit.short)
        val words = UNIT_WORDS[unit].orEmpty()
        val portion = words.firstNotNullOfOrNull { w -> food.portions.firstOrNull { w in it.label.lowercase() } }
            ?: if (unit == "serving" || unit == "piece") food.portions.firstOrNull() else null
        if (portion != null) return Fit(Amount(amount, portion.label), guessed = false)
        val grams = USUAL[unit] ?: 100.0
        return Fit(Amount(round1(amount * grams)), guessed = true)
    }

    private fun round1(x: Double) = (x * 10).roundToInt() / 10.0

    // Recipes.

    /** A pasted recipe's name, servings and method, read without the model. */
    data class RecipeParts(val name: String, val servings: Int?, val method: String)

    private val SERVES = Regex("""\b(?:serves|servings?|makes|portions?|for)\s*:?\s*(\d{1,2})\b|\b(\d{1,2})\s*(?:servings|portions|people)\b""", RegexOption.IGNORE_CASE)
    private val METHOD = Regex("""^\s*(method|instructions|directions|steps|preparation)\s*:?\s*$""", RegexOption.IGNORE_CASE)
    private val INGREDIENTS = Regex("""^\s*ingredients\s*:?\s*$""", RegexOption.IGNORE_CASE)

    fun recipeParts(text: String): RecipeParts {
        val lines = text.lines()
        val first = lines.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val name = first.replace(Regex("""\s*\((?:makes|serves)[^)]*\)\s*""", RegexOption.IGNORE_CASE), "").trim()
            .takeIf { !INGREDIENTS.matches(it) && it.length <= 80 } ?: ""
        val servings = SERVES.find(text)?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() }?.takeIf { it in 1..50 }
        val at = lines.indexOfFirst { METHOD.matches(it) }
        val method = if (at >= 0) lines.drop(at + 1).joinToString("\n").trim() else ""
        return RecipeParts(name, servings, method)
    }

    // The weekly summary's facts.

    /** The numbers the weekly summary is written from, as plain lines (the model only words them). */
    fun weekFacts(
        from: String,
        to: String,
        daysLogged: Int,
        avgKcal: Int?,
        targetKcal: Int?,
        daysOnTarget: Int?,
        avgProtein: Int?,
        targetProtein: Int?,
        workouts: Int?,
        workoutGoal: Int?,
        waterDays: Int?,
        weightChange: String?,
        energyUnit: String = "kcal",
    ): String = buildString {
        appendLine("The week of $from to $to.")
        appendLine("Days with food logged: $daysLogged of 7.")
        if (avgKcal != null) appendLine("Average calories: ${Units.grouped(avgKcal)} $energyUnit a day" + (targetKcal?.let { "; target ${Units.grouped(it)} $energyUnit." } ?: "."))
        if (daysOnTarget != null && targetKcal != null) appendLine("Days within 10% of the calorie target: $daysOnTarget of $daysLogged.")
        if (avgProtein != null) appendLine("Average protein: $avgProtein g a day" + (targetProtein?.let { "; target $it g." } ?: "."))
        if (workouts != null) appendLine("Workouts: $workouts" + (workoutGoal?.let { "; their goal is $it a week." } ?: "."))
        if (waterDays != null) appendLine("Water target reached on $waterDays of 7 days.")
        if (weightChange != null) appendLine("Weight: $weightChange.")
    }.trim()
}
