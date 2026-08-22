package com.matrixsign.di

import android.content.Context
import com.matrixsign.GestureLibraryManager
import com.matrixsign.SettingsManager
import com.matrixsign.MudraManager
import com.matrixsign.T9Predictor
import com.matrixsign.SpeechRecognizerHelper
import com.matrixsign.TextToSpeechHelper
import com.matrixsign.ArGlassesManager
import com.matrixsign.TranslationHelper
import com.matrixsign.LanguageManager
import com.matrixsign.SpeakerManager
import com.matrixsign.GestureToTextMapper
import com.matrixsign.DeviceAutoDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGestureLibraryManager(@ApplicationContext context: Context) = GestureLibraryManager(context)

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context) = SettingsManager(context)

    @Provides
    @Singleton
    fun provideMudraManager(@ApplicationContext context: Context) = MudraManager(context)

    @Provides
    @Singleton
    fun provideT9Predictor(@ApplicationContext context: Context) = T9Predictor(context)

    @Provides
    @Singleton
    fun provideSpeechRecognizerHelper(@ApplicationContext context: Context) = SpeechRecognizerHelper(context)

    @Provides
    @Singleton
    fun provideTextToSpeechHelper(@ApplicationContext context: Context) = TextToSpeechHelper(context)

    @Provides
    @Singleton
    fun provideArGlassesManager(@ApplicationContext context: Context) = ArGlassesManager(context)

    @Provides
    @Singleton
    fun provideTranslationHelper(@ApplicationContext context: Context) = TranslationHelper(context)

    @Provides
    @Singleton
    fun provideLanguageManager(@ApplicationContext context: Context) = LanguageManager(context)

    @Provides
    @Singleton
    fun provideSpeakerManager() = SpeakerManager()

    @Provides
    @Singleton
    fun provideGestureToTextMapper(@ApplicationContext context: Context, gestureLibraryManager: GestureLibraryManager) = GestureToTextMapper(context, gestureLibraryManager)

    @Provides
    @Singleton
    fun provideDeviceAutoDetector(@ApplicationContext context: Context) = DeviceAutoDetector(context)
}