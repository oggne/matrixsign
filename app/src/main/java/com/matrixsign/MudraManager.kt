package com.matrixsign

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

/**
 * Менеджер для работы с Mudra Link через BLE
 * Управляет Bluetooth LE подключением и специальными жестами
 * 
 * ВАЖНО: Это реальная BLE-имплементация с проверкой разрешений и GATT-подключением.
 * Для полной интеграции с официальным Mudra SDK необходимо добавить SDK из Maven
 * после получения доступа к нему. Текущая имплементация предоставляет интерфейс
 * для подключения к любому BLE-устройству с именем "Mudra" и может быть расширена.
 */
class MudraManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MudraManager"
        // Жест для подтверждения T9
        const val T9_CONFIRM_GESTURE = "T9_CONFIRM"
    }
    
    enum class ConnectionState {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val _currentGesture = MutableStateFlow<String?>(null)
    val currentGesture: StateFlow<String?> = _currentGesture
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    
    /**
     * Callback для подтверждения T9
     */
    var onT9ConfirmCallback: (() -> Unit)? = null
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { scanResult ->
                val device = scanResult.device
                val deviceName = if (hasBluetoothPermissions()) {
                    try {
                        device.name
                    } catch (e: SecurityException) {
                        null
                    }
                } else {
                    null
                }
                
                Log.d(TAG, "Found BLE device: ${deviceName ?: "Unknown"}")
                
                // Подключаемся к первому найденному устройству с именем "Mudra"
                if (deviceName?.contains("Mudra", ignoreCase = true) == true) {
                    stopScanning()
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            _connectionState.value = ConnectionState.ERROR
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _connectionState.value = ConnectionState.CONNECTED
                    _isConnected.value = true
                    connectedDevice = gatt?.device
                    
                    // Обнаружение сервисов GATT
                    if (hasBluetoothPermissions()) {
                        try {
                            gatt?.discoverServices()
                        } catch (e: SecurityException) {
                            Log.e(TAG, "No permission to discover services", e)
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _isConnected.value = false
                    connectedDevice = null
                    gatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                // Здесь можно подписаться на характеристики для получения данных жестов
                // Это требует знания UUID сервисов и характеристик Mudra SDK
            } else {
                Log.w(TAG, "Service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            // Обработка изменений характеристик (данные жестов от Mudra)
            characteristic?.value?.let { data ->
                // Здесь должна быть логика декодирования данных от Mudra SDK
                // Пока что это заглушка для демонстрации потока данных
                Log.d(TAG, "Characteristic changed: ${data.contentToString()}")
            }
        }
    }
    
    /**
     * Проверка наличия необходимых BLE-разрешений
     */
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == 
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == 
                PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == 
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == 
                PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Начать сканирование BLE-устройств
     */
    fun startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter is null or not enabled")
            _connectionState.value = ConnectionState.ERROR
            return
        }
        
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Missing Bluetooth permissions")
            _connectionState.value = ConnectionState.ERROR
            return
        }
        
        if (_connectionState.value == ConnectionState.SCANNING) {
            Log.d(TAG, "Already scanning")
            return
        }
        
        try {
            bluetoothLeScanner?.startScan(scanCallback)
            _connectionState.value = ConnectionState.SCANNING
            Log.d(TAG, "Started BLE scan")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during scan", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }
    
    /**
     * Остановить сканирование
     */
    fun stopScanning() {
        if (_connectionState.value != ConnectionState.SCANNING) {
            return
        }
        
        try {
            if (hasBluetoothPermissions()) {
                bluetoothLeScanner?.stopScan(scanCallback)
            }
            Log.d(TAG, "Stopped BLE scan")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException stopping scan", e)
        }
        
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }
    
    /**
     * Подключиться к конкретному устройству
     */
    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Missing Bluetooth permissions for connection")
            _connectionState.value = ConnectionState.ERROR
            return
        }
        
        try {
            _connectionState.value = ConnectionState.CONNECTING
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            Log.d(TAG, "Connecting to device: ${device.name ?: "Unknown"}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during connection", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }
    
    /**
     * Поиск и подключение к Mudra Link устройству
     * Сначала проверяет сопряженные устройства, затем начинает сканирование
     */
    fun connectToMudra() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            _connectionState.value = ConnectionState.ERROR
            return
        }
        
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Missing Bluetooth permissions")
            _connectionState.value = ConnectionState.ERROR
            return
        }
        
        // Сначала проверяем уже сопряженные устройства
        try {
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
            pairedDevices?.forEach { device ->
                if (device.name?.contains("Mudra", ignoreCase = true) == true) {
                    Log.d(TAG, "Found paired Mudra device: ${device.name}")
                    connectToDevice(device)
                    return
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException accessing paired devices", e)
        }
        
        // Если не найдено в сопряженных, начинаем сканирование
        Log.d(TAG, "No paired Mudra device found, starting scan")
        startScanning()
    }
    
    /**
     * Обработка жеста от Mudra Link
     * Этот метод может быть вызван из callback характеристики или камеры как fallback
     */
    fun processMudraGesture(gesture: String) {
        _currentGesture.value = gesture
        
        // Если это жест подтверждения T9
        if (gesture == T9_CONFIRM_GESTURE) {
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
    
    /**
     * Отключиться от устройства
     */
    fun disconnect() {
        stopScanning()
        
        try {
            if (hasBluetoothPermissions()) {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during disconnect", e)
        }
        
        bluetoothGatt = null
        connectedDevice = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _isConnected.value = false
        _currentGesture.value = null
    }
    
    /**
     * Проверка, включен ли Bluetooth
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Получить имя подключенного устройства
     */
    fun getConnectedDeviceName(): String? {
        if (!hasBluetoothPermissions()) return null
        
        return try {
            connectedDevice?.name
        } catch (e: SecurityException) {
            null
        }
    }
}




