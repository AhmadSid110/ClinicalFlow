package com.clinicalflow.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class GeminiClient(private val apiKey: String) {
    
    companion object {
        private const val TAG = "GeminiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-1.5-flash"
        
        const val SOAP_SYSTEM_PROMPT = """
You are a professional medical scribe assistant. Your role is to convert raw clinical transcripts into structured medical documentation.

Given the transcript and any rough notes from the user, generate a complete SOAP note:

**Subjective:** Patient's chief complaint, history of present illness, relevant past medical history, medications, allergies, review of systems.

**Objective:** Vital signs, physical exam findings, relevant lab/imaging results mentioned.

**Assessment:** Working diagnosis, differential diagnosis.

**Plan:** Treatment plan, medications prescribed, follow-up instructions, referrals.

Guidelines:
- Use medical terminology appropriately
- If information is unclear, indicate with [unclear] rather than guessing
- Preserve clinically relevant details even if informal language was used
- Do NOT fabricate information not present in the transcript
- Use bullet points for clarity where appropriate
"""

        const val STUDY_SYSTEM_PROMPT = """
You are a medical education assistant. Convert lecture or educational content into high-yield study notes.

Generate structured study notes with:
- **Key Concepts**: Main topics covered
- **Definitions**: Important terms and their meanings
- **Clinical Pearls**: Clinically relevant points that are commonly tested
- **Differential Diagnosis Table**: If applicable
- **Treatment Algorithms**: If applicable
- **Mnemonics**: Create memorable mnemonics for complex topics
- **Practice Questions**: 2-3 board-style questions to test understanding

Keep notes concise and exam-focused.
"""

        const val SUMMARY_SYSTEM_PROMPT = """
You are a medical documentation assistant. Create a concise summary of the clinical encounter.

Include:
- Chief complaint (1 line)
- Key findings (2-3 bullet points)
- Diagnosis/Assessment (1-2 lines)
- Plan (2-3 bullet points)
- Follow-up needed (yes/no + timeframe)

Keep total summary under 150 words.
"""
    }
    
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    enum class OutputType {
        SOAP_NOTE,
        STUDY_NOTES,
        SUMMARY
    }
    
    suspend fun processTranscript(
        transcript: String,
        roughNotes: String = "",
        outputType: OutputType = OutputType.SOAP_NOTE
    ): Result<String> = withContext(Dispatchers.IO) {
        
        val systemPrompt = when (outputType) {
            OutputType.SOAP_NOTE -> SOAP_SYSTEM_PROMPT
            OutputType.STUDY_NOTES -> STUDY_SYSTEM_PROMPT
            OutputType.SUMMARY -> SUMMARY_SYSTEM_PROMPT
        }
        
        val url = "$BASE_URL/$MODEL:generateContent?key=$apiKey"
        
        val requestBody = buildRequestBody(systemPrompt, transcript, roughNotes)
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()
        
        try {
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Gemini API error: ${response.code} - $errorBody")
                return@withContext Result.failure(Exception("Gemini API error: ${response.code}"))
            }
            
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response body"))
            
            val result = parseGeminiResponse(responseBody)
            Result.success(result)
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            Result.failure(e)
        }
    }
    
    private fun buildRequestBody(systemPrompt: String, transcript: String, roughNotes: String): String {
        val content = buildString {
            append("## System Prompt\n$systemPrompt\n\n")
            append("## Transcript\n$transcript\n\n")
            if (roughNotes.isNotBlank()) {
                append("## Rough Notes (User's quick jotting)\n$roughNotes\n\n")
            }
        }
        
        return JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", content)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 4096)
            })
            // Safety settings - BLOCK_ONLY_HIGH (allows medical terminology)
            put("safetySettings", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HARASSMENT")
                    put("threshold", "BLOCK_ONLY_HIGH")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HATE_SPEECH")
                    put("threshold", "BLOCK_ONLY_HIGH")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT")
                    put("threshold", "BLOCK_ONLY_HIGH")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                    put("threshold", "BLOCK_ONLY_HIGH")
                })
            })
        }.toString()
    }
    
    private fun parseGeminiResponse(responseBody: String): String {
        val json = JSONObject(responseBody)
        
        // Check for blocked content
        if (json.has("promptFeedback")) {
            val feedback = json.getJSONObject("promptFeedback")
            if (feedback.has("blockReason")) {
                val blockReason = feedback.getString("blockReason")
                throw Exception("Content blocked: $blockReason")
            }
        }
        
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return ""
        }
        
        val firstCandidate = candidates.optJSONObject(0)
        if (firstCandidate == null) {
            return ""
        }
        
        // Check if content was blocked
        if (firstCandidate.has("finishReason")) {
            val finishReason = firstCandidate.getString("finishReason")
            if (finishReason == "SAFETY") {
                throw Exception("Response blocked due to safety concerns")
            }
        }
        
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val firstPart = parts?.optJSONObject(0)
        return firstPart?.optString("text") ?: ""
    }
}
