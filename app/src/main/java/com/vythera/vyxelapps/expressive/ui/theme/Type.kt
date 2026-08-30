package com.vythera.vyxelapps.expressive.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

private val Display = FontFamily.Default
private val Body = FontFamily.Default

/**
 * Expressive type scale. Display and headline roles are pushed heavier and tighter
 * than baseline M3 so section headers carry the page without needing extra chrome.
 */
val VyxelTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Black,
        fontSize = 54.sp, lineHeight = 60.sp, letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
        fontSize = 43.sp, lineHeight = 50.sp, letterSpacing = (-0.9).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp,
    ),

    headlineLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 25.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),

    titleLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.3.sp,
    ),

    labelLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp,
    ),
)

/** Extra styles that don't map onto a standard M3 role. */
data class VyxelTextStyleSet(
    val RailHeader: TextStyle,
    val Overline: TextStyle,
    val StatValue: TextStyle,
)

val DefaultTextStyles = VyxelTextStyleSet(
    RailHeader = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp,
    ),
    Overline = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp,
    ),
    StatValue = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Black,
        fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = (-0.3).sp,
        textAlign = TextAlign.Center,
    ),
)

val LocalVyxelTextStyles = androidx.compose.runtime.staticCompositionLocalOf {
    DefaultTextStyles
}

/**
 * These styles sit outside the Material type scale, so swapping `Typography` alone
 * leaves them in the stock font — which is why rail headers stayed sans-serif under
 * Cyberpunk while everything around them turned to Orbitron. Routing them through a
 * `CompositionLocal` lets a skin restyle them along with the rest.
 */
val VyxelTextStyles: VyxelTextStyleSet
    @Composable get() = LocalVyxelTextStyles.current
