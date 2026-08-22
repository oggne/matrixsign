package com.matrixsign

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.matrixsign.ui.theme.MatrixSignTheme
import com.matrixsign.ui.theme.getMatrixFontFamily
import kotlinx.coroutines.launch

/**
 * Activity для настройки быстрых фраз (сетка 3×4 = 12 ячеек)
 */
class QuickPhrasesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MatrixSignTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QuickPhrasesScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QuickPhrasesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA)
    )
    
    val userIdManager = remember { UserIdManager(context) }
    val quickPhraseManager = remember { QuickPhraseManager(context) }
    val gestureLibraryManager = remember { GestureLibraryManager(context) }
    val scope = rememberCoroutineScope()
    
    var userId by remember { mutableStateOf("") }
    var phrases by remember { mutableStateOf<List<QuickPhrase>>(emptyList()) }
    var selectedPosition by remember { mutableStateOf<Int?>(null) }
    var showGestureSelection by remember { mutableStateOf(false) }
    var showPhraseInput by remember { mutableStateOf(false) }
    var currentPhraseText by remember { mutableStateOf("") }
    
    val matrixGreen = Color(0xFF00FF41)
    val matrixFontFamily = getMatrixFontFamily()
    
    // Загрузка пользователя и фраз
    LaunchedEffect(Unit) {
        scope.launch {
            userId = userIdManager.getUserId()
            quickPhraseManager.getAllPhrases(userId).collect { phraseList ->
                phrases = phraseList
            }
        }
    }
    
    // Проверка разрешений
    if (!permissionsState.allPermissionsGranted) {
        LaunchedEffect(Unit) {
            permissionsState.launchMultiplePermissionRequest()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Требуется разрешение на камеру",
                color = matrixGreen,
                fontSize = 24.sp,
                fontFamily = matrixFontFamily
            )
        }
        return
    }
    
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
            Text(
                text = "Быстрые фразы",
                color = matrixGreen,
                fontSize = 24.sp,
                fontFamily = matrixFontFamily,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onBack) {
                Text(
                    text = "Назад",
                    color = matrixGreen,
                    fontFamily = matrixFontFamily
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Сетка 3×4
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed((0 until 12).toList()) { index, position ->
                val phrase = phrases.find { it.position == position }
                
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .border(2.dp, matrixGreen, MaterialTheme.shapes.medium)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable {
                            selectedPosition = position
                            if (phrase != null) {
                                currentPhraseText = phrase.phrase
                                showPhraseInput = true
                            } else {
                                showGestureSelection = true
                            }
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (phrase != null) {
                            Text(
                                text = phrase.gestureName,
                                color = matrixGreen,
                                fontSize = 14.sp,
                                fontFamily = matrixFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = phrase.phrase,
                                color = matrixGreen.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontFamily = matrixFontFamily,
                                maxLines = 2
                            )
                        } else {
                            Text(
                                text = "+",
                                color = matrixGreen.copy(alpha = 0.5f),
                                fontSize = 32.sp,
                                fontFamily = matrixFontFamily
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Диалог выбора жеста
    if (showGestureSelection && selectedPosition != null) {
        GestureSelectionDialog(
            onDismiss = { showGestureSelection = false },
            onGestureSelected = { gestureName ->
                showGestureSelection = false
                showPhraseInput = true
            },
            gestureLibraryManager = gestureLibraryManager,
            userId = userId
        )
    }
    
    // Диалог ввода фразы
    if (showPhraseInput && selectedPosition != null) {
        PhraseInputDialog(
            initialText = currentPhraseText,
            onDismiss = {
                showPhraseInput = false
                selectedPosition = null
                currentPhraseText = ""
            },
            onSave = { gestureName, phraseText ->
                scope.launch {
                    val quickPhrase = QuickPhrase(
                        userId = userId,
                        gestureName = gestureName,
                        phrase = phraseText,
                        position = selectedPosition!!
                    )
                    quickPhraseManager.savePhrase(quickPhrase)
                }
                showPhraseInput = false
                selectedPosition = null
                currentPhraseText = ""
            },
            selectedPosition = selectedPosition!!
        )
    }
}

@Composable
fun GestureSelectionDialog(
    onDismiss: () -> Unit,
    onGestureSelected: (String) -> Unit,
    gestureLibraryManager: GestureLibraryManager,
    userId: String
) {
    val matrixGreen = Color(0xFF00FF41)
    val matrixFontFamily = getMatrixFontFamily()
    var gestures by remember { mutableStateOf<List<Gesture>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        gestureLibraryManager.getAllGestures(userId).collect { gestureList ->
            gestures = gestureList
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Выберите жест",
                color = matrixGreen,
                fontFamily = matrixFontFamily,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gestures.forEach { gesture ->
                    TextButton(
                        onClick = { onGestureSelected(gesture.gestureLabel) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = gesture.gestureLabel,
                            color = matrixGreen,
                            fontFamily = matrixFontFamily
                        )
                    }
                }
            }
        },
        confirmButton = {
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
fun PhraseInputDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    selectedPosition: Int
) {
    val matrixGreen = Color(0xFF00FF41)
    val matrixFontFamily = getMatrixFontFamily()
    var gestureName by remember { mutableStateOf("") }
    var phraseText by remember { mutableStateOf(initialText) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Настройка фразы (позиция $selectedPosition)",
                color = matrixGreen,
                fontFamily = matrixFontFamily,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = gestureName,
                    onValueChange = { gestureName = it },
                    label = { Text("Имя жеста", color = matrixGreen, fontFamily = matrixFontFamily) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = matrixGreen,
                        unfocusedTextColor = matrixGreen.copy(alpha = 0.7f),
                        focusedLabelColor = matrixGreen,
                        unfocusedLabelColor = matrixGreen.copy(alpha = 0.7f),
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phraseText,
                    onValueChange = { phraseText = it },
                    label = { Text("Фраза", color = matrixGreen, fontFamily = matrixFontFamily) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = matrixGreen,
                        unfocusedTextColor = matrixGreen.copy(alpha = 0.7f),
                        focusedLabelColor = matrixGreen,
                        unfocusedLabelColor = matrixGreen.copy(alpha = 0.7f),
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (gestureName.isNotEmpty() && phraseText.isNotEmpty()) {
                        onSave(gestureName, phraseText)
                    }
                }
            ) {
                Text(
                    text = "Сохранить",
                    color = matrixGreen,
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

