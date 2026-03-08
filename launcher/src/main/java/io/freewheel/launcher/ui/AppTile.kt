package io.freewheel.launcher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.freewheel.launcher.data.HomeTile
import io.freewheel.launcher.data.RideRecord
import io.freewheel.launcher.data.TileCategory
import io.freewheel.launcher.ui.theme.*

private val TileShape = RoundedCornerShape(16.dp)
private val CompactTileShape = RoundedCornerShape(12.dp)

/**
 * Hero mode AppTile — used for the START RIDE card.
 * Features animated gradient border and glow effect.
 */
@Composable
fun HeroStartRideTile(
    lastRide: RideRecord?,
    streakDays: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "heroScale",
    )

    // Animated gradient border rotation
    val infiniteTransition = rememberInfiniteTransition(label = "heroBorder")
    val gradientAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
        ),
        label = "gradientAngle",
    )

    // Pulsing glow alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    val borderColors = listOf(NeonAccent, CadenceBlue, NeonAccent)

    Box(
        modifier = modifier
            .scale(scale)
            .drawWithContent {
                // Outer glow
                drawRoundRect(
                    color = NeonAccent.copy(alpha = glowAlpha * 0.5f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    size = size,
                    style = Stroke(width = 6.dp.toPx()),
                )
                drawContent()
            }
            .clip(TileShape)
            .drawBehind {
                // Animated gradient border
                rotate(gradientAngle) {
                    drawRect(
                        brush = Brush.sweepGradient(
                            colors = borderColors,
                            center = Offset(size.width / 2, size.height / 2),
                        ),
                        size = size,
                    )
                }
            }
            .padding(3.dp) // Border thickness
            .clip(TileShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        NeonAccent.copy(alpha = 0.08f),
                        SurfaceBright,
                        SurfaceColor,
                    ),
                ),
                TileShape,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() },
                )
            }
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                contentDescription = "Start Ride",
                tint = NeonAccent,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "START RIDE",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                ),
                color = NeonAccent,
            )
            if (lastRide != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Last: ${lastRide.durationSeconds / 60}m | ${lastRide.calories} cal | ${lastRide.avgPowerWatts}W avg",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            if (streakDays > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Streak: $streakDays day${if (streakDays != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = SpeedOrange,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Hold to Start",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 1.sp,
                ),
                color = TextMuted,
            )
        }
    }
}

/**
 * Compact mode AppTile — used for media and fitness quick-launch strips.
 * Smaller, icon-centered with name below.
 */
@Composable
fun CompactAppTile(
    tile: HomeTile.App,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "compactScale",
    )

    val isInstalled = tile.isInstalled

    Box(
        modifier = modifier
            .scale(scale)
            .clip(CompactTileShape)
            .background(SurfaceBright.copy(alpha = 0.7f), CompactTileShape)
            .border(
                width = 1.dp,
                color = SurfaceBorder.copy(alpha = 0.5f),
                shape = CompactTileShape,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() },
                )
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isInstalled && tile.icon != null) {
                Image(
                    bitmap = tile.icon.toBitmap(96, 96).asImageBitmap(),
                    contentDescription = tile.label,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.GetApp,
                    contentDescription = "Not installed",
                    tint = TextMuted,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = if (isInstalled) TextPrimary else TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Dimmed overlay for uninstalled apps
        if (!isInstalled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(DarkBackground.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "N/A",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}

/**
 * Original AppTile — kept for backward compatibility (all apps drawer, etc.)
 */
@Composable
fun AppTile(
    tile: HomeTile,
    lastRide: RideRecord?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tileScale",
    )

    val accentColor = when (tile) {
        is HomeTile.StartRide -> NeonAccent
        is HomeTile.App -> when (tile.category) {
            TileCategory.FITNESS -> FitnessAccent
            TileCategory.MEDIA -> MediaAccent
            TileCategory.SYSTEM -> SystemAccent
            TileCategory.APP -> AppAccent
        }
    }

    val isInstalled = tile !is HomeTile.App || tile.isInstalled

    Box(
        modifier = modifier
            .scale(scale)
            .clip(TileShape)
            .background(SurfaceBright, TileShape)
            .then(
                if (tile is HomeTile.StartRide) {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(NeonAccent.copy(alpha = 0.12f), Color.Transparent)
                        ),
                        TileShape,
                    )
                } else Modifier
            )
            .drawBehind {
                // Category accent stripe on the left edge
                drawRect(
                    color = if (isInstalled) accentColor else accentColor.copy(alpha = 0.3f),
                    topLeft = Offset.Zero,
                    size = Size(4.dp.toPx(), size.height),
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() },
                )
            }
            .padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (tile) {
            is HomeTile.StartRide -> StartRideTileContent(lastRide)
            is HomeTile.App -> AppTileContent(tile)
        }

        // Dimmed overlay for uninstalled apps
        if (!isInstalled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(DarkBackground.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Not Installed",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun StartRideTileContent(lastRide: RideRecord?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
            contentDescription = "Start Ride",
            tint = NeonAccent,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "START RIDE",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            ),
            color = NeonAccent,
        )
        if (lastRide != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Last: ${lastRide.durationSeconds / 60}m  |  ${lastRide.calories} cal  |  ${lastRide.avgPowerWatts}W",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun AppTileContent(tile: HomeTile.App) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (tile.isInstalled && tile.icon != null) {
            Image(
                bitmap = tile.icon.toBitmap(128, 128).asImageBitmap(),
                contentDescription = tile.label,
                modifier = Modifier.size(64.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Default.GetApp,
                contentDescription = "Not installed",
                tint = TextMuted,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = tile.label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = if (tile.isInstalled) TextPrimary else TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
