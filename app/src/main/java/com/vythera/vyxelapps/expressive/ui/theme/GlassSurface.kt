package com.vythera.vyxelapps.expressive.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Fills a surface and clips its children to [shape].
 *
 * In the paid build this samples a captured wallpaper layer through a blur and a lens
 * distortion, which is what makes a card read as glass under a Liquid Glass skin. That
 * renderer is not part of the open core, and the original already degraded to a plain
 * colour fill wherever no backdrop had been captured — every non-glass skin, and any
 * device where the capture was unavailable. This is that path, and only that path.
 *
 * A drop-in replacement for `clip(shape).background(color)`, so callers use it
 * unconditionally either way.
 */
@Composable
fun Modifier.glassSurface(color: Color, shape: Shape): Modifier =
    this.clip(shape).background(color)
