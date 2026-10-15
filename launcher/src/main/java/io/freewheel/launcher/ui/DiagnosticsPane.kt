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
import io.freewheel.ucb.BridgeDiagnostics.LinkState

data class DiagnosticsState(
    /** Bridge bound / port open / valid frames within 5s — see BridgeDiagnostics.classify. */
    val linkState: LinkState = LinkState.BRIDGE_NOT_RUNNING,
    /** False when the installed FreewheelBridge predates getDiagnostics(). */
    val diagnosticsAvailable: Boolean = false,
    val portDev: String? = null,
    val baud: Int = 0,
    val bytesRead: Long = 0,
    val framesOk: Long = 0,
    val crcErrors: Long = 0,
    val lastFrameAgeMs: Long = -1,
    val holders: String = "none",
    val sensorTestSecondsLeft: Int = 0,
    val sensorTestMessage: String? = null,
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

fun linkStateColor(state: LinkState) = when (state) {
    LinkState.BIKE_CONNECTED -> StatusGreen
    LinkState.PORT_OPEN_NO_DATA, LinkState.PORT_CLOSED -> StatusYellow
    LinkState.BRIDGE_NOT_RUNNING -> StatusRed
}

@Composable
fun DiagnosticsPane(
    state: DiagnosticsState,
    onToggleRawMonitor: (Boolean) -> Unit,
    onStartSensorTest: () -> Unit = {},
    onStopSensorTest: () -> Unit = {},
) {
    // Never leave a test workout running after the user navigates away from this pane
    val stopTest by rememberUpdatedState(onStopSensorTest)
    DisposableEffect(Unit) {
        onDispose { stopTest() }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Link status: only "Bike connected" when valid frames arrived in the last 5s
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(linkStateColor(state.linkState), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.linkState.label,
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

        // Serial link counters from the bridge
        if (state.diagnosticsAvailable) {
            val age = when {
                state.lastFrameAgeMs < 0 -> "never"
                state.lastFrameAgeMs < 1000 -> "${state.lastFrameAgeMs} ms ago"
                else -> "${state.lastFrameAgeMs / 1000} s ago"
            }
            Text(
                text = "Port ${state.portDev ?: "?"} @ ${state.baud}  \u2022  bytes ${state.bytesRead}" +
                    "  \u2022  frames ${state.framesOk}  \u2022  CRC errors ${state.crcErrors}" +
                    "  \u2022  last frame $age",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = TextSecondary,
            )
            if (state.holders != "none") {
                Text(
                    text = "Serial port also held by: ${state.holders}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusYellow,
                )
            }
        } else if (state.linkState != LinkState.BRIDGE_NOT_RUNNING) {
            Text(
                text = "Installed FreewheelBridge is too old to report link diagnostics \u2013 update it.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        // 30s sensor test: starts a workout through the normal API so the bike streams data
        Row(verticalAlignment = Alignment.CenterVertically) {
            val testing = state.sensorTestSecondsLeft > 0
            OutlinedButton(
                onClick = { if (testing) onStopSensorTest() else onStartSensorTest() },
                enabled = testing || state.linkState != LinkState.BRIDGE_NOT_RUNNING,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAccent),
                modifier = Modifier.height(48.dp),
            ) {
                Text(if (testing) "Stop test (${state.sensorTestSecondsLeft}s)" else "Test sensors (30s)")
            }
            if (state.sensorTestMessage != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = state.sensorTestMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
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
