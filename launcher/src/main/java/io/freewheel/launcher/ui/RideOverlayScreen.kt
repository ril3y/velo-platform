package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import io.freewheel.launcher.data.MediaApp
import io.freewheel.launcher.data.Workout
import io.freewheel.launcher.data.WorkoutSegment
import io.freewheel.launcher.ui.theme.*

// ---- Glass panel styling constants ----

private val GlassPanelShape = RoundedCornerShape(26.dp)
private val GlassPanelBackground = Color.Black.copy(alpha = 0.35f)
private val GlassPanelBorder = Color.White.copy(alpha = 0.10f)
private val CyanLabel = Color(0xFF22D3EE)
private val Cyan300 = Color(0xFF22D3EE)
private val RoseButton = Color(0xFFF43F5E) // rose-500

private fun Modifier.glassPanel(hazeState: HazeState): Modifier = this
    .hazeChild(
        state = hazeState,
        style = HazeStyle(
            blurRadius = 40.dp,
            backgroundColor = Color.Black.copy(alpha = 0.35f),
            tints = emptyList(),
        ),
    )
    .background(GlassPanelBackground, GlassPanelShape)
    .border(1.dp, GlassPanelBorder, GlassPanelShape)

// ---- Format duration helper ----

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}

// ---- Segment calculation helpers ----

/**
 * Returns the index of the segment the rider is currently in,
 * based on elapsed seconds. Returns -1 if past all segments.
 */
private fun currentSegmentIndex(segments: List<WorkoutSegment>, elapsedSeconds: Int): Int {
    var accumulated = 0
    for ((index, seg) in segments.withIndex()) {
        accumulated += seg.durationSeconds
        if (elapsedSeconds < accumulated) return index
    }
    return segments.lastIndex.coerceAtLeast(0)
}

/**
 * Seconds elapsed within the current segment.
 */
private fun secondsIntoCurrentSegment(segments: List<WorkoutSegment>, elapsedSeconds: Int): Int {
    var accumulated = 0
    for (seg in segments) {
        if (elapsedSeconds < accumulated + seg.durationSeconds) {
            return elapsedSeconds - accumulated
        }
        accumulated += seg.durationSeconds
    }
    return 0
}

/**
 * Seconds remaining until the next segment starts.
 */
private fun secondsUntilNextSegment(segments: List<WorkoutSegment>, elapsedSeconds: Int): Int {
    var accumulated = 0
    for (seg in segments) {
        accumulated += seg.durationSeconds
        if (elapsedSeconds < accumulated) {
            return accumulated - elapsedSeconds
        }
    }
    return 0
}

// ---- Main composable ----

