package com.clinicalflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clinicalflow.data.*
import com.clinicalflow.ui.*
import com.clinicalflow.utils.SecureStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    
    private val database by lazy { DatabaseProvider.getDatabase(this) }
    private val noteDao get() = database.noteDao()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            ClinicalFlowTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                
                // Check if API keys are set
                val hasKeys = SecureStorage.hasKeys(this)
                
                // Collect notes
                val notes by noteDao.getAllFlow().collectAsState(initial = emptyList())
                
                NavHost(
                    navController = navController,
                    startDestination = if (hasKeys) "home" else "settings"
                ) {
                    composable("home") {
                        HomeScreen(
                            navController = navController,
                            notes = notes,
                            onNoteClick = { noteId ->
                                navController.navigate("note/$noteId")
                            },
                            onNewNote = { noteType ->
                                navController.navigate("record/${noteType.name}")
                            }
                        )
                    }
                    
                    composable(
                        "record/{noteType}",
                        arguments = listOf(navArgument("noteType") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val noteType = NoteType.valueOf(
                            backStackEntry.arguments?.getString("noteType") ?: "PATIENT"
                        )
                        
                        RecordingScreen(
                            navController = navController,
                            noteType = noteType,
                            onSave = { transcript, roughNotes, structuredNote ->
                                scope.launch {
                                    noteDao.insert(
                                        Note(
                                            type = noteType,
                                            transcript = transcript,
                                            roughNotes = roughNotes,
                                            finalStructuredNote = structuredNote
                                        )
                                    )
                                }
                            }
                        )
                    }
                    
                    composable(
                        "note/{noteId}",
                        arguments = listOf(navArgument("noteId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L
                        
                        val note = runBlocking {
                            noteDao.getById(noteId)
                        } ?: return@composable
                        
                        NoteDetailScreen(
                            navController = navController,
                            note = note,
                            onDelete = {
                                scope.launch { noteDao.deleteById(noteId) }
                            },
                            onRegenerate = {
                                // Re-run Gemini processing
                            }
                        )
                    }
                    
                    composable("settings") {
                        SettingsScreen(navController = navController)
                    }
                }
            }
        }
    }
}

@Composable
fun ClinicalFlowTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = MaterialTheme.colorScheme.primary,
            secondary = MaterialTheme.colorScheme.secondary,
            tertiary = MaterialTheme.colorScheme.tertiary
        )
    } else {
        lightColorScheme()
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                content()
            }
        }
    )
}