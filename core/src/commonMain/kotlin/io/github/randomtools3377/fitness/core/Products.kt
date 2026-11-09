package io.github.randomtools3377.fitness.core

/** A packaged product from a country file: per 100 g (or 100 ml), with its serving if known. */
data class Product(
    val barcode: String,
    val name: String,
    val brand: String,
    val per100: Nutrients,
    val servingGrams: Double? = null,
    val servingLabel: String = "",
) {
    /** As a food to log. Its key is "b:P" and the barcode, so it shows among recent foods. */
    fun toFood(): Food = Food(
        builtInId = PREFIX + barcode,
        name = name,
        brand = brand,
        unit = BaseUnit.GRAM,
        per100 = per100,
        portions = servingGrams?.let { listOf(Portion(servingLabel.ifBlank { "1 serving" }, it)) } ?: emptyList(),
    )

    companion object {
        const val PREFIX = "P"
    }
}

/** Reading the country files made by tools/build-country-files.py, and barcodes. */
object Products {
    private const val MARK = "# Fitness App country file:"

    /**
     * A barcode as stored: digits only, 13 digits for EAN-13 and UPC-A (with a leading 0), 8 for
     * EAN-8. Null if it isn't a product barcode.
     */
    fun normalize(raw: String): String? {
        var d = raw.filter { it.isDigit() }
        if (d.length == 12) d = "0$d"
        if (d.length == 14 && d.startsWith("0")) d = d.substring(1)
        return if (d.length == 8 || d.length == 13) d else null
    }

    /** Whether a barcode's last digit checks out (EAN-8, EAN-13). */
    fun checkDigitOk(code: String): Boolean {
        if (code.length != 8 && code.length != 13 || code.any { !it.isDigit() }) return false
        val digits = code.map { it - '0' }
        val body = digits.dropLast(1).reversed()
        val sum = body.mapIndexed { i, d -> if (i % 2 == 0) d * 3 else d }.sum()
        return (10 - sum % 10) % 10 == digits.last()
    }

    /** The country a file is for ("France"), from its first line, or null if it isn't a country file. */
    fun countryOf(firstLine: String): String? {
        if (!firstLine.startsWith(MARK)) return null
        return Regex("sold in ([^,.]+)").find(firstLine)?.groupValues?.get(1)?.trim()
    }

    /** One line of a country file, or null for a comment or a line that can't be read. */
    fun parseLine(line: String): Product? {
        if (line.isEmpty() || line.startsWith("#")) return null
        val f = line.split('\t')
        if (f.size < 7) return null
        val code = normalize(f[0]) ?: return null
        val kcal = f[3].toDoubleOrNull() ?: return null
        return Product(
            barcode = code,
            name = f[1],
            brand = f[2],
            per100 = Nutrients(
                kcal, f[4].toDoubleOrNull() ?: 0.0, f[5].toDoubleOrNull() ?: 0.0, f[6].toDoubleOrNull() ?: 0.0,
                fibre = f.getOrNull(9)?.toDoubleOrNull(), sugar = f.getOrNull(10)?.toDoubleOrNull(),
                satFat = f.getOrNull(11)?.toDoubleOrNull(), salt = f.getOrNull(12)?.toDoubleOrNull(),
            ),
            servingGrams = f.getOrNull(7)?.toDoubleOrNull()?.takeIf { it > 0 },
            servingLabel = f.getOrNull(8).orEmpty(),
        )
    }
}
