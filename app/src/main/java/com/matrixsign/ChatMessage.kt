package com.matrixsign

import androidx.compose.ui.graphics.Color

/**
 * Data class для сообщения в групповом чате
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val speakerId: String,
    val speakerName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean = true, // true для входящих (STT), false для исходящих (жесты)
    val speakerColor: Color = Color(0xFF00FF41)
)





