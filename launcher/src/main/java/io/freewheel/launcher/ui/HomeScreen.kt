package io.freewheel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.freewheel.launcher.data.AppInfo
import io.freewheel.launcher.data.HomeTile
import io.freewheel.launcher.data.RideRecord
import io.freewheel.launcher.data.TileCategory
import io.freewheel.launcher.service.ServiceStatus
import io.freewheel.launcher.ui.theme.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

// Accent colors matching the React mockup — boosted for device visibility
private val Cyan200 = Color(0xFF67E8F9)
private val Cyan300 = Color(0xFF22D3EE)
private val Cyan400 = Color(0xFF06B6D4)
private val Cyan500 = Color(0xFF0891B2)
private val Emerald400 = Color(0xFF34D399)
private val Emerald500 = Color(0xFF10B981)

// Shell/card background colors from the React mockup
private val ShellBgTop = Color(0xFF0A0B12)
private val ShellBgBottom = Color(0xFF090A11)
private val HeroBgTop = Color(0xFF0C0F1B)
private val HeroBgBottom = Color(0xFF090B13)
private val ScreenBg = Color(0xFF05060B)

@Composable
fun HomeScreen(
    tiles: List<HomeTile>,
    allApps: List<AppInfo>,
    recentRides: List<RideRecord>,
    serviceStatus: ServiceStatus,
    wifiSsid: String,
    ramUsedMb: Long,
    ramTotalMb: Long,
    lastRidePower: Int = 0,
    lastRideRpm: Int = 0,
    fitnessApps: List<AppInfo> = emptyList(),
    recentApps: List<AppInfo> = emptyList(),
    workoutCount: Int = 0,
    workoutCategoryCount: Int = 0,
    onTileClick: (HomeTile) -> Unit,
    onAppClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onBridgeClick: () -> Unit,
    onTaskManagerClick: () -> Unit,
    onAppInfo: (String) -> Unit = {},
    onUninstall: (String) -> Unit = {},
    onViewAllRides: () -> Unit = {},
    burnInOffsetX: Int = 0,
    burnInOffsetY: Int = 0,
    onBrowseWorkouts: () -> Unit = {},
    onMediaClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    updateAvailable: Boolean = false,
    currentTime: Long = System.currentTimeMillis(),
) {
    var showAllApps by remember { mutableStateOf(false) }
    var longPressedTile: HomeTile.App? by remember { mutableStateOf(null) }

    val lastRide = recentRides.firstOrNull()
    val streakDays = remember(recentRides) { calculateStreak(recentRides) }
    val startRide = tiles.filterIsInstance<HomeTile.StartRide>().firstOrNull()

    val todayRideMinutes = remember(recentRides) {
        val cal = java.util.Calendar.getInstance()
        val todayStart = cal.apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        recentRides
            .filter { it.startTime >= todayStart }
            .sumOf { it.durationSeconds } / 60
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            // Subtle ambient radial glow on the entire screen — dark blue-cyan undertone
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0E1525).copy(alpha = 0.8f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.3f, size.height * 0.3f),
                        radius = size.width * 0.7f,
                    ),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.3f, size.height * 0.3f),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = burnInOffsetX.dp, y = burnInOffsetY.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -50) showAllApps = true
                    }
                },
        ) {
            // Status bar
            StatusBar(
                serviceStatus = serviceStatus,
                wifiSsid = wifiSsid,
                ramUsedMb = ramUsedMb,
                ramTotalMb = ramTotalMb,
                currentTime = currentTime,
                onSettingsClick = onSettingsClick,
                onBridgeClick = onBridgeClick,
                onAllAppsClick = { showAllApps = true },
                onTaskManagerClick = onTaskManagerClick,
                updateAvailable = updateAvailable,
            )

            // Main content: 12-col grid approximation
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // LEFT: Hero card (7/12 cols)
                HeroWorkoutCard(
                    lastRide = lastRide,
                    streakDays = streakDays,
                    todayRideMinutes = todayRideMinutes,
                    onBrowseWorkouts = onBrowseWorkouts,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                )

                // RIGHT column (5/12 cols)
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // App strips: Fitness + Recent side by side
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppColumn(
                            label = "FITNESS",
                            apps = fitnessApps,
                            onAppClick = onAppClick,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        AppColumn(
                            label = "RECENT",
                            apps = recentApps,
                            onAppClick = onAppClick,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }

                    // Workouts tile with stats
                    NavTile(
                        icon = Icons.Default.TrackChanges,
                        name = "Workouts",
                        description = if (workoutCount > 0)
                            "$workoutCount workouts \u00B7 $workoutCategoryCount categories"
                        else
                            "Browse structured workout plans",
                        isFirst = true,
                        onClick = onBrowseWorkouts,
                        modifier = Modifier.fillMaxWidth().weight(0.7f),
                    )

                    // History tile with recent ride data
                    HistoryNavTile(
                        recentRides = recentRides,
                        onClick = onHistoryClick,
                        modifier = Modifier.fillMaxWidth().weight(0.7f),
                    )
                }
            }

        }

        // All apps drawer
        AllAppsDrawer(
            apps = allApps,
            visible = showAllApps,
            onDismiss = { showAllApps = false },
            onAppClick = { pkg ->
                showAllApps = false
                onAppClick(pkg)
            },
        )

        // Long-press context menu
        DropdownMenu(
            expanded = longPressedTile != null,
            onDismissRequest = { longPressedTile = null },
        ) {
            DropdownMenuItem(
                text = { Text("App Info") },
                onClick = {
                    longPressedTile?.let { onAppInfo(it.packageName) }
                    longPressedTile = null
                },
            )
            DropdownMenuItem(
                text = { Text("Uninstall") },
                onClick = {
                    longPressedTile?.let { onUninstall(it.packageName) }
                    longPressedTile = null
                },
            )
        }
    }
}

