package com.matrixsign

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.View
import android.widget.TextView
import androidx.camera.core.CameraSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Универсальный менеджер для AR-очков
 * Поддерживает реальное отображение на вторичном дисплее через DisplayManager + Presentation
 * Совместимо с AR-очками, которые появляются как внешний дисплей (Xreal, Inmo, и т.д.)
 */
class ArGlassesManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ArGlassesManager"
    }
    
    enum class ArDeviceType {
        NONE,
        INMO_AIR3,
        XREAL_AIR2,
        XREAL_ULTRA,
        RAY_BAN_META,
        UNKNOWN_AR_GLASSES // Внешний дисплей обнаружен, тип неизвестен
    }
    
    private val _deviceType = MutableStateFlow(ArDeviceType.NONE)
    val deviceType: StateFlow<ArDeviceType> = _deviceType
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val _transparentModeEnabled = MutableStateFlow(false)
    val transparentModeEnabled: StateFlow<Boolean> = _transparentModeEnabled
    
    private val displayManager: DisplayManager = 
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    
    private var currentPresentation: MatrixOverlayPresentation? = null
    private var externalDisplay: Display? = null
    
    init {
        detectDevice()
        detectExternalDisplay()
    }
    
    /**
     * Автоопределение типа AR-устройства по модели устройства
     * Это подсказка, реальное подключение проверяется через внешний дисплей
     */
    private fun detectDevice() {
        val model = Build.MODEL
        
        when {
            model.contains("Inmo", ignoreCase = true) || 
            model.contains("Air3", ignoreCase = true) -> {
                _deviceType.value = ArDeviceType.INMO_AIR3
            }
            model.contains("Xreal", ignoreCase = true) || 
            model.contains("Air2", ignoreCase = true) -> {
                _deviceType.value = ArDeviceType.XREAL_AIR2
            }
            model.contains("Ultra", ignoreCase = true) -> {
                _deviceType.value = ArDeviceType.XREAL_ULTRA
            }
            model.contains("Ray-Ban", ignoreCase = true) || 
            model.contains("Meta", ignoreCase = true) -> {
                _deviceType.value = ArDeviceType.RAY_BAN_META
            }
            else -> {
                // Тип не определен, но может быть обнаружен как внешний дисплей
                _deviceType.value = ArDeviceType.NONE
            }
        }
        
        Log.d(TAG, "Device type hint: ${_deviceType.value}")
    }
    
    /**
     * Обнаружение внешнего дисплея (AR-очки обычно появляются как вторичный дисплей)
     */
    private fun detectExternalDisplay() {
        val displays = displayManager.displays
        
        for (display in displays) {
            // Ищем не-дефолтный дисплей
            if (display.displayId != Display.DEFAULT_DISPLAY) {
                externalDisplay = display
                _isConnected.value = true
                
                // Если тип не определен по модели, помечаем как неизвестные очки
                if (_deviceType.value == ArDeviceType.NONE) {
                    _deviceType.value = ArDeviceType.UNKNOWN_AR_GLASSES
                }
                
                Log.d(TAG, "External display detected: ${display.name}, ID: ${display.displayId}")
                return
            }
        }
        
        // Внешний дисплей не найден
        externalDisplay = null
        _isConnected.value = false
        Log.d(TAG, "No external display detected")
    }
    
    /**
     * Подключение к AR-устройству
     * В новой реализации это проверяет наличие внешнего дисплея
     */
    fun connect() {
        detectExternalDisplay()
        
        if (_isConnected.value) {
            Log.d(TAG, "Connected to AR display: ${_deviceType.value}")
        } else {
            Log.d(TAG, "No AR display available")
        }
    }
    
    /**
     * Отображение overlay на внешнем дисплее AR-очков
     */
    fun showOverlay(content: String, landmarks: String? = null) {
        if (!_isConnected.value || externalDisplay == null) {
            Log.w(TAG, "Cannot show overlay: not connected to external display")
            return
        }
        
        // Закрываем старую презентацию если есть
        currentPresentation?.dismiss()
        
        // Создаем новую презентацию на внешнем дисплее
        currentPresentation = MatrixOverlayPresentation(context, externalDisplay!!, content, landmarks)
        currentPresentation?.show()
        
        Log.d(TAG, "Showing overlay on external display")
    }
    
    /**
     * Обновить содержимое overlay без пересоздания
     */
    fun updateOverlay(content: String, landmarks: String? = null) {
        currentPresentation?.updateContent(content, landmarks)
    }
    
    /**
     * Скрыть overlay
     */
    fun hideOverlay() {
        currentPresentation?.dismiss()
        currentPresentation = null
    }
    
    fun disconnect() {
        hideOverlay()
        _isConnected.value = false
        _transparentModeEnabled.value = false
        externalDisplay = null
    }
    
    /**
     * Получить CameraSelector для камеры AR очков
     * В реальной реализации AR-очки обычно используют внешнюю камеру телефона
     */
    fun getCameraSelector(): CameraSelector {
        return when (_deviceType.value) {
            ArDeviceType.INMO_AIR3, 
            ArDeviceType.XREAL_AIR2, 
            ArDeviceType.XREAL_ULTRA,
            ArDeviceType.RAY_BAN_META,
            ArDeviceType.UNKNOWN_AR_GLASSES -> {
                // Для AR очков используем фронтальную камеру как fallback
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            else -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }
    
    /**
     * Проверка, используется ли камера AR очков
     */
    fun isUsingArCamera(): Boolean {
        return _isConnected.value && _deviceType.value != ArDeviceType.NONE
    }
    
    /**
     * Запуск прозрачного режима для AR очков
     * В этом режиме фон камеры отключается, но landmarks и текст остаются видимыми
     */
    fun startTransparentMode() {
        if (!_isConnected.value) {
            Log.w(TAG, "Cannot start transparent mode: not connected")
            return
        }
        
        _transparentModeEnabled.value = true
        currentPresentation?.setTransparentMode(true)
        
        Log.d(TAG, "Transparent mode enabled for ${_deviceType.value}")
    }
    
    /**
     * Остановить прозрачный режим
     */
    fun stopTransparentMode() {
        _transparentModeEnabled.value = false
        currentPresentation?.setTransparentMode(false)
        
        Log.d(TAG, "Transparent mode disabled")
    }
    
    /**
     * Проверить доступность внешнего дисплея
     */
    fun isExternalDisplayAvailable(): Boolean {
        detectExternalDisplay()
        return _isConnected.value
    }
}

/**
 * Presentation для отображения Matrix-overlay на внешнем дисплее
 */
private class MatrixOverlayPresentation(
    context: Context,
    display: Display,
    initialContent: String,
    initialLandmarks: String?
) : Presentation(context, display) {
    
    private var contentTextView: TextView? = null
    private var landmarksTextView: TextView? = null
    private var rootView: View? = null
    
    private var content: String = initialContent
    private var landmarks: String? = initialLandmarks
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Создаем простой layout для отображения текста
        // В продакшене это должен быть полноценный Matrix-стиль UI из основного экрана
        rootView = View.inflate(context, android.R.layout.simple_list_item_2, null)
        
        contentTextView = rootView?.findViewById(android.R.id.text1)
        landmarksTextView = rootView?.findViewById(android.R.id.text2)
        
        contentTextView?.text = content
        contentTextView?.textSize = 24f
        contentTextView?.setTextColor(0xFF00FF41.toInt()) // Matrix green
        
        landmarksTextView?.text = landmarks ?: ""
        landmarksTextView?.textSize = 14f
        landmarksTextView?.setTextColor(0xFF00FF41.toInt())
        
        setContentView(rootView)
    }
    
    fun updateContent(newContent: String, newLandmarks: String?) {
        content = newContent
        landmarks = newLandmarks
        
        contentTextView?.text = content
        landmarksTextView?.text = landmarks ?: ""
    }
    
    fun setTransparentMode(enabled: Boolean) {
        if (enabled) {
            // В прозрачном режиме делаем фон прозрачным
            window?.decorView?.setBackgroundColor(0x00000000)
        } else {
            // Обычный режим - черный фон
            window?.decorView?.setBackgroundColor(0xFF000000.toInt())
        }
    }
}





