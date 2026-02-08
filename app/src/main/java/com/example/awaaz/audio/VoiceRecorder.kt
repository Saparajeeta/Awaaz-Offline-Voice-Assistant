package com.example.awaaz.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records voice/audio using [MediaRecorder] on Android.
 * Output: AAC in MPEG_4 container (.m4a), mono, 44.1 kHz, 128 kbps.
 *
 * @param context Application or Activity context (required for API 31+).
 *
 * Usage:
 * ```
 * val recorder = VoiceRecorder(context)
 * val file = recorder.createOutputFile()
 * recorder.startRecording(file)
 * // ... later ...
 * recorder.stopRecording()
 * val path = (recorder.state as RecordingState.Stopped).filePath
 * ```
 */
class VoiceRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    private var _state: RecordingState = RecordingState.Idle
    /** Current recording state. */
    val state: RecordingState get() = _state

    /**
     * Whether recording is currently active.
     */
    val isRecording: Boolean get() = _state == RecordingState.Recording

    /**
     * Creates a new output file in app-specific storage (Music/Recordings).
     * Filename format: voice_yyyyMMdd_HHmmss.m4a
     */
    fun createOutputFile(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context.filesDir
        val subdir = File(dir, "Recordings").apply { mkdirs() }
        val name = "voice_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        return File(subdir, name)
    }

    /**
     * Starts recording to [file]. Overwrites if the file exists.
     * No-op if already recording.
     * @throws IOException if [MediaRecorder] setup or start fails
     */
    @Throws(IOException::class)
    fun startRecording(file: File) {
        if (_state == RecordingState.Recording) return

        releaseRecorder()
        outputFile = file

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(128_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        mediaRecorder = recorder
        _state = RecordingState.Recording
    }

    /**
     * Stops recording and finalizes the file.
     * [state] becomes [RecordingState.Stopped] with the file path, or [RecordingState.Error] on failure.
     */
    fun stopRecording() {
        val recorder = mediaRecorder ?: run {
            if (_state == RecordingState.Recording) _state = RecordingState.Error("Recorder was null")
            return
        }
        val file = outputFile

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recorder.stop()
            } else {
                @Suppress("DEPRECATION")
                recorder.stop()
            }
            _state = if (file != null && file.exists()) {
                RecordingState.Stopped(file.absolutePath)
            } else {
                RecordingState.Error("Output file not found")
            }
        } catch (e: Exception) {
            _state = RecordingState.Error(e.message ?: "Stop failed")
        } finally {
            releaseRecorder()
            outputFile = null
        }
    }

    /**
     * Resets state to [RecordingState.Idle]. Call after handling Stopped/Error.
     */
    fun reset() {
        releaseRecorder()
        outputFile = null
        _state = RecordingState.Idle
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) { /* ignore */ }
        mediaRecorder = null
    }
}
