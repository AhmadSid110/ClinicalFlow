package com.clinicalflow.utils

import android.content.Context

/**
 * Abstraction for secure token storage
 */
interface TokenManager {
    fun saveDeepgramKey(key: String)
    fun saveGeminiKey(key: String)
    fun getDeepgramKey(): String?
    fun getGeminiKey(): String?
    fun hasKeys(): Boolean
    fun clearKeys()
}

/**
 * EncryptedSharedPreferences implementation of TokenManager
 */
class EncryptedTokenManager(private val context: Context) : TokenManager {
    
    private val masterKey by lazy {
        androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val prefs by lazy {
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    companion object {
        private const val PREFS_NAME = "clinicalflow_tokens"
        private const val KEY_DEEPGRAM = "deepgram_api_key"
        private const val KEY_GEMINI = "gemini_api_key"
    }
    
    override fun saveDeepgramKey(key: String) {
        prefs.edit().putString(KEY_DEEPGRAM, key).apply()
    }
    
    override fun saveGeminiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI, key).apply()
    }
    
    override fun getDeepgramKey(): String? {
        return prefs.getString(KEY_DEEPGRAM, null)
    }
    
    override fun getGeminiKey(): String? {
        return prefs.getString(KEY_GEMINI, null)
    }
    
    override fun hasKeys(): Boolean {
        return !getDeepgramKey().isNullOrBlank() && !getGeminiKey().isNullOrBlank()
    }
    
    override fun clearKeys() {
        prefs.edit().clear().apply()
    }
}
