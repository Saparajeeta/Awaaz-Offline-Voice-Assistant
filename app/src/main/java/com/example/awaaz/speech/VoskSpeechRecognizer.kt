package com.example.awaaz.speech

import android.content.Context
import org.vosk.LibVosk
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import org.json.JSONObject

class VoskSpeechRecognizer(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000.0f
        const val MODEL_PATH = "model"   // assets/model/
        const val WAV_HEADER_SIZE = 44
    }

    private var model: Model? = null

    init {
        // Set log level (0 = silent, 2 = warnings)
        LibVosk.setLogLevel(org.vosk.LogLevel.WARNINGS)

    }

    /**
     * Load Vosk model from assets/model/
     */
    fun loadModel(
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        StorageService.unpack(
            context,
            MODEL_PATH,
            "model",
            { m ->
                model = m
                onSuccess()
            },
            { e ->
                onError(e ?: Exception("Model load error"))
            }
        )
    }

    /**
     * Recognize speech from WAV file
     */
    fun recognizeWavFile(
        file: File,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val m = model
        if (m == null) {
            onError(IOException("Model not loaded"))
            return
        }

        try {
            val inputStream = FileInputStream(file)

            // Skip WAV header
            inputStream.skip(WAV_HEADER_SIZE.toLong())

            val recognizer = Recognizer(m, SAMPLE_RATE)

            val buffer = ByteArray(4096)
            while (true) {
                val nbytes = inputStream.read(buffer)
                if (nbytes < 0) break
                recognizer.acceptWaveForm(buffer, nbytes)
            }

            val finalResult = recognizer.finalResult

            val text = extractText(finalResult)

            onResult(text)

        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Extract recognized text from JSON result
     */
    private fun extractText(json: String?): String {
        if (json.isNullOrEmpty()) return ""

        return try {
            val obj = JSONObject(json)
            obj.getString("text")
        } catch (e: Exception) {
            ""
        }
    }
}
