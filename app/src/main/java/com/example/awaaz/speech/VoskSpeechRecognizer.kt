package com.example.awaaz.speech

import android.content.Context
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService
import org.vosk.android.StorageService
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Offline speech recognition using the Vosk Android SDK.
 *
 * Setup:
 * 1. Add a Vosk language model to `src/main/assets/`. For example, download a small model
 *    from https://alphacephei.com/vosk/models and place the unpacked folder in assets
 *    (e.g. `assets/model-en-us/` for English US).
 * 2. Call [loadModel] with the asset folder name (e.g. "model-en-us") before starting recognition.
 *
 * Usage:
 * ```
 * val recognizer = VoskSpeechRecognizer(context)
 * recognizer.listener = object : VoskSpeechRecognizer.Listener {
 *     override fun onPartialResult(text: String) { ... }
 *     override fun onFinalResult(text: String) { ... }
 *     override fun onError(e: Exception) { ... }
 * }
 * recognizer.loadModel("model-en-us") {
 *     recognizer.startListening()
 * }
 * // ... later ...
 * recognizer.stopListening()
 * recognizer.release()
 * ```
 */
class VoskSpeechRecognizer(private val context: Context) : RecognitionListener {

    companion object {
        /** Sample rate required by Vosk (Hz). */
        const val SAMPLE_RATE = 16000.0f

        /**
         * Default asset path for the Vosk model. Place the unpacked model folder at
         * `src/main/assets/model/` (e.g. with `am/`, `conf/`, `graph/` inside it).
         */
        const val DEFAULT_MODEL_ASSET_PATH = "model"

        /** Standard WAV header size (bytes to skip to get to PCM data for 16-bit mono). */
        const val WAV_HEADER_SIZE = 44
    }

    /**
     * Callbacks for recognition results and errors.
     */
    interface Listener {
        /** Partial (interim) recognition result. */
        fun onPartialResult(text: String) {}

        /** Final recognition result for an utterance. */
        fun onFinalResult(text: String) {}

        /** Recognition error. */
        fun onError(e: Exception) {}

        /** Recognition timed out (silence). */
        fun onTimeout() {}
    }

    var listener: Listener? = null

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isListening = false

    /** True when the model is loaded and recognition can be started. */
    val isReady: Boolean get() = model != null

    /** True when the recognizer is actively listening to the microphone. */
    val isListeningState: Boolean get() = isListening

    private val fileRecognitionExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var streamService: SpeechStreamService? = null

    init {
        LibVosk.setLogLevel(LogLevel.WARNING)
    }

    /**
     * Loads the Vosk model from the app's assets. The model files must be in
     * `src/main/assets/[modelAssetName]/` (e.g. `assets/model/` with `am/`, `conf/`, `graph/` inside).
     *
     * @param modelAssetName Name of the model folder in assets (use [DEFAULT_MODEL_ASSET_PATH] for `assets/model/`).
     * @param onReady Called on the main thread when the model is loaded; can call [startListening].
     * @param onError Called if loading fails.
     */
    fun loadModel(
        modelAssetName: String,
        onReady: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        if (model != null) {
            onReady?.invoke()
            return
        }
        StorageService.unpack(
            context,
            modelAssetName,
            "model",
            { m ->
                model = m
                onReady?.invoke()
            },
            { e ->
                val ex = e ?: Exception("Unknown error loading model")
                onError?.invoke(ex)
                listener?.onError(ex)
            }
        )
    }

    /**
     * Loads the Vosk model from `assets/model/`. Place the unpacked model folder at
     * `src/main/assets/model/` (e.g. download from https://alphacephei.com/vosk/models,
     * unzip, and put contents so that `assets/model/am/`, `assets/model/conf/`, etc. exist).
     *
     * @param onReady Called when the model is loaded; safe to call [startListening].
     * @param onError Called if unpacking or loading fails.
     */
    fun loadModelFromAssets(
        onReady: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        loadModel(DEFAULT_MODEL_ASSET_PATH, onReady, onError)
    }

    /**
     * Starts listening to the microphone and performing offline speech recognition.
     * Call [loadModel] first and wait for [Listener] / onReady before calling this.
     *
     * @param grammar Optional JSON array of phrase strings to restrict recognition (e.g. "[\"hello\", \"world\"]"). Null for free-form.
     * @throws IOException if the model is not loaded or recognition cannot be started.
     */
    @Throws(IOException::class)
    fun startListening(grammar: String? = null) {
        val m = model ?: throw IOException("Model not loaded. Call loadModel() first.")
        if (isListening) return

        val recognizer = if (grammar != null) {
            Recognizer(m, SAMPLE_RATE, grammar)
        } else {
            Recognizer(m, SAMPLE_RATE)
        }
        val service = SpeechService(recognizer, SAMPLE_RATE)
        service.startListening(this)
        speechService = service
        isListening = true
    }

    /**
     * Stops listening and releases the current recognition session.
     */
    fun stopListening() {
        speechService?.let {
            it.stop()
            it.shutdown()
        }
        speechService = null
        isListening = false
    }

    /**
     * Pauses or resumes recognition while still holding the microphone.
     */
    fun setPause(paused: Boolean) {
        speechService?.setPause(paused)
    }

    /**
     * Releases the model and stops any active recognition. Call when done with the recognizer.
     */
    fun release() {
        stopListening()
        stopFileRecognition()
        model = null
    }

