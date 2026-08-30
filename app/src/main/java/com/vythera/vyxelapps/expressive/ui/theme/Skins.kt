package com.vythera.vyxelapps.expressive.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.AppThemeColors

/**
 * The visual identity the shell renders with.
 *
 * A skin is a whole identity — palette, silhouette, type and a live background layer —
 * which is why it is separate from [ThemeMode]. Mode answers "light or dark"; skin
 * answers "which Vyxel is this".
 *
 * The open-core build ships [VyxelSkin.Default] only: the stock Material 3 Expressive
 * look, with [ThemeMode] and dynamic colour in charge. The additional skins are part
 * of the paid build and are not included here. The type is kept as an enum rather than
 * removed outright so the seam stays visible and the settings, theming and background
 * paths remain the same code in both builds.
 */
enum class VyxelSkin {
    Default;

    /** Whether this skin paints over the Material scheme entirely. */
    val isPremium: Boolean get() = false

    /** Glass skins need a captured backdrop layer to blur against. */
    val isGlass: Boolean get() = false
}

/** The Classic palette a skin renders with, or null for the stock Material look. */
val VyxelSkin.palette: AppThemeColors?
    get() = null

/** Colour scheme for a skin, or null to fall through to mode/dynamic colour. */
val VyxelSkin.colorScheme: ColorScheme?
    get() = null

val LocalVyxelSkin = staticCompositionLocalOf { VyxelSkin.Default }

// ── Silhouette ───────────────────────────────────────────────────────────────

/**
 * The corner treatment for surfaces that sit outside Material's shape scale.
 *
 * Expressive's chrome is built from a handful of named shapes rather than
 * `MaterialTheme.shapes`, so they are routed through a `CompositionLocal` — which is
 * what lets a skin change the silhouette of the whole shell.
 */
data class VyxelShapeSet(
    val Card: Shape,
    val CardPressed: Shape,
    val Hero: Shape,
    val Chip: Shape,
    val Pill: Shape,
    val Icon: Shape,
    val IconSmall: Shape,
    val Sheet: Shape,
    val SearchBar: Shape,
)

/** Stock Expressive: soft and pill-forward. */
val RoundedShapeSet = VyxelShapeSet(
    Card = RoundedCornerShape(26.dp),
    CardPressed = RoundedCornerShape(34.dp),
    Hero = RoundedCornerShape(32.dp),
    Chip = RoundedCornerShape(50),
    Pill = RoundedCornerShape(50),
    Icon = RoundedCornerShape(22.dp),
    IconSmall = RoundedCornerShape(15.dp),
    Sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    SearchBar = RoundedCornerShape(28.dp),
)

val LocalVyxelShapes = staticCompositionLocalOf { RoundedShapeSet }

val VyxelSkin.shapeSet: VyxelShapeSet
    get() = RoundedShapeSet

/** Material's own shape scale, so components that use it follow the skin too. */
val VyxelSkin.materialShapes: Shapes
    get() = VyxelShapes

fun VyxelSkin.typographyFor(base: Typography): Typography = base

fun VyxelSkin.textStylesFor(typography: Typography): VyxelTextStyleSet = DefaultTextStyles

// ── Background layer ─────────────────────────────────────────────────────────

/** The live backdrop a skin paints behind the shell. Default paints nothing. */
@Composable
fun SkinBackground(skin: VyxelSkin, modifier: Modifier = Modifier) = Unit
