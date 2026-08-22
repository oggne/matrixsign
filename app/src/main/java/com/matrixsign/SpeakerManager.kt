package com.matrixsign

import androidx.compose.ui.graphics.Color

/**
 * Менеджер для управления спикерами в групповом чате
 * Поддержка до 8 участников с уникальными цветами
 */
class SpeakerManager {
    
    // 8 оттенков зелёного для спикеров
    private val speakerColors = listOf(
        Color(0xFF00FF41), // Основной зелёный
        Color(0xFF00DD41),
        Color(0xFF00BB41),
        Color(0xFF009941),
        Color(0xFF007741),
        Color(0xFF005541),
        Color(0xFF003341),
        Color(0xFF001141)
    )
    
    private val speakers = mutableMapOf<String, Speaker>()
    private var nextColorIndex = 0
    
    /**
     * Получить или создать спикера
     */
    fun getOrCreateSpeaker(speakerId: String, speakerName: String? = null): Speaker {
        if (!speakers.containsKey(speakerId)) {
            if (speakers.size >= 8) {
                // Если уже 8 спикеров, переиспользуем цвета
                val colorIndex = speakers.size % speakerColors.size
                val color = speakerColors[colorIndex]
                speakers[speakerId] = Speaker(
                    id = speakerId,
                    name = speakerName ?: "Спикер ${speakers.size + 1}",
                    color = color
                )
            } else {
                val color = speakerColors[nextColorIndex % speakerColors.size]
                speakers[speakerId] = Speaker(
                    id = speakerId,
                    name = speakerName ?: "Спикер ${speakers.size + 1}",
                    color = color
                )
                nextColorIndex++
            }
        }
        return speakers[speakerId]!!
    }
    
    /**
     * Получить цвет спикера
     */
    fun getSpeakerColor(speakerId: String): Color {
        return speakers[speakerId]?.color ?: speakerColors[0]
    }
    
    /**
     * Получить имя спикера
     */
    fun getSpeakerName(speakerId: String): String {
        return speakers[speakerId]?.name ?: "Неизвестный"
    }
    
    /**
     * Получить всех спикеров
     */
    fun getAllSpeakers(): List<Speaker> {
        return speakers.values.toList()
    }
    
    /**
     * Очистить список спикеров
     */
    fun clear() {
        speakers.clear()
        nextColorIndex = 0
    }
}

/**
 * Data class для спикера
 */
data class Speaker(
    val id: String,
    val name: String,
    val color: Color
)





