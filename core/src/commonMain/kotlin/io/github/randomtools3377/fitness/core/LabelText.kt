package io.github.randomtools3377.fitness.core

/**
 * The numbers read off a nutrition label, before the user checks them. [perServing] says whether
 * they are per serving (of [servingSize] g or ml) or per 100. Anything not found is null.
 */
data class LabelReading(
    val perServing: Boolean,
    val servingSize: Double?,
    val millilitres: Boolean,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fibre: Double?,
    val sugar: Double?,
    val satFat: Double?,
    val salt: Double?,
    /** A US-style label, whose carbs included fibre: the fibre has been taken off [carbs]. */
    val fibreTakenOff: Boolean,
) {
    /** How many of the numbers were found, to tell the user. */
    val found: Int get() = listOfNotNull(kcal, protein, carbs, fat, fibre, sugar, satFat, salt).size
}

/**
 * Reads the text that text recognition found on a nutrition label (0.7.0). It knows EU labels
 * ("Energy 1550 kJ / 370 kcal", "of which sugars", per 100 g in the first column) and US labels
 * ("Calories 190", "Total Carbohydrate", per serving, sodium in mg). It only reads what is there:
 * a number it can't find stays empty for the user to type.
 */
object LabelText {
    private val NUMBER = Regex("""(<\s*)?(\d+(?:[.,]\d+)?)\s*(mg|g|kj|kcal)?""")

    fun read(text: String): LabelReading? {
        val clean = fixDigits(text.lowercase())
        val lines = clean.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val per100 = Regex("""(per|/|pour|je)?\s*100\s*(g|ml)\b""").containsMatchIn(clean)
        val us = "total carbohydrate" in clean || "dietary fiber" in clean || "nutrition facts" in clean ||
            (Regex("""\bcalories\b""").containsMatchIn(clean) && "kj" !in clean)
        val serving = servingSize(clean)
        val millilitres = Regex("""100\s*ml\b""").containsMatchIn(clean) ||
            (serving != null && Regex("""serving size[^\n]*\d\s*ml\b""").containsMatchIn(clean))

        var kcal = Regex("""(\d+(?:[.,]\d+)?)\s*kcal""").find(clean)?.let { num(it.groupValues[1]) }
        if (kcal == null) kcal = Regex("""(\d+(?:[.,]\d+)?)\s*kj""").find(clean)?.let { num(it.groupValues[1])?.div(Units.KJ_PER_KCAL) }
        if (kcal == null) kcal = Regex("""\bcalories\D{0,12}?(\d+(?:[.,]\d+)?)""").find(clean)?.let { num(it.groupValues[1]) }

        var protein: Double? = null
        var carbs: Double? = null
        var fat: Double? = null
        var fibre: Double? = null
        var sugar: Double? = null
        var satFat: Double? = null
        var salt: Double? = null
        var sodium: Double? = null
        for (line in lines) {
            when {
                "saturate" in line || "sat fat" in line || "sat. fat" in line -> if (satFat == null) satFat = grams(line, "satur", "sat")
                "trans" in line || "unsaturate" in line || "polyunsat" in line || "monounsat" in line -> {}
                "added sugar" in line || "polyol" in line -> {}
                "sugar" in line -> if (sugar == null) sugar = grams(line, "sugar")
                "fibre" in line || "fiber" in line -> if (fibre == null) fibre = grams(line, "fibre", "fiber")
                "carbohydrate" in line || Regex("""\bcarbs?\b""").containsMatchIn(line) -> if (carbs == null) carbs = grams(line, "carb")
                "protein" in line -> if (protein == null) protein = grams(line, "protein")
                "sodium" in line -> if (sodium == null) sodium = sodiumMg(line)
                "salt" in line -> if (salt == null) salt = grams(line, "salt")
                Regex("""\bfat\b""").containsMatchIn(line) -> if (fat == null) fat = grams(line, "fat")
            }
        }
        if (salt == null && sodium != null) salt = Nutrients.saltFromSodiumMg(sodium)
        val takeOff = us && carbs != null && fibre != null
        if (takeOff) carbs = (carbs!! - fibre!!).coerceAtLeast(0.0)

        val reading = LabelReading(
            perServing = !per100 && serving != null || (us && !per100),
            servingSize = serving,
            millilitres = millilitres,
            kcal = kcal, protein = protein, carbs = carbs, fat = fat,
            fibre = fibre, sugar = sugar, satFat = satFat, salt = salt,
            fibreTakenOff = takeOff,
        )
        return reading.takeIf { it.found > 0 }
    }

    /** OCR often reads a 0 as the letter O, and a 1 as l or I, next to other digits. */
    private fun fixDigits(text: String): String =
        text.replace(Regex("""(?<=\d)[oO](?=[\d.,\sgk%]|$)|(?<=[\s<])[oO](?=[.,]\d)""")) { "0" }
            .replace(Regex("""(?<=\d)[lI](?=\d)|(?<=\s)[lI](?=\d)"""), "1")

    private fun num(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

    /** The first amount after the nutrient's name on the line, in grams; "< 0.5 g" is 0. */
    private fun grams(line: String, vararg names: String): Double? {
        val at = names.map { line.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val m = NUMBER.findAll(line, at).firstOrNull { it.groupValues[3] != "kj" && it.groupValues[3] != "kcal" } ?: return null
        if (m.groupValues[1].isNotEmpty()) return 0.0
        val x = num(m.groupValues[2]) ?: return null
        return if (m.groupValues[3] == "mg") x / 1000.0 else x
    }

    /** Sodium, in mg: US labels give mg; a value in g is turned into mg. */
    private fun sodiumMg(line: String): Double? {
        val m = NUMBER.findAll(line, line.indexOf("sodium")).firstOrNull() ?: return null
        if (m.groupValues[1].isNotEmpty()) return 0.0
        val x = num(m.groupValues[2]) ?: return null
        return if (m.groupValues[3] == "g") x * 1000.0 else x
    }

    /** The serving size in g or ml: "Serving size 1 bar (45g)", "per 30 g serving", "Per portion (30g)". */
    private fun servingSize(text: String): Double? {
        val patterns = listOf(
            Regex("""serving size[^\n]*?\(?\s*(\d+(?:[.,]\d+)?)\s*(g|ml)\b"""),
            Regex("""per\s+(?:serving|portion|bar|biscuit|slice|pack)[^\n]*?(\d+(?:[.,]\d+)?)\s*(g|ml)\b"""),
            Regex("""per\s+(\d+(?:[.,]\d+)?)\s*(g|ml)\s*(?:serving|portion)"""),
        )
        for (p in patterns) {
            for (m in p.findAll(text)) {
                val x = num(m.groupValues[1]) ?: continue
                if (x > 0 && x != 100.0) return x
            }
        }
        return null
    }
}