@Composable
private fun HeroWorkoutCard(
    lastRide: RideRecord?,
    streakDays: Int,
    todayRideMinutes: Int,
    onBrowseWorkouts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(32.dp)
    val lastRideSummary = if (lastRide != null) {
        "Last ride ${lastRide.durationSeconds / 60} min \u00B7 ${lastRide.calories} cal"
    } else {
        "No rides yet"
    }
    val recentWorkoutName = if (lastRide != null) {
        "${lastRide.durationSeconds / 60} min ride"
    } else "None"
    val goalMinutes = 30
    val remaining = (goalMinutes - todayRideMinutes).coerceAtLeast(0)

    Box(
        modifier = modifier
            .clip(cardShape)
            // Layer 1: Base dark card gradient (linear top-to-bottom)
            .background(
                Brush.linearGradient(
                    colors = listOf(HeroBgTop, HeroBgBottom),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY),
                ),
                cardShape,
            )
            // Layer 2: PRIMARY radial cyan glow — top-left, big and bright
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Cyan300.copy(alpha = 0.30f),
                            Cyan400.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        center = Offset(0f, 0f),
                        radius = size.width * 0.50f,
                    ),
                    radius = size.width * 0.50f,
                    center = Offset(0f, 0f),
                )
            }
            // Layer 3: Secondary softer cyan haze — slightly right of center-top for depth
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Cyan400.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.25f, size.height * 0.1f),
                        radius = size.width * 0.35f,
                    ),
                    radius = size.width * 0.35f,
                    center = Offset(size.width * 0.25f, size.height * 0.1f),
                )
            }
            // Layer 4: Very subtle warm glow at bottom-right to break monotony
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1040).copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.9f, size.height * 0.85f),
                        radius = size.width * 0.30f,
                    ),
                    radius = size.width * 0.30f,
                    center = Offset(size.width * 0.9f, size.height * 0.85f),
                )
            }
            // Border: visible white/10 like the mockup
            .border(1.dp, Color.White.copy(alpha = 0.12f), cardShape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top row: READY pill + last ride
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // READY pill — emerald with a subtle glow
                Box(
                    modifier = Modifier
                        .drawBehind {
                            // Emerald glow behind the pill
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Emerald400.copy(alpha = 0.15f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.width * 0.8f,
                                ),
                                radius = size.width * 0.8f,
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                        }
                        .border(1.dp, Emerald400.copy(alpha = 0.60f), RoundedCornerShape(20.dp))
                        .background(Emerald400.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "R E A D Y",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            fontSize = 11.sp,
                        ),
                        color = Emerald400,
                    )
                }

                Text(
                    text = lastRideSummary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = Color.White.copy(alpha = 0.50f),
                )
            }

            // Center: Icon + "Start Workout" + subtitle + buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Bike emoji in circle with cyan glow behind it
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        // Glow layer behind the circle
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Cyan300.copy(alpha = 0.25f),
                                        Cyan400.copy(alpha = 0.08f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.width * 0.85f,
                                ),
                                radius = size.width * 0.85f,
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                        }
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Cyan300.copy(alpha = 0.35f),
                                    Cyan400.copy(alpha = 0.12f),
                                ),
                            ),
                            shape = CircleShape,
                        )
                        .background(Color.Black.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\uD83D\uDEB4",
                        fontSize = 52.sp,
                    )
                }

                Spacer(Modifier.width(32.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // "Start Workout" — BIG, cyan with text shadow for glow
                    Text(
                        text = "Start Workout",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 52.sp,
                            lineHeight = 56.sp,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-0.5).sp,
                            shadow = Shadow(
                                color = Cyan300.copy(alpha = 0.30f),
                                offset = Offset(0f, 0f),
                                blurRadius = 24f,
                            ),
                        ),
                        color = Cyan200,
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Pick a structured workout, free ride, or pair media with a ride overlay.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                        ),
                        color = Color.White.copy(alpha = 0.60f),
                    )

                    Spacer(Modifier.height(24.dp))

                    // Single CTA button
                    Button(
                        onClick = onBrowseWorkouts,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan300,
                            contentColor = Color(0xFF0F172A),
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .testTag("btn_browse_workouts")
                            .semantics { contentDescription = "Start Workout" }
                            .drawBehind {
                                // Button glow
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Cyan300.copy(alpha = 0.20f),
                                            Color.Transparent,
                                        ),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = size.width * 0.7f,
                                    ),
                                    radius = size.width * 0.7f,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                )
                            },
                    ) {
                        Text(
                            text = "Start Workout",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            ),
                        )
                    }
                }
            }

            // Bottom: 3 mini stat cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MiniStatCard(
                    label = "RECENT",
                    value = recentWorkoutName,
                    subtitle = "Suggested next",
                    accentColor = Cyan400,
                    modifier = Modifier.weight(1f),
                )
                MiniStatCard(
                    label = "STREAK",
                    value = if (streakDays > 0) "$streakDays day${if (streakDays != 1) "s" else ""}" else "0 days",
                    subtitle = if (streakDays > 0) "Keep it going" else "Start today",
                    accentColor = Emerald400,
                    modifier = Modifier.weight(1f),
                )
                MiniStatCard(
                    label = "TODAY GOAL",
                    value = "$goalMinutes min",
                    subtitle = if (remaining > 0) "$remaining min remaining" else "Goal reached!",
                    accentColor = Color(0xFFFFD54F),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Cyan400,
) {
    val cardShape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .clip(cardShape)
            // Subtle gradient background instead of flat color
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.Black.copy(alpha = 0.25f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY),
                ),
                cardShape,
            )
            // Tiny accent glow at top-left corner
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(0f, 0f),
                        radius = size.width * 0.5f,
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(0f, 0f),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.12f), cardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = accentColor.copy(alpha = 0.70f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            ),
            color = TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = Color.White.copy(alpha = 0.50f),
            maxLines = 1,
        )
    }
}

