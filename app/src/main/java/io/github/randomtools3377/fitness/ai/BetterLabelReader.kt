package io.github.randomtools3377.fitness.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import io.github.randomtools3377.fitness.core.OcrMath
import java.io.File
import java.nio.FloatBuffer

/**
 * Reads the text on a label photo with PaddleOCR's text-finding and text-reading models (0.16.0),
 * run on the phone with ONNX Runtime. Only used when the user has turned on better label reading
 * and opened its model file; the app's own Tesseract reader is used otherwise.
 */
object BetterLabelReader {
    fun available(context: Context) = "labels" in AiModels.installed(context)

    /** The label's text, line by line. */
    fun read(context: Context, bitmap: Bitmap): String {
        val dir = AiModels.dir(context, "labels")
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        val boxes = env.createSession(File(dir, "det.onnx").path, options).use { det ->
            val (dw, dh) = OcrMath.detSize(w, h)
            val map = tensor(env, OcrMath.detInput(px, w, h, dw, dh), 1, 3, dh.toLong(), dw.toLong()).use { x ->
                det.run(mapOf(det.inputNames.first() to x)).use { r -> floats(r[0] as OnnxTensor) }
            }
            OcrMath.boxes(map, dw, dh, w, h)
        }
        if (boxes.isEmpty()) return ""
        val alphabet = OcrMath.alphabet(File(dir, "keys.txt").readText())
        val allowed = OcrMath.allowed(alphabet)
        val words = env.createSession(File(dir, "rec.onnx").path, options).use { rec ->
            boxes.map { box ->
                val (input, width) = OcrMath.recInput(px, w, h, box)
                tensor(env, input, 1, 3, OcrMath.REC_HEIGHT.toLong(), width.toLong()).use { x ->
                    rec.run(mapOf(rec.inputNames.first() to x)).use { r ->
                        val t = r[0] as OnnxTensor
                        val shape = t.info.shape
                        box to OcrMath.decode(floats(t), shape[1].toInt(), shape[2].toInt(), alphabet, allowed)
                    }
                }
            }
        }
        return OcrMath.lines(words)
    }

    private fun tensor(env: OrtEnvironment, data: FloatArray, vararg shape: Long) = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    private fun floats(t: OnnxTensor): FloatArray {
        val b = t.floatBuffer
        return FloatArray(b.remaining()).also { b.get(it) }
    }
}
