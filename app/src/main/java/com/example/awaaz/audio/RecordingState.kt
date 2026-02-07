package com.example.awaaz.audio

/**
 * State of the microphone recorder.
 */
sealed class RecordingState {
    /** Not recording; ready to start. */
    data object Idle : RecordingState()

    /** Currently recording. */
    data object Recording : RecordingState()

    /** Recording stopped; [filePath] is the saved recording. */
    data class Stopped(val filePath: String) : RecordingState()

    /** An error occurred; [message] describes it. */
    data class Error(val message: String) : RecordingState()
}
