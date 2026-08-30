package com.vythera.vyxelapps

import android.graphics.Typeface
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.vythera.vyxelapps.R

data class AppThemeColors(
    val bgPrimary              : Color,
    val bgSurface              : Color,
    val bgSurfaceAlt           : Color,
    val bgSurfaceHigh          : Color,
    val textPrimary            : Color,
    val textSecondary          : Color,
    val accent                 : Color,
    val accentAlt              : Color,
    val accentContainer        : Color,
    val onAccentContainer      : Color,
    val accentTertiary         : Color,
    val accentTertiaryContainer: Color,
    val border                 : Color,
    val borderVariant          : Color,
    val dockBg                 : Color,
    val dockForeground         : Color,
    val isDark                 : Boolean = false
)

// Parse "#RRGGBB" or "RRGGBB" to a fully-opaque Compose Color
fun hexToColor(hex: String, fallback: Color = Color.Black): Color = try {
    val h = hex.trimStart('#').let {
        if (it.length == 3) it.map { c -> "$c$c" }.joinToString("") else it
    }.padStart(6, '0').take(6)
    Color(android.graphics.Color.parseColor("#$h"))
} catch (_: Exception) { fallback }

// Convert a Color to its "#RRGGBB" hex string (alpha ignored — always FF when reloaded)
fun Color.toHex6(): String = "#%06X".format(((value shr 32).toInt()) and 0x00FFFFFF)

// Convert any AppThemeColors to a CustomThemeData (just saves the accent)
fun AppThemeColors.toCustomThemeData() = CustomThemeData(
    accentHex = accent.toHex6(),
    isDark    = isDark
)

// Derive a full AppThemeColors palette from accent color, optionally overriding individual colors
fun CustomThemeData.toAppThemeColors(): AppThemeColors {
    val accent = hexToColor(accentHex, Color(0xFFD0BCFF))
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb(
            (accent.red   * 255).toInt(),
            (accent.green * 255).toInt(),
            (accent.blue  * 255).toInt()
        ), hsv
    )
    val h = hsv[0]
    fun c(hue: Float, s: Float, v: Float) =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
    fun overrideOrDerive(hex: String, derived: Color) =
        if (hex.isNotEmpty()) hexToColor(hex, derived) else derived
    val tertH = (h + 60f) % 360f

    val derived = if (isDark) AppThemeColors(
        bgPrimary               = c(h, 0.10f, 0.09f),
        bgSurface               = c(h, 0.09f, 0.14f),
        bgSurfaceAlt            = c(h, 0.08f, 0.18f),
        bgSurfaceHigh           = c(h, 0.07f, 0.22f),
        textPrimary             = c(h, 0.06f, 0.91f),
        textSecondary           = c(h, 0.10f, 0.79f),
        accent                  = accent,
        accentAlt               = c(h, 0.55f, 0.85f),
        accentContainer         = c(h, 0.60f, 0.33f),
        onAccentContainer       = c(h, 0.15f, 0.93f),
        accentTertiary          = c(tertH, 0.50f, 0.85f),
        accentTertiaryContainer = c(tertH, 0.55f, 0.30f),
        border                  = c(h, 0.20f, 0.58f),
        borderVariant           = c(h, 0.15f, 0.30f),
        dockBg                  = c(h, 0.09f, 0.14f).copy(alpha = 0.94f),
        dockForeground          = accent,
        isDark                  = true
    ) else AppThemeColors(
        bgPrimary               = c(h, 0.04f, 0.99f),
        bgSurface               = c(h, 0.05f, 0.95f),
        bgSurfaceAlt            = c(h, 0.06f, 0.91f),
        bgSurfaceHigh           = c(h, 0.07f, 0.87f),
        textPrimary             = c(h, 0.10f, 0.11f),
        textSecondary           = c(h, 0.12f, 0.30f),
        accent                  = accent,
        accentAlt               = c(h, 0.45f, 0.55f),
        accentContainer         = c(h, 0.20f, 0.93f),
        onAccentContainer       = c(h, 0.70f, 0.22f),
        accentTertiary          = c(tertH, 0.40f, 0.50f),
        accentTertiaryContainer = c(tertH, 0.15f, 0.92f),
        border                  = c(h, 0.15f, 0.48f),
        borderVariant           = c(h, 0.08f, 0.79f),
        dockBg                  = c(h, 0.05f, 0.95f).copy(alpha = 0.94f),
        dockForeground          = accent,
        isDark                  = false
    )
    // Apply user-overridden colors on top of the auto-derived palette
    val bg      = overrideOrDerive(bgHex,        derived.bgPrimary)
    val surface = overrideOrDerive(surfaceHex,   derived.bgSurface)
    val onSurf  = overrideOrDerive(onSurfaceHex, derived.textPrimary)
    val sec     = overrideOrDerive(secondaryHex, derived.accentAlt)
    val tert    = overrideOrDerive(tertiaryHex,  derived.accentTertiary)
    return derived.copy(
        bgPrimary      = bg,
        bgSurface      = surface,
        bgSurfaceAlt   = if (surfaceHex.isNotEmpty()) surface else derived.bgSurfaceAlt,
        bgSurfaceHigh  = if (surfaceHex.isNotEmpty()) surface else derived.bgSurfaceHigh,
        textPrimary    = onSurf,
        accentAlt      = sec,
        accentTertiary = tert,
        dockBg         = if (surfaceHex.isNotEmpty()) surface.copy(alpha = 0.94f) else derived.dockBg
    )
}

