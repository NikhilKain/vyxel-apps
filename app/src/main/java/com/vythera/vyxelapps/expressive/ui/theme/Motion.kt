package com.vythera.vyxelapps.expressive.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Motion tokens for the store.
 *
 * Everything the user can touch animates on a spring — springs stay interruptible,
 * so a card that is mid-expand can be flung back without the snap you get from a
 * duration-based tween. Tweens are kept only for non-interactive decoration
 * (shimmer, ambient gradients) where a fixed cadence is actually what you want.
 */
object VyxelMotion {

    // --- Easing (decorative / non-interruptible only) ---
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // --- Durations ---
    const val DurationFast = 180
    const val DurationMedium = 320
    const val DurationSlow = 520
    const val DurationExtraSlow = 900

    // --- Spring specs ---

    /** Snappy, minimal overshoot — chips, icons, small toggles. */
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = 1400f,
    )

    /** The default for cards and containers: a touch of bounce, settles quickly. */
    fun <T> expressive(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = 480f,
    )

    /** Playful overshoot for hero moments — FAB expansion, celebratory states. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.52f,
        stiffness = 380f,
    )

    /** Heavy, smooth glide for large surfaces (sheets, full-screen containers). */
    fun <T> smooth(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 260f,
    )

    /** Spatial spring tuned for offsets — avoids visible overshoot on long travel. */
    val OffsetSpring: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.82f,
        stiffness = 420f,
        visibilityThreshold = IntOffset(1, 1),
    )

    /** Spring for size changes; pairs with `animateContentSize`. */
    val SizeSpring: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = 0.8f,
        stiffness = 420f,
        visibilityThreshold = IntSize(1, 1),
    )

    /** Alpha fades ride a tween — fades read as cleaner without bounce. */
    fun <T> fade(duration: Int = DurationMedium): FiniteAnimationSpec<T> =
        tween(durationMillis = duration, easing = StandardEasing)
}
