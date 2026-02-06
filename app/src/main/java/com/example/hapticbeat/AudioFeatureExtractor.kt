package com.example.hapticbeat

/*import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.sqrt

// Ensure this import is present:
// import com.example.hapticbeat.HapticMode

class AudioFeatureExtractor(
    private val audioSessionId: Int,
    private val currentHapticMode: HapticMode,
    private val onBeatDetected: (Float) -> Unit,
    private val onBassDetected: (Float) -> Unit,
    private val onMidRangeDetected: (Float) -> Unit,
    private val onTrebleDetected: (Float) -> Unit
) : Visualizer.OnDataCaptureListener {

    private var visualizer: Visualizer? = null
    private var isAnalyzing = false

    private var internalLastPeakAmplitude = 0f
    private var internalLastBeatTime = System.currentTimeMillis()

    fun startAnalysis() {
        if (isAnalyzing) {
            Log.d("AudioFeatureExtractor", "Analysis already running.")
            return
        }

        try {
            visualizer = Visualizer(audioSessionId)
            visualizer?.apply {
                captureSize = Constants.CAPTURE_SIZE

                // Determine if waveform and/or FFT data capture is needed based on the HapticMode
                val captureWaveform = when (currentHapticMode) {
                    HapticMode.ONLY_BEATS, HapticMode.BEATS_BASS, HapticMode.BEATS_HIGH_FREQUENCY_INSTRUMENTS -> true
                    HapticMode.MID_RANGE_VOCALS, HapticMode.ONLY_HIGH_FREQUENCY_INSTRUMENTS, HapticMode.NONE -> false
                }
                val captureFft = when (currentHapticMode) {
                    HapticMode.BEATS_BASS, HapticMode.MID_RANGE_VOCALS,
                    HapticMode.ONLY_HIGH_FREQUENCY_INSTRUMENTS, HapticMode.BEATS_HIGH_FREQUENCY_INSTRUMENTS -> true
                    HapticMode.ONLY_BEATS, HapticMode.NONE -> false
                }

               // setDataCaptureListener(this@AudioFeatureExtractor, Constants.VISUALIZER_RATE_HZ * 1000, captureWaveform, captureFft)
                enabled = true
            }
            isAnalyzing = true
            Log.d("AudioFeatureExtractor", "Visualizer started for session ID: $audioSessionId, Haptic Mode: $currentHapticMode")
        } catch (e: Exception) {
            Log.e("AudioFeatureExtractor", "Failed to start Visualizer with session ID $audioSessionId: ${e.message}", e)
        }
    }

    fun stopAnalysis() {
        if (!isAnalyzing) return

        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        isAnalyzing = false
        Log.d("AudioFeatureExtractor", "Visualizer stopped and released.")
    }

    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
        val shouldProcessWaveform = when (currentHapticMode) {
            HapticMode.ONLY_BEATS, HapticMode.BEATS_BASS, HapticMode.BEATS_HIGH_FREQUENCY_INSTRUMENTS -> true
            HapticMode.MID_RANGE_VOCALS, HapticMode.ONLY_HIGH_FREQUENCY_INSTRUMENTS, HapticMode.NONE -> false
        }

        if (waveform == null || !shouldProcessWaveform) {
             Log.d("AudioFeatureExtractor", "Waveform data not processed (null or not needed for mode: $currentHapticMode)")
            return
        }
        Log.d("AudioFeatureExtractor", "Waveform data captured! Size: ${waveform.size}, Sampling Rate: $samplingRate")

        val rms = calculateRms(waveform)
        val currentAmplitude = rms / 128f

        val currentTime = System.currentTimeMillis()

        if (currentAmplitude > Constants.BEAT_THRESHOLD &&
            currentAmplitude > internalLastPeakAmplitude * 1.1 &&
            (currentTime - internalLastBeatTime) > Constants.MIN_BEAT_INTERVAL_MS) {
            onBeatDetected(currentAmplitude)
        }
        internalLastPeakAmplitude = currentAmplitude
    }

    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
        val shouldProcessFft = when (currentHapticMode) {
            HapticMode.BEATS_BASS, HapticMode.MID_RANGE_VOCALS,
            HapticMode.ONLY_HIGH_FREQUENCY_INSTRUMENTS, HapticMode.BEATS_HIGH_FREQUENCY_INSTRUMENTS -> true
            HapticMode.ONLY_BEATS, HapticMode.NONE -> false
        }

        if (fft == null || !shouldProcessFft) {
            // Log.d("AudioFeatureExtractor", "FFT data not processed (null or not needed for mode: $currentHapticMode)")
            return
        }
        Log.d("AudioFeatureExtractor", "FFT data captured! Size: ${fft.size}, Sampling Rate: $samplingRate")

        val numBins = Constants.CAPTURE_SIZE / 2 + 1
        val freqStep = samplingRate.toFloat() / Constants.CAPTURE_SIZE

        var bassMagnitudeSum = 0f
        var midMagnitudeSum = 0f
        var trebleMagnitudeSum = 0f

        var bassBinCount = 0
        var midBinCount = 0
        var trebleBinCount = 0

        for (i in 1 until numBins) {
            val real = fft[i * 2].toInt().toByte().toFloat()
            val imag = fft[i * 2 + 1].toInt().toByte().toFloat()

            val magnitude = sqrt((real * real + imag * imag).toDouble()).toFloat()
            val normalizedMagnitude = (magnitude / 180f).coerceIn(0f, 1f)

            val freq = i * freqStep

            when (currentHapticMode) {
                HapticMode.BEATS_BASS -> {
                    if (freq < Constants.FREQ_RANGE_BASS_MAX) {
                        bassMagnitudeSum += normalizedMagnitude
                        bassBinCount++
                    }
                    if (freq >= Constants.FREQ_RANGE_TREBLE_MIN) {
                        trebleMagnitudeSum += normalizedMagnitude
                        trebleBinCount++
                    }
                }
                HapticMode.MID_RANGE_VOCALS -> {
                    if (freq >= Constants.FREQ_RANGE_MID_MIN && freq < Constants.FREQ_RANGE_MID_MAX) {
                        midMagnitudeSum += normalizedMagnitude
                        midBinCount++
                    }
                }
                HapticMode.ONLY_HIGH_FREQUENCY_INSTRUMENTS -> {
                    if (freq >= Constants.FREQ_RANGE_TREBLE_MIN) {
                        trebleMagnitudeSum += normalizedMagnitude
                        trebleBinCount++
                    }
                }
                HapticMode.BEATS_HIGH_FREQUENCY_INSTRUMENTS -> {
                    if (freq >= Constants.FREQ_RANGE_TREBLE_MIN) {
                        trebleMagnitudeSum += normalizedMagnitude
                        trebleBinCount++
                    }
                }
                else -> { /* Do nothing for modes that don't need specific FFT analysis results */ }
            }
        }

        when (currentHapticMode) {
            HapticMode.BEATS_BASS -> {
                val bassIntensity = if (bassBinCount > 0) bassMagnitudeSum / bassBinCount else 0f
                onBassDetected(bassIntensity.coerceIn(0f, 1f))
                val trebleIntensity = if (trebleBinCount > 0) trebleMagnitudeSum / trebleBinCount else 0f
                onTrebleDetected(trebleIntensity.coerceIn(0f, 1f))
            }
            HapticMode.MID_RANGE_VOCALS -> {
                val midIntensity = if (midBinCount > 0) midMagnitudeSum / midBinCount else 0f
                onMidRangeDetected(midIntensity.coerceIn(0f, 1f))
            }
            HapticMode.ONLY_HIGH_FREQUENCY_INSTRUMENTS, HapticMode.BEATS_HIGH_FREQUENCY_INSTRUMENTS -> {
                val trebleIntensity = if (trebleBinCount > 0) trebleMagnitudeSum / trebleBinCount else 0f
                onTrebleDetected(trebleIntensity.coerceIn(0f, 1f))
            }
            else -> { /* Do nothing for other modes */ }
        }
    }

    private fun calculateRms(waveform: ByteArray): Float {
        var sumSq = 0.0
        for (b in waveform) {
            val value = b.toInt() - 128
            sumSq += (value * value).toDouble()
        }
        return sqrt(sumSq / waveform.size).toFloat()
    }
}*/