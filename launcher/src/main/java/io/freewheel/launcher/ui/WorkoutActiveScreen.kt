package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.launcher.ui.theme.*

@Composable
fun WorkoutActiveScreen(
    power: Int,
    rpm: Int,
    elapsed: Int,
    workoutName: String?,
    onReturnToMedia: () -> Unit,
    onEndWorkout: () -> Unit,
) {
    val m = elapsed / 60
    val s = elapsed % 60
    val timeStr = "%d:%02d".format(m, s)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "WORKOUT IN PROGRESS",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
                color = NeonAccent,
            )

            if (workoutName != null) {
                Text(
                    text = workoutName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )
            }

            // Live stats
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                StatValue(timeStr, "ELAPSED")
                StatValue("$power", "WATTS")
                StatValue("$rpm", "RPM")
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onReturnToMedia,
                colors = ButtonDefaults.buttonColors(containerColor = NeonAccent),
                modifier = Modifier
                    .width(280.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "Return to Media",
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            OutlinedButton(
                onClick = onEndWorkout,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeartRateRed),
                modifier = Modifier
                    .width(280.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "End Workout",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StatValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
    }
}
