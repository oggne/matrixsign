package com.matrixsign

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Экран настроек языков
 * Позволяет выбрать:
 * - Язык оппонента (речь): русский, английский, украинский
 * - Язык жестов пользователя: РЖЯ, ASL, УЖЯ
 */
class LanguageSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LanguageSettingsScreen(
                onBack = { finish() }
            )
        }
    }
}

@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val languageManager = remember { LanguageManager(context) }
    val scope = rememberCoroutineScope()
    
    // Состояния для выбранных языков
    var selectedOpponentLanguage by remember { mutableStateOf<String?>(null) }
    var selectedUserSignLanguage by remember { mutableStateOf<String?>(null) }
    
    // Загружаем текущие настройки
    LaunchedEffect(Unit) {
        selectedOpponentLanguage = languageManager.opponentLanguage.first()
        selectedUserSignLanguage = languageManager.userSignLanguage.first()
    }
    
    val matrixGreen = Color(0xFF00FF41)
    val matrixFontFamily = FontFamily.Monospace
    
    // Списки языков
    val opponentLanguages = listOf(
        LanguageItem(LanguageManager.OPPONENT_LANGUAGE_RU, "Русский", "🇷🇺"),
        LanguageItem(LanguageManager.OPPONENT_LANGUAGE_EN, "English", "🇬🇧"),
        LanguageItem(LanguageManager.OPPONENT_LANGUAGE_UK, "Українська", "🇺🇦")
    )
    
    val signLanguages = listOf(
        LanguageItem(LanguageManager.USER_SIGN_LANGUAGE_RSL, "РЖЯ (Русский жестовый)", "✋"),
        LanguageItem(LanguageManager.USER_SIGN_LANGUAGE_ASL, "ASL (American Sign Language)", "✋"),
        LanguageItem(LanguageManager.USER_SIGN_LANGUAGE_USL, "УЖЯ (Українська жестова)", "✋")
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Заголовок
            Text(
                text = "Настройки языков",
                color = matrixGreen,
                fontSize = 28.sp,
                fontFamily = matrixFontFamily,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Язык оппонента (речь)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Язык оппонента (речь)",
                        color = matrixGreen,
                        fontSize = 18.sp,
                        fontFamily = matrixFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Выберите язык, на котором говорит ваш собеседник",
                        color = Color(0xFF00FF40).copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontFamily = matrixFontFamily
                    )
                    
                    opponentLanguages.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOpponentLanguage == language.code,
                                onClick = {
                                    selectedOpponentLanguage = language.code
                                    scope.launch {
                                        languageManager.setOpponentLanguage(language.code)
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = matrixGreen,
                                    unselectedColor = Color(0xFF00FF40).copy(alpha = 0.5f)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = language.emoji,
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = language.name,
                                color = if (selectedOpponentLanguage == language.code) {
                                    matrixGreen
                                } else {
                                    Color(0xFF00FF40).copy(alpha = 0.8f)
                                },
                                fontSize = 16.sp,
                                fontFamily = matrixFontFamily
                            )
                        }
                    }
                }
            }
            
            // Язык жестов пользователя
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Мой язык жестов",
                        color = matrixGreen,
                        fontSize = 18.sp,
                        fontFamily = matrixFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Выберите язык жестов, который вы используете",
                        color = Color(0xFF00FF40).copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontFamily = matrixFontFamily
                    )
                    
                    signLanguages.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedUserSignLanguage == language.code,
                                onClick = {
                                    selectedUserSignLanguage = language.code
                                    scope.launch {
                                        languageManager.setUserSignLanguage(language.code)
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = matrixGreen,
                                    unselectedColor = Color(0xFF00FF40).copy(alpha = 0.5f)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = language.emoji,
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = language.name,
                                color = if (selectedUserSignLanguage == language.code) {
                                    matrixGreen
                                } else {
                                    Color(0xFF00FF40).copy(alpha = 0.8f)
                                },
                                fontSize = 16.sp,
                                fontFamily = matrixFontFamily
                            )
                        }
                    }
                }
            }
            
            // Информация о переводе
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF00FF41).copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ℹ️ О переводе",
                        color = matrixGreen,
                        fontSize = 16.sp,
                        fontFamily = matrixFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Приложение автоматически переводит речь оппонента в ваш язык жестов и наоборот. Все переводы выполняются оффлайн на устройстве.",
                        color = Color(0xFF00FF40).copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontFamily = matrixFontFamily,
                        lineHeight = 20.sp
                    )
                }
            }
            
            // Кнопка "Назад"
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = matrixGreen,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Назад",
                    fontSize = 18.sp,
                    fontFamily = matrixFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Data class для элемента языка
 */
data class LanguageItem(
    val code: String,
    val name: String,
    val emoji: String
)




