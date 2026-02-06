package com.example.hapticbeat

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.math.roundToInt
import kotlin.math.max

// Placeholder for RichTapUtils (In a real project, this would be from the RichTap SDK)
class RichTapUtils private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: RichTapUtils? = null

        fun getInstance(context: Context? = null): RichTapUtils {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RichTapUtils(context!!).also { INSTANCE = it }
            }
        }
    }

    fun init(context: Context): RichTapUtils {
        Log.d("RichTapUtils", "RichTapUtils initialized (placeholder).")
        return this
    }

    fun quit() {
        Log.d("RichTapUtils", "RichTapUtils quit (placeholder).")
    }

    fun isSupportedRichTap(): Boolean {
        // For demonstration, assume RichTap is supported on API 29+
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    fun playExtPrebaked(prebakedId: Int, amplitude: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator?.hasVibrator() == false) {
            Log.w("RichTapUtils", "No vibrator found to play prebaked effect.")
            return
        }

        val actualAmplitude = amplitude.coerceIn(0, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect: VibrationEffect = when (prebakedId) {
                PrebakedEffectId.RT_DRUM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createWaveform(longArrayOf(0, 30, 20, 50), intArrayOf(0, actualAmplitude, (actualAmplitude * 0.5f).roundToInt(), 0), -1)
                    } else {
                        // Fallback for Q- specific waveform if prebakedId is RT_DRUM but device is O-P
                        VibrationEffect.createOneShot(80, actualAmplitude)
                    }
                }
                PrebakedEffectId.RT_CLICK -> VibrationEffect.createOneShot(20, actualAmplitude)
                PrebakedEffectId.RT_DOUBLE_CLICK -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createWaveform(longArrayOf(0, 20, 50, 20), intArrayOf(0, actualAmplitude, 0, actualAmplitude), -1)
                    } else {
                        VibrationEffect.createOneShot(100, actualAmplitude) // Simple double click for O-P
                    }
                }
                else -> VibrationEffect.createOneShot(50, actualAmplitude)
            }
            vibrator?.vibrate(effect)
        } else {
            // For API < O, use simple one-shot vibrate with a default duration
            val duration: Long = when (prebakedId) {
                PrebakedEffectId.RT_DRUM -> 80L
                PrebakedEffectId.RT_CLICK -> 20L
                PrebakedEffectId.RT_DOUBLE_CLICK -> 100L
                else -> 50L
            }
            @Suppress("DEPRECATION")
            vibrator?.vibrate(duration)
        }
        Log.d("RichTapUtils", "Playing prebaked effect ID: $prebakedId with amplitude: $amplitude (placeholder).")
    }
}

// Placeholder for PrebakedEffectId (In a real project, this would be from the RichTap SDK)
object PrebakedEffectId {
    const val RT_CLICK = 0
    const val RT_GUNSHOT = 1
    const val RT_DOUBLE_CLICK = 2
    const val RT_SPEED_UP = 3
    const val RT_SOFT_CLICK = 4
    const val RT_JUMP = 5
    const val RT_TICK = 6
    const val RT_DRUM = 7 // We will use this for beats
    const val RT_THUD = 8
    const val RT_COIN_DROP = 9
    const val RT_FAILURE = 10
    const val RT_HEARTBEAT = 11
    const val RT_SUCCESSRT_PLUCKING = 13
    const val RT_RAMP_UP = 14
    const val RT_DRAWING_ARROW = 15
    const val RT_TOGGLE_SWITCH = 16
    const val RT_CAMERA_SHUTTER = 17
    const val RT_LONG_PRESS = 18
    const val RT_FIREWORKS = 19
    const val RT_VIRTUAL_KEY = 20
    const val RT_SNIPER_RIFLE = 21
    const val RT_KEYBOARD_TAP = 22
    const val RT_ASSAULT_RIFLE = 23
    const val RT_CLOCK_TICK = 24
    const val RT_CYMBAL = 25
    const val RT_CALENDAR_DATE = 26
    const val RT_TAMBOURINE = 27
    const val RT_CONTEXT_CLICK = 28
    const val RT_FAST_MOVING = 29
    const val RT_KEYBOARD_RELEASERT_FLY = 31
    const val RT_VIRTUAL_KEY_RELEASERT_FOOTSTEP = 33
    const val RT_TEXT_HANDLE_MOVERT_ICE = 35
    const val RT_ENTRY_BUMP = 36
    const val RT_LIGHTNING = 37
    const val RT_DRAG_CROSSING = 38
    const val RT_SPRING = 39
    const val RT_GESTURE = 40
    const val RT_SWING = 41
    const val RT_CONFIRM = 42
    const val RT_WIND = 43
    const val RT_REJECT = 44
    const val RT_VICTORY = 45
    const val RT_BOMB = 46
    const val RT_AWARD = 47
    const val RT_SWORD = 48
    const val RT_GAMEOVER = 49
}


