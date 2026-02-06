package com.example.hapticbeat

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.util.Log
import android.os.Build
import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import org.jtransforms.fft.FloatFFT_1D
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.abs

class AudioAnalyzer(
    private val context: Context,
    private val mediaProjection: MediaProjection?,
    private val onBeatDetected: (Float) -> Unit,
    private val onBassDetected: ((Float) -> Unit)? = null,
    private val onMidRangeDetected: ((Float) -> Unit)? = null,
    private val onTrebleDetected: ((Float) -> Unit)? = null,
    private val hapticMode: HapticMode,
    private val isMicInputEnabled: Boolean,
    private val isFFTEnabled: Boolean, // This is now always true, but parameter is kept for consistency
    private val preferredBufferSizeSamples: Int = 1024 // Default to 1024 samples, common for lower latency
) {

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var audioProcessingThread: Thread? = null

    // Audio recording parameters
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    // FFT parameters
    private val FFT_SIZE = 1024 // Must be a power of 2
    private val fft = FloatFFT_1D(FFT_SIZE.toLong())
    private val spectrumBuffer = FloatArray(FFT_SIZE * 2) // For real and imaginary parts of FFT

    // Beat detection parameters
    private var generalBeatThreshold = 0.005f
    private val BASS_BEAT_THRESHOLD = 0.90f // CHANGED: Increased from 0.15f to 0.25f for more accuracy

    private var lastBeatTime = 0L // Timestamp of the last detected beat
    private val MIN_BEAT_INTERVAL_MS = 200L // CHANGED: Increased from 100L to 200L to prevent rapid false positives
    private val BEAT_ENERGY_SCALE_FACTOR = 100.0f // Factor to scale raw energy to haptic intensity

    // Intensity scaling for haptics (0.0 to 1.0)
    private val MAX_INTENSITY_LEVEL = 500.0f // Increased from 5.0f to 500.0f for better dynamic range
    private val BASS_FREQ_RANGE = 20f to 250f // Hz
    private val MID_FREQ_RANGE = 251f to 4000f // Hz
    private val TREBLE_FREQ_RANGE = 4001f to 20000f // Hz

    init {
        Log.d(TAG, "AudioAnalyzer initialized. Mic Input: $isMicInputEnabled, FFT Enabled: $isFFTEnabled, Preferred Buffer Size: $preferredBufferSizeSamples samples")
    }

    fun startRecording() {
        if (isRecording.get()) {
            Log.w(TAG, "Audio recording already in progress.")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val actualBufferSizeSamples = max(minBufferSize / 2, preferredBufferSizeSamples)
        val bufferSizeInBytes = actualBufferSizeSamples * 2

        if (bufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE || bufferSizeInBytes == AudioRecord.ERROR) {
            Log.e(TAG, "AudioRecord.getMinBufferSize returned an error: $bufferSizeInBytes")
            return
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val audioFormatObj = AudioFormat.Builder()
            .setEncoding(AUDIO_FORMAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .build()


        audioRecord = if (isMicInputEnabled) {
            Log.d(TAG, "Attempting to initialize AudioRecord with MIC input.")
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeInBytes
            )
        } else {
            Log.d(TAG, "Attempting to initialize AudioRecord with MediaProjection input (internal audio).")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (mediaProjection == null) {
                    Log.e(TAG, "MediaProjection is null. Cannot record internal audio.")
                    return
                }
                val audioPlaybackCaptureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(audioPlaybackCaptureConfig)
                    .setAudioFormat(audioFormatObj)
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .build()
            } else {
                Log.e(TAG, "Internal audio capture requires Android 10 (API 29) or higher.")
                return
            }
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed. State: ${audioRecord?.state}")
            audioRecord?.release()
            audioRecord = null
            return
        }

        try {
            audioRecord?.startRecording()
            isRecording.set(true)
            Log.d(TAG, "AudioRecord started recording with buffer size: $bufferSizeInBytes bytes (${actualBufferSizeSamples} samples)")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}")
            isRecording.set(false)
            audioRecord?.release()
            audioRecord = null
            return
        }

        audioProcessingThread = Thread {
            val audioBuffer = ShortArray(actualBufferSizeSamples)
            while (isRecording.get()) {
                val shortsRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (shortsRead > 0) {
                    val maxAbsValue = audioBuffer.maxOfOrNull { abs(it.toInt()) } ?: 0
                    Log.d(TAG, "Raw Audio Buffer Peak (0-32767): $maxAbsValue")
                    processAudioData(audioBuffer, shortsRead)
                } else if (shortsRead == AudioRecord.ERROR_DEAD_OBJECT) {
                    Log.w(TAG, "AudioRecord dead object. Attempting to restore or stopping.")
                    stopRecording()
                    break
                } else if (shortsRead < 0) {
                    Log.w(TAG, "AudioRecord read returned $shortsRead bytes. Stopping analysis.")
                    if (shortsRead == AudioRecord.ERROR_INVALID_OPERATION || shortsRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "Critical AudioRecord error: $shortsRead. Stopping analysis.")
                        stopRecording()
                        break
                    }
                } else if (shortsRead == 0) {
                    Log.w(TAG, "AudioRecord read returned 0 bytes. No audio data available.")
                }
            }
            Log.d(TAG, "Audio processing thread stopped.")
        }
        audioProcessingThread?.start()
    }

    private fun processAudioData(audioBuffer: ShortArray, shortsRead: Int) {
        for (i in 0 until FFT_SIZE) {
            spectrumBuffer[i] = if (i < shortsRead) audioBuffer[i].toFloat() / 32768.0f else 0.0f
        }

        if (isFFTEnabled) {
            fft.realForward(spectrumBuffer)
            val (bassIntensity, midIntensity, trebleIntensity) = analyzeFFT(spectrumBuffer) // Get intensities from FFT
            // Pass bassIntensity to detectBeat
            detectBeat(audioBuffer, shortsRead, bassIntensity)

            // Trigger haptics based on selected mode for frequency bands
            when (hapticMode) {
                HapticMode.BEATS_BASS -> onBassDetected?.invoke(bassIntensity)
                HapticMode.MID_RANGE_VOCALS -> onMidRangeDetected?.invoke(midIntensity)
                HapticMode.ONLY_HIGH_FREQUENCY_INSTRUMENTS, HapticMode.BEATS_HIGH_FREQUENCY_INSTRUMENTS -> onTrebleDetected?.invoke(trebleIntensity)
                else -> { /* Beat is handled by detectBeat, other modes don't trigger these */ }
            }
        } else {
            // If FFT is somehow disabled (though it's always true now), still try to detect beats
            detectBeat(audioBuffer, shortsRead, 0f) // Pass 0f for bassIntensity if FFT is off
        }
    }

    // Changed return type to include all intensities
    private fun analyzeFFT(fftData: FloatArray): Triple<Float, Float, Float> {
        val sampleRate = SAMPLE_RATE.toFloat()
        val numBins = FFT_SIZE / 2

        val magnitudes = FloatArray(numBins)
        for (i in 0 until numBins) {
            val real = fftData[2 * i]
            val imag = fftData[2 * i + 1]
            magnitudes[i] = (real * real + imag * imag) // Power spectrum
        }

        val maxMagnitude = magnitudes.maxOrNull() ?: 0f
        Log.d(TAG, "Raw FFT Magnitudes Peak: %.4f".format(maxMagnitude))


        val bassBandStartBin = (BASS_FREQ_RANGE.first / sampleRate * FFT_SIZE).toInt().coerceIn(0, numBins - 1)
        val bassBandEndBin = (BASS_FREQ_RANGE.second / sampleRate * FFT_SIZE).toInt().coerceIn(0, numBins - 1)
        val midBandStartBin = (MID_FREQ_RANGE.first / sampleRate * FFT_SIZE).toInt().coerceIn(0, numBins - 1)
        val midBandEndBin = (MID_FREQ_RANGE.second / sampleRate * FFT_SIZE).toInt().coerceIn(0, numBins - 1)
        val trebleBandStartBin = (TREBLE_FREQ_RANGE.first / sampleRate * FFT_SIZE).toInt().coerceIn(0, numBins - 1)
        val trebleBandEndBin = (TREBLE_FREQ_RANGE.second / sampleRate * FFT_SIZE).toInt().coerceIn(0, numBins - 1)

        var bassSum = 0.0f
        var midSum = 0.0f
        var trebleSum = 0.0f

        for (i in bassBandStartBin..bassBandEndBin) {
            bassSum += magnitudes[i]
        }
        for (i in midBandStartBin..midBandEndBin) {
            midSum += magnitudes[i]
        }
        for (i in trebleBandStartBin..trebleBandEndBin) {
            trebleSum += magnitudes[i]
        }

        val bassNormFactor = (bassBandEndBin - bassBandStartBin + 1).toFloat().coerceAtLeast(1f)
        val midNormFactor = (midBandEndBin - midBandStartBin + 1).toFloat().coerceAtLeast(1f)
        val trebleNormFactor = (trebleBandEndBin - trebleBandStartBin + 1).toFloat().coerceAtLeast(1f)

        var bassIntensity = (bassSum / bassNormFactor / MAX_INTENSITY_LEVEL).coerceIn(0f, 1f)
        var midIntensity = (midSum / midNormFactor / MAX_INTENSITY_LEVEL).coerceIn(0f, 1f)
        var trebleIntensity = (trebleSum / trebleNormFactor / MAX_INTENSITY_LEVEL).coerceIn(0f, 1f)

        bassIntensity = bassIntensity.pow(0.5f)
        midIntensity = midIntensity.pow(0.5f)
        trebleIntensity = trebleIntensity.pow(0.5f)

        Log.d(TAG, "FFT Intensities - Bass: %.4f, Mid: %.4f, Treble: %.4f".format(bassIntensity, midIntensity, trebleIntensity))

        return Triple(bassIntensity, midIntensity, trebleIntensity)
    }


    private fun detectBeat(audioBuffer: ShortArray, shortsRead: Int, bassIntensityFromFFT: Float) {
        var currentEnergy = 0.0f
        for (i in 0 until shortsRead) {
            val sample = audioBuffer[i].toFloat() / 32768.0f
            currentEnergy += sample * sample
        }
        currentEnergy /= shortsRead.toFloat()

        Log.d(TAG, "Raw Beat Energy: %.6f".format(currentEnergy))

        val beatIntensity = (currentEnergy * BEAT_ENERGY_SCALE_FACTOR).coerceIn(0f, 1f)

        val currentTime = System.currentTimeMillis()

        // Determine the energy to use for triggering the beat based on HapticMode
        val triggerThresholdForMode = if (hapticMode == HapticMode.ONLY_BEATS) {
            // Use a higher threshold for bass-driven beats to filter out non-bass peaks
            BASS_BEAT_THRESHOLD
        } else {
            // Use the general threshold for other modes
            generalBeatThreshold
        }

        val triggerEnergy = if (hapticMode == HapticMode.ONLY_BEATS) {
            // In ONLY_BEATS mode, use bassIntensityFromFFT for triggering
            bassIntensityFromFFT
        } else {
            // In other modes (e.g., BEATS_BASS), use the general scaled energy
            (if (currentEnergy > 0) log10(currentEnergy.toDouble() + 1.0) else 0.0).toFloat()
        }

        if (triggerEnergy > triggerThresholdForMode && (currentTime - lastBeatTime > MIN_BEAT_INTERVAL_MS)) {
            onBeatDetected(beatIntensity) // Still pass overall beat intensity for haptic strength
            lastBeatTime = currentTime
            Log.d(TAG, "BEAT DETECTED! Intensity: %.4f (Trigger Energy: %.4f, Threshold: %.4f)".format(beatIntensity, triggerEnergy, triggerThresholdForMode))
        }
    }


    fun stopRecording() {
        isRecording.set(false)
        audioProcessingThread?.join()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "AudioRecord stopped and released.")
    }

    fun isRecording(): Boolean {
        return isRecording.get()
    }

    companion object {
        private const val TAG = "AudioAnalyzer"
    }
}
