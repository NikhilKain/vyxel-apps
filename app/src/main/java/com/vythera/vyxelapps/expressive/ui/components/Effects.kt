package com.vythera.vyxelapps.expressive.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.ui.theme.LocalMotionIntensity
import com.vythera.vyxelapps.expressive.ui.theme.VyxelMotion
import kotlin.math.cos
import kotlin.math.sin

/** Multiplier applied to decorative motion so users can dial the UI down. */
@Composable
fun motionScale(): Float = LocalMotionIntensity.current.scale

/**
 * Sweeping highlight used for loading placeholders.
 *
 * Driven by an infinite transition rather than per-item animations so a screen full
 * of placeholders shares one clock and stays in phase.
 */
fun Modifier.shimmer(
    enabled: Boolean = true,
    baseColor: Color? = null,
    highlightColor: Color? = null,
): Modifier = composed {
    if (!enabled) return@composed this

    val base = baseColor ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = highlightColor ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val scale = motionScale()
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1400 / scale.coerceAtLeast(0.3f)).toInt(),
                easing = VyxelMotion.StandardEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    drawWithCache {
        val width = size.width.coerceAtLeast(1f)
        val travel = width * 2f
        val start = -width + travel * progress
        val brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(start, 0f),
            end = Offset(start + width, size.height),
        )
        onDrawBehind { drawRect(brush) }
    }
}

/**
 * Ambient background: three slowly drifting radial glows.
 *
 * Drawn behind content at low alpha to give large surfaces some depth without the
 * cost of a real blur.
 */
fun Modifier.auroraGlow(
    colors: List<Color>,
    alpha: Float = 0.22f,
    speedMillis: Int = 14000,
): Modifier = composed {
    val scale = motionScale()
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (speedMillis / scale.coerceAtLeast(0.3f)).toInt(),
                easing = androidx.compose.animation.core.LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "auroraPhase",
    )

    drawWithCache {
        onDrawBehind {
            colors.forEachIndexed { index, color ->
                val offsetPhase = phase + index * 2.1f
                val radius = size.minDimension * (0.55f + 0.12f * sin(offsetPhase * 0.7f))
                val cx = size.width * (0.5f + 0.32f * cos(offsetPhase + index))
                val cy = size.height * (0.45f + 0.34f * sin(offsetPhase * 1.3f + index))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Fill,
                )
            }
        }
    }
}

/**
 * Scales a component down while it is pressed.
 *
 * Uses a spring so a quick tap-and-release doesn't snap: the release animation picks
 * up from wherever the press animation got to.
 */
@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.955f,
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = motionScale()
    val target = if (pressed) 1f - (1f - pressedScale) * scale.coerceIn(0.4f, 1.5f) else 1f
    return androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = VyxelMotion.snappy(),
        label = "pressScale",
    )
}

/** Applies [rememberPressScale] as a graphics-layer transform. */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.955f,
): Modifier = composed {
    val scale by rememberPressScale(interactionSource, pressedScale)
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades the top and bottom edges of a scrolling container so content dissolves
 * instead of being clipped hard.
 */
fun Modifier.fadingEdges(
    top: Dp = 0.dp,
    bottom: Dp = 32.dp,
): Modifier = composed {
    val density = LocalDensity.current
    val topPx = with(density) { top.toPx() }
    val bottomPx = with(density) { bottom.toPx() }

    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithCache {
            onDrawWithContent {
                drawContent()
                if (topPx > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startY = 0f,
                            endY = topPx,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                if (bottomPx > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = size.height - bottomPx,
                            endY = size.height,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
        }
}

/**
 * Staggered entrance: each item animates in slightly after the previous one.
 * [index] drives the delay, capped so long lists don't crawl.
 */
@Composable
fun rememberStaggeredEntrance(
    index: Int,
    key: Any? = Unit,
    perItemDelayMillis: Int = 45,
    maxDelayMillis: Int = 420,
): State<Float> {
    val progress = remember(key, index) { Animatable(0f) }
    val spec: AnimationSpec<Float> = VyxelMotion.expressive()
    LaunchedEffect(key, index) {
        try {
            kotlinx.coroutines.delay(
                (index * perItemDelayMillis).coerceAtMost(maxDelayMillis).toLong()
            )
            progress.animateTo(1f, spec)
        } finally {
            // Home rails are replaced as sources stream in (CDN first, then live),
            // and that recomposition cancels this effect mid-delay. Without this the
            // cards were left parked at alpha 0 — rail headers visible, contents
            // invisible. Never leave an item stuck hidden.
            if (progress.value < 1f) progress.snapTo(1f)
        }
    }
    return progress.asState()
}

/** Applies a staggered fade + rise to a list item. */
fun Modifier.staggeredEntrance(
    index: Int,
    key: Any? = Unit,
    riseDp: Dp = 22.dp,
): Modifier = composed {
    val progress by rememberStaggeredEntrance(index, key)
    val rise = with(LocalDensity.current) { riseDp.toPx() }
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * rise
    }
}

/** Vertical gradient scrim, used to keep text legible over artwork. */
fun Modifier.scrim(
    color: Color = Color.Black,
    startAlpha: Float = 0f,
    endAlpha: Float = 0.78f,
): Modifier = this.background(
    Brush.verticalGradient(
        colors = listOf(color.copy(alpha = startAlpha), color.copy(alpha = endAlpha)),
    )
)
