package com.vythera.vyxelapps.expressive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.Platform
import com.vythera.vyxelapps.expressive.data.model.SourceId
import com.vythera.vyxelapps.expressive.ui.components.icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import com.vythera.vyxelapps.expressive.ui.SearchUiState
import com.vythera.vyxelapps.expressive.ui.components.AppListRow
import com.vythera.vyxelapps.expressive.ui.components.EmptyState
import com.vythera.vyxelapps.expressive.ui.components.SourceFilterChip
import com.vythera.vyxelapps.expressive.ui.components.staggeredEntrance
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.glassSurface

/**
 * Cross-source search.
 *
 * Results stream in as one merged, deduplicated list rather than being grouped by
 * source — when you're looking for an app you care about the app, not which forge
 * it happens to live on. The source is shown as a badge on each row instead.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    enabledSources: Set<SourceId>,
    platformFilter: Platform?,
    onPlatformFilter: (Platform?) -> Unit,
    /** Narrows results to apps that drive Shizuku. */
    shizukuOnly: Boolean = false,
    onShizukuOnly: (Boolean) -> Unit = {},
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onItemClick: (AppItem) -> Unit,
    onToggleSource: (SourceId, Boolean) -> Unit,
    contentPadding: PaddingValues,
    /**
     * Previous queries, shared with Classic's list.
     *
     * The empty search screen was pure decoration before this — an icon and a
     * sentence. Classic has always offered the user's own history there, which is
     * the one thing on that screen anyone actually taps.
     */
    recentSearches: List<String> = emptyList(),
    onRecentClick: (String) -> Unit = {},
    onClearRecent: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current

    Column(modifier.fillMaxSize()) {
        Spacer(Modifier.height(contentPadding.calculateTopPadding()))

        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            onClear = onClear,
            focusRequester = focusRequester,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        AnimatedVisibility(visible = state.searching, enter = fadeIn(), exit = fadeOut()) {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        // Platform filter — parity with the Classic UI's Android/Windows/Linux row.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                PlatformChip(
                    label = xs.filterAll,
                    icon = null,
                    selected = platformFilter == null,
                    onClick = { onPlatformFilter(null) },
                )
            }
            items(Platform.entries.toList(), key = { it.name }) { platform ->
                PlatformChip(
                    label = when (platform) {
                        Platform.Android -> xs.filterAndroid
                        Platform.Linux -> xs.filterLinux
                        Platform.Windows -> xs.filterWindows
                        Platform.Module -> xs.filterModules
                    },
                    icon = platform.icon,
                    selected = platformFilter == platform,
                    onClick = {
                        onPlatformFilter(if (platformFilter == platform) null else platform)
                    },
                )
            }
            // Shizuku-only, asked for by users who wanted a way to find these at all.
            // Sits with the platform chips rather than the source chips because it
            // narrows *what kind of app*, not where the file came from.
            item(key = "shizuku") {
                PlatformChip(
                    label = xs.shizukuFilter,
                    icon = null,
                    selected = shizukuOnly,
                    onClick = { onShizukuOnly(!shizukuOnly) },
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SourceId.entries.toList(), key = { it.name }) { source ->
                SourceFilterChip(
                    source = source,
                    selected = source in enabledSources,
                    onClick = { onToggleSource(source, source !in enabledSources) },
                )
            }
        }

        when {
            state.query.isBlank() -> Column(Modifier.fillMaxWidth()) {
                if (recentSearches.isEmpty()) {
                    EmptyState(
                        title = xs.searchEverything,
                        subtitle = xs.searchEverythingDesc,
                        icon = Icons.Filled.Search,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    RecentSearches(recentSearches, onRecentClick, onClearRecent)
                }
            }

            state.results.isEmpty() && !state.searching && state.submitted -> EmptyState(
                title = xs.noMatches,
                subtitle = String.format(xs.noMatchesDesc, state.query),
                icon = Icons.Filled.SearchOff,
                modifier = Modifier.fillMaxWidth(),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 6.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "count") {
                    Text(
                        text = String.format(
                            if (state.results.size == 1) xs.resultCountOne else xs.resultCount,
                            state.results.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                // No staggered entrance here, and `animateItem` handles reordering.
                // Results are re-ranked every time a source answers, and a per-index
                // entrance animation replayed on each pass — rows visibly flashing
                // and sliding while the user was still typing.
                items(state.results, key = { it.id }) { item ->
                    AppListRow(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** Platform filter pill. Mirrors [SourceFilterChip] so the two rows read as a set. */
@Composable
private fun PlatformChip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(VyxelShapeTokens.Chip)
            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
            .border(
                BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
                VyxelShapeTokens.Chip,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        )
    }
}

/** The user's previous queries, newest first, each one a tap away from re-running. */
@Composable
private fun RecentSearches(
    queries: List<String>,
    onClick: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val s = com.vythera.vyxelapps.LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.recentSearches.uppercase(),
                style = com.vythera.vyxelapps.expressive.ui.theme.VyxelTextStyles.Overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = s.clearAll,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(VyxelShapeTokens.Pill)
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        queries.forEach { query ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(VyxelShapeTokens.Chip)
                    .clickable { onClick(query) }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    /** Overrides the default hint; the Modules screen searches modules, not apps. */
    placeholder: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                VyxelShapeTokens.SearchBar,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(12.dp))

        val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder ?: xs.searchPlaceholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = xs.clearLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
