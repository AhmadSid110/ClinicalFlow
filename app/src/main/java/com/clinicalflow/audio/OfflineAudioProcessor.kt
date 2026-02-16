package com.clinicalflow.audio

import android.content.Context
import android.util.Log
import com.clinicalflow.network.DeepgramClient
import com.clinicalflow.utils.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Offline audio processor for uploading saved recordings to Deepgram REST API
 * when network becomes available.
 */
class OfflineAudioProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "OfflineAudioProcessor"
        private const val DEEPGRAM_REST_URL = "https://api.deepgram.com/v1/listen"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    
    private val jsonMediaType = "application/json".toMediaType()
    
    /**
     * Process an offline audio file with Deepgram REST API
     * @param audioFile The WAV file to process
     * @param onTranscript Callback with transcript and whether it's final
     * @return Result with full transcript or error
     */
    suspend fun processAudioFile(
        audioFile: File,
        onTranscript: ((text: String, isFinal: Boolean) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = SecureStorage.getDeepgramKey(context)
            ?: return@withContext Result.failure(Exception("Deepgram API key not found"))
        
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", "nova-2-medical")
                .addFormDataPart("language", "en-US")
                .addFormDataPart("punctuate", "true")
                .addFormDataPart("smart_format", "true")
                .addFormDataPart("diarize", "false")
                .addFormDataPart("paragraphs", "true")
                .build()
            
            val request = Request.Builder()
                .url(DEEPGRAM_REST_URL)
                .addHeader("Authorization", "Token $apiKey")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Deepgram API error: ${response.code} - $errorBody")
                return@withContext Result.failure(Exception("Deepgram API error: ${response.code}"))
            }
            
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response body"))
            
            val transcript = parseDeepgramResponse(responseBody)
            
            // Notify with transcript if callback provided
            onTranscript?.invoke(transcript, true)
            
            Result.success(transcript)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio file: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get list of offline recordings in app's files directory
     */
    fun getOfflineRecordings(): List<File> {
        val filesDir = context.filesDir
        return filesDir.listFiles { file ->
            file.extension == "wav" || file.extension == "raw"
        }?.toList() ?: emptyList()
    }
    
    /**
     * Delete an offline recording
     */
    fun deleteRecording(file: File): Boolean {
        return file.delete()
    }
    
    private fun parseDeepgramResponse(responseBody: String): String {
        val json = org.json.JSONObject(responseBody)
        val results = json.optJSONArray("results")
        
        if (results == null || results.length() == 0) {
            return ""
        }
        
        val result = results.optJSONObject(0)
        val alternatives = result?.optJSONArray("alternatives")
        val alternative = alternatives?.optJSONObject(0)
        
        return alternative?.optString("transcript") ?: ""
    }
}
