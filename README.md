# MatrixSign

Двусторонний коммуникатор для глухонемых в стиле фильма "Матрица".

## Описание

MatrixSign — это Android-приложение для реального времени коммуникации между слышащими и глухонемыми людьми без использования клавиатуры.

### Основные возможности:

- **Распознавание жестов**: Использует MediaPipe Gesture Recognizer для распознавания жестов в реальном времени
- **Визуализация landmarks**: Отображает 21 точку суставов руки с зелёными линиями в стиле Матрица
- **STT (Speech-to-Text)**: Преобразует речь собеседника в крупный текст на экране
- **TTS (Text-to-Speech)**: Озвучивает текст, введённый жестами
- **T9-предсказатель**: Интеллектуальный предсказатель слов на основе жестов
- **Mudra Link интеграция**: Поддержка специальных жестов через Bluetooth
- **AR-очки**: Поддержка Inmo Air3, Xreal Air 2/Ultra, Ray-Ban Meta
- **Полный оффлайн режим**: Все модели работают локально

## Технические требования

- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 35 (Android 15)
- **Kotlin**: 2.0.21+
- **Jetpack Compose**: 1.6+
- **CameraX**: 1.3+
- **MediaPipe Tasks Vision**: 0.10.14+

## Установка моделей MediaPipe

Для работы приложения необходимо добавить модели MediaPipe в папку `app/src/main/assets/`:

1. **gesture_recognizer.task** — модель распознавания жестов
   - Скачать с: https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task

2. **hand_landmarker.task** — модель для определения landmarks руки
   - Скачать с: https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task

Создайте структуру:
```
app/src/main/assets/
├── gesture_recognizer.task
├── hand_landmarker.task
└── dictionaries/
    ├── russian_words.txt (опционально)
    └── english_words.txt (опционально)
```

## Структура проекта

```
app/src/main/java/com/matrixsign/
├── MainActivity.kt              # Главная активность
├── DialogScreen.kt              # Основной экран диалога (Compose)
├── MpOverlayView.kt             # Overlay для визуализации landmarks
├── GestureRecognizerHelper.kt   # Интеграция с MediaPipe
├── T9Predictor.kt               # T9-предсказатель слов
├── SpeechRecognizerHelper.kt    # STT (ML Kit)
├── TextToSpeechHelper.kt        # TTS
├── MudraManager.kt              # Менеджер Mudra Link
├── ArGlassesManager.kt          # Менеджер AR-очков
├── CustomGestureTrainer.kt      # Тренер кастомных жестов
├── AvatarRenderer.kt            # Рендерер 3D-аватара (опционально)
└── utils/
    └── DrawingUtils.kt          # Утилиты для отрисовки landmarks
```

## Дизайн в стиле Матрица

- **Цвет текста**: Ярко-зелёный (#00FF40, #00FF00)
- **Шрифт**: Моноширинный (Fira Code / Roboto Mono)
- **Размер шрифта**: 28-48sp (настраиваемый)
- **Фон**: Размытое и затемнённое видео с селфи-камеры
- **Landmarks**: Зелёные точки и линии поверх видео

## Разрешения

Приложение требует следующие разрешения:
- `CAMERA` — для селфи-камеры
- `RECORD_AUDIO` — для распознавания речи
- `BLUETOOTH` — для Mudra Link
- `VIBRATE` — для тактильной обратной связи

## Использование

1. Запустите приложение
2. Разрешите доступ к камере и микрофону
3. Направьте камеру на руки для распознавания жестов
4. Говорите — ваша речь будет отображаться крупным текстом
5. Показывайте жесты — они будут распознаваться и озвучиваться

## Разработка

### Запуск проекта

1. Клонируйте репозиторий
2. Откройте в Android Studio
3. Скачайте модели MediaPipe (см. выше)
4. Соберите и запустите

### Зависимости

Все зависимости указаны в `app/build.gradle.kts`. Основные:
- MediaPipe Tasks Vision 0.10.14
- CameraX 1.3.4
- Jetpack Compose BOM 2024.09.00
- ML Kit Speech Recognition 17.0.1

## Лицензия

Этот проект создан для помощи глухонемым людям в коммуникации.

## Примечания

- Модели MediaPipe должны быть загружены вручную в папку assets
- Для полной функциональности Mudra Link требуется физическое устройство
- AR-очки требуют соответствующих SDK (Inmo, Xreal, Ray-Ban Meta)
- T9-словарь загружается из assets или использует базовый набор слов









