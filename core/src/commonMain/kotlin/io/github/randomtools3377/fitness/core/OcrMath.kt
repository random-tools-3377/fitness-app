package io.github.randomtools3377.fitness.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The maths around PaddleOCR's text-finding and text-reading models (0.16.0): getting a photo into
 * the shape the models want, turning the text-finding model's map into boxes around lines of text,
 * cutting each line out straight, and turning the reading model's output into letters. The models
 * themselves run in the app; this part is plain Kotlin so it is tested on any machine.
 *
 * Pixels are ARGB ints, row by row, as Android's Bitmap gives them.
 */
object OcrMath {
    const val REC_HEIGHT = 48
    private const val REC_MIN_WIDTH = 320
    private const val DET_MIN_SIDE = 736
    private const val DET_MAX_SIDE = 2000
    private const val THRESH = 0.3f
    private const val BOX_THRESH = 0.5f
    private const val UNCLIP = 1.6
    private const val MAX_BOXES = 1000

    /** A line of text: a rectangle, maybe slightly turned, in the photo's own pixels. */
    data class Box(
        /** The direction along the line, a unit vector. */
        val ux: Double, val uy: Double,
        /** Across the line, a unit vector pointing down the photo. */
        val vx: Double, val vy: Double,
        val u0: Double, val u1: Double, val v0: Double, val v1: Double,
    ) {
        val length: Double get() = u1 - u0
        val height: Double get() = v1 - v0
        fun x(u: Double, v: Double) = u * ux + v * vx
        fun y(u: Double, v: Double) = u * uy + v * vy
        val centreY: Double get() = y((u0 + u1) / 2, (v0 + v1) / 2)
        val left: Double get() = min(x(u0, v0), x(u0, v1))
    }

    /** The size the text-finding model gets: the short side at least 736, the long side at most 2000, both multiples of 32. */
    fun detSize(w: Int, h: Int): Pair<Int, Int> {
        var s = if (min(w, h) < DET_MIN_SIDE) DET_MIN_SIDE.toDouble() / min(w, h) else 1.0
        if (max(w, h) * s > DET_MAX_SIDE) s = DET_MAX_SIDE.toDouble() / max(w, h)
        fun r(x: Int) = max(32, (x * s / 32).roundToInt() * 32)
        return r(w) to r(h)
    }

    /** The photo resized to [dw] × [dh], as the models want it: blue, green, red planes, from -1 to 1. */
    fun detInput(px: IntArray, w: Int, h: Int, dw: Int, dh: Int): FloatArray {
        val out = FloatArray(3 * dw * dh)
        val plane = dw * dh
        val sx = w.toDouble() / dw
        val sy = h.toDouble() / dh
        val rgb = FloatArray(3)
        for (y in 0 until dh) for (x in 0 until dw) {
            sample(px, w, h, (x + 0.5) * sx, (y + 0.5) * sy, rgb)
            val i = y * dw + x
            out[i] = norm(rgb[2]); out[plane + i] = norm(rgb[1]); out[2 * plane + i] = norm(rgb[0])
        }
        return out
    }

    private fun norm(c: Float) = (c / 255f - 0.5f) / 0.5f

    /** Bilinear sample at ([fx], [fy]), pixel centres at .5, into [rgb]. */
    private fun sample(px: IntArray, w: Int, h: Int, fx: Double, fy: Double, rgb: FloatArray) {
        val x = (fx - 0.5).coerceIn(0.0, w - 1.0)
        val y = (fy - 0.5).coerceIn(0.0, h - 1.0)
        val x0 = x.toInt(); val y0 = y.toInt()
        val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
        val ax = (x - x0).toFloat(); val ay = (y - y0).toFloat()
        val p00 = px[y0 * w + x0]; val p10 = px[y0 * w + x1]; val p01 = px[y1 * w + x0]; val p11 = px[y1 * w + x1]
        for (c in 0..2) {
            val shift = 16 - 8 * c
            fun ch(p: Int) = ((p shr shift) and 0xFF).toFloat()
            rgb[c] = (ch(p00) * (1 - ax) + ch(p10) * ax) * (1 - ay) + (ch(p01) * (1 - ax) + ch(p11) * ax) * ay
        }
    }

