package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.freewheel.launcher.ui.theme.*

data class CalibrationState(
    val isActive: Boolean = false,
    val currentStep: Int = 0,
    val totalSteps: Int = 3,
    val instruction: String = "",
    val completed: Boolean = false,
    val success: Boolean = false,
    val liveResistance: Int = 0, // current knob position from sensor (0-100)
)

@Composable
fun CalibrationPane(
    state: CalibrationState,
    onStartCalibration: () -> Unit,
    onConfirmStep: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "RESISTANCE CALIBRATION",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = TextMuted,
        )

        Text(
            text = "Calibrate the resistance knob for accurate resistance levels. " +
                "3 steps: turn fully left (min), fully right (max), then halfway.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        if (!state.isActive && !state.completed) {
            // Start button
            Button(
                onClick = onStartCalibration,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonAccent,
                    contentColor = DarkBackground,
                ),
                modifier = Modifier.height(48.dp),
            ) {
                Text("Start Calibration", fontWeight = FontWeight.Bold)
            }
        }

        if (state.isActive) {
            // Progress indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (step in 0 until state.totalSteps) {
                    val isComplete = step < state.currentStep
                    val isCurrent = step == state.currentStep
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                when {
                                    isComplete -> StatusGreen
                                    isCurrent -> NeonAccent
                                    else -> SurfaceBorder
                                },
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${step + 1}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isComplete || isCurrent) DarkBackground else TextMuted,
                        )
                    }
                    if (step < state.totalSteps - 1) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(
                                    if (step < state.currentStep) StatusGreen else SurfaceBorder,
                                ),
                        )
                    }
                }
            }

            // Instruction card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Step ${state.currentStep + 1} of ${state.totalSteps}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonAccent,
                )

                Text(
                    text = state.instruction,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )

                // Live resistance indicator — always shown, highlighted on step 3
                val isHalfwayStep = state.currentStep == 2
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Large number
                    Text(
                        text = "${state.liveResistance}",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        ),
                        color = when {
                            isHalfwayStep && state.liveResistance in 45..55 -> StatusGreen
                            isHalfwayStep -> NeonAccent
                            else -> TextSecondary
                        },
                    )
                    Text(
                        text = if (isHalfwayStep) "Turn to 50" else "Current position",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isHalfwayStep) NeonAccent else TextMuted,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Progress bar showing 0-100
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(12.dp)
                            .background(SurfaceBorder, RoundedCornerShape(6.dp)),
                    ) {
                        // Fill
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (state.liveResistance / 100f).coerceIn(0f, 1f))
                                .background(
                                    when {
                                        isHalfwayStep && state.liveResistance in 45..55 -> StatusGreen
                                        isHalfwayStep -> NeonAccent
                                        else -> TextSecondary
                                    },
                                    RoundedCornerShape(6.dp),
                                ),
                        )
                        // Center mark (50%) — shown during halfway step
                        if (isHalfwayStep) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(StatusGreen),
                            )
                        }
                    }

                    if (isHalfwayStep && state.liveResistance in 45..55) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "✓ Good — press Confirm",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = StatusGreen,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HeartRateRed),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onConfirmStep,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonAccent,
                            contentColor = DarkBackground,
                        ),
                    ) {
                        Text("Confirm", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Result
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
                    text = if (state.success) "Calibration Complete" else "Calibration Cancelled",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (state.success) StatusGreen else StatusRed,
                )
                if (state.success) {
                    Text(
                        text = "Resistance knob is now calibrated. Values will take effect immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onStartCalibration,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAccent),
            ) {
                Text("Recalibrate")
            }
        }
    }
}
