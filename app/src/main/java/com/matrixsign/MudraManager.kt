package com.matrixsign

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Менеджер для работы с Mudra Link SDK
 * Управляет Bluetooth-подключением и специальными жестами
 *
 * ⚠️ БЕЗОПАСНОСТЬ — ЭТО ЗАГЛУШКА, НЕ РЕАЛЬНАЯ АУТЕНТИФИКАЦИЯ:
 * connectToMudra() сейчас доверяет любому уже сопряжённому Bluetooth-устройству,
 * чьё имя содержит "Mudra" — никакого GATT-хендшейка, обмена ключами или проверки
 * подлинности устройства не происходit. Подделать имя BLE-устройства тривиально,
 * так что в текущем виде это не защита, а плейсхолдер для разработки.
 * НЕЛЬЗЯ полагаться на этот класс как на границу доверия (например, не давать
 * Mudra-устройству прав на действия, которые нежелательны от постороннего BLE-девайса)
 * до тех пор, пока сюда не будет добавлена настоящая интеграция с Mudra SDK
 * (подписанная характеристика / challenge-response при подключении). Это требует
 * официального SDK от производителя, которого в проекте пока нет.
 */
class MudraManager(private val context: Context) {
    
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val _currentGesture = MutableStateFlow<String?>(null)
    val currentGesture: StateFlow<String?> = _currentGesture
    
    // Специальный жест для подтверждения T9
    val T9_CONFIRM_GESTURE = "T9_CONFIRM"
    
    /**
     * Поиск и подключение к Mudra Link устройству
     */
    fun connectToMudra() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _isConnected.value = false
            return
        }
        
        // Поиск устройств Mudra (обычно имеют специфичное имя)
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        pairedDevices?.forEach { device ->
            if (device.name?.contains("Mudra", ignoreCase = true) == true) {
                // Подключение к устройству
                // В реальном приложении здесь нужна полная интеграция с Mudra SDK
                _isConnected.value = true
                return
            }
        }
        
        // Если устройство не найдено, симулируем подключение для разработки
        _isConnected.value = false
    }
    
    /**
     * Callback для подтверждения T9
     */
    var onT9ConfirmCallback: (() -> Unit)? = null
    
    /**
     * Обработка жеста от Mudra Link
     */
    fun processMudraGesture(gesture: String) {
        _currentGesture.value = gesture
        
        // Если это жест подтверждения T9
        if (gesture == T9_CONFIRM_GESTURE) {
            // Вызываем callback для подтверждения T9-выбора
            onT9ConfirmCallback?.invoke()
        }
    }
    
    /**
     * Установить callback для подтверждения T9
     */
    fun setT9ConfirmCallback(callback: () -> Unit) {
        onT9ConfirmCallback = callback
    }
    
    /**
     * Проверка, является ли жест T9_CONFIRM
     * Также проверяет базовые жесты MediaPipe (например, сжатый кулак)
     */
    fun isT9ConfirmGesture(gestureName: String): Boolean {
        return gestureName == T9_CONFIRM_GESTURE || 
               gestureName == "Closed_Fist" || // Сжатый кулак как альтернатива
               gestureName == "FIST"
    }
    
    fun disconnect() {
        _isConnected.value = false
        _currentGesture.value = null
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
}




