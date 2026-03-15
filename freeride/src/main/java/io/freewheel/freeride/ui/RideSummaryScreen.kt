package io.freewheel.freeride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.freeride.RideSummary
import io.freewheel.freeride.ui.theme.*

@Composable
fun RideSummaryScreen(
    summary: RideSummary,
    onDone: () -> Unit,
) {
    val h = summary.durationSeconds / 3600
    val m = (summary.durationSeconds % 3600) / 60
    val s = summary.durationSeconds % 60
    val timeStr = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    val saved = summary.durationSeconds >= 60

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(PowerGreen.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.3f),
                        radius = size.width * 0.4f,
                    ),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 80.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Text(
                if (saved) "RIDE COMPLETE" else "RIDE ENDED",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 4.sp, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                ),
                color = if (saved) PowerGreen else TextMuted,
            )
            Spacer(Modifier.height(4.dp))
            if (summary.workoutName != null) {
                Text(
                    summary.workoutName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                timeStr,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                color = TextPrimary,
            )
            if (saved) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Saved to ride history",
                    style = MaterialTheme.typography.bodySmall,
                    color = PowerGreen.copy(alpha = 0.7f),
                )
            } else {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Ride under 1 minute — not saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Stats grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceBright.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryStatColumn(
                    listOf(
                        SummaryStat("AVG POWER", "${summary.avgPower}", "W", PowerGreen),
                        SummaryStat("MAX POWER", "${summary.maxPower}", "W", PowerGreen.copy(alpha = 0.7f)),
                        SummaryStat("CALORIES", "${summary.calories}", "CAL", SpeedOrange),
                    ),
                    Modifier.weight(1f),
                )
                Box(Modifier.width(1.dp).height(160.dp).background(SurfaceBorder.copy(alpha = 0.5f)))
                SummaryStatColumn(
                    listOf(
                        SummaryStat("AVG CADENCE", "${summary.avgRpm}", "RPM", CadenceBlue),
                        SummaryStat("AVG RESISTANCE", "${summary.avgResistance}", "LVL", ResistanceYellow),
                        SummaryStat("AVG HEART RATE", if (summary.avgHeartRate > 0) "${summary.avgHeartRate}" else "--", "BPM", HeartRateRed),
                    ),
                    Modifier.weight(1f),
                )
                Box(Modifier.width(1.dp).height(160.dp).background(SurfaceBorder.copy(alpha = 0.5f)))
                SummaryStatColumn(
                    listOf(
                        SummaryStat("DISTANCE", "%.1f".format(summary.distanceMiles), "MI", NeonAccent),
                        SummaryStat("AVG SPEED", "%.1f".format(summary.avgSpeedMph), "MPH", SpeedOrange),
                        SummaryStat("DURATION", timeStr, "", TextPrimary),
                    ),
                    Modifier.weight(1f),
                )
            }

            Spacer(Modifier.weight(1f))

            // Done button
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (saved) PowerGreen else SurfaceBright,
                    contentColor = if (saved) DarkBackground else TextPrimary,
                ),
                modifier = Modifier.width(280.dp).height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "DONE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                    ),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private data class SummaryStat(val label: String, val value: String, val unit: String, val color: Color)

@Composable
private fun SummaryStatColumn(stats: List<SummaryStat>, modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (stat in stats) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stat.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium, fontSize = 9.sp,
                    ),
                    color = TextMuted,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        stat.value,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        ),
                        color = stat.color,
                    )
                    if (stat.unit.isNotEmpty()) {
                        Text(
                            " ${stat.unit}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = stat.color.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
