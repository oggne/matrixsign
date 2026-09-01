package com.matrixsign

import android.content.Context
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * DEPRECATED: Single-frame 968-class MLP classifier (legacy).
 * 
 * This classifier is NO LONGER USED in the live recognition path as of Sprint 1.
 * It has been replaced by RslSequenceClassifier which:
 * - Uses a sequence of frames (not single frame)
 * - Targets 36-word vocabulary from rsl_dictionary.json (not 968 Slovo classes)
 * - Supports hold-to-sign UX
 * 
 * This file is kept for reference during migration but should be removed after
 * Sprint 1 verification is complete.
 * 
 * See: RslSequenceClassifier.kt (NEW)
 * See: docs/RSL_SEQUENCE_TRAINING.md
 * 
 * Original description:
 * Классификатор жестов РЖЯ на основе TFLite модели.
 * Принимает на вход landmarks от MediaPipe и выдает метку жеста.
 * Optimized version: pre-allocates buffers.
 */
@Deprecated(
    message = "Use RslSequenceClassifier instead. This single-frame classifier is no longer used.",
    replaceWith = ReplaceWith("RslSequenceClassifier"),
    level = DeprecationLevel.WARNING
)
class RslClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var isInitialized = false
    
    // Pre-allocated buffers to avoid GC pressure
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: Array<FloatArray>? = null

    /**
     * Инициализация классификатора.
     * Загружает модель rsl_classifier.tflite из filesDir (если есть) или assets.
     */
    fun initialize() {
        if (isInitialized) return
        
        try {
            android.util.Log.d("RslClassifier", "Initializing RSL classifier...")
            
            // Сначала ищем модель во внутреннем хранилище (обученную пользователем)
            val customModelFile = File(context.filesDir, "rsl_classifier.tflite")
            val modelBuffer: ByteBuffer? = if (customModelFile.exists()) {
                android.util.Log.d("RslClassifier", "Loading custom model from filesDir")
                loadModelFromFile(customModelFile)
            } else {
                // Иначе пытаемся загрузить из assets (предобученная)
                android.util.Log.d("RslClassifier", "Loading model from assets")
                loadModelFromAssets("rsl_classifier.tflite")
            }

            if (modelBuffer != null) {
                android.util.Log.d("RslClassifier", "Model buffer loaded, size: ${modelBuffer.capacity()} bytes")
                
                val options = Interpreter.Options()
                // Use XNNPACK delegate if available for better CPU performance on Android
                // options.setUseXNNPACK(true) 
                
                interpreter = Interpreter(modelBuffer, options)
                android.util.Log.d("RslClassifier", "TFLite interpreter created")
                
                // Загружаем метки (labels)
                val customLabelsFile = File(context.filesDir, "rsl_labels.txt")
                labels = if (customLabelsFile.exists()) {
                    android.util.Log.d("RslClassifier", "Loading custom labels from filesDir")
                    customLabelsFile.readLines()
                } else {
                    android.util.Log.d("RslClassifier", "Loading labels from assets")
                    loadLabelsFromAssets("rsl_labels.txt")
                }
                
                android.util.Log.d("RslClassifier", "Loaded ${labels.size} labels")
                
                // Pre-allocate buffers
                // Input: [1, 63] (21 points * 3 coords) * 4 bytes/float
                inputBuffer = ByteBuffer.allocateDirect(21 * 3 * 4).apply {
                    order(ByteOrder.nativeOrder())
                }
                
                // Output: [1, num_classes]
                if (labels.isNotEmpty()) {
                    outputBuffer = Array(1) { FloatArray(labels.size) }
                }

                isInitialized = true
                android.util.Log.i("RslClassifier", "RSL classifier initialized successfully with ${labels.size} classes")
            } else {
                android.util.Log.e("RslClassifier", "Failed to load model buffer")
                isInitialized = false
            }
        } catch (e: Exception) {
            android.util.Log.e("RslClassifier", "Failed to initialize RSL classifier", e)
            e.printStackTrace()
            isInitialized = false
        }
    }

    private fun loadModelFromFile(file: File): ByteBuffer {
        val inputStream = FileInputStream(file)
        val fileChannel = inputStream.channel
        val startOffset = 0L
        val declaredLength = fileChannel.size()
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadModelFromAssets(fileName: String): ByteBuffer? {
        return try {
            val fileDescriptor = context.assets.openFd(fileName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            // Резервный механизм: копируем во временный кэш, если AAPT сжал модель при упаковке
            try {
                val cacheFile = File(context.cacheDir, fileName)
                if (!cacheFile.exists()) {
                    context.assets.open(fileName).use { input ->
                        java.io.FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                loadModelFromFile(cacheFile)
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
        }
    }
    
    private fun loadLabelsFromAssets(fileName: String): List<String> {
        return try {
            context.assets.open(fileName).bufferedReader().readLines()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Result of RSL classification with label and confidence
     */
    data class ClassificationResult(
        val label: String,
        val confidence: Float,
        val passedThreshold: Boolean
    )

    /**
     * Классификация жеста.
     * @param result Результат от MediaPipe HandLandmarker
     * @return Название жеста или null, если уверенность низкая или модель не готова.
     */
    fun classify(result: HandLandmarkerResult): String? {
        val fullResult = classifyWithConfidence(result)
        return if (fullResult?.passedThreshold == true) fullResult.label else null
    }
    
    /**
     * Классификация жеста с полной информацией о confidence.
     * Используется для показа необработанного top-1 в UI.
     */
    fun classifyWithConfidence(result: HandLandmarkerResult): ClassificationResult? {
        android.util.Log.d("RslClassifier", "classify() called")
        
        if (!isInitialized || interpreter == null || labels.isEmpty()) {
            android.util.Log.w("RslClassifier", "Classifier not ready: initialized=$isInitialized, interpreter=${interpreter != null}, labels=${labels.size}")
            return null
        }
        
        // Берем первую руку
        if (result.landmarks().isEmpty()) {
            android.util.Log.d("RslClassifier", "No landmarks detected")
            return null
        }
        val landmarks = result.landmarks()[0]
        
        android.util.Log.d("RslClassifier", "Processing ${landmarks.size} landmarks")
        
        // Reset input buffer position
        val input = inputBuffer ?: return null
        input.rewind()
        
        // 1. Центрирование (wrist-relative): смещаем координаты относительно запястья (точка 0)
        val wristX = landmarks[0].x()
        val wristY = landmarks[0].y()
        val wristZ = landmarks[0].z()
        
        val coordsNorm = FloatArray(21 * 3)
        for (i in 0 until 21) {
            val landmark = landmarks[i]
            coordsNorm[i * 3] = landmark.x() - wristX
            coordsNorm[i * 3 + 1] = landmark.y() - wristY
            coordsNorm[i * 3 + 2] = landmark.z() - wristZ
        }
        
        // 2. Масштабирование (unit-scale): делим на максимальное расстояние от запястья до любой точки
        var maxDist = 0.0f
        for (i in 0 until 21) {
            val x = coordsNorm[i * 3]
            val y = coordsNorm[i * 3 + 1]
            val z = coordsNorm[i * 3 + 2]
            val dist = kotlin.math.sqrt(x * x + y * y + z * z)
            if (dist > maxDist) {
                maxDist = dist
            }
        }
        
        if (maxDist > 0.0f) {
            for (i in 0 until coordsNorm.size) {
                coordsNorm[i] /= maxDist
            }
        }
        
        // Записываем нормализованные данные во входной буфер TFLite
        for (coord in coordsNorm) {
            input.putFloat(coord)
        }
        
        // CRITICAL FIX: Rewind buffer after filling so TFLite can read from position 0
        input.rewind()
        android.util.Log.d("RslClassifier", "Input buffer prepared: position=${input.position()}, capacity=${input.capacity()}")
        
        // Run inference
        val output = outputBuffer ?: return null
        
        try {
            interpreter?.run(input, output)
            android.util.Log.d("RslClassifier", "TFLite inference completed")
        } catch (e: Exception) {
            android.util.Log.e("RslClassifier", "TFLite inference failed", e)
            return null
        }
        
        // Поиск максимума
        val probabilities = output[0]
        var maxIndex = -1
        var maxProb = 0.0f
        
        for (i in probabilities.indices) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i]
                maxIndex = i
            }
        }
        
        val topLabel = if (maxIndex >= 0 && maxIndex < labels.size) labels[maxIndex] else "?"
        
        // ALWAYS log classification result for debugging
        android.util.Log.d("RslClassifier", "Top prediction: index=$maxIndex, label='$topLabel', confidence=$maxProb (threshold=0.4)")
        
        val passedThreshold = maxIndex != -1 && maxProb > 0.4f && maxIndex < labels.size
        
        if (passedThreshold) {
            android.util.Log.i("RslClassifier", "✓ Recognized: '$topLabel' (confidence=$maxProb)")
        } else {
            android.util.Log.d("RslClassifier", "✗ Below threshold or invalid: maxProb=$maxProb, maxIndex=$maxIndex")
        }
        
        return ClassificationResult(
            label = topLabel,
            confidence = maxProb,
            passedThreshold = passedThreshold
        )
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
        inputBuffer = null
        outputBuffer = null
    }
}

