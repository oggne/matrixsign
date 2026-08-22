package com.matrixsign

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Цвет Matrix (читаемый)
private val matrixGreen = Color(0xFF00FF41)

/**
 * Диалог выбора устройства
 */
@Composable
fun DeviceSelectionDialog(
    currentDevice: String,
    onDeviceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val devices = listOf(
        SettingsManager.DEVICE_NONE to "Нет",
        SettingsManager.DEVICE_PHONE to "Смартфон",
        SettingsManager.DEVICE_MUDRA to "Mudra Link",
        SettingsManager.DEVICE_INMO_AIR3 to "Inmo Air3",
        SettingsManager.DEVICE_XREAL_AIR2 to "Xreal Air 2",
        SettingsManager.DEVICE_XREAL_ULTRA to "Xreal Ultra",
        SettingsManager.DEVICE_RAY_BAN_META to "Ray-Ban Meta"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Выбор оборудования",
                color = matrixGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                devices.forEach { (deviceId, deviceName) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = deviceName,
                            color = if (currentDevice == deviceId) 
                                matrixGreen else matrixGreen.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (currentDevice == deviceId) FontWeight.Bold else FontWeight.Medium
                        )
                        RadioButton(
                            selected = currentDevice == deviceId,
                            onClick = { onDeviceSelected(deviceId) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = matrixGreen,
                                unselectedColor = matrixGreen.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "OK",
                    color = matrixGreen,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        containerColor = Color.Black,
        textContentColor = matrixGreen
    )
}

/**
 * Диалог калибровки Mudra Link
 */
@Composable
fun MudraCalibrationDialog(
    settingsManager: SettingsManager,
    mudraManager: MudraManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var sensitivity by remember { mutableStateOf(0.5f) }
    var gestureThreshold by remember { mutableStateOf(0.7f) }
    var isConnected by remember { mutableStateOf(false) }
    
    // Загружаем текущие настройки
    LaunchedEffect(Unit) {
        val settings = settingsManager.getMudraSettings()
        sensitivity = settings.sensitivity
        gestureThreshold = settings.gestureThreshold
        isConnected = settings.isConnected
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Калибровка Mudra Link",
                color = matrixGreen,
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
                // Статус подключения
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "Статус: ${if (isConnected) "Подключено" else "Не подключено"}",
                        color = if (isConnected) matrixGreen else Color(0xFFFF0000),
                        fontFamily = FontFamily.Monospace
                    )
                    Button(
                        onClick = {
                            if (isConnected) {
                                mudraManager.disconnect()
                                isConnected = false
                            } else {
                                mudraManager.connectToMudra()
                                isConnected = mudraManager.isConnected.value
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) 
                                Color(0xFFFF0000).copy(alpha = 0.3f) 
                            else matrixGreen.copy(alpha = 0.3f),
                            contentColor = if (isConnected) Color(0xFFFF0000) else matrixGreen
                        )
                    ) {
                        Text(
                            text = if (isConnected) "Отключить" else "Подключить",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                Divider(color = matrixGreen.copy(alpha = 0.3f))
                
                // Чувствительность
                Text(
                    text = "Чувствительность: ${(sensitivity * 100).toInt()}%",
                    color = matrixGreen,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = matrixGreen,
                        activeTrackColor = matrixGreen.copy(alpha = 0.7f)
                    )
                )
                
                // Порог распознавания жеста
                Text(
                    text = "Порог жеста: ${(gestureThreshold * 100).toInt()}%",
                    color = matrixGreen,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = gestureThreshold,
                    onValueChange = { gestureThreshold = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = matrixGreen,
                        activeTrackColor = matrixGreen.copy(alpha = 0.7f)
                    )
                )
                
                // Инструкция
                Text(
                    text = "Настройте чувствительность и порог распознавания для оптимальной работы с жестами.",
                    color = matrixGreen.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        settingsManager.saveMudraSettings(
                            MudraCalibrationSettings(
                                sensitivity = sensitivity,
                                gestureThreshold = gestureThreshold,
                                isConnected = isConnected
                            )
                        )
                    }
                    onDismiss()
                }
            ) {
                Text(
                    text = "Сохранить",
                    color = matrixGreen,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Отмена",
                    color = matrixGreen.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        containerColor = Color.Black,
        textContentColor = matrixGreen
    )
}

/**
 * Диалог калибровки AR очков
 */
@Composable
fun ArCalibrationDialog(
    settingsManager: SettingsManager,
    arGlassesManager: ArGlassesManager,
    deviceType: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var overlayX by remember { mutableStateOf(0.5f) }
    var overlayY by remember { mutableStateOf(0.5f) }
    var overlayScale by remember { mutableStateOf(1.0f) }
    var brightness by remember { mutableStateOf(0.8f) }
    var isConnected by remember { mutableStateOf(false) }
    
    // Загружаем текущие настройки
    LaunchedEffect(Unit) {
        val settings = settingsManager.getArSettings()
        overlayX = settings.overlayX
        overlayY = settings.overlayY
        overlayScale = settings.overlayScale
        brightness = settings.brightness
        isConnected = settings.isConnected
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Калибровка AR очков",
                color = matrixGreen,
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
                // Устройство
                Text(
                    text = "Устройство: ${getDeviceName(deviceType)}",
                    color = matrixGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                
                // Статус подключения
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "Статус: ${if (isConnected) "Подключено" else "Не подключено"}",
                        color = if (isConnected) matrixGreen else Color(0xFFFF0000),
                        fontFamily = FontFamily.Monospace
                    )
                    Button(
                        onClick = {
                            if (isConnected) {
                                arGlassesManager.disconnect()
                                isConnected = false
                            } else {
                                arGlassesManager.connect()
                                isConnected = arGlassesManager.isConnected.value
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) 
                                Color(0xFFFF0000).copy(alpha = 0.3f) 
                            else matrixGreen.copy(alpha = 0.3f),
                            contentColor = if (isConnected) Color(0xFFFF0000) else matrixGreen
                        )
                    ) {
                        Text(
                            text = if (isConnected) "Отключить" else "Подключить",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                Divider(color = matrixGreen.copy(alpha = 0.3f))
                
                // Позиция overlay по X
                Text(
                    text = "Позиция X: ${(overlayX * 100).toInt()}%",
                    color = matrixGreen,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = overlayX,
                    onValueChange = { overlayX = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = matrixGreen,
                        activeTrackColor = matrixGreen.copy(alpha = 0.7f)
                    )
                )
                
                // Позиция overlay по Y
                Text(
                    text = "Позиция Y: ${(overlayY * 100).toInt()}%",
                    color = matrixGreen,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = overlayY,
                    onValueChange = { overlayY = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = matrixGreen,
                        activeTrackColor = matrixGreen.copy(alpha = 0.7f)
                    )
                )
                
                // Масштаб overlay
                Text(
                    text = "Масштаб: ${(overlayScale * 100).toInt()}%",
                    color = matrixGreen,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = overlayScale,
                    onValueChange = { overlayScale = it },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = matrixGreen,
                        activeTrackColor = matrixGreen.copy(alpha = 0.7f)
                    )
                )
                
                // Яркость
                Text(
                    text = "Яркость: ${(brightness * 100).toInt()}%",
                    color = matrixGreen,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = matrixGreen,
                        activeTrackColor = matrixGreen.copy(alpha = 0.7f)
                    )
                )
                
                // Инструкция
                Text(
                    text = "Настройте позицию, масштаб и яркость overlay для комфортного просмотра.",
                    color = matrixGreen.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        settingsManager.saveArSettings(
                            ArCalibrationSettings(
                                deviceType = deviceType,
                                overlayX = overlayX,
                                overlayY = overlayY,
                                overlayScale = overlayScale,
                                brightness = brightness,
                                isConnected = isConnected
                            )
                        )
                    }
                    onDismiss()
                }
            ) {
                Text(
                    text = "Сохранить",
                    color = matrixGreen,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Отмена",
                    color = matrixGreen.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        containerColor = Color.Black,
        textContentColor = matrixGreen
    )
}

