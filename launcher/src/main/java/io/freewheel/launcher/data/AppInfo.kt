package io.freewheel.launcher.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isInstalled: Boolean = true,
    val category: TileCategory = TileCategory.APP,
)

enum class TileCategory {
    FITNESS,
    MEDIA,
    SYSTEM,
    APP,
}
