package com.matrixsign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class GestureRecognizerHelper(
    private val context: Context,
    private val onResults: (GestureRecognizerResult) -> Unit,
    private val onHandLandmarks: (List<NormalizedLandmarkList>) -> Unit,
    private val onError: (String, Exception?) -> Unit,
    private val onCustomGesture: ((String) -> Unit)? = null,
    initialSignLanguage: String = LanguageManager.USER_SIGN_LANGUAGE_RSL
) {
    private var gestureRecognizer: GestureRecognizer? = null
    private var handLandmarker: HandLandmarker? = null
    private var customGestureRecognizers: MutableMap<String, GestureRecognizer> = mutableMapOf()
    private val rslClassifier = RslClassifier(context)
    private val gestureSmoother = GestureSmoother()
    
    private val gestureLibraryManager = GestureLibraryManager(context)
    private val userIdManager = UserIdManager(context)
    private val languageManager = LanguageManager(context)
    
    // Текущий язык жестов - initialize with default to avoid race condition
    private var currentSignLanguage: String = initialSignLanguage
    
    // Track RSL classifier availability
    private var isRslInitialized = false
    
    // Базовые опции для разных моделей
    private val baseOptionsDefault = BaseOptions.builder()
        .setDelegate(Delegate.GPU)
        .setModelAssetPath("gesture_recognizer.task")
        .build()
    
    // Опции для Slovo (РЖЯ) - если модель доступна
    private val baseOptionsSlovo = try {
        // Проверяем наличие файла в assets
        val modelName = "slovo_gestures.task"
        val assets = context.assets.list("")
        if (assets?.contains(modelName) == true) {
            BaseOptions.builder()
                .setDelegate(Delegate.GPU)
                .setModelAssetPath(modelName)
                .build()
        } else {
            null
        }
    } catch (e: Exception) {
        null // Модель может отсутствовать
    }

    private val isRslClassifierAvailable = try {
        context.assets.list("")?.contains("rsl_classifier.tflite") == true
    } catch (e: Exception) {
        false
    }

    fun isRslModelAvailable(): Boolean {
        return baseOptionsSlovo != null || isRslClassifierAvailable
    }

    private val handBaseOptions = BaseOptions.builder()
        .setDelegate(Delegate.GPU)
        .setModelAssetPath("hand_landmarker.task")
        .build()

    init {
        setupGestureRecognizer()
        setupHandLandmarker()
        try {
            rslClassifier.initialize()
            isRslInitialized = true
            android.util.Log.d("GestureRecognizerHelper", "RSL classifier initialized successfully")
        } catch (e: Exception) {
            isRslInitialized = false
            android.util.Log.w("GestureRecognizerHelper", "RSL classifier not available: ${e.message}")
        }
        // Загрузка кастомных моделей будет выполнена асинхронно через loadCustomModels()
    }
    
    /**
     * Переключить модель распознавания жестов в зависимости от языка
     */
    suspend fun switchSignLanguage(signLanguage: String) = withContext(Dispatchers.IO) {
        if (currentSignLanguage == signLanguage) return@withContext
        
        currentSignLanguage = signLanguage
        
        // Закрываем старый распознаватель
        gestureRecognizer?.close()
        gestureRecognizer = null
        
        // Сбрасываем сглаживатель
        gestureSmoother.reset()
        
        // Выбираем модель в зависимости от языка жестов
        val modelPath = when (signLanguage.lowercase()) {
            LanguageManager.USER_SIGN_LANGUAGE_RSL -> {
                // Пытаемся использовать Slovo для РЖЯ
                if (baseOptionsSlovo != null) {
                    "slovo_gestures.task"
                } else {
                    "gesture_recognizer.task" // Fallback на стандартную модель
                }
            }
            LanguageManager.USER_SIGN_LANGUAGE_ASL -> {
                "gesture_recognizer.task" // Стандартная модель MediaPipe поддерживает ASL
            }
            LanguageManager.USER_SIGN_LANGUAGE_USL -> {
                "gesture_recognizer.task" // Пока используем стандартную модель
            }
            else -> "gesture_recognizer.task"
        }
        
        // Создаём новый распознаватель с выбранной моделью
        try {
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.GPU)
                .setModelAssetPath(modelPath)
                .build()
            
            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumHands(2)
                .setResultListener { result: GestureRecognizerResult, _: MPImage ->
                    onResults(result)
                }
                .setErrorListener { error: RuntimeException ->
                    onError("GestureRecognizer error: ${error.message}", error)
                }
                .build()
            
            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
        } catch (e: Exception) {
            onError("Failed to switch to model $modelPath: ${e.message}", e)
            // Fallback на стандартную модель
            setupGestureRecognizer()
        }
    }
    
    /**
     * Загрузка кастомных моделей для пользователя
     * Вызывается из MainActivity или DialogScreen
     */
    suspend fun loadCustomModels() = withContext(Dispatchers.IO) {
        try {
            val userId = userIdManager.getUserId()
            val gestures = gestureLibraryManager.getAllGestures(userId)
            
            // Получаем первый snapshot списка жестов
            val gestureList = gestures.first()
            
            gestureList.forEach { gesture ->
                val modelPath = gesture.modelPath
                val modelFile = File(context.filesDir, modelPath)
                
                if (modelFile.exists()) {
                    try {
                        val customBaseOptions = BaseOptions.builder()
                            .setDelegate(Delegate.GPU)
                            .setModelAssetPath(modelFile.absolutePath)
                            .build()
                        
                        val customOptions = GestureRecognizer.GestureRecognizerOptions.builder()
                            .setBaseOptions(customBaseOptions)
                            .setRunningMode(RunningMode.LIVE_STREAM)
                            .setMinHandDetectionConfidence(0.5f)
                            .setMinHandPresenceConfidence(0.5f)
                            .setMinTrackingConfidence(0.5f)
                            .setNumHands(2)
                            .setResultListener { result: GestureRecognizerResult, _: MPImage ->
                                onResults(result)
                            }
                            .setErrorListener { error: RuntimeException ->
                                onError("Custom GestureRecognizer error: ${error.message}", error)
                            }
                            .build()
                        
                        val customRecognizer = GestureRecognizer.createFromOptions(context, customOptions)
                        customGestureRecognizers[gesture.gestureLabel] = customRecognizer
                    } catch (e: Exception) {
                        onError("Failed to load custom model for ${gesture.gestureLabel}: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            onError("Failed to load custom models: ${e.message}", e)
        }
    }
    
    /**
     * Загрузить кастомную модель для конкретного жеста
     */
    suspend fun loadCustomModel(gestureLabel: String, modelPath: String) = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(context.filesDir, modelPath)
            if (!modelFile.exists()) {
                onError("Custom model file not found: $modelPath", null)
                return@withContext
            }
            
            val customBaseOptions = BaseOptions.builder()
                .setDelegate(Delegate.GPU)
                .setModelAssetPath(modelFile.absolutePath)
                .build()
            
            val customOptions = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(customBaseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumHands(2)
                .setResultListener { result: GestureRecognizerResult, _: MPImage ->
                    onResults(result)
                }
                .setErrorListener { error: RuntimeException ->
                    onError("Custom GestureRecognizer error: ${error.message}", error)
                }
                .build()
            
            val customRecognizer = GestureRecognizer.createFromOptions(context, customOptions)
            customGestureRecognizers[gestureLabel] = customRecognizer
        } catch (e: Exception) {
            onError("Failed to load custom model: ${e.message}", e)
        }
    }

    private fun setupGestureRecognizer() {
        try {
            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptionsDefault)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumHands(2)
                .setResultListener { result: GestureRecognizerResult, _: MPImage ->
                    onResults(result)
                }
                .setErrorListener { error: RuntimeException ->
                    onError("GestureRecognizer error: ${error.message}", error)
                }
                .build()

            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
        } catch (e: Exception) {
            onError("Failed to initialize GestureRecognizer: ${e.message}", e)
        }
    }

    private fun setupHandLandmarker() {
        try {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(handBaseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumHands(2)
                .setResultListener { result: HandLandmarkerResult, _: MPImage ->
                    val handLandmarkProtoLists = result.landmarks().map { handLandmarks ->
                        val landmarkListBuilder = NormalizedLandmarkList.newBuilder()
                        handLandmarks.forEach { landmark ->
                            val protoLandmark = NormalizedLandmark.newBuilder()
                                .setX(landmark.x())
                                .setY(landmark.y())
                                .setZ(landmark.z())
                                .build()
                            landmarkListBuilder.addLandmark(protoLandmark)
                        }
                        landmarkListBuilder.build()
                    }
                    onHandLandmarks(handLandmarkProtoLists)
                    
                    // RSL Classification (только если выбран язык RSL и модель доступна)
                    if (currentSignLanguage == LanguageManager.USER_SIGN_LANGUAGE_RSL && isRslInitialized) {
                        try {
                            val rslResult = rslClassifier.classify(result)
                            // Smooth the result
                            val smoothedResult = gestureSmoother.process(rslResult)
                            
                            if (smoothedResult != null) {
                                onCustomGesture?.invoke(smoothedResult)
                            }
                        } catch (e: Exception) {
                            // Ignore classification errors to not stop the stream
                            android.util.Log.e("GestureRecognizerHelper", "RSL classification error", e)
                        }
                    }
                }
                .setErrorListener { error: RuntimeException ->
                    onError("HandLandmarker error: ${error.message}", error)
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            onError("Failed to initialize HandLandmarker: ${e.message}", e)
        }
    }
    
    /**
     * Check if RSL classifier is available and initialized
     */
    fun isRslAvailable(): Boolean {
        return isRslInitialized
    }

    @OptIn(ExperimentalGetImage::class)
    fun recognizeGesture(imageProxy: ImageProxy) {
        // Блок `use` гарантирует, что imageProxy будет закрыт, даже если возникнет исключение.
        imageProxy.use { proxy ->
            try {
                // Создаем Bitmap для хранения данных из ImageProxy.
                // Требует, чтобы формат ImageAnalysis был OUTPUT_IMAGE_FORMAT_RGBA_8888.
                val bitmapBuffer = Bitmap.createBitmap(
                    proxy.width,
                    proxy.height,
                    Bitmap.Config.ARGB_8888
                )

                // Копируем пиксели из ImageProxy в Bitmap.
                val buffer = proxy.planes[0].buffer
                buffer.rewind()
                bitmapBuffer.copyPixelsFromBuffer(buffer)

                // Создаем матрицу для поворота и зеркального отражения.
                val matrix = Matrix()
                matrix.postRotate(proxy.imageInfo.rotationDegrees.toFloat())

                // Зеркально отражаем изображение по горизонтали для фронтальной камеры.
                matrix.postScale(-1f, 1f, proxy.width / 2f, proxy.height / 2f)

                val rotatedBitmap = Bitmap.createBitmap(
                    bitmapBuffer, 0, 0,
                    proxy.width, proxy.height,
                    matrix, true
                )

                // Создаем MPImage из повернутого и отраженного Bitmap.
                val mpImage = BitmapImageBuilder(rotatedBitmap).build()
                val timestamp = proxy.imageInfo.timestamp

                // Запускаем асинхронное распознавание.
                gestureRecognizer?.recognizeAsync(mpImage, timestamp)
                handLandmarker?.detectAsync(mpImage, timestamp)

            } catch (e: Exception) {
                onError("Failed to process image: ${e.message}", e)
            }
        }
    }

    fun close() {
        try {
            gestureRecognizer?.close()
            handLandmarker?.close()
            // Закрываем все кастомные распознаватели
            customGestureRecognizers.values.forEach { it.close() }
            customGestureRecognizers.values.forEach { it.close() }
            customGestureRecognizers.clear()
            rslClassifier.close()
        } catch (e: Exception) {
            onError("Error closing recognizers: ${e.message}", e)
        }
    }
}
