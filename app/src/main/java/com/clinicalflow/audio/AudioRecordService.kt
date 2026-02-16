package com.clinicalflow.audio

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.clinicalflow.R
import com.clinicalflow.network.DeepgramClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecordService : Service() {
    
    companion object {
        const val CHANNEL_ID = "clinicalflow_recording"
        const val NOTIFICATION_ID = 1001
        
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_FACTOR = 2
        
        val isRecording = MutableStateFlow(false)
    }
    
    private val binder = LocalBinder()
    private var audioRecord: AudioRecord? = null
    private var deepgramClient: DeepgramClient? = null
    private var recordingJob: Job? = null
    private var audioFile: File? = null
    private var fileOutputStream: FileOutputStream? = null
    
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript
    
    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript
    
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude
    
    inner class LocalBinder : Binder() {
        fun getService(): AudioRecordService = this@AudioRecordService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_RECORDING" -> startRecording()
            "STOP_RECORDING" -> stopRecording()
        }
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "ClinicalFlow recording in progress"
        }
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    
    private fun buildNotification(): Notification {
        val intent = Intent(this, AudioRecordService::class.java).apply {
            action = "STOP_RECORDING"
        }
        
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ClinicalFlow")
            .setContentText("Recording in progress...")
            .setSmallIcon(R.drawable.ic_mic)
            .addAction(R.drawable.ic_stop, "Stop", pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    fun startRecording() {
        if (isRecording.value) return
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("AudioRecordService", "Record audio permission not granted")
            return
        }
        
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(4096) * BUFFER_SIZE_FACTOR
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioRecordService", "AudioRecord not initialized")
            return
        }
        
        // Setup audio file for offline storage (as WAV)
        audioFile = File(filesDir, "recording_${System.currentTimeMillis()}.wav")
        fileOutputStream = FileOutputStream(audioFile)
        
        // Write WAV header placeholder (will be updated on close)
        writeWavHeader(fileOutputStream!!, 0)
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, buildNotification())
        
        audioRecord?.startRecording()
        isRecording.value = true
        
        // Connect to Deepgram
        val deepgramKey = com.clinicalflow.utils.SecureStorage.getDeepgramKey(this)
        if (!deepgramKey.isNullOrBlank()) {
            deepgramClient = DeepgramClient(deepgramKey)
            deepgramClient?.connect(
                onTranscript = { text, isFinal ->
                    if (isFinal) {
                        _transcript.value += " $text"
                        _partialTranscript.value = ""
                    } else {
                        _partialTranscript.value = text
                    }
                },
                onError = { error ->
                    Log.e("AudioRecordService", "Deepgram error: $error")
                }
            )
        }
        
        // Start recording coroutine
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(bufferSize / 2)
            var totalBytesWritten = 0L
            
            while (isRecording.value && isActive) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    // Calculate amplitude for visualization
                    val sum = buffer.take(bytesRead).map { it.toInt() }.sumOf { 
                        kotlin.math.abs(it) 
                    }
                    _amplitude.value = (sum.toFloat() / bytesRead) / 32768f
                    
                    // Save to file
                    fileOutputStream?.write(buffer, 0, bytesRead)
                    totalBytesWritten += bytesRead
                    
                    // Send to Deepgram
                    deepgramClient?.sendAudio(buffer.copyOf(bytesRead))
                }
            }
            
            // Update WAV header with final size
            fileOutputStream?.close()
            audioFile?.let { updateWavHeader(it, totalBytesWritten.toInt()) }
        }
    }
    
    fun stopRecording(): File? {
        if (!isRecording.value) return null
        
        isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        fileOutputStream?.close()
        fileOutputStream = null
        
        deepgramClient?.close()
        deepgramClient = null
        
        _amplitude.value = 0f
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        return audioFile
    }
    
    fun getAudioFile(): File? = audioFile
    
    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }
    
    // WAV header helpers
    private fun writeWavHeader(out: FileOutputStream, dataSize: Int) {
        val totalSize = dataSize + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8 // sampleRate * channels * bitsPerSample/8
        val blockAlign = 1 * 16 / 8 // channels * bitsPerSample/8
        
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF header
        header.put("RIFF".toByteArray())
        header.putInt(totalSize)
        header.put("WAVE".toByteArray())
        
        // fmt subchunk
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size (16 for PCM)
        header.putShort(1) // AudioFormat (1 = PCM)
        header.putShort(1) // NumChannels (1 = mono)
        header.putInt(SAMPLE_RATE) // SampleRate
        header.putInt(byteRate) // ByteRate
        header.putShort(blockAlign.toShort()) // BlockAlign
        header.putShort(16) // BitsPerSample
        
        // data subchunk
        header.put("data".toByteArray())
        header.putInt(dataSize)
        
        out.write(header.array())
    }
    
    private fun updateWavHeader(file: File, dataSize: Int) {
        try {
            val raf = RandomAccessFile(file, "rw")
            
            // Update RIFF chunk size
            raf.seek(4)
            raf.write(intToByteArrayLE(dataSize + 36))
            
            // Update data chunk size
            raf.seek(40)
            raf.write(intToByteArrayLE(dataSize))
            
            raf.close()
        } catch (e: Exception) {
            Log.e("AudioRecordService", "Failed to update WAV header: ${e.message}")
        }
    }
    
    private fun intToByteArrayLE(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }
}