class HapticEngine(private val context: Context) {

    private var vibrator: Vibrator? = null
    private var globalIntensityPercent: Int = 50 // 0-100%

    // Constants for sustained haptics (for RICHTAP_HAPTICS)
    // Beat RICHTAP_HAPTICS waveform constants (these will be replaced by RichTap prebaked for BEATS)
    private val BEAT_INITIAL_PULSE_DURATION_MS = 50L
    private val BEAT_SUSTAIN_PULSE_DURATION_MS = 100L
    private val BEAT_FADE_OUT_DURATION_MS = 50L
    private val BEAT_SUSTAIN_AMPLITUDE_FACTOR = 0.3f

    // New constants for BASS RICHTAP_HAPTICS waveform
    private val BASS_INITIAL_PULSE_DURATION_MS = 80L // Longer initial pulse for bass
    private val BASS_SUSTAIN_PULSE_DURATION_MS = 200L // Sustained for a deeper feel
    private val BASS_FADE_OUT_DURATION_MS = 100L // Smooth fade out
    private val BASS_SUSTAIN_AMPLITUDE_FACTOR = 0.8f // Higher sustain for bass feel

    // Constants for VOCAL (MID_RANGE) RICHTAP_HAPTICS waveform
    private val VOCAL_INITIAL_PULSE_DURATION_MS = 40L // Slightly shorter initial pulse
    private val VOCAL_SUSTAIN_PULSE_DURATION_MS = 350L // Even longer sustain for continuous vocal feel
    private val VOCAL_FADE_OUT_DURATION_MS = 150L // Longer fade out for smoother decay
    private val VOCAL_INITIAL_AMPLITUDE_FACTOR = 0.5f // Less sharp initial
    private val VOCAL_SUSTAIN_AMPLITUDE_FACTOR = 0.7f // Higher sustain for continuous feel

    // Constants for TREBLE (HIGH_FREQUENCY) RICHTAP_HAPTICS waveform
    private val TREBLE_INITIAL_PULSE_DURATION_MS = 15L // Even shorter initial pulse for crispness
    private val TREBLE_FADE_OUT_DURATION_MS = 8L


