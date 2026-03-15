package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.freewheel.launcher.ui.theme.*

data class OtaFlashState(
    val currentFirmwareVersion: String = "Unknown",
    val selectedFilePath: String? = null,
    val selectedFileName: String? = null,
    val isFlashing: Boolean = false,
    val phase: Int = 0,
    val phaseName: String = "Idle",
    val blockCurrent: Int = 0,
    val blockTotal: Int = 0,
    val error: String? = null,
    val completed: Boolean = false,
    val success: Boolean = false,
)

@Composable
fun OtaFlashPane(
    state: OtaFlashState,
    onPickFile: () -> Unit,
    onStartFlash: () -> Unit,
    onCancelFlash: () -> Unit,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "UCB FIRMWARE",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = TextMuted,
        )

        // Current firmware version
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Current firmware", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(state.currentFirmwareVersion, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }

        // File picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onPickFile,
                enabled = !state.isFlashing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAccent),
            ) {
                Text("Select Firmware File")
            }
            if (state.selectedFileName != null) {
                Text(
                    text = state.selectedFileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        // Flash button
        if (!state.isFlashing && !state.completed) {
            Button(
                onClick = { showConfirmDialog = true },
                enabled = state.selectedFilePath != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HeartRateRed,
                    contentColor = TextPrimary,
                ),
                modifier = Modifier.height(48.dp),
            ) {
                Text("Flash Firmware", fontWeight = FontWeight.Bold)
            }
        }

        // Progress display
        if (state.isFlashing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.phaseName,
                    style = MaterialTheme.typography.titleSmall,
                    color = NeonAccent,
                )

                if (state.blockTotal > 0) {
                    val progress = state.blockCurrent.toFloat() / state.blockTotal
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = NeonAccent,
                        trackColor = SurfaceBorder,
                    )
                    Text(
                        text = "Block ${state.blockCurrent} / ${state.blockTotal}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = NeonAccent,
                        trackColor = SurfaceBorder,
                    )
                }

                OutlinedButton(
                    onClick = onCancelFlash,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HeartRateRed),
                ) {
                    Text("Cancel")
                }
            }
        }

        // Result display
        if (state.completed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (state.success) StatusGreen.copy(alpha = 0.1f) else StatusRed.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(16.dp),
            ) {
                Text(
                    text = if (state.success) "Flash Complete" else "Flash Failed",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (state.success) StatusGreen else StatusRed,
                )
                if (state.success) {
                    Text(
                        text = "UCB is rebooting. Please wait 30 seconds, then power cycle the bike.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
            }
        }

        // Warning
        if (!state.isFlashing && !state.completed) {
            Text(
                text = "WARNING: Flashing erases the current UCB firmware. If flash fails after erase, the UCB will be bricked. Only proceed with a known-good firmware file.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusYellow,
            )
        }
    }

    // Confirmation dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text("Confirm Firmware Flash", color = TextPrimary)
            },
            text = {
                Text(
                    "This will update UCB firmware. Do not power off the bike during the flash.\n\n" +
                        "File: ${state.selectedFileName ?: "none"}\n\n" +
                        "This operation CANNOT be undone once erase begins.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onStartFlash()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HeartRateRed),
                ) {
                    Text("FLASH", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceBright,
        )
    }
}
