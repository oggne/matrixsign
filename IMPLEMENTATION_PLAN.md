# План реализации 5 критических улучшений MatrixSign

## 📋 Общий план

### 1. Групповой чат с цветовой разметкой спикеров
**Файлы:**
- `DialogScreen.kt` - обновить UI для отображения сообщений с цветами спикеров
- `ChatMessage.kt` - data class для сообщений
- `SpeakerManager.kt` - управление спикерами и их цветами
- `SpeechRecognizerHelper.kt` - добавить speaker diarization (fallback на порядок)

**Цвета спикеров (8 оттенков зелёного):**
- #00FF41 (основной)
- #00DD41
- #00BB41
- #009941
- #007741
- #005541
- #003341
- #001141

### 2. РЖЯ из коробки (Russian Sign Language)
**Файлы:**
- `RslGestureRecognizer.kt` - интеграция с Slovo датасетом
- `SlovoModelLoader.kt` - загрузка моделей из assets
- `GestureRecognizerHelper.kt` - добавить поддержку РЖЯ жестов

**Необходимые ресурсы:**
- Модели Slovo из https://github.com/hukenovs/slovo
- Ключевые точки жестов РЖЯ (1000 жестов)
- TensorFlow Lite модель или KNN классификатор

### 3. Silero TTS v5_ru (замена Android TTS)
**Файлы:**
- `TextToSpeechHelper.kt` - полностью переписать на Silero
- `SileroTtsEngine.kt` - движок Silero TTS
- `build.gradle.kts` - добавить ONNX Runtime или PyTorch Mobile

**Необходимые ресурсы:**
- Silero TTS v5 модель: `ru_v5.pt` (~150 МБ)
- Голоса: kseniya, eugene, aidar, baya, xenia
- ONNX Runtime для Android или PyTorch Mobile

**Зависимости:**
```kotlin
// ONNX Runtime (рекомендуется, легче)
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")

// ИЛИ PyTorch Mobile (если нужна полная поддержка)
implementation("org.pytorch:pytorch_android:2.3.0")
implementation("org.pytorch:pytorch_android_torchvision:2.3.0")
```

### 4. Автоопределение устройства (Bluetooth LE)
**Файлы:**
- `DeviceAutoDetector.kt` - новый класс для сканирования Bluetooth
- `DialogScreen.kt` - автоматическое переключение режима
- `MainActivity.kt` - запуск сканирования при старте

**Определяемые устройства:**
- Mudra Link (по имени "Mudra" или service UUID)
- Inmo Air3 (по MAC или имени)
- Xreal Air (по MAC или имени)
- Ray-Ban Meta (по MAC или имени)

### 5. Быстрые фразы одним жестом
**Файлы:**
- `QuickPhrasesActivity.kt` - экран с сеткой 3×4
- `QuickPhraseManager.kt` - Room DB для хранения фраз
- `QuickPhrase.kt` - Entity для Room
- `DialogScreen.kt` - проверка быстрых фраз при распознавании

**Room Database:**
- Entity: QuickPhrase (id, gestureName, phrase, position)
- DAO: QuickPhraseDao
- Database: MatrixSignDatabase (расширить существующую)

## 📥 Необходимые скачивания

### 1. Silero TTS v5 модели
**Источник:** https://github.com/snakers4/silero-models
**Файлы:**
- `ru_v5.pt` (~150 МБ) - основная модель
- Или конвертированные в ONNX: `ru_v5.onnx`

**Размещение:** `app/src/main/assets/silero/ru_v5.pt`

### 2. Slovo датасет и модели
**Источник:** https://github.com/hukenovs/slovo
**Файлы:**
- Ключевые точки жестов РЖЯ (JSON/CSV)
- TensorFlow Lite модель классификации (если есть)
- Или использовать MediaPipe Hand Landmarks + KNN

**Размещение:** `app/src/main/assets/slovo/`

### 3. ONNX Runtime (если используем ONNX)
**Источник:** Maven Central
**Зависимость:** уже добавлена в build.gradle.kts

## 🔧 Порядок реализации

1. **Быстрые фразы** (самое простое, Room DB уже есть)
2. **Автоопределение устройства** (Bluetooth LE)
3. **Групповой чат** (UI изменения)
4. **Silero TTS** (замена TTS)
5. **РЖЯ** (самое сложное, требует модели)

## ⚠️ Важные замечания

- Silero TTS требует ONNX Runtime или PyTorch Mobile (большой размер APK)
- Slovo модели могут быть большими, нужна оптимизация
- Bluetooth LE требует разрешения (уже есть в манифесте)
- Room DB нужно расширить для быстрых фраз





