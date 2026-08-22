package com.matrixsign

// Force re-compile


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.matrixsign.ui.theme.MatrixSignTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Проверка согласия на обработку биометрических данных
        val consentManager = ConsentManager(this)
        if (!consentManager.hasConsent()) {
            // Если согласие не дано, переходим на экран согласия
            val intent = android.content.Intent(this, OnboardingConsentActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        // Инициализация базы данных жестов
        val gestureLibraryManager = GestureLibraryManager(this)
        val userIdManager = UserIdManager(this)
        val settingsManager = SettingsManager(this)
        val arGlassesManager = ArGlassesManager(this)
        val deviceAutoDetector = DeviceAutoDetector(this)
        
        // Автоопределение и проверку оборудования запускаем ТОЛЬКО при наличии разрешений.
        // Здесь мы просто инициализируем менеджеры. Саму логику запуска переносим в DialogScreen/ViewModel
        // или проверяем разрешения перед запуском.
        
        // Проверка наличия разрешений перед запуском фоновых задач
        if (checkPermissions()) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                autoDetectDevice(deviceAutoDetector, settingsManager, arGlassesManager)
                checkEquipment(settingsManager, arGlassesManager)
            }
        }
        
        setContent {
            MatrixSignTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Загрузка кастомных моделей при старте
                    LaunchedEffect(Unit) {
                        loadCustomModels(gestureLibraryManager, userIdManager)
                    }
                    
                    DialogScreen()
                }
            }
        }
    }
    
    /**
     * Загрузка кастомных моделей для текущего пользователя
     */
    private suspend fun loadCustomModels(
        gestureLibraryManager: GestureLibraryManager,
        userIdManager: UserIdManager
    ) = withContext(Dispatchers.IO) {
        try {
            val userId = userIdManager.getUserId()
            val gestures = gestureLibraryManager.getAllGestures(userId)
            
            // Получаем список жестов и логируем (используем first() для получения первого значения)
            val gestureList = gestures.first()
            android.util.Log.d("MainActivity", "Loaded ${gestureList.size} custom gestures for user $userId")
            gestureList.forEach { gesture ->
                android.util.Log.d("MainActivity", "Gesture: ${gesture.gestureLabel}, Model: ${gesture.modelPath}")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to load custom models", e)
        }
    }
    
    /**
     * Проверка оборудования при старте/возврате
     */
    private suspend fun checkEquipment(
        settingsManager: SettingsManager,
        arGlassesManager: ArGlassesManager
    ) = withContext(Dispatchers.IO) {
        try {
            val selectedDevice = settingsManager.selectedDevice.first()
            Log.d("MainActivity", "Selected device: $selectedDevice")
            
            // Подключаемся к AR очкам, если выбраны
            if (selectedDevice in listOf(
                SettingsManager.DEVICE_INMO_AIR3,
                SettingsManager.DEVICE_XREAL_AIR2,
                SettingsManager.DEVICE_XREAL_ULTRA,
                SettingsManager.DEVICE_RAY_BAN_META
            )) {
                arGlassesManager.connect()
                Log.d("MainActivity", "AR glasses connected")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to check equipment", e)
        }
    }
    
    /**
     * Автоопределение устройства через Bluetooth LE
     */
    private suspend fun autoDetectDevice(
        deviceAutoDetector: DeviceAutoDetector,
        settingsManager: SettingsManager,
        arGlassesManager: ArGlassesManager
    ) = withContext(Dispatchers.IO) {
        try {
            // Сначала проверяем подключённые устройства
            val bondedDevice = deviceAutoDetector.checkBondedDevices()
            if (bondedDevice != SettingsManager.DEVICE_NONE) {
                settingsManager.setSelectedDevice(bondedDevice)
                Log.d("MainActivity", "Auto-detected device from bonded: $bondedDevice")
                
                // Настраиваем AR очки если нужно
                if (bondedDevice in listOf(
                    SettingsManager.DEVICE_INMO_AIR3,
                    SettingsManager.DEVICE_XREAL_AIR2,
                    SettingsManager.DEVICE_XREAL_ULTRA,
                    SettingsManager.DEVICE_RAY_BAN_META
                )) {
                    arGlassesManager.connect()
                    arGlassesManager.startTransparentMode()
                }
                return@withContext
            }
            
            // Если не найдено в подключённых, запускаем сканирование BLE
            if (deviceAutoDetector.isBluetoothEnabled()) {
                deviceAutoDetector.startScanning()
                
                // Ждём до 5 секунд на обнаружение
                kotlinx.coroutines.delay(5000)
                
                val detectedDevice = deviceAutoDetector.detectedDevice.value
                if (detectedDevice != SettingsManager.DEVICE_NONE) {
                    settingsManager.setSelectedDevice(detectedDevice)
                    Log.d("MainActivity", "Auto-detected device from BLE scan: $detectedDevice")
                    
                    // Настраиваем AR очки если нужно
                    if (detectedDevice in listOf(
                        SettingsManager.DEVICE_INMO_AIR3,
                        SettingsManager.DEVICE_XREAL_AIR2,
                        SettingsManager.DEVICE_XREAL_ULTRA,
                        SettingsManager.DEVICE_RAY_BAN_META
                    )) {
                        arGlassesManager.connect()
                        arGlassesManager.startTransparentMode()
                    }
                }
                
                deviceAutoDetector.stopScanning()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to auto-detect device", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Проверяем оборудование при возврате на экран
        val settingsManager = SettingsManager(this)
        val arGlassesManager = ArGlassesManager(this)
        val deviceAutoDetector = DeviceAutoDetector(this)
        
        // Асинхронная проверка не блокирует UI
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            autoDetectDevice(deviceAutoDetector, settingsManager, arGlassesManager)
            checkEquipment(settingsManager, arGlassesManager)
        }
    }

    private fun checkPermissions(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                   checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true // Для старых версий (или если не используется runtime permissions для BT)
    }
}




