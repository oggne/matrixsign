package com.matrixsign

import android.content.Context
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Тренер для создания и обучения кастомных жестов, а также управления текстом.
 * 
 * Примечание: MediaPipe Model Maker для Android пока не имеет официального API,
 * поэтому здесь используется упрощённая реализация с сохранением landmarks.
 */
class CustomGestureTrainer(private val context: Context) {
    
    private val gestureLibraryManager = GestureLibraryManager(context)
    private val userIdManager = UserIdManager(context)
    
    // --- Состояния для процесса обучения ---
    private val _isTraining = MutableStateFlow(false)
    val isTraining: StateFlow<Boolean> = _isTraining
    
    private val _trainingProgress = MutableStateFlow(0f)
    val trainingProgress: StateFlow<Float> = _trainingProgress
    
    private val _trainingStatus = MutableStateFlow<String>("")
    val trainingStatus: StateFlow<String> = _trainingStatus

    // --- Состояния для расшифровки текста (Т9) ---
    private val _decipheredText = MutableStateFlow("")
    val decipheredText: StateFlow<String> = _decipheredText

    /**
     * Добавление распознанного символа к тексту.
     */
    fun appendToDecipheredText(symbol: String) {
        _decipheredText.value += symbol
    }

    /**
     * Добавление пробела в расшифрованный текст.
     */
    fun addSpace() {
        // Условие, чтобы не добавлять пробелы подряд или в начало строки
        if (_decipheredText.value.isNotEmpty() && !_decipheredText.value.endsWith(" ")) {
            _decipheredText.value += " "
        }
    }

    /**
     * Удаление последнего символа из расшифрованного текста (Backspace).
     */
    fun deleteLastSymbol() {
        _decipheredText.value = _decipheredText.value.dropLast(1)
    }

    /**
     * Очистка всего расшифрованного текста.
     */
    fun clearDecipheredText() {
        _decipheredText.value = ""
    }
    
    /**
     * Создание нового кастомного жеста из записанных samples.
     */
    suspend fun createCustomGesture(
        gestureLabel: String,
        samples: List<List<NormalizedLandmarkList>>,
        videoPath: String? = null,
        isCustomSymbol: Boolean = false,
        role: String? = null // Роль жеста: NEXT, PREV, SPEAK, CONFIRM
    ): Boolean = withContext(Dispatchers.IO) {
        _isTraining.value = true
        _trainingProgress.value = 0f
        _trainingStatus.value = "Подготовка данных..."
        
        try {
            val userId = userIdManager.getUserId()
            
            if (gestureLibraryManager.gestureExists(userId, gestureLabel)) {
                _trainingStatus.value = "Жест с меткой '$gestureLabel' уже существует"
                _isTraining.value = false
                return@withContext false
            }
            
            _trainingProgress.value = 0.2f
            _trainingStatus.value = "Обработка samples..."
            
            if (samples.isEmpty()) {
                _trainingStatus.value = "Ошибка: нет samples для обучения"
                _isTraining.value = false
                return@withContext false
            }
            
            _trainingProgress.value = 0.4f
            _trainingStatus.value = "Сохранение landmarks..."
            
            val landmarksDir = File(context.filesDir, "gestures/$userId")
            landmarksDir.mkdirs()
            
            val landmarksFile = File(landmarksDir, "${gestureLabel}_landmarks.dat")
            saveLandmarksToFile(landmarksFile, samples)
            
            _trainingProgress.value = 0.6f
            _trainingStatus.value = "Обучение модели (placeholder)..."
            
            val modelPath = "gestures/$userId/${gestureLabel}_model.task"
            val modelFile = File(context.filesDir, modelPath)
            modelFile.parentFile?.mkdirs()
            
            createPlaceholderModel(modelFile, gestureLabel, samples.size)
            
            _trainingProgress.value = 0.8f
            _trainingStatus.value = "Сохранение в базу данных..."
            
            val gesture = Gesture(
                userId = userId,
                gestureLabel = gestureLabel,
                videoPath = videoPath,
                modelPath = modelPath,
                confidenceThreshold = 0.5f,
                isCustomSymbol = isCustomSymbol,
                role = role
            )
            
            gestureLibraryManager.saveGesture(gesture)
            
            _trainingProgress.value = 1.0f
            _trainingStatus.value = "Готово! Жест '$gestureLabel' сохранён"
            
            _isTraining.value = false
            return@withContext true
        } catch (e: Exception) {
            _trainingStatus.value = "Ошибка: ${e.message}"
            _isTraining.value = false
            return@withContext false
        }
    }
    
