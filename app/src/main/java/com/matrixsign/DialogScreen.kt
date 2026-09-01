package com.matrixsign

// Force re-compile


import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import kotlinx.coroutines.flow.first
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Основной экран диалога в стиле Матрица
 * 
 * КЛЮЧЕВЫЕ ИСПРАВЛЕНИЯ:
 * - Использование ViewModel для управления состоянием
 * - Strict State Machine
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DialogScreen(
    viewModel: DialogViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Разрешения
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    )
    
    // Auto Device Detection (Logic moved to VM)
    
    // Запуск сканирования и прослушивания при наличии разрешений
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.startDeviceScanning()
            viewModel.startSpeechRecognition()
        }
    }
    
    // Проверка разрешений
    if (!permissionsState.allPermissionsGranted) {
        LaunchedEffect(Unit) {
            permissionsState.launchMultiplePermissionRequest()
        }
        PermissionRequestScreen()
        return
    }

    val matrixFontFamily = FontFamily.Monospace
    val matrixGreen = Color(0xFF00FF00)
    
    var imageWidth by remember { mutableStateOf(1080) }
    var imageHeight by remember { mutableStateOf(1920) }

    // Диалог обнаружения устройства
    if (uiState.showDeviceFoundDialog != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDeviceFoundResult(false) },
            title = { Text("Устройство обнаружено", color = matrixGreen, fontFamily = matrixFontFamily) },
            text = { Text("Найдено устройство: ${uiState.showDeviceFoundDialog}. Подключиться?", color = matrixGreen, fontFamily = matrixFontFamily) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onDeviceFoundResult(true) }
                ) { Text("Да", color = matrixGreen, fontFamily = matrixFontFamily) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDeviceFoundResult(false) }) {
                    Text("Нет", color = matrixGreen, fontFamily = matrixFontFamily)
                }
            },
            containerColor = Color.Black,
            textContentColor = matrixGreen
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Камера с overlay для landmarks
        CameraPreviewWithOverlay(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f),
            onImageAvailable = viewModel::processImage,
            landmarks = uiState.handLandmarks,
            lifecycleOwner = lifecycleOwner,
            onImageSizeChanged = { width, height ->
                imageWidth = width
                imageHeight = height
            },
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            useArCamera = uiState.useArCamera,
            arGlassesManager = viewModel.arGlassesManager
        )
        
        // Overlay с UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .systemBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .then(
                    if (uiState.useArCamera) Modifier.background(Color.Transparent)
                    else Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.3f)
                            )
                        )
                    )
                )
        ) {
            // Список сообщений
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = false
            ) {
                items(uiState.chatMessages) { message ->
                    ChatMessageItem(
                        message = message,
                        fontSize = uiState.fontSize.sp,
                        fontFamily = matrixFontFamily
                    )
                }
            }
            
            // Текущий текст (STT или Жесты) - ALWAYS show if composing, even with chat history
            val textToDisplay = when {
                (uiState.dialogState == DialogState.COMPOSING || uiState.dialogState == DialogState.CONFIRMING) -> uiState.decodedGestureText
                uiState.translatedSttText.isNotEmpty() -> uiState.translatedSttText
                uiState.lastSttText.isNotEmpty() -> uiState.lastSttText
                else -> ""
            }
            
            // Show text even when chat has messages (producer requirement)
            if (textToDisplay.isNotEmpty()) {
                AnimatedText(
                    text = textToDisplay,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    fontSize = uiState.fontSize.sp,
                    color = matrixGreen,
                    fontFamily = matrixFontFamily
                )
            }
            
            // Raw classifier top-1 for live debug (unsmoothed)
            if (uiState.rawClassifierTop1.isNotEmpty() && uiState.userSignLanguage == LanguageManager.USER_SIGN_LANGUAGE_RSL) {
                Text(
                    text = "🔍 ${uiState.rawClassifierTop1} (${String.format("%.2f", uiState.rawClassifierConfidence)})",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = (uiState.fontSize * 0.8f).sp,
                    color = matrixGreen.copy(alpha = 0.6f),
                    fontFamily = matrixFontFamily
                )
            }
            
            // T9 Предсказания
            if (uiState.t9Suggestions.isNotEmpty() && 
               (uiState.dialogState == DialogState.COMPOSING || uiState.dialogState == DialogState.CONFIRMING)) {
                T9SuggestionsRow(
                    suggestions = uiState.t9Suggestions,
                    selectedIndex = uiState.selectedT9Index,
                    onSelect = { /* TODO: Implement click selection in VM if needed */ },
                    fontSize = uiState.fontSize.sp,
                    showConfirmIndicator = uiState.showT9ConfirmIndicator
                )
            }
            
            // Кнопки управления (Settings, Keyboard, Confirm)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settings
                FloatingActionButton(
                    onClick = { viewModel.showSettings() },
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = matrixGreen
                ) {
                     Text("⚙️", fontSize = 24.sp)
                }
                
                // Keyboard Input
                FloatingActionButton(
                    onClick = { viewModel.showKeyboardInput() },
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = matrixGreen,
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text("⌨️", fontSize = 24.sp)
                }
                
                // Add Gesture (Custom)
                FloatingActionButton(
                    onClick = { 
                        val intent = Intent(context, GestureCreationActivity::class.java)
                        context.startActivity(intent)
                    },
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = matrixGreen,
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Gesture",
                        tint = matrixGreen
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))

                // Confirm Button (Visible only when composing with suggestions)
                if ((uiState.dialogState == DialogState.COMPOSING || uiState.dialogState == DialogState.CONFIRMING) && 
                    uiState.t9Suggestions.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { viewModel.handleT9Confirm() },
                        containerColor = matrixGreen,
                        contentColor = Color.Black
                    ) {
                        Text("✅", fontSize = 24.sp)
                    }
                }
            }
        }
        
        // Settings Dialog
        if (uiState.showSettings) {
             SettingsDialog(
                fontSize = uiState.fontSize.sp,
                onFontSizeChange = { newSize ->
                    scope.launch { viewModel.settingsManager.setFontSize(newSize.value) }
                },
                onDismiss = { viewModel.hideSettings() },
                context = context,
                mudraManager = viewModel.mudraManager,
                arGlassesManager = viewModel.arGlassesManager
            )
        }
        
        // Keyboard Input Dialog
        if (uiState.showKeyboardInput) {
            AlertDialog(
                onDismissRequest = { viewModel.hideKeyboardInput() },
                title = { Text("Ввод текста", color = matrixGreen, fontFamily = matrixFontFamily) },
                text = {
                     TextField(
                         value = uiState.keyboardInputText,
                         onValueChange = { viewModel.updateKeyboardInput(it) },
                         colors = TextFieldDefaults.colors(
                             focusedContainerColor = Color.Black,
                             unfocusedContainerColor = Color.Black,
                             focusedTextColor = matrixGreen,
                             unfocusedTextColor = matrixGreen,
                             cursorColor = matrixGreen
                         )
                     )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.confirmKeyboardInput() }
                    ) { Text("OK", color = matrixGreen) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideKeyboardInput() }) { Text("Отмена", color = matrixGreen) }
                },
                containerColor = Color.Black
            )
        }
        
        // Индикатор изменения контекста
        if (uiState.contextChanged) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF00FF00).copy(alpha = 0.1f))
            )
        }
    }
}



