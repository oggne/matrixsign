package com.matrixsign

// Force re-compile


import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList

enum class DialogState {
    LISTENING,
    COMPOSING,
    SPEAKING,
    CONFIRMING
}

data class DialogUiState(
    val dialogState: DialogState = DialogState.LISTENING,
    val decodedGestureText: String = "",
    val gestureText: String = "", // Raw gesture name
    val t9Suggestions: List<String> = emptyList(),
    val selectedT9Index: Int = 0,
    val opponentLanguage: String = LanguageManager.DEFAULT_OPPONENT_LANGUAGE,
    val userSignLanguage: String = LanguageManager.DEFAULT_USER_SIGN_LANGUAGE,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isOfflineMode: Boolean = false,
    val showT9ConfirmIndicator: Boolean = false,
    val contextChanged: Boolean = false,
    val lastSttText: String = "",
    val translatedSttText: String = "",
    val currentContext: T9Predictor.DialogContext = T9Predictor.DialogContext.GENERAL,
    val fontSize: Float = 18f,
    val selectedDevice: String = SettingsManager.DEVICE_NONE,
    val useArCamera: Boolean = false,
    val ttsBuffer: String = "",
    val handLandmarks: List<NormalizedLandmarkList> = emptyList(),
    // Dialog visibility & Input state
    val showSettings: Boolean = false,
    val showKeyboardInput: Boolean = false,
    val keyboardInputText: String = "",
    val showDeviceFoundDialog: String? = null,
    val rslModelAvailable: Boolean = true, // Track if RSL model is available
    val mudraConnectionState: MudraManager.ConnectionState = MudraManager.ConnectionState.DISCONNECTED
)
