package com.matrixsign

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Entity для быстрых фраз (привязка жеста к фразе)
 */
@Entity(tableName = "quick_phrases")
data class QuickPhrase(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "gesture_name")
    val gestureName: String, // Имя жеста (из MediaPipe или кастомного)
    @ColumnInfo(name = "phrase")
    val phrase: String, // Полная фраза для вставки
    @ColumnInfo(name = "position")
    val position: Int, // Позиция в сетке (0-11 для 3×4)
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface QuickPhraseDao {
    @Query("SELECT * FROM quick_phrases WHERE user_id = :userId ORDER BY position ASC")
    fun getAllPhrasesByUser(userId: String): Flow<List<QuickPhrase>>
    
    @Query("SELECT * FROM quick_phrases WHERE user_id = :userId AND gesture_name = :gestureName LIMIT 1")
    suspend fun getPhraseByGesture(userId: String, gestureName: String): QuickPhrase?
    
    @Query("SELECT * FROM quick_phrases WHERE user_id = :userId AND position = :position LIMIT 1")
    suspend fun getPhraseByPosition(userId: String, position: Int): QuickPhrase?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhrase(phrase: QuickPhrase): Long
    
    @Update
    suspend fun updatePhrase(phrase: QuickPhrase)
    
    @Delete
    suspend fun deletePhrase(phrase: QuickPhrase)
    
    @Query("DELETE FROM quick_phrases WHERE user_id = :userId AND id = :phraseId")
    suspend fun deletePhraseById(userId: String, phraseId: Long)
    
    @Query("SELECT COUNT(*) FROM quick_phrases WHERE user_id = :userId")
    suspend fun getPhraseCount(userId: String): Int
}

