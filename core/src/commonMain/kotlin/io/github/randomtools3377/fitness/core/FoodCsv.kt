package io.github.randomtools3377.fitness.core

/**
 * Reads the user's own food list from a spreadsheet saved as CSV.
 *
 * The first row names the columns; the order doesn't matter and only "name" and an energy
 * column are needed. Columns (names are matched loosely, in English):
 *
 *   name, brand, per (100 g, 100 ml or serving), serving size, serving name,
 *   kcal or kJ, protein, carbs, fat, portions ("1 slice=30; 1 cup=240")
 *
 * Commas or semicolons separate the cells (whichever the header uses). With semicolons, a decimal
 * comma ("12,5") is read too, as European spreadsheets save it. Cells can be in double quotes.
 */
object FoodCsv {
    /** A row that couldn't be read: its line number in the file (1 is the header) and why. */
    data class Problem(val line: Int, val reason: String)

    data class Result(val foods: List<Food>, val problems: List<Problem>) {
        /** In plain words: "12 foods ready, 2 rows skipped". */
        fun summary(): String {
            val f = if (foods.size == 1) "1 food" else "${foods.size} foods"
            return if (problems.isEmpty()) "$f ready" else "$f ready, ${problems.size} ${if (problems.size == 1) "row" else "rows"} skipped"
        }
    }

    /** A file to fill in, with two examples. */
    val TEMPLATE: String = listOf(
        "name,brand,per,serving size,serving name,kcal,protein,carbs,fat,portions,fibre,sugar,saturated fat,salt",
        "Rolled oats,Mill & Co,100 g,,,372,13.5,58.7,7,1 bowl=40,,,,",
        "Protein bar,Snack Co,serving,45,1 bar,190,20,15,6,,,,,",
        "Orange juice,,100 ml,,,45,0.7,10,0.2,1 glass=200,,,,",
    ).joinToString("\n") + "\n"

    private enum class Col { NAME, BRAND, PER, SERVING_SIZE, SERVING_NAME, KCAL, KJ, PROTEIN, CARBS, FAT, PORTIONS, FIBRE, SUGAR, SAT_FAT, SALT, SODIUM }

    private fun column(header: String): Col? {
        val h = header.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        return when {
            h == "name" || h == "food" || h == "food name" || h == "description" -> Col.NAME
            // Before carbs and fat, as "carbs of which sugars" and "fat saturates" start the same way.
            "sugar" in h -> Col.SUGAR
            "saturate" in h || h == "sat fat" -> Col.SAT_FAT
            h.startsWith("fibre") || h.startsWith("fiber") -> Col.FIBRE
            h == "salt" || h.startsWith("salt ") -> Col.SALT
            h.startsWith("sodium") -> Col.SODIUM
            h == "brand" || h == "maker" -> Col.BRAND
            h == "per" || h == "basis" || h == "values per" || h == "per 100" -> Col.PER
            h.startsWith("serving size") || h == "serving g" || h == "serving ml" || h == "serving" -> Col.SERVING_SIZE
            h.startsWith("serving name") || h == "serving called" || h == "serving label" -> Col.SERVING_NAME
            h == "kj" || h.startsWith("energy kj") || h == "kilojoules" -> Col.KJ
            h == "kcal" || h == "calories" || h == "energy" || h.startsWith("energy kcal") || h == "cal" -> Col.KCAL
            h.startsWith("protein") -> Col.PROTEIN
            h.startsWith("carb") -> Col.CARBS
            h == "fat" || h.startsWith("fat ") || h.startsWith("total fat") -> Col.FAT
            h.startsWith("portion") -> Col.PORTIONS
            else -> null
        }
    }

    /** Splits one line into cells, honouring double quotes ("a, b" and "" inside quotes). */
    internal fun cells(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == sep -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out.map { it.trim() }
    }

    private fun number(text: String, decimalComma: Boolean): Double? {
        var t = text.trim().replace(" ", "").replace(" ", "")
        if (t.isEmpty()) return null
        if (decimalComma) t = t.replace(',', '.')
        val x = t.toDoubleOrNull() ?: return null
        return if (x.isNaN() || x.isInfinite()) null else x
    }

