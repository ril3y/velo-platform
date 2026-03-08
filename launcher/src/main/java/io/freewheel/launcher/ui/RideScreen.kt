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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.launcher.ui.theme.*

private const val DEFAULT_GOAL_MINUTES = 30

@Composable
fun RideScreen(
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
                // Subtle radial gradient: lighter in center where timer lives
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
            // ---- Connection status (top) ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isConnected) StatusGreen else StatusRed,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isConnected) "CONNECTED" else "CONNECTING...",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isConnected) StatusGreen else StatusRed,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ---- Primary area: POWER | TIMER | CADENCE ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                // Left: Power
                PrimaryMetric(
                    value = power,
                    unit = "W",
                    label = "POWER",
                    color = PowerGreen,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(32.dp))

                // Center: Timer + progress bar
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1.4f),
                ) {
                    // Elapsed time - dominant element
                    Text(
                        text = formatDuration(elapsedSeconds),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 64.sp,
                            lineHeight = 68.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = "ELAPSED",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    Spacer(Modifier.height(12.dp))

                    // Progress bar toward goal
                    val goalMinutes = DEFAULT_GOAL_MINUTES
                    val progress = if (goalMinutes > 0) {
                        (elapsedSeconds / 60f) / goalMinutes
                    } else {
                        0f
                    }
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress.coerceAtMost(1f),
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "progress",
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .width(220.dp)
                            .height(4.dp),
                        color = NeonAccent,
                        trackColor = SurfaceBorder,
                    )

                    Text(
                        text = "goal: ${goalMinutes}m",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = TextMuted.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.width(32.dp))

                // Right: Cadence
                PrimaryMetric(
                    value = rpm,
                    unit = "RPM",
                    label = "CADENCE",
                    color = CadenceBlue,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---- Secondary metrics strip ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SurfaceBright.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(vertical = 14.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryMetric(
                    value = "$calories",
                    label = "CAL",
                    color = SpeedOrange,
                )
                VerticalDivider()
                SecondaryMetric(
                    value = "%.1f".format(speedMph),
                    label = "MPH",
                    color = SpeedOrange,
                )
                VerticalDivider()
                SecondaryMetric(
                    value = "%.1f".format(distanceMiles),
                    label = "MI",
                    color = NeonAccent,
                )
                VerticalDivider()
                SecondaryMetric(
                    value = "LVL $resistance",
                    label = "RES",
                    color = ResistanceYellow,
                )
                VerticalDivider()
                SecondaryMetric(
                    value = if (heartRate > 0) "$heartRate" else "--",
                    label = "BPM",
                    color = if (heartRate > 0) HeartRateRed else TextMuted,
                    icon = if (heartRate > 0) Icons.Default.Favorite else null,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- STOP button ----
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HeartRateRed,
                    contentColor = TextPrimary,
                ),
                modifier = Modifier
                    .width(220.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                ),
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "STOP RIDE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---- Primary metric (large, flanking timer) ----

@Composable
private fun PrimaryMetric(
    value: Int,
    unit: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "primary_$label",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = animatedValue.toString(),
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 44.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = color,
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelLarge,
            color = color.copy(alpha = 0.7f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
    }
}

// ---- Secondary metric (compact, horizontal strip) ----

@Composable
private fun SecondaryMetric(
    value: String,
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 3.dp),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = color,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

// ---- Subtle vertical divider between secondary metrics ----

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(SurfaceBorder.copy(alpha = 0.5f)),
    )
}

// ---- Format elapsed seconds to MM:SS or H:MM:SS ----

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
