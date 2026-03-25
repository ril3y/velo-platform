package io.freewheel.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.fit.VeloFitnessClient
import io.freewheel.launcher.data.Workout
import io.freewheel.launcher.data.WorkoutSegment
import io.freewheel.launcher.ui.theme.*

private val GlassPanelShape = RoundedCornerShape(26.dp)
private val CyanLabel = Color(0xFF22D3EE)
private val RoseButton = Color(0xFFF43F5E)
private val GlassBg = Color(0xFF0D0F1A).copy(alpha = 0.75f)
private val GlassBorder = Color.White.copy(alpha = 0.10f)

private fun Modifier.glassPanel(): Modifier = this
    .background(GlassBg, GlassPanelShape)
    .border(1.dp, GlassBorder, GlassPanelShape)

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun currentSegmentIndex(segments: List<WorkoutSegment>, elapsed: Int): Int {
    var acc = 0
    for ((i, seg) in segments.withIndex()) {
        acc += seg.durationSeconds
        if (elapsed < acc) return i
    }
    return segments.lastIndex.coerceAtLeast(0)
}

private fun secondsUntilNextSegment(segments: List<WorkoutSegment>, elapsed: Int): Int {
    var acc = 0
    for (seg in segments) {
        acc += seg.durationSeconds
        if (elapsed < acc) return acc - elapsed
    }
    return 0
}

