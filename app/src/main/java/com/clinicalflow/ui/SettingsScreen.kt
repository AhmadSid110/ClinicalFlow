package com.clinicalflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.clinicalflow.utils.SecureStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    
    var deepgramKey by remember { mutableStateOf(SecureStorage.getDeepgramKey(context) ?: "") }
    var geminiKey by remember { mutableStateOf(SecureStorage.getGeminiKey(context) ?: "") }
    var showDeepgramKey by remember { mutableStateOf(false) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var showSavedMessage by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "API Keys",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                "Your keys are stored encrypted on this device only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Deepgram API Key
            OutlinedTextField(
                value = deepgramKey,
                onValueChange = { deepgramKey = it },
                label = { Text("Deepgram API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showDeepgramKey)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showDeepgramKey = !showDeepgramKey }) {
                        Icon(
                            if (showDeepgramKey) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (showDeepgramKey) "Hide" else "Show"
                        )
                    }
                },
                singleLine = true
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Gemini API Key
            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showGeminiKey)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                        Icon(
                            if (showGeminiKey) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (showGeminiKey) "Hide" else "Show"
                        )
                    }
                },
                singleLine = true
            )
            
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = {
                    SecureStorage.saveDeepgramKey(context, deepgramKey)
                    SecureStorage.saveGeminiKey(context, geminiKey)
                    showSavedMessage = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = deepgramKey.isNotBlank() && geminiKey.isNotBlank()
            ) {
                Text("Save Keys")
            }
            
            if (showSavedMessage) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Keys saved securely!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Divider()
            
            Spacer(Modifier.height(24.dp))
            
            // Info cards
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Get Your API Keys",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        "Deepgram: console.deepgram.com",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Gemini: aistudio.google.com",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Cost Estimate",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Deepgram: Free tier ~200 hrs/month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "Gemini Flash: Free tier ~60 requests/min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}