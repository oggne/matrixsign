package com.matrixsign.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList

/**
 * Утилиты для отрисовки landmarks MediaPipe в стиле Матрица
 * Основано на официальных примерах MediaPipe
 */
object DrawingUtils {
    
    // Цвет для landmarks в стиле Матрица
    private const val MATRIX_GREEN = 0xFF00FF00.toInt()
    private const val MATRIX_GREEN_ALPHA = 0xFF00FF40.toInt()
    
    // Толщина линий
    private const val CONNECTION_THICKNESS = 3.0f
    private const val LANDMARK_THICKNESS = 5.0f
    
    /**
     * Соединения для руки (21 точка)
     * Источник: https://github.com/google-ai-edge/mediapipe-samples
     */
    val HAND_CONNECTIONS = listOf(
        // Wrist
        0 to 1, 0 to 5, 0 to 9, 0 to 13, 0 to 17,
        // Thumb
        1 to 2, 2 to 3, 3 to 4,
        // Index finger
        5 to 6, 6 to 7, 7 to 8,
        // Middle finger
        9 to 10, 10 to 11, 11 to 12,
        // Ring finger
        13 to 14, 14 to 15, 15 to 16,
        // Pinky
        17 to 18, 18 to 19, 19 to 20,
        // Palm
        1 to 5, 5 to 9, 9 to 13, 13 to 17
    )
    