// ── M3 Expressive – Light (seed #6750A4 · Purple) ────────────────────────────
// Exact M3 tonal-palette tokens from Material Theme Builder.
// surface = N98, surfaceContainer = N93, primary = P40, onSurfaceVariant = NV30.
val LightTheme = AppThemeColors(
    bgPrimary               = Color(0xFFFEF7FF),   // background / surface  (N 98)
    bgSurface               = Color(0xFFF3EDF7),   // surfaceContainer      (N ~93)
    bgSurfaceAlt            = Color(0xFFECE6F0),   // surfaceContainerHigh  (N ~90)
    bgSurfaceHigh           = Color(0xFFE6E0E9),   // surfaceContainerHighest (N ~87)
    textPrimary             = Color(0xFF1C1B1F),   // onSurface / onBackground (N 10)
    textSecondary           = Color(0xFF49454F),   // onSurfaceVariant      (NV 30)
    accent                  = Color(0xFF6750A4),   // primary               (P 40)
    accentAlt               = Color(0xFF625B71),   // secondary             (S 40)
    accentContainer         = Color(0xFFEADDFF),   // primaryContainer      (P 90)
    onAccentContainer       = Color(0xFF21005D),   // onPrimaryContainer    (P 10)
    accentTertiary          = Color(0xFF7D5260),   // tertiary              (T 40)
    accentTertiaryContainer = Color(0xFFFFD8E4),   // tertiaryContainer     (T 90)
    border                  = Color(0xFF79747E),   // outline               (NV 50)
    borderVariant           = Color(0xFFCAC4D0),   // outlineVariant        (NV 80)
    dockBg                  = Color(0xF5F3EDF7),   // surfaceContainer @ 96 %
    dockForeground          = Color(0xFF6750A4),
    isDark                  = false
)

