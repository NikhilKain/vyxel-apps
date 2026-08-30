package com.vythera.vyxelapps.expressive.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Slider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.vythera.vyxelapps.expressive.ui.theme.VyxelSkin
import com.vythera.vyxelapps.expressive.ui.theme.palette
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vythera.vyxelapps.expressive.data.Settings
import com.vythera.vyxelapps.expressive.ui.formatSize
import com.vythera.vyxelapps.expressive.ui.theme.MotionIntensity
import com.vythera.vyxelapps.expressive.ui.theme.ThemeMode
import androidx.compose.foundation.layout.Box
import com.vythera.vyxelapps.expressive.ui.theme.VyxelMotion
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.VyxelTextStyles
import com.vythera.vyxelapps.expressive.ui.theme.glassSurface

@Composable
fun SettingsScreen(
    settings: Settings,
    cacheSize: Long,
    tokenRejected: Boolean,
    onSwitchToClassic: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onMotionIntensity: (MotionIntensity) -> Unit,
    onShowDesktopSources: (Boolean) -> Unit,
    onGithubToken: (String) -> Unit,
    onClearCache: () -> Unit,
    contentPadding: PaddingValues,
    onSkin: (VyxelSkin) -> Unit = {},
    /**
     * Liquid Glass tuning, held in Classic's settings so both shells render the theme
     * identically — a slider moved here is already applied over there.
     */
    glassWallpaperUri: String = "",
    glassBlur: Float = 7f,
    glassEdge: Float = 1f,
    glassRefraction: Float = 1f,
    onGlassWallpaper: (String) -> Unit = {},
    onGlassBlur: (Float) -> Unit = {},
    onGlassEdge: (Float) -> Unit = {},
    onGlassRefraction: (Float) -> Unit = {},
    /**
     * The nav bar's own glass trio.
     *
     * Separate from the tile sliders above because the floating bar sits over content
     * rather than over the wallpaper, and wants a heavier blur than a card does.
     * Classic has had these since the theme shipped; only the tile trio was ported.
     */
    glassNavBlur: Float = 7f,
    glassNavEdge: Float = 1f,
    glassNavRefraction: Float = 1f,
    onGlassNavBlur: (Float) -> Unit = {},
    onGlassNavEdge: (Float) -> Unit = {},
    onGlassNavRefraction: (Float) -> Unit = {},
    /** Current language name, as stored in Classic's shared settings. */
    language: String = "English",
    onLanguage: (String) -> Unit = {},
    /**
     * PRO themes gate, read from Classic's licence state.
     *
     * Always false in the open-core build: the premium skins and the licence check
     * that unlocks them are part of the paid build. Kept on the signature so the two
     * builds share one settings screen rather than forking it.
     */
    proUnlocked: Boolean = false,
    licenseKeyInput: String = "",
    licenseVerifyState: com.vythera.vyxelapps.LicenseVerifyState =
        com.vythera.vyxelapps.LicenseVerifyState.IDLE,
    onLicenseKeyInput: (String) -> Unit = {},
    onVerifyLicense: () -> Unit = {},
    /** Opens Classic's custom-repo manager; Expressive had no entry point to it. */
    onManageRepos: () -> Unit = {},
    customRepoCount: Int = 0,
    /** Opens Classic's profile screen — favourites, browse history, rollback. */
    onOpenLibrary: () -> Unit = {},
    favouriteCount: Int = 0,
    /**
     * Whether pre-release builds are offered. Lives in Classic's shared settings and
     * gates version selection in both shells, so it belongs on both settings screens.
     */
    showPreReleases: Boolean = false,
    onShowPreReleases: (Boolean) -> Unit = {},
    /** Restores every app the user has hidden from search and the home screen. */
    onClearHidden: () -> Unit = {},
    /** Root state, for the module installer. Probed only when the user asks. */
    rootManager: com.vythera.vyxelapps.root.RootManager =
        com.vythera.vyxelapps.root.RootManager.None,
    rootChecking: Boolean = false,
    onCheckRoot: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var token by remember(settings.githubToken) { mutableStateOf(settings.githubToken) }
    val s = com.vythera.vyxelapps.LocalStrings.current
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    val ctx = LocalContext.current
    var showUnlockDialog by remember { mutableStateOf(false) }
    // The skin actually rendering, which is not always the stored one: a premium skin
    // restored from a backup onto an unlicensed install is guarded back to Default, and
    // the picker has to agree with what the user can see.
    val activeSkin = com.vythera.vyxelapps.expressive.ui.theme.LocalVyxelSkin.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title") {
            Text(
                text = s.settingsTitle,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }

        item(key = "uistyle") {
            SettingsCard(xs.sectionInterface) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = xs.expressiveUi,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = xs.switchToClassicDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .clip(VyxelShapeTokens.Pill)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable(onClick = onSwitchToClassic)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = xs.classicLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        item(key = "skin") {
            SettingsCard(xs.sectionSkin) {
                Text(
                    text = xs.skinDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SkinPicker(
                    current = activeSkin,
                    locked = !proUnlocked,
                    onSelect = onSkin,
                    onLockedTap = { showUnlockDialog = true },
                )
            }
        }

        // Only meaningful while a glass skin is on, and Neon Punk pins its own values.
        if (activeSkin.isGlass) {
            item(key = "glass") {
                SettingsCard(s.liquidGlassSection) {
                    GlassControls(
                        wallpaperUri = glassWallpaperUri,
                        blur = glassBlur,
                        edge = glassEdge,
                        refraction = glassRefraction,
                        onWallpaper = onGlassWallpaper,
                        onBlur = onGlassBlur,
                        onEdge = onGlassEdge,
                        onRefraction = onGlassRefraction,
                    )
                }
            }

            item(key = "glassnav") {
                SettingsCard(s.navBarLabel) {
                    GlassSlider(
                        s.blurIntensity, "${glassNavBlur.toInt()} dp",
                        glassNavBlur, 2f..28f, onGlassNavBlur,
                    )
                    GlassSlider(
                        s.edgeEffect, "${(glassNavEdge * 100).toInt()}%",
                        glassNavEdge, 0f..2f, onGlassNavEdge,
                    )
                    GlassSlider(
                        s.refraction, "${(glassNavRefraction * 100).toInt()}%",
                        glassNavRefraction, 0f..2f, onGlassNavRefraction,
                    )
                }
            }
        }

        item(key = "appearance") {
            SettingsCard(s.appearance) {
                // A premium skin supplies its own palette, so the mode and dynamic
                // colour controls below would do nothing — say so rather than leaving
                // the user tapping dead switches.
                if (activeSkin.isPremium) {
                    Text(
                        text = xs.skinOverridesTheme,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                SegmentedRow(
                    label = s.themeColor,
                    options = ThemeMode.entries.map {
                        when (it) {
                            ThemeMode.System -> xs.modeSystem
                            ThemeMode.Light -> xs.modeLight
                            ThemeMode.Dark -> xs.modeDark
                            ThemeMode.Amoled -> xs.modeAmoled
                        }
                    },
                    selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                    onSelect = { onThemeMode(ThemeMode.entries[it]) },
                )
                Spacer(Modifier.height(14.dp))
                ToggleRow(
                    title = xs.dynamicColour,
                    subtitle = xs.dynamicColourDesc,
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColor,
                )
            }
        }

        // Language, mirroring Classic's picker and writing the same setting, so the
        // choice follows the user across both shells.
        item(key = "language") {
            SettingsCard(s.languageSection) {
                LanguagePicker(current = language, onSelect = onLanguage)
            }
        }

        item(key = "motion") {
            SettingsCard(xs.sectionMotion) {
                SegmentedRow(
                    label = xs.motionIntensity,
                    options = MotionIntensity.entries.map {
                        when (it) {
                            MotionIntensity.Calm -> xs.motionCalm
                            MotionIntensity.Balanced -> xs.motionBalanced
                            MotionIntensity.Expressive -> xs.motionExpressive
                        }
                    },
                    selectedIndex = MotionIntensity.entries.indexOf(settings.motionIntensity),
                    onSelect = { onMotionIntensity(MotionIntensity.entries[it]) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = xs.motionIntensityDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "catalog") {
            SettingsCard(xs.sectionCatalog) {
                ToggleRow(
                    title = xs.showDesktopSources,
                    subtitle = xs.showDesktopSourcesDesc,
                    checked = settings.showDesktopSources,
                    onCheckedChange = onShowDesktopSources,
                )
                Spacer(Modifier.height(14.dp))
                // Offering a beta build is a catalog decision, not a theme one, and
                // the flag is shared with Classic — set it here and Classic honours it.
                ToggleRow(
                    title = s.showPreReleases,
                    subtitle = s.showPreReleasesDesc,
                    checked = showPreReleases,
                    onCheckedChange = onShowPreReleases,
                )
                Spacer(Modifier.height(14.dp))
                NavRow(
                    title = s.addYourOwnRepo,
                    subtitle = if (customRepoCount > 0) "$customRepoCount" else s.addFirstRepo,
                    onClick = onManageRepos,
                )
                Spacer(Modifier.height(14.dp))
                // Hidden apps are invisible by design, so the only way to find out
                // what is hidden — or to change your mind — is a control that says so.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = xs.hiddenAppsTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (settings.hiddenPackages.isEmpty()) xs.hiddenAppsNone
                            else String.format(xs.hiddenAppsCount, settings.hiddenPackages.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (settings.hiddenPackages.isNotEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        ActionPill(xs.restoreAllHidden, onClick = onClearHidden)
                    }
                }
            }
        }

        // ── Shizuku ───────────────────────────────────────────────────────────
        //
        // The install path has honoured Shizuku since it landed, but only Classic's
        // settings ever showed it. From this shell there was no way to see whether it
        // was connected and no way to grant it — so users who had Shizuku running
        // still got a system prompt on every install and had no idea why.
        item(key = "shizuku") {
            var shizukuAvailable by remember {
                mutableStateOf(com.vythera.vyxelapps.ShizukuInstaller.isAvailable())
            }
            var shizukuGranted by remember {
                mutableStateOf(com.vythera.vyxelapps.ShizukuInstaller.hasPermission())
            }

            SettingsCard(s.installationSection) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Fully qualified: this file already imports the Material icon
                    // named `Image`, which would otherwise shadow the composable.
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            id = com.vythera.vyxelapps.R.drawable.ic_shizuku
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Shizuku",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                shizukuAvailable && shizukuGranted -> s.shizukuActive
                                shizukuAvailable -> s.shizukuRunning
                                else -> s.shizukuNotRunning
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    when {
                        shizukuAvailable && shizukuGranted -> Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = s.shizukuActive,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        shizukuAvailable -> ActionPill(s.grantLabel) {
                            com.vythera.vyxelapps.ShizukuInstaller.requestPermission(1001)
                            shizukuGranted = com.vythera.vyxelapps.ShizukuInstaller.hasPermission()
                        }
                        else -> ActionPill(s.refresh) {
                            shizukuAvailable = com.vythera.vyxelapps.ShizukuInstaller.isAvailable()
                            shizukuGranted = com.vythera.vyxelapps.ShizukuInstaller.hasPermission()
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = xs.shizukuSetupDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(18.dp))

                // ── Root ──────────────────────────────────────────────────────
                //
                // Sits with Shizuku because they answer the same question — "what
                // can this app do without asking you every time" — and because a
                // user hunting for one will look where the other is.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = xs.rootSectionTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                rootChecking -> xs.rootChecking
                                !rootManager.available -> xs.rootNone
                                rootManager.version.isBlank() ->
                                    String.format(xs.rootActiveNoVersion, rootManager.label)
                                else -> String.format(
                                    xs.rootActive, rootManager.label, rootManager.version,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    if (rootManager.available) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        ActionPill(xs.rootCheck, onClick = onCheckRoot)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = xs.rootDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Favourites, browse history, install history and rollback. Classic keeps all
        // four on its Profile tab; Expressive has no fifth tab to spare, so it reaches
        // the same screen from here rather than growing a second copy of it.
        item(key = "library") {
            SettingsCard(s.profileTitle) {
                NavRow(
                    title = s.favourites,
                    subtitle = if (favouriteCount > 0) "$favouriteCount  ·  ${s.installHistory}"
                    else s.installHistory,
                    onClick = onOpenLibrary,
                )
            }
        }

        item(key = "github") {
            SettingsCard(s.githubSection) {
                Text(
                    text = xs.githubTokenDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (tokenRejected) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = xs.tokenRejectedDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(12.dp))
                // A token is a secret to *retrieve* as often as one to enter: it is
                // typed once and then needed again whenever Vyxel is set up on another
                // device. Permanently masked, the only way to get it onto that second
                // device is to revoke it on github.com and mint a new one. Revealing is
                // opt-in and resets whenever the stored token changes, so the default
                // is still hidden.
                var revealToken by remember(settings.githubToken) { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(VyxelShapeTokens.Chip)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                if (token.isEmpty()) {
                                    Text(
                                        text = "ghp_…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.6f),
                                    )
                                }
                                BasicTextField(
                                    value = token,
                                    onValueChange = { token = it },
                                    singleLine = true,
                                    visualTransformation =
                                        if (revealToken) VisualTransformation.None
                                        else PasswordVisualTransformation(),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (token.isNotEmpty()) {
                                Icon(
                                    imageVector =
                                        if (revealToken) Icons.Filled.VisibilityOff
                                        else Icons.Filled.Visibility,
                                    contentDescription =
                                        if (revealToken) s.hideTokenLabel else s.showTokenLabel,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { revealToken = !revealToken }
                                        .padding(8.dp)
                                        .size(20.dp),
                                )
                                // Copying beats re-typing 40 opaque characters, and it
                                // is the whole point of being able to see it again.
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = s.copyTokenLabel,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            ctx.getSystemService(android.content.ClipboardManager::class.java)
                                                ?.setPrimaryClip(
                                                    android.content.ClipData.newPlainText(
                                                        "GitHub token", token,
                                                    )
                                                )
                                        }
                                        .padding(8.dp)
                                        .size(20.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (token.isNotEmpty()) {
                        Box(
                            Modifier
                                .clip(VyxelShapeTokens.Pill)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable {
                                    token = ""
                                    onGithubToken("")
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = xs.clearLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    // Greys out once the field matches what's already stored, so the
                    // button visibly reflects whether there is anything to save.
                    val dirty = token != settings.githubToken
                    val saveBg by animateColorAsState(
                        targetValue = if (dirty) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = VyxelMotion.fade(),
                        label = "saveBg",
                    )
                    val saveFg by animateColorAsState(
                        targetValue = if (dirty) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        animationSpec = VyxelMotion.fade(),
                        label = "saveFg",
                    )
                    Box(
                        Modifier
                            .clip(VyxelShapeTokens.Pill)
                            .background(saveBg)
                            .clickable(enabled = dirty) { onGithubToken(token) }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = if (dirty) s.save else xs.savedLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = saveFg,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                com.vythera.vyxelapps.GitHubQuotaMeter()
            }
        }

        item(key = "storage") {
            SettingsCard(xs.sectionStorage) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = xs.downloadedApks,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (cacheSize > 0) formatSize(cacheSize) else xs.storageEmpty,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier
                            .clip(VyxelShapeTokens.Pill)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(onClick = onClearCache)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = xs.clearLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        item(key = "backup") {
            SettingsCard(s.backupRestore) {
                Text(
                    text = xs.backupDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionPill(xs.exportLabel, Modifier.weight(1f), onExport)
                    ActionPill(xs.importLabel, Modifier.weight(1f), onImport)
                }
            }
        }

        item(key = "about") {
            SettingsCard(s.about) {
                Text(
                    // Read from BuildConfig, not a literal: the hardcoded "1.0.0"
                    // disagreed with the Classic screen after the 1.0.8 bump.
                    text = "Vyxel Apps ${com.vythera.vyxelapps.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = xs.aboutBlurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

}

/**
 * Wallpaper picker and the three glass sliders, mirroring Classic's Liquid Glass
 * section — same controls, same ranges, same underlying settings.
 */
@Composable
private fun GlassControls(
    wallpaperUri: String,
    blur: Float,
    edge: Float,
    refraction: Float,
    onWallpaper: (String) -> Unit,
    onBlur: (Float) -> Unit,
    onEdge: (Float) -> Unit,
    onRefraction: (Float) -> Unit,
) {
    val s = com.vythera.vyxelapps.LocalStrings.current
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    val context = LocalContext.current

    // OpenDocument, not GetContent: the URI has to stay readable across restarts, and
    // only a persistable grant survives. Without it the wallpaper silently vanishes
    // the next time the app launches.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onWallpaper(it.toString())
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(VyxelShapeTokens.Chip)
            .clickable { picker.launch(arrayOf("image/*")) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = s.wallpaperLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (wallpaperUri.isNotEmpty()) xs.wallpaperActive else xs.wallpaperPick,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (wallpaperUri.isNotEmpty()) {
            AsyncImage(
                model = wallpaperUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(VyxelShapeTokens.IconSmall),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onWallpaper("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = xs.wallpaperRemove,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            }
        } else {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    // Ranges match Classic exactly, so a value set in one shell reads the same here.
    GlassSlider(s.blurIntensity, "${blur.toInt()} dp", blur, 2f..28f, onBlur)
    GlassSlider(s.edgeEffect, "${(edge * 100).toInt()}%", edge, 0f..2f, onEdge)
    GlassSlider(s.refraction, "${(refraction * 100).toInt()}%", refraction, 0f..2f, onRefraction)
}

@Composable
private fun GlassSlider(
    label: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = readout,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Skin chooser — a swatch row, because these are looks rather than settings and the
 * palette communicates the choice faster than its name does.
 *
 * Every skin but [VyxelSkin.Default] is one of Classic's PRO themes and is gated on
 * the same licence. Locked rows stay visible and keep their swatches — the palette is
 * the whole pitch — but wear a padlock and route to the unlock dialog instead of
 * selecting.
 */
@Composable
private fun SkinPicker(
    current: VyxelSkin,
    locked: Boolean,
    onSelect: (VyxelSkin) -> Unit,
    onLockedTap: () -> Unit,
) {
    val xs = com.vythera.vyxelapps.expressive.ui.LocalExpressiveStrings.current
    // Only the stock look ships in the open core; the paid skins are absent.
    val names = mapOf(
        VyxelSkin.Default to xs.skinDefault,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VyxelSkin.entries.forEach { skin ->
            val skinLocked = locked && skin.isPremium
            val selected = skin == current && !skinLocked
            val palette = skin.palette
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(VyxelShapeTokens.Chip)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .clickable { if (skinLocked) onLockedTap() else onSelect(skin) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Three dots of the skin's own palette: background, accent, alt.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val swatches = if (palette == null) {
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        )
                    } else {
                        listOf(palette.bgPrimary, palette.accent, palette.accentAlt)
                    }
                    swatches.forEach { c ->
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(c)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = names[skin].orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        selected -> MaterialTheme.colorScheme.onPrimaryContainer
                        skinLocked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                when {
                    skinLocked -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "PRO",
                            style = MaterialTheme.typography.labelSmall,
                            color = ProGold,
                        )
                        // Decorative: the "PRO" label beside it already reads out.
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = ProGold,
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    selected -> Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** The PRO badge gold, matching Classic's locked theme tiles. */
private val ProGold = androidx.compose.ui.graphics.Color(0xFFFFD700)

/**
 * Language chooser, expanding in place.
 *
 * The list is intentionally the same set of display names Classic writes into
 * `AppSettings.language`, because that single string drives both shells' strings and
 * the translate target.
 */
@Composable
private fun LanguagePicker(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val languages = listOf(
        "English", "Hindi", "Spanish", "French", "German", "Japanese",
        "Portuguese", "Italian", "Russian", "Chinese", "Korean",
        "Arabic", "Dutch", "Turkish", "Polish", "Swedish",
    )

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(VyxelShapeTokens.Chip)
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = current,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                languages.forEach { lang ->
                    val selected = lang == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(VyxelShapeTokens.Chip)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .clickable {
                                expanded = false
                                onSelect(lang)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = lang,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One settings tile.
 *
 * Painted through [glassSurface] rather than a flat `background`, so under a glass
 * skin the tile blurs the wallpaper like every card on the home rails instead of
 * sitting on top of it as an opaque slab. Without a glass skin the modifier collapses
 * to exactly the `clip` + `background` it replaces.
 */
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerLow, VyxelShapeTokens.Card)
            .padding(18.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = VyxelTextStyles.Overline,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun ActionPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(VyxelShapeTokens.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Settings row that opens a sub-screen, with the usual disclosure chevron. */
@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(VyxelShapeTokens.Chip)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Pill-style segmented control; the selected segment animates its fill. */
@Composable
private fun SegmentedRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(VyxelShapeTokens.Pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val container by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent,
                    animationSpec = VyxelMotion.fade(),
                    label = "segmentBg",
                )
                val content by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = VyxelMotion.fade(),
                    label = "segmentFg",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .clip(VyxelShapeTokens.Pill)
                        .background(container)
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium,
                        color = content,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
