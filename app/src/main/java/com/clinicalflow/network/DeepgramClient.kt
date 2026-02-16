package com.clinicalflow.network

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class DeepgramClient(private val apiKey: String) {
    
    companion object {
        private const val TAG = "DeepgramClient"
        private const val BASE_URL = "wss://api.deepgram.com/v1/listen"
        private const val MODEL = "nova-2-medical"
        
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val RECONNECT_BACKOFF_MULTIPLIER = 2.0
    }
    
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var isConnecting = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private var reconnectJob: Job? = null
    
    // Callbacks
    private var onTranscriptCallback: ((String, Boolean) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    fun connect(
        model: String = MODEL,
        language: String = "en-US",
        onTranscript: (text: String, isFinal: Boolean) -> Unit,
        onError: (error: String) -> Unit
    ) {
        onTranscriptCallback = onTranscript
        onErrorCallback = onError
        
        if (isConnecting || isConnected) {
            Log.d(TAG, "Already connected or connecting")
            return
        }
        
        doConnect(model, language)
    }
    
    private fun doConnect(model: String, language: String) {
        if (isConnecting) return
        isConnecting = true
        
        val url = HttpUrl.Builder()
            .scheme("wss")
            .host("api.deepgram.com")
            .addPathSegment("v1")
            .addPathSegment("listen")
            .addQueryParameter("encoding", "linear16")
            .addQueryParameter("sample_rate", "16000")
            .addQueryParameter("channels", "1")
            .addQueryParameter("model", model)
            .addQueryParameter("language", language)
            .addQueryParameter("punctuate", "true")
            .addQueryParameter("smart_format", "true")
            .addQueryParameter("interim_results", "true")
            .addQueryParameter("endpointing", "300")
            .build()
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Token $apiKey")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected = true
                isConnecting = false
                reconnectDelay = INITIAL_RECONNECT_DELAY_MS // Reset backoff
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(Dispatchers.Main) {
                    try {
                        val json = org.json.JSONObject(text)
                        
                        when (json.optString("type")) {
                            "Results" -> {
                                val channel = json.optJSONObject("channel")
                                val alternative = channel?.optJSONArray("alternatives")?.optJSONObject(0)
                                val transcript = alternative?.optString("transcript") ?: ""
                                val isFinal = json.optBoolean("is_final")
                                
                                if (transcript.isNotBlank()) {
                                    onTranscriptCallback?.invoke(transcript, isFinal)
                                }
                            }
                            "Metadata" -> {
                                Log.d(TAG, "Metadata received")
                            }
                            "SpeechStarted" -> {
                                Log.d(TAG, "Speech started")
                            }
                            "SpeechFinished" -> {
                                Log.d(TAG, "Speech finished")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse message: ${e.message}")
                    }
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                isConnected = false
                webSocket.close(code, reason)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                isConnected = false
                isConnecting = false
                attemptReconnect()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected = false
                isConnecting = false
                onErrorCallback?.invoke(t.message ?: "WebSocket error")
                attemptReconnect()
            }
        })
    }
    
    private fun attemptReconnect() {
        if (reconnectJob?.isActive == true) return
        
        Log.d(TAG, "Attempting reconnect in ${reconnectDelay}ms")
        
        reconnectJob = scope.launch {
            delay(reconnectDelay)
            if (!isConnected && !isConnecting) {
                doConnect(MODEL, "en-US")
            }
        }
        
        // Exponential backoff
        reconnectDelay = (reconnectDelay * RECONNECT_BACKOFF_MULTIPLIER).toLong()
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }
    
    fun sendAudio(audioData: ByteArray): Boolean {
        if (isConnected) {
            val success = webSocket?.send(ByteString.of(*audioData)) ?: false
            if (!success) {
                Log.w(TAG, "Failed to send audio data")
            }
            return success
        }
        return false
    }
    
    fun close() {
        reconnectJob?.cancel()
        isConnected = false
        isConnecting = false
        webSocket?.close(1000, "Client closed")
        webSocket = null
        scope.cancel()
    }
    
    fun isReady(): Boolean = isConnected
}
