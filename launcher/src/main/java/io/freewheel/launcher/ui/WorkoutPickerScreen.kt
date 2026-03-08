package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.launcher.data.Workout
import io.freewheel.launcher.data.WorkoutSegment
import io.freewheel.launcher.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Gradient palettes for workout cards (cycle through)
private val CardGradients = listOf(
    listOf(Color(0xFF7C3AED), Color(0xFFA855F7), Color(0xFF06B6D4)),
    listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF06B6D4)),
    listOf(Color(0xFFF97316), Color(0xFFFB923C), Color(0xFFE11D48)),
    listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF6366F1)),
    listOf(Color(0xFFEC4899), Color(0xFFF472B6), Color(0xFFA855F7)),
    listOf(Color(0xFFDC2626), Color(0xFFEF4444), Color(0xFFF97316)),
    listOf(Color(0xFF8B5CF6), Color(0xFFA78BFA), Color(0xFF6366F1)),
    listOf(Color(0xFF14B8A6), Color(0xFF2DD4BF), Color(0xFF06B6D4)),
)

private val CardBottomBg = Color(0xFF1A1D35)
private val CyanLabel = Color(0xFF22D3EE)
private val EmeraldCta = Color(0xFF34D399)
private val SparkBarColor = Color(0xFF22D3EE)

// Category display order
private val CategoryOrder = listOf("Quick", "HIIT", "Endurance", "Strength", "Recovery", "General")

@Composable
fun WorkoutPickerScreen(
    workouts: List<Workout>,
    onSelect: (Workout) -> Unit,
    onBack: () -> Unit,
    ramUsedMb: Long,
    ramTotalMb: Long,
    currentTime: Long,
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    val timeStr = timeFormat.format(Date(currentTime))

    // Group by category, ordered
    val grouped = remember(workouts) {
        val map = workouts.groupBy { it.category }
        CategoryOrder.mapNotNull { cat ->
            val list = map[cat]
            if (list != null) cat to list else null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0B12), Color(0xFF090A11)),
                )
            ),
    ) {
        // Top bar
        WorkoutPickerTopBar(
            timeStr = timeStr,
            ramUsedMb = ramUsedMb,
            ramTotalMb = ramTotalMb,
            onBack = onBack,
        )

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WORKOUT LIBRARY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp,
                        fontSize = 12.sp,
                    ),
                    color = CyanLabel.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choose your ride",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                    ),
                    color = TextPrimary,
                )
            }

            // Search field
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(44.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "Search workouts...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        }

        // Scrollable category sections
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            grouped.forEachIndexed { index, (category, categoryWorkouts) ->
                // Divider between groups (not before first)
                if (index > 0) {
                    item(key = "divider_$category") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 8.dp)
                                .height(2.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            CyanLabel.copy(alpha = 0.30f),
                                            CyanLabel.copy(alpha = 0.45f),
                                            CyanLabel.copy(alpha = 0.30f),
                                            Color.Transparent,
                                        )
                                    )
                                )
                        )
                    }
                }

                // Category header
                item(key = "header_$category") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Accent bar
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(22.dp)
                                .background(CyanLabel, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = category.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp,
                                fontSize = 16.sp,
                            ),
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${categoryWorkouts.size} workouts",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                            ),
                            color = TextMuted,
                        )
                    }
                }

                // Horizontal row of cards
                item(key = "row_$category") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        items(categoryWorkouts, key = { it.id }) { workout ->
                            val colorIdx = workouts.indexOf(workout)
                            WorkoutCard(
                                workout = workout,
                                gradientColors = CardGradients[colorIdx.coerceAtLeast(0) % CardGradients.size],
                                onSelect = { onSelect(workout) },
                                modifier = Modifier
                                    .width(280.dp)
                                    .height(340.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutPickerTopBar(
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
        NavItem("Home", false, onBack)
        Spacer(Modifier.width(24.dp))
        NavItem("Workouts", true, {})
        Spacer(Modifier.width(24.dp))
        NavItem("Media", false, {})
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
private fun NavItem(label: String, active: Boolean, onClick: () -> Unit) {
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
private fun WorkoutCard(
    workout: Workout,
    gradientColors: List<Color>,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.4f),
            )
            .clip(cardShape)
            .background(CardBottomBg)
            .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
            .clickable { onSelect() },
    ) {
        // Gradient hero section — fixed 120dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.linearGradient(
                        colors = gradientColors.map { it.copy(alpha = 0.92f) },
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    )
                )
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.Transparent,
                            ),
                            center = Offset(0f, 0f),
                            radius = size.maxDimension * 0.7f,
                        )
                    )
                }
                .padding(14.dp),
        ) {
            // Type badge top-left
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = workout.type.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }

            // Duration pill top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "${workout.durationMinutes} MIN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 10.sp,
                    ),
                    color = Color.White,
                )
            }
        }

        // Info section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // Name — big, bright, readable
                Text(
                    text = workout.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(4.dp))

                // Type + Coach row
                Text(
                    text = "${workout.type}  ·  ${workout.coach}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                    ),
                    color = Color.White.copy(alpha = 0.50f),
                    maxLines = 1,
                )

                Spacer(Modifier.height(10.dp))

                // Sparkline
                WorkoutSparkline(
                    segments = workout.segments,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                )

                Spacer(Modifier.height(10.dp))

                // Description — readable
                Text(
                    text = workout.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Select button
            Button(
                onClick = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldCta,
                    contentColor = Color(0xFF0F172A),
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = "Select",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                )
            }
        }
    }
}

@Composable
fun WorkoutSparkline(
    segments: List<WorkoutSegment>,
    modifier: Modifier = Modifier,
    barColor: Color = SparkBarColor,
) {
    if (segments.isEmpty()) return
    val maxResistance = segments.maxOf { it.resistance }.coerceAtLeast(1)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (segment in segments) {
            val fraction = segment.resistance.toFloat() / maxResistance
            Box(
                modifier = Modifier
                    .weight(segment.durationSeconds.toFloat().coerceAtLeast(1f))
                    .fillMaxHeight(fraction.coerceAtLeast(0.08f))
                    .background(
                        barColor.copy(alpha = 0.55f + 0.45f * fraction),
                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                    )
            )
        }
    }
}
