package io.freewheel.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.launcher.ui.theme.*

@Composable
fun FreeRideScreen(
    power: Int,
    rpm: Int,
    resistance: Int,
    calories: Int,
    elapsedSeconds: Int,
    speedMph: Float,
    distanceMiles: Float,
    heartRate: Int,
    isConnected: Boolean,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SurfaceBright.copy(alpha = 0.3f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height * 0.38f),
                        radius = size.width * 0.45f,
                    ),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Connection status — only show when not yet connected
            if (!isConnected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(StatusRed, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "CONNECTING...",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusRed,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Primary: POWER | TIMER | CADENCE
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PrimaryMetric(power, "W", "POWER", PowerGreen, Modifier.weight(1f))
                Spacer(Modifier.width(32.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1.4f),
                ) {
                    Text(
                        text = formatDuration(elapsedSeconds),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 96.sp, lineHeight = 100.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text("ELAPSED", style = MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp), color = TextMuted)

                    Spacer(Modifier.height(12.dp))

                    val progress by animateFloatAsState(
                        targetValue = (elapsedSeconds / 60f / 30f).coerceAtMost(1f),
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "progress",
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.width(320.dp).height(6.dp),
                        color = NeonAccent,
                        trackColor = SurfaceBorder,
                    )
                    Text(
                        "goal: 30m",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = TextMuted.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.width(32.dp))
                PrimaryMetric(rpm, "RPM", "CADENCE", CadenceBlue, Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // Secondary metrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceBright.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(vertical = 14.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryMetric("$calories", "CAL", SpeedOrange)
                MetricDivider()
                SecondaryMetric("%.1f".format(speedMph), "MPH", SpeedOrange)
                MetricDivider()
                SecondaryMetric("%.1f".format(distanceMiles), "MI", NeonAccent)
                MetricDivider()
                SecondaryMetric("LVL $resistance", "RES", ResistanceYellow)
                MetricDivider()
                SecondaryMetric(
                    if (heartRate > 0) "$heartRate" else "--", "BPM",
                    if (heartRate > 0) HeartRateRed else TextMuted,
                    if (heartRate > 0) Icons.Default.Favorite else null,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Stop button
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HeartRateRed, contentColor = TextPrimary,
                ),
                modifier = Modifier.width(280.dp).height(60.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "END WORKOUT",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PrimaryMetric(value: Int, unit: String, label: String, color: Color, modifier: Modifier) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "primary_$label",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            "$animated",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 72.sp, lineHeight = 76.sp, fontWeight = FontWeight.Bold,
            ),
            color = color,
        )
        Text(unit, style = MaterialTheme.typography.labelLarge.copy(fontSize = 20.sp), color = color.copy(alpha = 0.7f))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp), color = TextMuted)
    }
}

@Composable
private fun SecondaryMetric(value: String, label: String, color: Color, icon: ImageVector? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp).padding(end = 4.dp))
            }
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 32.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold,
                ),
                color = color,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp), color = TextMuted)
    }
}

@Composable
private fun MetricDivider() {
    Box(modifier = Modifier.width(1.dp).height(28.dp).background(SurfaceBorder.copy(alpha = 0.5f)))
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