    /**
     * Сохранение landmarks в CSV файл для последующего обучения в Python.
     * Формат: label,x1,y1,z1,x2,y2,z2...
     */
    suspend fun saveLandmarksToCsv(gestureLabel: String, samples: List<List<NormalizedLandmarkList>>): Boolean = withContext(Dispatchers.IO) {
        try {
            val datasetsDir = File(context.filesDir, "datasets")
            if (!datasetsDir.exists()) {
                datasetsDir.mkdirs()
            }
            
            val csvFile = File(datasetsDir, "rsl_data.csv")
            val isNewFile = !csvFile.exists()
            
            FileOutputStream(csvFile, true).bufferedWriter().use { writer ->
                // Записываем заголовок, если файл новый
                if (isNewFile) {
                    val header = StringBuilder("label")
                    for (i in 0 until 21) {
                        header.append(",x$i,y$i,z$i")
                    }
                    writer.write(header.toString())
                    writer.newLine()
                }
                
                // Записываем данные
                samples.forEach { sample ->
                    // Берем первый список landmarks из семпла (обычно там одна рука)
                    if (sample.isNotEmpty()) {
                        val landmarks = sample[0] 
                        if (landmarks.landmarkCount == 21) {
                            val line = StringBuilder(gestureLabel)
                            landmarks.landmarkList.forEach { landmark ->
                                line.append(",${landmark.x},${landmark.y},${landmark.z}")
                            }
                            writer.write(line.toString())
                            writer.newLine()
                        }
                    }
                }
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun saveLandmarksToFile(file: File, samples: List<List<NormalizedLandmarkList>>) {
        // Упрощённое сохранение: в реальном приложении нужна сериализация protobuf
        file.writer().use { writer ->
            writer.write("${samples.size}\n")
            samples.forEach { sample ->
                writer.write("${sample.size}\n")
                sample.forEach { landmarkList ->
                    writer.write("${landmarkList.landmarkCount}\n")
                    landmarkList.getLandmarkList().forEach { landmark ->
                        writer.write("${landmark.x},${landmark.y},${landmark.z}\n")
                    }
                }
            }
        }
    }
    
    private fun createPlaceholderModel(modelFile: File, gestureLabel: String, sampleCount: Int) {
        // В реальном приложении здесь должен быть экспорт .task файла из MediaPipe Model Maker
        modelFile.writeText("PLACEHOLDER_MODEL:$gestureLabel:$sampleCount")
    }

    /**
     * Копирует предобученную модель из assets во внутреннее хранилище, если ее там нет.
     * @return true, если модель успешно установлена или уже существует.
     */
    suspend fun installPretrainedModelIfNeeded(modelAssetPath: String = "models/rzhy_gesture_recognizer.task"): Boolean = withContext(Dispatchers.IO) {
        _trainingStatus.value = "Проверка основной модели..."
        try {
            val modelName = File(modelAssetPath).name
            val modelFile = File(context.filesDir, modelName)

            if (modelFile.exists()) {
                _trainingStatus.value = "Основная модель уже установлена."
                return@withContext true
            }

            _trainingStatus.value = "Установка основной модели..."
            context.assets.open(modelAssetPath).use { inputStream ->
                FileOutputStream(modelFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            _trainingStatus.value = "Основная модель успешно установлена."
            true
        } catch (e: IOException) {
            _trainingStatus.value = "Ошибка при установке модели: ${e.message}"
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Удаление обученного жеста.
     */
    suspend fun deleteGesture(gestureLabel: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = userIdManager.getUserId()
            val gesture = gestureLibraryManager.getGestureByLabel(userId, gestureLabel) ?: return@withContext false

            // Удаляем файл модели
            val modelFile = File(context.filesDir, gesture.modelPath)
            if (modelFile.exists()) {
                modelFile.delete()
            }
            
            // Удаляем файл landmarks
            val landmarksDir = File(context.filesDir, "gestures/$userId")
            val landmarksFile = File(landmarksDir, "${gestureLabel}_landmarks.dat")
            if (landmarksFile.exists()) {
                landmarksFile.delete()
            }
            
            // Удаляем из БД
            gestureLibraryManager.deleteGesture(userId, gesture.id)
            _trainingStatus.value = "Жест '$gestureLabel' удален."
            true
        } catch (e: Exception) {
            _trainingStatus.value = "Ошибка при удалении жеста: ${e.message}"
            false
        }
    }
}
