package io.freewheel.launcher.data

import android.graphics.drawable.Drawable

sealed class HomeTile {
    object StartRide : HomeTile()

    data class App(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val isInstalled: Boolean,
        val category: TileCategory,
    ) : HomeTile()
}
