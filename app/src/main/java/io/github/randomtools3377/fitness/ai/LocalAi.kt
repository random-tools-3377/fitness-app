package io.github.randomtools3377.fitness.ai

import android.app.ActivityManager
import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The optional on-phone AI (0.18.0 onwards): asks one of the model files the user opened, on the
 * phone, with llama.cpp. One model is kept loaded at a time, as each needs a lot of memory. The
 * prompts are the app's own, in assets/ai, the same ones tools/ai-check tries on a build machine.
 */
object LocalAi {
    class Unavailable(message: String) : Exception(message)

    private val lock = Mutex()
    private var started: Boolean? = null
    private var loadedId: String? = null
    private var handle = 0L

    /** Whether this phone can run the AI at all: a 64-bit ARM phone. */
    fun supported(): Boolean = android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a")

    /** Whether the phone has little memory for it (under about 4 GB), to say it may be slow or fail. */
    fun lowMemory(context: Context): Boolean {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return info.totalMem in 1 until 3_600_000_000L
    }

    private fun start(context: Context) {
        if (started == true) return
        if (started == false || !supported()) throw Unavailable("The AI parts can't run on this phone.")
        started = try {
            System.loadLibrary("fitness_ai")
            NativeAi.init(context.applicationInfo.nativeLibraryDir)
            true
        } catch (e: Throwable) {
            false
        }
        if (started != true) throw Unavailable("The AI parts can't run on this phone.")
    }

    private fun asset(context: Context, name: String): String =
        context.assets.open("ai/$name").bufferedReader().use { it.readText() }

    /** Loads model file [id] if it isn't the one loaded. */
    private fun use(context: Context, id: String) {
        if (loadedId == id && handle != 0L) return
        if (handle != 0L) NativeAi.unload(handle)
        handle = 0L
        loadedId = null
        val dir = AiModels.dir(context, id)
        val model = File(dir, "model.gguf")
        if (!model.isFile) throw Unavailable("Open the AI file first (Settings → Features).")
        val mmproj = File(dir, "mmproj.gguf").takeIf { it.isFile }?.path.orEmpty()
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        handle = NativeAi.load(model.path, mmproj, 4096, threads)
        if (handle == 0L) throw Unavailable("The AI file couldn't be loaded. There may not be enough free memory; close other apps and try again.")
        loadedId = id
    }

    /**
     * Asks model [id] with the prompt files [system] and [user] from assets/ai ("{text}" in the
     * user file is replaced by [text]), held to [grammar] if given, and with a photo or recording.
     */
    suspend fun ask(
        context: Context,
        id: String,
        system: String?,
        user: String?,
        text: String,
        grammar: String? = null,
        maxTokens: Int = 300,
        media: ByteArray? = null,
        prefix: String? = null,
        examples: String? = null,
    ): String = withContext(Dispatchers.Default) {
        lock.withLock {
            start(context)
            use(context, id)
            val sys = system?.let { asset(context, it) }.orEmpty()
            val usr = user?.let { asset(context, it).replace("{text}", text) } ?: text
            val prompt = NativeAi.format(handle, sys, usr, examples?.let { asset(context, it) }.orEmpty()) + (prefix?.let { asset(context, it) }.orEmpty())
            NativeAi.answer(handle, prompt, grammar?.let { asset(context, it) }.orEmpty(), maxTokens, media)
        }
    }

    /** Stops an answer being written; what was written so far comes back. */
    fun stop() {
        if (started == true) NativeAi.stop()
    }

    /** Frees the memory a model takes, such as when the app goes to the background. */
    suspend fun release() = lock.withLock {
        if (handle != 0L) NativeAi.unload(handle)
        handle = 0L
        loadedId = null
    }
}
