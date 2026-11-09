package io.github.randomtools3377.fitness.ai

/** The app's AI code in C++ (app/src/main/cpp), with llama.cpp. Only [LocalAi] calls it. */
internal object NativeAi {
    external fun init(libDir: String)
    external fun load(model: String, mmproj: String, nCtx: Int, nThreads: Int): Long
    external fun unload(handle: Long)
    external fun format(handle: Long, system: String, user: String): String
    external fun answer(handle: Long, prompt: String, grammar: String, maxTokens: Int, media: ByteArray?): String
    external fun stop()
}
