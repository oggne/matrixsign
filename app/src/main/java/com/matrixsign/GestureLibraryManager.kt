package com.matrixsign

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Room Database для хранения кастомных жестов пользователей
 * Каждый пользователь имеет свою библиотеку жестов
 */
@Entity(tableName = "gestures")
data class Gesture(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "gesture_label")
    val gestureLabel: String, // Буква, слово, символ (например, "А", "привет", "!")
    @ColumnInfo(name = "video_path")
    val videoPath: String? = null, // Путь к записанному видео (опционально)
    @ColumnInfo(name = "model_path")
    val modelPath: String, // Путь к обученной .task модели
    @ColumnInfo(name = "confidence_threshold")
    val confidenceThreshold: Float = 0.5f, // Порог уверенности для распознавания
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_custom_symbol")
    val isCustomSymbol: Boolean = false, // true для специальных символов (!, @, и т.д.)
    @ColumnInfo(name = "role")
    val role: String? = null // Роль жеста: NEXT, PREV, SPEAK, CONFIRM или null (обычный жест)
)

@Dao
interface GestureDao {
    @Query("SELECT * FROM gestures WHERE user_id = :userId ORDER BY created_at DESC")
    fun getAllGesturesByUser(userId: String): Flow<List<Gesture>>
    
    @Query("SELECT * FROM gestures WHERE user_id = :userId AND gesture_label = :label LIMIT 1")
    suspend fun getGestureByLabel(userId: String, label: String): Gesture?
    
    @Query("SELECT * FROM gestures WHERE user_id = :userId AND id = :gestureId")
    suspend fun getGestureById(userId: String, gestureId: Long): Gesture?
    
    @Query("SELECT * FROM gestures WHERE user_id = :userId AND is_custom_symbol = 1")
    fun getCustomSymbolsByUser(userId: String): Flow<List<Gesture>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGesture(gesture: Gesture): Long
    
    @Update
    suspend fun updateGesture(gesture: Gesture)
    
    @Delete
    suspend fun deleteGesture(gesture: Gesture)
    
    @Query("DELETE FROM gestures WHERE user_id = :userId AND id = :gestureId")
    suspend fun deleteGestureById(userId: String, gestureId: Long)
    
    @Query("SELECT COUNT(*) FROM gestures WHERE user_id = :userId")
    suspend fun getGestureCount(userId: String): Int
}

@Database(
    entities = [Gesture::class, QuickPhrase::class],
    version = 3,
    exportSchema = false
)
abstract class GestureDatabase : RoomDatabase() {
    abstract fun gestureDao(): GestureDao
    abstract fun quickPhraseDao(): QuickPhraseDao
    
    companion object {
        @Volatile
        private var INSTANCE: GestureDatabase? = null
        
        fun getDatabase(context: Context): GestureDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GestureDatabase::class.java,
                    "gesture_database"
                )
                    .fallbackToDestructiveMigration() // Для разработки
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Менеджер для работы с библиотекой жестов
 */
class GestureLibraryManager(private val context: Context) {
    private val database = GestureDatabase.getDatabase(context)
    private val dao = database.gestureDao()
    
    /**
     * Получить все жесты пользователя
     */
    fun getAllGestures(userId: String): Flow<List<Gesture>> {
        return dao.getAllGesturesByUser(userId)
    }
    
    /**
     * Получить жест по метке
     */
    suspend fun getGestureByLabel(userId: String, label: String): Gesture? {
        return dao.getGestureByLabel(userId, label)
    }
    
    /**
     * Получить жест по ID
     */
    suspend fun getGestureById(userId: String, gestureId: Long): Gesture? {
        return dao.getGestureById(userId, gestureId)
    }
    
    /**
     * Получить кастомные символы пользователя
     */
    fun getCustomSymbols(userId: String): Flow<List<Gesture>> {
        return dao.getCustomSymbolsByUser(userId)
    }
    
    /**
     * Сохранить новый жест
     */
    suspend fun saveGesture(gesture: Gesture): Long {
        return dao.insertGesture(gesture)
    }
    
    /**
     * Обновить жест
     */
    suspend fun updateGesture(gesture: Gesture) {
        dao.updateGesture(gesture.copy(updatedAt = System.currentTimeMillis()))
    }
    
    /**
     * Удалить жест
     */
    suspend fun deleteGesture(userId: String, gestureId: Long) {
        dao.deleteGestureById(userId, gestureId)
    }
    
    /**
     * Получить количество жестов пользователя
     */
    suspend fun getGestureCount(userId: String): Int {
        return dao.getGestureCount(userId)
    }
    
    /**
     * Проверить, существует ли жест с такой меткой
     */
    suspend fun gestureExists(userId: String, label: String): Boolean {
        return dao.getGestureByLabel(userId, label) != null
    }
}


