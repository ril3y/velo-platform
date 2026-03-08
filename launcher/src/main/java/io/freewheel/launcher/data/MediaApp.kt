package io.freewheel.launcher.data

import androidx.compose.ui.graphics.Color

data class MediaApp(
    val name: String,
    val packageName: String,
    val color: Color,
    val iconLetter: String,
)

object MediaApps {
    val all = listOf(
        MediaApp("Netflix", "com.netflix.mediaclient", Color(0xFFE50914), "N"),
        MediaApp("Disney+", "com.disney.disneyplus", Color(0xFF0E6AC0), "D"),
        MediaApp("YouTube", "com.google.android.youtube", Color(0xFFFF0000), "▶"),
        MediaApp("Prime Video", "com.amazon.avod.thirdpartyclient", Color(0xFF00A8E1), "P"),
        MediaApp("Hulu", "com.hulu.plus", Color(0xFF1CE783), "H"),
        MediaApp("JRNY", "com.nautilus.bowflex.usb", Color(0xFF475569), "J"),
    )
}