@Composable
fun CameraPreviewWithOverlay(
    modifier: Modifier = Modifier,
    onImageAvailable: (ImageProxy) -> Unit,
    landmarks: List<NormalizedLandmarkList>,
    lifecycleOwner: LifecycleOwner,
    onImageSizeChanged: (Int, Int) -> Unit = { _, _ -> },
    imageWidth: Int = 1080,
    imageHeight: Int = 1920,
    useArCamera: Boolean = false,
    arGlassesManager: ArGlassesManager? = null
) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    
    // Состояние для отслеживания событий жизненного цикла
    var lifecycleEvent by remember { mutableStateOf(androidx.lifecycle.Lifecycle.Event.ON_CREATE) }
    
    // Подписываемся на события жизненного цикла
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            lifecycleEvent = event
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Box(modifier = modifier) {
        // PreviewView для камеры
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    previewView = this
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Overlay для landmarks
        MpOverlayView(
            landmarks = landmarks,
            modifier = Modifier.fillMaxSize(),
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }
    
    // Используем event в ключе LaunchedEffect, чтобы перезапускать привязку при ON_RESUME
    LaunchedEffect(cameraProviderFuture, useArCamera, lifecycleEvent) {
        if (useArCamera) {
            // Если AR-очки, не используем CameraX
            return@LaunchedEffect
        }
        
        // Привязываем камеру только если активность активна (RESUMED или STARTED)
        // Это восстановит камеру после того, как GestureCreationActivity вызвал unbindAll()
        if (lifecycleEvent == androidx.lifecycle.Lifecycle.Event.ON_RESUME || 
            lifecycleEvent == androidx.lifecycle.Lifecycle.Event.ON_START) {
            
            try {
                val cameraProvider = withContext(Dispatchers.IO) {
                    cameraProviderFuture.get()
                }
                
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView?.surfaceProvider)
                    }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            onImageSizeChanged(imageProxy.width, imageProxy.height)
                            onImageAvailable(imageProxy)
                        }
                    }
                
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                cameraProvider.unbindAll() // Очищаем предыдущие привязки
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                android.util.Log.e("MatrixSign", "Camera error", e)
            }
        }
    }
}

