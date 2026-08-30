package com.vythera.vyxelapps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Families offered as filters; null is "everything" and leads. */
private val CLASSIC_MODULE_FAMILIES = listOf(null, "Magisk", "Zygisk", "LSPosed", "KernelSU")

/**
 * Root modules, in the Classic shell's own idiom.
 *
 * A separate screen rather than rows mixed into the catalogue, because a module is a
 * different kind of thing from an app — different install path, different
 * prerequisites, and inert without a root manager. Mixing a thousand of them into the
 * catalogue would bury the apps most people came for.
 *
 * Installation goes through the device's root manager, never `PackageInstaller`: a
 * module is a flashable zip, and handing that to the package manager fails with a
 * parse error that reads like a corrupt download.
 */
@Composable
fun ModulesScreen(
    modules: List<GitHubRepo>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: (GitHubRepo) -> Unit,
) {
    val isGlass = LocalIsLiquidGlass.current
    var query by remember { mutableStateOf("") }
    var family by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onRefresh() }

    val visible = remember(modules, query, family) {
        val needle = query.trim().lowercase()
        modules.filter { repo ->
            // The family rides in `language`: the one free-text field on GitHubRepo
            // that the module mapping does not otherwise need.
            (family == null || repo.language == family) &&
                (needle.isBlank() ||
                    repo.name.lowercase().contains(needle) ||
                    repo.packageId.lowercase().contains(needle) ||
                    repo.description.orEmpty().lowercase().contains(needle))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isGlass) Color.Transparent else MaterialTheme.colorScheme.background)
    ) {
        ScreenBackground(ScreenBg.SEARCH)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarSpace()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        "Modules",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isLoading && modules.isEmpty()) "Reading the module repos…"
                        else "${visible.size} modules",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                placeholder = { Text("Search modules, ids, authors…") },
                shape = CircleShape,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(CLASSIC_MODULE_FAMILIES, key = { it ?: "all" }) { entry ->
                    val selected = family == entry
                    Text(
                        text = entry ?: "All",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable { family = if (selected) null else entry }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 10.dp, bottom = 110.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { repo ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    repo.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!repo.description.isNullOrBlank()) {
                                    Text(
                                        repo.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    listOfNotNull(
                                        repo.language?.takeIf { it.isNotBlank() },
                                        repo.stargazers_count.takeIf { it > 0 }?.let { "★ $it" },
                                    ).joinToString("  ·  "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                )
                            }
                            TextButton(onClick = { onInstall(repo) }) {
                                Text(
                                    "Install",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Console for a module flash.
 *
 * The root manager's own stdout is the content rather than a spinner: module
 * installers print their compatibility checks and their reasons for refusing, and
 * reducing that to a verdict turns "your kernel is too old" into a silent failure.
 *
 * Not dismissible while the installer is running — half-flashing a module and walking
 * away is the one thing that must not be easy to do by accident.
 */
@Composable
fun ModuleInstallDialog(
    ui: ModuleInstallUi,
    onDismiss: () -> Unit,
    onReboot: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(ui.lines.size) {
        if (ui.lines.isNotEmpty()) listState.animateScrollToItem(ui.lines.lastIndex)
    }

    AlertDialog(
        onDismissRequest = { if (!ui.running) onDismiss() },
        title = {
            Column {
                Text(ui.name, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        ui.running -> "Installing…"
                        ui.success == true -> "Installed — reboot to activate"
                        else -> "Install failed"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ui.success == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(10.dp),
            ) {
                items(ui.lines.size) { index ->
                    Text(
                        ui.lines[index],
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            // Offered only once it worked, and never performed automatically.
            if (ui.success == true) {
                TextButton(onClick = onReboot) { Text("Reboot now") }
            }
        },
        dismissButton = {
            TextButton(enabled = !ui.running, onClick = onDismiss) { Text("Close") }
        },
    )
}