    /**
     * Отрисовка landmarks руки на Canvas
     */
    fun drawHandLandmarks(
        canvas: Canvas,
        landmarkList: NormalizedLandmarkList,
        imageWidth: Int,
        imageHeight: Int,
        connectionColor: Int = MATRIX_GREEN,
        landmarkColor: Int = MATRIX_GREEN_ALPHA
    ) {
        if (landmarkList.landmarkCount == 0) return
        
        val connectionPaint = Paint().apply {
            color = connectionColor
            strokeWidth = CONNECTION_THICKNESS
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        val landmarkPaint = Paint().apply {
            color = landmarkColor
            strokeWidth = LANDMARK_THICKNESS
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // Отрисовка соединений
        for ((startIdx, endIdx) in HAND_CONNECTIONS) {
            if (startIdx < landmarkList.landmarkCount && endIdx < landmarkList.landmarkCount) {
                val start = landmarkList.getLandmark(startIdx)
                val end = landmarkList.getLandmark(endIdx)
                
                val startPoint = PointF(
                    start.x * imageWidth,
                    start.y * imageHeight
                )
                val endPoint = PointF(
                    end.x * imageWidth,
                    end.y * imageHeight
                )
                
                canvas.drawLine(
                    startPoint.x,
                    startPoint.y,
                    endPoint.x,
                    endPoint.y,
                    connectionPaint
                )
            }
        }
        
        // Отрисовка точек
        for (i in 0 until landmarkList.landmarkCount) {
            val landmark = landmarkList.getLandmark(i)
            val point = PointF(
                landmark.x * imageWidth,
                landmark.y * imageHeight
            )
            
            canvas.drawCircle(point.x, point.y, LANDMARK_THICKNESS, landmarkPaint)
        }
    }
    
    /**
     * Отрисовка landmarks руки на Canvas с масштабированием
     */
    fun drawHandLandmarksScaled(
        canvas: Canvas,
        landmarkList: NormalizedLandmarkList,
        imageWidth: Int,
        imageHeight: Int,
        scaleX: Float,
        scaleY: Float,
        connectionColor: Int = MATRIX_GREEN,
        landmarkColor: Int = MATRIX_GREEN_ALPHA
    ) {
        if (landmarkList.landmarkCount == 0) return
        
        val connectionPaint = Paint().apply {
            color = connectionColor
            strokeWidth = CONNECTION_THICKNESS * scaleX.coerceAtMost(scaleY)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        val landmarkPaint = Paint().apply {
            color = landmarkColor
            strokeWidth = LANDMARK_THICKNESS * scaleX.coerceAtMost(scaleY)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // Отрисовка соединений
        for ((startIdx, endIdx) in HAND_CONNECTIONS) {
            if (startIdx < landmarkList.landmarkCount && endIdx < landmarkList.landmarkCount) {
                val start = landmarkList.getLandmark(startIdx)
                val end = landmarkList.getLandmark(endIdx)
                
                val startPoint = PointF(
                    start.x * imageWidth * scaleX,
                    start.y * imageHeight * scaleY
                )
                val endPoint = PointF(
                    end.x * imageWidth * scaleX,
                    end.y * imageHeight * scaleY
                )
                
                canvas.drawLine(
                    startPoint.x,
                    startPoint.y,
                    endPoint.x,
                    endPoint.y,
                    connectionPaint
                )
            }
        }
        
        // Отрисовка точек
        for (i in 0 until landmarkList.landmarkCount) {
            val landmark = landmarkList.getLandmark(i)
            val point = PointF(
                landmark.x * imageWidth * scaleX,
                landmark.y * imageHeight * scaleY
            )
            
            val radius = LANDMARK_THICKNESS * scaleX.coerceAtMost(scaleY)
            canvas.drawCircle(point.x, point.y, radius, landmarkPaint)
        }
    }
    
    /**
     * Отрисовка landmarks руки на Canvas с прямым преобразованием нормализованных координат
     * Landmarks приходят в нормализованных координатах (0-1), преобразуем их напрямую в пиксели Canvas
     */
    fun drawHandLandmarksOnCanvas(
        canvas: Canvas,
        landmarkList: NormalizedLandmarkList,
        canvasWidth: Float,
        canvasHeight: Float,
        connectionColor: Int = MATRIX_GREEN,
        landmarkColor: Int = MATRIX_GREEN_ALPHA
    ) {
        if (landmarkList.landmarkCount == 0) return
        
        // Адаптивная толщина линий в зависимости от размера экрана
        val baseThickness = (canvasWidth / 360f).coerceIn(2f, 8f) // От 2 до 8 пикселей
        val connectionThickness = baseThickness
        val landmarkRadius = baseThickness * 1.5f
        
        val connectionPaint = Paint().apply {
            color = connectionColor
            strokeWidth = connectionThickness
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        
        val landmarkPaint = Paint().apply {
            color = landmarkColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // Отрисовка соединений (линий между точками)
        for ((startIdx, endIdx) in HAND_CONNECTIONS) {
            if (startIdx < landmarkList.landmarkCount && endIdx < landmarkList.landmarkCount) {
                val start = landmarkList.getLandmark(startIdx)
                val end = landmarkList.getLandmark(endIdx)
                
                // Прямое преобразование нормализованных координат (0-1) в пиксели Canvas
                val startPoint = PointF(
                    start.x * canvasWidth,
                    start.y * canvasHeight
                )
                val endPoint = PointF(
                    end.x * canvasWidth,
                    end.y * canvasHeight
                )
                
                canvas.drawLine(
                    startPoint.x,
                    startPoint.y,
                    endPoint.x,
                    endPoint.y,
                    connectionPaint
                )
            }
        }
        
        // Отрисовка точек (21 точка суставов)
        for (i in 0 until landmarkList.landmarkCount) {
            val landmark = landmarkList.getLandmark(i)
            val point = PointF(
                landmark.x * canvasWidth,
                landmark.y * canvasHeight
            )
            
            canvas.drawCircle(point.x, point.y, landmarkRadius, landmarkPaint)
        }
    }
    
    /**
     * Отрисовка landmarks лица (для мимики)
     */
    fun drawFaceLandmarks(
        canvas: Canvas,
        landmarkList: NormalizedLandmarkList,
        imageWidth: Int,
        imageHeight: Int,
        color: Int = MATRIX_GREEN
    ) {
        if (landmarkList.landmarkCount == 0) return
        
        val paint = Paint().apply {
            this.color = color
            strokeWidth = 2.0f
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        for (i in 0 until landmarkList.landmarkCount) {
            val landmark = landmarkList.getLandmark(i)
            val point = PointF(
                landmark.x * imageWidth,
                landmark.y * imageHeight
            )
            canvas.drawCircle(point.x, point.y, 3.0f, paint)
        }
    }
    
    /**
     * Конвертация NormalizedLandmark в PointF
     */
    fun normalizedLandmarkToPoint(
        landmark: NormalizedLandmark,
        imageWidth: Int,
        imageHeight: Int
    ): PointF {
        return PointF(
            landmark.x * imageWidth,
            landmark.y * imageHeight
        )
    }
}