@Composable
fun AnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    fontFamily: FontFamily
) {
    val infiniteTransition = rememberInfiniteTransition(label = "matrix")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )
    
    Box(
        modifier = modifier
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            color = color,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = offsetY.dp)
        )
    }
}

@Composable
fun T9SuggestionsRow(
    suggestions: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    fontSize: androidx.compose.ui.unit.TextUnit,
    showConfirmIndicator: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            TextButton(
                onClick = { onSelect(index) },
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = suggestion,
                    fontSize = fontSize * 0.7f,
                    color = if (index == selectedIndex) {
                        if (showConfirmIndicator) Color(0xFF00FFFF) else Color(0xFF00FF00)
                    } else {
                        Color(0xFF00FF40)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun BoxScope.T9ConfirmIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "confirm")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 80.dp)
            .size(24.dp)
            .background(Color(0xFF00FF00).copy(alpha = alpha))
    )
}

@Composable
fun SettingsDialog(
    fontSize: androidx.compose.ui.unit.TextUnit,
    onFontSizeChange: (androidx.compose.ui.unit.TextUnit) -> Unit,
    onDismiss: () -> Unit,
    context: Context,
    mudraManager: MudraManager,
    arGlassesManager: ArGlassesManager
) {
    val settingsManager = remember { SettingsManager(context) }
    val consentManager = remember { ConsentManager(context) }
    val scope = rememberCoroutineScope()
    var selectedDevice by remember { mutableStateOf(SettingsManager.DEVICE_NONE) }
    var showDeviceSelection by remember { mutableStateOf(false) }
    var showLanguageSelection by remember { mutableStateOf(false) }
    var showMudraCalibration by remember { mutableStateOf(false) }
    var showArCalibration by remember { mutableStateOf(false) }
    var showRevokeConsentDialog by remember { mutableStateOf(false) }
    
    // Загружаем выбранное устройство
    LaunchedEffect(Unit) {
        selectedDevice = settingsManager.selectedDevice.first()
    }

    val languageManager = remember { LanguageManager(context) }
    var selectedSignLanguage by remember { mutableStateOf(LanguageManager.DEFAULT_USER_SIGN_LANGUAGE) }
    
    // Загружаем выбранный язык жестов
    LaunchedEffect(Unit) {
        selectedSignLanguage = languageManager.userSignLanguage.first()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Настройки",
                color = Color(0xFF00FF00),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Размер шрифта
                Text(
                    text = "Размер шрифта: ${fontSize.value.toInt()}sp",
                    color = Color(0xFF00FF40),
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = fontSize.value,
                    onValueChange = { newValue -> onFontSizeChange(newValue.sp) },
                    valueRange = 28f..48f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00FF00),
                        activeTrackColor = Color(0xFF00FF40)
                    )
                )
                
                Divider(color = Color(0xFF00FF40).copy(alpha = 0.3f))
                
                // Языковые настройки
                Text(
                    text = "Язык жестов",
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showLanguageSelection = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF40).copy(alpha = 0.2f),
                        contentColor = Color(0xFF00FF00)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Выбранный язык: ${getSignLanguageName(selectedSignLanguage)}",
                        fontFamily = FontFamily.Monospace
                    )
                }

                Divider(color = Color(0xFF00FF40).copy(alpha = 0.3f))
                
                // Выбор оборудования
                Text(
                    text = "Оборудование",
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Кнопка выбора устройства
                Button(
                    onClick = { showDeviceSelection = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF40).copy(alpha = 0.2f),
                        contentColor = Color(0xFF00FF00)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Выбрать устройство: ${getDeviceName(selectedDevice)}",
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                // Кнопка калибровки Mudra Link
                if (selectedDevice == SettingsManager.DEVICE_MUDRA) {
                    Button(
                        onClick = { showMudraCalibration = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF40).copy(alpha = 0.2f),
                            contentColor = Color(0xFF00FF00)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Калибровка Mudra Link",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                // Кнопка калибровки AR очков
                if (selectedDevice in listOf(
                    SettingsManager.DEVICE_INMO_AIR3,
                    SettingsManager.DEVICE_XREAL_AIR2,
                    SettingsManager.DEVICE_XREAL_ULTRA,
                    SettingsManager.DEVICE_RAY_BAN_META
                )) {
                    Button(
                        onClick = { showArCalibration = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF40).copy(alpha = 0.2f),
                            contentColor = Color(0xFF00FF00)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Калибровка AR очков",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                       
                Divider(color = Color(0xFF00FF40).copy(alpha = 0.3f))
                       
                // Кнопка отзыва согласия
                Text(
                    text = "Управление данными",
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                       
                Button(
                    onClick = { showRevokeConsentDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.2f),
                        contentColor = Color.Red
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Отозвать согласие и удалить все данные",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "OK",
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        containerColor = Color.Black,
        textContentColor = Color(0xFF00FF40)
    )
    
    // Диалог выбора устройства
    if (showDeviceSelection) {
        DeviceSelectionDialog(
            currentDevice = selectedDevice,
            onDeviceSelected = { device ->
                selectedDevice = device
                scope.launch {
                    settingsManager.setSelectedDevice(device)
                }
                showDeviceSelection = false
            },
            onDismiss = { showDeviceSelection = false }
        )
    }
    
    // Диалог калибровки Mudra Link
    if (showMudraCalibration) {
        MudraCalibrationDialog(
            settingsManager = settingsManager,
            mudraManager = mudraManager,
            onDismiss = { showMudraCalibration = false }
        )
    }
    
    // Диалог калибровки AR очков
    if (showArCalibration) {
        ArCalibrationDialog(
            settingsManager = settingsManager,
            arGlassesManager = arGlassesManager,
            deviceType = selectedDevice,
            onDismiss = { showArCalibration = false }
        )
    }
    
    // Диалог подтверждения отзыва согласия
    if (showRevokeConsentDialog) {
        RevokeConsentDialog(
            onConfirm = {
                scope.launch {
                    consentManager.revokeConsent()
                    // Перезапускаем приложение на экран согласия
                    val intent = android.content.Intent(context, OnboardingConsentActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
                    if (context is Activity) {
                        (context as Activity).finishAffinity()
                    }
                }
            },
            onDismiss = { showRevokeConsentDialog = false }
        )
    }

    // Диалог выбора языка
    if (showLanguageSelection) {
        LanguageSelectionDialog(
            currentLanguage = selectedSignLanguage,
            onLanguageSelected = { language ->
                selectedSignLanguage = language
                scope.launch {
                    languageManager.setUserSignLanguage(language)
                    // TODO: Notify ViewModel to reload models? 
                    // ViewModel observes Settings, but maybe not LanguageSettings automatically.
                    // For now, relies on observing Flow in ViewModel if implemented, or restart may be needed.
                }
                showLanguageSelection = false
            },
            onDismiss = { showLanguageSelection = false }
        )
    }
}

@Composable
fun getSignLanguageName(code: String): String {
    return when (code) {
        LanguageManager.USER_SIGN_LANGUAGE_RSL -> "Русский (РЖЯ)"
        LanguageManager.USER_SIGN_LANGUAGE_ASL -> "English (ASL)"
        else -> code.uppercase()
    }
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf(
        LanguageManager.USER_SIGN_LANGUAGE_RSL to "Русский (РЖЯ) [Slovo]",
        LanguageManager.USER_SIGN_LANGUAGE_ASL to "English (ASL)"
    )
    val matrixGreen = Color(0xFF00FF00)
    val matrixFontFamily = FontFamily.Monospace

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Выберите язык жестов",
                color = matrixGreen,
                fontFamily = matrixFontFamily
            )
        },
        text = {
            Column {
                languages.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (code == currentLanguage),
                            onClick = { onLanguageSelected(code) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = matrixGreen,
                                unselectedColor = matrixGreen.copy(alpha = 0.5f)
                            )
                        )
                        TextButton(onClick = { onLanguageSelected(code) }) {
                            Text(
                                text = name,
                                color = if (code == currentLanguage) matrixGreen else matrixGreen.copy(alpha = 0.7f),
                                fontFamily = matrixFontFamily
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = matrixGreen, fontFamily = matrixFontFamily)
            }
        },
        containerColor = Color.Black,
        textContentColor = matrixGreen
    )
}

@Composable
fun RevokeConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val matrixGreen = Color(0xFF00FF41)
    val matrixFontFamily = FontFamily.Monospace
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Отозвать согласие?",
                color = Color.Red,
                fontFamily = matrixFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Вы уверены, что хотите отозвать согласие на обработку биометрических данных?",
                    color = matrixGreen,
                    fontFamily = matrixFontFamily,
                    fontSize = 18.sp
                )
                Text(
                    text = "Это действие:\n• Удалит все ваши данные\n• Удалит кастомные жесты\n• Удалит настройки\n• Вернёт вас на экран согласия",
                    color = matrixGreen.copy(alpha = 0.9f),
                    fontFamily = matrixFontFamily,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Да, отозвать",
                    fontFamily = matrixFontFamily
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Отмена",
                    color = matrixGreen,
                    fontFamily = matrixFontFamily
                )
            }
        },
        containerColor = Color.Black,
        textContentColor = matrixGreen
    )
}

