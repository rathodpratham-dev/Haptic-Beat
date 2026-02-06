package com.example.hapticbeat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo // Import ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class MusicHapticService : Service() {

    private var audioAnalyzer: AudioAnalyzer? = null
    private lateinit var hapticEngine: HapticEngine
    private var mediaProjection: MediaProjection? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private var currentGlobalIntensity: Int = 50
    private var currentHapticMode: HapticMode = HapticMode.ONLY_BEATS
    private var currentHapticType: HapticType = HapticType.NORMAL_HAPTICS
    private var isMicInputEnabled: Boolean = false
    private var isFFTEnabled: Boolean = true

    private val NOTIFICATION_CHANNEL_ID = "HapticBeatServiceChannel"
    private val NOTIFICATION_ID = 101

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.w(TAG, "MediaProjection session stopped unexpectedly. Stopping service.")
            stopSelf() // Stop the service if MediaProjection is terminated
        }
    }

    override fun onCreate() {
        super.onCreate()
        hapticEngine = HapticEngine(this)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
        Log.d(TAG, "MusicHapticService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                Constants.ACTION_START_SERVICE -> {
                    val resultCode = it.getIntExtra(Constants.EXTRA_MEDIA_PROJECTION_RESULT_CODE, 0)
                    val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.getParcelableExtra(Constants.EXTRA_MEDIA_PROJECTION_RESULT_DATA, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        it.getParcelableExtra(Constants.EXTRA_MEDIA_PROJECTION_RESULT_DATA)
                    }

                    currentHapticMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.getSerializableExtra(Constants.EXTRA_HAPTIC_MODE, HapticMode::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        it.getSerializableExtra(Constants.EXTRA_HAPTIC_MODE) as? HapticMode
                    } ?: HapticMode.ONLY_BEATS

                    currentHapticType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.getSerializableExtra(Constants.EXTRA_HAPTIC_TYPE, HapticType::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        it.getSerializableExtra(Constants.EXTRA_HAPTIC_TYPE) as? HapticType
                    } ?: HapticType.NORMAL_HAPTICS


                    currentGlobalIntensity = it.getIntExtra(Constants.EXTRA_GLOBAL_INTENSITY, 50)
                    isMicInputEnabled = it.getBooleanExtra(Constants.EXTRA_IS_MIC_INPUT_ENABLED, false)
                    isFFTEnabled = it.getBooleanExtra(Constants.EXTRA_FFT_ENABLED, true)

                    hapticEngine.setGlobalIntensity(currentGlobalIntensity)

                    val notification = buildNotification()

                    // FIXED: Always use a foregroundServiceType declared in the manifest.
                    // If mic input is enabled, use MICROPHONE type.
                    // If mic input is NOT enabled, it means we intend to use Media Projection,
                    // so declare MEDIA_PROJECTION type from the start.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (isMicInputEnabled) {
                            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                        } else {
                            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                        }
                    } else {
                        // For older APIs, foregroundServiceType parameter is not available
                        startForeground(NOTIFICATION_ID, notification)
                    }


                    if (!isMicInputEnabled) {
                        if (resultData != null) {
                            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
                            if (mediaProjection == null) {
                                Log.e(TAG, "MediaProjection is null after obtaining. Cannot start audio capture.")
                                stopSelf()
                                return START_NOT_STICKY
                            }
                            mediaProjection?.registerCallback(mediaProjectionCallback, null)
                            // Removed: Redundant second startForeground call as it's handled above
                            startAudioAnalysis(mediaProjection)
                        } else {
                            Log.e(TAG, "Cannot start service: MediaProjection data is null and mic input is disabled.")
                            stopSelf()
                            return START_NOT_STICKY
                        }
                    } else {
                        startAudioAnalysis(null)
                    }
                }
                Constants.ACTION_STOP_SERVICE -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopAudioAnalysis()
                    stopSelf()
                }
                Constants.ACTION_SET_GLOBAL_INTENSITY -> {
                    currentGlobalIntensity = it.getIntExtra(Constants.EXTRA_GLOBAL_INTENSITY, currentGlobalIntensity)
                    hapticEngine.setGlobalIntensity(currentGlobalIntensity)
                    Log.d(TAG, "Service: Global Intensity set to $currentGlobalIntensity")
                }
                Constants.ACTION_SET_MODE -> {
                    currentHapticMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.getSerializableExtra(Constants.EXTRA_HAPTIC_MODE, HapticMode::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        it.getSerializableExtra(Constants.EXTRA_HAPTIC_MODE) as? HapticMode
                    } ?: currentHapticMode
                    Log.d(TAG, "Service: Haptic Mode set to $currentHapticMode")
                    if (audioAnalyzer?.isRecording() == true) {
                        stopAudioAnalysis()
                        startAudioAnalysis(mediaProjection)
                    }
                }
                Constants.ACTION_SET_HAPTIC_TYPE -> {
                    currentHapticType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.getSerializableExtra(Constants.EXTRA_HAPTIC_TYPE, HapticType::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        it.getSerializableExtra(Constants.EXTRA_HAPTIC_TYPE) as? HapticType
                    } ?: currentHapticType
                    Log.d(TAG, "Service: Haptic Type set to $currentHapticType")
                }
                Constants.ACTION_SET_MIC_INPUT -> {
                    isMicInputEnabled = it.getBooleanExtra(Constants.EXTRA_IS_MIC_INPUT_ENABLED, isMicInputEnabled)
                    Log.d(TAG, "Service: Mic Input Toggled: $isMicInputEnabled")
                    startAudioAnalysis(mediaProjection)
                }
                Constants.ACTION_SET_FFT_ENABLED -> {
                    isFFTEnabled = it.getBooleanExtra(Constants.EXTRA_FFT_ENABLED, isFFTEnabled)
                    Log.d(TAG, "Service: FFT Analysis Toggled: $isFFTEnabled")
                    if (audioAnalyzer?.isRecording() == true) {
                        stopAudioAnalysis()
                        startAudioAnalysis(mediaProjection)
                    }
                }
                Constants.ACTION_REQUEST_SERVICE_STATUS -> {
                    sendServiceStatusUpdate()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startAudioAnalysis(mp: MediaProjection?) {
        audioAnalyzer?.stopRecording()
        hapticEngine.cancelHapticFeedback()

        audioAnalyzer = AudioAnalyzer(
            this,
            mp,
            onBeatDetected = { intensity ->
                hapticEngine.triggerHapticFeedback(HapticEngine.HapticEvent.BEATS, intensity, currentHapticType)
            },
            onBassDetected = { intensity ->
                if (currentHapticMode == HapticMode.BEATS_BASS) {
                    hapticEngine.triggerHapticFeedback(HapticEngine.HapticEvent.BASS, intensity, currentHapticType)
                }
            },
            onMidRangeDetected = { intensity ->
                if (currentHapticMode == HapticMode.MID_RANGE_VOCALS) {
                    hapticEngine.triggerHapticFeedback(HapticEngine.HapticEvent.MID_RANGE, intensity, currentHapticType)
                }
            },
            onTrebleDetected = { intensity ->
                if (currentHapticMode == HapticMode.ONLY_HIGH_FREQUENCY_INSTRUMENTS || currentHapticMode == HapticMode.BEATS_HIGH_FREQUENCY_INSTRUMENTS) {
                    hapticEngine.triggerHapticFeedback(HapticEngine.HapticEvent.TREBLE, intensity, currentHapticType)
                }
            },
            hapticMode = currentHapticMode,
            isMicInputEnabled = isMicInputEnabled,
            isFFTEnabled = isFFTEnabled,
            preferredBufferSizeSamples = 1024
        )
        audioAnalyzer?.startRecording()
        sendServiceStatusUpdate()
        Log.d(TAG, "Audio analysis started by service.")
    }

    private fun stopAudioAnalysis() {
        audioAnalyzer?.stopRecording()
        hapticEngine.cancelHapticFeedback()
        Log.d(TAG, "Audio analysis stopped by service.")
        sendServiceStatusUpdate()
    }

    private fun sendServiceStatusUpdate() {
        val statusIntent = Intent(this, MainActivity::class.java).apply { // Changed to MainActivity for broadcast
            action = Constants.ACTION_SERVICE_STATUS_UPDATE
            putExtra(Constants.EXTRA_IS_RUNNING, audioAnalyzer?.isRecording() ?: false)
            putExtra(Constants.EXTRA_CURRENT_HAPTIC_MODE, currentHapticMode as java.io.Serializable)
            putExtra(Constants.EXTRA_CURRENT_HAPTIC_TYPE, currentHapticType as java.io.Serializable)
            putExtra(Constants.EXTRA_GLOBAL_INTENSITY_PERCENT, currentGlobalIntensity)
            putExtra(Constants.EXTRA_IS_MIC_INPUT_ENABLED, isMicInputEnabled)
        }
        sendBroadcast(statusIntent) // Changed to sendBroadcast
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Haptic Beat Service Channel",
                NotificationManager.IMPORTANCE_LOW // Changed to IMPORTANCE_LOW for silent notification
            ).apply {
                description = "This channel is used by the Haptic Beat service for audio processing. Do not dismiss this notification for continuous haptic feedback."
                setSound(null, null) // Explicitly set no sound
                enableVibration(false) // Disable vibration
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Haptic Beat Running")
            .setContentText("Tap to open. Do NOT dismiss for continuous haptics.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Set priority to default as sound is handled by channel
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        stopAudioAnalysis()
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
        Log.d(TAG, "MusicHapticService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "MusicHapticService"
    }
}