@Composable
fun WorkoutRideScreen(
    workout: Workout,
    power: Int,
    rpm: Int,
    resistance: Int,
    calories: Int,
    elapsedSeconds: Int,
    speedMph: Float,
    distanceMiles: Float,
    heartRate: Int,
    isConnected: Boolean,
    powerHistory: List<Int>,
    ftp: Int,
    onEndRide: () -> Unit,
) {
    val context = LocalContext.current
    val fitnessClient = remember { VeloFitnessClient(context) }
    var difficulty by remember { mutableFloatStateOf(1.0f) }

    val segments = workout.segments
    val currentIdx = currentSegmentIndex(segments, elapsedSeconds)
    val currentSegment = segments.getOrNull(currentIdx)
    val nextSegment = segments.getOrNull(currentIdx + 1)
    val secsUntilNext = secondsUntilNextSegment(segments, elapsedSeconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF11131F), Color(0xFF08090F)))
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(CyanLabel.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.1f),
                        radius = size.width * 0.35f,
                    ),
                )
            },
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                workout.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            // Connection pill
            Row(
                modifier = Modifier
                    .background(
                        if (isConnected) StatusGreen.copy(alpha = 0.20f) else StatusRed.copy(alpha = 0.20f),
                        RoundedCornerShape(20.dp),
                    )
                    .border(
                        1.dp,
                        if (isConnected) StatusGreen.copy(alpha = 0.4f) else StatusRed.copy(alpha = 0.4f),
                        RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(if (isConnected) StatusGreen else StatusRed, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isConnected) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (isConnected) StatusGreen else StatusRed,
                )
            }
        }

        if (segments.isEmpty()) {
            // ── Free ride mode: full-screen immersive stats ──

            // Top bar: workout name + elapsed + connection
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    workout.name,
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
                    color = TextSecondary,
                )
                Text(
                    formatDuration(elapsedSeconds),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 36.sp,
                    ),
                    color = TextPrimary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).background(
                            if (isConnected) StatusGreen else StatusRed,
                            CircleShape,
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isConnected) "Connected" else "Connecting",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isConnected) StatusGreen else StatusRed,
                    )
                }
            }

            // Center: massive power display
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "$power",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 160.sp,
                        lineHeight = 160.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = PowerGreen,
                )
                Text(
                    "WATTS",
                    style = MaterialTheme.typography.titleLarge.copy(
                        letterSpacing = 8.sp,
                        fontSize = 20.sp,
                    ),
                    color = PowerGreen.copy(alpha = 0.6f),
                )
            }

            // Flanking: RPM left, Heart right
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "$rpm",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = CadenceBlue,
                )
                Text("RPM", style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 4.sp), color = CadenceBlue.copy(alpha = 0.6f))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (heartRate > 0) "$heartRate" else "--",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = if (heartRate > 0) HeartRateRed else TextMuted,
                )
                Text("BPM", style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 4.sp), color = HeartRateRed.copy(alpha = 0.6f))
            }

            // Bottom bar: secondary metrics + end ride
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 24.dp)
                    .glassPanel()
                    .padding(vertical = 14.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FreeRideSecondaryMetric("$calories", "CAL", SpeedOrange)
                FreeRideMetricDivider()
                FreeRideSecondaryMetric("%.1f".format(speedMph), "MPH", SpeedOrange)
                FreeRideMetricDivider()
                FreeRideSecondaryMetric("%.1f".format(distanceMiles), "MI", CyanLabel)
                FreeRideMetricDivider()
                FreeRideSecondaryMetric("LVL $resistance", "RES", ResistanceYellow)
                FreeRideMetricDivider()

                Button(
                    onClick = onEndRide,
                    colors = ButtonDefaults.buttonColors(containerColor = RoseButton, contentColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                ) {
                    Text("End Ride", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                }
            }
        } else {
            // ── Structured workout mode: full-screen landscape layout ──
            val scaledRes = ((currentSegment?.resistance ?: 0) * difficulty).toInt().coerceIn(1, 25)
            val powerTarget = remember(scaledRes) { fitnessClient.getTargetPower(scaledRes) }

            // Top bar: segment info + timer + connection
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        currentSegment?.label ?: workout.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                        color = TextPrimary,
                    )
                    Text(
                        "Segment ${currentIdx + 1}/${segments.size}" +
                            if (nextSegment != null) " \u00B7 next: ${nextSegment.label} in ${formatDuration(secsUntilNext)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    formatDuration(elapsedSeconds),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 36.sp,
                    ),
                    color = TextPrimary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(if (isConnected) StatusGreen else StatusRed, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isConnected) "Connected" else "Connecting", style = MaterialTheme.typography.labelMedium, color = if (isConnected) StatusGreen else StatusRed)
                }
            }

            // Center: large power with RPM + HR flanking
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "$power",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 120.sp, lineHeight = 120.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    ),
                    color = PowerGreen,
                )
                Text("WATTS", style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 6.sp), color = PowerGreen.copy(alpha = 0.5f))
            }

            // Left: RPM
            Column(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$rpm", style = MaterialTheme.typography.displayMedium.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), color = CadenceBlue)
                Text("RPM", style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp), color = CadenceBlue.copy(alpha = 0.5f))
                Spacer(Modifier.height(24.dp))
                Text("LVL $resistance", style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), color = ResistanceYellow)
                Text("RES", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp), color = ResistanceYellow.copy(alpha = 0.5f))
            }

            // Right: Heart + effort indicator
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (heartRate > 0) "$heartRate" else "--",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    color = if (heartRate > 0) HeartRateRed else TextMuted,
                )
                Text("BPM", style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp), color = HeartRateRed.copy(alpha = 0.5f))
                Spacer(Modifier.height(24.dp))
                // Compact effort zone indicator
                val zone = powerTarget?.zone(power)
                val zoneColor = when (zone) {
                    io.freewheel.fit.PowerTarget.EffortZone.ON_TARGET -> StatusGreen
                    io.freewheel.fit.PowerTarget.EffortZone.UNDER -> CadenceBlue
                    io.freewheel.fit.PowerTarget.EffortZone.OVER -> SpeedOrange
                    else -> TextMuted
                }
                val zoneLabel = when (zone) {
                    io.freewheel.fit.PowerTarget.EffortZone.ON_TARGET -> "ON TARGET"
                    io.freewheel.fit.PowerTarget.EffortZone.UNDER -> "PUSH MORE"
                    io.freewheel.fit.PowerTarget.EffortZone.OVER -> "EASE UP"
                    else -> "IDLE"
                }
                Text(zoneLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp), color = zoneColor)
            }

            // Bottom: hill chart + metrics + end ride
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(start = 28.dp, end = 28.dp, top = 10.dp, bottom = 14.dp),
            ) {
                // Full-width hill chart
                WorkoutGraph(
                    segments = segments,
                    totalSeconds = segments.sumOf { it.durationSeconds },
                    elapsedSeconds = elapsedSeconds,
                    actualPower = powerHistory,
                    ftp = ftp,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                )
                Spacer(Modifier.height(8.dp))
                // Metrics + difficulty + end ride row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Workout info
                    Column {
                        Text(workout.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Text(
                            "${formatDuration(elapsedSeconds)} \u00B7 segment ${currentIdx + 1}/${segments.size}",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        )
                    }
                    // Secondary stats
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FreeRideSecondaryMetric("$calories", "CAL", SpeedOrange)
                        FreeRideSecondaryMetric("%.1f".format(distanceMiles), "MI", CyanLabel)
                    }
                    // Difficulty
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { difficulty = (difficulty - 0.1f).coerceAtLeast(0.5f) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(36.dp),
                        ) { Text("\u2212", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        Text("%.1fx".format(difficulty), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), color = CyanLabel, modifier = Modifier.padding(horizontal = 8.dp))
                        Button(
                            onClick = { difficulty = (difficulty + 0.1f).coerceAtMost(2.0f) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(36.dp),
                        ) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    }
                    // End ride
                    Button(
                        onClick = onEndRide,
                        colors = ButtonDefaults.buttonColors(containerColor = RoseButton, contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    ) {
                        Text("End Ride", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeRidePrimaryMetric(value: Int, unit: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$value",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
            color = color,
        )
        Text(unit, style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp), color = color.copy(alpha = 0.7f))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = TextSecondary)
    }
}

@Composable
private fun FreeRideSecondaryMetric(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 28.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
            color = color,
        )
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = TextSecondary)
    }
}