@Composable
fun PermissionRequestScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Требуются разрешения на камеру и микрофон",
            color = Color(0xFF00FF40),
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage, fontSize: androidx.compose.ui.unit.TextUnit, fontFamily: FontFamily) {
    val alignment = if (!message.isIncoming) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (!message.isIncoming) Color.Transparent else Color.Black.copy(alpha = 0.4f)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .background(backgroundColor)
                .padding(8.dp)
        ) {
            // Имя спикера (только для входящих сообщений)
            if (message.isIncoming) {
                Text(
                    text = message.speakerName,
                    fontSize = fontSize * 0.8f,
                    color = message.speakerColor,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // Текст сообщения
            Text(
                text = message.text,
                color = message.speakerColor,
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

fun getDeviceName(device: String): String {
    return when (device) {
        SettingsManager.DEVICE_NONE -> "Нет"
        SettingsManager.DEVICE_MUDRA -> "Mudra Link"
        SettingsManager.DEVICE_INMO_AIR3 -> "Inmo Air3"
        SettingsManager.DEVICE_XREAL_AIR2 -> "Xreal Air 2"
        SettingsManager.DEVICE_XREAL_ULTRA -> "Xreal Ultra"
        SettingsManager.DEVICE_RAY_BAN_META -> "Ray-Ban Meta"
        else -> "Неизвестно"
    }
}