@Composable
private fun NavTile(
    icon: ImageVector,
    name: String,
    description: String,
    isFirst: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileShape = RoundedCornerShape(28.dp)
    // First tile is brighter, rest are subtle
    val bgAlphaTop = if (isFirst) 0.10f else 0.06f
    val bgAlphaBottom = if (isFirst) 0.04f else 0.02f

    Row(
        modifier = modifier
            .clip(tileShape)
            // Gradient background instead of flat — subtle top-to-bottom fade
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = bgAlphaTop),
                        Color.White.copy(alpha = bgAlphaBottom),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY),
                ),
                tileShape,
            )
            // Subtle cyan accent glow on the left edge for depth
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Cyan400.copy(alpha = if (isFirst) 0.08f else 0.04f),
                            Color.Transparent,
                        ),
                        center = Offset(0f, size.height * 0.5f),
                        radius = size.height * 0.8f,
                    ),
                    radius = size.height * 0.8f,
                    center = Offset(0f, size.height * 0.5f),
                )
            }
            .border(1.dp, Color.White.copy(alpha = if (isFirst) 0.14f else 0.10f), tileShape)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon in rounded container with inner gradient and subtle border
        Box(
            modifier = Modifier
                .size(52.dp)
                .drawBehind {
                    // Icon glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Cyan300.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 0.9f,
                        ),
                        radius = size.width * 0.9f,
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                }
                .border(
                    width = 1.dp,
                    color = Cyan400.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                )
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.15f),
                        ),
                    ),
                    RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = Cyan300,
                modifier = Modifier.size(26.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    letterSpacing = 0.sp,
                ),
                color = TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.50f),
                maxLines = 1,
            )
        }

        // Arrow with subtle glow
        Text(
            text = "\u2192",
            fontSize = 20.sp,
            color = Cyan300,
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(
                    color = Cyan300.copy(alpha = 0.40f),
                    offset = Offset(0f, 0f),
                    blurRadius = 8f,
                ),
            ),
        )
    }
}

