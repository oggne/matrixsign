package com.matrixsign

import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Класс для вычисления Log-Mel Spectrogram, необходимой для модели Whisper.
 * Реализует упрощенный FFT и Mel Filterbank.
 */
class MelSpectrogram {
    companion object {
        const val SAMPLE_RATE = 16000
        const val N_FFT = 400
        const val HOP_LENGTH = 160
        const val N_MELS = 80
        const val CHUNK_LENGTH = 30 // seconds
        const val N_SAMPLES = CHUNK_LENGTH * SAMPLE_RATE
        const val N_FRAMES = N_SAMPLES / HOP_LENGTH
    }

    private val melFilters: Array<FloatArray> = createMelFilters()
    private val window: FloatArray = createHannWindow()

    fun process(samples: FloatArray): FloatArray {
        // 1. Pad or trim to 30 seconds
        val paddedSamples = if (samples.size < N_SAMPLES) {
            samples + FloatArray(N_SAMPLES - samples.size)
        } else {
            samples.copyOf(N_SAMPLES)
        }

        // 2. Compute STFT -> Mel Spectrogram
        val melSpec = FloatArray(N_MELS * N_FRAMES)
        
        for (i in 0 until N_FRAMES) {
            val start = i * HOP_LENGTH
            val frame = FloatArray(N_FFT)
            
            // Extract frame and apply window
            for (j in 0 until N_FFT) {
                if (start + j < N_SAMPLES) {
                    frame[j] = paddedSamples[start + j] * window[j]
                }
            }
            
            // FFT
            val fftMagnitude = computeFFTMagnitude(frame)
            
            // Apply Mel Filters
            for (j in 0 until N_MELS) {
                var sum = 0f
                for (k in 0 until N_FFT / 2 + 1) {
                    sum += fftMagnitude[k] * melFilters[j][k]
                }
                // Logarithm (log10(max(sum, 1e-10))) -> then scale if needed
                // Whisper uses log10(x + 1e-8) usually, or similar.
                // Standard: log10(max(val, 1e-10)) * 10 (dB) - but Whisper expects specific scaling.
                // Simplified: log10(sum + 1e-9)
                melSpec[j * N_FRAMES + i] = log10(sum + 1e-9f)
            }
        }
        
        return melSpec
    }

    private fun createHannWindow(): FloatArray {
        val window = FloatArray(N_FFT)
        for (i in 0 until N_FFT) {
            window[i] = (0.5 * (1 - cos(2 * PI * i / N_FFT))).toFloat()
        }
        return window
    }

    private fun computeFFTMagnitude(input: FloatArray): FloatArray {
        // Simple DFT implementation (slow but works without external libs)
        // For production, use JTransforms or similar.
        // Optimization: Real-valued FFT
        val n = input.size
        val output = FloatArray(n / 2 + 1)
        
        // Precompute sine/cosine tables could optimize this
        for (k in 0 until n / 2 + 1) {
            var real = 0f
            var imag = 0f
            for (t in 0 until n) {
                val angle = 2 * PI * t * k / n
                real += (input[t] * cos(angle)).toFloat()
                imag -= (input[t] * sin(angle)).toFloat()
            }
            output[k] = sqrt(real * real + imag * imag)
        }
        return output
    }

    private fun createMelFilters(): Array<FloatArray> {
        // Simplified Mel filterbank creation
        // Needs to match librosa.filters.mel(sr=16000, n_fft=400, n_mels=80)
        val nFft = N_FFT
        val nMels = N_MELS
        val sampleRate = SAMPLE_RATE
        
        val filters = Array(nMels) { FloatArray(nFft / 2 + 1) }
        
        val minMel = 0f
        val maxMel = 2595 * log10(1 + (sampleRate / 2) / 700f)
        
        val melPoints = FloatArray(nMels + 2)
        for (i in 0 until nMels + 2) {
            melPoints[i] = minMel + i * (maxMel - minMel) / (nMels + 1)
        }
        
        val hzPoints = FloatArray(nMels + 2)
        for (i in 0 until nMels + 2) {
            hzPoints[i] = (700 * (10.0.pow(melPoints[i] / 2595.0) - 1)).toFloat()
        }
        
        val binPoints = IntArray(nMels + 2)
        for (i in 0 until nMels + 2) {
            binPoints[i] = floor((nFft + 1) * hzPoints[i] / sampleRate).toInt()
        }
        
        for (i in 0 until nMels) {
            for (j in binPoints[i] until binPoints[i + 1]) {
                filters[i][j] = (j - binPoints[i]).toFloat() / (binPoints[i + 1] - binPoints[i])
            }
            for (j in binPoints[i + 1] until binPoints[i + 2]) {
                filters[i][j] = (binPoints[i + 2] - j).toFloat() / (binPoints[i + 2] - binPoints[i + 1])
            }
        }
        
        return filters
    }
}
