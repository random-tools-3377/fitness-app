package io.github.randomtools3377.fitness.core

/** How often, and how lately, a food was logged. [lastUsed] is epoch milliseconds. */
data class Usage(val key: String, val count: Int, val lastUsed: Long)

/** Finding foods: the user's own and the built-in list, with the most recent and most used first. */
object FoodSearch {
    private const val DAY_MS = 86_400_000.0

    /**
     * A food's place among the recent and frequent ones: higher comes first. Each use counts,
     * and counts less the longer ago the food was last eaten, so a food eaten every day stays
     * near the top and one eaten often last year drifts down.
     */
    fun usageScore(usage: Usage, now: Long): Double {
        val daysAgo = ((now - usage.lastUsed) / DAY_MS).coerceAtLeast(0.0)
        return usage.count / (1.0 + daysAgo / 7.0)
    }

    /** The favourites (0.7.0) and then the foods that have been used, most recent and most used first. */
    fun recent(foods: List<Food>, usages: Map<String, Usage>, now: Long, favourites: Set<String> = emptySet()): List<Food> =
        foods.filter { it.key in usages || it.key in favourites }
            .sortedWith(
                compareByDescending<Food> { it.key in favourites }
                    .thenByDescending { usages[it.key]?.let { u -> usageScore(u, now) } ?: 0.0 }
                    .thenBy { it.name.lowercase() },
            )

    /**
     * The built-in foods to list: all of them, but those the user made their own copy of (0.19.0),
     * while that copy is still one of their foods. Deleting the copy brings the original back.
     */
    fun visibleBuiltIns(built: List<Food>, replaced: Map<String, Long>, mine: List<Food>): List<Food> {
        if (replaced.isEmpty()) return built
        val live = mine.filter { !it.hidden }.map { it.id }.toSet()
        val gone = replaced.filterValues { it in live }.keys
        return if (gone.isEmpty()) built else built.filter { it.builtInId !in gone }
    }

    /** A built-in food as the start of the user's own copy: the same numbers and portions, as a new food. */
    fun copyOf(food: Food): Food = food.copy(id = 0, builtInId = null, hidden = false, barcode = null)

    /** Lowercase words, with accents and punctuation taken off. */
    fun words(text: String): List<String> =
        fold(text).split(' ').filter { it.isNotEmpty() }

    private fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text.lowercase()) {
            val plain = ACCENTS[c] ?: c
            sb.append(if (plain.isLetterOrDigit()) plain else ' ')
        }
        return sb.toString()
    }

    /** Whether every word typed starts some word of the food's name or brand. */
    fun matches(food: Food, query: String): Boolean {
        val q = words(query)
        if (q.isEmpty()) return false
        val w = words(food.name + " " + food.brand)
        return q.all { part -> w.any { it.startsWith(part) } }
    }

    /**
     * Foods matching [query], best first: the user's favourites (0.7.0), then foods used before (most recent and most used first),
     * then the user's own, then names that start with what was typed, then shorter names.
     */
    fun search(
        query: String,
        foods: List<Food>,
        usages: Map<String, Usage>,
        now: Long,
        limit: Int = 100,
        favourites: Set<String> = emptySet(),
    ): List<Food> {
        val q = words(query)
        if (q.isEmpty()) return emptyList()
        val first = q.first()
        return foods.asSequence()
            .filter { matches(it, query) }
            .map { food ->
                val nameWords = words(food.name)
                var score = 0.0
                if (food.key in favourites) score += 10_000.0
                usages[food.key]?.let { score += 1000.0 + 100.0 * usageScore(it, now) }
                if (food.builtInId == null) score += 500.0
                if (nameWords.firstOrNull()?.startsWith(first) == true) score += 50.0
                if (nameWords.any { it == first }) score += 20.0
                score -= nameWords.size * 2.0
                score -= food.name.length / 10.0
                food to score
            }
            .sortedWith(compareByDescending<Pair<Food, Double>> { it.second }.thenBy { it.first.name.lowercase() })
            .take(limit)
            .map { it.first }
            .toList()
    }

    private val ACCENTS: Map<Char, Char> = buildMap {
        "àáâãäå".forEach { put(it, 'a') }
        "èéêë".forEach { put(it, 'e') }
        "ìíîï".forEach { put(it, 'i') }
        "òóôõö".forEach { put(it, 'o') }
        "ùúûü".forEach { put(it, 'u') }
        put('ç', 'c'); put('ñ', 'n'); put('ý', 'y'); put('ÿ', 'y')
    }
}

/** Reads the bundled food list (foods.tsv, made by tools/build-food-list.py). */
object BuiltInFoods {
    /** An optional number column: empty or missing is unknown. */
    private fun opt(f: List<String>, i: Int): Double? = f.getOrNull(i)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    fun parse(text: String): List<Food> {
        val out = ArrayList<Food>(16_000)
        for (line in text.lineSequence()) {
            if (line.isEmpty() || line.startsWith("#")) continue
            val f = line.split('\t')
            if (f.size < 6) continue
            val portions = if (f.size > 6 && f[6].isNotEmpty()) {
                f[6].split('|').mapNotNull { p ->
                    val i = p.lastIndexOf('=')
                    if (i <= 0) null else p.substring(i + 1).toDoubleOrNull()?.let { Portion(p.substring(0, i), it) }
                }
            } else {
                emptyList()
            }
            out += Food(
                builtInId = f[0],
                name = f[1],
                unit = BaseUnit.GRAM,
                per100 = Nutrients(
                    f[2].toDouble(), f[3].toDouble(), f[4].toDouble(), f[5].toDouble(),
                    fibre = opt(f, 7), sugar = opt(f, 8), satFat = opt(f, 9), salt = opt(f, 10),
                    micros = Micros.fromColumns(f, 11),
                ),
                portions = portions,
            )
        }
        return out
    }
}
