package com.vythera.vyxelapps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Neutral stand-ins for the paid build's theme layer.
 *
 * Vyxel is open core. The store — sources, search, downloads, verification, updates,
 * modules, both shells — is all here. The four Liquid Glass Pro skins and the
 * entitlement service that unlocks them are not, and this file is the seam between
 * the two.
 *
 * Everything below is a *plain Material 3* implementation of a name the rest of the
 * app calls. `GlassSurface` is a `Surface`; `neonGlow` returns the modifier it was
 * given; `SkinBackground` draws nothing. None of it reproduces the paid rendering —
 * there is no backdrop blur, no specular pass, no animated background, and no palette
 * data. What it preserves is the call surface, so the ~500 sites that reference these
 * names compile unchanged and stay readable as a base to build on.
 *
 * Two consequences worth knowing:
 *
 *  - The open-core app always renders the stock Material look. `LocalIsLiquidGlass`
 *    is a constant `false`, so every branch guarded on it takes the plain path.
 *  - Nothing here can be flipped on to reveal a premium theme. The palettes and the
 *    blur pipeline are absent from this source tree, not disabled within it.
 */

// ── Theme-mode identifiers ───────────────────────────────────────────────────

const val CYBERPUNK_MODE = "Cyberpunk"
const val NEON_PUNK_MODE = "Neon Punk"

/**
 * Stored `themeMode` values that belong to the paid build.
 *
 * Kept so a settings blob restored from the paid build — via Auto Backup, or a
 * device transfer — is recognised and replaced with a mode this build can render,
 * rather than falling through to a palette that does not exist here.
 */
val PREMIUM_THEME_MODES = setOf(
    "Liquid Glass Dark",
    "Liquid Glass Light",
    NEON_PUNK_MODE,
    CYBERPUNK_MODE,
)

// ── Composition locals ───────────────────────────────────────────────────────

/** Always false here: the glass renderer is not part of the open core. */
val LocalIsLiquidGlass = compositionLocalOf { false }

val LocalGlassBackdrop = compositionLocalOf<Any?> { null }
val LocalGlassBgBackdrop = compositionLocalOf<Any?> { null }
val LocalGlassWallpaperUri = compositionLocalOf { "" }
val LocalGlassBlur = compositionLocalOf { 7f }
val LocalGlassEdgeIntensity = compositionLocalOf { 1.0f }
val LocalGlassRefraction = compositionLocalOf { 1.0f }
val LocalGlassNavBlur = compositionLocalOf { 7f }
val LocalGlassNavEdgeIntensity = compositionLocalOf { 1.0f }
val LocalGlassNavRefraction = compositionLocalOf { 1.0f }
val LocalGlassNavTextColor = compositionLocalOf<Color?> { null }
val LocalCyberpunkFx = compositionLocalOf { true }
val LocalCyberClock = compositionLocalOf<State<Float>?> { null }

// ── Tokens ───────────────────────────────────────────────────────────────────

/**
 * Greyscale placeholders.
 *
 * These are the same neutral fallbacks the paid build uses before its theme pack is
 * downloaded — the branded values were never compiled into the APK in the first
 * place, so nothing is being withheld here that used to be present.
 */
object GlassTokens {
    private val Dark = Color(0xFF121212)
    private val Light = Color(0xFFF2F2F2)
    private val VeilDark = Color(0x14FFFFFF)
    private val VeilLight = Color(0x14000000)
    private val OnDark = Color(0xFFEDEDED)
    private val OnLight = Color(0xFF1A1A1A)
    private val Accent = Color(0xFF8A8A8A)

    val cardRadius = 22.dp
    val navRadius = 28.dp
    val tabRadius = 18.dp

    val darkBg = Dark
    val darkGlassFill = VeilDark
    val darkChromeTop = VeilDark
    val darkChromeMid = VeilDark
    val darkChromeBot = VeilDark
    val darkSpecular = VeilDark
    val darkInnerGlow = VeilDark
    val darkAbLeft = VeilDark
    val darkAbRight = VeilDark
    val darkNavFill = VeilDark
    val darkNavBorder = VeilDark
    val darkNavRimTop = VeilDark
    val darkNavRimAlpha = 0f
    val darkPopup = Dark
    val darkAccent = Accent
    val darkTextPrimary = OnDark
    val darkTextSecondary = OnDark.copy(alpha = 0.7f)

    val lightBg = Light
    val lightGlassFill = VeilLight
    val lightChromeTop = VeilLight
    val lightChromeMid = VeilLight
    val lightChromeBot = VeilLight
    val lightSpecular = VeilLight
    val lightInnerGlow = VeilLight
    val lightAbLeft = VeilLight
    val lightAbRight = VeilLight
    val lightNavFill = VeilLight
    val lightNavBorder = VeilLight
    val lightNavRimTop = VeilLight
    val lightNavRimAlpha = 0f
    val lightPopup = Light
    val lightAccent = Accent
    val lightTextPrimary = OnLight
    val lightTextSecondary = OnLight.copy(alpha = 0.7f)
}

// ── Surfaces ─────────────────────────────────────────────────────────────────

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassTokens.cardRadius),
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) { Box(content = content) }
}

@Composable
fun GlassColoredSurface(
    modifier: Modifier,
    gradient: Brush,
    shape: Shape,
    onClick: (() -> Unit)? = null,
    cyberAccent: Color? = null,
    cyberCorners: Boolean = false,
    cyberCut: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(gradient)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        content = content,
    )
}

