package com.vythera.vyxelapps.expressive.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Expressive shape scale — noticeably rounder than the baseline M3 scale, which is
 * what gives the store its soft, pill-forward silhouette.
 */
val VyxelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Shapes used by specific surfaces that sit outside the standard scale.
 *
 * Reads the active [VyxelShapeSet] so a skin can change the shell's silhouette —
 * Cyberpunk chamfers every one of these into a HUD panel. Composable rather than a
 * plain object for exactly that reason; see [LocalVyxelShapes].
 */
val VyxelShapeTokens: VyxelShapeSet
    @Composable get() = LocalVyxelShapes.current