@Composable
fun RideOverlayScreen(
    workout: Workout,
    media: MediaApp?,
    // Live ride metrics
    power: Int,
    rpm: Int,
    resistance: Int,
    heartRate: Int,
    elapsedSeconds: Int,
    calories: Int,
    isConnected: Boolean,
    // Actions
    onEndRide: () -> Unit,
    onBack: () -> Unit,
    onMinimize: () -> Unit,
) {
    val segments = workout.segments
    val currentIdx = currentSegmentIndex(segments, elapsedSeconds)
    val currentSegment = segments.getOrNull(currentIdx)
    val nextSegment = segments.getOrNull(currentIdx + 1)
    val secsUntilNext = secondsUntilNextSegment(segments, elapsedSeconds)
    val totalWorkoutSeconds = segments.sumOf { it.durationSeconds }
    val hazeState = remember { HazeState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF11131F),
                        Color(0xFF08090F),
                    ),
                )
            )
            .drawBehind {
                // Radial cyan glow at top-right (matches mockup)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Cyan300.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.1f),
                        radius = size.width * 0.35f,
                    ),
                )
                // Secondary subtle diagonal sheen
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.15f),
                        radius = size.width * 0.40f,
                    ),
                )
            }
            .haze(hazeState),
    ) {
        // ---- Center: massive media app name watermark ----
        if (media != null) {
            Text(
                text = media.name,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 180.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp,
                ),
                color = Color.White.copy(alpha = 0.12f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ---- Top bar ----
        TopOverlayBar(
            media = media,
            isConnected = isConnected,
            onBack = onBack,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )

        // ---- Left side overlay panels ----
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 80.dp)
                .width(320.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Panel 1: 2x2 stat grid
            StatGridPanel(
                power = power,
                rpm = rpm,
                resistance = resistance,
                heartRate = heartRate,
                hazeState = hazeState,
            )

            // Panel 2: Upcoming segment preview
            if (nextSegment != null) {
                UpcomingSegmentPanel(
                    nextSegment = nextSegment,
                    secsUntilNext = secsUntilNext,
                    segments = segments,
                    currentIdx = currentIdx,
                    hazeState = hazeState,
                )
            }
        }

        // ---- Right side: Live effort bar ----
        LiveEffortBar(
            liveResistance = resistance,
            targetResistance = currentSegment?.resistance ?: 0,
            maxResistance = segments.maxOfOrNull { it.resistance } ?: 25,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp, top = 80.dp, bottom = 120.dp),
        )

        // ---- Bottom bar ----
        BottomOverlayBar(
            workout = workout,
            segments = segments,
            currentIdx = currentIdx,
            currentSegment = currentSegment,
            nextSegment = nextSegment,
            elapsedSeconds = elapsedSeconds,
            secsUntilNext = secsUntilNext,
            onMinimize = onMinimize,
            onEndRide = onEndRide,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

// ---- Top bar ----

@Composable
private fun TopOverlayBar(
    media: MediaApp?,
    isConnected: Boolean,
    onBack: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    blurRadius = 40.dp,
                    backgroundColor = Color.Black.copy(alpha = 0.35f),
                    tints = emptyList(),
                ),
            )
            .background(Color.Black.copy(alpha = 0.35f))
            .drawBehind {
                // Bottom border line
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: media icon + info
        if (media != null) {
            // Colored rounded square icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(media.color, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = media.iconLetter,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    ),
                    color = Color.White,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = media.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextPrimary,
                )
                Text(
                    text = "Now playing with workout overlay",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        } else {
            Text(
                text = "Workout Overlay",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TextPrimary,
            )
        }

        Spacer(Modifier.weight(1f))

        // Right: connection pill + back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Connected pill
            Row(
                modifier = Modifier
                    .background(
                        if (isConnected) StatusGreen.copy(alpha = 0.20f)
                        else StatusRed.copy(alpha = 0.20f),
                        RoundedCornerShape(20.dp),
                    )
                    .border(
                        1.dp,
                        if (isConnected) StatusGreen.copy(alpha = 0.4f)
                        else StatusRed.copy(alpha = 0.4f),
                        RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                    text = if (isConnected) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (isConnected) StatusGreen else StatusRed,
                )
            }

            // Back button
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary,
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.12f),
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Back",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

// ---- Stat grid panel ----

@Composable
private fun StatGridPanel(
    power: Int,
    rpm: Int,
    resistance: Int,
    heartRate: Int,
    hazeState: HazeState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(hazeState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Row 1: Power + Cadence
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OverlayStatTile(
                label = "POWER",
                value = "$power",
                unit = "W",
                valueColor = PowerGreen,
                modifier = Modifier.weight(1f),
            )
            OverlayStatTile(
                label = "CADENCE",
                value = "$rpm",
                unit = "RPM",
                valueColor = CadenceBlue,
                modifier = Modifier.weight(1f),
            )
        }
        // Row 2: Resistance + Heart Rate
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OverlayStatTile(
                label = "RESISTANCE",
                value = "$resistance",
                unit = "%",
                valueColor = ResistanceYellow,
                modifier = Modifier.weight(1f),
            )
            OverlayStatTile(
                label = "HEART",
                value = if (heartRate > 0) "$heartRate" else "--",
                unit = "BPM",
                valueColor = HeartRateRed,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---- Individual stat tile ----

@Composable
fun OverlayStatTile(
    label: String,
    value: String,
    unit: String,
    valueColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.8.sp,
                fontSize = 11.sp,
            ),
            color = Color.White.copy(alpha = 0.38f),
        )

        Spacer(Modifier.height(4.dp))

        // Value
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                lineHeight = 32.sp,
            ),
            color = valueColor,
        )

        // Unit
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                fontSize = 10.sp,
            ),
            color = CyanLabel.copy(alpha = 0.8f),
        )
    }
}

// ---- Upcoming segment panel ----

@Composable
private fun UpcomingSegmentPanel(
    nextSegment: WorkoutSegment,
    secsUntilNext: Int,
    segments: List<WorkoutSegment>,
    currentIdx: Int,
    hazeState: HazeState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(hazeState)
            .padding(16.dp),
    ) {
        // Header
        Text(
            text = "UPCOMING",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                fontSize = 10.sp,
            ),
            color = CyanLabel.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(8.dp))

        // Segment name
        Text(
            text = nextSegment.label,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            ),
            color = TextPrimary,
        )

        Spacer(Modifier.height(4.dp))

        // Time until + target resistance
        Text(
            text = "In ${formatDuration(secsUntilNext)} \u00B7 target resistance ${nextSegment.resistance}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        Spacer(Modifier.height(14.dp))

        // Hill preview chart
        HillPreviewChart(
            segments = segments,
            currentIndex = currentIdx,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
        )
    }
}

