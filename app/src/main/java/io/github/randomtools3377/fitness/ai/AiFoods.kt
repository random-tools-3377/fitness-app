package io.github.randomtools3377.fitness.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import io.github.randomtools3377.fitness.core.AiText
import io.github.randomtools3377.fitness.core.Amount
import io.github.randomtools3377.fitness.core.Food
import io.github.randomtools3377.fitness.core.Usage
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The steps from what the user typed, said or photographed to foods they check (0.18.0 onwards):
 * the model names the foods, and the app's own food data, with the user's own foods and the ones
 * they use first, gives everything else.
 */
object AiFoods {
    /** One food found, for the user to check: what was said, the food picked for it and the others that might fit. */
    data class Row(
        val said: String,
        val candidates: List<Food>,
        val food: Food?,
        val amount: Amount,
        val guessed: Boolean,
        val include: Boolean = true,
    )

    /** The foods to look among: the user's own and the built-in ones. */
    data class Pool(val foods: List<Food>, val usages: Map<String, Usage>, val favourites: Set<String>, val now: Long)

    private fun rows(items: List<AiText.Item>, pool: Pool): List<Row> = items.map { item ->
        val candidates = AiText.candidates(item.food, pool.foods, pool.usages, pool.now, pool.favourites)
        val food = candidates.firstOrNull()
        val fit = food?.let { AiText.amountFor(it, item.amount, item.unit) }
        Row(
            said = listOf(io.github.randomtools3377.fitness.core.formatAmount(item.amount), item.unit, item.food).joinToString(" "),
            candidates = candidates,
            food = food,
            amount = fit?.amount ?: Amount(item.amount),
            guessed = fit?.guessed ?: true,
            include = food != null,
        )
    }

    /** Another food picked for a row: the amount is worked out again for it. */
    fun choose(row: Row, food: Food): Row {
        val parts = row.said.split(' ', limit = 3)
        val amount = parts.getOrNull(0)?.toDoubleOrNull() ?: 1.0
        val unit = parts.getOrNull(1) ?: "serving"
        val fit = AiText.amountFor(food, amount, unit)
        return row.copy(food = food, amount = fit.amount, guessed = fit.guessed, include = true)
    }

    /** "two eggs and a slice of toast" as foods. */
    suspend fun fromText(context: Context, text: String, pool: Pool): List<Row> {
        val answer = LocalAi.ask(context, "text", "meal-system.txt", null, text.trim(), "meal.gbnf", 300, prefix = "qwen3-prefix.txt")
        return withContext(Dispatchers.Default) { rows(AiText.items(answer), pool) }
    }

    /** A pasted recipe: its name, servings and method, and its ingredients as foods. */
    suspend fun fromRecipe(context: Context, text: String, pool: Pool): Pair<AiText.RecipeParts, List<Row>> {
        val parts = AiText.recipeParts(text)
        val answer = LocalAi.ask(context, "text", "recipe-system.txt", null, text.trim().take(6000), "meal.gbnf", 600, prefix = "qwen3-prefix.txt")
        return parts to withContext(Dispatchers.Default) { rows(AiText.items(answer), pool) }
    }

    /** The foods on a photo of a plate, each as one serving for the user to change. */
    suspend fun fromPlate(context: Context, photo: Uri, pool: Pool): List<Row> {
        val jpeg = withContext(Dispatchers.IO) { smallJpeg(context, photo) } ?: throw LocalAi.Unavailable("That photo couldn't be read.")
        val answer = LocalAi.ask(context, "plate", "plate-system.txt", "plate-user.txt", "", "plate.gbnf", 80, media = jpeg)
        return withContext(Dispatchers.Default) { rows(AiText.names(answer).map { AiText.Item(1.0, "serving", it) }, pool) }
    }

    /** What was said in a recording (16 kHz mono WAV), as text. */
    suspend fun hear(context: Context, wav: ByteArray): String =
        AiText.heard(LocalAi.ask(context, "speech", null, "speech-user.txt", "", null, 150, media = wav))

    /** A few sentences about the week, from its numbers. */
    suspend fun weekSummary(context: Context, facts: String): String =
        LocalAi.ask(context, "text", "summary-system.txt", null, facts, null, 220, prefix = "qwen3-prefix.txt").trim()

    /** The photo at most 768 pixels on its longer side, as a JPEG, the right way up. */
    private fun smallJpeg(context: Context, uri: Uri): ByteArray? = runCatching {
        val max = 768
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val side = maxOf(info.size.width, info.size.height)
                if (side > max) {
                    val s = max.toDouble() / side
                    decoder.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                }
            }
        } else {
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            val s = max.toFloat() / maxOf(raw.width, raw.height)
            if (s < 1f) Bitmap.createScaledBitmap(raw, (raw.width * s).toInt(), (raw.height * s).toInt(), true) else raw
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }.getOrNull()
}
