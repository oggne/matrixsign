package com.matrixsign

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Менеджер настроек приложения с использованием DataStore
 */
class SettingsManager(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
        
        // Ключи для настроек
        private val KEY_SELECTED_DEVICE = stringPreferencesKey("selected_device")
        private val KEY_MUDRA_CONNECTED = booleanPreferencesKey("mudra_connected")
        private val KEY_AR_DEVICE_TYPE = stringPreferencesKey("ar_device_type")
        private val KEY_AR_CONNECTED = booleanPreferencesKey("ar_connected")
        
        // Калибровка Mudra Link
        private val KEY_MUDRA_SENSITIVITY = floatPreferencesKey("mudra_sensitivity")
        private val KEY_MUDRA_GESTURE_THRESHOLD = floatPreferencesKey("mudra_gesture_threshold")
        
        // Калибровка AR очков
        private val KEY_AR_OVERLAY_X = floatPreferencesKey("ar_overlay_x")
        private val KEY_AR_OVERLAY_Y = floatPreferencesKey("ar_overlay_y")
        private val KEY_AR_OVERLAY_SCALE = floatPreferencesKey("ar_overlay_scale")
        private val KEY_AR_BRIGHTNESS = floatPreferencesKey("ar_brightness")
        
        // Размер шрифта
        private val KEY_FONT_SIZE = floatPreferencesKey("font_size")

        // Идентификатор пользователя/установки (для разделения жестов на общем устройстве)
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        
        // Значения по умолчанию
        const val DEVICE_NONE = "none"
        const val DEVICE_PHONE = "phone" // Смартфон (селфи-камера)
        const val DEVICE_MUDRA = "mudra"
        const val DEVICE_INMO_AIR3 = "inmo_air3"
        const val DEVICE_XREAL_AIR2 = "xreal_air2"
        const val DEVICE_XREAL_ULTRA = "xreal_ultra"
        const val DEVICE_RAY_BAN_META = "ray_ban_meta"
    }
    
    /**
     * Получить выбранное устройство
     */
    val selectedDevice: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_DEVICE] ?: DEVICE_NONE
    }
    
    /**
     * Сохранить выбранное устройство
     */
    suspend fun setSelectedDevice(device: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_DEVICE] = device
        }
    }
    
    /**
     * Получить настройки Mudra Link
     */
    suspend fun getMudraSettings(): MudraCalibrationSettings {
        val preferences = context.dataStore.data.first()
        return MudraCalibrationSettings(
            sensitivity = preferences[KEY_MUDRA_SENSITIVITY] ?: 0.5f,
            gestureThreshold = preferences[KEY_MUDRA_GESTURE_THRESHOLD] ?: 0.7f,
            isConnected = preferences[KEY_MUDRA_CONNECTED] ?: false
        )
    }
    
    /**
     * Получить Flow настроек Mudra Link
     */
    val mudraSettings: Flow<MudraCalibrationSettings> = context.dataStore.data.map { preferences ->
        MudraCalibrationSettings(
            sensitivity = preferences[KEY_MUDRA_SENSITIVITY] ?: 0.5f,
            gestureThreshold = preferences[KEY_MUDRA_GESTURE_THRESHOLD] ?: 0.7f,
            isConnected = preferences[KEY_MUDRA_CONNECTED] ?: false
        )
    }
    
    /**
     * Сохранить настройки калибровки Mudra Link
     */
    suspend fun saveMudraSettings(settings: MudraCalibrationSettings) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MUDRA_SENSITIVITY] = settings.sensitivity
            preferences[KEY_MUDRA_GESTURE_THRESHOLD] = settings.gestureThreshold
            preferences[KEY_MUDRA_CONNECTED] = settings.isConnected
        }
    }
    
    /**
     * Получить настройки AR очков
     */
    suspend fun getArSettings(): ArCalibrationSettings {
        val preferences = context.dataStore.data.first()
        return ArCalibrationSettings(
            deviceType = preferences[KEY_AR_DEVICE_TYPE] ?: DEVICE_NONE,
            overlayX = preferences[KEY_AR_OVERLAY_X] ?: 0.5f,
            overlayY = preferences[KEY_AR_OVERLAY_Y] ?: 0.5f,
            overlayScale = preferences[KEY_AR_OVERLAY_SCALE] ?: 1.0f,
            brightness = preferences[KEY_AR_BRIGHTNESS] ?: 0.8f,
            isConnected = preferences[KEY_AR_CONNECTED] ?: false
        )
    }
    
    /**
     * Получить Flow настроек AR очков
     */
    val arSettings: Flow<ArCalibrationSettings> = context.dataStore.data.map { preferences ->
        ArCalibrationSettings(
            deviceType = preferences[KEY_AR_DEVICE_TYPE] ?: DEVICE_NONE,
            overlayX = preferences[KEY_AR_OVERLAY_X] ?: 0.5f,
            overlayY = preferences[KEY_AR_OVERLAY_Y] ?: 0.5f,
            overlayScale = preferences[KEY_AR_OVERLAY_SCALE] ?: 1.0f,
            brightness = preferences[KEY_AR_BRIGHTNESS] ?: 0.8f,
            isConnected = preferences[KEY_AR_CONNECTED] ?: false
        )
    }
    
    /**
     * Сохранить настройки калибровки AR очков
     */
    suspend fun saveArSettings(settings: ArCalibrationSettings) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AR_DEVICE_TYPE] = settings.deviceType
            preferences[KEY_AR_OVERLAY_X] = settings.overlayX
            preferences[KEY_AR_OVERLAY_Y] = settings.overlayY
            preferences[KEY_AR_OVERLAY_SCALE] = settings.overlayScale
            preferences[KEY_AR_BRIGHTNESS] = settings.brightness
            preferences[KEY_AR_CONNECTED] = settings.isConnected
        }
    }
    
    /**
     * Получить размер шрифта (по умолчанию 22sp)
     */
    suspend fun getFontSize(): Float {
        val preferences = context.dataStore.data.first()
        return preferences[KEY_FONT_SIZE] ?: 22f
    }
    
    /**
     * Получить Flow размера шрифта
     */
    val fontSize: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_FONT_SIZE] ?: 22f
    }
    
    /**
     * Сохранить размер шрифта
     */
    suspend fun setFontSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FONT_SIZE] = size.coerceIn(18f, 32f)
        }
    }

    /**
     * Получить существующий user_id или сгенерировать и сохранить новый (UUID).
     * Заменяет прежний захардкоженный "default_user" — теперь у каждой установки
     * приложения (а не у всех пользователей общего устройства сразу) свой ID,
     * и данные жестов больше не смешиваются между людьми.
     */
    suspend fun getOrCreateUserId(): String {
        val existing = context.dataStore.data.first()[KEY_USER_ID]
        if (!existing.isNullOrBlank()) return existing

        val newId = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = newId
        }
        return newId
    }

    /**
     * Полностью очистить это DataStore (используется при отзыве согласия).
     * ВАЖНО: context.deleteSharedPreferences(...) НЕ удаляет файлы DataStore —
     * это разные механизмы хранения, несмотря на похожее название. Раньше
     * revokeConsent() полагался именно на deleteSharedPreferences и фактически
     * не очищал этот DataStore.
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences -> preferences.clear() }
    }
}

/**
 * Настройки калибровки Mudra Link
 */
data class MudraCalibrationSettings(
    val sensitivity: Float = 0.5f, // Чувствительность (0.0 - 1.0)
    val gestureThreshold: Float = 0.7f, // Порог распознавания жеста (0.0 - 1.0)
    val isConnected: Boolean = false
)

/**
 * Настройки калибровки AR очков
 */
data class ArCalibrationSettings(
    val deviceType: String = SettingsManager.DEVICE_NONE,
    val overlayX: Float = 0.5f, // Позиция overlay по X (0.0 - 1.0)
    val overlayY: Float = 0.5f, // Позиция overlay по Y (0.0 - 1.0)
    val overlayScale: Float = 1.0f, // Масштаб overlay (0.5 - 2.0)
    val brightness: Float = 0.8f, // Яркость overlay (0.0 - 1.0)
    val isConnected: Boolean = false
)

