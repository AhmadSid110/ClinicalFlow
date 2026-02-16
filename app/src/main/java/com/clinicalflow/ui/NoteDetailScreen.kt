package com.clinicalflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.clinicalflow.data.Note
import com.clinicalflow.data.NoteType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    navController: NavController,
    note: Note,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf(Section.TRANSCRIPT) }
    
    val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            val (icon, color) = when (note.type) {
                NoteType.PATIENT -> Icons.Default.Person to MaterialTheme.colorScheme.primary
                NoteType.LECTURE -> Icons.Default.School to MaterialTheme.colorScheme.secondary
                NoteType.STUDY -> Icons.Default.MenuBook to MaterialTheme.colorScheme.tertiary
            }
            
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        note.title.ifBlank { "${note.type.name.lowercase().replaceFirstChar { it.titlecase() }}" },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        dateFormat.format(Date(note.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Tabbed sections
            SegmentedButton(
                expandedSection = expandedSection,
                onSectionChange = { expandedSection = it }
            )
            
            Spacer(Modifier.height(16.dp))
            
            when (expandedSection) {
                Section.TRANSCRIPT -> {
                    TranscriptSection(transcript = note.transcript)
                }
                Section.NOTES -> {
                    NotesSection(roughNotes = note.roughNotes)
                }
                Section.OUTPUT -> {
                    OutputSection(
                        structuredNote = note.finalStructuredNote,
                        noteType = note.type,
                        onRegenerate = onRegenerate
                    )
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Note?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                        navController.popBackStack()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

enum class Section {
    TRANSCRIPT, NOTES, OUTPUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedButton(
    expandedSection: Section,
    onSectionChange: (Section) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Section.entries.forEach { section ->
            FilterChip(
                selected = expandedSection == section,
                onClick = { onSectionChange(section) },
                label = {
                    Text(
                        when (section) {
                            Section.TRANSCRIPT -> "Transcript"
                            Section.NOTES -> "Notes"
                            Section.OUTPUT -> "Output"
                        }
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TranscriptSection(transcript: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Raw Transcript", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                transcript.ifBlank { "No transcript available" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun NotesSection(roughNotes: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Quick Notes", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                roughNotes.ifBlank { "No quick notes added" },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun OutputSection(
    structuredNote: String,
    noteType: NoteType,
    onRegenerate: () -> Unit
) {
    Column {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        when (noteType) {
                            NoteType.PATIENT -> "SOAP Note"
                            NoteType.LECTURE -> "Study Notes"
                            NoteType.STUDY -> "Summary"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = onRegenerate) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    structuredNote.ifBlank { "Processing..." },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}