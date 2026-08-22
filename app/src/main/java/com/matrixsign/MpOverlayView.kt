package com.matrixsign

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList
import com.matrixsign.utils.DrawingUtils

/**
 * Compose-компонент для отрисовки MediaPipe landmarks поверх камеры
 * В стиле Матрица (зелёные точки и линии)
 */
@Composable
fun MpOverlayView(
    landmarks: List<NormalizedLandmarkList>,
    modifier: Modifier = Modifier,
    imageWidth: Int = 1080,
    imageHeight: Int = 1920
) {
    Canvas(
        modifier = modifier.fillMaxSize()
    ) { 
        val matrixGreen = Color(0xFF00FF00)
        val matrixGreenAlpha = Color(0xFF00FF40)

        drawIntoCanvas { canvas ->
            val androidCanvas = canvas.nativeCanvas
            // Получаем размеры Canvas (экрана)
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // ВАЖНО: Landmarks приходят в нормализованных координатах (0-1)
            // Нужно напрямую преобразовать их в пиксели экрана
            // НЕ используем imageWidth/imageHeight для масштабирования, так как
            // координаты уже нормализованы относительно размера изображения MediaPipe
            
            // Отрисовка landmarks для каждой руки
            landmarks.forEach { landmarkList ->
                if (landmarkList.landmarkCount > 0) {
                    // Используем DrawingUtils для отрисовки с прямым преобразованием координат
                    DrawingUtils.drawHandLandmarksOnCanvas(
                        androidCanvas,
                        landmarkList,
                        canvasWidth,
                        canvasHeight,
                        matrixGreen.toArgb(),
                        matrixGreenAlpha.toArgb()
                    )
                }
            }
        }
    }
}

/**
 * Android View версия для использования с PreviewView
 */
class MpOverlayAndroidView(context: android.content.Context) : View(context) {
    
    private var landmarks: List<NormalizedLandmarkList> = emptyList()
    private var imageWidth: Int = 1080
    private var imageHeight: Int = 1920
    
    private val matrixGreen = android.graphics.Color.parseColor("#00FF00")
    private val matrixGreenAlpha = android.graphics.Color.parseColor("#00FF40")
    
    fun updateLandmarks(newLandmarks: List<NormalizedLandmarkList>) {
        landmarks = newLandmarks
        invalidate()
    }
    
    fun updateImageSize(width: Int, height: Int) {
        imageWidth = width
        imageHeight = height
    }
    
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        
        landmarks.forEach { landmarkList ->
            if (landmarkList.landmarkCount > 0) {
                DrawingUtils.drawHandLandmarks(
                    canvas,
                    landmarkList,
                    imageWidth,
                    imageHeight,
                    matrixGreen,
                    matrixGreenAlpha
                )
            }
        }
    }
}
