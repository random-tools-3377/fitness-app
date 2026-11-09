package io.github.randomtools3377.fitness.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Records what the user says for "Say a meal" (0.19.0): 16 kHz mono, at most 30 seconds, kept
 * in memory only and turned into a WAV for the speech model. Nothing is saved.
 */
class Recorder {
    @Volatile private var recording = false

    fun stop() {
        recording = false
    }

    /** Records until [stop] or 30 seconds; needs the microphone permission. */
    @SuppressLint("MissingPermission")
    suspend fun record(): ByteArray = withContext(Dispatchers.IO) {
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
        val pcm = ByteArrayOutputStream()
        val buf = ByteArray(maxOf(min, 3200))
        recording = true
        try {
            rec.startRecording()
            while (recording && isActive && pcm.size() < rate * 2 * 30) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) pcm.write(buf, 0, n)
            }
        } finally {
            recording = false
            runCatching { rec.stop() }
            rec.release()
        }
        wav(pcm.toByteArray(), rate)
    }

    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(pcm.size)
        return header.array() + pcm
    }
}
