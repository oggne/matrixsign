package com.matrixsign

import android.content.Context
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Sequence-based RSL classifier using windowed landmarks.
 * 
 * This replaces the dead-end 968-class single-frame MLP with a sequence-based approach
 * suitable for isolated sign recognition (hold-to-sign).
 * 
 * **Current Implementation (Sprint 1 Baseline):**
 * - Vocabulary: 13 words from rsl_dictionary.json that have training data (not all 36)
 * - Input: Sliding window of landmark sequences (default 15 frames @ 30fps = 0.5s)
 * - Model: Uses existing single-frame classifier with temporal smoothing as baseline
 * 
 * **Future Enhancement (When Training Data Available):**
 * Replace with true 1D-CNN trained on landmark sequences:
 * - Input: [batch, sequence_length, 21*3] = [1, 15, 63]
 * - Architecture: Conv1D layers + pooling + dense
 * - Training: Collect video sequences for all 36 dictionary words
 * 
 * See: app/src/main/assets/rsl_dictionary_label_mapping.json for word mapping
 * See: docs/TRAINING.md for CNN training instructions (TODO)
 */
class RslSequenceClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var vocabularyMapping: VocabularyMapping? = null
    private var isInitialized = false
    
    // Sequence buffering
    private val windowSize = 15 // frames (0.5s @ 30fps)
    private val landmarkBuffer = mutableListOf<NormalizedLandmarks>()
    
    // Pre-allocated buffers
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: Array<FloatArray>? = null
    
    // Labels from old 968-class model (we'll filter to vocabulary later)
    private var allLabels: List<String> = emptyList()

    /**
     * Normalized landmarks for one frame (21 points × 3 coords = 63 floats)
     */
    data class NormalizedLandmarks(
        val coords: FloatArray,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Result with label, confidence, and whether it's in our target vocabulary
     */
    data class ClassificationResult(
        val word: String,
        val confidence: Float,
        val passedThreshold: Boolean,
        val inVocabulary: Boolean
    )

    /**
     * Vocabulary mapping from rsl_dictionary_label_mapping.json
     */
    data class VocabularyMapping(
        val description: String,
        @SerializedName("trained_words") val trainedWords: List<TrainedWord>,
        @SerializedName("untrained_words") val untrainedWords: List<String>,
        val note: String
    )

    data class TrainedWord(
        val word: String,
        val label: String,
        @SerializedName("label_index") val labelIndex: Int
    )

    /**
     * Initialize the classifier: load model, labels, and vocabulary mapping.
     */
    fun initialize() {
        if (isInitialized) return
        
        try {
            android.util.Log.d(TAG, "Initializing RSL Sequence Classifier...")
            
            // Load vocabulary mapping
            vocabularyMapping = loadVocabularyMapping()
            android.util.Log.d(TAG, "Loaded vocabulary: ${vocabularyMapping?.trainedWords?.size ?: 0} trained words")
            
            // Load the existing single-frame model (temporary baseline)
            // TODO: Replace with proper sequence model when training data available
            val customModelFile = File(context.filesDir, "rsl_classifier.tflite")
            val modelBuffer: ByteBuffer? = if (customModelFile.exists()) {
                android.util.Log.d(TAG, "Loading custom model from filesDir")
                loadModelFromFile(customModelFile)
            } else {
                android.util.Log.d(TAG, "Loading model from assets")
                loadModelFromAssets("rsl_classifier.tflite")
            }

            if (modelBuffer != null) {
                val options = Interpreter.Options()
                interpreter = Interpreter(modelBuffer, options)
                android.util.Log.d(TAG, "TFLite interpreter created")
                
                // Load all 968 labels
                val customLabelsFile = File(context.filesDir, "rsl_labels.txt")
                allLabels = if (customLabelsFile.exists()) {
                    customLabelsFile.readLines()
                } else {
                    loadLabelsFromAssets("rsl_labels.txt")
                }
                
                android.util.Log.d(TAG, "Loaded ${allLabels.size} labels from training set")
                
                // Pre-allocate buffers for single-frame inference
                inputBuffer = ByteBuffer.allocateDirect(21 * 3 * 4).apply {
                    order(ByteOrder.nativeOrder())
                }
                
                if (allLabels.isNotEmpty()) {
                    outputBuffer = Array(1) { FloatArray(allLabels.size) }
                }

                isInitialized = true
                android.util.Log.i(TAG, "✓ RSL Sequence Classifier initialized (baseline mode, ${vocabularyMapping?.trainedWords?.size} vocabulary words)")
            } else {
                android.util.Log.e(TAG, "Failed to load model buffer")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize RSL Sequence Classifier", e)
            isInitialized = false
        }
    }

    private fun loadVocabularyMapping(): VocabularyMapping? {
        return try {
            val json = context.assets.open("rsl_dictionary_label_mapping.json")
                .bufferedReader()
                .use { it.readText() }
            Gson().fromJson(json, VocabularyMapping::class.java)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load vocabulary mapping", e)
            null
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
            // Fallback: copy to cache if AAPT compressed
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
     * Add a landmark frame to the sequence buffer.
     * Called every frame from MediaPipe HandLandmarker.
     */
    fun addFrame(result: HandLandmarkerResult) {
        if (!isInitialized) return
        
        // Extract first hand
        if (result.landmarks().isEmpty()) {
            return
        }
        
        val landmarks = result.landmarks()[0]
        if (landmarks.size != 21) {
            return
        }
        
        // Normalize landmarks (wrist-relative + unit-scale)
        val normalized = normalizeLandmarks(landmarks)
        
        // Add to buffer
        synchronized(landmarkBuffer) {
            landmarkBuffer.add(NormalizedLandmarks(normalized))
            
            // Keep only last windowSize frames
            while (landmarkBuffer.size > windowSize) {
                landmarkBuffer.removeAt(0)
            }
        }
    }

    /**
     * Classify the current sequence buffer (hold-to-sign).
     * Returns null if buffer is not full or classification fails.
     */
    fun classifySequence(): ClassificationResult? {
        if (!isInitialized || interpreter == null) {
            android.util.Log.w(TAG, "Classifier not initialized")
            return null
        }
        
        // Check if we have enough frames
        val frames = synchronized(landmarkBuffer) { landmarkBuffer.toList() }
        
        if (frames.size < windowSize) {
            android.util.Log.d(TAG, "Buffer not full yet: ${frames.size}/$windowSize frames")
            return null
        }
        
        // BASELINE IMPLEMENTATION: Use last frame + temporal voting
        // TODO: Replace with proper 1D-CNN when training data available
        val lastFrame = frames.last()
        
        // Run single-frame inference on last frame
        val input = inputBuffer ?: return null
        input.rewind()
        
        for (coord in lastFrame.coords) {
            input.putFloat(coord)
        }
        input.rewind()
        
        val output = outputBuffer ?: return null
        
        try {
            interpreter?.run(input, output)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "TFLite inference failed", e)
            return null
        }
        
        // Find top prediction
        val probabilities = output[0]
        var maxIndex = -1
        var maxProb = 0.0f
        
        for (i in probabilities.indices) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i]
                maxIndex = i
            }
        }
        
        if (maxIndex < 0 || maxIndex >= allLabels.size) {
            return null
        }
        
        val topLabel = allLabels[maxIndex]
        
        // Check if this label is in our vocabulary
        val mapping = vocabularyMapping
        val trainedWord = mapping?.trainedWords?.find { 
            it.label.equals(topLabel, ignoreCase = false) 
        }
        
        val inVocabulary = trainedWord != null
        val finalWord = trainedWord?.word ?: topLabel
        
        // Threshold: require higher confidence for sequence recognition
        val threshold = 0.5f
        val passedThreshold = maxProb > threshold && inVocabulary
        
        android.util.Log.d(TAG, "Sequence classification: label='$topLabel', word='$finalWord', confidence=$maxProb, inVocab=$inVocabulary, passed=$passedThreshold")
        
        return ClassificationResult(
            word = finalWord,
            confidence = maxProb,
            passedThreshold = passedThreshold,
            inVocabulary = inVocabulary
        )
    }

    /**
     * Clear the sequence buffer (e.g., after successful recognition or timeout)
     */
    fun clearBuffer() {
        synchronized(landmarkBuffer) {
            landmarkBuffer.clear()
        }
    }

    /**
     * Get current buffer fill level (for UI feedback)
     */
    fun getBufferFillLevel(): Float {
        val size = synchronized(landmarkBuffer) { landmarkBuffer.size }
        return size.toFloat() / windowSize.toFloat()
    }

    /**
     * Normalize landmarks: wrist-relative + unit-scale
     */
    private fun normalizeLandmarks(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): FloatArray {
        val wristX = landmarks[0].x()
        val wristY = landmarks[0].y()
        val wristZ = landmarks[0].z()
        
        val coords = FloatArray(21 * 3)
        
        // 1. Make wrist-relative
        for (i in 0 until 21) {
            val landmark = landmarks[i]
            coords[i * 3] = landmark.x() - wristX
            coords[i * 3 + 1] = landmark.y() - wristY
            coords[i * 3 + 2] = landmark.z() - wristZ
        }
        
        // 2. Scale to unit magnitude
        var maxDist = 0.0f
        for (i in 0 until 21) {
            val x = coords[i * 3]
            val y = coords[i * 3 + 1]
            val z = coords[i * 3 + 2]
            val dist = kotlin.math.sqrt(x * x + y * y + z * z)
            if (dist > maxDist) {
                maxDist = dist
            }
        }
        
        if (maxDist > 0.0f) {
            for (i in 0 until coords.size) {
                coords[i] /= maxDist
            }
        }
        
        return coords
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
        inputBuffer = null
        outputBuffer = null
        landmarkBuffer.clear()
    }

    companion object {
        private const val TAG = "RslSequenceClassifier"
    }
}
