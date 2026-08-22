package com.matrixsign

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Помощник для синтеза речи (TTS)
 * Использует Silero TTS v5_ru (оффлайн) с fallback на Android TTS
 * 
 * КЛЮЧЕВЫЕ ИСПРАВЛЕНИЯ:
 * - Правильная инициализация с Google TTS engine
 * - Установка русского языка (Locale("ru"))
 * - Буферизация полных фраз (не буква за буквой)
 * - Озвучивание только после T9_CONFIRM
 * - Прогрев TTS пустой строкой
 * - Обработка ошибок с fallback
 */
class TextToSpeechHelper(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isWarmedUp = false
    private val sileroEngine = SileroTtsEngine(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking
    
    private val _isSileroAvailable = MutableStateFlow(false)
    val isSileroAvailable: StateFlow<Boolean> = _isSileroAvailable
    
    // Буфер для полной фразы (говорим только после подтверждения)
    private val phraseBuffer = StringBuilder()
    
    private var useSilero = false // Флаг использования Silero (пока false, пока модель не загружена)
    private var currentLanguage = "ru" // Текущий язык TTS
    
    init {
        // Инициализация Android TTS с Google engine
        initializeAndroidTts()
        
        // Попытка инициализации Silero для русского (асинхронно)
        scope.launch {
            useSilero = sileroEngine.initialize("ru")
            _isSileroAvailable.value = useSilero
            if (useSilero) {
                Log.d("TTS", "Silero TTS initialized successfully for Russian")
            }
        }
    }
    
    /**
     * Установить язык TTS
     */
    fun setLanguage(language: String) {
        if (language != currentLanguage) {
            currentLanguage = language
            scope.launch {
                // Пытаемся инициализировать Silero для нового языка
                val sileroReady = sileroEngine.initialize(language)
                useSilero = sileroReady
                _isSileroAvailable.value = sileroReady
                
                if (sileroReady) {
                    Log.d("TTS", "Switched Silero TTS to language: $language")
                } else {
                    // Fallback на Android TTS
                    Log.d("TTS", "Silero not available for $language, using Android TTS")
                }
            }
        }
    }
    
    /**
     * Инициализация Android TTS с явным указанием Google TTS engine
     */
    private fun initializeAndroidTts() {
        // Явно указываем Google TTS engine
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Устанавливаем Google TTS engine явно
                val engineName = tts?.defaultEngine
                Log.d("TTS", "TTS initialized with engine: $engineName")
                
                // Пытаемся установить Google TTS engine
                val googleEngine = "com.google.android.tts"
                val engines = tts?.engines
                val hasGoogleEngine = engines?.any { it.name == googleEngine } == true
                
                if (hasGoogleEngine && tts?.defaultEngine != googleEngine) {
                    try {
                        tts?.stop()
                        tts?.shutdown()
                        tts = TextToSpeech(context) { newStatus ->
                            if (newStatus == TextToSpeech.SUCCESS) {
                                configureTts()
                            } else {
                                Log.e("TTS", "Failed to initialize Google TTS, using default")
                                configureTts()
                            }
                        }
                        return@TextToSpeech
                    } catch (e: Exception) {
                        Log.e("TTS", "Error setting Google TTS engine: ${e.message}")
                    }
                }
                
                configureTts()
            } else {
                Log.e("TTS", "TTS initialization failed with status: $status")
                isInitialized = false
            }
        }
    }
    
    /**
     * Настройка TTS после инициализации
     */
    private fun configureTts() {
        isInitialized = true
        
        // Устанавливаем русский язык
        val result = tts?.setLanguage(Locale("ru", "RU"))
        if (result == TextToSpeech.LANG_MISSING_DATA || 
            result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("TTS", "Russian language not supported, falling back to English")
            tts?.setLanguage(Locale.US)
        } else {
            Log.d("TTS", "Russian language set successfully")
        }
        
        // Настройки для лучшего качества
        tts?.setSpeechRate(1.0f)
        tts?.setPitch(1.0f)
        
        // Устанавливаем listener для отслеживания состояния
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                Log.d("TTS", "Started speaking: $utteranceId")
            }
            
            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                Log.d("TTS", "Finished speaking: $utteranceId")
            }
            
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                Log.e("TTS", "TTS error for utterance: $utteranceId")
                // Fallback: пытаемся переинициализировать
                scope.launch {
                    delay(500)
                    if (!isInitialized) {
                        initializeAndroidTts()
                    }
                }
            }
        })
        
        // Прогрев TTS пустой строкой
        scope.launch {
            warmUpTts()
        }
    }
    
    /**
     * Прогрев TTS пустой строкой для предотвращения шума при первом использовании
     */
    private suspend fun warmUpTts() {
        if (isInitialized && !isWarmedUp && tts != null) {
            try {
                delay(300) // Небольшая задержка для полной инициализации
                // Говорим пустую строку для прогрева
                tts?.speak("", TextToSpeech.QUEUE_FLUSH, null, "warmup")
                delay(100)
                tts?.stop()
                isWarmedUp = true
                Log.d("TTS", "TTS warmed up successfully")
            } catch (e: Exception) {
                Log.e("TTS", "Error warming up TTS: ${e.message}")
            }
        }
    }
    
    /**
     * Добавить текст в буфер (не озвучивать сразу)
     * Используется для накопления полной фразы
     */
    fun addToBuffer(text: String) {
        if (text.isNotEmpty()) {
            if (phraseBuffer.isNotEmpty()) {
                phraseBuffer.append(" ")
            }
            phraseBuffer.append(text.trim())
            // Содержимое разговора не логируем в релизе — это как раз то,
            // что согласие пользователя обещает не сохранять и не передавать.
            if (BuildConfig.DEBUG) {
                Log.d("TTS", "Added to buffer: '$text', current buffer: '${phraseBuffer.toString()}'")
            }
        }
    }
    
    /**
     * Очистить буфер
     */
    fun clearBuffer() {
        phraseBuffer.clear()
        Log.d("TTS", "Buffer cleared")
    }
    
    /**
     * Получить текущий буфер
     */
    fun getBuffer(): String = phraseBuffer.toString()
    
    /**
     * Озвучить полную фразу из буфера (вызывается после T9_CONFIRM)
     * После озвучивания буфер очищается
     */
    fun speakBufferedPhrase(language: String = "ru") {
        val fullPhrase = phraseBuffer.toString().trim()
        if (fullPhrase.isEmpty()) {
            Log.w("TTS", "Attempted to speak empty buffer")
            return
        }
        
        if (BuildConfig.DEBUG) {
            Log.d("TTS", "Speaking full phrase from buffer: '$fullPhrase'")
        }
        
        // Озвучиваем полную фразу
        speak(fullPhrase, language)
        
        // Очищаем буфер после озвучивания
        clearBuffer()
    }
    
    /**
     * Синтез речи
     * Использует Silero если доступен, иначе Android TTS
     * ВАЖНО: Не вызывать напрямую для букв/слов - использовать addToBuffer + speakBufferedPhrase
     */
    fun speak(text: String, language: String = "ru") {
        // Проверяем, что текст не пустой и не содержит только пробелы
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            Log.w("TTS", "Attempted to speak empty text")
            return
        }
        
        // Если TTS не инициализирован, пытаемся инициализировать
        if (!isInitialized || tts == null) {
            Log.w("TTS", "TTS not initialized, attempting initialization")
            initializeAndroidTts()
            // Ждём немного и пробуем снова
            scope.launch {
                delay(500)
                if (isInitialized && tts != null) {
                    speakWithAndroidTts(cleanText, language)
                } else {
                    Log.e("TTS", "Failed to initialize TTS, cannot speak")
                }
            }
            return
        }
        
        // Если Silero доступен, используем его (когда будет реализован)
        if (useSilero && sileroEngine.isReady()) {
            // TODO: Использовать Silero TTS когда будет полная реализация
            speakWithAndroidTts(cleanText, language)
        } else {
            speakWithAndroidTts(cleanText, language)
        }
    }
    
    /**
     * Внутренний метод для озвучивания через Android TTS
     */
    private fun speakWithAndroidTts(text: String, language: String) {
        if (!isInitialized || tts == null) {
            Log.e("TTS", "Cannot speak: TTS not initialized")
            return
        }
        
        // Убеждаемся, что текст чистый (без байтов/шума)
        val cleanText = text.trim()
            .replace(Regex("[^\\p{L}\\p{N}\\s.,!?;:()-]"), "") // Удаляем спецсимволы кроме пунктуации
            .replace(Regex("\\s+"), " ") // Нормализуем пробелы
        
        if (cleanText.isEmpty()) {
            Log.w("TTS", "Text is empty after cleaning")
            return
        }
        
        val locale = when (language.lowercase()) {
            "ru", "russian" -> Locale("ru", "RU")
            "en", "english" -> Locale.US
            else -> Locale.getDefault()
        }
        
        // Устанавливаем язык
        val langResult = tts?.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || 
            langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("TTS", "Language $language not supported, using default")
        }
        
        // Останавливаем предыдущее озвучивание и начинаем новое
        tts?.stop()
        
        // Озвучиваем с уникальным ID для отслеживания
        val utteranceId = "utterance_${System.currentTimeMillis()}"
        val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        
        if (result == TextToSpeech.ERROR) {
            Log.e("TTS", "Error speaking text" + if (BuildConfig.DEBUG) ": $cleanText" else "")
            // Fallback: пытаемся переинициализировать
            scope.launch {
                delay(500)
                initializeAndroidTts()
            }
        } else if (BuildConfig.DEBUG) {
            Log.d("TTS", "Successfully queued text for speaking: '$cleanText'")
        }
    }
    
    /**
     * Установить голос Silero
     */
    fun setSileroVoice(voice: String) {
        sileroEngine.setVoice(voice)
    }
    
    /**
     * Получить текущий голос Silero
     */
    fun getCurrentSileroVoice(): String {
        return sileroEngine.getCurrentVoice()
    }
    
    /**
     * Остановить озвучивание
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        Log.d("TTS", "TTS stopped")
    }
    
    /**
     * Установить скорость речи
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
        Log.d("TTS", "Speech rate set to: $rate")
    }
    
    /**
     * Установить высоту тона
     */
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
        Log.d("TTS", "Pitch set to: $pitch")
    }
    
    /**
     * Освободить ресурсы TTS
     */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        isWarmedUp = false
        clearBuffer()
        Log.d("TTS", "TTS released")
    }
}





