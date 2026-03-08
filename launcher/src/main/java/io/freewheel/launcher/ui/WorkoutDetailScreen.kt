package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.launcher.data.MediaApp
import io.freewheel.launcher.data.Workout
import io.freewheel.launcher.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CyanLabel = Color(0xFF22D3EE)
private val EmeraldCta = Color(0xFF34D399)
private val CardDarkBg = Color(0xFF202544)
private val PanelBg = Color(0xFF202544)
private val Cyan300 = Color(0xFF22D3EE)
private val Emerald400 = Color(0xFF34D399)
private val SparkBarCyan = Color(0xFF22D3EE)
private val SparkBarEmerald = Color(0xFF34D399)

// Reuse gradient palettes from picker
private val DetailGradients = listOf(
    listOf(Color(0xFF7C3AED), Color(0xFFA855F7), Color(0xFF06B6D4)),
    listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF06B6D4)),
    listOf(Color(0xFFF97316), Color(0xFFFB923C), Color(0xFFE11D48)),
    listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF6366F1)),
)

@Composable
fun WorkoutDetailScreen(
    workout: Workout,
    mediaApps: List<MediaApp>,
    selectedMedia: MediaApp?,
    onMediaSelect: (MediaApp) -> Unit,
    onStartWithMedia: () -> Unit,
    onStartStatsOnly: () -> Unit,
    onBack: () -> Unit,
    ramUsedMb: Long,
    ramTotalMb: Long,
    currentTime: Long,
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    val timeStr = timeFormat.format(Date(currentTime))

    // Pick gradient based on workout id hash
    val gradientIndex = (workout.id.hashCode() and 0x7FFFFFFF) % DetailGradients.size
    val gradientColors = DetailGradients[gradientIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BackgroundCenter, BackgroundEdge),
                )
            ),
    ) {
        // ── Top bar ──
        DetailTopBar(
            timeStr = timeStr,
            ramUsedMb = ramUsedMb,
            ramTotalMb = ramTotalMb,
            onBack = onBack,
        )

        // ── Main content: 2-column layout (5 / 7 ratio) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── LEFT COLUMN (5/12) ── Workout detail card
            WorkoutDetailCard(
                workout = workout,
                gradientColors = gradientColors,
                onGo = {
                    if (selectedMedia != null) onStartWithMedia() else onStartStatsOnly()
                },
                onBack = onBack,
                modifier = Modifier
                    .weight(5f)
                    .fillMaxHeight(),
            )

            // ── RIGHT COLUMN (7/12) ──
            Column(
                modifier = Modifier
                    .weight(7f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Media selection section
                MediaSelectionSection(
                    mediaApps = mediaApps,
                    selectedMedia = selectedMedia,
                    onMediaSelect = onMediaSelect,
                    onStartWithMedia = onStartWithMedia,
                    onStartStatsOnly = onStartStatsOnly,
                    hasOptionalMedia = workout.optionalMedia,
                )

                // Bottom row: segments + preview graph side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Upcoming Segments card
                    UpcomingSegmentsCard(
                        segments = workout.segments,
                        modifier = Modifier
                            .weight(1f)
                            .height(240.dp),
                    )

                    // Preview Graph card
                    PreviewGraphCard(
                        segments = workout.segments,
                        modifier = Modifier
                            .weight(1f)
                            .height(240.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    timeStr: String,
    ramUsedMb: Long,
    ramTotalMb: Long,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeStr,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )

        Spacer(Modifier.width(32.dp))

        // Nav
        DetailNavItem("Home", false, onBack)
        Spacer(Modifier.width(24.dp))
        DetailNavItem("Workouts", true, {})
        Spacer(Modifier.width(24.dp))
        DetailNavItem("Media", false, {})

        Spacer(Modifier.weight(1f))

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
    }
}

@Composable
private fun DetailNavItem(label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) NeonAccent else TextMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = 1.sp,
            ),
            color = color,
        )
        if (active) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(NeonAccent, RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun WorkoutDetailCard(
    workout: Workout,
    gradientColors: List<Color>,
    onGo: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(30.dp)

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(PanelBg, cardShape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Hero gradient area ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.linearGradient(
                        colors = gradientColors.map { it.copy(alpha = 0.95f) },
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    )
                )
                .drawBehind {
                    // Radial white highlight (top-left glow like the mockup)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                            center = Offset(0f, 0f),
                            radius = size.maxDimension * 0.7f,
                        ),
                        radius = size.maxDimension * 0.7f,
                        center = Offset(0f, 0f),
                    )
                }
                .padding(20.dp),
        ) {
            // Circle icon top-left
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = workout.type.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = Color.White,
                )
            }

            // Duration pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "${workout.durationMinutes} MIN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    ),
                    color = Color.White,
                )
            }

            // Back button (small pill, top-right area below duration)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onBack() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "Back",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }

        // ── Info section ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardDarkBg)
                .padding(20.dp),
        ) {
            Text(
                text = workout.name,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                ),
                color = TextPrimary,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = workout.coach.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.5.sp,
                    fontSize = 10.sp,
                ),
                color = TextMuted,
            )

            Spacer(Modifier.height(16.dp))

            // Entertainment section
            Text(
                text = "ENTERTAINMENT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.5.sp,
                    fontSize = 10.sp,
                ),
                color = Cyan300.copy(alpha = 0.70f),
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = if (workout.optionalMedia) "Media optional — watch while you ride" else "Stats focused — pure performance",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            Spacer(Modifier.height(16.dp))

            // Sparkline
            WorkoutSparkline(
                segments = workout.segments,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Description
            Text(
                text = workout.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(20.dp))

            // Go button
            Button(
                onClick = onGo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald400,
                    contentColor = Color(0xFF0F172A),
                ),
            ) {
                Text(
                    text = "Go",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontSize = 17.sp,
                    ),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Preview button (outlined)
            OutlinedButton(
                onClick = { /* preview */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary,
                ),
            ) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MediaSelectionSection(
    mediaApps: List<MediaApp>,
    selectedMedia: MediaApp?,
    onMediaSelect: (MediaApp) -> Unit,
    onStartWithMedia: () -> Unit,
    onStartStatsOnly: () -> Unit,
    hasOptionalMedia: Boolean,
) {
    val sectionShape = RoundedCornerShape(30.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), sectionShape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), sectionShape)
            .padding(24.dp),
    ) {
        // Section header
        Text(
            text = "OPTIONAL MEDIA",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.5.sp,
                fontSize = 11.sp,
            ),
            color = Cyan300.copy(alpha = 0.70f),
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Watch while you ride",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            ),
            color = TextPrimary,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Choose a provider or start in pure stats mode.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        Spacer(Modifier.height(16.dp))

        // 3x2 grid of media app cards
        val rows = mediaApps.chunked(3)
        for ((rowIndex, row) in rows.withIndex()) {
            if (rowIndex > 0) Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (app in row) {
                    MediaAppCard(
                        app = app,
                        isSelected = selectedMedia?.packageName == app.packageName,
                        onClick = { onMediaSelect(app) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Fill remaining slots
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onStartWithMedia,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan300,
                    contentColor = Color(0xFF0F172A),
                    disabledContainerColor = Cyan300.copy(alpha = 0.3f),
                    disabledContentColor = Color(0xFF0F172A).copy(alpha = 0.5f),
                ),
                enabled = selectedMedia != null,
            ) {
                Text(
                    text = "Start with Media",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                )
            }

            OutlinedButton(
                onClick = onStartStatsOnly,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary,
                ),
            ) {
                Text(
                    text = "Stats Only",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MediaAppCard(
    app: MediaApp,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    val borderColor = if (isSelected) Cyan300.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f)
    val borderWidth = if (isSelected) 1.5.dp else 1.dp

    Row(
        modifier = modifier
            .clip(cardShape)
            .background(
                Color.Black.copy(alpha = 0.15f),
                cardShape,
            )
            .border(borderWidth, borderColor, cardShape)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon square
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(app.color.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.iconLetter,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                color = Color.White,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Launch with workout overlay",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun UpcomingSegmentsCard(
    segments: List<io.freewheel.launcher.data.WorkoutSegment>,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(30.dp)
    val segmentRowShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(Color.White.copy(alpha = 0.04f), cardShape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
            .padding(20.dp),
    ) {
        Text(
            text = "UPCOMING SEGMENTS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.5.sp,
                fontSize = 10.sp,
            ),
            color = Cyan300.copy(alpha = 0.70f),
        )

        Spacer(Modifier.height(12.dp))

        val displaySegments = segments.take(5)
        for ((index, segment) in displaySegments.withIndex()) {
            if (index > 0) Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.15f), segmentRowShape)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), segmentRowShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Segment label
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Duration
                val minutes = segment.durationSeconds / 60
                val seconds = segment.durationSeconds % 60
                Text(
                    text = "${minutes}:${String.format("%02d", seconds)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = TextSecondary,
                )

                Spacer(Modifier.width(12.dp))

                // Resistance badge
                Text(
                    text = "R${segment.resistance}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = Cyan300,
                )
            }
        }

        if (segments.size > 5) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "+${segments.size - 5} more segments",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun PreviewGraphCard(
    segments: List<io.freewheel.launcher.data.WorkoutSegment>,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(30.dp)

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(Color.White.copy(alpha = 0.04f), cardShape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
            .padding(20.dp),
    ) {
        Text(
            text = "PREVIEW GRAPH",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.5.sp,
                fontSize = 10.sp,
            ),
            color = Cyan300.copy(alpha = 0.70f),
        )

        Spacer(Modifier.height(12.dp))

        // Area chart rendered as bars with gradient
        if (segments.isNotEmpty()) {
            val maxResistance = segments.maxOf { it.resistance }.coerceAtLeast(1)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                for ((index, segment) in segments.withIndex()) {
                    val fraction = segment.resistance.toFloat() / maxResistance
                    val lerpFraction = index.toFloat() / (segments.size - 1).coerceAtLeast(1)
                    val barColor = lerpColor(SparkBarCyan, SparkBarEmerald, lerpFraction)

                    Box(
                        modifier = Modifier
                            .weight(segment.durationSeconds.toFloat().coerceAtLeast(1f))
                            .fillMaxHeight(fraction.coerceAtLeast(0.05f))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        barColor.copy(alpha = 0.8f),
                                        barColor.copy(alpha = 0.3f),
                                    ),
                                ),
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                            )
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No segments",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
    }
}

/**
 * Simple linear interpolation between two colors.
 */
private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}
