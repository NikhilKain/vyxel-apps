package com.vythera.vyxelapps.expressive.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Vyxel brand palette: an electric violet primary paired with a teal secondary and
 * a warm amber tertiary. Tonal values follow the Material 3 tonal palette structure
 * so dynamic color and the static fallback stay visually consistent.
 */

// Primary — violet
val VyxelViolet10 = Color(0xFF20005C)
val VyxelViolet20 = Color(0xFF360091)
val VyxelViolet30 = Color(0xFF4B1FC0)
val VyxelViolet40 = Color(0xFF6242E0)
val VyxelViolet80 = Color(0xFFCBBEFF)
val VyxelViolet90 = Color(0xFFE7DEFF)
val VyxelViolet100 = Color(0xFFFFFFFF)

// Secondary — teal
val VyxelTeal10 = Color(0xFF00201F)
val VyxelTeal20 = Color(0xFF003735)
val VyxelTeal30 = Color(0xFF00504D)
val VyxelTeal40 = Color(0xFF006A66)
val VyxelTeal80 = Color(0xFF4EDBD4)
val VyxelTeal90 = Color(0xFF9FF2EC)

// Tertiary — amber
val VyxelAmber10 = Color(0xFF2C1600)
val VyxelAmber20 = Color(0xFF4A2800)
val VyxelAmber30 = Color(0xFF693B00)
val VyxelAmber40 = Color(0xFF8B5000)
val VyxelAmber80 = Color(0xFFFFB86B)
val VyxelAmber90 = Color(0xFFFFDCBE)

// Neutrals
val VyxelNeutral6 = Color(0xFF101116)
val VyxelNeutral10 = Color(0xFF16171D)
val VyxelNeutral12 = Color(0xFF1B1C22)
val VyxelNeutral17 = Color(0xFF22232A)
val VyxelNeutral20 = Color(0xFF2B2C33)
val VyxelNeutral22 = Color(0xFF2F3037)
val VyxelNeutral24 = Color(0xFF34353C)
val VyxelNeutral90 = Color(0xFFE4E1E9)
val VyxelNeutral95 = Color(0xFFF2EFF7)
val VyxelNeutral98 = Color(0xFFFDFAFF)
val VyxelNeutral99 = Color(0xFFFFFBFF)

val VyxelLightColors = lightColorScheme(
    primary = VyxelViolet40,
    onPrimary = Color.White,
    primaryContainer = VyxelViolet90,
    onPrimaryContainer = VyxelViolet10,
    inversePrimary = VyxelViolet80,

    secondary = VyxelTeal40,
    onSecondary = Color.White,
    secondaryContainer = VyxelTeal90,
    onSecondaryContainer = VyxelTeal10,

    tertiary = VyxelAmber40,
    onTertiary = Color.White,
    tertiaryContainer = VyxelAmber90,
    onTertiaryContainer = VyxelAmber10,

    background = VyxelNeutral99,
    onBackground = Color(0xFF1B1B21),
    surface = VyxelNeutral99,
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE6E0EC),
    onSurfaceVariant = Color(0xFF48454E),
    surfaceTint = VyxelViolet40,

    surfaceDim = Color(0xFFDDD8E0),
    surfaceBright = VyxelNeutral99,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = VyxelNeutral98,
    surfaceContainer = Color(0xFFF6F1F9),
    surfaceContainerHigh = Color(0xFFF0EBF4),
    surfaceContainerHighest = Color(0xFFEAE5EE),

    outline = Color(0xFF79757F),
    outlineVariant = Color(0xFFCAC4CF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = VyxelNeutral95,
    scrim = Color.Black,
)

val VyxelDarkColors = darkColorScheme(
    primary = VyxelViolet80,
    onPrimary = VyxelViolet20,
    primaryContainer = VyxelViolet30,
    onPrimaryContainer = VyxelViolet90,
    inversePrimary = VyxelViolet40,

    secondary = VyxelTeal80,
    onSecondary = VyxelTeal20,
    secondaryContainer = VyxelTeal30,
    onSecondaryContainer = VyxelTeal90,

    tertiary = VyxelAmber80,
    onTertiary = VyxelAmber20,
    tertiaryContainer = VyxelAmber30,
    onTertiaryContainer = VyxelAmber90,

    background = VyxelNeutral6,
    onBackground = VyxelNeutral90,
    surface = VyxelNeutral6,
    onSurface = VyxelNeutral90,
    surfaceVariant = Color(0xFF48454E),
    onSurfaceVariant = Color(0xFFCAC4CF),
    surfaceTint = VyxelViolet80,

    surfaceDim = VyxelNeutral6,
    surfaceBright = Color(0xFF3A3B42),
    surfaceContainerLowest = Color(0xFF0B0C10),
    surfaceContainerLow = VyxelNeutral10,
    surfaceContainer = VyxelNeutral12,
    surfaceContainerHigh = VyxelNeutral17,
    surfaceContainerHighest = VyxelNeutral22,

    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF48454E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = VyxelNeutral90,
    inverseOnSurface = Color(0xFF303036),
    scrim = Color.Black,
)

/**
 * True-black variant for OLED panels. Derived from [VyxelDarkColors] so the accent
 * ramp stays identical and only the surface stack collapses to black.
 */
val VyxelAmoledColors = VyxelDarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF101012),
    surfaceContainerHigh = Color(0xFF17171A),
    surfaceContainerHighest = Color(0xFF1F1F23),
    surfaceDim = Color.Black,
)

/** Source-brand accents used for chips, rails and the source picker. */
object SourceColors {
    val GitHub = Color(0xFF8B95A5)
    val GitLab = Color(0xFFFC6D26)
    val FDroid = Color(0xFF1F97F3)
    val IzzyOnDroid = Color(0xFF64B5A0)
    val Codeberg = Color(0xFF2185D0)
    val Flathub = Color(0xFF4A90D9)
    val WinGet = Color(0xFF0FA5E9)

    /** APKPure green. */
    val ApkPure = Color(0xFF3DDC84)

    /** Aptoide orange. */
    val Aptoide = Color(0xFFF57C00)

    /** Aurora's own aurora-green. */
    val Aurora = Color(0xFF32C48D)

    // Modules read as one family on screen, so both repos share a hue and are told
    // apart by the badge text rather than by colour.
    val MagiskAlt = Color(0xFF9B6BDF)
    val Googlers = Color(0xFFB08AE8)
}
