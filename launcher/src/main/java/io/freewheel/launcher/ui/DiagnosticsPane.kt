package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.launcher.ui.theme.*

data class DiagnosticsState(
    val connected: Boolean = false,
    val firmwareState: Int = -1,
    val firmwareStateName: String = "Unknown",
    val resistance: Int = 0,
    val rpm: Int = 0,
    val tilt: Int = 0,
    val power: Float = 0f,
    val crankRevCount: Long = 0,
    val crankEventTime: Int = 0,
    val heartRate: Int = 0,
    val hrmDeviceName: String? = null,
    val rawFrames: List<String> = emptyList(),
    val rawMonitorEnabled: Boolean = false,
)

@Composable
fun DiagnosticsPane(
    state: DiagnosticsState,
    onToggleRawMonitor: (Boolean) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Connection status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        if (state.connected) StatusGreen else StatusRed,
                        CircleShape,
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.connected) "UCB Connected" else "UCB Disconnected",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "FW: ${state.firmwareStateName} (${state.firmwareState})",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        // Sensor dashboard
        Text(
            text = "SENSORS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = TextMuted,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SensorCard("RPM", "${state.rpm}", CadenceBlue, Modifier.weight(1f))
            SensorCard("Resistance", "${state.resistance}", ResistanceYellow, Modifier.weight(1f))
            SensorCard("Power", String.format("%.0fW", state.power), PowerGreen, Modifier.weight(1f))
            SensorCard("Tilt", "${state.tilt}", SpeedOrange, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SensorCard("Crank Rev", "${state.crankRevCount}", TextSecondary, Modifier.weight(1f))
            SensorCard("Crank Time", "${state.crankEventTime}", TextSecondary, Modifier.weight(1f))
            SensorCard(
                "Heart Rate",
                if (state.heartRate > 0) "${state.heartRate} BPM" else "--",
                HeartRateRed,
                Modifier.weight(1f),
            )
            if (state.hrmDeviceName != null) {
                SensorCard("HRM", state.hrmDeviceName, TextSecondary, Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        // Raw frame monitor
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "RAW UCB MONITOR",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = TextMuted,
            )
            Switch(
                checked = state.rawMonitorEnabled,
                onCheckedChange = onToggleRawMonitor,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonAccent,
                    checkedTrackColor = NeonAccent.copy(alpha = 0.3f),
                ),
            )
        }

        if (state.rawMonitorEnabled) {
            val listState = rememberLazyListState()
            LaunchedEffect(state.rawFrames.size) {
                if (state.rawFrames.isNotEmpty()) {
                    listState.animateScrollToItem(state.rawFrames.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(SurfaceDark, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                items(state.rawFrames) { frame ->
                    Text(
                        text = frame,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = if (frame.startsWith("TX")) NeonAccent else CadenceBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorCard(
    label: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = accentColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}
