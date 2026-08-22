package com.matrixsign

import android.content.Context
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Маппер для преобразования жестов в текст (буквы/слова/фразы)
 * Поддерживает базовые жесты MediaPipe и кастомные жесты пользователя
 * Фокус на РЖЯ (Русский жестовый язык)
 */
class GestureToTextMapper(
    private val context: Context,
    private val gestureLibraryManager: GestureLibraryManager
) {
    
    private val userIdManager = UserIdManager(context)
    
    // Базовый маппинг жестов MediaPipe на буквы/слова РЖЯ
    // Это упрощённая версия, в реальности нужна более сложная логика
    private val baseGestureMap = mapOf(
        // Базовые жесты MediaPipe -> РЖЯ буквы/слова
        "Thumb_Up" to "А",
        "Thumb_Down" to "Б",
        "Victory" to "В",
        "Pointing_Up" to "Г",
        "Open_Palm" to "Д",
        "Closed_Fist" to "Е",
        "OK" to "Ж",
        "C" to "З",
        "ILoveYou" to "И",
        "Rock" to "Й",
        "Call" to "К",
        "Thumb" to "Л",
        "Peace" to "М",
        "No" to "Н",
        "None" to "" // Пустой жест
    )
    
    // Маппинг для слов РЖЯ (комбинации жестов)
    private val wordGestureMap = mapOf(
        "Thumb_Up" + "Open_Palm" to "привет",
        "Victory" + "Pointing_Up" to "пока",
        "OK" + "Thumb_Up" to "спасибо",
        "Open_Palm" + "Closed_Fist" to "да",
        "No" + "Closed_Fist" to "нет",
        "Pointing_Up" + "Victory" to "помощь"
    )
    
    /**
     * Преобразовать результат распознавания жеста в текст
     */
    suspend fun gestureToText(
        result: GestureRecognizerResult,
        handLandmarks: List<NormalizedLandmarkList>
    ): String = withContext(Dispatchers.Default) {
        val userId = userIdManager.getUserId()
        
        // Получаем топовый жест из результата
        val gestures = result.gestures()
        if (gestures.isEmpty() || gestures[0].isEmpty()) {
            return@withContext ""
        }
        
        val topGesture = gestures[0][0]
        val gestureName = topGesture.categoryName()
        val confidence = topGesture.score()
        
        // Если уверенность низкая, возвращаем пустую строку
        if (confidence < 0.5f) {
            return@withContext ""
        }
        
        // Сначала проверяем кастомные жесты пользователя
        val customGesture = gestureLibraryManager.getGestureByLabel(userId, gestureName)
        if (customGesture != null && confidence >= customGesture.confidenceThreshold) {
            return@withContext customGesture.gestureLabel
        }
        
        // Затем проверяем базовый маппинг
        val mappedText = baseGestureMap[gestureName]
        if (mappedText != null) {
            return@withContext mappedText
        }
        
        // Если не найдено, возвращаем название жеста как есть
        gestureName
    }
    
    /**
     * Преобразовать последовательность жестов в слово/фразу
     */
    suspend fun gestureSequenceToText(
        gestureSequence: List<String>,
        handLandmarksSequence: List<List<NormalizedLandmarkList>>
    ): String = withContext(Dispatchers.Default) {
        if (gestureSequence.isEmpty()) return@withContext ""
        
        val userId = userIdManager.getUserId()
        val textBuilder = StringBuilder()
        
        for (gestureName in gestureSequence) {
            // Проверяем кастомные жесты
            val customGesture = gestureLibraryManager.getGestureByLabel(userId, gestureName)
            if (customGesture != null) {
                textBuilder.append(customGesture.gestureLabel)
                continue
            }
            
            // Проверяем базовый маппинг
            val mappedText = baseGestureMap[gestureName]
            if (mappedText != null) {
                textBuilder.append(mappedText)
            } else {
                textBuilder.append(gestureName)
            }
        }
        
        textBuilder.toString()
    }
    
    /**
     * Определить, является ли жест специальным (например, T9_CONFIRM)
     */
    fun isSpecialGesture(gestureName: String): Boolean {
        return gestureName == "T9_CONFIRM" || 
               gestureName == "SPACE" || 
               gestureName == "BACKSPACE" ||
               gestureName == "ENTER"
    }
    
    /**
     * Получить букву из жеста для T9
     */
    suspend fun gestureToLetter(gestureName: String): Char? = withContext(Dispatchers.Default) {
        val userId = userIdManager.getUserId()
        
        // Проверяем кастомные жесты
        val customGesture = gestureLibraryManager.getGestureByLabel(userId, gestureName)
        if (customGesture != null && customGesture.gestureLabel.length == 1) {
            return@withContext customGesture.gestureLabel[0]
        }
        
        // Проверяем базовый маппинг
        val mappedText = baseGestureMap[gestureName]
        if (mappedText != null && mappedText.length == 1) {
            return@withContext mappedText[0]
        }
        
        null
    }
    
    /**
     * Получить символ из жеста (для кастомных символов)
     */
    suspend fun gestureToSymbol(gestureName: String): String? = withContext(Dispatchers.Default) {
        val userId = userIdManager.getUserId()
        
        // Проверяем кастомные символы
        val customGesture = gestureLibraryManager.getGestureByLabel(userId, gestureName)
        if (customGesture != null && customGesture.isCustomSymbol) {
            return@withContext customGesture.gestureLabel
        }
        
        null
    }
}

/**
 * Менеджер для управления user_id
 * Использует DataStore (SettingsManager) для хранения — ID генерируется один раз
 * (UUID) при первом обращении и переживает перезапуски приложения.
 */
class UserIdManager(private val context: Context) {
    private val settingsManager = SettingsManager(context)

    /**
     * Получить текущий user_id (генерируется и сохраняется при первом вызове).
     * Раньше всегда возвращал константу "default_user", из-за чего жесты всех
     * людей, пользующихся общим устройством, попадали в одну и ту же запись.
     */
    suspend fun getUserId(): String {
        return settingsManager.getOrCreateUserId()
    }
}






