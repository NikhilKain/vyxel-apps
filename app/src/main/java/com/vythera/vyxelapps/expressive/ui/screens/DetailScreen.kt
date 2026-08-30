package com.vythera.vyxelapps.expressive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.Platform
import com.vythera.vyxelapps.expressive.install.DownloadState
import com.vythera.vyxelapps.expressive.ui.components.AppIcon
import com.vythera.vyxelapps.expressive.ui.components.InstallAction
import com.vythera.vyxelapps.expressive.ui.components.InstallButton
import com.vythera.vyxelapps.expressive.ui.components.SourceBadge
import com.vythera.vyxelapps.expressive.ui.components.auroraGlow
import com.vythera.vyxelapps.expressive.ui.components.brandColor
import com.vythera.vyxelapps.expressive.data.source.isoToEpochMillis
import com.vythera.vyxelapps.expressive.ui.formatCount
import com.vythera.vyxelapps.expressive.ui.formatRelativeTime
import com.vythera.vyxelapps.expressive.ui.formatSize
import com.vythera.vyxelapps.expressive.ui.theme.LocalVyxelAccents
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.VyxelTextStyles
import com.vythera.vyxelapps.expressive.ui.theme.glassSurface

/**
 * Full app detail.
 *
 * Desktop entries (Flathub, WinGet) intentionally show a copyable install command
 * instead of an install button — surfacing a disabled button for something that
 * fundamentally can't run on the device would be misleading.
 */
