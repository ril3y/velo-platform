package io.freewheel.launcher.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.freewheel.launcher.data.AppInfo
import io.freewheel.launcher.data.TileCategory
import io.freewheel.launcher.ui.theme.*

private val DrawerTileShape = RoundedCornerShape(10.dp)
private const val GRID_COLUMNS = 6

private sealed class DrawerItem {
    data class Header(val title: String) : DrawerItem()
    data class App(val info: AppInfo) : DrawerItem()
}

@Composable
fun AllAppsDrawer(
    apps: List<AppInfo>,
    visible: Boolean,
    onDismiss: () -> Unit,
    onAppClick: (String) -> Unit,
) {
    var filterText by remember { mutableStateOf("") }

    // Reset filter when drawer opens
    LaunchedEffect(visible) {
        if (visible) filterText = ""
    }

    val filteredApps = remember(apps, filterText) {
        if (filterText.isBlank()) apps
        else apps.filter { it.label.contains(filterText, ignoreCase = true) }
    }

    // Group apps by category
    val drawerItems = remember(filteredApps) {
        val fitness = filteredApps.filter { it.category == TileCategory.FITNESS }
        val media = filteredApps.filter { it.category == TileCategory.MEDIA }
        val system = filteredApps.filter { it.category == TileCategory.SYSTEM }
        val other = filteredApps.filter { it.category == TileCategory.APP }
        val groups = buildList {
            if (fitness.isNotEmpty()) add("FITNESS" to fitness)
            if (media.isNotEmpty()) add("ENTERTAINMENT" to media)
            if (system.isNotEmpty()) add("SYSTEM" to system)
            if (other.isNotEmpty()) add("APPS" to other)
        }
        buildList<DrawerItem> {
            for ((title, groupApps) in groups) {
                add(DrawerItem.Header(title))
                addAll(groupApps.map { DrawerItem.App(it) })
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground.copy(alpha = 0.97f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 50) onDismiss()
                    }
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with search
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ALL APPS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = TextPrimary,
                    )

                    Spacer(Modifier.width(24.dp))

                    // Search field
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = {
                            Text("Filter apps...", color = TextMuted)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonAccent.copy(alpha = 0.5f),
                            unfocusedBorderColor = SurfaceBorder,
                            cursorColor = NeonAccent,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    )

                    Spacer(Modifier.width(16.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                // Category-grouped app grid — 6 columns for landscape
                LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    drawerItems.forEach { item ->
                        when (item) {
                            is DrawerItem.Header -> {
                                item(
                                    span = { GridItemSpan(GRID_COLUMNS) },
                                ) {
                                    CategoryHeader(title = item.title)
                                }
                            }
                            is DrawerItem.App -> {
                                item {
                                    DrawerAppTile(
                                        app = item.info,
                                        onClick = { onAppClick(item.info.packageName) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
            .background(
                SectionHeaderColor.copy(alpha = 0.25f),
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            style = SectionHeaderStyle,
            color = SectionHeaderColor,
        )
    }
}

@Composable
private fun DrawerAppTile(
    app: AppInfo,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(DrawerTileShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(48.dp),
            )
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
