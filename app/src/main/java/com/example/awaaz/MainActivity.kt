package com.example.awaaz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.awaaz.audio.VoiceRecorder
import com.example.awaaz.audio.RecordingState
import com.example.awaaz.speech.VoskSpeechRecognizer
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var recognizer: VoskSpeechRecognizer
    private lateinit var recorder: VoiceRecorder
    private var audioFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                0
            )
        }

        recognizer = VoskSpeechRecognizer(this)
        recorder = VoiceRecorder(this)

        setContent { AwazScreen() }
    }

    @Composable
    fun AwazScreen() {

        var text by remember {
            mutableStateOf("Model loading...")
        }

        var recording by remember {
            mutableStateOf(false)
        }

        LaunchedEffect(Unit) {
            recognizer.loadModelFromAssets(
                onReady = {
                    text = "Model Loaded Successfully"
                },
                onError = {
                    text = "Model Load Error"
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text("Awaaz")

            Spacer(Modifier.height(20.dp))

            Text(text)

            Spacer(Modifier.height(20.dp))

            Button(onClick = {

                if (!recording) {

                    audioFile = recorder.createOutputFile()
                    recorder.startRecording(audioFile!!)

                    text = "Recording..."
                    recording = true

                } else {

                    recorder.stopRecording()

                    val path = when (val state = recorder.state) {
                        is RecordingState.Stopped -> state.filePath
                        is RecordingState.Error -> {
                            text = "Recording error: ${state.message}"
                            return@Button
                        }
                        else -> {
                            text = "Recording not ready"
                            return@Button
                        }
                    }

                    recognizer.recognizeRecordedFile(
                        path,
                        onResult = {
                            runOnUiThread {
                                text = "You said: $it"
                            }
                        },
                        onError = {
                            runOnUiThread {
                                text = "Recognition error"
                            }
                        }
                    )

                    recording = false
                }

            }) {
                Text(if (recording) "Stop Recording" else "Start Recording")
            }
        }
    }
}
