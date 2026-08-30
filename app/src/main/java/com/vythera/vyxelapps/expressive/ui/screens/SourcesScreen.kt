package com.vythera.vyxelapps.expressive.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.data.model.SourceId
import com.vythera.vyxelapps.expressive.data.model.SourceState
import com.vythera.vyxelapps.expressive.ui.components.brandColor
import com.vythera.vyxelapps.expressive.ui.components.icon
import com.vythera.vyxelapps.expressive.ui.formatRelativeTime
import com.vythera.vyxelapps.expressive.ui.theme.VyxelMotion
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.VyxelTextStyles
import com.vythera.vyxelapps.expressive.ui.theme.glassSurface
import androidx.compose.material3.Icon
import com.vythera.vyxelapps.expressive.ui.components.staggeredEntrance

/**
 * Source manager: what's connected, what it returned, and what went wrong.
 *
 * Each card doubles as a live status readout — index syncs report progress here,
 * which matters because the F-Droid index is a ~12 MB download on first run.
 */
@Composable
fun SourcesScreen(
    enabledSources: Set<SourceId>,
    sourceStates: Map<SourceId, SourceState>,
    onToggle: (SourceId, Boolean) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Column(Modifier.padding(bottom = 6.dp, start = 4.dp)) {
                Text(
                    text = xs.tabSources,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = String.format(
                        xs.sourcesEnabledOf, enabledSources.size, SourceId.entries.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        itemsIndexed(SourceId.entries.toList(), key = { _, s -> s.name }) { index, source ->
            SourceCard(
                source = source,
                enabled = source in enabledSources,
                state = sourceStates[source] ?: SourceState.Idle,
                onToggle = { onToggle(source, it) },
                modifier = Modifier.staggeredEntrance(index, key = "sources"),
            )
        }

        item(key = "refresh") {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(VyxelShapeTokens.Pill)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onRefresh)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = xs.resyncAll,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: SourceId,
    enabled: Boolean,
    state: SourceState,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.55f,
        animationSpec = VyxelMotion.fade(),
        label = "sourceAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerLow, VyxelShapeTokens.Card)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(source.brandColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = source.platform.icon,
                    contentDescription = null,
                    tint = source.brandColor,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    // The full repository name: this row has the width for it, and
                    // the abbreviation that fits on a card is not self-explanatory.
                    text = source.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = source.platform.name,
                    style = VyxelTextStyles.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }

        Spacer(Modifier.height(12.dp))

        AnimatedContent(targetState = state, label = "sourceState") { current ->
            when (current) {
                is SourceState.Loading -> Column {
                    if (current.progress != null && current.progress > 0f) {
                        LinearWavyProgressIndicator(
                            progress = { current.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(6.dp))
                    StatusText(current.note ?: xs.syncing)
                }

                is SourceState.Ready -> StatusText(
                    String.format(
                        xs.appsSynced, current.count, formatRelativeTime(current.syncedAt),
                    )
                )

                is SourceState.Failed -> Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                SourceState.SearchOnly -> StatusText(
                    if (enabled) xs.sourceSearchOnly else xs.disabledLabel
                )

                SourceState.ScanOnly -> StatusText(
                    if (enabled) xs.sourceScanOnly else xs.disabledLabel
                )

                SourceState.Idle -> StatusText(
                    if (enabled) xs.waitingToSync else xs.disabledLabel
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
