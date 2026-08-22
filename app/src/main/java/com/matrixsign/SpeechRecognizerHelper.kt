package com.matrixsign

import android.content.Context
import android.speech.SpeechRecognizer
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Помощник для распознавания речи (STT)
 * Использует ML Kit on-device + fallback на системный SpeechRecognizer
 * + Поддержка Offline Whisper (TFLite)
 */
class SpeechRecognizerHelper(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private val languageIdentifier: LanguageIdentifier = 
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
        )
    
    private val _recognizedText = MutableStateFlow<String>("")
    val recognizedText: StateFlow<String> = _recognizedText
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening
    
    // Offline Whisper Support
    private val whisperHelper = WhisperHelper(context)
    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode
    
    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        
        // Initialize Whisper asynchronously
        GlobalScope.launch(Dispatchers.IO) {
            if (whisperHelper.initialize()) {
                _isOfflineMode.value = true // Auto-enable if model exists
            }
        }
    }
    
    fun startListening(language: String = "ru-RU") {
        if (_isOfflineMode.value) {
            startOfflineListening()
        } else {
            startOnlineListening(language)
        }
    }
    
    private fun startOnlineListening(language: String) {
        if (speechRecognizer == null) {
            _recognizedText.value = "Распознавание речи недоступно"
            return
        }
        
        _isListening.value = true
        
        // Используем системный SpeechRecognizer как основной
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        // Устанавливаем слушатель
        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _isListening.value = false
            }
            override fun onError(error: Int) {
                _isListening.value = false
                // Auto-restart on common errors
                if (error == android.speech.SpeechRecognizer.ERROR_NO_MATCH || 
                    error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        // Restart after a short delay
                        GlobalScope.launch(Dispatchers.Main) {
                            kotlinx.coroutines.delay(100)
                            startOnlineListening(language)
                        }
                } else {
                     android.util.Log.e("SpeechRecognizer", "Error: $error")
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _recognizedText.value = matches[0]
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _recognizedText.value = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        // Запускаем слушатель
        speechRecognizer?.startListening(intent)
    }
    
    private fun startOfflineListening() {
        _isListening.value = true
        GlobalScope.launch(Dispatchers.Main) {
            whisperHelper.startRecording()
            // Observe transcription
            launch {
                whisperHelper.transcription.collect { text ->
                    if (text.isNotEmpty()) {
                        _recognizedText.value = text
                    }
                }
            }
        }
    }
    
    fun stopListening() {
        if (_isOfflineMode.value) {
            whisperHelper.stopRecording()
        } else {
            speechRecognizer?.cancel()
        }
        _isListening.value = false
    }
    
    fun processAudio(audioData: ByteArray) {
        // Обработка аудио через ML Kit (если нужно)
        // В текущей реализации используем системный API
    }
    
    fun identifyLanguage(text: String, callback: (String?) -> Unit) {
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                callback(languageCode)
            }
            .addOnFailureListener {
                callback(null)
            }
    }
    
    fun destroy() {
        speechRecognizer?.destroy()
        whisperHelper.close()
        languageIdentifier.close()
    }
    
    fun setOfflineMode(enabled: Boolean) {
        _isOfflineMode.value = enabled
    }
}

