package com.vythera.vyxelapps.expressive.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import kotlin.math.absoluteValue

/**
 * App artwork with a generated fallback.
 *
 * Several sources ship no icons at all (WinGet) or have gaps (bare git repos), so
 * rather than a grey box the fallback derives a stable two-tone gradient from the
 * app's name — the same app always gets the same tile.
 */
@Composable
fun AppIcon(
    item: AppItem,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = VyxelShapeTokens.Icon,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(monogramBrush(item.name)),
        contentAlignment = Alignment.Center,
    ) {
        val url = item.iconUrl
        if (url.isNullOrBlank()) {
            Monogram(item.monogram, size)
        } else {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .build(),
            )
            val state by painter.state.collectAsState()

            Crossfade(
                targetState = state is AsyncImagePainter.State.Success,
                label = "iconLoad",
            ) { loaded ->
                if (loaded) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (state is AsyncImagePainter.State.Error) Modifier
                                else Modifier.shimmer()
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state is AsyncImagePainter.State.Error) Monogram(item.monogram, size)
                    }
                }
            }
        }
    }
}

@Composable
private fun Monogram(letter: String, size: Dp) {
    Text(
        text = letter,
        color = Color.White.copy(alpha = 0.92f),
        fontWeight = FontWeight.Black,
        fontSize = (size.value * 0.42f).sp,
        style = MaterialTheme.typography.headlineMedium,
    )
}

/** Deterministic gradient derived from the app name. */
private fun monogramBrush(seed: String): Brush {
    val hash = seed.fold(7) { acc, c -> acc * 31 + c.code }.absoluteValue
    val hue = (hash % 360).toFloat()
    val hue2 = (hue + 38f) % 360f
    return Brush.linearGradient(
        listOf(
            Color.hsl(hue, 0.62f, 0.52f),
            Color.hsl(hue2, 0.58f, 0.38f),
        )
    )
}