@Composable
private fun AppColumn(
    label: String,
    apps: List<AppInfo>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colShape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(colShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.20f)),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY),
                ),
                colShape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), colShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = Cyan400.copy(alpha = 0.60f),
        )
        Spacer(Modifier.height(8.dp))

        if (apps.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "No apps yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.30f),
                )
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (app in apps.take(4)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                            .clickable { onAppClick(app.packageName) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White.copy(alpha = 0.90f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryNavTile(
    recentRides: List<RideRecord>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileShape = RoundedCornerShape(28.dp)
    val dateFormat = remember { java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()) }
    val timeFormat = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }

    Row(
        modifier = modifier
            .clip(tileShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.02f)),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY),
                ),
                tileShape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), tileShape)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(52.dp)
                .border(1.dp, Cyan400.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.15f)),
                    ),
                    RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = "History",
                tint = Cyan300,
                modifier = Modifier.size(26.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "History",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                ),
                color = Color.White,
            )
            if (recentRides.isEmpty()) {
                Text(
                    "No rides yet",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.50f),
                )
            } else {
                val last = recentRides.first()
                Text(
                    "${dateFormat.format(java.util.Date(last.startTime))} at ${timeFormat.format(java.util.Date(last.startTime))} \u00B7 ${last.durationSeconds / 60}m \u00B7 ${last.calories} cal",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.50f),
                    maxLines = 1,
                )
                if (recentRides.size > 1) {
                    Text(
                        "${recentRides.size} recent rides",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Cyan400.copy(alpha = 0.50f),
                    )
                }
            }
        }

        Text(
            text = "\u2192",
            fontSize = 20.sp,
            color = Cyan300,
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(
                    color = Cyan300.copy(alpha = 0.40f),
                    offset = Offset(0f, 0f),
                    blurRadius = 8f,
                ),
            ),
        )
    }
}

@androidx.annotation.VisibleForTesting
internal fun calculateStreak(rides: List<RideRecord>): Int {
    if (rides.isEmpty()) return 0
    val cal = java.util.Calendar.getInstance()
    val today = cal.get(java.util.Calendar.DAY_OF_YEAR) +
            cal.get(java.util.Calendar.YEAR) * 366
    val rideDays = rides.map { ride ->
        cal.timeInMillis = ride.startTime
        cal.get(java.util.Calendar.DAY_OF_YEAR) +
                cal.get(java.util.Calendar.YEAR) * 366
    }.distinct().sorted().reversed()
    if (rideDays.isEmpty()) return 0
    val mostRecent = rideDays.first()
    if (today - mostRecent > 1) return 0
    var streak = 1
    for (i in 1 until rideDays.size) {
        if (rideDays[i - 1] - rideDays[i] == 1) streak++ else break
    }
    return streak
}
