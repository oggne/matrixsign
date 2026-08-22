package com.matrixsign

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrixsign.ui.theme.MatrixSignTheme
import kotlin.system.exitProcess

/**
 * Экран согласия на обработку биометрических данных
 * Показывается при первом запуске и после отзыва согласия
 * Соответствует требованиям 152-ФЗ и GDPR
 */
class OnboardingConsentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Проверяем, дано ли уже согласие
        val consentManager = ConsentManager(this)
        if (consentManager.hasConsent()) {
            // Если согласие уже дано, сразу переходим в MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        setContent {
            MatrixSignTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    ConsentScreen(
                        onConsentGiven = {
                            consentManager.saveConsent()
                            
                            // Переход в MainActivity
                            val intent = Intent(this, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        },
                        onDecline = {
                            // Закрываем приложение при отказе
                            finishAffinity()
                            exitProcess(0)
                        },
                        onPrivacyPolicyClick = {
                            val intent = Intent(this, PrivacyPolicyActivity::class.java)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConsentScreen(
    onConsentGiven: () -> Unit,
    onDecline: () -> Unit,
    onPrivacyPolicyClick: () -> Unit
) {
    var isConsentChecked by remember { mutableStateOf(false) }
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
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(greenNoiseBrush)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Заголовок
            Text(
                text = "Согласие\nна обработку биометрических данных",
                fontSize = 28.sp,
                color = matrixGreen,
                fontFamily = matrixFontFamily,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Основной текст
            Text(
                text = """
                    Приложение MatrixSign обрабатывает:

                    • Видео с ваших рук и лица (для распознавания жестов)
                    • Голос собеседника (для преобразования речи в текст)

                    Всё происходит ЛОКАЛЬНО на вашем устройстве, ничего не передаётся в интернет и третьим лицам.

                    Видео и звук диалога НЕ сохраняются — они используются только в моменте, для перевода жестов в текст и речи в текст.

                    Если вы создаёте СВОИ жесты (раздел «Обучение жестам»), их landmark-данные и название сохраняются локально на устройстве — чтобы приложение могло их распознавать в дальнейшем. Эти данные хранятся, пока вы их не удалите или не отзовёте согласие.

                    Отозвать согласие можно в любой момент в настройках — это удалит все сохранённые на устройстве жесты и настройки.

                    Обработка биометрических персональных данных осуществляется в соответствии со ст. 11 Федерального закона № 152-ФЗ и ст. 9 GDPR.
                """.trimIndent(),
                fontSize = 20.sp,
                color = matrixGreen,
                fontFamily = matrixFontFamily,
                fontWeight = FontWeight.Medium,
                lineHeight = 28.sp,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Чекбокс согласия
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isConsentChecked = !isConsentChecked }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Checkbox(
                    checked = isConsentChecked,
                    onCheckedChange = { isConsentChecked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = matrixGreen,
                        uncheckedColor = matrixGreen.copy(alpha = 0.7f),
                        checkmarkColor = Color.Black
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Я даю согласие на обработку биометрических персональных данных в указанных выше целях",
                    fontSize = 18.sp,
                    color = matrixGreen,
                    fontFamily = matrixFontFamily,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Кнопка "Политика конфиденциальности"
            OutlinedButton(
                onClick = onPrivacyPolicyClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = matrixGreen
                ),
                border = BorderStroke(2.dp, matrixGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Политика конфиденциальности",
                    fontSize = 18.sp,
                    fontFamily = matrixFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Кнопка "Продолжить"
            Button(
                onClick = onConsentGiven,
                enabled = isConsentChecked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .then(
                        if (!isConsentChecked) {
                            Modifier.border(2.dp, matrixGreen, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConsentChecked) matrixGreen else Color.Transparent,
                    contentColor = if (isConsentChecked) Color.Black else matrixGreen.copy(alpha = 0.5f),
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = matrixGreen.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Продолжить",
                    fontSize = 20.sp,
                    fontFamily = matrixFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Кнопка "Отказаться"
            TextButton(
                onClick = onDecline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Отказаться",
                    fontSize = 16.sp,
                    color = matrixGreen.copy(alpha = 0.7f),
                    fontFamily = matrixFontFamily
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

