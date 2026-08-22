package com.matrixsign

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper class to smooth out gesture recognition results.
 * It uses a window of recent predictions to filter out noise and jitter.
 */
class GestureSmoother(
    private val windowSize: Int = 10,
    private val threshold: Float = 0.7f,
    private val cooldownMs: Long = 1000
) {
    // Current window of gestures
    private val window = ArrayDeque<String>(windowSize)
    
    // Timestamp of the last confirmed gesture to prevent spam
    private var lastGestureTime = 0L
    private var lastConfirmedGesture: String? = null

    /**
     * Add a new prediction to the smoother.
     * @param gesture The raw gesture label from the classifier.
     * @return The smoothed gesture if it meets the criteria, or null.
     */
    fun process(gesture: String?): String? {
        // If null (no gesture), just add null or empty to window to degrade potential matches
        if (gesture == null) {
            addToWindow(null)
            return null
        }

        addToWindow(gesture)

        // Count occurrences in the window
        val counts = window.groupingBy { it }.eachCount()
        val mostFrequent = counts.maxByOrNull { it.value }

        if (mostFrequent != null) {
            val (candidate, count) = mostFrequent
            // Note: candidate in the map keys will be null if we added nulls to deque? 
            // Actually ArrayDeque in Kotlin doesn't allow random nulls easily if type is String, 
            // but we defined Deque<String>. Let's handle "no gesture" as skipping or special value if needed.
            // For simplicity, let's say "null" input means "noise" or "nothing".
            
            val fraction = count.toFloat() / window.size
            
            if (fraction >= threshold) {
                if (candidate == "Unknown") return null

                val currentTime = SystemClock.elapsedRealtime()
                
                // Check cooldown (unless it's a "continuous" gesture, but for typing usually we want single trigger)
                // If it is the SAME gesture as last time, we check cooldown.
                // If it is a DIFFERENT gesture, we can trigger immediately (and reset time).
                
                if (candidate == lastConfirmedGesture) {
                    if (currentTime - lastGestureTime > cooldownMs) {
                        lastGestureTime = currentTime
                        return candidate
                    }
                } else {
                    lastConfirmedGesture = candidate
                    lastGestureTime = currentTime
                    return candidate
                }
            }
        }
        
        return null
    }

    private fun addToWindow(item: String?) {
        if (window.size >= windowSize) {
            window.removeFirst()
        }
        // treat null as "Unknown" to dilute the buffer
        window.addLast(item ?: "Unknown")
    }
    
    fun reset() {
        window.clear()
        lastConfirmedGesture = null
        lastGestureTime = 0
    }
}
