package com.vythera.vyxelapps.expressive.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import com.vythera.vyxelapps.expressive.data.model.SourceState
import com.vythera.vyxelapps.expressive.ui.HomeUiState
import com.vythera.vyxelapps.expressive.ui.components.AppRailCard
import com.vythera.vyxelapps.expressive.ui.components.AppRailCardSkeleton
import com.vythera.vyxelapps.expressive.ui.components.EmptyState
import com.vythera.vyxelapps.expressive.ui.components.HeroCarousel
import com.vythera.vyxelapps.expressive.ui.components.SectionHeader
import com.vythera.vyxelapps.expressive.ui.components.SourceErrorChip
import com.vythera.vyxelapps.expressive.ui.components.shimmer
import com.vythera.vyxelapps.expressive.ui.components.staggeredEntrance
import com.vythera.vyxelapps.expressive.ui.theme.VyxelMotion
import com.vythera.vyxelapps.expressive.ui.theme.VyxelTextStyles

/**
 * The store front.
 *
 * The greeting block is part of the scrolling content rather than a pinned app bar —
 * it fades and drifts as the list moves, so the hero carousel gets the full width of
 * the screen once the user starts browsing.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    sourceStates: Map<SourceId, SourceState>,
    onItemClick: (AppItem) -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    /**
     * Opens the Modules browser.
     *
     * One doorway rather than a rail of module tiles: modules are a mode, not a
     * category, and the people who want them want to filter by family and search
     * properly rather than swipe past four of them on the way to something else.
     */
    onOpenModules: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Drives the greeting's parallax; derivedStateOf keeps this off the recomposition
    // path for every pixel of scroll.
    val headerCollapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 320f).coerceIn(0f, 1f)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item(key = "greeting") {
            Greeting(
                collapse = headerCollapse,
                sourceCount = sourceStates.count { it.value is SourceState.Ready },
            )
        }

        if (state.hero.isNotEmpty()) {
            item(key = "hero") {
                HeroCarousel(items = state.hero, onItemClick = onItemClick)
            }
        }

        // Directly under the hero: high enough to be found, and out of the way of
        // the app rails that most visits are actually here for.
        item(key = "modulesEntry") {
            ModulesEntryCard(onClick = onOpenModules)
        }

        // Per-source failures belong on the Sources screen, not on Discover.
        //
        // A red "IzzyOnDroid · Timed out · Try Again" bar directly under the hero
        // names infrastructure the reader did not choose and cannot fix, on a page
        // that is otherwise full of working content. It is only worth interrupting
        // the browse with when there is nothing else to show — when every source
        // failed, the screen is empty anyway and the error IS the content.
        val failures = sourceStates.entries
            .filter { it.value is SourceState.Failed }
            .takeIf { state.rails.isEmpty() }
            .orEmpty()
        if (failures.isNotEmpty()) {
            item(key = "failures") {
                Column(
                    Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    failures.forEach { (source, failure) ->
                        SourceErrorChip(
                            source = source,
                            message = (failure as SourceState.Failed).message,
                            onRetry = onRetry,
                        )
                    }
                }
            }
        }

        itemsIndexed(state.rails, key = { _, rail -> rail.title }) { railIndex, rail ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader(
                    title = rail.title,
                    subtitle = rail.subtitle,
                    source = rail.source,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(rail.items, key = { _, item -> item.id }) { index, item ->
                        AppRailCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            modifier = Modifier.staggeredEntrance(
                                index = index,
                                key = rail.title,
                            ),
                        )
                    }
                }
            }
        }

        // A placeholder rail for every source still in flight.
        //
        // Sources land between 0.5s and 11s apart, and rails used to simply
        // append as they arrived — so the page grew under the reader's thumb for
        // ten seconds. Holding a slot for each pending source keeps the page its
        // final height from the first frame: content fills in, nothing moves.
        val pending = sourceStates.count { it.value is SourceState.Loading }
        if (pending > 0) {
            items(pending, key = { "pending$it" }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SkeletonHeader()
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false,
                    ) {
                        items(4) { AppRailCardSkeleton() }
                    }
                }
            }
        }

        if (!state.loading && state.rails.isEmpty()) {
            item(key = "empty") {
                val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
                EmptyState(
                    title = xs.nothingToShow,
                    subtitle = state.error ?: xs.nothingToShowDesc,
                    icon = Icons.Filled.CloudOff,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "tail") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Greeting(collapse: Float, sourceCount: Int) {
    val alpha by animateFloatAsState(
        targetValue = 1f - collapse * 0.85f,
        animationSpec = VyxelMotion.fade(120),
        label = "greetingAlpha",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = -collapse * 26f
            },
    ) {
        val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
        Text(
            text = xs.brandWordmark,
            style = VyxelTextStyles.Overline,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = xs.heroTagline,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                sourceCount == 1 -> String.format(xs.sourceConnectedOne, sourceCount)
                sourceCount > 1 -> String.format(xs.sourcesConnected, sourceCount)
                else -> xs.connectingToSources
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SkeletonHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(24.dp)
                .fillMaxWidth(0.45f)
                .clip(RoundedCornerShape(12.dp))
                .shimmer()
        )
    }
}
