package com.matrixsign

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.matrixsign.ui.theme.MatrixSignTheme
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Activity для записи и обучения кастомных жестов
 * Пользователь записывает видео жеста (5-10 сек), аннотирует его и обучает модель
 */
class GestureCreationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {




            MatrixSignTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GestureCreationScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GestureCreationScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Разрешения
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA)
    )
    
    // Состояния
    var gestureLabel by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recordedSamples by remember { mutableStateOf<List<List<NormalizedLandmarkList>>>(emptyList()) }
    var isCustomSymbol by remember { mutableStateOf(false) }
    var handLandmarks by remember { mutableStateOf<List<NormalizedLandmarkList>>(emptyList()) }
    
    val customGestureTrainer = remember { CustomGestureTrainer(context) }
    val gestureHelper = remember {
        GestureRecognizerHelper(
            context = context,
            onResults = { result: GestureRecognizerResult ->
                // Сохраняем landmarks при записи
                if (isRecording) {
                    // Обработка результатов для записи
                }
            },
            onHandLandmarks = { landmarks: List<NormalizedLandmarkList> ->
                handLandmarks = landmarks
                if (isRecording && landmarks.isNotEmpty()) {
                    // Сохраняем landmarks в текущий sample
                    val currentSample = recordedSamples.lastOrNull()?.toMutableList() ?: mutableListOf()
                    currentSample.addAll(landmarks)
                    recordedSamples = recordedSamples.dropLast(1) + listOf(currentSample)
                }
            },
            onError = { message, error ->
                android.util.Log.e("GestureCreation", message, error)
            }
        )
    }
    
    val scope = rememberCoroutineScope()
    var isTraining by remember { mutableStateOf(false) }
    var trainingProgress by remember { mutableStateOf(0f) }
    var trainingStatus by remember { mutableStateOf("") }
    
    // Подписка на прогресс обучения
    LaunchedEffect(Unit) {
        customGestureTrainer.isTraining.collect { training ->
            isTraining = training
        }
    }
    
    LaunchedEffect(Unit) {
        customGestureTrainer.trainingProgress.collect { progress ->
            trainingProgress = progress
        }
    }
    
    LaunchedEffect(Unit) {
        customGestureTrainer.trainingStatus.collect { status ->
            trainingStatus = status
        }
    }
    
    // Проверка разрешений
    if (!permissionsState.allPermissionsGranted) {
        LaunchedEffect(Unit) {
            permissionsState.launchMultiplePermissionRequest()
        }
        GesturePermissionRequestScreen()
        return
    }
    
    // Основной UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Заголовок
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "← Назад",
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "Создание жеста",
                color = Color(0xFF00FF00),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(80.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Превью камеры с landmarks
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            CameraPreviewWithOverlay(
                modifier = Modifier.fillMaxSize(),
                onImageAvailable = { imageProxy ->
                    gestureHelper.recognizeGesture(imageProxy)
                },
                landmarks = handLandmarks,
                lifecycleOwner = lifecycleOwner
            )
            
            // Индикатор записи
            if (isRecording) {
                RecordingIndicator()
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Форма для ввода метки жеста
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = gestureLabel,
                onValueChange = { gestureLabel = it },
                label = {
                    Text(
                        text = "Метка жеста (буква, слово, символ)",
                        color = Color(0xFF00FF40),
                        fontFamily = FontFamily.Monospace
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF00FF00),
                    unfocusedTextColor = Color(0xFF00FF40),
                    focusedBorderColor = Color(0xFF00FF00),
                    unfocusedBorderColor = Color(0xFF00FF40)
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Чекбокс для кастомного символа (только если не выбрана роль)
            // И выбор роли жеста
            var selectedRole by remember { mutableStateOf<String?>(null) }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Text(
                    text = "Тип жеста:",
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Радио-кнопки для выбора роли
                val roles = listOf(
                    null to "Символ / Текст",
                    "NEXT" to "Команда: ДАЛЕЕ (Next)",
                    "PREV" to "Команда: НАЗАД (Prev)",
                    "SPEAK" to "Команда: ОЗВУЧИТЬ (Speak)",
                    "CONFIRM" to "Команда: ПОДТВЕРДИТЬ (Confirm)"
                )
                
                roles.forEach { (role, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedRole = role
                                if (role != null) {
                                    isCustomSymbol = false // Команды не могут быть кастомными символами
                                    gestureLabel = label.substringAfter(": ") // Автозаполнение метки для команд
                                } else {
                                    gestureLabel = "" // Очищаем для символа
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRole == role,
                            onClick = {
                                selectedRole = role
                                if (role != null) {
                                    isCustomSymbol = false
                                    gestureLabel = label.substringAfter(": ")
                                } else {
                                    gestureLabel = ""
                                }
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF00FF00),
                                unselectedColor = Color(0xFF00FF40).copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = Color(0xFF00FF40),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                // Чекбокс "Кастомный символ" только для режима "Символ / Текст"
                if (selectedRole == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isCustomSymbol,
                            onCheckedChange = { isCustomSymbol = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF00FF00),
                                uncheckedColor = Color(0xFF00FF40)
                            )
                        )
                        Text(
                            text = "Это спецсимвол (!, @, #...)",
                            color = Color(0xFF00FF40),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            
            // Кнопки управления записью
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (!isRecording) {
                            // Начать запись
                            isRecording = true
                            recordedSamples = emptyList()
                        } else {
                            // Остановить запись
                            isRecording = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFFF0000) else Color(0xFF00FF00),
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isRecording) "⏹ Остановить" else "⏺ Записать",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Button(
                    onClick = {
                        // Очистить записанные samples
                        recordedSamples = emptyList()
                        gestureLabel = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF40).copy(alpha = 0.3f),
                        contentColor = Color(0xFF00FF00)
                    ),
                    modifier = Modifier.weight(1f),
                    enabled = !isRecording && !isTraining
                ) {
                    Text(
                        text = "Очистить",
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            // Информация о записанных samples
            if (recordedSamples.isNotEmpty()) {
                Text(
                    text = "Записано samples: ${recordedSamples.size}",
                    color = Color(0xFF00FF40),
                    fontFamily = FontFamily.Monospace
                )
            }
            
            // Кнопка обучения
            Button(
                onClick = {
                    if (gestureLabel.isNotEmpty() && recordedSamples.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            customGestureTrainer.createCustomGesture(
                                gestureLabel = gestureLabel,
                                samples = recordedSamples,
                                isCustomSymbol = isCustomSymbol,
                                role = selectedRole
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FF00),
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = gestureLabel.isNotEmpty() && 
                         recordedSamples.isNotEmpty() && 
                         !isRecording && 
                         !isTraining
            ) {
                Text(
                    text = if (isTraining) "Обучение..." else "Обучить модель",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Прогресс обучения
            if (isTraining) {
                LinearProgressIndicator(
                    progress = { trainingProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF00FF00),
                    trackColor = Color(0xFF00FF40).copy(alpha = 0.3f)
                )
                Text(
                    text = trainingStatus,
                    color = Color(0xFF00FF40),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun CameraPreviewWithOverlay(
    modifier: Modifier = Modifier,
    onImageAvailable: (ImageProxy) -> Unit,
    landmarks: List<NormalizedLandmarkList>,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    
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
            imageWidth = 1080,
            imageHeight = 1920
        )
    }
    
    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        
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
                    onImageAvailable(imageProxy)
                }
            }
        
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            android.util.Log.e("GestureCreation", "Camera error", e)
        }
    }
}

@Composable
fun BoxScope.RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(16.dp)
            .background(Color(0xFFFF0000).copy(alpha = alpha))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "● ЗАПИСЬ",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun GesturePermissionRequestScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Требуется разрешение на камеру",
            color = Color(0xFF00FF40),
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}