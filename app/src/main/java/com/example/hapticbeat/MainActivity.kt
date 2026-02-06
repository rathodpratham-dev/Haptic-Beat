package com.example.hapticbeat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.hapticbeat.ui.theme.HapticBeatTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import com.example.hapticbeat.HapticControlScreen // Import your HapticControlScreen composable
import com.example.hapticbeat.RichTapUtils // Import RichTapUtils to check support

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    // State variables for Compose UI
    private var isServiceRunning by mutableStateOf(false)
    private var currentGlobalIntensity by mutableStateOf(50)
    private var isMicInputEnabled by mutableStateOf(false)
    private var isFFTEnabled by mutableStateOf(true) // FFT is now always enabled
    private var currentHapticMode by mutableStateOf(HapticMode.ONLY_BEATS)
    private var currentHapticType by mutableStateOf(HapticType.NORMAL_HAPTICS)
    private var isRichTapSupported by mutableStateOf(false) // State for RichTap support

    // For SharedPreferences
    private val PREFS_NAME = "HapticBeatPrefs"
    private val KEY_MIC_INPUT = "isMicInputEnabled"
    private val KEY_FFT_ENABLED = "isFFTEnabled"
    private val KEY_GLOBAL_INTENSITY = "globalIntensity"
    private val KEY_HAPTIC_MODE_ORDINAL = "hapticModeOrdinal" // Store ordinal for enums
    private val KEY_HAPTIC_TYPE_ORDINAL = "hapticTypeOrdinal" // Store ordinal for enums


    private val requestMediaProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startHapticServiceWithAudio(result.resultCode, result.data!!)
        } else {
            Log.e(TAG, "MediaProjection permission denied or failed.")
            Toast.makeText(this, "Media Projection permission denied.", Toast.LENGTH_SHORT).show()
            isServiceRunning = false // Update state
        }
    }

    private val requestRecordAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "RECORD_AUDIO permission granted.")
            if (isMicInputEnabled) { // Check if mic input is still desired after permission
                startHapticServiceWithAudio(RESULT_OK, Intent()) // Dummy intent for mic input
            }
        } else {
            Log.e(TAG, "RECORD_AUDIO permission denied.")
            Toast.makeText(this, "Microphone permission denied. Cannot use mic input.", Toast.LENGTH_SHORT).show()
            isMicInputEnabled = false // Update state
            isServiceRunning = false // Update state
        }
    }

    private val requestBatteryOptimization = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (isIgnoringBatteryOptimizations()) {
            Toast.makeText(this, "Battery optimizations ignored for Haptic Beat.", Toast.LENGTH_SHORT).show()
            checkAndRequestNotificationPermission()
        } else {
            Toast.makeText(this, "Battery optimization permission denied. App might not work reliably in background.", Toast.LENGTH_LONG).show()
            isServiceRunning = false // Update state
        }
    }

    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "POST_NOTIFICATIONS permission granted.")
            // No direct service start here; this is part of the initial permission cascade.
            // The actual service start is triggered by the "Start Haptics" button.
        } else {
            Log.e(TAG, "POST_NOTIFICATIONS permission denied. Notifications will not be shown.")
            Toast.makeText(this, "Notification permission denied. Service notifications may not appear.", Toast.LENGTH_LONG).show()
            // Proceed anyway, but with a warning
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize RichTapUtils and check support status
        RichTapUtils.getInstance(this).init(this)
        isRichTapSupported = RichTapUtils.getInstance().isSupportedRichTap()

        // Determine the default HapticType based on RichTap support
        val defaultHapticTypeForApp = if (isRichTapSupported) HapticType.RICHTAP_HAPTICS else HapticType.NORMAL_HAPTICS

        // Load saved preferences
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isMicInputEnabled = sharedPrefs.getBoolean(KEY_MIC_INPUT, false)
        isFFTEnabled = sharedPrefs.getBoolean(KEY_FFT_ENABLED, true) // Always true now
        currentGlobalIntensity = sharedPrefs.getInt(KEY_GLOBAL_INTENSITY, 50)

        val savedHapticModeOrdinal = sharedPrefs.getInt(KEY_HAPTIC_MODE_ORDINAL, HapticMode.ONLY_BEATS.ordinal)
        currentHapticMode = HapticMode.entries[savedHapticModeOrdinal]

        // Load saved HapticType, using the determined default if no preference is saved
        val savedHapticTypeOrdinal = sharedPrefs.getInt(KEY_HAPTIC_TYPE_ORDINAL, defaultHapticTypeForApp.ordinal)
        currentHapticType = HapticType.entries[savedHapticTypeOrdinal]


        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            HapticBeatTheme {
                HapticControlScreen(
                    isServiceRunning = isServiceRunning,
                    onToggleService = { shouldStart ->
                        if (shouldStart) {
                            startHapticsBasedOnSwitchState()
                        } else {
                            stopHapticsService()
                        }
                    },
                    micInputEnabled = isMicInputEnabled,
                    onMicInputToggle = { newValue ->
                        isMicInputEnabled = newValue
                        sharedPrefs.edit().putBoolean(KEY_MIC_INPUT, newValue).apply()
                        sendServiceUpdate(Constants.ACTION_SET_MIC_INPUT, Constants.EXTRA_IS_MIC_INPUT_ENABLED, newValue)
                        // If service is running and mic input changes, restart service to apply change
                        if (isServiceRunning) {
                            stopHapticsService() // Stop current service
                            // Re-evaluate starting haptics with new mic input state
                            // This will trigger permission checks again if needed
                            startHapticsBasedOnSwitchState()
                        }
                    },
                    globalIntensity = currentGlobalIntensity / 100f, // Convert to 0f-1f for Slider
                    onGlobalIntensityChange = { newIntensityFloat ->
                        val newIntensity = (newIntensityFloat * 100).toInt()
                        currentGlobalIntensity = newIntensity
                        sharedPrefs.edit().putInt(KEY_GLOBAL_INTENSITY, newIntensity).apply()
                        sendServiceUpdate(Constants.ACTION_SET_GLOBAL_INTENSITY, Constants.EXTRA_GLOBAL_INTENSITY, newIntensity)
                    },
                    currentHapticMode = currentHapticMode,
                    onHapticModeChange = { newMode ->
                        currentHapticMode = newMode
                        sharedPrefs.edit().putInt(KEY_HAPTIC_MODE_ORDINAL, newMode.ordinal).apply()
                        sendServiceUpdate(Constants.ACTION_SET_MODE, Constants.EXTRA_HAPTIC_MODE, newMode as java.io.Serializable)
                    },
                    currentHapticType = currentHapticType,
                    onHapticTypeChange = { newType ->
                        currentHapticType = newType
                        sharedPrefs.edit().putInt(KEY_HAPTIC_TYPE_ORDINAL, newType.ordinal).apply()
                        sendServiceUpdate(Constants.ACTION_SET_HAPTIC_TYPE, Constants.EXTRA_HAPTIC_TYPE, newType as java.io.Serializable)
                    },
                    isRichTapSupported = isRichTapSupported
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Initiate permission checks when the activity comes to the foreground
        checkAndRequestBatteryOptimization()
    }

    private fun checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
            requestIgnoreBatteryOptimizations()
        } else {
            checkAndRequestNotificationPermission()
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            requestBatteryOptimization.launch(intent)
        }
    }

    private fun startHapticsBasedOnSwitchState() {
        if (isMicInputEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startHapticServiceWithAudio(RESULT_OK, Intent())
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaProjectionManager.createScreenCaptureIntent()?.let {
                    requestMediaProjection.launch(it)
                } ?: run {
                    Toast.makeText(this, "Failed to create Media Projection intent.", Toast.LENGTH_SHORT).show()
                    isServiceRunning = false // Update state
                }
            } else {
                Toast.makeText(this, "Internal audio capture requires Android 10 (API 29) or higher. Please enable Microphone Input.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Internal audio capture not supported on this device SDK: ${Build.VERSION.SDK_INT}")
                isServiceRunning = false // Update state
            }
        }
    }

    private fun startHapticServiceWithAudio(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, MusicHapticService::class.java).apply {
            action = Constants.ACTION_START_SERVICE
            putExtra(Constants.EXTRA_MEDIA_PROJECTION_RESULT_CODE, resultCode)
            putExtra(Constants.EXTRA_MEDIA_PROJECTION_RESULT_DATA, data)
            putExtra(Constants.EXTRA_GLOBAL_INTENSITY, currentGlobalIntensity)
            putExtra(Constants.EXTRA_HAPTIC_MODE, currentHapticMode as java.io.Serializable)
            putExtra(Constants.EXTRA_HAPTIC_TYPE, currentHapticType as java.io.Serializable)
            putExtra(Constants.EXTRA_IS_MIC_INPUT_ENABLED, isMicInputEnabled)
            putExtra(Constants.EXTRA_FFT_ENABLED, isFFTEnabled)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isServiceRunning = true // Update state
    }

    private fun stopHapticsService() {
        val serviceIntent = Intent(this@MainActivity, MusicHapticService::class.java).apply {
            action = Constants.ACTION_STOP_SERVICE
        }
        stopService(serviceIntent)
        isServiceRunning = false // Update state
    }

    // Helper to send updates to the service (for intensity, mode, type, etc.)
    private fun <T : java.io.Serializable> sendServiceUpdate(action: String, extraKey: String, extraValue: T) {
        val serviceIntent = Intent(this@MainActivity, MusicHapticService::class.java).apply {
            this.action = action
            putExtra(extraKey, extraValue)
        }
        startService(serviceIntent) // Use startService for updates to a running service
    }

    override fun onDestroy() {
        super.onDestroy()
        // Optionally, stop the service when the activity is destroyed
        // stopHapticsService()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
