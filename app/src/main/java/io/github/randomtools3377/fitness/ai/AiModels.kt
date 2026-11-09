package io.github.randomtools3377.fitness.ai

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The optional on-phone AI model files (0.16.0). Each is a zip file from the "AI models" Release,
 * opened through the system file dialog like a country file, and kept in the app's own storage,
 * out of backups. Nothing is downloaded by the app itself.
 */
object AiModels {
    /** The model files the app knows, by id, with the files each must contain. */
    val KNOWN: Map<String, Set<String>> = mapOf(
        "labels" to setOf("manifest.json", "det.onnx", "rec.onnx", "keys.txt"),
    )

    /** The file name on the Releases page, to tell the user which one to get. */
    fun fileName(id: String) = "fitness-ai-$id.zip"

    class NotAModelFile(message: String) : Exception(message)

    private fun root(context: Context) = File(context.noBackupFilesDir, "ai")

    fun dir(context: Context, id: String) = File(root(context), id)

    fun installed(context: Context): Set<String> =
        KNOWN.filter { (id, files) -> files.all { File(dir(context, id), it).isFile } }.keys

    /** Unpacks a model file; returns its id. Only the expected files are kept, and a file that isn't one leaves nothing behind. */
    suspend fun install(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val incoming = File(root(context), ".incoming").apply { deleteRecursively(); mkdirs() }
        try {
            val allowed = KNOWN.values.flatten().toSet() + setOf("LICENSE.txt", "MODEL_LICENSES.md", "README.txt")
            ZipInputStream(context.contentResolver.openInputStream(uri) ?: throw NotAModelFile("That file could not be opened.")).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory || e.name !in allowed) continue
                    File(incoming, e.name).outputStream().use { zip.copyTo(it) }
                }
            }
            val manifest = File(incoming, "manifest.json").takeIf { it.isFile }
                ?: throw NotAModelFile("That isn't a Fitness App AI file. Get one from the \"AI models\" Release.")
            val id = runCatching { org.json.JSONObject(manifest.readText()).optString("id") }.getOrNull()
            val need = KNOWN[id] ?: throw NotAModelFile("That AI file is for a newer version of the app. Update the app first.")
            if (!need.all { File(incoming, it).isFile }) throw NotAModelFile("That AI file is incomplete. Download it again.")
            val target = dir(context, id!!)
            target.deleteRecursively()
            if (!incoming.renameTo(target)) throw NotAModelFile("There isn't room for it on the phone.")
            id
        } finally {
            incoming.deleteRecursively()
        }
    }

    fun remove(context: Context, id: String) {
        dir(context, id).deleteRecursively()
    }
}