// ── M3 Expressive – Dark (seed #6750A4 · Purple) ─────────────────────────────
// surface = N 6, surfaceContainer = N ~12, primary = P 80, outline = NV 60.
val DarkTheme = AppThemeColors(
    bgPrimary               = Color(0xFF141218),   // background / surface      (N 6)
    bgSurface               = Color(0xFF211F26),   // surfaceContainer          (N ~12)
    bgSurfaceAlt            = Color(0xFF2B2930),   // surfaceContainerHigh      (N ~17)
    bgSurfaceHigh           = Color(0xFF36343B),   // surfaceContainerHighest   (N ~22)
    textPrimary             = Color(0xFFE6E0E9),   // onSurface / onBackground  (N 90)
    textSecondary           = Color(0xFFCAC4D0),   // onSurfaceVariant          (NV 80)
    accent                  = Color(0xFFD0BCFF),   // primary                   (P 80)
    accentAlt               = Color(0xFFCCC2DC),   // secondary                 (S 80)
    accentContainer         = Color(0xFF4F378B),   // primaryContainer          (P 30)
    onAccentContainer       = Color(0xFFEADDFF),   // onPrimaryContainer        (P 90)
    accentTertiary          = Color(0xFFEFB8C8),   // tertiary                  (T 80)
    accentTertiaryContainer = Color(0xFF633B48),   // tertiaryContainer         (T 30)
    border                  = Color(0xFF938F99),   // outline                   (NV 60)
    borderVariant           = Color(0xFF49454F),   // outlineVariant            (NV 30)
    dockBg                  = Color(0xF5211F26),   // surfaceContainer @ 96 %
    dockForeground          = Color(0xFFD0BCFF),
    isDark                  = true
)

// ── M3 Expressive – AMOLED (true-black surfaces · teal seed #00796B) ─────────
// True-black OLED optimisation. M3 dark palette shifted to a teal hue for
// maximum contrast vibrancy against black; surfaces follow M3 N 4–12 tones.
val AmoledTheme = AppThemeColors(
    bgPrimary               = Color(0xFF000000),   // true black
    bgSurface               = Color(0xFF0F0D13),   // surfaceContainerLowest (N 4)
    bgSurfaceAlt            = Color(0xFF1D1B20),   // surfaceContainerLow    (N 10)
    bgSurfaceHigh           = Color(0xFF211F26),   // surfaceContainer       (N 12)
    textPrimary             = Color(0xFFE6E0E9),   // onSurface              (N 90)
    textSecondary           = Color(0xFFCAC4D0),   // onSurfaceVariant       (NV 80)
    accent                  = Color(0xFF5DD5BB),   // primary teal dark      (T 80)
    accentAlt               = Color(0xFF4FBFAB),   // secondary teal         (T2 80)
    accentContainer         = Color(0xFF004D40),   // primaryContainer       (T 30)
    onAccentContainer       = Color(0xFFB2F5EC),   // onPrimaryContainer     (T 90)
    accentTertiary          = Color(0xFFFFB74D),   // tertiary warm amber    (complementary to teal)
    accentTertiaryContainer = Color(0xFF4A2900),   // tertiaryContainer dark
    border                  = Color(0xFF3B3840),   // outline subtle on black
    borderVariant           = Color(0xFF1D1B20),   // outlineVariant
    dockBg                  = Color(0xF50F0D13),   // surfaceContainerLowest @ 96 %
    dockForeground          = Color(0xFF5DD5BB),
    isDark                  = true
)

// ── M3 Expressive – Minimal (neutral/zero-chroma seed) ───────────────────────
// Strictly achromatic M3 dark variant — surfaces and accent follow the N
// (neutral) tonal palette only; no hue saturation anywhere.
val MinimalTheme = AppThemeColors(
    bgPrimary               = Color(0xFF0E0E0E),   // background neutral     (N ~5)
    bgSurface               = Color(0xFF1A1A1A),   // surfaceContainer       (N ~10)
    bgSurfaceAlt            = Color(0xFF242424),   // surfaceContainerHigh   (N ~14)
    bgSurfaceHigh           = Color(0xFF2E2E2E),   // surfaceContainerHighest (N ~18)
    textPrimary             = Color(0xFFE3E3E3),   // onSurface              (N 89)
    textSecondary           = Color(0xFFA3A3A3),   // onSurfaceVariant       (N 64)
    accent                  = Color(0xFFBEBDBD),   // primary neutral        (N 80)
    accentAlt               = Color(0xFF9E9E9E),   // secondary neutral      (N 62)
    accentContainer         = Color(0xFF3D3D3D),   // primaryContainer       (N ~24)
    onAccentContainer       = Color(0xFFE3E3E3),   // onPrimaryContainer     (N 89)
    accentTertiary          = Color(0xFF8A8A8A),   // tertiary neutral       (N 54)
    accentTertiaryContainer = Color(0xFF2A2A2A),   // tertiaryContainer      (N ~16)
    border                  = Color(0xFF5C5C5C),   // outline                (N 36)
    borderVariant           = Color(0xFF2E2E2E),   // outlineVariant         (N 18)
    dockBg                  = Color(0xF51A1A1A),   // surfaceContainer @ 96 %
    dockForeground          = Color(0xFFBEBDBD),
    isDark                  = true
)

