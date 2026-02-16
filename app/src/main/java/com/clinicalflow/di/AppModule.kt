package com.clinicalflow.di

import android.content.Context
import com.clinicalflow.audio.OfflineAudioProcessor
import com.clinicalflow.data.AppDatabase
import com.clinicalflow.network.DeepgramClient
import com.clinicalflow.network.GeminiClient
import com.clinicalflow.utils.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * Provides a singleton DeepgramClient instance
     * Note: API key should be provided at connection time
     */
    @Provides
    @Singleton
    fun provideDeepgramClient(
        @ApplicationContext context: Context
    ): DeepgramClient {
        val apiKey = SecureStorage.getDeepgramKey(context) ?: ""
        return DeepgramClient(apiKey)
    }
    
    /**
     * Provides a singleton GeminiClient instance
     * Note: API key should be provided at connection time
     */
    @Provides
    @Singleton
    fun provideGeminiClient(
        @ApplicationContext context: Context
    ): GeminiClient {
        val apiKey = SecureStorage.getGeminiKey(context) ?: ""
        return GeminiClient(apiKey)
    }
    
    /**
     * Provides a singleton Room Database instance
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getInstance(context)
    }
    
    /**
     * Provides a singleton OfflineAudioProcessor instance
     */
    @Provides
    @Singleton
    fun provideOfflineAudioProcessor(
        @ApplicationContext context: Context
    ): OfflineAudioProcessor {
        return OfflineAudioProcessor(context)
    }
}