    /**
     * Converts a recorded audio file to text using the Vosk recognizer.
     * The file must be 16 kHz mono PCM. For WAV files, the standard 44-byte header is skipped.
     *
     * @param file Path to a 16 kHz mono WAV file (header skipped) or raw PCM file.
     * @param skipHeaderBytes Number of bytes to skip at the start (use [WAV_HEADER_SIZE] for WAV, 0 for raw PCM).
     * @param onResult Called with the recognized text when done (may be on a background thread).
     * @param onError Called if the model is not loaded, file cannot be read, or recognition fails.
     */
    fun recognizeFile(
        file: File,
        skipHeaderBytes: Int = WAV_HEADER_SIZE,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val m = model
        if (m == null) {
            onError(IOException("Model not loaded. Call loadModel() first."))
            return
        }
        fileRecognitionExecutor.execute {
            try {
                FileInputStream(file).use { fis ->
                    if (skipHeaderBytes > 0) {
                        val skipped = fis.skip(skipHeaderBytes.toLong())
                        if (skipped < skipHeaderBytes) {
                            onError(IOException("File too short: could not skip $skipHeaderBytes bytes"))
                            return@execute
                        }
                    }
                    val recognizer = Recognizer(m, SAMPLE_RATE)
                    val service = SpeechStreamService(recognizer, fis, SAMPLE_RATE.toInt())
                    streamService = service
                    val resultText = StringBuilder()
                    val listener = object : RecognitionListener {
                        private fun extractText(hypothesis: String?): String {
                            if (hypothesis.isNullOrBlank()) return ""
                            return try {
                                val json = JSONObject(hypothesis)
                                if (json.has("text")) json.getString("text").trim() else hypothesis
                            } catch (_: Exception) {
                                hypothesis.trim()
                            }
                        }
                        private fun finish(text: String) {
                            this@VoskSpeechRecognizer.streamService = null
                            onResult(text)
                        }
                        private fun fail(e: Exception) {
                            this@VoskSpeechRecognizer.streamService = null
                            onError(e)
                        }
                        override fun onPartialResult(hypothesis: String?) {
                            val t = extractText(hypothesis)
                            if (t.isNotEmpty()) resultText.append(t).append(" ")
                        }
                        override fun onResult(hypothesis: String?) {
                            val t = extractText(hypothesis)
                            if (t.isNotEmpty()) resultText.append(t).append(" ")
                        }
                        override fun onFinalResult(hypothesis: String?) {
                            val t = extractText(hypothesis)
                            if (t.isNotEmpty()) resultText.append(t).append(" ")
                            finish(resultText.toString().trim())
                        }
                        override fun onError(exception: Exception?) {
                            fail(exception ?: Exception("Recognition error"))
                        }
                        override fun onTimeout() {
                            finish(resultText.toString().trim())
                        }
                    }
                    streamService = service
                    service.start(listener)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /**
     * Converts a recorded audio file (by path) to text. Same as [recognizeFile] with a [File].
     */
    fun recognizeFile(
        filePath: String,
        skipHeaderBytes: Int = WAV_HEADER_SIZE,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        recognizeFile(File(filePath), skipHeaderBytes, onResult, onError)
    }

    /**
     * Stops any in-progress file recognition.
     */
    fun stopFileRecognition() {
        streamService?.stop()
        streamService = null
    }

    /**
     * Converts a recorded audio file into text. Supports:
     * - **.m4a** (e.g. from [com.example.awaaz.audio.VoiceRecorder]): decoded to 16 kHz WAV then recognized.
     * - **.wav**: must be 16 kHz mono; recognized directly.
     *
     * @param filePath Path to the recorded file (.m4a or .wav).
     * @param onResult Called with the recognized text.
     * @param onError Called if model not loaded, conversion fails, or recognition fails.
     */
    fun recognizeRecordedFile(
        filePath: String,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val path = filePath.lowercase()
        when {
            path.endsWith(".m4a") -> {
                fileRecognitionExecutor.execute {
                    try {
                        val wavPath = AudioFileConverter.m4aTo16kWav(filePath)
                        if (wavPath == null) {
                            onError(IOException("Failed to convert M4A to WAV"))
                            return@execute
                        }
                        val wavFile = File(wavPath)
                        recognizeFile(wavPath, WAV_HEADER_SIZE,
                            onResult = { text ->
                                wavFile.delete()
                                onResult(text)
                            },
                            onError = { e ->
                                wavFile.delete()
                                onError(e)
                            }
                        )
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
            }
            path.endsWith(".wav") -> recognizeFile(filePath, WAV_HEADER_SIZE, onResult, onError)
            else -> onError(IllegalArgumentException("Unsupported format. Use .m4a or .wav"))
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        if (!hypothesis.isNullOrBlank()) {
            listener?.onPartialResult(hypothesis)
        }
    }

    override fun onResult(hypothesis: String?) {
        if (!hypothesis.isNullOrBlank()) {
            listener?.onFinalResult(hypothesis)
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        if (!hypothesis.isNullOrBlank()) {
            listener?.onFinalResult(hypothesis)
        }
        isListening = false
        speechService = null
    }

    override fun onError(exception: Exception?) {
        val e = exception ?: Exception("Unknown recognition error")
        isListening = false
        speechService = null
        listener?.onError(e)
    }

    override fun onTimeout() {
        isListening = false
        speechService = null
        listener?.onTimeout()
    }
}
