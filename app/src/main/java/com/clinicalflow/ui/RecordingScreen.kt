package com.clinicalflow.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.clinicalflow.audio.AudioRecordService
import com.clinicalflow.data.NoteType
import com.clinicalflow.network.GeminiClient
import com.clinicalflow.utils.PiiScrubber
import com.clinicalflow.utils.SecureStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    navController: NavController,
    noteType: NoteType,
    onSave: (transcript: String, roughNotes: String, structuredNote: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isRecording by remember { mutableStateOf(false) }
    var roughNotes by remember { mutableStateOf("") }
    var showRoughNotesDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
    )}
    
    val transcript by AudioRecordService.isRecording
        .collectAsState(initial = false)
    
    // This would be connected to the service in a real implementation
    var currentTranscript by remember { mutableStateOf("") }
    var partialTranscript by remember { mutableStateOf("") }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }
    
    // Permission request on first launch
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when (noteType) {
                        NoteType.PATIENT -> "Patient Encounter"
                        NoteType.LECTURE -> "Lecture Recording"
                        NoteType.STUDY -> "Study Session"
                    })
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRoughNotesDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Add Notes")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Transcript display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (currentTranscript.isBlank() && partialTranscript.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Start recording to see transcript...",
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        Text(
                            currentTranscript + partialTranscript,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Rough notes preview
            if (roughNotes.isNotBlank()) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            roughNotes,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            
            // Recording button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(if (isRecording) 1.2f else 1f),
                contentAlignment = Alignment.Center
            ) {
                FloatingActionButton(
                    onClick = {
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@FloatingActionButton
                        }
                        
                        if (isRecording) {
                            // Stop recording
                            isRecording = false
                            isProcessing = true
                            
                            scope.launch {
                                val geminiKey = SecureStorage.getGeminiKey(context) ?: ""
                                val gemini = GeminiClient(geminiKey)
                                
                                val outputType = when (noteType) {
                                    NoteType.PATIENT -> GeminiClient.OutputType.SOAP_NOTE
                                    NoteType.LECTURE -> GeminiClient.OutputType.STUDY_NOTES
                                    NoteType.STUDY -> GeminiClient.OutputType.SUMMARY
                                }
                                
                                // Scrub PII before sending to Gemini
                                val (scrubbedTranscript, piiTypes) = PiiScrubber.scrub(currentTranscript)
                                
                                val result = gemini.processTranscript(
                                    transcript = scrubbedTranscript,
                                    roughNotes = roughNotes,
                                    outputType = outputType
                                )
                                
                                isProcessing = false
                                
                                result.onSuccess { structuredNote ->
                                    onSave(currentTranscript, roughNotes, structuredNote)
                                    navController.popBackStack()
                                }.onFailure { error ->
                                    // Handle error - show snackbar
                                }
                            }
                        } else {
                            isRecording = true
                            currentTranscript = ""
                            partialTranscript = ""
                            // In real implementation: start AudioRecordService
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    containerColor = if (isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop" else "Record",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                if (isRecording) "Tap to stop" else "Tap to record",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
    
    // Rough notes dialog
    if (showRoughNotesDialog) {
        AlertDialog(
            onDismissRequest = { showRoughNotesDialog = false },
            title = { Text("Quick Notes") },
            text = {
                OutlinedTextField(
                    value = roughNotes,
                    onValueChange = { roughNotes = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    placeholder = { Text("Add context, corrections, or key points...") },
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(onClick = { showRoughNotesDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}