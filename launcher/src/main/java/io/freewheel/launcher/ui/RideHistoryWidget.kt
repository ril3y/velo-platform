package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.freewheel.launcher.data.RideRecord
import io.freewheel.launcher.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val RideCardShape = RoundedCornerShape(12.dp)

@Composable
fun RideHistoryWidget(
    rides: List<RideRecord>,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (rides.isEmpty()) {
            // Placeholder when no rides
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceBright, RideCardShape)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "No rides yet -- start pedaling!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "RECENT RIDES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
                    ),
                    color = TextMuted,
                    modifier = Modifier.padding(start = 4.dp),
                )
                if (onViewAll != null) {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = NeonAccent,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable { onViewAll() },
                    )
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rides.take(5)) { ride ->
                    RideCard(ride)
                }
            }
        }
    }
}

@Composable
private fun RideCard(ride: RideRecord) {
    val glowBrush = Brush.verticalGradient(
        listOf(NeonGlow, PowerGreen.copy(alpha = 0.6f), NeonGlow)
    )
    Row(
        modifier = Modifier
            .background(SurfaceBright, RideCardShape)
            .drawBehind {
                // Accent glow stripe
                drawRoundRect(
                    brush = glowBrush,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Date column
        Column {
            Text(
                text = formatRideDate(ride.startTime),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Text(
                text = formatRideTime(ride.startTime),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }

        // Compact stat line with monospace numbers
        Text(
            text = "${ride.durationSeconds / 60}m",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = TextSecondary,
        )
        Text(
            text = "\u00B7",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        Text(
            text = "${ride.calories}cal",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = NeonAccentDim,
        )
        Text(
            text = "\u00B7",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        Text(
            text = "${ride.avgRpm}rpm",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = CadenceBlue,
        )
        Text(
            text = "\u00B7",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        Text(
            text = "${ride.avgPowerWatts}W",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = PowerGreen,
        )
    }
}


private fun formatRideDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val ride = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        now.get(Calendar.DAY_OF_YEAR) == ride.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == ride.get(Calendar.YEAR) -> "Today"
        now.get(Calendar.DAY_OF_YEAR) - ride.get(Calendar.DAY_OF_YEAR) == 1 &&
            now.get(Calendar.YEAR) == ride.get(Calendar.YEAR) -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
    }
}

private fun formatRideTime(timestamp: Long): String {
    return SimpleDateFormat("h:mm a", Locale.US).format(Date(timestamp))
}
