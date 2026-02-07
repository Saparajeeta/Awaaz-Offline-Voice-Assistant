package com.example.awaaz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.awaaz.audio.AudioRecorder
import com.example.awaaz.audio.RecordingState
import com.example.awaaz.ui.theme.AwaazTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwaazTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecordingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private const val PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO

@Composable
fun RecordingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context.applicationContext) }
    var uiState by remember { mutableStateOf<RecordingState>(recorder.state) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, PERMISSION_RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) uiState = RecordingState.Error("Microphone permission denied")
    }

    fun requestMicrophonePermission() {
        permissionLauncher.launch(PERMISSION_RECORD_AUDIO)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Awaaz",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (!hasPermission) {
            Text(
                text = "Microphone access is needed to record audio.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { requestMicrophonePermission() }) {
                Text("Allow microphone")
            }
        } else {
            Text(
            text = when (uiState) {
                is RecordingState.Idle -> "Tap to start recording"
                is RecordingState.Recording -> "Recording…"
                is RecordingState.Stopped -> "Saved: ${(uiState as RecordingState.Stopped).filePath}"
                is RecordingState.Error -> (uiState as RecordingState.Error).message
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (uiState) {
            is RecordingState.Recording -> {
                Button(
                    onClick = {
                        recorder.stopRecording()
                        uiState = recorder.state
                    }
                ) {
                    Text("Stop")
                }
            }
            else -> {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, PERMISSION_RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestMicrophonePermission()
                            return@Button
                        }
                        try {
                            val file = recorder.createOutputFile()
                            recorder.startRecording(file)
                            uiState = RecordingState.Recording
                        } catch (e: Exception) {
                            uiState = RecordingState.Error(e.message ?: "Start failed")
                        }
                    }
                ) {
                    Text("Start recording")
                }
                if (uiState is RecordingState.Stopped || uiState is RecordingState.Error) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            recorder.reset()
                            uiState = RecordingState.Idle
                        }
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
        }
    }
}
