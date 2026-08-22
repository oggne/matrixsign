package com.matrixsign

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import org.json.JSONObject
import java.nio.charset.Charset

/**
 * Helper class for offline speech recognition using Whisper TFLite.
 */
class WhisperHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var vocab: Map<Int, String> = emptyMap()
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    
    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription

    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    
    private val melSpectrogram = MelSpectrogram()

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(context.filesDir, "whisper.tflite")
            val vocabFile = File(context.filesDir, "filters_vocab_gen.bin") // Or vocab.json

            if (!modelFile.exists()) return@withContext false

            // Load Model
            val fileInputStream = FileInputStream(modelFile)
            val fileChannel = fileInputStream.channel
            val startOffset = 0L
            val declaredLength = fileChannel.size()
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            interpreter = Interpreter(modelBuffer)

            // Load Vocab (Simplified JSON loader)
            // Assuming user provides vocab.json for simplicity in this implementation
            val jsonFile = File(context.filesDir, "vocab.json")
            if (jsonFile.exists()) {
                val jsonString = jsonFile.readText()
                val jsonObject = JSONObject(jsonString)
                val map = mutableMapOf<Int, String>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[jsonObject.getInt(key)] = key
                }
                vocab = map
            }
            
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun startRecording() = withContext(Dispatchers.IO) {
        if (_isRecording.value) return@withContext
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            
            audioRecord?.startRecording()
            _isRecording.value = true
            
            val audioBuffer = ShortArray(bufferSize)
            val allSamples = mutableListOf<Float>()
            
            // Record for up to 30 seconds or until stopped
            val maxSamples = 30 * sampleRate
            
            while (_isRecording.value && allSamples.size < maxSamples) {
                val read = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                if (read > 0) {
                    for (i in 0 until read) {
                        // Normalize to [-1.0, 1.0]
                        allSamples.add(audioBuffer[i] / 32768.0f)
                    }
                }
            }
            
            stopRecording()
            
            // Process
            if (allSamples.isNotEmpty()) {
                transcribe(allSamples.toFloatArray())
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }

    private suspend fun transcribe(samples: FloatArray) = withContext(Dispatchers.Default) {
        if (interpreter == null) return@withContext

        try {
            // 1. Compute Log-Mel Spectrogram
            val melSpec = melSpectrogram.process(samples)
            
            // 2. Prepare Input Buffer
            // Whisper input shape: [1, 80, 3000] (usually)
            // Check model input shape
            val inputShape = interpreter?.getInputTensor(0)?.shape() // e.g. [1, 80, 3000]
            
            val inputBuffer = ByteBuffer.allocateDirect(1 * 80 * 3000 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            
            // Copy melSpec to buffer (ensure correct layout: [batch, channels, time] or [batch, time, channels])
            // Whisper usually expects [1, 80, 3000]
            for (value in melSpec) {
                inputBuffer.putFloat(value)
            }
            
            // 3. Run Inference
            // Output shape: [1, N_TOKENS] (e.g. [1, 448])
            val outputTensor = interpreter?.getOutputTensor(0)
            val outputShape = outputTensor?.shape()
            val outputBuffer = ByteBuffer.allocateDirect((outputShape?.get(1) ?: 448) * 4) // Int32 tokens
            outputBuffer.order(ByteOrder.nativeOrder())
            
            interpreter?.run(inputBuffer, outputBuffer)
            
            // 4. Decode Tokens
            outputBuffer.rewind()
            val sb = StringBuilder()
            while (outputBuffer.hasRemaining()) {
                val token = outputBuffer.int
                // Skip special tokens (usually < 50257 for English, depends on vocab)
                // Simple decoding:
                vocab[token]?.let { word ->
                    // Handle special characters like Ġ (space)
                    var text = word.replace("Ġ", " ")
                    // Remove other special chars if needed
                    sb.append(text)
                }
            }
            
            _transcription.value = sb.toString().trim()
            
        } catch (e: Exception) {
            e.printStackTrace()
            _transcription.value = "Error: ${e.message}"
        }
    }
    
    fun close() {
        interpreter?.close()
    }
}
