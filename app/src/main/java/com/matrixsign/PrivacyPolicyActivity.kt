package com.matrixsign

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrixsign.ui.theme.MatrixSignTheme

/**
 * Экран политики конфиденциальности
 * Полный текст политики, готовый к копированию
 */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MatrixSignTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    PrivacyPolicyScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    val matrixGreen = Color(0xFF00FF41)
    val matrixFontFamily = FontFamily.Monospace
    
    // Зелёный шум (градиент для эффекта Матрицы)
    val greenNoiseBrush = Brush.radialGradient(
        colors = listOf(
            Color.Black,
            Color(0xFF001100),
            Color.Black
        ),
        radius = 1000f
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(greenNoiseBrush)
    ) {
        // Заголовок с кнопкой "Назад"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "← Назад",
                    color = matrixGreen,
                    fontFamily = matrixFontFamily,
                    fontSize = 18.sp
                )
            }
            Text(
                text = "Политика конфиденциальности",
                color = matrixGreen,
                fontFamily = matrixFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(80.dp)) // Для центрирования
        }
        
        Divider(color = matrixGreen.copy(alpha = 0.3f))
        
        // Прокручиваемый текст политики
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "ПОЛИТИКА КОНФИДЕНЦИАЛЬНОСТИ",
                fontSize = 24.sp,
                color = matrixGreen,
                fontFamily = matrixFontFamily,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Приложение MatrixSign (версия 1.0 и выше)",
                fontSize = 18.sp,
                color = matrixGreen.copy(alpha = 0.8f),
                fontFamily = matrixFontFamily,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = """
                    1. Мы не собираем, не храним и не передаём ваши персональные данные.
                    
                    2. Все операции (распознавание жестов, речи, TTS) происходят исключительно на вашем устройстве.
                    
                    3. Видео и аудио удаляются сразу после обработки.
                    
                    4. Кастомные жесты хранятся только локально и могут быть удалены вами в любой момент.
                    
                    5. Мы не используем аналитику, рекламу и трекеры.
                    
                    6. Разработчик: MatrixSign Team
                       Контакты для запросов на удаление: support@matrixsign.app
                    
                    По вопросам обработки данных пишите: support@matrixsign.app
                """.trimIndent(),
                fontSize = 20.sp,
                color = matrixGreen,
                fontFamily = matrixFontFamily,
                fontWeight = FontWeight.Medium,
                lineHeight = 28.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = """
                    ДОПОЛНИТЕЛЬНАЯ ИНФОРМАЦИЯ
                    
                    • Все модели машинного обучения (MediaPipe, Silero TTS) работают полностью оффлайн
                    
                    • Данные камеры и микрофона обрабатываются только в оперативной памяти
                    
                    • Никакие данные не сохраняются на диск, кроме ваших кастомных жестов (по вашему выбору)
                    
                    • При удалении приложения все данные удаляются автоматически
                    
                    • Вы можете отозвать согласие в любой момент через настройки приложения
                """.trimIndent(),
                fontSize = 18.sp,
                color = matrixGreen.copy(alpha = 0.9f),
                fontFamily = matrixFontFamily,
                fontWeight = FontWeight.Normal,
                lineHeight = 26.sp
            )
        }
    }
}

