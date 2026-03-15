package io.freewheel.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.freewheel.launcher.service.ServiceStatus
import io.freewheel.launcher.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusBar(
    serviceStatus: ServiceStatus,
    wifiSsid: String,
    ramUsedMb: Long,
    ramTotalMb: Long,
    currentTime: Long,
    onSettingsClick: () -> Unit,
    onBridgeClick: () -> Unit,
    onOverlayClick: () -> Unit,
    onAllAppsClick: () -> Unit = {},
    onTaskManagerClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    val timeStr = timeFormat.format(Date(currentTime))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Clock
        Text(
            text = timeStr,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )

        Spacer(Modifier.width(24.dp))

        Spacer(Modifier.weight(1f))

        // WiFi
        if (wifiSsid.isNotBlank() && wifiSsid != "<unknown ssid>") {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "WiFi",
                tint = TextSecondary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = wifiSsid,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.width(16.dp))
        }

        // RAM
        val ramColor = when {
            ramTotalMb == 0L -> TextSecondary
            ramUsedMb * 100 / ramTotalMb > 85 -> HeartRateRed
            ramUsedMb * 100 / ramTotalMb > 70 -> StatusYellow
            else -> StatusGreen
        }
        Text(
            text = "${ramUsedMb}/${ramTotalMb}M",
            style = MaterialTheme.typography.labelSmall,
            color = ramColor,
        )

        Spacer(Modifier.width(12.dp))

        // All Apps
        IconButton(
            onClick = onAllAppsClick,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = "All Apps",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }

        // Task Manager
        IconButton(
            onClick = onTaskManagerClick,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = "Task Manager",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }

        // Settings gear
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun StatusDot(isHealthy: Boolean) {
    if (isHealthy) {
        val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .alpha(pulseAlpha)
                .background(StatusGreen)
        )
    } else {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(StatusRed)
        )
    }
}