@Composable
fun DetailScreen(
    item: AppItem,
    resolving: Boolean,
    installAction: InstallAction,
    downloadState: DownloadState,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopy: (String) -> Unit,
    contentPadding: PaddingValues,
    /** Description rendered in the chosen language; null until the user asks. */
    translatedDescription: String? = null,
    /** "What's New" in the chosen language; null until the user asks. */
    translatedChangelog: String? = null,
    translating: Boolean = false,
    onTranslate: () -> Unit = {},
    onUndoTranslate: () -> Unit = {},
    /** Every published release, newest first — drives the version picker. */
    releases: List<com.vythera.vyxelapps.Release> = emptyList(),
    selectedRelease: com.vythera.vyxelapps.Release? = null,
    loadingReleases: Boolean = false,
    /**
     * Why the release lookup came back empty, when it did.
     *
     * Without this the install button just reads "No APK", which is a statement about
     * the app. It is usually a statement about the request: an exhausted GitHub quota,
     * a rejected token, or no connection. Those are all fixable by the user, and none
     * of them are fixable if the screen refuses to name them.
     */
    releaseError: String? = null,
    onSelectRelease: (com.vythera.vyxelapps.Release) -> Unit = {},
    onInstallRelease: (com.vythera.vyxelapps.Release) -> Unit = {},
    /**
     * Favourites, shared with Classic's list.
     *
     * Expressive could display favourites once the library screen existed but had no
     * way to *create* one, which made the list permanently empty for anyone who lives
     * in this shell.
     */
    isFavourite: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    /**
     * Classic's trust score for this app, already computed by its release lookup.
     *
     * Expressive had no equivalent and simply didn't show it, so the same app looked
     * differently vetted depending on which shell you opened it in.
     */
    trustScore: com.vythera.vyxelapps.TrustScore? = null,
    /**
     * Removal, which this shell had no control for at all.
     *
     * Installing from Expressive and then having to leave for Android Settings — or
     * for the Classic shell — to undo it was the single most reported gap.
     */
    onUninstall: () -> Unit = {},
    /** Whether this app is on the user's hidden list. */
    isHidden: Boolean = false,
    onToggleHidden: () -> Unit = {},
    /** Saves a root module's zip to Downloads, for flashing by hand. */
    onDownloadZip: () -> Unit = {},
    /** Flashes the module through the device's own root manager. */
    onInstallModule: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accents = LocalVyxelAccents.current
    val s = com.vythera.vyxelapps.LocalStrings.current
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    var descriptionExpanded by remember(item.id) { mutableStateOf(false) }
    // Which screenshot is open full-screen, or null. Reset per app so returning to a
    // different detail page never opens the previous app's gallery.
    var shotFullscreen by remember(item.id) { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + 40.dp,
        ),
    ) {
        item(key = "header") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .auroraGlow(
                        colors = listOf(item.source.brandColor, accents.glowA, accents.glowB),
                        alpha = 0.30f,
                    )
                    .padding(
                        top = contentPadding.calculateTopPadding() + 8.dp,
                        bottom = 22.dp,
                    ),
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BackButton(onBack)
                        Spacer(Modifier.weight(1f))
                        FavouriteButton(isFavourite, onToggleFavourite)
                    }
                    Spacer(Modifier.height(10.dp))

                    Row(
                        Modifier.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(item = item, size = 84.dp, shape = VyxelShapeTokens.Card)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.author?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SourceBadge(item.source)
                                // Shizuku is a setup requirement, not a nice-to-have:
                                // without it these apps do nothing on first launch, so
                                // it belongs next to the source, before the install tap.
                                if (item.usesShizuku) {
                                    Text(
                                        text = xs.shizukuBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .clip(VyxelShapeTokens.Pill)
                                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    if (item.platform == Platform.Android) {
                        Box(Modifier.padding(horizontal = 24.dp)) {
                            InstallButton(
                                action = installAction,
                                state = downloadState,
                                onInstall = onInstall,
                                onOpen = onOpen,
                                onCancel = onCancel,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Uninstall only exists once there is something to remove;
                            // "Open" is what the install button shows in that state.
                            if (installAction == InstallAction.Open ||
                                installAction == InstallAction.Update
                            ) {
                                SecondaryAction(
                                    label = s.uninstall,
                                    modifier = Modifier.weight(1f),
                                    destructive = true,
                                    onClick = onUninstall,
                                )
                            }
                            SecondaryAction(
                                label = if (isHidden) xs.unhideApp else xs.hideApp,
                                modifier = Modifier.weight(1f),
                                destructive = false,
                                onClick = onToggleHidden,
                            )
                        }
                    } else if (item.platform == Platform.Module) {
                        // A module is a zip a root manager flashes, so there is no
                        // Install button to offer — the honest actions are "give me
                        // the file" and "hand this to something that can flash it".
                        Column(Modifier.padding(horizontal = 24.dp)) {
                            Text(
                                text = xs.moduleNeedsRoot,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            // Install is the primary action even when root has not
                            // been granted yet: tapping it is what triggers the su
                            // prompt, and the console then reports exactly what the
                            // root manager said. Hiding it until root is confirmed
                            // would mean probing for su on every module page.
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(VyxelShapeTokens.Pill)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable(onClick = onInstallModule)
                                    .padding(vertical = 15.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = xs.moduleInstall,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryAction(
                                    label = xs.moduleDownloadZip,
                                    modifier = Modifier.weight(1f),
                                    destructive = false,
                                    onClick = onDownloadZip,
                                )
                                SecondaryAction(
                                    label = if (isHidden) xs.unhideApp else xs.hideApp,
                                    modifier = Modifier.weight(1f),
                                    destructive = false,
                                    onClick = onToggleHidden,
                                )
                            }
                        }
                    } else {
                        item.installCommand?.let { command ->
                            InstallCommandCard(
                                command = command,
                                onCopy = { onCopy(command) },
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }

                    AnimatedVisibility(visible = resolving) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            LinearWavyProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = s.checkingRelease,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }
            }
        }

        if (item.screenshots.isNotEmpty()) {
            item(key = "shots") {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(item.screenshots, key = { _, url -> url }) { index, url ->
                        Image(
                            painter = rememberAsyncImagePainter(url),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(300.dp)
                                .width(160.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                // A 160dp-wide thumbnail is unreadable for a phone
                                // screenshot; tapping opens the full-size gallery,
                                // which is what Classic has always done.
                                .clickable { shotFullscreen = index },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        item(key = "stats") {
            StatStrip(item, Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(22.dp))
        }

        if (item.description.isNotBlank()) {
            item(key = "about") {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = s.about,
                            style = VyxelTextStyles.RailHeader,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        // Same affordance as Classic: translate on demand, and tap
                        // again to go back to the author's own wording.
                        Text(
                            text = when {
                                translating -> xs.translating
                                translatedDescription != null -> s.translatedRedo
                                else -> s.translate
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(enabled = !translating) {
                                if (translatedDescription != null) onUndoTranslate()
                                else onTranslate()
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val body = translatedDescription ?: item.description
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .animateContentSize()
                            .clickable { descriptionExpanded = !descriptionExpanded },
                    )
                    if (body.length > 280) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (descriptionExpanded) s.showLess else s.readMore,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                descriptionExpanded = !descriptionExpanded
                            },
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        if (item.antiFeatures.isNotEmpty()) {
            item(key = "anti") {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = xs.thingsToKnow,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    item.antiFeatures.forEach { flag ->
                        Text(
                            text = "•  ${flag.humanizeAntiFeature()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        if (!item.changelog.isNullOrBlank()) {
            item(key = "changelog") {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = s.whatsNew,
                        style = VyxelTextStyles.RailHeader,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = translatedChangelog ?: item.changelog.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 14,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        if (trustScore != null) {
            item(key = "trust") {
                TrustCard(trustScore, Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(22.dp))
            }
        }

        // Everything the source told us about the app, beyond the four headline stats.
        item(key = "details") {
            AppFactsCard(item, Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(22.dp))
        }

        // Releases and the version picker are an APK feature, and are hidden for
        // modules.
        //
        // Their "Install this" routes through Classic's installer, which hands the
        // asset to PackageInstaller — so on a module it would feed a flashable zip
        // to the package manager and fail with a parse error that looks like the
        // module is broken. Modules install from the button above, through the root
        // manager, and their version history is not fetched at all.
        val isModule = item.platform == Platform.Module
        val shown = (selectedRelease ?: releases.firstOrNull()).takeUnless { isModule }
        if (shown != null) {
            item(key = "release") {
                ReleaseCard(shown, Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(22.dp))
            }
        }

        // Only when there is nothing to install: an app that resolved fine has no
        // reason to carry a warning, even if some earlier attempt failed.
        if (!isModule && shown == null && !loadingReleases && !releaseError.isNullOrBlank()) {
            item(key = "releaseError") {
                Text(
                    text = releaseError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(22.dp))
            }
        }

        // Version history. The whole point is being able to go *back* — a bad update
        // is the most common reason someone opens this screen at all.
        if (!isModule && (loadingReleases || releases.size > 1)) {
            item(key = "versionsHeader") {
                Row(
                    Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = xs.allVersions,
                        style = VyxelTextStyles.RailHeader,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (loadingReleases) {
                        Text(
                            text = s.checkingRelease,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            itemsIndexed(releases, key = { _, r -> "rel:" + r.tag_name }) { index, release ->
                VersionRow(
                    release = release,
                    isLatest = index == 0,
                    isSelected = release.tag_name == shown?.tag_name,
                    onClick = { onSelectRelease(release) },
                    onInstall = { onInstallRelease(release) },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 3.dp),
                )
            }
            item(key = "versionsTail") { Spacer(Modifier.height(22.dp)) }
        }

        item(key = "links") {
            Column(
                Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.website?.let {
                    LinkRow(Icons.Filled.Language, xs.website, it) { onOpenUrl(it) }
                }
                item.sourceCodeUrl?.let {
                    LinkRow(Icons.Filled.Code, s.viewSourceCode, it) { onOpenUrl(it) }
                }
                item.donateUrl?.let {
                    LinkRow(Icons.Filled.Favorite, xs.donate, it) { onOpenUrl(it) }
                }
            }
        }
    }

    // Classic's own pinch-zoom gallery rather than a second implementation — it is a
    // Dialog, so it lays itself over the whole screen from here.
    shotFullscreen?.let { index ->
        com.vythera.vyxelapps.FullScreenImageViewer(
            urls = item.screenshots,
            initialIndex = index,
            onDismiss = { shotFullscreen = null },
        )
    }
}

/**
 * Outlined pill for the actions that sit under the primary install button.
 *
 * Deliberately quieter than [InstallButton]: uninstalling and hiding are things a
 * user does occasionally and by intent, and giving them equal weight to Install would
 * invite mis-taps on the one control here that is hard to undo.
 */
@Composable
private fun SecondaryAction(
    label: String,
    destructive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(VyxelShapeTokens.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(42.dp)
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = com.vythera.vyxelapps.LocalStrings.current.back,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Heart toggle, filled once the app is in the shared favourites list. */
@Composable
private fun FavouriteButton(isFavourite: Boolean, onClick: () -> Unit) {
    val s = com.vythera.vyxelapps.LocalStrings.current
    Box(
        Modifier
            .size(42.dp)
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFavourite) Icons.Filled.Favorite
            else Icons.Filled.FavoriteBorder,
            contentDescription = s.favourites,
            tint = if (isFavourite) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Version / size / updated / license, skipping whatever the source didn't provide. */
@Composable
private fun StatStrip(item: AppItem, modifier: Modifier = Modifier) {
    val s = com.vythera.vyxelapps.LocalStrings.current
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    val stats = buildList {
        item.version?.takeIf { it.isNotBlank() }?.let { add(s.version to it) }
        formatSize(item.sizeBytes).takeIf { it.isNotBlank() }?.let { add(xs.statSize to it) }
        formatRelativeTime(item.updatedAt).takeIf { it.isNotBlank() }
            ?.let { add(xs.statUpdated to it) }
        item.license?.takeIf { it.isNotBlank() }?.let { add(xs.statLicense to it) }
        item.minSdk.takeIf { it > 0 }?.let { add(xs.statMinAndroid to "API $it") }
    }
    if (stats.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(stats, key = { it.first }) { (label, value) ->
            Column(
                Modifier
                    .glassSurface(
                        MaterialTheme.colorScheme.surfaceContainer,
                        VyxelShapeTokens.Card,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = label.uppercase(),
                    style = VyxelTextStyles.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The facts a store page is expected to carry — package id, developer, categories,
 * popularity — which the header alone has no room for.
 *
 * Rows for fields the source did not provide are skipped rather than shown empty; a
 * catalogue entry with half the metadata missing looks broken when rendered as blanks.
 */
@Composable
private fun AppFactsCard(item: AppItem, modifier: Modifier = Modifier) {
    val s = com.vythera.vyxelapps.LocalStrings.current
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current

    val facts = buildList {
        // The CDN addresses GitHub entries by their numeric repo id and stores it in
        // this field, so it is a lookup key rather than a package name. Printing
        // "Package 111583593" was just leaking an internal id at the user.
        item.packageName
            ?.takeIf { it.isNotBlank() && it.toLongOrNull() == null }
            ?.let { add(xs.factPackage to it) }
        item.author?.takeIf { it.isNotBlank() }?.let { add(xs.factDeveloper to it) }
        add(xs.factSource to item.source.displayName)
        item.categories.takeIf { it.isNotEmpty() }
            ?.let { add(s.topics to it.joinToString(", ")) }
        item.stars.takeIf { it > 0 }?.let { add(s.stars to formatCount(it.toLong())) }
        item.installs.takeIf { it > 0 }?.let { add(xs.factInstalls to formatCount(it)) }
        item.license?.takeIf { it.isNotBlank() }?.let { add(xs.statLicense to it) }
        item.minSdk.takeIf { it > 0 }?.let { add(xs.statMinAndroid to "API $it") }
        item.antiFeatures.takeIf { it.isNotEmpty() }
            ?.let { add(xs.factFlags to it.size.toString()) }
    }
    if (facts.isEmpty()) return

    Column(
        modifier
            .fillMaxWidth()
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerLow, VyxelShapeTokens.Card)
            .padding(18.dp),
    ) {
        Text(
            text = xs.factsHeader,
            style = VyxelTextStyles.Overline,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        facts.forEachIndexed { index, (label, value) ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(120.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Vyxel score — how well-maintained the project looks.
 *
 * Same numbers Classic shows, from the same `calculateTrustScore`: popularity,
 * recency of the last update, fork count and how many releases have shipped. The bar
 * is the fastest read; the four stats underneath are what it is made of, because a
 * bare number with no working out invites distrust of the number itself.
 */
@Composable
private fun TrustCard(
    score: com.vythera.vyxelapps.TrustScore,
    modifier: Modifier = Modifier,
) {
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    val s = com.vythera.vyxelapps.LocalStrings.current
    val tint = when {
        score.score >= 85 -> Color(0xFF3DDC84)
        score.score >= 65 -> MaterialTheme.colorScheme.primary
        score.score >= 45 -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.error
    }

    Column(
        modifier
            .fillMaxWidth()
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerLow, VyxelShapeTokens.Card)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = s.trustScoreTitle.uppercase(),
                style = VyxelTextStyles.Overline,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${score.score}",
                style = MaterialTheme.typography.headlineSmall,
                color = tint,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            // Classic's own translated band labels rather than TrustScore.label,
            // which is a hardcoded English string on the model.
            text = when {
                score.score >= 85 -> s.trustHighlyTrusted
                score.score >= 65 -> s.trustTrusted
                score.score >= 45 -> s.trustModerate
                else -> s.trustLow
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(VyxelShapeTokens.Pill)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((score.score / 100f).coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(VyxelShapeTokens.Pill)
                    .background(tint),
            )
        }
        Spacer(Modifier.height(14.dp))
        val facts = listOf(
            s.stars to formatCount(score.stars.toLong()),
            s.forks to formatCount(score.forks.toLong()),
            s.releaseHistory to score.releaseCount.toString(),
            xs.statUpdated to
                if (score.daysSinceUpdate >= 999) "—" else "${score.daysSinceUpdate}d",
        )
        facts.forEachIndexed { index, (label, value) ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(120.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Tag, publication date and downloadable assets for one release. */
@Composable
private fun ReleaseCard(
    release: com.vythera.vyxelapps.Release,
    modifier: Modifier = Modifier,
) {
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }

    Column(
        modifier
            .fillMaxWidth()
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerLow, VyxelShapeTokens.Card)
            .padding(18.dp),
    ) {
        Text(
            text = xs.releaseHeader,
            style = VyxelTextStyles.Overline,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = release.tag_name.ifBlank { release.name.orEmpty() },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (release.prerelease) {
                Text(
                    text = xs.prerelease,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .clip(VyxelShapeTokens.Chip)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        release.published_at?.takeIf { it.isNotBlank() }?.let { published ->
            Spacer(Modifier.height(3.dp))
            Text(
                text = formatRelativeTime(published.isoToEpochMillis()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (apks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            apks.forEach { asset ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (asset.size > 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = formatSize(asset.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One row of the version list, with its own install action.
 *
 * Installing an older build is a downgrade: Android refuses it unless the current
 * install is removed first, so the label says so rather than failing silently at the
 * package installer.
 */
@Composable
private fun VersionRow(
    release: com.vythera.vyxelapps.Release,
    isLatest: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = com.vythera.vyxelapps.LocalStrings.current
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current

    Row(
        modifier
            .fillMaxWidth()
            .glassSurface(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                VyxelShapeTokens.Card,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = release.tag_name.ifBlank { release.name.orEmpty() },
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (isLatest) add(xs.latestLabel)
                if (release.prerelease) add(xs.prerelease)
                release.published_at?.takeIf { it.isNotBlank() }
                    ?.let { add(formatRelativeTime(it.isoToEpochMillis())) }
            }.joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (isLatest) s.install else xs.installThisVersion,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(VyxelShapeTokens.Pill)
                .clickable(onClick = onInstall)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun InstallCommandCard(
    command: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerHigh, VyxelShapeTokens.Card)
            .padding(16.dp),
    ) {
        val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
        Text(
            text = xs.installOnDesktop,
            style = VyxelTextStyles.Overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onCopy),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = xs.copyCommand,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    url: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerLow, VyxelShapeTokens.Card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** F-Droid anti-feature codes are terse; expand the common ones. */
@Composable
private fun String.humanizeAntiFeature(): String {
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    return when (this) {
        "Ads" -> xs.antiAdsDesc
        "Tracking" -> xs.antiTrackingDesc
        "NonFreeNet" -> xs.antiNonFreeNet
        "NonFreeAdd" -> xs.antiNonFreeAdd
        "NonFreeDep" -> xs.antiNonFreeDep
        "NonFreeAssets" -> xs.antiNonFreeAssets
        "UpstreamNonFree" -> xs.antiUpstreamNonFree
        "KnownVuln" -> xs.antiKnownVuln
        "NoSourceSince" -> xs.antiNoSourceSince
        // The code F-Droid publishes is unchanged upstream; only the wording is ours.
        "ApplicationDebuggable" -> xs.antiDebuggable
        else -> this
    }
}