    /**
     * The boxes around lines of text in the text-finding model's map ([mw] × [mh], each a chance
     * from 0 to 1), scaled to a photo of [w] × [h].
     */
    fun boxes(map: FloatArray, mw: Int, mh: Int, w: Int, h: Int): List<Box> {
        // Above the threshold, thickened by one pixel to the right and down, as PaddleOCR does.
        val on = BooleanArray(mw * mh)
        for (y in 0 until mh) for (x in 0 until mw) {
            val i = y * mw + x
            on[i] = map[i] > THRESH || (x > 0 && map[i - 1] > THRESH) || (y > 0 && map[i - mw] > THRESH) ||
                (x > 0 && y > 0 && map[i - mw - 1] > THRESH)
        }
        val seen = BooleanArray(mw * mh)
        val stack = IntArray(mw * mh)
        val out = ArrayList<Box>()
        val sx = w.toDouble() / mw
        val sy = h.toDouble() / mh
        for (start in on.indices) {
            if (!on[start] || seen[start]) continue
            // One patch of text, by flood fill; kept as each row's leftmost and rightmost pixel.
            val rowMin = HashMap<Int, Int>()
            val rowMax = HashMap<Int, Int>()
            var count = 0
            var score = 0.0
            var top = 0
            stack[top++] = start
            seen[start] = true
            while (top > 0) {
                val i = stack[--top]
                val x = i % mw
                val y = i / mw
                count++
                score += map[i]
                rowMin[y] = min(rowMin[y] ?: x, x)
                rowMax[y] = max(rowMax[y] ?: x, x)
                if (x + 1 < mw) push(i + 1, on, seen, stack, top)?.let { top = it }
                if (x > 0) push(i - 1, on, seen, stack, top)?.let { top = it }
                if (y + 1 < mh) push(i + mw, on, seen, stack, top)?.let { top = it }
                if (y > 0) push(i - mw, on, seen, stack, top)?.let { top = it }
            }
            if (count < 10 || score / count < BOX_THRESH) continue
            val corners = ArrayList<Pair<Double, Double>>(rowMin.size * 4)
            for ((y, x0) in rowMin) {
                val x1 = rowMax.getValue(y) + 1
                corners += x0.toDouble() to y.toDouble(); corners += x0.toDouble() to y + 1.0
                corners += x1.toDouble() to y.toDouble(); corners += x1.toDouble() to y + 1.0
            }
            val box = minAreaBox(hull(corners)) ?: continue
            if (min(box.length, box.height) < 3) continue
            val d = box.length * box.height * UNCLIP / (2 * (box.length + box.height))
            out += scale(box.copy(u0 = box.u0 - d, u1 = box.u1 + d, v0 = box.v0 - d, v1 = box.v1 + d), sx, sy)
            if (out.size >= MAX_BOXES) break
        }
        return out
    }

    private fun push(j: Int, on: BooleanArray, seen: BooleanArray, stack: IntArray, top: Int): Int? {
        if (!on[j] || seen[j]) return null
        seen[j] = true
        stack[top] = j
        return top + 1
    }

