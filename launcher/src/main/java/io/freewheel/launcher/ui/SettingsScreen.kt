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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.freewheel.launcher.service.ServiceStatus
import io.freewheel.launcher.ui.theme.*

private enum class SettingsCategory(val label: String) {
    SERVICES("Services"),
    RIDE_DATA("Ride Data"),
    DISPLAY("Display"),
    HOME_TILES("Home Tiles"),
    SYSTEM("System"),
    ABOUT("About"),
}

@Composable
fun SettingsScreen(
    serviceStatus: ServiceStatus,
    onBack: () -> Unit,
    onRestartBridge: () -> Unit,
    onRestartOverlay: () -> Unit,
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
    autoRestartOverlay: Boolean = true,
    onAutoRestartBridgeChange: (Boolean) -> Unit = {},
    onAutoRestartOverlayChange: (Boolean) -> Unit = {},
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
                    .background(SurfaceBright, RoundedCornerShape(12.dp))
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (selectedCategory) {
                    SettingsCategory.SERVICES -> {
                        PaneTitle("Services")
                        ServiceRow(
                            name = "SerialBridge (TCP:9999)",
                            running = serviceStatus.serialBridgeRunning,
                            detail = if (serviceStatus.serialBridgeTcpAlive) "TCP alive" else "TCP down",
                            onRestart = onRestartBridge,
                        )
                        ToggleRow(
                            label = "Auto-restart SerialBridge",
                            checked = autoRestartBridge,
                            onCheckedChange = onAutoRestartBridgeChange,
                        )
                        Spacer(Modifier.height(8.dp))
                        ServiceRow(
                            name = "Jailbreak Overlay",
                            running = serviceStatus.overlayRunning,
                            onRestart = onRestartOverlay,
                        )
                        ToggleRow(
                            label = "Auto-restart Overlay",
                            checked = autoRestartOverlay,
                            onCheckedChange = onAutoRestartOverlayChange,
                        )
                    }
                    SettingsCategory.RIDE_DATA -> {
                        PaneTitle("Ride Data")
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
                        InfoRow("VeloLauncher", "1.0.0")
                        InfoRow("Package", "io.freewheel.launcher")
                        InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        InfoRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    }
                }
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
