package io.github.randomtools3377.fitness.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.nio.FloatBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Reads a label drawn on a slight slant with the real PaddleOCR models, the same way the app does,
 * when FITNESS_AI_MODELS names a folder with det.onnx and rec.onnx (CI builds them with
 * tools/build-ai-labels.sh). Without it, there is nothing to run and the test passes.
 */
class OcrModelTest {
    private val label = listOf(
        "NUTRITION INFORMATION",
        "Typical values   Per 100g   Per 40g serving",
        "Energy 1567kJ / 372kcal   627kJ / 149kcal",
        "Fat 7.0g   2.8g",
        "of which saturates 1.2g   0.5g",
        "Carbohydrate 58.7g   23.5g",
        "of which sugars 1.0g   0.4g",
        "Fibre 9.0g   3.6g",
        "Protein 13.5g   5.4g",
        "Salt <0.01g   <0.01g",
    )

    private fun draw(): BufferedImage {
        val img = BufferedImage(1160, 700, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(245, 240, 230)
        g.fillRect(0, 0, img.width, img.height)
        g.transform = AffineTransform.getRotateInstance(Math.toRadians(-2.0), 580.0, 350.0)
        g.color = Color(20, 20, 20)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 34)
        label.forEachIndexed { i, line -> g.drawString(line, 50, 70 + 56 * i) }
        g.dispose()
        return img
    }

    private fun tensor(env: OrtEnvironment, data: FloatArray, vararg shape: Long) = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    @Test
    fun aSlantedLabelIsReadByTheModels() {
        val dir = System.getenv("FITNESS_AI_MODELS")?.let(::File)?.takeIf { File(it, "det.onnx").exists() } ?: return
        val img = draw()
        System.getenv("FITNESS_OCR_DUMP")?.let { javax.imageio.ImageIO.write(img, "png", File(it)) }
        val w = img.width
        val h = img.height
        val px = img.getRGB(0, 0, w, h, null, 0, w)
        val env = OrtEnvironment.getEnvironment()
        val det = env.createSession(File(dir, "det.onnx").path, OrtSession.SessionOptions())
        val rec = env.createSession(File(dir, "rec.onnx").path, OrtSession.SessionOptions())
        val (dw, dh) = OcrMath.detSize(w, h)
        val map = tensor(env, OcrMath.detInput(px, w, h, dw, dh), 1, 3, dh.toLong(), dw.toLong()).use { x ->
            det.run(mapOf("x" to x)).use { r ->
                val t = r[0] as OnnxTensor
                val shape = t.info.shape
                assertEquals(dh.toLong(), shape[2])
                val buf = t.floatBuffer
                FloatArray(buf.remaining()).also { buf.get(it) }
            }
        }
        val boxes = OcrMath.boxes(map, dw, dh, w, h)
        val alphabet = OcrMath.alphabet(File(dir, "keys.txt").readText())
        val allowed = OcrMath.allowed(alphabet)
        val words = boxes.map { box ->
            val (input, width) = OcrMath.recInput(px, w, h, box)
            tensor(env, input, 1, 3, OcrMath.REC_HEIGHT.toLong(), width.toLong()).use { x ->
                rec.run(mapOf("x" to x)).use { r ->
                    val t = r[0] as OnnxTensor
                    val shape = t.info.shape
                    val buf = t.floatBuffer
                    val scores = FloatArray(buf.remaining()).also { buf.get(it) }
                    box to OcrMath.decode(scores, shape[1].toInt(), shape[2].toInt(), alphabet, allowed)
                }
            }
        }
        val text = OcrMath.lines(words)
        println(text)
        assertEquals(label.size, text.lines().size, text)
        val r = assertNotNull(LabelText.read(text), text)
        assertEquals(372.0, r.kcal!!, 0.01)
        assertEquals(7.0, r.fat!!, 0.01)
        assertEquals(1.2, r.satFat!!, 0.01)
        assertEquals(58.7, r.carbs!!, 0.01)
        assertEquals(1.0, r.sugar!!, 0.01)
        assertEquals(9.0, r.fibre!!, 0.01)
        assertEquals(13.5, r.protein!!, 0.01)
        assertEquals(0.0, r.salt!!, 0.01)
    }
}
