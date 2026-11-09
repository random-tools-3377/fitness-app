package io.github.randomtools3377.fitness.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * The user's own photo behind the diary (0.17.0, copied from the checklist app), kept in the app's
 * own storage and made smaller. Like progress photos, it stays on the phone and out of the backup.
 */
object BackgroundPhoto {
    private const val MAX_SIDE = 1600
    private const val QUALITY = 85

    fun file(context: Context) = File(context.noBackupFilesDir, "background.jpg")

    fun delete(context: Context) {
        file(context).delete()
    }

    /** Copies a picked photo in, at most 1600 pixels on its longer side. False if it isn't a photo. */
    fun add(context: Context, uri: Uri): Boolean {
        val bitmap = try {
            decode(context, uri)
        } catch (e: Exception) {
            null
        } ?: return false
        val target = file(context)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "background.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        return tmp.renameTo(target)
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Turns the photo the right way up, as the camera recorded it.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longer = maxOf(info.size.width, info.size.height)
                if (longer > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longer
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        return if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
    }
}
