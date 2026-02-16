package com.clinicalflow.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStorage {
    private const val PREFS_NAME = "api_keys"
    
    private fun getMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        getMasterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveDeepgramKey(context: Context, key: String) {
        getPrefs(context).edit().putString("deepgram_api_key", key).apply()
    }
    
    fun saveGeminiKey(context: Context, key: String) {
        getPrefs(context).edit().putString("gemini_api_key", key).apply()
    }
    
    fun getDeepgramKey(context: Context): String? {
        return getPrefs(context).getString("deepgram_api_key", null)
    }
    
    fun getGeminiKey(context: Context): String? {
        return getPrefs(context).getString("gemini_api_key", null)
    }
    
    fun hasKeys(context: Context): Boolean {
        val prefs = getPrefs(context)
        return !prefs.getString("deepgram_api_key", null).isNullOrBlank() &&
               !prefs.getString("gemini_api_key", null).isNullOrBlank()
    }
}
