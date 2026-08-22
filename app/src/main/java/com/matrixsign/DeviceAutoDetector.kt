package com.matrixsign

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Автоматическое определение устройств через Bluetooth LE
 * Определяет: Mudra Link, Inmo Air3, Xreal Air, Ray-Ban Meta
 */
class DeviceAutoDetector(private val context: Context) {
    
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    
    private val _detectedDevice = MutableStateFlow<String>(SettingsManager.DEVICE_NONE)
    val detectedDevice: StateFlow<String> = _detectedDevice
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    // Известные UUID сервисов и имена устройств
    private val MUDRA_SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB") // Пример UUID для Mudra
    private val MUDRA_NAMES = listOf("Mudra", "MudraLink", "MUDRA")
    private val INMO_NAMES = listOf("Inmo", "InmoAir3", "INMO")
    private val XREAL_NAMES = listOf("Xreal", "XrealAir", "XREAL", "Nreal")
    private val RAYBAN_NAMES = listOf("Ray-Ban", "RayBan", "Meta", "RAYBAN")
    
    // Известные MAC адреса (если известны)
    private val knownMacAddresses: Map<String, String> = mapOf(
        // Можно добавить известные MAC адреса устройств
        // Пример: "AA:BB:CC:DD:EE:FF" to "Mudra Link"
    )
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: ""
            val deviceAddress = device.address
            
            android.util.Log.d("DeviceAutoDetector", "Found device: $deviceName ($deviceAddress)")
            
            // Проверка по имени
            when {
                MUDRA_NAMES.any { deviceName.contains(it, ignoreCase = true) } -> {
                    _detectedDevice.value = SettingsManager.DEVICE_MUDRA
                    stopScanning()
                }
                INMO_NAMES.any { deviceName.contains(it, ignoreCase = true) } -> {
                    _detectedDevice.value = SettingsManager.DEVICE_INMO_AIR3
                    stopScanning()
                }
                XREAL_NAMES.any { deviceName.contains(it, ignoreCase = true) } -> {
                    _detectedDevice.value = SettingsManager.DEVICE_XREAL_AIR2
                    stopScanning()
                }
                RAYBAN_NAMES.any { deviceName.contains(it, ignoreCase = true) } -> {
                    _detectedDevice.value = SettingsManager.DEVICE_RAY_BAN_META
                    stopScanning()
                }
            }
            
            // Проверка по UUID сервисов
            result.scanRecord?.serviceUuids?.forEach { uuid ->
                if (uuid.uuid == MUDRA_SERVICE_UUID) {
                    _detectedDevice.value = SettingsManager.DEVICE_MUDRA
                    stopScanning()
                }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            android.util.Log.e("DeviceAutoDetector", "BLE scan failed: $errorCode")
            _isScanning.value = false
        }
    }
    
    /**
     * Начать сканирование Bluetooth LE устройств
     */
    fun startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            android.util.Log.w("DeviceAutoDetector", "Bluetooth is not enabled")
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
            bluetoothLeScanner?.let { scanner ->
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                
                // Фильтр для Mudra (по UUID)
                val scanFilter = ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(MUDRA_SERVICE_UUID))
                    .build()
                
                try {
                    scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
                    _isScanning.value = true
                    android.util.Log.d("DeviceAutoDetector", "BLE scan started")
                } catch (e: Exception) {
                    android.util.Log.e("DeviceAutoDetector", "Failed to start scan", e)
                }
            }
        }
    }
    
    /**
     * Остановить сканирование
     */
    fun stopScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothLeScanner?.stopScan(scanCallback)
            _isScanning.value = false
            android.util.Log.d("DeviceAutoDetector", "BLE scan stopped")
        }
    }
    
    /**
     * Проверить подключённые устройства (bonded devices)
     */
    fun checkBondedDevices(): String {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            return SettingsManager.DEVICE_NONE
        }
        
        val bondedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        bondedDevices?.forEach { device ->
            val deviceName = device.name ?: ""
            
            when {
                MUDRA_NAMES.any { deviceName.contains(it, ignoreCase = true) } -> {
                    return SettingsManager.DEVICE_MUDRA
                }
                INMO_NAMES.any { deviceName.contains(it, ignoreCase = true) } -> {
                    return SettingsManager.DEVICE_INMO_AIR3
                }
                XREAL_NAMES.any { deviceName.contains(it, ignoreCase = true) } -> {
                    return SettingsManager.DEVICE_XREAL_AIR2
                }
                RAYBAN_NAMES.any { deviceName.contains(it, ignoreCase = true) } -> {
                    return SettingsManager.DEVICE_RAY_BAN_META
                }
            }
        }
        
        return SettingsManager.DEVICE_NONE
    }
    
    /**
     * Проверить, включён ли Bluetooth
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
}