    /** The convex hull, by the monotone chain. */
    fun hull(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val p = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (p.size < 3) return p
        fun cross(o: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Double, Double>>()
        for (q in p) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), q) <= 0) lower.removeAt(lower.size - 1)
            lower += q
        }
        val upper = ArrayList<Pair<Double, Double>>()
        for (q in p.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), q) <= 0) upper.removeAt(upper.size - 1)
            upper += q
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** The smallest rectangle around a convex hull, turned along the line of text. */
    fun minAreaBox(hull: List<Pair<Double, Double>>): Box? {
        if (hull.size < 3) return null
        var best: Box? = null
        var bestArea = Double.MAX_VALUE
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            val ex = b.first - a.first
            val ey = b.second - a.second
            val len = sqrt(ex * ex + ey * ey)
            if (len == 0.0) continue
            val ux = ex / len
            val uy = ey / len
            val vx = -uy
            val vy = ux
            var u0 = Double.MAX_VALUE; var u1 = -Double.MAX_VALUE; var v0 = Double.MAX_VALUE; var v1 = -Double.MAX_VALUE
            for (q in hull) {
                val u = q.first * ux + q.second * uy
                val v = q.first * vx + q.second * vy
                u0 = min(u0, u); u1 = max(u1, u); v0 = min(v0, v); v1 = max(v1, v)
            }
            val area = (u1 - u0) * (v1 - v0)
            if (area < bestArea) {
                bestArea = area
                best = Box(ux, uy, vx, vy, u0, u1, v0, v1)
            }
        }
        return best?.let(::upright)
    }

    /** The same rectangle with u along its long side, pointing right, and v pointing down. */
    private fun upright(b: Box): Box {
        var r = b
        if (r.length < r.height) r = Box(r.vx, r.vy, r.ux, r.uy, r.v0, r.v1, r.u0, r.u1)
        if (r.ux < 0) r = r.copy(ux = -r.ux, uy = -r.uy, u0 = -r.u1, u1 = -r.u0)
        if (r.vy < 0) r = r.copy(vx = -r.vx, vy = -r.vy, v0 = -r.v1, v1 = -r.v0)
        return r
    }

    /** A box in the map's pixels, in the photo's pixels instead. Only even scales keep it a rectangle, so it is rebuilt from its corners. */
    private fun scale(b: Box, sx: Double, sy: Double): Box {
        val cx = { u: Double, v: Double -> b.x(u, v) * sx }
        val cy = { u: Double, v: Double -> b.y(u, v) * sy }
        val corners = listOf(cx(b.u0, b.v0) to cy(b.u0, b.v0), cx(b.u1, b.v0) to cy(b.u1, b.v0), cx(b.u1, b.v1) to cy(b.u1, b.v1), cx(b.u0, b.v1) to cy(b.u0, b.v1))
        return minAreaBox(corners) ?: b
    }

    /** The line in [box] cut out straight, 48 pixels high, for the reading model, and its width before padding. */
    fun recInput(px: IntArray, w: Int, h: Int, box: Box): Pair<FloatArray, Int> {
        val cw = max(1, ceil(REC_HEIGHT * box.length / box.height).toInt())
        val width = max(cw, REC_MIN_WIDTH)
        val plane = REC_HEIGHT * width
        val out = FloatArray(3 * plane)
        val rgb = FloatArray(3)
        for (y in 0 until REC_HEIGHT) {
            val v = box.v0 + (y + 0.5) / REC_HEIGHT * box.height
            for (x in 0 until cw) {
                val u = box.u0 + (x + 0.5) / cw * box.length
                sample(px, w, h, box.x(u, v), box.y(u, v), rgb)
                val i = y * width + x
                out[i] = norm(rgb[2]); out[plane + i] = norm(rgb[1]); out[2 * plane + i] = norm(rgb[0])
            }
        }
        return out to width
    }

    /**
     * The reading model's letters: the model's own list, read from the model file, with the
     * "nothing" mark first and a space last.
     */
    fun alphabet(fromModel: String): List<String> = listOf("") + fromModel.split('\n') + listOf(" ")

    /** Which letters may be read: Latin letters, digits, signs and a few symbols. Labels here are in European languages. */
    fun allowed(alphabet: List<String>): BooleanArray = BooleanArray(alphabet.size) { i ->
        val c = alphabet[i]
        i == 0 || c.length == 1 && c[0].let { ch -> ch.code < 128 || ch in "µ°%€£" || (ch.isLetter() && ch.code < 0x250) }
    }

    /** The reading model's output ([steps] × [classes] scores) as text: the best allowed letter at each step, repeats and "nothing" dropped. */
    fun decode(scores: FloatArray, steps: Int, classes: Int, alphabet: List<String>, allowed: BooleanArray): String {
        val sb = StringBuilder()
        var prev = -1
        for (t in 0 until steps) {
            var best = 0
            var bestScore = -Float.MAX_VALUE
            val base = t * classes
            for (c in 0 until min(classes, allowed.size)) {
                if (!allowed[c]) continue
                val s = scores[base + c]
                if (s > bestScore) { bestScore = s; best = c }
            }
            if (best != prev && best != 0) sb.append(alphabet[best])
            prev = best
        }
        return sb.toString().trim()
    }

    /** Words read from boxes, put back into lines: top to bottom, and left to right within a line. */
    fun lines(words: List<Pair<Box, String>>): String {
        val sorted = words.filter { it.second.isNotBlank() }.sortedBy { it.first.centreY }
        val lines = ArrayList<Pair<Double, MutableList<Pair<Box, String>>>>()
        for (w in sorted) {
            val last = lines.lastOrNull()
            if (last != null && kotlin.math.abs(last.first - w.first.centreY) < w.first.height * 0.5) last.second += w
            else lines += w.first.centreY to mutableListOf(w)
        }
        return lines.joinToString("\n") { (_, ws) -> ws.sortedBy { it.first.left }.joinToString(" ") { it.second } }
    }
}
