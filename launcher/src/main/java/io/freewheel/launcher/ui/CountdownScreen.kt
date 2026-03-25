package io.freewheel.launcher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.launcher.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Full-screen countdown (5, 4, 3, 2, 1, GO!) before a workout starts.
 * Calls [onComplete] when the countdown finishes.
 */
@Composable
fun CountdownScreen(
    workoutName: String? = null,
    onComplete: () -> Unit,
) {
    var count by remember { mutableIntStateOf(5) }
    val isGo = count <= 0

    // Countdown timer
    LaunchedEffect(Unit) {
        for (i in 5 downTo 1) {
            count = i
            delay(1000)
        }
        count = 0
        delay(600) // brief "GO!" display
        onComplete()
    }

    // Scale animation: each number pops in large then shrinks
    val scale by animateFloatAsState(
        targetValue = if (isGo) 1.5f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
        label = "countScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (workoutName != null) {
                Text(
                    text = workoutName.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        letterSpacing = 3.sp,
                    ),
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 32.dp),
                )
            }

            Text(
                text = if (isGo) "GO!" else "$count",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = if (isGo) 96.sp else 120.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = when {
                    isGo -> NeonAccent
                    count <= 2 -> NeonAccent
                    else -> TextPrimary
                },
                modifier = Modifier.scale(scale),
            )

            Text(
                text = if (isGo) "" else "GET READY",
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 4.sp,
                ),
                color = TextSecondary,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
