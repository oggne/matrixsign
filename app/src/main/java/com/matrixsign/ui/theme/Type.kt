package com.matrixsign.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Читаемый моноширинный шрифт для стиля Матрица
// Используем системный Monospace (Roboto Mono) - отлично читается и выглядит техно
@Composable
fun getMatrixFontFamily(): FontFamily {
    // Всегда используем системный Monospace для максимальной читаемости
    return FontFamily.Monospace
}

// Моноширинный шрифт для стиля Матрица
val MatrixFontFamily = FontFamily.Monospace

// Цвет Matrix (чуть мягче чистого зелёного для лучшей читаемости)
// Переименовано в MatrixGreenColor чтобы избежать конфликтов
val MatrixGreenColor = androidx.compose.ui.graphics.Color(0xFF00FF41)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp, // Размер для входящих/исходящих сообщений
        letterSpacing = 0.5.sp,
        color = MatrixGreenColor
    ),
    bodyMedium = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp, // Размер для T9-подсказок
        letterSpacing = 0.25.sp,
        color = MatrixGreenColor
    ),
    bodySmall = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp, // Размер для системных надписей
        letterSpacing = 0.1.sp,
        color = MatrixGreenColor
    ),
    labelMedium = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MatrixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)





