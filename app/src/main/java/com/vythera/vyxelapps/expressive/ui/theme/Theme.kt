package com.vythera.vyxelapps.expressive.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { System, Light, Dark, Amoled }

/**
 * How much the UI is allowed to move. Users who find heavy motion distracting can
 * dial it back without losing state changes entirely.
 */
enum class MotionIntensity(val scale: Float) {
    Calm(0.45f),
    Balanced(1f),
    Expressive(1.35f),
}

val LocalMotionIntensity = staticCompositionLocalOf { MotionIntensity.Balanced }

/** Ambient gradient colors used by hero surfaces; tracks the active scheme. */
data class VyxelAccents(
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
)

val LocalVyxelAccents = staticCompositionLocalOf {
    VyxelAccents(VyxelViolet40, VyxelTeal40, VyxelAmber40)
}

@Composable
fun VyxelTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    motionIntensity: MotionIntensity = MotionIntensity.Balanced,
    /**
     * A premium look shared with Classic. When set to anything but
     * [VyxelSkin.Default] it supplies the palette, silhouette and type outright, and
     * [themeMode] / [dynamicColor] no longer apply — the skin *is* the theme.
     */
    skin: VyxelSkin = VyxelSkin.Default,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Amoled -> true
    }
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val scheme: ColorScheme = skin.colorScheme ?: when {
        dynamicColor && supportsDynamic && dark -> {
            val d = dynamicDarkColorScheme(context)
            if (themeMode == ThemeMode.Amoled) d.toAmoled() else d
        }
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        themeMode == ThemeMode.Amoled -> VyxelAmoledColors
        dark -> VyxelDarkColors
        else -> VyxelLightColors
    }

    val accents = VyxelAccents(
        glowA = scheme.primary,
        glowB = scheme.tertiary,
        glowC = scheme.secondary,
    )

    // Classic's background composables and glass surfaces read LocalTheme, so a skin
    // has to publish its palette there as well as into the Material scheme.
    //
    // Without a skin this used to hand out Classic's stock DarkTheme regardless of
    // what Expressive was actually rendering. That was invisible while only glass
    // backgrounds read it, but Expressive now hosts whole Classic screens — track an
    // app, custom repos, the library — and they came out dark-on-dark, or light text
    // on a light scheme. Deriving the palette from the live scheme keeps them legible
    // in every mode, including dynamic colour.
    val classicPalette = skin.palette ?: scheme.toAppThemeColors(dark)
    val typography = skin.typographyFor(VyxelTypography)

    CompositionLocalProvider(
        LocalMotionIntensity provides motionIntensity,
        LocalVyxelAccents provides accents,
        LocalVyxelSkin provides skin,
        LocalVyxelShapes provides skin.shapeSet,
        LocalVyxelTextStyles provides skin.textStylesFor(typography),
        com.vythera.vyxelapps.LocalTheme provides classicPalette,
        com.vythera.vyxelapps.LocalIsLiquidGlass provides skin.isGlass,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = skin.materialShapes,
            typography = typography,
            content = content,
        )
    }
}

/**
 * Projects a Material scheme onto Classic's palette type.
 *
 * Classic describes a theme with [com.vythera.vyxelapps.AppThemeColors] rather than a
 * `ColorScheme`, so any Classic screen hosted inside Expressive needs the active
 * scheme translated into that shape. Mirrors `dynamicAppThemeColors`, which does the
 * same job for wallpaper colours on the Classic side.
 */
private fun ColorScheme.toAppThemeColors(dark: Boolean) =
    com.vythera.vyxelapps.AppThemeColors(
        bgPrimary = background,
        bgSurface = surface,
        bgSurfaceAlt = surfaceContainer,
        bgSurfaceHigh = surfaceContainerHigh,
        textPrimary = onSurface,
        textSecondary = onSurfaceVariant,
        accent = primary,
        accentAlt = secondary,
        accentContainer = primaryContainer,
        onAccentContainer = onPrimaryContainer,
        accentTertiary = tertiary,
        accentTertiaryContainer = tertiaryContainer,
        border = outline,
        borderVariant = outlineVariant,
        dockBg = surfaceContainerHigh,
        dockForeground = primary,
        isDark = dark,
    )

/** Collapses a scheme's surface stack to true black, keeping its accent ramp. */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF101012),
    surfaceContainerHigh = Color(0xFF17171A),
    surfaceContainerHighest = Color(0xFF1F1F23),
    surfaceDim = Color.Black,
)
