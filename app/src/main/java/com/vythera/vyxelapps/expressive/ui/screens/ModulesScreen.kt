package com.vythera.vyxelapps.expressive.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.ui.components.AppListRow
import com.vythera.vyxelapps.expressive.ui.components.EmptyState
import com.vythera.vyxelapps.expressive.ui.components.staggeredEntrance
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.VyxelTextStyles
import com.vythera.vyxelapps.expressive.ui.theme.glassSurface

/**
 * The families a module can belong to, in the order they are offered.
 *
 * `null` is "everything" and leads, because most visits start by browsing rather
 * than by knowing which manager you are on.
 */
private val FAMILIES = listOf(null, "Magisk", "Zygisk", "LSPosed", "KernelSU")

/**
 * A dedicated browser for root modules.
 *
 * Modules are a different kind of thing from apps — different install path, different
 * prerequisites, different vocabulary — so they get their own screen rather than
 * competing with apps for row space in search. The shape follows Modex: search across
 * the resident catalogue, filter by family, and a plain vertical list underneath.
 *
 * Everything here is served from memory once the catalogue has loaded, so filtering
 * and typing are instant and work offline.
 */
@Composable
fun ModulesScreen(
    modules: List<AppItem>,
    loading: Boolean,
    onItemClick: (AppItem) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    val s = com.vythera.vyxelapps.LocalStrings.current

    var query by remember { mutableStateOf("") }
    var family by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onRefresh() }

    val visible = remember(modules, query, family) {
        val needle = query.trim().lowercase()
        modules.asSequence()
            .filter { family == null || it.categories.firstOrNull() == family }
            .filter { item ->
                needle.isBlank() ||
                    item.name.lowercase().contains(needle) ||
                    item.packageName.orEmpty().lowercase().contains(needle) ||
                    item.summary.lowercase().contains(needle) ||
                    item.author.orEmpty().lowercase().contains(needle)
            }
            .toList()
    }

    Column(modifier.fillMaxSize()) {
        Spacer(Modifier.height(contentPadding.calculateTopPadding()))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 24.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(VyxelShapeTokens.Pill)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = s.back,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = xs.modulesTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        loading && modules.isEmpty() -> xs.modulesLoading
                        else -> String.format(xs.modulesCount, visible.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = loading) { ContainedLoadingIndicator() }
        }

        Spacer(Modifier.height(12.dp))

        // Shared with the Search tab so the two search boxes are the same control.
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onClear = { query = "" },
            focusRequester = remember { androidx.compose.ui.focus.FocusRequester() },
            placeholder = xs.modulesSearchHint,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(FAMILIES, key = { it ?: "all" }) { entry ->
                FamilyChip(
                    label = entry ?: xs.filterAll,
                    selected = family == entry,
                    onClick = { family = if (family == entry) null else entry },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty() && !loading) {
            EmptyState(
                title = xs.modulesEmpty,
                subtitle = xs.modulesEmptyDesc,
                icon = Icons.Filled.Extension,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(visible, key = { _, m -> m.id }) { index, item ->
                    AppListRow(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.staggeredEntrance(index, key = "modules"),
                        trailing = {
                            // The family, not an install button: nothing on this
                            // screen can be installed without a root manager, and a
                            // button that never works is worse than no button.
                            Text(
                                text = item.categories.firstOrNull().orEmpty(),
                                style = VyxelTextStyles.Overline,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(VyxelShapeTokens.Pill)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Family selector pill. */
@Composable
private fun FamilyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(VyxelShapeTokens.Chip)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/**
 * The home-screen doorway to this screen.
 *
 * One wide card rather than a rail of module tiles. Modules are a mode, not a
 * category: someone who wants one wants to browse and filter them properly, and
 * someone who does not should be able to skip the whole subject in one glance
 * instead of scrolling a rail of things their phone probably cannot flash.
 */
@Composable
fun ModulesEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerLow, VyxelShapeTokens.Card)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(VyxelShapeTokens.Icon)
                .background(
                    com.vythera.vyxelapps.expressive.ui.theme.SourceColors.MagiskAlt
                        .copy(alpha = 0.20f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Extension,
                contentDescription = null,
                tint = com.vythera.vyxelapps.expressive.ui.theme.SourceColors.MagiskAlt,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = xs.modulesTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = xs.modulesEntryDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
