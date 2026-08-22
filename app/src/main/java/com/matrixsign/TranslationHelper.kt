package com.matrixsign

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Помощник для перевода текста в реальном времени
 * Использует ML Kit Translation — сам перевод выполняется on-device.
 *
 * ВАЖНО: это не 100% офлайн с первого запуска. downloadModelIfNeeded() (см. ниже)
 * при первом использовании пары языков скачивает модель перевода из сети Google.
 * Текст диалога при этом никуда не отправляется, но сетевой трафик на скачивание
 * модели присутствует — это стоит отразить в политике конфиденциальности, а не
 * заявлять полный офлайн без оговорок. После первого скачивания модель кэшируется
 * на устройстве и последующие переводы уже полностью локальны.
 *
 * Поддерживаемые языки:
 * - Речь: русский (ru), английский (en), украинский (uk)
 * - Жесты: РЖЯ (rsl), ASL (asl), УЖЯ (usl)
 */
class TranslationHelper(private val context: Context) {
    
    private val languageIdentifier: LanguageIdentifier = 
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
        )
    
    // Кэш переводчиков для избежания повторной инициализации
    private val translatorCache = mutableMapOf<String, Translator>()
    
    /**
     * Определить язык текста
     */
    suspend fun identifyLanguage(text: String): String? = withContext(Dispatchers.IO) {
        if (text.trim().isEmpty()) return@withContext null
        
        suspendCancellableCoroutine { continuation ->
            languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener { languageCode ->
                    // Не логируем сырой текст диалога, только код языка.
                    if (BuildConfig.DEBUG) {
                        Log.d("Translation", "Identified language: $languageCode for text: $text")
                    } else {
                        Log.d("Translation", "Identified language: $languageCode")
                    }
                    continuation.resume(languageCode)
                }
                .addOnFailureListener { exception ->
                    Log.e("Translation", "Failed to identify language: ${exception.message}")
                    continuation.resume(null)
                }
        }
    }
    
    /**
     * Получить или создать переводчик для пары языков
     */
    private suspend fun getTranslator(sourceLanguage: String, targetLanguage: String): Translator? = 
        withContext(Dispatchers.IO) {
            val cacheKey = "${sourceLanguage}_$targetLanguage"
            
            // Проверяем кэш
            translatorCache[cacheKey]?.let { return@withContext it }
            
            // Проверяем, поддерживается ли пара языков
            val sourceLang = try {
                TranslateLanguage.fromLanguageTag(sourceLanguage) 
                    ?: TranslateLanguage.fromLanguageTag(getLanguageCode(sourceLanguage))
            } catch (e: Exception) {
                Log.e("Translation", "Unsupported source language: $sourceLanguage")
                return@withContext null
            }
            
            val targetLang = try {
                TranslateLanguage.fromLanguageTag(targetLanguage)
                    ?: TranslateLanguage.fromLanguageTag(getLanguageCode(targetLanguage))
            } catch (e: Exception) {
                Log.e("Translation", "Unsupported target language: $targetLanguage")
                return@withContext null
            }
            
            if (sourceLang == null || targetLang == null) {
                Log.e("Translation", "Invalid language pair: $sourceLanguage -> $targetLanguage")
                return@withContext null
            }
            
            // Создаём переводчик
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
            
            val translator = Translation.getClient(options)
            translatorCache[cacheKey] = translator
            
            // Скачиваем модель, если нужно (первый раз)
            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    Log.d("Translation", "Translation model ready: $sourceLanguage -> $targetLanguage")
                }
                .addOnFailureListener { exception ->
                    Log.e("Translation", "Failed to download model: ${exception.message}")
                }
            
            translator
        }
    
    /**
     * Перевести текст
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String? = withContext(Dispatchers.IO) {
        if (text.trim().isEmpty()) return@withContext text
        if (sourceLanguage == targetLanguage) return@withContext text
        
        val translator = getTranslator(sourceLanguage, targetLanguage) ?: return@withContext null
        
        suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    if (BuildConfig.DEBUG) {
                        Log.d("Translation", "Translated: '$text' ($sourceLanguage) -> '$translatedText' ($targetLanguage)")
                    } else {
                        Log.d("Translation", "Translated $sourceLanguage -> $targetLanguage")
                    }
                    continuation.resume(translatedText)
                }
                .addOnFailureListener { exception ->
                    Log.e("Translation", "Translation failed: ${exception.message}")
                    continuation.resume(null)
                }
        }
    }
    
    /**
     * Автоматически определить язык и перевести в целевой
     */
    suspend fun translateAuto(text: String, targetLanguage: String): String? = 
        withContext(Dispatchers.IO) {
            if (text.trim().isEmpty()) return@withContext text
            
            // Определяем язык источника
            val sourceLanguage = identifyLanguage(text) ?: return@withContext null
            
            // Если языки совпадают, возвращаем исходный текст
            if (sourceLanguage == targetLanguage || 
                getLanguageCode(sourceLanguage) == getLanguageCode(targetLanguage)) {
                return@withContext text
            }
            
            // Переводим
            translate(text, sourceLanguage, targetLanguage)
        }
    
    /**
     * Конвертировать код языка жестов в код языка речи
     */
    private fun getLanguageCode(language: String): String {
        return when (language.lowercase()) {
            "rsl", "ru" -> "ru" // Русский жестовый язык -> русский
            "asl", "en" -> "en" // Американский жестовый язык -> английский
            "usl", "uk" -> "uk" // Украинский жестовый язык -> украинский
            else -> language
        }
    }
    
    /**
     * Освободить ресурсы
     */
    fun release() {
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
        languageIdentifier.close()
    }
}




