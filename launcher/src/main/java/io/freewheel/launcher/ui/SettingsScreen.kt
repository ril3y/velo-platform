package io.freewheel.launcher.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.freewheel.launcher.BuildConfig
import io.freewheel.launcher.data.AppInfo
import io.freewheel.launcher.service.ServiceStatus
import io.freewheel.launcher.update.AppUpdate
import io.freewheel.launcher.update.UpdateStatus
import io.freewheel.launcher.ui.theme.*

private enum class SettingsCategory(val label: String) {
    SERVICES("Services"),
    RIDE_DATA("Ride Data"),
    DISPLAY("Display"),
    HOME_TILES("Home Tiles"),
    DIAGNOSTICS("Diagnostics"),
    UCB_FIRMWARE("UCB Firmware"),
    CALIBRATION("Calibration"),
    SYSTEM("System"),
    ABOUT("About"),
}

@Composable
fun SettingsScreen(
    serviceStatus: ServiceStatus,
    onBack: () -> Unit,
    onRestartBridge: () -> Unit,
    onExportRides: () -> Unit,
    onClearRides: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    dimTimeoutMinutes: Int = 5,
    offTimeoutMinutes: Int = 15,
    onDimTimeoutChange: (Int) -> Unit = {},
    onOffTimeoutChange: (Int) -> Unit = {},
    pinnedApps: List<Pair<String, String>> = emptyList(),
    onTogglePin: (String, Boolean) -> Unit = { _, _ -> },
    autoRestartBridge: Boolean = true,
    onAutoRestartBridgeChange: (Boolean) -> Unit = {},
    // Default fitness app
    defaultFitnessApp: String = "io.freewheel.freeride",
    fitnessApps: List<AppInfo> = emptyList(),
    onDefaultFitnessAppChange: (String) -> Unit = {},
    // Diagnostics
    diagnosticsState: DiagnosticsState = DiagnosticsState(),
    onToggleRawMonitor: (Boolean) -> Unit = {},
    // OTA firmware flash
    otaFlashState: OtaFlashState = OtaFlashState(),
    onPickFirmwareFile: () -> Unit = {},
    onStartOtaFlash: () -> Unit = {},
    onCancelOtaFlash: () -> Unit = {},
    // Calibration
    calibrationState: CalibrationState = CalibrationState(),
    onStartCalibration: () -> Unit = {},
    onConfirmCalibrationStep: () -> Unit = {},
    onCancelCalibration: () -> Unit = {},
    // Update
    updateStatus: UpdateStatus = UpdateStatus.IDLE,
    updateLatestVersion: String = "",
    updateChangelog: String = "",
    updateDownloadProgress: Float = 0f,
    onCheckForUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    availableUpdates: List<AppUpdate> = emptyList(),
    onDownloadAppUpdate: (AppUpdate) -> Unit = {},
) {
    var selectedCategory by remember { mutableStateOf(SettingsCategory.SERVICES) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TextPrimary,
            )
        }

        // Two-pane layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left pane: category list
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(SurfaceBright, RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp),
            ) {
                for (cat in SettingsCategory.entries) {
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_tab_${cat.name}")
                            .clickable { selectedCategory = cat }
                            .then(
                                if (isSelected) Modifier.background(NeonAccent.copy(alpha = 0.1f))
                                else Modifier
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = cat.label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (isSelected) NeonAccent else TextSecondary,
                        )
                    }
                }
            }

            // Right pane: content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("settings_pane_${selectedCategory.name}")
                    .background(SurfaceBright, RoundedCornerShape(12.dp))
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (selectedCategory) {
                    SettingsCategory.SERVICES -> {
                        PaneTitle("Services")
                        ServiceRow(
                            name = "FreewheelBridge (TCP:9999)",
                            running = serviceStatus.serialBridgeRunning || serviceStatus.serialBridgeTcpAlive,
                            detail = if (serviceStatus.serialBridgeTcpAlive) "Connected" else "Not responding",
                            onRestart = onRestartBridge,
                        )
                        ToggleRow(
                            label = "Auto-restart FreewheelBridge",
                            checked = autoRestartBridge,
                            onCheckedChange = onAutoRestartBridgeChange,
                        )
                    }
                    SettingsCategory.RIDE_DATA -> {
                        PaneTitle("Ride Data")

                        // Default fitness app picker
                        FitnessAppPicker(
                            selectedPackage = defaultFitnessApp,
                            fitnessApps = fitnessApps,
                            onSelect = onDefaultFitnessAppChange,
                        )

                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = onExportRides,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAccent),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text("Export CSV")
                            }
                            OutlinedButton(
                                onClick = onClearRides,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeartRateRed),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text("Clear History")
                            }
                        }
                    }
                    SettingsCategory.DISPLAY -> {
                        PaneTitle("Display")
                        DropdownSettingRow(
                            label = "Burn-in dim timeout",
                            options = listOf(1, 5, 10, 15),
                            selectedValue = dimTimeoutMinutes,
                            formatLabel = { "$it min" },
                            onValueChange = onDimTimeoutChange,
                        )
                        DropdownSettingRow(
                            label = "Burn-in off timeout",
                            options = listOf(5, 15, 30),
                            selectedValue = offTimeoutMinutes,
                            formatLabel = { "$it min" },
                            onValueChange = onOffTimeoutChange,
                        )
                    }
                    SettingsCategory.HOME_TILES -> {
                        PaneTitle("Home Tiles")
                        if (pinnedApps.isEmpty()) {
                            Text(
                                text = "No pinned apps.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                            )
                        } else {
                            for ((pkg, label) in pinnedApps) {
                                var visible by remember { mutableStateOf(true) }
                                ToggleRow(
                                    label = label,
                                    checked = visible,
                                    onCheckedChange = { checked ->
                                        visible = checked
                                        onTogglePin(pkg, checked)
                                    },
                                )
                            }
                        }
                    }
                    SettingsCategory.DIAGNOSTICS -> {
                        DiagnosticsPane(
                            state = diagnosticsState,
                            onToggleRawMonitor = onToggleRawMonitor,
                        )
                    }
                    SettingsCategory.UCB_FIRMWARE -> {
                        OtaFlashPane(
                            state = otaFlashState,
                            onPickFile = onPickFirmwareFile,
                            onStartFlash = onStartOtaFlash,
                            onCancelFlash = onCancelOtaFlash,
                        )
                    }
                    SettingsCategory.CALIBRATION -> {
                        CalibrationPane(
                            state = calibrationState,
                            onStartCalibration = onStartCalibration,
                            onConfirmStep = onConfirmCalibrationStep,
                            onCancel = onCancelCalibration,
                        )
                    }
                    SettingsCategory.SYSTEM -> {
                        PaneTitle("System")
                        OutlinedButton(
                            onClick = onOpenSystemSettings,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            modifier = Modifier.height(48.dp),
                        ) {
                            Text("Open Android Settings")
                        }
                    }
                    SettingsCategory.ABOUT -> {
                        PaneTitle("About")
                        InfoRow("VeloLauncher", "${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_HASH})")
                        InfoRow("Build", "#${BuildConfig.VERSION_CODE}")
                        InfoRow("Package", BuildConfig.APPLICATION_ID)
                        InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        InfoRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")

                        Spacer(Modifier.height(16.dp))
                        PaneTitle("Updates")

                        when (updateStatus) {
                            UpdateStatus.IDLE -> {
                                OutlinedButton(
                                    onClick = onCheckForUpdate,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAccent),
                                    modifier = Modifier.height(48.dp),
                                ) {
                                    Text("Check for Updates")
                                }
                            }
                            UpdateStatus.CHECKING -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = NeonAccent,
                                        strokeWidth = 2.dp,
                                    )
                                    Text("Checking for updates...", color = TextSecondary)
                                }
                            }
                            UpdateStatus.UP_TO_DATE -> {
                                Text("You're up to date.", color = TextSecondary)
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onCheckForUpdate,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                                    modifier = Modifier.height(40.dp),
                                ) {
                                    Text("Check Again")
                                }
                            }
                            UpdateStatus.AVAILABLE -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NeonAccent.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Update available: v$updateLatestVersion",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NeonAccent,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (updateChangelog.isNotBlank()) {
                                        SimpleMarkdownText(updateChangelog)
                                    }

                                    if (availableUpdates.size <= 1) {
                                        // Single APK — use the legacy download path
                                        Button(
                                            onClick = onDownloadUpdate,
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonAccent),
                                            modifier = Modifier.height(48.dp),
                                        ) {
                                            Text("Download Update", color = DarkBackground, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        // Multiple APKs — show each with its own download button
                                        Text(
                                            "${availableUpdates.size} packages available:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                        )
                                        for (update in availableUpdates) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(DarkBackground.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        update.label.replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = TextPrimary,
                                                        fontWeight = FontWeight.Medium,
                                                    )
                                                    Text(
                                                        update.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextMuted,
                                                    )
                                                }
                                                Button(
                                                    onClick = { onDownloadAppUpdate(update) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = NeonAccent),
                                                    modifier = Modifier.height(36.dp),
                                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                                ) {
                                                    Text("Download", color = DarkBackground, fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            UpdateStatus.DOWNLOADING -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Downloading v$updateLatestVersion...", color = TextSecondary)
                                    LinearProgressIndicator(
                                        progress = { updateDownloadProgress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = NeonAccent,
                                        trackColor = SurfaceBorder,
                                    )
                                    Text(
                                        "${(updateDownloadProgress * 100).toInt()}%",
                                        color = TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            UpdateStatus.READY -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(StatusGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Ready to install v$updateLatestVersion",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = StatusGreen,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Button(
                                        onClick = onInstallUpdate,
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                        modifier = Modifier.height(48.dp),
                                    ) {
                                        Text("Install Update", color = DarkBackground, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            UpdateStatus.ERROR -> {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Update check failed.", color = StatusRed)
                                    OutlinedButton(
                                        onClick = onCheckForUpdate,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAccent),
                                        modifier = Modifier.height(40.dp),
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val lines = markdown.lines()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            when {
                trimmed.startsWith("## ") -> Text(
                    trimmed.removePrefix("## "),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                trimmed.startsWith("# ") -> Text(
                    trimmed.removePrefix("# "),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                trimmed.startsWith("- **") || trimmed.startsWith("* **") -> {
                    val content = trimmed.removePrefix("- ").removePrefix("* ")
                        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                    Text(
                        "\u2022 $content",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Text(
                    "\u2022 ${trimmed.removePrefix("- ").removePrefix("* ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                trimmed.startsWith("|") -> {} // skip markdown tables
                trimmed.startsWith("---") -> {} // skip horizontal rules
                else -> Text(
                    trimmed.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                        .replace(Regex("`([^`]+)`"), "$1"),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun PaneTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = TextMuted,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun ServiceRow(
    name: String,
    running: Boolean,
    detail: String? = null,
    onRestart: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (running) StatusGreen else StatusRed,
                    androidx.compose.foundation.shape.CircleShape,
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        if (!running) {
            TextButton(
                onClick = onRestart,
                modifier = Modifier.height(48.dp),
            ) {
                Text("Restart", color = NeonAccent)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonAccent,
                checkedTrackColor = NeonAccent.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun DropdownSettingRow(
    label: String,
    options: List<Int>,
    selectedValue: Int,
    formatLabel: (Int) -> String,
    onValueChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) {
                Text(formatLabel(selectedValue))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceBright),
            ) {
                for (option in options) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                formatLabel(option),
                                color = if (option == selectedValue) NeonAccent else TextPrimary,
                            )
                        },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FitnessAppPicker(
    selectedPackage: String,
    fitnessApps: List<AppInfo>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = fitnessApps.find { it.packageName == selectedPackage }?.label
        ?: selectedPackage.substringAfterLast('.')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Default Fitness App", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) {
                Text(selectedLabel)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceBright),
            ) {
                if (fitnessApps.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No fitness apps installed", color = TextMuted) },
                        onClick = { expanded = false },
                    )
                } else {
                    for (app in fitnessApps) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    app.label,
                                    color = if (app.packageName == selectedPackage) NeonAccent else TextPrimary,
                                    fontWeight = if (app.packageName == selectedPackage) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onSelect(app.packageName)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