@Composable
private fun FreeRideMetricDivider() {
    Box(modifier = Modifier.width(1.dp).height(28.dp).background(GlassBorder))
}

@Composable
private fun StatTile(label: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 2.8.sp, fontSize = 12.sp), color = Color.White.copy(alpha = 0.45f))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 38.sp), color = color)
        Text(unit, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, fontSize = 12.sp), color = CyanLabel.copy(alpha = 0.8f))
    }
}

/**
 * Convert a resistance level (1-25) to target center power in watts.
 * Same formula as VeloFitnessClient.computeTargetPowerLocally().
 */
private fun resistanceToWatts(resistance: Int, ftp: Int): Float {
    val res = resistance.coerceIn(1, 25)
    val effortFraction = 0.10f + (res - 1) * (0.90f / 24f)
    return ftp * effortFraction
}

@Composable
private fun WorkoutGraph(
    segments: List<WorkoutSegment>,
    totalSeconds: Int,
    elapsedSeconds: Int,
    actualPower: List<Int>,
    ftp: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (segments.isEmpty() || totalSeconds <= 0) return

    // Convert segment resistance levels to watts so both target and actual use the same scale
    val segmentWatts = segments.map { resistanceToWatts(it.resistance, ftp) }
    val maxTarget = segmentWatts.max()
    val maxActual = if (actualPower.isNotEmpty()) actualPower.max().toFloat() else 0f
    val maxY = (maxOf(maxTarget, maxActual) * 1.2f).coerceAtLeast(1f)
    val graphHeight = if (compact) 32.dp else 100.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(graphHeight)
            .background(SurfaceBright, RoundedCornerShape(8.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height
        val pad = 2f

        fun valToY(v: Float): Float =
            h - pad - ((v / maxY) * (h - pad * 2))

        // Target profile (filled area) — plotted in watts
        val targetPath = Path()
        targetPath.moveTo(0f, h)
        var xOff = 0f
        for ((idx, seg) in segments.withIndex()) {
            val segW = (seg.durationSeconds.toFloat() / totalSeconds) * w
            val y = valToY(segmentWatts[idx])
            targetPath.lineTo(xOff, y)
            targetPath.lineTo(xOff + segW, y)
            xOff += segW
        }
        targetPath.lineTo(w, h)
        targetPath.close()

        drawPath(
            targetPath,
            brush = Brush.verticalGradient(
                listOf(NeonAccent.copy(alpha = 0.25f), NeonAccent.copy(alpha = 0.05f))
            ),
            style = Fill,
        )

        // Target outline — plotted in watts
        xOff = 0f
        for ((i, seg) in segments.withIndex()) {
            val segW = (seg.durationSeconds.toFloat() / totalSeconds) * w
            val y = valToY(segmentWatts[i])
            if (i > 0) {
                val prevY = valToY(segmentWatts[i - 1])
                drawLine(NeonAccent.copy(alpha = 0.3f), Offset(xOff, prevY), Offset(xOff, y), 1f)
            }
            drawLine(NeonAccent.copy(alpha = 0.6f), Offset(xOff, y), Offset(xOff + segW, y), 2f)
            xOff += segW
        }

        // Actual power line (already in watts)
        if (actualPower.size >= 2 && !compact) {
            val actualPath = Path()
            val step = w / totalSeconds.toFloat()
            for ((i, power) in actualPower.withIndex()) {
                val x = i * step
                val y = valToY(power.toFloat())
                if (i == 0) actualPath.moveTo(x, y) else actualPath.lineTo(x, y)
            }
            drawPath(actualPath, PowerGreen.copy(alpha = 0.8f), style = Stroke(width = 2f))
        }

        // Progress line
        if (elapsedSeconds > 0) {
            val px = (elapsedSeconds.toFloat() / totalSeconds) * w
            drawLine(
                Color.White.copy(alpha = 0.6f),
                Offset(px, 0f),
                Offset(px, h),
                strokeWidth = if (compact) 2f else 1.5f,
            )
        }
    }
}

@Composable
private fun EffortBar(actualPower: Int, powerTarget: io.freewheel.fit.PowerTarget, actualResistance: Int, modifier: Modifier) {
    val zone = powerTarget.zone(actualPower)
    val ratio = powerTarget.complianceRatio(actualPower)
    val zoneLow = powerTarget.zoneLowFraction()
    val zoneHigh = powerTarget.zoneHighFraction()

    val (label, color) = when (zone) {
        io.freewheel.fit.PowerTarget.EffortZone.IDLE -> "IDLE" to TextSecondary
        io.freewheel.fit.PowerTarget.EffortZone.UNDER -> "UNDER" to CadenceBlue
        io.freewheel.fit.PowerTarget.EffortZone.OVER -> "OVER" to SpeedOrange
        io.freewheel.fit.PowerTarget.EffortZone.ON_TARGET -> "ON TARGET" to PowerGreen
    }

    Column(modifier.width(120.dp).glassPanel().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("LIVE EFFORT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.8.sp, fontSize = 11.sp), color = CyanLabel.copy(alpha = 0.8f))
        Spacer(Modifier.height(4.dp))
        Text("${powerTarget.targetLow}-${powerTarget.targetHigh}W", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp), color = PowerGreen.copy(alpha = 0.8f))
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.width(36.dp).height(280.dp)
                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
        ) {
            val h = size.height; val w = size.width
            val cr = CornerRadius(4.dp.toPx())

            // Target zone band
            val zoneTopY = h * (1f - zoneHigh)
            val zoneBottomY = h * (1f - zoneLow)
            drawRect(PowerGreen.copy(alpha = 0.25f), Offset(0f, zoneTopY), Size(w, zoneBottomY - zoneTopY))
            drawLine(PowerGreen.copy(alpha = 0.6f), Offset(0f, zoneTopY), Offset(w, zoneTopY), 1.5.dp.toPx())
            drawLine(PowerGreen.copy(alpha = 0.6f), Offset(0f, zoneBottomY), Offset(w, zoneBottomY), 1.5.dp.toPx())

            // Fill from bottom to marker
            if (ratio > 0f) {
                val fillTop = h * (1f - ratio)
                drawRect(color.copy(alpha = 0.2f), Offset(0f, fillTop), Size(w, h - fillTop))
            }

            // Marker line
            if (actualPower > 0) {
                val markerY = h * (1f - ratio)
                drawLine(color, Offset(0f, markerY), Offset(w, markerY), 3.dp.toPx())
                drawLine(Color.White, Offset(2.dp.toPx(), markerY), Offset(w - 2.dp.toPx(), markerY), 1.5.dp.toPx())
            }

            // Border
            drawRoundRect(Color.White.copy(alpha = 0.1f), Offset.Zero, Size(w, h), cr, style = Stroke(1.dp.toPx()))
        }
        Spacer(Modifier.height(12.dp))
        Text("${actualPower}W", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 28.sp), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 10.sp), color = color)
        Spacer(Modifier.height(8.dp))
        Text("R: $actualResistance", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)
    }
}

private fun lerpColor(a: Color, b: Color, f: Float): Color {
    val t = f.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)
}
