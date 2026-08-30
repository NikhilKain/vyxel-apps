package com.vythera.vyxelapps.expressive.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.ui.theme.LocalVyxelAccents
import com.vythera.vyxelapps.expressive.ui.theme.VyxelMotion
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.VyxelTextStyles
import com.vythera.vyxelapps.expressive.ui.theme.glassSurface
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/**
 * Auto-advancing hero pager.
 *
 * Neighbouring pages are visible and scaled down by their scroll offset, so a drag
 * reads as a physical stack rather than a slideshow. Auto-advance pauses while the
 * user is actually touching the pager.
 */
@Composable
fun HeroCarousel(
    items: List<AppItem>,
    onItemClick: (AppItem) -> Unit,
    modifier: Modifier = Modifier,
    autoAdvanceMillis: Long = 5200L,
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState, items.size, dragged) {
        if (dragged || items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(autoAdvanceMillis)
            if (pagerState.pageCount == 0) break
            val next = (pagerState.currentPage + 1) % pagerState.pageCount
            pagerState.animateScrollToPage(next)
        }
    }

    Column(modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 14.dp,
        ) { page ->
            val offset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).absoluteValue

            HeroCard(
                item = items[page],
                onClick = { onItemClick(items[page]) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .graphicsLayer {
                        val scale = lerp(0.88f, 1f, 1f - offset.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(0.55f, 1f, 1f - offset.coerceIn(0f, 1f))
                    },
            )
        }

        Spacer(Modifier.height(14.dp))
        PagerDots(pagerState, items.size, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun HeroCard(
    item: AppItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalVyxelAccents.current
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction, pressedScale = 0.97f)

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerHigh, VyxelShapeTokens.Hero)
            .auroraGlow(
                colors = listOf(accents.glowA, accents.glowB, accents.glowC),
                alpha = 0.34f,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(item = item, size = 62.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.promoLabel?.uppercase()
                            ?: com.vythera.vyxelapps.LocalStrings.current.featured.uppercase(),
                        style = VyxelTextStyles.Overline,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = item.displaySummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SourceBadge(item.source)
                item.license?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Dots that stretch into a pill for the active page. */
@Composable
private fun PagerDots(state: PagerState, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = state.currentPage == index
            val width by animateDpAsState(
                targetValue = if (selected) 22.dp else 7.dp,
                animationSpec = VyxelMotion.expressive(),
                label = "dotWidth",
            )
            val alpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0.32f,
                animationSpec = VyxelMotion.fade(),
                label = "dotAlpha",
            )
            Box(
                Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
