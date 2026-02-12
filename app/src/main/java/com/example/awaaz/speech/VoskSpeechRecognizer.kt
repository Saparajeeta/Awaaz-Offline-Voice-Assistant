package com.example.awaaz.speech

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechStreamService
import org.vosk.android.StorageService
import java.io.File
import java.io.FileInputStream

class VoskSpeechRecognizer(private val context: Context) {

    private var model: Model? = null

    companion object {
        private const val TAG = "VOSK_DEBUG"
    }

    /**
     * 🔥 MODEL LOAD WITH DEBUG LOGS
     */
    fun loadModelFromAssets(
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        Log.d(TAG, "Starting model unpack...")

        StorageService.unpack(
            context,
            "model-en-us",   // assets folder
            "model",         // internal storage folder
            { m ->
                Log.d(TAG, "Model unpack SUCCESS")
                model = m
                onReady()
            },
            { e ->
                Log.e(TAG, "Model unpack FAILED", e)
                onError(e ?: Exception("Model load error"))
            }
        )
    }

    /**
     * 🔥 FILE RECOGNITION WITH DEBUG
     */
    fun recognizeRecordedFile(
        filePath: String,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {

            Log.d(TAG, "Recognition started for file: $filePath")

            val m = model ?: throw Exception("Model not loaded")

            val inputStream = FileInputStream(File(filePath))

            val recognizer = Recognizer(m, 16000.0f)

            val service =
                SpeechStreamService(
                    recognizer,
                    inputStream,
                    16000f
                )

            service.start(object : RecognitionListener {

                override fun onPartialResult(hypothesis: String?) {}

                override fun onResult(hypothesis: String?) {}

                override fun onFinalResult(hypothesis: String?) {

                    Log.d(TAG, "Final result: $hypothesis")

                    val text =
                        JSONObject(hypothesis ?: "{}")
                            .optString("text")

                    onResult(text)
                }

                override fun onError(exception: Exception?) {
                    Log.e(TAG, "Recognition error", exception)
                    onError(exception ?: Exception("Error"))
                }

                override fun onTimeout() {
                    Log.e(TAG, "Recognition timeout")
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            onError(e)
        }
    }
}
