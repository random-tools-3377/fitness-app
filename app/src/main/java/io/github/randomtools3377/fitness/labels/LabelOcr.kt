package io.github.randomtools3377.fitness.labels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.googlecode.tesseract.android.TessBaseAPI
import io.github.randomtools3377.fitness.ai.BetterLabelReader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the text on a photo of a food label, on the phone, with Tesseract (Apache-2.0) and its
 * English data, which is bundled in the app's assets. Nothing leaves the phone.
 */
object LabelOcr {
    private const val LANGUAGE = "eng"
    private const val MAX_SIDE = 2200

    /** A file in the app's cache for the camera app to save a label photo into. */
    fun newPhotoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "labels").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "label-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Deletes label photos the app took. */
    fun clear(context: Context) {
        File(context.cacheDir, "labels").listFiles()?.forEach { it.delete() }
    }

    /**
     * The text on the photo at [uri], or "" if none could be read. With [better], and its model
     * file opened, the optional PaddleOCR models read it (0.16.0); if they fail, Tesseract does.
     */
    suspend fun read(context: Context, uri: Uri, better: Boolean = false): String = withContext(Dispatchers.Default) {
        val bitmap = load(context, uri) ?: return@withContext ""
        if (better && BetterLabelReader.available(context)) {
            val text = runCatching { BetterLabelReader.read(context, bitmap) }.getOrDefault("")
            if (text.isNotBlank()) {
                bitmap.recycle()
                return@withContext text
            }
        }
        val dataDir = prepare(context)
        val api = TessBaseAPI()
        try {
            if (!api.init(dataDir.absolutePath, LANGUAGE)) return@withContext ""
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api.setImage(bitmap)
            api.getUTF8Text().orEmpty()
        } finally {
            api.recycle()
            bitmap.recycle()
        }
    }

    /** Copies the bundled language data out of the assets once, as Tesseract reads it from a file. */
    private fun prepare(context: Context): File {
        val base = File(context.noBackupFilesDir, "ocr")
        val data = File(base, "tessdata/$LANGUAGE.traineddata")
        if (!data.exists()) {
            data.parentFile?.mkdirs()
            val part = File(data.path + ".part")
            context.assets.open("tessdata/$LANGUAGE.traineddata").use { input -> part.outputStream().use { input.copyTo(it) } }
            part.renameTo(data)
        }
        return base
    }

    /** The photo, turned the right way up and no bigger than needed. */
    private fun load(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val side = maxOf(info.size.width, info.size.height)
                if (side > MAX_SIDE) {
                    val scale = MAX_SIDE.toDouble() / side
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()?.let { if (it.config != Bitmap.Config.ARGB_8888) it.copy(Bitmap.Config.ARGB_8888, false) else it }
}
