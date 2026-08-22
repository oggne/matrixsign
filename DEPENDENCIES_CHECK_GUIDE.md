# Руководство по проверке зависимостей для новичков

## 📋 Что такое зависимости?

Зависимости (dependencies) — это библиотеки, которые использует ваше приложение. Например, MediaPipe, CameraX, Compose — это все зависимости.

## 🔍 Шаг 1: Проверка файла build.gradle.kts

### Где находится файл?
- `app/build.gradle.kts` — файл зависимостей вашего приложения

### Что проверить?

1. **Откройте файл** `app/build.gradle.kts` в Android Studio

2. **Найдите секцию `dependencies {`** (примерно строка 56)

3. **Проверьте наличие MediaPipe зависимостей:**
   ```kotlin
   // MediaPipe
   val mediapipeVersion = "0.10.14"
   implementation("com.google.mediapipe:tasks-vision:$mediapipeVersion")
   implementation("com.google.mediapipe:tasks-core:$mediapipeVersion")
   ```

4. **Проверьте другие важные зависимости:**
   - CameraX (для камеры)
   - Jetpack Compose (для UI)
   - ML Kit (для распознавания речи)

## 🔄 Шаг 2: Синхронизация проекта с Gradle

### Что это значит?
Синхронизация загружает все зависимости из интернета и делает их доступными в проекте.

### Как сделать:

1. **В Android Studio:**
   - Нажмите на уведомление "Sync Now" (если оно появилось)
   - ИЛИ: `File` → `Sync Project with Gradle Files`
   - ИЛИ: Нажмите на иконку 🐘 (слон) в панели инструментов

2. **Дождитесь завершения:**
   - Внизу экрана появится прогресс-бар
   - Дождитесь сообщения "Gradle sync finished"

## ✅ Шаг 3: Проверка успешной загрузки

### Способ 1: Через окно Build

1. Откройте вкладку **"Build"** внизу экрана
2. Ищите сообщения типа:
   - ✅ `BUILD SUCCESSFUL` — всё хорошо!
   - ❌ `BUILD FAILED` — есть проблемы

### Способ 2: Через окно Gradle

1. Справа в Android Studio найдите панель **"Gradle"**
2. Раскройте: `MatrixSign` → `app` → `Tasks` → `android`
3. Дважды кликните на `dependencies`
4. Проверьте вывод — не должно быть ошибок

### Способ 3: Через External Libraries

1. В левой панели (Project) найдите **"External Libraries"**
2. Раскройте и найдите:
   - `com.google.mediapipe:tasks-vision:0.10.14`
   - `androidx.camera:camera-core:1.3.4`
   - И другие библиотеки
3. Если библиотеки есть — зависимости загружены ✅

## 🚨 Шаг 4: Что делать, если зависимости не загружаются?

### Проблема 1: "Could not resolve..."

**Решение:**
1. Проверьте интернет-соединение
2. Проверьте файл `settings.gradle.kts` — должны быть репозитории:
   ```kotlin
   repositories {
       google()
       mavenCentral()
   }
   ```

### Проблема 2: "Unresolved reference..."

**Решение:**
1. Синхронизируйте проект: `File` → `Sync Project with Gradle Files`
2. Очистите проект: `Build` → `Clean Project`
3. Пересоберите: `Build` → `Rebuild Project`

### Проблема 3: Зависимости загружаются очень долго

**Решение:**
1. Проверьте скорость интернета
2. Закройте другие программы, использующие интернет
3. Подождите — первая загрузка может занять 5-10 минут

## 🔧 Шаг 5: Очистка кэша (если ничего не помогает)

### Инвалидация кэша:

1. `File` → `Invalidate Caches...`
2. Выберите:
   - ✅ `Invalidate and Restart`
3. Дождитесь перезапуска Android Studio

### Очистка Gradle кэша:

1. Закройте Android Studio
2. Удалите папку `.gradle` в папке проекта (если есть)
3. Откройте Android Studio снова
4. Синхронизируйте проект

## 📦 Шаг 6: Проверка конкретных зависимостей MediaPipe

### Для MediaPipe Tasks Vision:

1. Откройте `app/build.gradle.kts`
2. Найдите строки:
   ```kotlin
   implementation("com.google.mediapipe:tasks-vision:0.10.14")
   ```
3. Убедитесь, что версия правильная: `0.10.14`

### Проверка через терминал (опционально):

1. В Android Studio откройте вкладку **"Terminal"** (внизу)
2. Введите команду:
   ```bash
   ./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep mediapipe
   ```
3. Должны увидеть строки с `com.google.mediapipe`

## 🎯 Быстрая проверка (чек-лист)

- [ ] Файл `app/build.gradle.kts` содержит зависимости MediaPipe
- [ ] Файл `settings.gradle.kts` содержит `google()` и `mavenCentral()`
- [ ] Проект синхронизирован (`File` → `Sync Project with Gradle Files`)
- [ ] В окне Build нет ошибок
- [ ] В External Libraries видны библиотеки MediaPipe
- [ ] Проект компилируется без ошибок

## 💡 Полезные советы

1. **Всегда синхронизируйте после изменения build.gradle.kts**
2. **Первая синхронизация может занять время** — это нормально
3. **Если что-то не работает** — попробуйте `Clean Project` → `Rebuild Project`
4. **Проверяйте версии зависимостей** — они должны быть совместимы

## 🆘 Если ничего не помогает

1. Проверьте логи в окне **Build** (внизу экрана)
2. Скопируйте текст ошибки
3. Поищите решение в интернете или спросите у опытных разработчиков

## 📚 Дополнительная информация

- Официальная документация Gradle: https://docs.gradle.org
- Официальная документация MediaPipe: https://ai.google.dev/edge/mediapipe









