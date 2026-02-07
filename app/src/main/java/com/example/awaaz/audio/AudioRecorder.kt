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
 * Microphone recording module using [MediaRecorder].
 * Records to AAC in MPEG_4 container (API 26+).
 *
 * @param context Application or Activity context (required for API 31+).
 *
 * Usage:
 * - Call [startRecording] with a [File] to write to.
 * - Call [stopRecording] to stop and finalize the file.
 * - Check [state] for current status; use a callback or Flow for UI updates.
 */
class AudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    private var _state: RecordingState = RecordingState.Idle
    val state: RecordingState get() = _state

    /**
     * Returns a new file in app-specific storage suitable for a recording (e.g. in Music/Recordings).
     * Filename format: awaaz_yyyyMMdd_HHmmss.m4a
     */
    fun createOutputFile(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context.filesDir
        val subdir = File(dir, "Recordings").apply { mkdirs() }
        val name = "awaaz_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        return File(subdir, name)
    }

    /**
     * Starts recording to the given [file]. The file will be overwritten if it exists.
     * Does nothing if already recording.
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
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        mediaRecorder = recorder
        _state = RecordingState.Recording
    }

    /**
     * Stops recording and finalizes the file. After this, [state] becomes [RecordingState.Stopped]
     * with the file path, or [RecordingState.Error] if something went wrong.
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
     * Resets state to [RecordingState.Idle] (e.g. after showing Stopped/Error to the user).
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
