package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.freewheel.launcher.data.RunningApp
import io.freewheel.launcher.ui.theme.*

@Composable
fun TaskManager(
    runningApps: List<RunningApp>,
    ramUsedMb: Long,
    ramTotalMb: Long,
    onKill: (String) -> Unit,
    onKillAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground.copy(alpha = 0.97f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TASK MANAGER",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Spacer(Modifier.weight(1f))

                // RAM bar
                val pct = if (ramTotalMb > 0) (ramUsedMb * 100 / ramTotalMb).toInt() else 0
                val ramColor = when {
                    pct > 85 -> HeartRateRed
                    pct > 70 -> StatusYellow
                    else -> StatusGreen
                }
                Text(
                    text = "RAM: ${ramUsedMb}M / ${ramTotalMb}M ($pct%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ramColor,
                )
                Spacer(Modifier.width(16.dp))

                TextButton(onClick = onKillAll) {
                    Icon(Icons.Default.DeleteSweep, null, tint = HeartRateRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kill All", color = HeartRateRed)
                }

                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Process list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(runningApps) { app ->
                    ProcessRow(app, onKill = { onKill(app.packageName) })
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(
    app: RunningApp,
    onKill: () -> Unit,
) {
    val isProtected = app.packageName == "io.freewheel.launcher" ||
        app.packageName == "io.freewheel.bridge"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBright, MaterialTheme.shapes.small)
            .border(1.dp, SurfaceBorder, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Text(
            text = "${app.memoryMb}MB",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (!isProtected) {
            TextButton(onClick = onKill) {
                Text("Kill", color = HeartRateRed, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text(
                text = "protected",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
