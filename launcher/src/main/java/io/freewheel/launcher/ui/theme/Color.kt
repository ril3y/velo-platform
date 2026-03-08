package io.freewheel.launcher.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Core background palette
val SurfaceDark = Color(0xFF0D0D14)
val DarkBackground = Color(0xFF0A0A0F)
val SurfaceColor = Color(0xFF14141F)
val SurfaceBright = Color(0xFF1E1E2E)
val SurfaceBorder = Color(0xFF2A2A3A)

// Metric accent colors
val HeartRateRed = Color(0xFFFF3B5C)
val CadenceBlue = Color(0xFF3BA4FF)
val PowerGreen = Color(0xFF39FF6E)
val SpeedOrange = Color(0xFFFF9F2E)
val ResistanceYellow = Color(0xFFFFD54F)

// Neon accent
val NeonAccent = Color(0xFF00FFCC)
val NeonAccentDim = Color(0xFF00CC99)

// Text
val TextPrimary = Color(0xFFF0F0F5)
val TextSecondary = Color(0xFF8888AA)
val TextMuted = Color(0xFF555570)

// Status
val StatusGreen = Color(0xFF4CAF50)
val StatusRed = Color(0xFFFF5252)
val StatusYellow = Color(0xFFFFD54F)

// Tile categories
val FitnessTileBorder = NeonAccent
val MediaTileBorder = Color(0xFF3A3A5A)
val SystemTileBorder = Color(0xFF2A2A4A)

// Hero card gradient
val HeroGradient = Brush.linearGradient(
    listOf(NeonAccent.copy(alpha = 0.25f), SurfaceBright)
)
val HeroGradientBorder = Brush.linearGradient(
    listOf(NeonAccent.copy(alpha = 0.7f), NeonAccent.copy(alpha = 0.2f))
)

// Category accent stripe colors
val FitnessAccent = NeonAccent
val MediaAccent = CadenceBlue
val SystemAccent = SpeedOrange
val AppAccent = TextMuted

// Background gradient
val BackgroundCenter = Color(0xFF0F1020)
val BackgroundEdge = Color(0xFF080810)
val BackgroundGradient = Brush.radialGradient(
    listOf(BackgroundCenter, BackgroundEdge)
)

// Glow effects
val NeonGlow = Color(0x3300FFCC)  // 20% alpha neon for glows
val BlueGlow = Color(0x333BA4FF)

// Elevated surface (for cards that need to pop)
val SurfaceElevated = Color(0xFF252540)
val SurfaceElevatedBorder = Color(0xFF353555)

// Section header
val SectionHeaderColor = Color(0xFF444466)
