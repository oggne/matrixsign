package com.matrixsign

// Force re-compile


import android.app.Application
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList
import androidx.compose.ui.graphics.Color
import javax.inject.Inject

@HiltViewModel
class DialogViewModel @Inject constructor(
    private val application: Application,
    val gestureLibraryManager: GestureLibraryManager,
    val settingsManager: SettingsManager,
    val mudraManager: MudraManager,
    val t9Predictor: T9Predictor,
    val speechRecognizer: SpeechRecognizerHelper,
    val ttsHelper: TextToSpeechHelper,
    val arGlassesManager: ArGlassesManager,
    val translationHelper: TranslationHelper,
    val languageManager: LanguageManager,
    val speakerManager: SpeakerManager,
    val gestureToTextMapper: GestureToTextMapper,
    val deviceAutoDetector: DeviceAutoDetector
) : ViewModel() {

    private val context = application.applicationContext

    // UI State
    private val _uiState = MutableStateFlow(DialogUiState())
    val uiState: StateFlow<DialogUiState> = _uiState.asStateFlow()

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // Internal state for logic
    private var gestureRoles: Map<String, String> = emptyMap()
    private var lastNavigationTime: Long = 0
    private val navigationCooldown = 500L
    private var speakGestureStartTime: Long = 0
    private val speakGestureDuration = 1500L

    // Gesture Helper (Initializing lazily or manually to handle callbacks)
    lateinit var gestureHelper: GestureRecognizerHelper
        private set

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            // Load Gesture Roles
            launch {
                val userId = UserIdManager(context).getUserId()
                gestureLibraryManager.getAllGestures(userId).collect { gestures ->
                    gestureRoles = gestures.filter { it.role != null }
                        .associate { it.gestureLabel to it.role!! }
                }
            }

            // Load Settings & Language
            // Observe Opponent Language
            launch {
                languageManager.opponentLanguage.collect { lang ->
                    if (lang != _uiState.value.opponentLanguage) {
                        _uiState.update { it.copy(opponentLanguage = lang) }
                        startSpeechRecognition(lang)
                    }
                }
            }

            // Observe User Sign Language
            launch {
                languageManager.userSignLanguage.collect { lang ->
                    if (lang != _uiState.value.userSignLanguage) {
                        _uiState.update { it.copy(userSignLanguage = lang) }
                        gestureHelper.switchSignLanguage(lang)
                    }
                }
            }

            // Observe Font Size
            launch {
                settingsManager.selectedDevice.collect { device ->
                     _uiState.update { it.copy(selectedDevice = device) }
                }
            }

            // Initial load for non-flow settings (if any)
            launch {
                initializeGestureHelper()

                val fontSize = settingsManager.getFontSize()
                if (fontSize > 0f) {
                     _uiState.update { it.copy(fontSize = fontSize) }
                }
                t9Predictor.loadDictionary()
            }

            // Observe Speech
            launch {
                speechRecognizer.recognizedText.collect { text ->
                    handleSpeechResult(text)
                }
            }

            // Observe Offline Mode
            launch {
                speechRecognizer.isOfflineMode.collect { isOffline ->
                    _uiState.update { it.copy(isOfflineMode = isOffline) }
                }
            }

            // Observe Device Detection
            launch {
                deviceAutoDetector.detectedDevice.collect { device ->
                     if (device != SettingsManager.DEVICE_NONE && device != _uiState.value.selectedDevice) {
                         _uiState.update { it.copy(showDeviceFoundDialog = device) }
                     }
                }
            }
            
            // Observe Mudra Connection State
            launch {
                mudraManager.connectionState.collect { state ->
                    _uiState.update { it.copy(mudraConnectionState = state) }
                }
            }
        }
    }

    // Dialog Management
    fun showSettings() { _uiState.update { it.copy(showSettings = true) } }
    fun hideSettings() { _uiState.update { it.copy(showSettings = false) } }

    fun showKeyboardInput() { _uiState.update { it.copy(showKeyboardInput = true) } }
    fun hideKeyboardInput() { _uiState.update { it.copy(showKeyboardInput = false, keyboardInputText = "") } }

    fun updateKeyboardInput(text: String) { _uiState.update { it.copy(keyboardInputText = text) } }

    fun confirmKeyboardInput() {
        val text = _uiState.value.keyboardInputText
        if (text.isNotEmpty()) {
            val current = _uiState.value.decodedGestureText
            val newText = if (current.isNotEmpty()) "$current $text" else text
            _uiState.update { it.copy(decodedGestureText = newText) }
            ttsHelper.addToBuffer(text)
        }
        hideKeyboardInput()
    }

    fun onDeviceFoundResult(confirm: Boolean) {
        val device = _uiState.value.showDeviceFoundDialog
        if (confirm && device != null) {
            viewModelScope.launch { settingsManager.setSelectedDevice(device) }
        }
        _uiState.update { it.copy(showDeviceFoundDialog = null) }
    }

    fun startDeviceScanning() {
        deviceAutoDetector.startScanning()
    }

    private fun initializeGestureHelper() {
        gestureHelper = GestureRecognizerHelper(
            context = context,
            onResults = { result -> processGestureResult(result) },
            onHandLandmarks = { landmarks ->
                 _uiState.update { it.copy(handLandmarks = landmarks) }
            },
            onError = { msg, err -> Log.e("DialogVM", msg, err) },
            onCustomGesture = { gestureName ->
                viewModelScope.launch {
                     handleCustomGesture(gestureName)
                }
            },
            onRawClassifierResult = { label, confidence ->
                // Update raw top-1 immediately for live debug readout (unsmoothed)
                _uiState.update { it.copy(
                    rawClassifierTop1 = label,
                    rawClassifierConfidence = confidence
                )}
            },
            initialSignLanguage = _uiState.value.userSignLanguage
        )

        // Load initial sign language model
        viewModelScope.launch {
            // Ensure gesture recognizer model is loaded for current language
            gestureHelper.switchSignLanguage(_uiState.value.userSignLanguage)
            
            // Update UI state with RSL availability
            val isRslAvailable = gestureHelper.isRslAvailable()
            _uiState.update { it.copy(rslModelAvailable = isRslAvailable) }
            
            if (!isRslAvailable && _uiState.value.userSignLanguage == LanguageManager.USER_SIGN_LANGUAGE_RSL) {
                Log.w("DialogVM", "РЖЯ выбран, но модель недоступна - используется базовое распознавание")
            }
        }

        // Mudra callback
        mudraManager.setT9ConfirmCallback {
            handleT9Confirm()
        }
    }

    fun startSpeechRecognition(language: String = _uiState.value.opponentLanguage) {
        viewModelScope.launch(Dispatchers.Main) {
            speechRecognizer.startListening(language)
        }
    }

    private fun handleSpeechResult(text: String) {
        if (text.isEmpty() || text == _uiState.value.lastSttText) return

        viewModelScope.launch {
            val userLangCode = languageManager.getSpeechLanguageCode(_uiState.value.userSignLanguage)
            val translated = translationHelper.translateAuto(text, userLangCode) ?: text

            val detectedContext = t9Predictor.detectContextFromStt(translated)

            if (detectedContext != _uiState.value.currentContext && detectedContext != T9Predictor.DialogContext.GENERAL) {
                 handleContextChange(detectedContext)
            }

            // Add message
            val speaker = speakerManager.getOrCreateSpeaker("opponent", "Оппонент")
            // Constructor: id, speakerId, speakerName, text, timestamp, isIncoming, speakerColor
            val message = ChatMessage(
                speakerId = speaker.id, 
                speakerName = speaker.name, 
                text = translated, 
                isIncoming = true, 
                speakerColor = speaker.color
            )

            _uiState.update { 
                it.copy(
                    lastSttText = text,
                    translatedSttText = translated,
                    chatMessages = it.chatMessages + message
                )
            }
        }
    }

    fun processImage(imageProxy: androidx.camera.core.ImageProxy) {
        if (::gestureHelper.isInitialized) {
            gestureHelper.recognizeGesture(imageProxy)
        } else {
            imageProxy.close()
        }
    }

    private fun handleContextChange(newContext: T9Predictor.DialogContext) {
        t9Predictor.setContext(newContext)
        t9Predictor.clearSequence()
        ttsHelper.clearBuffer()

        _uiState.update { 
            it.copy(
                currentContext = newContext,
                decodedGestureText = "",
                ttsBuffer = "",
                contextChanged = true,
                dialogState = DialogState.LISTENING
            )
        }

        vibrateContextChange()

        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(contextChanged = false, dialogState = DialogState.COMPOSING) }
        }
    }

    private fun processGestureResult(result: GestureRecognizerResult) {
        val gestures = result.gestures()
        if (gestures.isEmpty() || gestures[0].isEmpty()) {
             speakGestureStartTime = 0
             return
        }

        val topGesture = gestures[0][0]
        val gestureName = topGesture.categoryName()
        val confidence = topGesture.score()

        viewModelScope.launch(Dispatchers.Main) {
             if (_uiState.value.dialogState == DialogState.SPEAKING) return@launch

             _uiState.update { it.copy(gestureText = gestureName) }

             // Command logic
             var command = gestureRoles[gestureName]
             if (command == null) {
                  command = when(gestureName) {
                      "Thumb_Down" -> if (!gestureRoles.containsValue("NEXT")) "NEXT" else null
                      "Thumb_Up" -> if (!gestureRoles.containsValue("PREV")) "PREV" else null
                      "Open_Palm" -> if (!gestureRoles.containsValue("SPEAK")) "SPEAK" else null
                      "Closed_Fist" -> if (!gestureRoles.containsValue("CONFIRM")) "CONFIRM" else null
                      else -> null
                  }
             }

             if (command != null) {
                 handleCommand(command)
                 if (command == "NEXT" || command == "PREV") return@launch
             } else {
                 speakGestureStartTime = 0
             }

             if (confidence >= 0.5f) {
                 if (_uiState.value.dialogState == DialogState.LISTENING) {
                     _uiState.update { it.copy(dialogState = DialogState.COMPOSING) }
                 }
             }
        }
    }

    // New method to be called from UI when landmarks are updated, or if we change Helper to pass both
    fun processLandmarks(landmarks: List<NormalizedLandmarkList>, result: GestureRecognizerResult) {
          viewModelScope.launch(Dispatchers.IO) {
               val decoded = gestureToTextMapper.gestureToText(result, landmarks)
               if (decoded.isNotEmpty()) {
                   withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(decodedGestureText = decoded) }
                        vibrateShort()
                   }
               }
          }
    }

    private fun handleCommand(command: String) {
        val currentTime = System.currentTimeMillis()
        when (command) {
            "NEXT" -> {
                if (currentTime - lastNavigationTime > navigationCooldown) {
                    t9Predictor.selectNext()
                    lastNavigationTime = currentTime
                    updateT9State()
                }
            }
            "PREV" -> {
                if (currentTime - lastNavigationTime > navigationCooldown) {
                    t9Predictor.selectPrevious()
                    lastNavigationTime = currentTime
                    updateT9State()
                }
            }
            "CONFIRM" -> handleT9Confirm()
            "SPEAK" -> {
                if (speakGestureStartTime == 0L) {
                    speakGestureStartTime = currentTime
                } else if (currentTime - speakGestureStartTime > speakGestureDuration) {
                    speakFullPhrase()
                    speakGestureStartTime = 0L
                    vibrateLong()
                }
            }
        }
        if (command != "SPEAK") speakGestureStartTime = 0L
    }

    fun handleT9Confirm() {
        viewModelScope.launch {
            _uiState.update { it.copy(dialogState = DialogState.CONFIRMING) }
            val confirmed = t9Predictor.confirmT9()
            Log.d("DialogVM", "Confirmed: $confirmed")

            if (confirmed != null) {
                ttsHelper.addToBuffer(confirmed)

                val currentText = _uiState.value.decodedGestureText
                val newText = if (currentText.isNotEmpty()) "$currentText $confirmed" else confirmed

                _uiState.update { it.copy(
                    decodedGestureText = newText,
                    showT9ConfirmIndicator = true,
                    ttsBuffer = ttsHelper.getBuffer()
                )}

                vibrateConfirm()
                delay(1000)
                _uiState.update { it.copy(
                    showT9ConfirmIndicator = false,
                    dialogState = DialogState.COMPOSING
                )}
                updateT9State()
            }
        }
    }

    fun speakFullPhrase() {
        val phrase = ttsHelper.getBuffer()
        if (phrase.isNotEmpty()) {
             viewModelScope.launch {
                 val userLangCode = languageManager.getSpeechLanguageCode(_uiState.value.userSignLanguage)
                 val targetLang = _uiState.value.opponentLanguage
                 val translated = withContext(Dispatchers.IO) {
                     translationHelper.translate(phrase, userLangCode, targetLang)
                 } ?: phrase

                 ttsHelper.setLanguage(targetLang)
                 ttsHelper.clearBuffer()
                 ttsHelper.addToBuffer(translated)

                 _uiState.update { it.copy(dialogState = DialogState.SPEAKING) }
                 ttsHelper.speakBufferedPhrase(targetLang)

                 // Constructor: id, speakerId, speakerName, text, timestamp, isIncoming, speakerColor
                 val message = ChatMessage(
                     speakerId = "me", 
                     speakerName = "Я", 
                     text = phrase, 
                     isIncoming = false, 
                     speakerColor = Color(0xFF00FF41)
                 )
                 _uiState.update { it.copy(
                     chatMessages = it.chatMessages + message,
                     decodedGestureText = "",
                     ttsBuffer = ""
                 )}

                 delay(2000)
                 _uiState.update { it.copy(dialogState = DialogState.LISTENING) }
             }
        }
    }

    private fun handleCustomGesture(gestureName: String) {
        if (_uiState.value.dialogState == DialogState.SPEAKING) return

        _uiState.update { 
            val current = it.decodedGestureText
            val newText = if (current.isEmpty()) gestureName else "$current $gestureName"
            it.copy(
             gestureText = gestureName,
             decodedGestureText = newText,
             dialogState = if (it.dialogState == DialogState.LISTENING) DialogState.COMPOSING else it.dialogState
        )}

        // Add recognized RSL sign to TTS buffer for later speech
        ttsHelper.addToBuffer(gestureName)
        _uiState.update { it.copy(ttsBuffer = ttsHelper.getBuffer()) }

        vibrateShort()
        if (mudraManager.isT9ConfirmGesture(gestureName)) {
            handleT9Confirm()
        }
    }

    private fun updateT9State() {
        _uiState.update { it.copy(
            t9Suggestions = t9Predictor.getCurrentPredictions(),
            selectedT9Index = t9Predictor.getSelectedIndex()
        )}
    }

    // Vibration helpers
    private fun vibrateShort() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun vibrateLong() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
             @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    private fun vibrateConfirm() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
             @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    private fun vibrateContextChange() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
        } else {
             @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 200, 100, 200), -1)
        }
    }

    override fun onCleared() {
        super.onCleared()
        gestureHelper.close()
        speechRecognizer.destroy()
        mudraManager.disconnect()
        arGlassesManager.disconnect()
    }
}
