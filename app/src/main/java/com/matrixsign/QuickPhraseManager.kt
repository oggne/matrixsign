package com.matrixsign

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Менеджер для работы с быстрыми фразами
 */
class QuickPhraseManager(private val context: Context) {
    private val database = GestureDatabase.getDatabase(context)
    private val dao = database.quickPhraseDao()
    
    /**
     * Получить все быстрые фразы пользователя
     */
    fun getAllPhrases(userId: String): Flow<List<QuickPhrase>> {
        return dao.getAllPhrasesByUser(userId)
    }
    
    /**
     * Получить фразу по жесту
     */
    suspend fun getPhraseByGesture(userId: String, gestureName: String): QuickPhrase? {
        return dao.getPhraseByGesture(userId, gestureName)
    }
    
    /**
     * Получить фразу по позиции в сетке
     */
    suspend fun getPhraseByPosition(userId: String, position: Int): QuickPhrase? {
        return dao.getPhraseByPosition(userId, position)
    }
    
    /**
     * Сохранить быструю фразу
     */
    suspend fun savePhrase(phrase: QuickPhrase): Long {
        return dao.insertPhrase(phrase)
    }
    
    /**
     * Обновить быструю фразу
     */
    suspend fun updatePhrase(phrase: QuickPhrase) {
        dao.updatePhrase(phrase)
    }
    
    /**
     * Удалить быструю фразу
     */
    suspend fun deletePhrase(userId: String, phraseId: Long) {
        dao.deletePhraseById(userId, phraseId)
    }
    
    /**
     * Получить количество фраз
     */
    suspend fun getPhraseCount(userId: String): Int {
        return dao.getPhraseCount(userId)
    }
}





