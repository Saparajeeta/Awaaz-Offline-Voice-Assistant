package com.example.awaaz.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class RecordingState {
    object Idle : RecordingState()
    object Recording : RecordingState()
    data class Stopped(val filePath: String) : RecordingState()
    data class Error(val message: String) : RecordingState()
}

class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    var state: RecordingState = RecordingState.Idle
        private set

    fun createOutputFile(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val folder = File(dir, "Recordings")
        folder.mkdirs()

        val name = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())

        return File(folder, "voice_$name.m4a")
    }

    fun startRecording(file: File) {

        outputFile = file

        recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(context)
            else
                MediaRecorder()

        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        state = RecordingState.Recording
    }

    fun stopRecording() {

        try {
            recorder?.stop()
            recorder?.release()

            state = RecordingState.Stopped(
                outputFile?.absolutePath ?: ""
            )

        } catch (e: Exception) {
            state = RecordingState.Error(e.message ?: "Error")
        }

        recorder = null
    }
}