// ── M3 Expressive – Sunset (warm orange seed #BF360C, dark) ──────────────────
// M3 tonal palette from a deep-orange seed. Rich warm surfaces follow the
// N (neutral) palette of the orange hue; accent = primary dark tone 80.
val SunsetTheme = AppThemeColors(
    bgPrimary               = Color(0xFF1C1107),   // background warm        (N ~6)
    bgSurface               = Color(0xFF28180A),   // surfaceContainer warm  (N ~12)
    bgSurfaceAlt            = Color(0xFF33210F),   // surfaceContainerHigh   (N ~17)
    bgSurfaceHigh           = Color(0xFF3E2C18),   // surfaceContainerHighest (N ~22)
    textPrimary             = Color(0xFFF0DFCF),   // onSurface warm         (N 90)
    textSecondary           = Color(0xFFD3C0B1),   // onSurfaceVariant warm  (NV 80)
    accent                  = Color(0xFFFFB77A),   // primary orange dark    (P 80)
    accentAlt               = Color(0xFFE5BE9B),   // secondary warm         (S 80)
    accentContainer         = Color(0xFF7B2000),   // primaryContainer       (P 30)
    onAccentContainer       = Color(0xFFFFDBC9),   // onPrimaryContainer     (P 90)
    accentTertiary          = Color(0xFF8ECAE6),   // tertiary cool sky blue (contrast with orange)
    accentTertiaryContainer = Color(0xFF0B3547),   // tertiaryContainer dark blue
    border                  = Color(0xFF9E8F83),   // outline warm           (NV 60)
    borderVariant           = Color(0xFF524540),   // outlineVariant warm    (NV 30)
    dockBg                  = Color(0xF528180A),   // surfaceContainer @ 96 %
    dockForeground          = Color(0xFFFFB77A),
    isDark                  = true
)

// ── Custom (user-defined) — live object rebuilt from CustomThemeData ──────────
val CustomTheme: AppThemeColors get() = DarkTheme.copy()

val LocalTheme = compositionLocalOf<AppThemeColors> { DarkTheme }

/**
 * Reserves the space the status bar occupies, whether or not it is currently visible.
 *
 * The app hides the status bar for a full-bleed look, which drops the ordinary
 * `statusBarsPadding()` to zero and pins headers to the very top edge — readable, but
 * visibly cramped. `…IgnoringVisibility` reports the inset the bar *would* have, so
 * layouts keep the breathing room they were designed with and nothing shifts when the
 * bar is swiped back in transiently.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
fun androidx.compose.ui.Modifier.statusBarSpace(): androidx.compose.ui.Modifier =
    this.windowInsetsPadding(
        androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility
    )

/**
 * Projects a Vyxel palette onto a Material 3 [androidx.compose.material3.ColorScheme].
 *
 * Both shells render the same premium themes, so the mapping lives here rather than
 * being written out at each theming site — otherwise the two skins of one app could
 * disagree about, say, which palette slot `surfaceContainerHigh` comes from.
 */
fun AppThemeColors.toColorScheme(): androidx.compose.material3.ColorScheme {
    val base = if (isDark) androidx.compose.material3.darkColorScheme()
    else androidx.compose.material3.lightColorScheme()
    return base.copy(
        primary              = accent,
        primaryContainer     = accentContainer,
        onPrimaryContainer   = onAccentContainer,
        secondary            = accentAlt,
        tertiary             = accentTertiary,
        tertiaryContainer    = accentTertiaryContainer,
        background           = bgPrimary,
        surface              = bgSurface,
        surfaceContainer     = bgSurfaceAlt,
        surfaceContainerHigh = bgSurfaceHigh,
        onBackground         = textPrimary,
        onSurface            = textPrimary,
        onSurfaceVariant     = textSecondary,
        outline              = border,
        outlineVariant       = borderVariant,
    )
}

