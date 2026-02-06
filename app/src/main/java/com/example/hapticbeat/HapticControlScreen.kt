package com.example.hapticbeat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HapticControlScreen(
    isServiceRunning: Boolean,
    onToggleService: (Boolean) -> Unit,
    micInputEnabled: Boolean,
    onMicInputToggle: (Boolean) -> Unit,
    globalIntensity: Float,
    onGlobalIntensityChange: (Float) -> Unit,
    currentHapticMode: HapticMode,
    onHapticModeChange: (HapticMode) -> Unit,
    currentHapticType: HapticType,
    onHapticTypeChange: (HapticType) -> Unit,
    isRichTapSupported: Boolean // New parameter for RichTap support status
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Haptic Beat Control",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Service On/Off Toggle
        Button(
            onClick = { onToggleService(!isServiceRunning) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isServiceRunning) "STOP HAPTIC SERVICE" else "START HAPTIC SERVICE")
        }

        // Microphone Input Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Use Microphone Input")
            Switch(
                checked = micInputEnabled,
                onCheckedChange = onMicInputToggle,
                enabled = !isServiceRunning // Disable if service is running, requires restart to change input
            )
        }

        // Global Intensity Slider
        Text("Global Haptic Intensity: ${(globalIntensity * 100).toInt()}%")
        Slider(
            value = globalIntensity,
            onValueChange = onGlobalIntensityChange,
            valueRange = 0f..1f,
            steps = 99, // 0 to 100%
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceRunning
        )

        // Haptic Mode Selection
        Text("Haptic Mode")
        ModeSelectionChips(
            selectedMode = currentHapticMode,
            onModeSelected = onHapticModeChange,
            enabled = isServiceRunning
        )

        // Haptic Type Selection
        Text("Haptic Type")
        TypeSelectionChips(
            selectedType = currentHapticType,
            onTypeSelected = onHapticTypeChange,
            enabled = isServiceRunning,
            isRichTapSupported = isRichTapSupported // Pass RichTap support status
        )

        // RichTap Support Status (New UI element)
        Text("RichTap Support: ${if (isRichTapSupported) "Yes" else "No"}")

        // Removed FFT Analysis Toggle as requested
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModeSelectionChips(
    selectedMode: HapticMode,
    onModeSelected: (HapticMode) -> Unit,
    enabled: Boolean
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HapticMode.entries.forEach { mode ->
            FilterChip(
                selected = (mode == selectedMode),
                onClick = { onModeSelected(mode) },
                label = { Text(mode.name.replace("_", " ")) },
                enabled = enabled
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypeSelectionChips(
    selectedType: HapticType,
    onTypeSelected: (HapticType) -> Unit,
    enabled: Boolean, // This `enabled` controls if the service is running
    isRichTapSupported: Boolean // New parameter to control RichTap chip enablement
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HapticType.entries.forEach { type ->
            FilterChip(
                selected = (type == selectedType),
                onClick = { onTypeSelected(type) },
                label = { Text(type.name.replace("_", " ")) },
                // Enable chip only if overall `enabled` (service running) AND
                // if it's RichTap, only if RichTap is supported. Normal Haptics is always enabled if service is running.
                enabled = enabled && (type != HapticType.RICHTAP_HAPTICS || isRichTapSupported)
            )
        }
    }
}
