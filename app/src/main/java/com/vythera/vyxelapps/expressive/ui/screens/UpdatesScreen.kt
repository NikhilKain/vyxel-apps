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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.data.ScanRow
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.install.DownloadState
import com.vythera.vyxelapps.expressive.install.UpdateCandidate
import androidx.compose.foundation.lazy.items
import com.vythera.vyxelapps.expressive.ui.components.AppListRow
import com.vythera.vyxelapps.expressive.ui.theme.VyxelTextStyles
import com.vythera.vyxelapps.expressive.ui.components.EmptyState
import com.vythera.vyxelapps.expressive.ui.components.InstallAction
import com.vythera.vyxelapps.expressive.ui.components.InstallButton
import com.vythera.vyxelapps.expressive.ui.components.staggeredEntrance
import androidx.compose.foundation.layout.width
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.glassSurface

/**
 * Updates for apps already on the device.
 *
 * Only F-Droid-protocol sources participate: they publish a real `versionCode`,
 * which is the only reliable way to tell "newer" from "different". Matching a git
 * tag against an installed build would flag half the device as out of date.
 */
/** One manually tracked app: which repo it is pinned to, and the untrack action. */
@Composable
private fun TrackedRow(
    tracked: com.vythera.vyxelapps.TrackedApp,
    onRemove: () -> Unit,
) {
    val s = com.vythera.vyxelapps.LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(
                MaterialTheme.colorScheme.surfaceContainerLow,
                VyxelShapeTokens.Card,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = tracked.appName.ifBlank { tracked.packageName },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = tracked.repoFullName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = s.removeTracking,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clip(VyxelShapeTokens.Pill)
                .clickable(onClick = onRemove)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Pill action used for the two scan buttons. */
@Composable
private fun UpdateAction(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(VyxelShapeTokens.Pill)
            .background(
                if (primary) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
fun UpdatesScreen(
    updates: List<AppItem>,
    /**
     * Everything the multi-source scan matched, current entries included.
     *
     * Mirrors Classic's "updates from all sources" section: a scan that matches an
     * app and finds it current is a result worth showing, not nothing.
     */
    scanResults: List<ScanRow> = emptyList(),
    /** Apps installed through Vyxel — Classic's "Installed" list, shown here too. */
    installed: List<AppItem> = emptyList(),
    scanning: Boolean,
    downloadStates: Map<String, DownloadState>,
    onScan: () -> Unit,
    onScanAllSources: () -> Unit,
    onItemClick: (AppItem) -> Unit,
    onInstall: (AppItem) -> Unit,
    onCancel: (AppItem) -> Unit,
    contentPadding: PaddingValues,
    /**
     * Apps the user pinned to a repo by hand.
     *
     * The update scan can only match what a source index knows about; anything
     * side-loaded, renamed or published somewhere unindexed is invisible to it.
     * Tracking is the manual escape hatch, and Expressive had no route to it — the
     * feature existed but only Classic could reach it.
     */
    trackedApps: List<com.vythera.vyxelapps.TrackedApp> = emptyList(),
    onTrackApp: () -> Unit = {},
    onRemoveTracked: (String) -> Unit = {},
    /** Downloads and installs every pending update in one go. */
    onUpdateAll: () -> Unit = {},
    /** Drops history entries for apps that are no longer on the device. */
    onClearRemoved: () -> Unit = {},
    /**
     * Ordering for the installed list.
     *
     * Newest-first is the default because the common reason to open this list is to
     * find something just installed, but a long list is only navigable alphabetically
     * — which is why both are offered rather than one being chosen for the user.
     */
    installedSort: com.vythera.vyxelapps.expressive.data.InstalledSort =
        com.vythera.vyxelapps.expressive.data.InstalledSort.Recent,
    onInstalledSort: (com.vythera.vyxelapps.expressive.data.InstalledSort) -> Unit = {},
    onUninstall: (AppItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Scan once when the tab is first opened rather than on every app launch —
    // it walks the whole package list plus both indexes.
    LaunchedEffect(Unit) {
        if (updates.isEmpty() && !scanning) onScan()
    }

    val s = com.vythera.vyxelapps.LocalStrings.current
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    val outOfDate = updates.size + scanResults.count { it.hasUpdate }
    val nothingFound = updates.isEmpty() && scanResults.isEmpty()

    Column(modifier.fillMaxSize()) {
        Spacer(Modifier.height(contentPadding.calculateTopPadding() + 16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = xs.tabUpdates,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        scanning -> xs.scanningYourApps
                        outOfDate == 0 -> xs.everythingCurrent
                        else -> String.format(xs.countAvailable, outOfDate)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = scanning) {
                ContainedLoadingIndicator()
            }
        }

        Spacer(Modifier.height(14.dp))

        // Both of Classic's actions, which the Expressive shell was missing entirely.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UpdateAction(xs.searchForUpdates, Modifier.weight(1f), primary = true, onClick = onScan)
            UpdateAction(s.scanAllSources, Modifier.weight(1f), primary = false, onClick = onScanAllSources)
        }

        Spacer(Modifier.height(10.dp))

        // "Update all" only appears when there is something to update — a permanently
        // visible button that does nothing most of the time reads as broken.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (outOfDate > 0) {
                UpdateAction(s.updateAll, Modifier.weight(1f), primary = true, onClick = onUpdateAll)
            }
            UpdateAction(s.trackAppTitle, Modifier.weight(1f), primary = false, onClick = onTrackApp)
        }

        Spacer(Modifier.height(16.dp))

        // Apps pinned to a repo by hand, with the untrack action Classic has.
        if (trackedApps.isNotEmpty()) {
            Text(
                text = "${s.trackedAppsSection}  ·  ${trackedApps.size}",
                style = VyxelTextStyles.Overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 4.dp),
            )
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                trackedApps.forEach { tracked ->
                    TrackedRow(tracked, onRemove = { onRemoveTracked(tracked.id) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // One list from here down.
        //
        // The installed section used to sit above this in the enclosing Column as a
        // horizontal rail, which capped it at whatever fitted on one line and made a
        // long list a sideways swipe. Users asked for the Classic shell's vertical
        // list back, and a vertical list of arbitrary length has to be inside the
        // LazyColumn or it doesn't scroll at all.
        val sortedInstalled = remember(installed, installedSort) {
            when (installedSort) {
                com.vythera.vyxelapps.expressive.data.InstalledSort.Name ->
                    installed.sortedBy { it.displayName.lowercase() }
                com.vythera.vyxelapps.expressive.data.InstalledSort.Recent ->
                    installed.sortedByDescending { it.updatedAt }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(updates, key = { _, u -> u.id }) { index, item ->
                AppListRow(
                    item = item,
                    onClick = { onItemClick(item) },
                    modifier = Modifier.staggeredEntrance(index, key = "updates"),
                    trailing = {
                        InstallButton(
                            action = InstallAction.Update,
                            state = downloadStates[item.id] ?: DownloadState.Idle,
                            onInstall = { onInstall(item) },
                            onOpen = {},
                            onCancel = { onCancel(item) },
                            compact = true,
                        )
                    },
                )
            }

            // Everything the cross-source scan matched. Current entries are shown
            // with a check rather than hidden, so the scan visibly reports back.
            if (scanResults.isNotEmpty()) {
                item(key = "scanHeader") {
                    Text(
                        text = "${xs.fromAllSources}  ·  ${scanResults.size}",
                        style = VyxelTextStyles.Overline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                }
                itemsIndexed(scanResults, key = { _, r -> "scanRow:" + r.item.id }) { index, row ->
                    val item = row.item
                    AppListRow(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.staggeredEntrance(index, key = "scan"),
                        trailing = {
                            if (row.hasUpdate) {
                                InstallButton(
                                    action = InstallAction.Update,
                                    state = downloadStates[item.id] ?: DownloadState.Idle,
                                    onInstall = { onInstall(item) },
                                    onOpen = {},
                                    onCancel = { onCancel(item) },
                                    compact = true,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = xs.upToDate,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            }

            // Installed-via-Vyxel list, so both shells describe the same device.
            if (sortedInstalled.isNotEmpty()) {
                item(key = "installedHeader") {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${xs.installedViaVyxel}  ·  ${sortedInstalled.size}",
                                style = VyxelTextStyles.Overline,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            // Prunes entries for apps the user has since uninstalled,
                            // which otherwise sit in this list forever.
                            Text(
                                text = s.clearRemoved,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(VyxelShapeTokens.Pill)
                                    .clickable(onClick = onClearRemoved)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        Row(
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = xs.sortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SortChip(
                                label = xs.sortByRecent,
                                selected = installedSort ==
                                    com.vythera.vyxelapps.expressive.data.InstalledSort.Recent,
                                onClick = {
                                    onInstalledSort(
                                        com.vythera.vyxelapps.expressive.data.InstalledSort.Recent
                                    )
                                },
                            )
                            SortChip(
                                label = xs.sortByName,
                                selected = installedSort ==
                                    com.vythera.vyxelapps.expressive.data.InstalledSort.Name,
                                onClick = {
                                    onInstalledSort(
                                        com.vythera.vyxelapps.expressive.data.InstalledSort.Name
                                    )
                                },
                            )
                        }
                    }
                }
                items(sortedInstalled, key = { "installed:" + it.id }) { item ->
                    AppListRow(
                        item = item,
                        onClick = { onItemClick(item) },
                        trailing = {
                            Text(
                                text = s.uninstall,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(VyxelShapeTokens.Pill)
                                    .clickable { onUninstall(item) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        },
                    )
                }
            }

            if (nothingFound && sortedInstalled.isEmpty() && !scanning) {
                item(key = "empty") {
                    Column {
                        EmptyState(
                            title = xs.allUpToDate,
                            subtitle = xs.allUpToDateDesc,
                            icon = Icons.Filled.CheckCircle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .fillMaxWidth()
                                .glassSurface(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    VyxelShapeTokens.Pill,
                                )
                                .clickable(onClick = onScan)
                                .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = xs.scanAgain,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Small selectable pill used by the installed list's sort row. */
@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(VyxelShapeTokens.Pill)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