// GitHub repos confirmed to have no installable APK. Home rows read this to hide
// them without every call site needing the set threaded through. Empty by default
// (e.g. in previews and non-home surfaces), so it filters nothing there.
val LocalApkAbsentIds = compositionLocalOf { emptySet<Long>() }

// Material You — palette derived from the user's wallpaper (Android 12+).
// Callers must gate on Build.VERSION.SDK_INT >= 31.
fun dynamicAppThemeColors(context: android.content.Context, dark: Boolean): AppThemeColors {
    val s = if (dark) androidx.compose.material3.dynamicDarkColorScheme(context)
            else      androidx.compose.material3.dynamicLightColorScheme(context)
    return AppThemeColors(
        bgPrimary               = s.background,
        bgSurface               = s.surface,
        bgSurfaceAlt            = s.surfaceContainer,
        bgSurfaceHigh           = s.surfaceContainerHigh,
        textPrimary             = s.onSurface,
        textSecondary           = s.onSurfaceVariant,
        accent                  = s.primary,
        accentAlt               = s.secondary,
        accentContainer         = s.primaryContainer,
        onAccentContainer       = s.onPrimaryContainer,
        accentTertiary          = s.tertiary,
        accentTertiaryContainer = s.tertiaryContainer,
        border                  = s.outline,
        borderVariant           = s.outlineVariant,
        dockBg                  = s.surfaceContainerHigh,
        dockForeground          = s.primary,
        isDark                  = dark
    )
}

enum class ThemeName(val label: String, val emoji: String) {
    LIGHT("Light", "☀️"), DARK("Dark", "🌙"), MINIMAL("Minimal", "◾"),
    AMOLED("AMOLED", "🖤"), SUNSET("Sunset", "🌅"), CUSTOM("Custom", "🎨"),
    LIQUID_GLASS_DARK("Liquid Glass Dark", "🔮"),
    LIQUID_GLASS_LIGHT("Liquid Glass Light", "🪟")
}

fun themeColors(name: ThemeName) = when (name) {
    ThemeName.LIGHT             -> LightTheme
    ThemeName.DARK              -> DarkTheme
    ThemeName.MINIMAL           -> MinimalTheme
    ThemeName.AMOLED            -> AmoledTheme
    ThemeName.SUNSET            -> SunsetTheme
    ThemeName.CUSTOM            -> CustomTheme
    ThemeName.LIQUID_GLASS_DARK -> DarkTheme
    ThemeName.LIQUID_GLASS_LIGHT -> LightTheme
}

val StarGold  = Color(0xFFF59E0B)
val GreenOk   = Color(0xFF1DB954)
val RedDanger = Color(0xFFEF4444)

private fun sysFont(familyName: String): FontFamily =
    try { FontFamily(Typeface.create(familyName, Typeface.NORMAL)) }
    catch (_: Exception) { FontFamily.Default }

// Returns an immediately-available FontFamily.
// Google Fonts are served from googleFontCache (populated by loadGoogleFont).
// While a font is still downloading, FontFamily.Default is returned; Compose
// recomposes automatically when fontFamily state updates in HomeScreen.
fun fontFamilyFor(name: String): FontFamily = when (name) {
    "Serif"            -> FontFamily.Serif
    "Monospace"        -> FontFamily.Monospace
    "Cursive"          -> FontFamily.Cursive
    "Roboto Condensed" -> sysFont("sans-serif-condensed")
    "Roboto Medium"    -> sysFont("sans-serif-medium")
    "Roboto Light"     -> sysFont("sans-serif-light")
    "Roboto Thin"      -> sysFont("sans-serif-thin")
    "Roboto Black"     -> sysFont("sans-serif-black")
    "Kilo"             -> FontFamily(Font(R.font.kilo))
    "Chococooky"       -> FontFamily(Font(R.font.chococooky))
    else               -> googleFontCache[name] ?: FontFamily.Default
}
