package com.matrixsign

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Менеджер для управления языковыми настройками
 * Хранит выбранные языки оппонента (речь) и пользователя (жесты)
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "language_settings")

/**
 * Менеджер для управления языковыми настройками
 * Хранит выбранные языки оппонента (речь) и пользователя (жесты)
 */
class LanguageManager(private val context: Context) {
    
    companion object {
        // Языки речи (оппонент)
        const val OPPONENT_LANGUAGE_RU = "ru"
        const val OPPONENT_LANGUAGE_EN = "en"
        const val OPPONENT_LANGUAGE_UK = "uk"
        
        // Языки жестов (пользователь)
        const val USER_SIGN_LANGUAGE_RSL = "rsl" // Русский жестовый язык
        const val USER_SIGN_LANGUAGE_ASL = "asl" // Американский жестовый язык
        const val USER_SIGN_LANGUAGE_USL = "usl" // Украинский жестовый язык
        
        // Значения по умолчанию
        const val DEFAULT_OPPONENT_LANGUAGE = OPPONENT_LANGUAGE_RU
        const val DEFAULT_USER_SIGN_LANGUAGE = USER_SIGN_LANGUAGE_RSL
        
        private val OPPONENT_LANGUAGE_KEY = stringPreferencesKey("opponent_language")
        private val USER_SIGN_LANGUAGE_KEY = stringPreferencesKey("user_sign_language")
    }
    
    /**
     * Получить язык оппонента (речь)
     */
    val opponentLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[OPPONENT_LANGUAGE_KEY] ?: DEFAULT_OPPONENT_LANGUAGE
    }
    
    /**
     * Получить язык жестов пользователя
     */
    val userSignLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_SIGN_LANGUAGE_KEY] ?: DEFAULT_USER_SIGN_LANGUAGE
    }
    
    /**
     * Установить язык оппонента
     */
    suspend fun setOpponentLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[OPPONENT_LANGUAGE_KEY] = language
        }
    }
    
    /**
     * Установить язык жестов пользователя
     */
    suspend fun setUserSignLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_SIGN_LANGUAGE_KEY] = language
        }
    }
    
    /**
     * Получить язык оппонента синхронно (для быстрого доступа)
     */
    suspend fun getOpponentLanguageSync(): String {
        return opponentLanguage.first() ?: DEFAULT_OPPONENT_LANGUAGE
    }
    
    /**
     * Получить язык жестов пользователя синхронно
     */
    suspend fun getUserSignLanguageSync(): String {
        return userSignLanguage.first() ?: DEFAULT_USER_SIGN_LANGUAGE
    }
    
    /**
     * Получить код языка речи для языка жестов
     */
    fun getSpeechLanguageCode(signLanguage: String): String {
        return when (signLanguage.lowercase()) {
            USER_SIGN_LANGUAGE_RSL -> OPPONENT_LANGUAGE_RU
            USER_SIGN_LANGUAGE_ASL -> OPPONENT_LANGUAGE_EN
            USER_SIGN_LANGUAGE_USL -> OPPONENT_LANGUAGE_UK
            else -> DEFAULT_OPPONENT_LANGUAGE
        }
    }
}

