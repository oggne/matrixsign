# Инструкция по установке моделей MediaPipe

Для работы приложения MatrixSign необходимо скачать и установить модели MediaPipe.

## Шаги установки:

1. **gesture_recognizer.task**
   - URL: https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task
   - Сохраните файл в: `app/src/main/assets/gesture_recognizer.task`

2. **hand_landmarker.task**
   - URL: https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
   - Сохраните файл в: `app/src/main/assets/hand_landmarker.task`

## Опциональные словари T9:

Для улучшения работы T9-предсказателя можно добавить словари:

- `app/src/main/assets/dictionaries/russian_words.txt` — русские слова (по одному на строку)
- `app/src/main/assets/dictionaries/english_words.txt` — английские слова (по одному на строку)

Если словари не найдены, приложение использует базовый набор слов.

## Проверка установки:

После добавления файлов структура должна выглядеть так:

```
app/src/main/assets/
├── gesture_recognizer.task
├── hand_landmarker.task
└── dictionaries/
    ├── russian_words.txt (опционально)
    └── english_words.txt (опционально)
```