// ---- Hill preview chart ----

@Composable
fun HillPreviewChart(
    segments: List<WorkoutSegment>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    val maxResistance = segments.maxOfOrNull { it.resistance }?.coerceAtLeast(1) ?: 1

    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            segments.forEachIndexed { index, segment ->
                val heightFraction = segment.resistance.toFloat() / maxResistance
                val isCompleted = index < currentIndex
                val isCurrent = index == currentIndex

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(heightFraction.coerceAtLeast(0.08f))
                            .drawBehind {
                                val barColor = when {
                                    isCurrent -> NeonAccent
                                    isCompleted -> NeonAccentDim.copy(alpha = 0.5f)
                                    else -> SurfaceBright
                                }
                                drawRoundRect(
                                    color = barColor,
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                )
                            },
                    )

                    Spacer(Modifier.height(3.dp))

                    // Segment number
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (isCurrent) NeonAccent else TextMuted,
                    )
                }
            }
        }
    }
}

// ---- Bottom bar ----

@Composable
private fun BottomOverlayBar(
    workout: Workout,
    segments: List<WorkoutSegment>,
    currentIdx: Int,
    currentSegment: WorkoutSegment?,
    nextSegment: WorkoutSegment?,
    elapsedSeconds: Int,
    secsUntilNext: Int,
    onMinimize: () -> Unit,
    onEndRide: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    blurRadius = 40.dp,
                    backgroundColor = Color.Black.copy(alpha = 0.35f),
                    tints = emptyList(),
                ),
            )
            .background(Color.Black.copy(alpha = 0.35f))
            .drawBehind {
                // Top border line
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // Left side: workout info
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // WORKOUT PROGRESS label
                Text(
                    text = "WORKOUT PROGRESS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        fontSize = 10.sp,
                    ),
                    color = CyanLabel.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(4.dp))

                // Workout name
                Text(
                    text = workout.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = TextPrimary,
                )

                Spacer(Modifier.height(2.dp))

                // Elapsed + segment info
                Text(
                    text = "${formatDuration(elapsedSeconds)} elapsed \u00B7 segment ${currentIdx + 1} of ${segments.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                // Coach message
                val coachMsg = currentSegment?.message
                if (!coachMsg.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = coachMsg,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                        ),
                        color = Cyan300.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Progress strip
                WorkoutProgressStrip(
                    segments = segments,
                    elapsedSeconds = elapsedSeconds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                )

                Spacer(Modifier.height(8.dp))

                // Info strip below progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ProgressInfoChip(
                        label = "Current",
                        value = currentSegment?.label ?: "--",
                    )
                    if (nextSegment != null) {
                        ProgressInfoChip(
                            label = "Next",
                            value = nextSegment.label,
                        )
                    }
                    ProgressInfoChip(
                        label = "Target res",
                        value = "${currentSegment?.resistance ?: 0}",
                    )
                    ProgressInfoChip(
                        label = "ETA",
                        value = formatDuration(secsUntilNext),
                    )
                }
            }

            Spacer(Modifier.width(24.dp))

            // Right side: action buttons
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedButton(
                    onClick = onMinimize,
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.12f),
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Minimize",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }

                Button(
                    onClick = onEndRide,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseButton,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(26.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                    ),
                ) {
                    Text(
                        text = "End Ride",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
}

// ---- Progress info chip ----

@Composable
private fun ProgressInfoChip(
    label: String,
    value: String,
) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
            ),
            color = TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = TextPrimary,
        )
    }
}

// ---- Workout progress strip ----

@Composable
fun WorkoutProgressStrip(
    segments: List<WorkoutSegment>,
    elapsedSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = segments.sumOf { it.durationSeconds }.coerceAtLeast(1)

    // Gradient colors: cyan-300 to emerald-300 (matching mockup)
    val completedStart = Cyan300
    val completedEnd = Color(0xFF6EE7B7) // emerald-300

    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(5.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            var accumulatedSeconds = 0

            segments.forEachIndexed { index, segment ->
                val segStart = accumulatedSeconds
                val segEnd = accumulatedSeconds + segment.durationSeconds
                accumulatedSeconds = segEnd

                val segWeight = segment.durationSeconds.toFloat() / totalSeconds

                // Determine fill for this segment
                val segFill = when {
                    elapsedSeconds >= segEnd -> 1f // fully completed
                    elapsedSeconds > segStart -> {
                        (elapsedSeconds - segStart).toFloat() / segment.durationSeconds
                    }
                    else -> 0f // not started
                }

                // Color interpolation position within overall workout
                val segMidFraction = (segStart + segment.durationSeconds / 2f) / totalSeconds
                val segColor = lerp(completedStart, completedEnd, segMidFraction)

                Box(
                    modifier = Modifier
                        .weight(segWeight)
                        .fillMaxHeight()
                        .drawBehind {
                            // Filled portion
                            if (segFill > 0f) {
                                drawRoundRect(
                                    color = segColor,
                                    size = Size(size.width * segFill, size.height),
                                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                                )
                            }
                            // Segment divider
                            if (index < segments.size - 1) {
                                drawLine(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    start = Offset(size.width - 1f, 0f),
                                    end = Offset(size.width - 1f, size.height),
                                    strokeWidth = 1.5f,
                                )
                            }
                        },
                )
            }
        }
    }
}

