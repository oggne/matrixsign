package com.matrixsign

import android.content.Context
import android.content.SharedPreferences

/**
 * Менеджер для управления согласием на обработку биометрических данных
 * Соответствует требованиям 152-ФЗ и GDPR
 */
class ConsentManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "consent_prefs"
        private const val KEY_CONSENT_GIVEN = "biometric_consent_given"
        private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Проверить, дано ли согласие
     */
    fun hasConsent(): Boolean {
        return prefs.getBoolean(KEY_CONSENT_GIVEN, false)
    }
    
    /**
     * Получить timestamp согласия
     */
    fun getConsentTimestamp(): Long {
        return prefs.getLong(KEY_CONSENT_TIMESTAMP, 0L)
    }
    
    /**
     * Сохранить согласие
     */
    fun saveConsent() {
        prefs.edit()
            .putBoolean(KEY_CONSENT_GIVEN, true)
            .putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Отозвать согласие и удалить все данные
     */
    fun revokeConsent() {
        // Удаляем согласие
        prefs.edit()
            .putBoolean(KEY_CONSENT_GIVEN, false)
            .remove(KEY_CONSENT_TIMESTAMP)
            .clear() // Очищаем все SharedPreferences
            .apply()
        
        // Удаляем Room Database
        try {
            // Закрываем соединение с базой данных
            val database = GestureDatabase.getDatabase(context)
            database.close()
            
            // Удаляем файл базы данных
            context.deleteDatabase("gesture_database")
        } catch (e: Exception) {
            android.util.Log.e("ConsentManager", "Failed to delete database", e)
        }
        
        // Удаляем DataStore (настройки, включая user_id).
        // ВАЖНО: context.deleteSharedPreferences("settings") здесь не работает —
        // SettingsManager хранит данные в Preferences DataStore (файл
        // files/datastore/settings.preferences_pb), а не в классическом
        // SharedPreferences, так что вызов ниже раньше был no-op и user_id/настройки
        // переживали "отзыв согласия". Чистим DataStore напрямую через его API.
        try {
            kotlinx.coroutines.runBlocking {
                SettingsManager(context).clearAll()
            }
        } catch (e: Exception) {
            android.util.Log.e("ConsentManager", "Failed to clear settings DataStore", e)
        }
        try {
            context.deleteSharedPreferences("settings")
        } catch (e: Exception) {
            android.util.Log.e("ConsentManager", "Failed to delete legacy SharedPreferences", e)
        }

        android.util.Log.d("ConsentManager", "Consent revoked and all data deleted")
    }
}