    fun parse(text: String): Result {
        val lines = text.removePrefix("﻿").lines()
        val headerIndex = lines.indexOfFirst { it.isNotBlank() }
        if (headerIndex < 0) return Result(emptyList(), listOf(Problem(1, "The file is empty.")))
        val headerLine = lines[headerIndex]
        val sep = if (headerLine.count { it == ';' } > headerLine.count { it == ',' }) ';' else if (headerLine.count { it == '\t' } > headerLine.count { it == ',' }) '\t' else ','
        val decimalComma = sep != ','
        val columns = cells(headerLine, sep).map { column(it) }
        if (Col.NAME !in columns) {
            return Result(emptyList(), listOf(Problem(headerIndex + 1, "The first row needs a column called \"name\". Start from the template.")))
        }
        if (Col.KCAL !in columns && Col.KJ !in columns) {
            return Result(emptyList(), listOf(Problem(headerIndex + 1, "The first row needs a column called \"kcal\" or \"kJ\". Start from the template.")))
        }
        val foods = mutableListOf<Food>()
        val problems = mutableListOf<Problem>()
        val seen = mutableSetOf<String>()
        for (i in headerIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val lineNo = i + 1
            val row = cells(line, sep)
            fun cell(c: Col): String = columns.indexOf(c).let { if (it >= 0 && it < row.size) row[it] else "" }
            fun num(c: Col): Double? = number(cell(c), decimalComma)

            val name = cell(Col.NAME)
            if (name.isBlank()) { problems += Problem(lineNo, "No name."); continue }
            val energy = num(Col.KCAL) ?: num(Col.KJ)?.let { it / Units.KJ_PER_KCAL }
            if (energy == null) { problems += Problem(lineNo, "$name: no calories (kcal or kJ)."); continue }
            val per = cell(Col.PER).lowercase().replace(" ", "")
            val unit: BaseUnit
            val servingSize: Double?
            when {
                per.isEmpty() || per == "100g" || per == "g" || per == "100" -> { unit = BaseUnit.GRAM; servingSize = null }
                per == "100ml" || per == "ml" -> { unit = BaseUnit.MILLILITRE; servingSize = null }
                per.startsWith("serving") || per.startsWith("portion") -> {
                    val size = number(cell(Col.SERVING_SIZE).replace(Regex("[A-Za-z]"), ""), decimalComma)
                    if (size == null || size <= 0) { problems += Problem(lineNo, "$name: per serving, but no serving size."); continue }
                    unit = if (cell(Col.SERVING_SIZE).lowercase().contains("ml")) BaseUnit.MILLILITRE else BaseUnit.GRAM
                    servingSize = size
                }
                else -> { problems += Problem(lineNo, "$name: \"per\" should be 100 g, 100 ml or serving."); continue }
            }
            val typed = Nutrients(
                energy, num(Col.PROTEIN) ?: 0.0, num(Col.CARBS) ?: 0.0, num(Col.FAT) ?: 0.0,
                fibre = num(Col.FIBRE), sugar = num(Col.SUGAR), satFat = num(Col.SAT_FAT),
                // Sodium in a spreadsheet is taken to be in mg, as US labels give it.
                salt = num(Col.SALT) ?: num(Col.SODIUM)?.let { Nutrients.saltFromSodiumMg(it) },
            )
            val per100 = if (servingSize == null) typed else Label.per100(typed, servingSize)
            val label = Label.problems(name, per100, unit)
            if (label.isNotEmpty()) { problems += Problem(lineNo, "$name: ${label.first()}"); continue }

            val portions = mutableListOf<Portion>()
            var bad = false
            if (servingSize != null) portions += Portion(cell(Col.SERVING_NAME).ifBlank { "1 serving" }, servingSize)
            for (part in cell(Col.PORTIONS).split(';', '|').map { it.trim() }.filter { it.isNotEmpty() }) {
                val at = part.lastIndexOf('=')
                val size = if (at > 0) number(part.substring(at + 1).replace("g", "").replace("ml", ""), true) else null
                if (at <= 0 || size == null || size <= 0) { bad = true; break }
                val p = Portion(part.substring(0, at).trim(), size)
                if (portions.none { it.label == p.label }) portions += p
            }
            if (bad) { problems += Problem(lineNo, "$name: portions should look like \"1 slice=30; 1 cup=240\"."); continue }

            val brand = cell(Col.BRAND)
            val key = name.lowercase() + "|" + brand.lowercase()
            if (!seen.add(key)) { problems += Problem(lineNo, "$name: listed twice in the file."); continue }
            foods += Food(name = name, brand = brand, unit = unit, per100 = per100, portions = portions)
        }
        return Result(foods, problems)
    }

    /**
     * The foods from [result] that aren't already among [existing] (same name and brand,
     * ignoring case), and a problem for each one left out.
     */
    fun withoutExisting(result: Result, existing: List<Food>): Result {
        val have = existing.filter { !it.hidden }.map { it.name.lowercase() + "|" + it.brand.lowercase() }.toSet()
        val (dupes, fresh) = result.foods.partition { (it.name.lowercase() + "|" + it.brand.lowercase()) in have }
        return Result(fresh, result.problems + dupes.map { Problem(0, "${it.displayName()}: already in your foods.") })
    }
}