// ---- Live effort bar ----

@Composable
private fun LiveEffortBar(
    liveResistance: Int,
    targetResistance: Int,
    maxResistance: Int,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val effectiveMax = maxResistance.coerceAtLeast(1)
    val liveFraction = (liveResistance.toFloat() / effectiveMax).coerceIn(0f, 1f)
    val targetFraction = (targetResistance.toFloat() / effectiveMax).coerceIn(0f, 1f)

    val diff = liveResistance - targetResistance
    val effortLabel: String
    val effortColor: Color

    when {
        diff >= -2 && diff <= 2 -> {
            effortLabel = "ON TARGET"
            effortColor = NeonAccent
        }
        diff > 2 -> {
            effortLabel = "HIGH"
            effortColor = NeonAccent
        }
        diff < -5 -> {
            effortLabel = "LOW"
            effortColor = HeartRateRed
        }
        else -> {
            effortLabel = "LOW"
            effortColor = ResistanceYellow
        }
    }

    Column(
        modifier = modifier
            .width(120.dp)
            .glassPanel(hazeState)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Text(
            text = "LIVE EFFORT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.8.sp,
                fontSize = 11.sp,
            ),
            color = CyanLabel.copy(alpha = 0.8f),
        )

        Spacer(Modifier.height(10.dp))

        // Vertical track
        val trackHeight = 280.dp
        val trackWidth = 36.dp
        val trackCorner = 18.dp

        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .background(
                    Color.White.copy(alpha = 0.06f),
                    RoundedCornerShape(trackCorner),
                )
                .drawBehind {
                    val trackPx = size.height
                    val trackW = size.width
                    val cornerPx = trackCorner.toPx()
                    val indicatorH = 24.dp.toPx()

                    // TARGET indicator (hollow)
                    val targetY = trackPx - (targetFraction * trackPx) - indicatorH / 2f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.40f),
                        topLeft = Offset(0f, targetY.coerceIn(0f, trackPx - indicatorH)),
                        size = Size(trackW, indicatorH),
                        cornerRadius = CornerRadius(cornerPx * 0.5f, cornerPx * 0.5f),
                        style = Stroke(2.dp.toPx()),
                    )

                    // LIVE indicator glow (larger, behind)
                    val liveY = trackPx - (liveFraction * trackPx) - indicatorH / 2f
                    val liveYClamped = liveY.coerceIn(0f, trackPx - indicatorH)
                    drawRoundRect(
                        color = NeonAccent.copy(alpha = 0.3f),
                        topLeft = Offset(-2.dp.toPx(), liveYClamped - 2.dp.toPx()),
                        size = Size(trackW + 4.dp.toPx(), indicatorH + 4.dp.toPx()),
                        cornerRadius = CornerRadius(cornerPx * 0.5f, cornerPx * 0.5f),
                    )

                    // LIVE indicator (filled)
                    drawRoundRect(
                        color = NeonAccent,
                        topLeft = Offset(0f, liveYClamped),
                        size = Size(trackW, indicatorH),
                        cornerRadius = CornerRadius(cornerPx * 0.5f, cornerPx * 0.5f),
                    )
                },
        )

        Spacer(Modifier.height(12.dp))

        // Resistance number
        Text(
            text = "$liveResistance",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                lineHeight = 32.sp,
            ),
            color = effortColor,
        )

        // Effort label
        Text(
            text = effortLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontSize = 10.sp,
            ),
            color = effortColor,
        )

        Spacer(Modifier.height(8.dp))

        // Target / Live labels
        Text(
            text = "Target: $targetResistance",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextSecondary,
        )
        Text(
            text = "Live: $liveResistance",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextSecondary,
        )
    }
}

// Simple color interpolation
private fun lerp(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}
