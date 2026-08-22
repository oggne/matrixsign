package com.matrixsign

import android.content.Context
import android.content.res.Resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * T9-предсказатель слов на основе Trie-структуры
 * Поддерживает русский и английский языки
 * 
 * КОНТЕКСТНАЯ ЛОГИКА:
 * - Поддержка контекстных словарей (greeting, weather, food)
 * - Автоматическое переключение контекста на основе STT
 * - Приоритизация контекстных слов в предсказаниях
 */
class T9Predictor(private val context: Context) {
    
    private data class TrieNode(
        val children: MutableMap<Char, TrieNode> = mutableMapOf(),
        var isWord: Boolean = false,
        var frequency: Int = 0,
        var contextTags: MutableSet<String> = mutableSetOf() // Теги контекста для слова
    )
    
    private val root = TrieNode()
    
    // Текущий контекст диалога
    private var currentContext: DialogContext = DialogContext.GENERAL
    
    // Контекстные словари
    private val contextDictionaries = mutableMapOf<DialogContext, List<String>>()
    
    /**
     * Контексты диалога
     */
    enum class DialogContext {
        GENERAL,    // Общий контекст
        GREETING,   // Приветствие
        WEATHER,    // Погода
        FOOD,       // Еда
        QUESTION,   // Вопрос
        RESPONSE    // Ответ
    }
    private val t9Map = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
        // Русские буквы
        'а' to "2", 'б' to "2", 'в' to "2", 'г' to "3", 'д' to "3",
        'е' to "3", 'ё' to "3", 'ж' to "3", 'з' to "3", 'и' to "4",
        'й' to "4", 'к' to "4", 'л' to "4", 'м' to "5", 'н' to "5",
        'о' to "5", 'п' to "6", 'р' to "6", 'с' to "6", 'т' to "7",
        'у' to "7", 'ф' to "7", 'х' to "7", 'ц' to "7", 'ч' to "7",
        'ш' to "7", 'щ' to "7", 'ъ' to "7", 'ы' to "7", 'ь' to "7",
        'э' to "7", 'ю' to "7", 'я' to "7"
    )
    
    private val reverseT9Map = mapOf(
        '2' to listOf('a', 'b', 'c', 'а', 'б', 'в'),
        '3' to listOf('d', 'e', 'f', 'г', 'д', 'е', 'ё', 'ж', 'з'),
        '4' to listOf('g', 'h', 'i', 'и', 'й', 'к', 'л'),
        '5' to listOf('j', 'k', 'l', 'м', 'н', 'о'),
        '6' to listOf('m', 'n', 'o', 'п', 'р', 'с'),
        '7' to listOf('p', 'q', 'r', 's', 'т', 'у', 'ф', 'х', 'ц', 'ч', 'ш', 'щ', 'ъ', 'ы', 'ь', 'э', 'ю', 'я'),
        '8' to listOf('t', 'u', 'v'),
        '9' to listOf('w', 'x', 'y', 'z')
    )
    
    suspend fun loadDictionary() = withContext(Dispatchers.IO) {
        try {
            // Загружаем основные словари из assets
            loadWordsFromAssets("dictionaries/russian_words.txt", 100000)
            loadWordsFromAssets("dictionaries/english_words.txt", 50000)
        } catch (e: Exception) {
            // Если словари не найдены, используем базовый набор
            loadDefaultWords()
        }
        
        // Загружаем контекстные словари
        loadContextualDictionaries()
    }
    
    /**
     * Загрузка контекстных словарей
     */
    private fun loadContextualDictionaries() {
        // Словарь приветствий
        contextDictionaries[DialogContext.GREETING] = listOf(
            "привет", "здравствуй", "добрый день", "добрый вечер", "доброе утро",
            "hi", "hello", "hey", "good morning", "good evening", "good afternoon",
            "как дела", "как поживаешь", "how are you", "how do you do"
        )
        
        // Словарь погоды
        contextDictionaries[DialogContext.WEATHER] = listOf(
            "солнечно", "дождь", "снег", "ветер", "жарко", "холодно", "тепло",
            "sunny", "rain", "snow", "wind", "hot", "cold", "warm",
            "ясно", "облачно", "туман", "clear", "cloudy", "foggy",
            "температура", "градус", "temperature", "degree"
        )
        
        // Словарь еды
        contextDictionaries[DialogContext.FOOD] = listOf(
            "еда", "есть", "голоден", "ресторан", "кафе", "обед", "ужин", "завтрак",
            "food", "eat", "hungry", "restaurant", "cafe", "lunch", "dinner", "breakfast",
            "вкусно", "сыт", "delicious", "full"
        )
        
        // Словарь ответов
        contextDictionaries[DialogContext.RESPONSE] = listOf(
            "да", "нет", "спасибо", "пожалуйста", "извините", "хорошо", "плохо",
            "yes", "no", "thank", "please", "sorry", "ok", "okay", "good", "bad",
            "понятно", "ясно", "understood", "clear"
        )
        
        // Добавляем контекстные слова в Trie с повышенной частотой
        contextDictionaries.forEach { (ctx, words) ->
            words.forEachIndexed { index, word ->
                addWord(word, 2000 - index, setOf(ctx.name)) // Высокая частота для контекстных слов
            }
        }
    }
    
    private fun loadWordsFromAssets(fileName: String, maxWords: Int) {
        try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            var count = 0
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (count >= maxWords) return@useLines
                    val word = line.trim().lowercase()
                    if (word.isNotEmpty()) {
                        addWord(word, 1000 - count / 100) // Частота уменьшается
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            // Файл не найден, пропускаем
        }
    }
    
    private fun loadDefaultWords() {
        // Базовый набор русских слов
        val russianWords = listOf(
            "привет", "пока", "да", "нет", "спасибо", "пожалуйста",
            "извините", "здравствуйте", "до свидания", "как дела",
            "хорошо", "плохо", "давай", "можно", "нельзя", "помощь",
            "вода", "еда", "дом", "работа", "семья", "друг", "любовь"
        )
        
        // Базовый набор английских слов
        val englishWords = listOf(
            "hello", "goodbye", "yes", "no", "thank", "please",
            "sorry", "help", "water", "food", "home", "work", "family"
        )
        
        russianWords.forEachIndexed { index, word ->
            addWord(word, 1000 - index)
        }
        englishWords.forEachIndexed { index, word ->
            addWord(word, 1000 - index)
        }
    }
    
    private fun addWord(word: String, frequency: Int, contextTags: Set<String> = emptySet()) {
        var node = root
        for (char in word.lowercase()) {
            if (!node.children.containsKey(char)) {
                node.children[char] = TrieNode()
            }
            node = node.children[char]!!
        }
        node.isWord = true
        // Если слово уже существует, увеличиваем частоту и добавляем теги
        node.frequency = maxOf(node.frequency, frequency)
        node.contextTags.addAll(contextTags)
    }
    
    /**
     * Предсказание слов по T9-последовательности
     * Приоритизирует слова из текущего контекста
     */
    fun predict(sequence: String, limit: Int = 10): List<String> {
        if (sequence.isEmpty()) return emptyList()
        
        val results = mutableListOf<Pair<String, Int>>()
        dfs(root, sequence, 0, "", results)
        
        // Сортируем с учётом контекста: слова из текущего контекста получают бонус
        return results
            .map { (word, freq) ->
                // Ищем слово в Trie для получения его контекстных тегов
                val node = findNode(word)
                val contextBonus = if (node != null && currentContext.name in node.contextTags) {
                    1000 // Бонус для слов из текущего контекста
                } else {
                    0
                }
                word to (freq + contextBonus)
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
    
    /**
     * Найти узел Trie для слова
     */
    private fun findNode(word: String): TrieNode? {
        var node = root
        for (char in word.lowercase()) {
            node = node.children[char] ?: return null
        }
        return node
    }
    
    private fun dfs(
        node: TrieNode,
        sequence: String,
        index: Int,
        currentWord: String,
        results: MutableList<Pair<String, Int>>
    ) {
        if (index == sequence.length) {
            if (node.isWord) {
                results.add(currentWord to node.frequency)
            }
            return
        }
        
        val digit = sequence[index]
        val possibleChars = reverseT9Map[digit] ?: return
        
        for (char in possibleChars) {
            val child = node.children[char]
            if (child != null) {
                dfs(child, sequence, index + 1, currentWord + char, results)
            }
        }
    }
    
    /**
     * Конвертация жеста в T9-последовательность
     */
    fun gestureToT9(gesture: String): String {
        // Простая маппинг жестов на цифры
        // В реальности это должно быть более сложное сопоставление
        val gestureMap = mapOf(
            "THUMB_UP" to "2",
            "THUMB_DOWN" to "3",
            "VICTORY" to "4",
            "POINTING_UP" to "5",
            "OPEN_PALM" to "6",
            "FIST" to "7",
            "OK" to "8",
            "C" to "9"
        )
        return gestureMap[gesture] ?: ""
    }
    
    /**
     * Текущая последовательность T9
     */
    private var currentSequence = StringBuilder()
    
    /**
     * Текущие предсказания
     */
    private var currentPredictions = mutableListOf<String>()
    
    /**
     * Индекс выбранного предсказания
     */
    private var selectedIndex = 0

    fun getSelectedIndex(): Int = selectedIndex

    
    /**
     * Добавить цифру в последовательность T9
     */
    fun addDigit(digit: Char) {
        if (digit in '2'..'9') {
            currentSequence.append(digit)
            currentPredictions = predict(currentSequence.toString(), limit = 5).toMutableList()
            selectedIndex = 0
        }
    }
    
    /**
     * Очистить последовательность
     */
    fun clearSequence() {
        currentSequence.clear()
        currentPredictions.clear()
        selectedIndex = 0
    }
    
    /**
     * Получить текущую последовательность
     */
    fun getCurrentSequence(): String {
        return currentSequence.toString()
    }
    
    /**
     * Получить текущие предсказания
     */
    fun getCurrentPredictions(): List<String> {
        return currentPredictions.toList()
    }
    
    /**
     * Получить выбранное предсказание
     */
    fun getSelectedPrediction(): String? {
        return if (currentPredictions.isNotEmpty() && selectedIndex < currentPredictions.size) {
            currentPredictions[selectedIndex]
        } else {
            null
        }
    }
    
    /**
     * Выбрать следующее предсказание
     */
    fun selectNext() {
        if (currentPredictions.isNotEmpty()) {
            selectedIndex = (selectedIndex + 1) % currentPredictions.size
        }
    }
    
    /**
     * Выбрать предыдущее предсказание
     */
    fun selectPrevious() {
        if (currentPredictions.isNotEmpty()) {
            selectedIndex = (selectedIndex - 1 + currentPredictions.size) % currentPredictions.size
        }
    }
    
    /**
     * Подтвердить выбранное предсказание
     * Возвращает подтверждённое слово и очищает последовательность
     */
    fun confirmT9(): String? {
        val confirmed = getSelectedPrediction()
        if (confirmed != null) {
            clearSequence()
            return confirmed
        }
        return null
    }
    
    /**
     * Добавить букву напрямую (для жестов, которые маппятся в буквы)
     */
    fun addLetter(letter: Char) {
        // Конвертируем букву в T9-цифру
        val digit = letterToT9(letter)
        if (digit != null) {
            addDigit(digit)
        }
    }
    
    /**
     * Конвертация буквы в T9-цифру
     */
    private fun letterToT9(letter: Char): Char? {
        val lowerLetter = letter.lowercaseChar()
        // Используем обратный маппинг из reverseT9Map
        for ((digit, chars) in reverseT9Map) {
            if (lowerLetter in chars) {
                return digit
            }
        }
        return null
    }
    
    /**
     * Определить контекст на основе STT текста
     * Использует ключевые слова из strings.xml
     */
    fun detectContextFromStt(sttText: String): DialogContext {
        val lowerText = sttText.lowercase()
        
        // Получаем ключевые слова из ресурсов
        val resources = context.resources
        
        // Проверяем контекст приветствия
        val greetingKeywords = resources.getString(R.string.context_greeting_keywords)
            .split(",").map { it.trim() }
        if (greetingKeywords.any { it in lowerText }) {
            return DialogContext.GREETING
        }
        
        // Проверяем контекст погоды
        val weatherKeywords = resources.getString(R.string.context_weather_keywords)
            .split(",").map { it.trim() }
        if (weatherKeywords.any { it in lowerText }) {
            return DialogContext.WEATHER
        }
        
        // Проверяем контекст еды
        val foodKeywords = resources.getString(R.string.context_food_keywords)
            .split(",").map { it.trim() }
        if (foodKeywords.any { it in lowerText }) {
            return DialogContext.FOOD
        }
        
        // Проверяем контекст вопроса
        val questionKeywords = resources.getString(R.string.context_question_keywords)
            .split(",").map { it.trim() }
        if (questionKeywords.any { it in lowerText } && lowerText.contains("?")) {
            return DialogContext.QUESTION
        }
        
        // Проверяем контекст ответа
        val responseKeywords = resources.getString(R.string.context_response_keywords)
            .split(",").map { it.trim() }
        if (responseKeywords.any { it in lowerText }) {
            return DialogContext.RESPONSE
        }
        
        return DialogContext.GENERAL
    }
    
    /**
     * Установить текущий контекст
     */
    fun setContext(newContext: DialogContext) {
        if (currentContext != newContext) {
            currentContext = newContext
            // При смене контекста обновляем предсказания
            val currentSeq = getCurrentSequence()
            if (currentSeq.isNotEmpty()) {
                currentPredictions = predict(currentSeq, limit = 5).toMutableList()
            }
        }
    }
    
    /**
     * Получить текущий контекст
     */
    fun getCurrentContext(): DialogContext = currentContext
    
    /**
     * Получить быстрые фразы для текущего контекста
     */
    fun getQuickPhrasesForContext(): List<String> {
        return contextDictionaries[currentContext]?.take(5) ?: emptyList()
    }
}