/** No live backdrop in the open core. */
@Composable
fun GlassScreenBackground(modifier: Modifier = Modifier) = Unit

/**
 * Unreachable here — the only caller is guarded by [LocalIsLiquidGlass], which is a
 * constant `false` in this build, so the plain nav bar always renders instead.
 */
@Composable
fun GlassNavBar(
    selectedTab: VAppTab,
    onTabSelect: (VAppTab) -> Unit,
    updateCount: Int = 0,
) = Unit

/** Unreachable here for the same reason as [GlassNavBar]. */
@Composable
fun GlassSearchBar(
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Unit

@Composable
fun GlassSearchShell(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier, content = content)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    containerColor: Color? = null,
    elevation: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val resolved = shape ?: RoundedCornerShape(GlassTokens.cardRadius)
    val colors = CardDefaults.cardColors(
        containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = resolved,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        ) { content() }
    } else {
        Card(
            modifier = modifier,
            shape = resolved,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        ) { content() }
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.primary,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        content = content,
    )
}

@Composable
fun GlassSettingsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(GlassTokens.cardRadius)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) {
            content()
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors) { content() }
    }
}

// ── Silhouette / type ────────────────────────────────────────────────────────

val CyberCutSmall: Shape = RoundedCornerShape(7.dp)
val CyberCutMedium: Shape = RoundedCornerShape(11.dp)
val CyberCutLarge: Shape = RoundedCornerShape(16.dp)

/** Rounded stand-in for the chamfered HUD panel. */
class CyberPanelShape(
    private val cut: Dp = 14.dp,
    private val notch: Dp = 7.dp,
) : Shape by RoundedCornerShape(11.dp)

/**
 * A neutral palette under the paid skin's name.
 *
 * Every branch that reads it is guarded by [isCyberpunk], which is a constant `false`
 * here — so this is dead code that still has to type-check. It resolves to the stock
 * dark palette rather than the Cyberpunk one, which is not in this source tree.
 */
val CyberpunkTheme: AppThemeColors get() = DarkTheme

/** Same, for the Neon Punk skin. */
val NeonPunkTheme: AppThemeColors get() = DarkTheme

/** Liquid Glass palettes resolve to the stock ones for the same reason. */
val LiquidGlassDarkTheme: AppThemeColors get() = DarkTheme
val LiquidGlassLightTheme: AppThemeColors get() = LightTheme

val CyberpunkShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(11.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

fun cyberpunkTypography(base: Typography): Typography = base

/** Never true here — there is no Cyberpunk palette in this build. */
fun AppThemeColors.isCyberpunk(): Boolean = false

/** Never true here — there is no Neon Punk palette in this build. */
fun AppThemeColors.isNeonPunk(): Boolean = false

// ── Decoration: all identity, no effect ──────────────────────────────────────

fun Modifier.neonGlow(
    color: Color,
    shape: Shape,
    glow: Dp = 22.dp,
    shadowOn: Boolean = true,
): Modifier = this

fun Modifier.neonGlowGradient(
    shape: Shape,
    glow: Dp = 22.dp,
    shadowOn: Boolean = true,
    light: Boolean = false,
    rim: Int = 0,
): Modifier = this

fun Modifier.cyberDoubleEdge(
    color: Color = Color.Transparent,
    cut: Dp = 11.dp,
    inset: Dp = 3.dp,
    rim: Int = 0,
    notch: Dp = 7.dp,
): Modifier = this

fun Modifier.cyberBloom(
    color: Color = Color.Transparent,
    alpha: Float = 0.2f,
    scale: Float = 1.15f,
): Modifier = this

fun Modifier.cyberGlitchName(seed: Int): Modifier = this

fun Modifier.cyberHudCorners(
    color: Color = Color.Transparent,
    len: Dp = 10.dp,
): Modifier = this

fun cyberRimFor(seed: Int): Int = 0

fun cyberTileBrush(hue: Color): Brush = SolidColor(hue)

fun cyberTitleGradient(): Brush = SolidColor(Color.Unspecified)

fun cyberPanelPath(size: Size, cut: Float, notch: Float, inset: Float = 0f) =
    androidx.compose.ui.graphics.Path().apply {
        addRect(androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset))
    }

/** A plain divider stands in for the HUD rule. */
@Composable
fun CyberSectionRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
fun GlitchText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) {
    androidx.compose.material3.Text(text = text, modifier = modifier, style = style, color = color)
}

// ── Backgrounds ──────────────────────────────────────────────────────────────

@Composable
fun CyberpunkBackground(
    screen: ScreenBg,
    effectsOn: Boolean = true,
    modifier: Modifier = Modifier,
) = Unit

@Composable
fun NeonPunkBackground(screen: ScreenBg, modifier: Modifier = Modifier) = Unit

// ── Neon Punk glass constants, referenced by the shared settings sliders ──────

const val NP_GLASS_BLUR = 7f
const val NP_GLASS_EDGE = 1.0f
const val NP_GLASS_REFRACTION = 1.0f

fun npHeroBorder(shape: Shape): Modifier = Modifier
fun npTextGradient(alpha: Float = 1f): Brush = SolidColor(Color.Unspecified)

/**
 * Premium theme-pack artwork lookup.
 *
 * The pack is downloaded by the paid build against a signed entitlement; there is no
 * pack here, so this always answers null and callers fall through to their bundled
 * asset — which is the same path the paid build takes before its pack arrives.
 */
@Composable
fun rememberPackPainter(name: String): androidx.compose.ui.graphics.painter.Painter? = null
