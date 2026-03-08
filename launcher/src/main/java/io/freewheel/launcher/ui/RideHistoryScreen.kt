package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.freewheel.launcher.data.RideRecord
import io.freewheel.launcher.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(12.dp)

@Composable
fun RideHistoryScreen(
    rides: List<RideRecord>,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                    )
                }
                Text(
                    text = "Ride History",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Summary stats
            if (rides.isNotEmpty()) {
                RideSummaryBar(rides)
            }

            // Ride list
            if (rides.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No rides yet -- start pedaling!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rides, key = { it.id }) { ride ->
                        RideHistoryCard(ride, onDelete = { onDelete(ride.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RideSummaryBar(rides: List<RideRecord>) {
    val totalRides = rides.size
    val totalSeconds = rides.sumOf { it.durationSeconds }
    val totalCalories = rides.sumOf { it.calories }
    val totalDistance = rides.sumOf { it.distanceMiles.toDouble() }.toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBright)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SummaryStat("RIDES", totalRides.toString())
        SummaryStat("TIME", formatTotalTime(totalSeconds))
        SummaryStat("CALORIES", "%,d".format(totalCalories))
        SummaryStat("DISTANCE", "%.1f mi".format(totalDistance))
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = NeonAccent,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

@Composable
private fun RideHistoryCard(ride: RideRecord, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    val date = Date(ride.startTime)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBright, CardShape)
            .drawBehind {
                val barColor = when (ride.source) {
                    "launcher" -> PowerGreen
                    "jrny_import" -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
                    else -> androidx.compose.ui.graphics.Color(0xFF22D3EE)
                }
                drawRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height),
                )
            }
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Date/time column
        Column(modifier = Modifier.width(100.dp)) {
            Text(
                text = dateFormat.format(date),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Text(
                text = timeFormat.format(date),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            if (ride.source != "launcher") {
                Text(
                    text = ride.sourceLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
                )
            }
        }

        // Stats
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CardStat(formatDurationShort(ride.durationSeconds), TextSecondary)
            CardStat("${ride.calories} cal", NeonAccentDim)
            CardStat("${ride.avgRpm} rpm", CadenceBlue)
            CardStat("${ride.avgPowerWatts}W", PowerGreen)
            CardStat("${ride.maxPowerWatts}W pk", PowerGreen.copy(alpha = 0.7f))
            CardStat("%.1f mi".format(ride.distanceMiles), NeonAccent)
            CardStat("%.1f mph".format(ride.avgSpeedMph), SpeedOrange)
        }

        // Delete button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete ride",
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CardStat(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = color,
    )
}

private fun formatDurationShort(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatTotalTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