    // Define HapticEvent enum here to resolve the reference
    enum class HapticEvent {
        BEATS,
        BASS,
        MID_RANGE, // Corresponds to Vocals
        TREBLE // Corresponds to High Frequency Instruments
    }

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        // Initialize RichTapUtils (placeholder)
        RichTapUtils.getInstance(context).init(context)
        Log.d(TAG, "HapticEngine initialized.")
    }

    fun setGlobalIntensity(intensity: Int) {
        globalIntensityPercent = intensity.coerceIn(0, 100)
        Log.d(TAG, "HapticEngine Global Intensity set to: $globalIntensityPercent%")
    }

    fun triggerHapticFeedback(event: HapticEvent, intensity: Float, type: HapticType) {
        if (vibrator?.hasVibrator() == false) {
            Log.w(TAG, "No vibrator found on this device.")
            return
        }

        val scaledIntensity = (intensity * (globalIntensityPercent / 100.0f)).coerceIn(0.0f, 1.0f)
        var amplitude = (scaledIntensity * 255).roundToInt() // Scale to 0-255 for VibrationEffect

        if (amplitude <= 0) {
            // Log.d(TAG, "Amplitude is zero, not triggering haptic feedback.")
            return
        }

        Log.d(TAG, "Triggered haptic feedback for $event with intensity: $scaledIntensity, type: $type")

        // Cancel any ongoing vibration before starting a new one to prevent overlaps
        vibrator?.cancel()

        when (type) {
            HapticType.NORMAL_HAPTICS -> {
                val vibrationDuration = (50 * scaledIntensity).toLong().coerceAtLeast(10L) // Min 10ms
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(vibrationDuration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(vibrationDuration)
                }
            }
            HapticType.RICHTAP_HAPTICS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    when (event) {
                        HapticEvent.BEATS -> { // Use RichTap prebaked effect for BEATS
                            if (RichTapUtils.getInstance().isSupportedRichTap()) {
                                RichTapUtils.getInstance().playExtPrebaked(PrebakedEffectId.RT_DRUM, amplitude)
                            } else {
                                // Fallback to custom waveform if RichTap not supported
                                val initialBeatAmplitude = amplitude
                                val sustainBeatAmplitude = (amplitude * BEAT_SUSTAIN_AMPLITUDE_FACTOR).roundToInt().coerceAtLeast(1)

                                val waveformTimings = longArrayOf(
                                    0, // Initial delay
                                    BEAT_INITIAL_PULSE_DURATION_MS, // Initial pulse
                                    BEAT_SUSTAIN_PULSE_DURATION_MS, // Sustained part
                                    BEAT_FADE_OUT_DURATION_MS // Fade out
                                )
                                val waveformAmplitudes = intArrayOf(
                                    0,
                                    initialBeatAmplitude,
                                    sustainBeatAmplitude,
                                    0
                                )
                                vibrator?.vibrate(VibrationEffect.createWaveform(waveformTimings, waveformAmplitudes, -1))
                            }
                        }
                        HapticEvent.BASS -> { // Distinct BASS haptic with 65% intensity scaling
                            amplitude = (amplitude * 0.65f).roundToInt().coerceAtLeast(1) // Apply 65% scaling

                            val initialBassAmplitude = (amplitude * BASS_SUSTAIN_AMPLITUDE_FACTOR).roundToInt().coerceAtLeast(1)
                            val sustainBassAmplitude = (amplitude * BASS_SUSTAIN_AMPLITUDE_FACTOR).roundToInt().coerceAtLeast(1)

                            val waveformTimings = longArrayOf(
                                0, // Initial delay
                                BASS_INITIAL_PULSE_DURATION_MS, // Initial pulse
                                BASS_SUSTAIN_PULSE_DURATION_MS, // Sustained part
                                BASS_FADE_OUT_DURATION_MS // Fade out
                            )
                            val waveformAmplitudes = intArrayOf(
                                0,
                                initialBassAmplitude,
                                sustainBassAmplitude,
                                0
                            )
                            vibrator?.vibrate(VibrationEffect.createWaveform(waveformTimings, waveformAmplitudes, -1))
                        }
                        HapticEvent.MID_RANGE -> { // VOCALS (Throat Vibrate Feel)
                            val initialVocalAmplitude = (amplitude * VOCAL_INITIAL_AMPLITUDE_FACTOR).roundToInt().coerceAtLeast(1)
                            val sustainVocalAmplitude = (amplitude * VOCAL_SUSTAIN_AMPLITUDE_FACTOR).roundToInt().coerceAtLeast(1)

                            val waveformTimings = longArrayOf(
                                0, // Initial delay
                                VOCAL_INITIAL_PULSE_DURATION_MS, // Initial pulse
                                VOCAL_SUSTAIN_PULSE_DURATION_MS, // Sustained part
                                VOCAL_FADE_OUT_DURATION_MS // Fade out
                            )
                            val waveformAmplitudes = intArrayOf(
                                0,
                                initialVocalAmplitude,
                                sustainVocalAmplitude,
                                0
                            )
                            vibrator?.vibrate(VibrationEffect.createWaveform(waveformTimings, waveformAmplitudes, -1))
                        }
                        HapticEvent.TREBLE -> { // HIGH FREQUENCY INSTRUMENTS (Crisp Thin Vibration)
                            val waveformTimings = longArrayOf(
                                0, // Initial delay
                                TREBLE_INITIAL_PULSE_DURATION_MS, // Short, crisp pulse
                                TREBLE_FADE_OUT_DURATION_MS // Quick fade out
                            )
                            val waveformAmplitudes = intArrayOf(
                                0,
                                amplitude, // Full amplitude for crispness
                                0
                            )
                            vibrator?.vibrate(VibrationEffect.createWaveform(waveformTimings, waveformAmplitudes, -1))
                        }
                    }
                } else {
                    // Fallback for older APIs if RICHTAP_HAPTICS is selected but waveform not supported
                    val vibrationDuration = (70 * scaledIntensity).toLong().coerceAtLeast(10L)
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(vibrationDuration)
                }
            }
        }
    }

    fun cancelHapticFeedback() {
        vibrator?.cancel()
        Log.d(TAG, "Cancelled haptic feedback.")
    }

    companion object {
        private const val TAG = "HapticEngine"
    }
}
