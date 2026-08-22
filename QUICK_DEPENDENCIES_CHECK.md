# 🚀 Быстрая проверка зависимостей (для новичков)

## ✅ Шаг 1: Откройте файл зависимостей

1. В Android Studio в левой панели найдите папку **`app`**
2. Раскройте её и найдите файл **`build.gradle.kts`**
3. Дважды кликните на него, чтобы открыть

## ✅ Шаг 2: Проверьте наличие MediaPipe

Прокрутите файл вниз до секции `dependencies {` (примерно строка 56)

**Должны быть эти строки:**
```kotlin
// MediaPipe
val mediapipeVersion = "0.10.14"
implementation("com.google.mediapipe:tasks-vision:$mediapipeVersion")
```

✅ Если есть — хорошо!  
❌ Если нет — добавьте их

## ✅ Шаг 3: Синхронизируйте проект

### Способ 1 (самый простой):
1. Вверху экрана Android Studio найдите кнопку **"Sync Now"** (если она появилась)
2. Нажмите на неё

### Способ 2 (через меню):
1. Нажмите **`File`** в верхнем меню
2. Выберите **`Sync Project with Gradle Files`**
3. Подождите, пока появится сообщение "Gradle sync finished"

⏱️ **Время ожидания:** 1-5 минут (зависит от скорости интернета)

## ✅ Шаг 4: Проверьте результат

### Что должно произойти:

1. **Внизу экрана** появится окно "Build"
2. Должно быть написано: **"BUILD SUCCESSFUL"** ✅
3. Если написано "BUILD FAILED" ❌ — есть проблема

### Как проверить загруженные библиотеки:

1. В левой панели (Project) найдите **"External Libraries"**
2. Раскройте эту папку
3. Найдите библиотеки:
   - `com.google.mediapipe:tasks-vision:0.10.14` ✅
   - `androidx.camera:camera-core:1.3.4` ✅
   - И другие...

Если библиотеки есть — всё загружено правильно! 🎉

## ❌ Если что-то пошло не так

### Ошибка: "Could not resolve..."

**Что делать:**
1. Проверьте интернет-соединение
2. Откройте файл `settings.gradle.kts` (в корне проекта)
3. Убедитесь, что там есть:
   ```kotlin
   repositories {
       google()
       mavenCentral()
   }
   ```
4. Синхронизируйте проект снова

### Ошибка: "Unresolved reference..."

**Что делать:**
1. `Build` → `Clean Project`
2. `Build` → `Rebuild Project`
3. Подождите завершения

### Зависимости не загружаются

**Что делать:**
1. `File` → `Invalidate Caches...`
2. Выберите `Invalidate and Restart`
3. Дождитесь перезапуска Android Studio
4. Синхронизируйте проект снова

## 📋 Чек-лист проверки

Пройдитесь по этому списку:

- [ ] Открыт файл `app/build.gradle.kts`
- [ ] В файле есть `implementation("com.google.mediapipe:tasks-vision:0.10.14")`
- [ ] Нажата кнопка "Sync Project with Gradle Files"
- [ ] В окне Build написано "BUILD SUCCESSFUL"
- [ ] В External Libraries видны библиотеки MediaPipe
- [ ] Нет красных ошибок в коде

## 💡 Полезные советы

1. **Всегда синхронизируйте после изменения build.gradle.kts**
   - Изменили зависимости? → Синхронизируйте!

2. **Первая загрузка может занять время**
   - Это нормально, особенно для MediaPipe (большая библиотека)

3. **Если не работает — очистите проект**
   - `Build` → `Clean Project` → `Rebuild Project`

4. **Проверяйте версии**
   - Убедитесь, что версия MediaPipe правильная: `0.10.14`

## 🎯 Визуальная проверка в Android Studio

### Где искать информацию:

1. **Окно Build** (внизу экрана)
   - Показывает процесс загрузки
   - Показывает ошибки (если есть)

2. **Панель Gradle** (справа)
   - Показывает задачи Gradle
   - Можно запустить `dependencies` вручную

3. **External Libraries** (слева в Project)
   - Показывает все загруженные библиотеки
   - Если библиотеки нет — она не загружена

## 🆘 Нужна помощь?

Если ничего не помогает:
1. Скопируйте текст ошибки из окна Build
2. Поищите в интернете по тексту ошибки
3. Или спросите у опытных разработчиков

## 📝 Пример правильного build.gradle.kts

Ваш файл должен содержать примерно это:

```kotlin
dependencies {
    // ... другие зависимости ...
    
    // MediaPipe
    val mediapipeVersion = "0.10.14"
    implementation("com.google.mediapipe:tasks-vision:$mediapipeVersion")
    
    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    
    // ... остальные зависимости ...
}
```

Если всё совпадает — вы на правильном пути! ✅









