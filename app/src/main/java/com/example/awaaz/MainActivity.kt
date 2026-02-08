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
import androidx.compose.material3.ButtonDefaults
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
import com.example.awaaz.audio.RecordingState
import com.example.awaaz.audio.VoiceRecorder
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

/**
 * Start Recording Button - Controls VoiceRecorder to begin recording.
 */
@Composable
fun StartRecordingButton(
    voiceRecorder: VoiceRecorder,
    hasPermission: Boolean,
    onPermissionRequest: () -> Unit,
    onStateChange: (RecordingState) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            if (!hasPermission) {
                onPermissionRequest()
                return@Button
            }
            
            try {
                val file = voiceRecorder.createOutputFile()
                voiceRecorder.startRecording(file)
                onStateChange(voiceRecorder.state)
            } catch (e: Exception) {
                onStateChange(RecordingState.Error(e.message ?: "Start failed"))
            }
        },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text("Start Recording")
    }
}

/**
 * Stop Recording Button - Controls VoiceRecorder to stop recording.
 */
@Composable
fun StopRecordingButton(
    voiceRecorder: VoiceRecorder,
    onStateChange: (RecordingState) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            voiceRecorder.stopRecording()
            onStateChange(voiceRecorder.state)
        },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text("Stop Recording")
    }
}

@Composable
fun RecordingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    // Initialize VoiceRecorder - remember keeps it across recompositions
    val voiceRecorder = remember { VoiceRecorder(context.applicationContext) }
    
    // Compose state that tracks the recording state for UI updates
    var recordingState by remember { mutableStateOf<RecordingState>(voiceRecorder.state) }

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
        if (!granted) recordingState = RecordingState.Error("Microphone permission denied")
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
            // Display current recording state
            Text(
                text = when (recordingState) {
                    is RecordingState.Idle -> "Tap to start recording"
                    is RecordingState.Recording -> "Recording…"
                    is RecordingState.Stopped -> {
                        val fileName = (recordingState as RecordingState.Stopped).filePath
                            .substringAfterLast("/")
                        "Saved: $fileName"
                    }
                    is RecordingState.Error -> (recordingState as RecordingState.Error).message
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Control buttons based on recording state
            when (recordingState) {
                is RecordingState.Recording -> {
                    // Show stop button while recording
                    StopRecordingButton(
                        voiceRecorder = voiceRecorder,
                        onStateChange = { recordingState = it }
                    )
                }
                else -> {
                    // Show start button when idle or after error
                    StartRecordingButton(
                        voiceRecorder = voiceRecorder,
                        hasPermission = hasPermission,
                        onPermissionRequest = { requestMicrophonePermission() },
                        onStateChange = { recordingState = it }
                    )
                    
                    // Show reset button after recording stopped or error
                    if (recordingState is RecordingState.Stopped || recordingState is RecordingState.Error) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                voiceRecorder.reset()
                                recordingState = RecordingState.Idle
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
