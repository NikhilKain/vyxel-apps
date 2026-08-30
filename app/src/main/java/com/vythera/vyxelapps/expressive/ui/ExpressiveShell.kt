package com.vythera.vyxelapps.expressive.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.toAppItem
import com.vythera.vyxelapps.expressive.data.toGitHubRepo
import com.vythera.vyxelapps.expressive.data.toScanRow
import com.vythera.vyxelapps.expressive.install.DownloadState
import com.vythera.vyxelapps.expressive.ui.components.InstallAction
import com.vythera.vyxelapps.expressive.ui.screens.DetailScreen
import com.vythera.vyxelapps.expressive.ui.screens.HomeScreen
import com.vythera.vyxelapps.expressive.ui.screens.SearchScreen
import com.vythera.vyxelapps.expressive.ui.screens.SettingsScreen
import com.vythera.vyxelapps.expressive.ui.screens.SourcesScreen
import com.vythera.vyxelapps.expressive.ui.screens.UpdatesScreen
import com.vythera.vyxelapps.expressive.ui.theme.LocalVyxelSkin
import com.vythera.vyxelapps.expressive.ui.theme.SkinBackground
import com.vythera.vyxelapps.expressive.ui.theme.VyxelMotion
import com.vythera.vyxelapps.expressive.ui.theme.VyxelShapeTokens
import com.vythera.vyxelapps.expressive.ui.theme.VyxelSkin
import com.vythera.vyxelapps.expressive.ui.theme.glassSurface
import kotlinx.coroutines.delay

/**
 * Full-screen destinations layered over the tabs.
 *
 * Kept as a flat enum rather than a nav graph for the same reason the tab/detail split
 * is a plain state machine: there is exactly one place that decides what back does, and
 * the tab underneath keeps its scroll position for free.
 */
enum class SubScreen { None, TrackApp, TrackRepoSearch, ManageRepos, AddRepo, Library, Modules }

enum class Tab(val icon: ImageVector) {
    Home(Icons.Filled.Explore),
    Search(Icons.Filled.Search),
    Updates(Icons.Filled.Upgrade),
    Sources(Icons.Filled.Layers),
    Settings(Icons.Filled.Settings),
}

/**
 * Tab captions are resolved per composition rather than stored on the enum, because
 * the enum is a singleton and would otherwise freeze the labels in whatever language
 * happened to be active when the class first loaded.
 */
@Composable
fun Tab.label(): String {
    val s = com.vythera.vyxelapps.LocalStrings.current
    val xs = LocalExpressiveStrings.current
    return when (this) {
        Tab.Home -> s.discoverTitle
        Tab.Search -> s.navSearch
        Tab.Updates -> xs.tabUpdates
        Tab.Sources -> xs.tabSources
        Tab.Settings -> s.navSettings
    }
}

/**
 * App shell: tab switching, the detail overlay, and the snackbar.
 *
 * Navigation is a small explicit state machine rather than a NavHost — the detail
 * view is an overlay over the current tab, so returning from it restores that tab's
 * scroll position for free, and there's exactly one place that decides what back
 * does.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExpressiveShell(
    viewModel: StoreViewModel,
    /**
     * Classic's view model, used as the single engine for search and updates.
     *
     * Both shells now read the same `searchResults` and `multiSourceUpdates`, so a
     * query or a scan produces identical results whichever skin is active. Before
     * this they each ran their own engine, which is why they behaved like two apps.
     */
    appViewModel: com.vythera.vyxelapps.AppViewModel,
    onSwitchToClassic: () -> Unit,
) {
    val classicState = appViewModel.state
    val home by viewModel.home.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sourceStates by viewModel.sourceStates.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val scanning by viewModel.scanningUpdates.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloads.states.collectAsStateWithLifecycle()
    val snackbar by viewModel.snackbar.collectAsStateWithLifecycle()
    val tokenRejected by viewModel.githubTokenRejected.collectAsStateWithLifecycle()
    val platformFilter by viewModel.platformFilter.collectAsStateWithLifecycle()
    val shizukuOnly by viewModel.shizukuOnly.collectAsStateWithLifecycle()
    val moduleCatalog by viewModel.modules.collectAsStateWithLifecycle()
    val modulesLoading by viewModel.modulesLoading.collectAsStateWithLifecycle()
    val rootManager by viewModel.rootManager.collectAsStateWithLifecycle()
    val rootChecking by viewModel.rootChecking.collectAsStateWithLifecycle()
    val moduleInstall by viewModel.moduleInstall.collectAsStateWithLifecycle()
    val extraResults by viewModel.extraResults.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val context = LocalContext.current

    // Hand finished installs and removals back to Classic's install history.
    //
    // That history is what *both* shells render as "Installed", and nothing in this
    // shell wrote to it — so an app installed here was missing from the list on both
    // sides, and the list only ever showed Classic's own installs. Registered once,
    // against the shared Classic view model.
    LaunchedEffect(appViewModel) {
        viewModel.onInstalled = { item, apkPath ->
            appViewModel.recordInstall(
                repo = item.toGitHubRepo(),
                tagName = item.version.orEmpty(),
                apkPath = apkPath,
                packageName = item.packageName.orEmpty(),
            )
        }
        // Pruning is by package name against the device, so removing an app here
        // drops its history entry wherever that entry came from.
        viewModel.onUninstalled = { appViewModel.clearRemovedApps() }
    }

    // Snackbars are raised from the view model, which cannot read a CompositionLocal,
    // so hand it the active table whenever the language changes.
    val expressiveStrings = LocalExpressiveStrings.current
    LaunchedEffect(expressiveStrings) { viewModel.setStrings(expressiveStrings) }

    // Storage Access Framework rather than a hard-coded path: no storage permission
    // is needed and the user picks where the backup actually lives.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(context, it) } }

    // Ignoring visibility: the status bar is hidden, so the ordinary inset is zero and
    // the header would sit flush against the top edge.
    val statusPadding = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues()
    val navPadding = WindowInsets.navigationBars.asPaddingValues()
    // The nav bar floats above content, so the bottom inset has to clear the bar
    // itself (~64dp) plus its own 16dp margin, or the last row sits underneath it.
    val contentPadding = PaddingValues(
        top = statusPadding.calculateTopPadding() + 12.dp,
        bottom = navPadding.calculateBottomPadding() + 116.dp,
    )

    // Full-screen sub-screens, hosted over the current tab.
    //
    // These are Classic's own screens rather than re-implementations. Tracking an app,
    // managing custom repos and the profile/library are whole flows with their own
    // package-manager scans, search plumbing and validation; a second copy of each in
    // Expressive would be a large surface to keep in step for no visual gain, and they
    // already follow the active theme through LocalTheme / LocalIsLiquidGlass, which
    // the Expressive theme provides.
    var subScreen by rememberSaveable { mutableStateOf(SubScreen.None) }
    // Carried between the three steps of the track-an-app flow: the user picks an
    // installed app first, then finds the repo that publishes it.
    var trackPackage by rememberSaveable { mutableStateOf("") }
    var trackAppName by rememberSaveable { mutableStateOf("") }
    var showEnterRepo by rememberSaveable { mutableStateOf(false) }

    fun closeTracking() {
        subScreen = SubScreen.None
        showEnterRepo = false
        trackPackage = ""
        trackAppName = ""
        appViewModel.clearTrackSearch()
    }

    val detailItem = detail.item
    // Innermost first: the sheet, then the sub-screen, then detail, then the tab.
    BackHandler(enabled = showEnterRepo) { showEnterRepo = false }
    BackHandler(enabled = !showEnterRepo && subScreen != SubScreen.None) {
        subScreen = when (subScreen) {
            // Both of these were reached from the screen before them, so back should
            // step one node up the flow rather than dropping the user out entirely.
            SubScreen.AddRepo -> SubScreen.ManageRepos
            SubScreen.TrackRepoSearch -> SubScreen.TrackApp
            else -> SubScreen.None
        }
    }
    BackHandler(enabled = subScreen == SubScreen.None && detailItem != null) {
        viewModel.clearDetail()
    }
    BackHandler(
        enabled = subScreen == SubScreen.None && detailItem == null && tab != Tab.Home,
    ) { tab = Tab.Home }

    // A premium skin paints its own live backdrop — animated grid and scanlines for
    // Cyberpunk, the neon city for NeonPunk, a wallpaper for Liquid Glass. The flat
    // scheme fill would cover it, so the shell goes transparent and lets it show.
    val skin = LocalVyxelSkin.current

    // Glass skins blur their own wallpaper. The layer has to be captured here, above
    // every surface that samples it, and provided down so cards can read it — a card
    // cannot capture the thing it is drawn on top of.
    val bgBackdrop = rememberLayerBackdrop { drawContent() }

    // Glass tuning comes from Classic's settings, the same values its own Liquid Glass
    // screens use, so the two shells render the theme identically and a slider moved
    // in one is already applied in the other. Neon Punk pins its own tuned constants
    // and ignores the sliders, exactly as Classic does.
    val glassSettings = classicState.settings
    val isNeon = false

    // Expressive's skins are Classic's PRO themes, so they are gated on the same
    // licence — one purchase unlocks both shells.
    val proUnlocked = classicState.liquidGlassUnlocked

    // A stored premium skin that is no longer licensed must not render. It can get
    // there via an Auto Backup restore onto a fresh install, or by the entitlement
    // expiring while it was selected — the same routes Classic guards against on startup.
    LaunchedEffect(proUnlocked, settings.skin) {
        if (!proUnlocked && settings.skin.isPremium) viewModel.setSkin(VyxelSkin.Default)
    }

    CompositionLocalProvider(
        com.vythera.vyxelapps.LocalGlassBgBackdrop provides
            if (skin.isGlass) bgBackdrop else null,
        com.vythera.vyxelapps.LocalGlassWallpaperUri provides
            if (skin.isGlass && !isNeon) glassSettings.liquidGlassWallpaperUri else "",
        com.vythera.vyxelapps.LocalGlassBlur provides when {
            !skin.isGlass -> 10f
            isNeon -> com.vythera.vyxelapps.NP_GLASS_BLUR
            else -> glassSettings.liquidGlassBlur
        },
        com.vythera.vyxelapps.LocalGlassEdgeIntensity provides when {
            !skin.isGlass -> 1f
            isNeon -> com.vythera.vyxelapps.NP_GLASS_EDGE
            else -> glassSettings.liquidGlassEdge
        },
        com.vythera.vyxelapps.LocalGlassRefraction provides when {
            !skin.isGlass -> 1f
            isNeon -> com.vythera.vyxelapps.NP_GLASS_REFRACTION
            else -> glassSettings.liquidGlassRefraction
        },
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (skin.isPremium) Modifier
                else Modifier.background(MaterialTheme.colorScheme.background)
            )
    ) {
        if (skin.isPremium) {
            SkinBackground(
                skin,
                Modifier
                    .fillMaxSize()
                    .then(if (skin.isGlass) Modifier.layerBackdrop(bgBackdrop) else Modifier),
            )
        }

        // Tabs slide horizontally in the direction of travel.
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val shift = if (forward) 1 else -1
                (
                    fadeIn(VyxelMotion.fade(200)) +
                        slideInHorizontally(VyxelMotion.expressive()) { (it / 8) * shift }
                    ).togetherWith(
                    fadeOut(VyxelMotion.fade(140)) +
                        slideOutHorizontally(VyxelMotion.expressive()) { -(it / 8) * shift }
                )
            },
            label = "tabSwitch",
        ) { current ->
            when (current) {
                Tab.Home -> PullToRefreshBox(
                    isRefreshing = home.loading && home.rails.isNotEmpty(),
                    onRefresh = { viewModel.loadHome(force = true) },
                ) {
                    // CDN-pinned promos lead the carousel. Applied here rather than in
                    // heroPicks so both shells read the same list off the same shared
                    // view model — one featured.json drives both.
                    val pinnedHero = remember(classicState.featuredPins) {
                        classicState.featuredPins.map { pin ->
                            pin.toGitHubRepo().toAppItem().copy(
                                promoLabel = pin.label.takeIf { it.isNotBlank() },
                                summary = pin.summary,
                                iconUrl = pin.iconUrl.takeIf { it.isNotBlank() },
                                version = pin.version.takeIf { it.isNotBlank() },
                                license = pin.license.takeIf { it.isNotBlank() },
                                downloadUrl = pin.apkUrl.takeIf { it.isNotBlank() },
                                needsReleaseLookup = false,
                            )
                        }
                    }
                    val heroState = remember(home, pinnedHero) {
                        if (pinnedHero.isEmpty()) home
                        else home.copy(
                            hero = pinnedHero + home.hero.filterNot { existing ->
                                pinnedHero.any { it.packageName != null &&
                                    it.packageName == existing.packageName }
                            }
                        )
                    }
                    // Store-only pins have no APK and no release to open a detail page
                    // on, so tapping one goes straight to its listing.
                    val externalPins = remember(classicState.featuredPins) {
                        classicState.featuredPins
                            .filter { it.isExternal }
                            .associate { it.packageName to it.storeUrl }
                    }
                    HomeScreen(
                        state = heroState,
                        sourceStates = sourceStates,
                        onItemClick = { item ->
                            val store = externalPins[item.packageName]
                            if (store != null) context.openUrl(store)
                            else viewModel.select(item)
                        },
                        onRetry = { viewModel.loadHome() },
                        contentPadding = contentPadding,
                        onOpenModules = { subScreen = SubScreen.Modules },
                    )
                }

                // Search runs on Classic's engine and is merely rendered here.
                Tab.Search -> SearchScreen(
                    state = SearchUiState(
                        query = classicState.searchQuery,
                        searching = classicState.isSearching,
                        // Filters are applied here rather than upstream because these
                        // results come from Classic's engine, which knows nothing about
                        // this shell's filter row or its hidden list.
                        results = remember(
                            classicState.searchResults,
                            classicState.searchQuery,
                            extraResults,
                            platformFilter,
                            shizukuOnly,
                            settings.hiddenPackages,
                        ) {
                            // Classic's engine plus the sources it has no enum for.
                            // Deduplicated on package so an app both engines found —
                            // say an F-Droid app that Aptoide also mirrors — is one
                            // row, keeping Classic's copy, which is the vetted one.
                            val classic = classicState.searchResults.map { it.toAppItem() }
                            val seen = classic.mapTo(HashSet()) { it.dedupeKey }
                            val merged = classic + extraResults.filter { seen.add(it.dedupeKey) }
                            // Re-rank the whole thing: appending one engine's output
                            // after the other's leaves the newcomers unordered at the
                            // bottom, however good a match they are.
                            viewModel.applyFilters(
                                viewModel.rankResults(merged, classicState.searchQuery)
                            )
                        },
                        submitted = classicState.searchQuery.isNotBlank(),
                    ),
                    enabledSources = settings.enabledSources,
                    platformFilter = platformFilter,
                    onPlatformFilter = viewModel::setPlatformFilter,
                    shizukuOnly = shizukuOnly,
                    onShizukuOnly = viewModel::setShizukuOnly,
                    onQueryChange = { query ->
                        appViewModel.onSearch(query)
                        // Same keystroke drives the sources Classic cannot see.
                        viewModel.searchExtras(query)
                    },
                    onClear = {
                        appViewModel.onSearch("")
                        viewModel.searchExtras("")
                    },
                    onItemClick = viewModel::select,
                    onToggleSource = viewModel::toggleSource,
                    contentPadding = contentPadding,
                    // Classic's own history list — the same engine already records it.
                    recentSearches = classicState.recentSearches,
                    onRecentClick = { appViewModel.onSearch(it) },
                    onClearRecent = appViewModel::clearRecentSearches,
                )

                // Updates also come from Classic's scan engine, including its
                // multi-source results, so both shells agree on what needs updating.
                Tab.Updates -> UpdatesScreen(
                    // Tracked-release updates, the output of `checkForUpdatesNow`.
                    updates = classicState.updates
                        .map { it.toAppItem() }
                        .distinctBy { it.packageName ?: it.name },
                    // The cross-source scan gets its own section, exactly as in
                    // Classic. Folding it into `updates` and filtering on `hasUpdate`
                    // discarded every current entry, so a scan that matched apps and
                    // found them all up to date rendered nothing and the button looked
                    // broken.
                    scanResults = classicState.multiSourceUpdates
                        .distinctBy { it.packageName }
                        .map { it.toScanRow() },
                    installed = classicState.installHistory
                        .distinctBy { it.packageName.ifBlank { it.repoName } }
                        .sortedByDescending { it.installedAt }
                        .map { it.toAppItem() },
                    // scanAllApps sets isMultiSourceScanning, NOT isCheckingUpdates —
                    // watching only the latter left the button with no feedback.
                    scanning = classicState.isCheckingUpdates ||
                        classicState.isMultiSourceScanning,
                    downloadStates = downloadStates,
                    onScan = { appViewModel.checkForUpdatesNow() },
                    onScanAllSources = { appViewModel.scanAllApps() },
                    onItemClick = viewModel::select,
                    onInstall = { item ->
                        // Route back through Classic so its installer, Shizuku path
                        // and install history all still apply.
                        val hit = classicState.multiSourceUpdates.firstOrNull { scan ->
                            scan.packageName == item.packageName
                        }
                        val link = hit?.link
                        when {
                            // Except for bundles. Classic's installer writes one file
                            // into a session, which is right for an APK and wrong for
                            // an XAPK — most large Play apps ship as split APKs, and
                            // handing that zip to the package manager fails with a
                            // parse error. Expressive's installer unpacks the splits
                            // and writes them into a single session.
                            link is com.vythera.vyxelapps.updater.ScanLink.Xapk ->
                                viewModel.install(
                                    item.copy(
                                        downloadUrl = link.link,
                                        needsReleaseLookup = false,
                                    )
                                )
                            hit != null -> appViewModel.downloadFromScanResult(hit)
                            else -> viewModel.install(item)
                        }
                    },
                    onCancel = viewModel::cancelDownload,
                    contentPadding = contentPadding,
                    // Manual tracking, bulk update and history pruning — all three
                    // already lived on Classic's Installed screen and on the shared
                    // view model; Expressive simply had no route to them.
                    trackedApps = classicState.settings.trackedApps,
                    onTrackApp = { subScreen = SubScreen.TrackApp },
                    onRemoveTracked = appViewModel::removeTrackedApp,
                    onUpdateAll = appViewModel::updateAll,
                    onClearRemoved = appViewModel::clearRemovedApps,
                    installedSort = settings.installedSort,
                    onInstalledSort = viewModel::setInstalledSort,
                    onUninstall = viewModel::uninstall,
                )

                Tab.Sources -> SourcesScreen(
                    enabledSources = settings.enabledSources,
                    sourceStates = sourceStates,
                    onToggle = viewModel::toggleSource,
                    onRefresh = { viewModel.loadHome(force = true) },
                    contentPadding = contentPadding,
                )

                Tab.Settings -> SettingsScreen(
                    settings = settings,
                    cacheSize = viewModel.downloadCacheSize(),
                    tokenRejected = tokenRejected,
                    onSwitchToClassic = onSwitchToClassic,
                    onExport = { exportLauncher.launch("vyxel-apps-backup.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                    onThemeMode = viewModel::setThemeMode,
                    onDynamicColor = viewModel::setDynamicColor,
                    onMotionIntensity = viewModel::setMotionIntensity,
                    onShowDesktopSources = viewModel::setShowDesktopSources,
                    onGithubToken = viewModel::setGithubToken,
                    onClearCache = viewModel::clearDownloadCache,
                    contentPadding = contentPadding,
                    onSkin = viewModel::setSkin,
                    // Written straight into Classic's settings — the single store both
                    // shells read, so the glass looks the same on either side.
                    glassWallpaperUri = classicState.settings.liquidGlassWallpaperUri,
                    glassBlur = classicState.settings.liquidGlassBlur,
                    glassEdge = classicState.settings.liquidGlassEdge,
                    glassRefraction = classicState.settings.liquidGlassRefraction,
                    onGlassWallpaper = { uri ->
                        appViewModel.updateSettings(
                            classicState.settings.copy(liquidGlassWallpaperUri = uri)
                        )
                    },
                    onGlassBlur = { v ->
                        appViewModel.updateSettings(
                            classicState.settings.copy(liquidGlassBlur = v)
                        )
                    },
                    onGlassEdge = { v ->
                        appViewModel.updateSettings(
                            classicState.settings.copy(liquidGlassEdge = v)
                        )
                    },
                    onGlassRefraction = { v ->
                        appViewModel.updateSettings(
                            classicState.settings.copy(liquidGlassRefraction = v)
                        )
                    },
                    // Language is Classic's setting; writing it there is what makes
                    // the choice stick across both shells.
                    language = classicState.settings.language,
                    onLanguage = { picked ->
                        appViewModel.updateSettings(
                            classicState.settings.copy(language = picked)
                        )
                    },
                    // Licence state is Classic's, unchanged: verifying a
                    // key here unlocks the PRO themes on both shells at once.
                    proUnlocked = proUnlocked,
                    licenseKeyInput = classicState.licenseKeyInput,
                    licenseVerifyState = classicState.licenseVerifyState,
                    onLicenseKeyInput = appViewModel::setLicenseKeyInput,
                    onVerifyLicense = appViewModel::verifyLicenseKey,
                    // Entry points to Classic's own screens, hosted as overlays.
                    onManageRepos = { subScreen = SubScreen.ManageRepos },
                    customRepoCount = classicState.customRepos.size,
                    onOpenLibrary = { subScreen = SubScreen.Library },
                    favouriteCount = classicState.favourites.size,
                    showPreReleases = classicState.settings.showPreReleases,
                    onShowPreReleases = { on ->
                        appViewModel.updateSettings(
                            classicState.settings.copy(showPreReleases = on)
                        )
                    },
                    onClearHidden = { viewModel.clearHidden() },
                    rootManager = rootManager,
                    rootChecking = rootChecking,
                    onCheckRoot = viewModel::checkRoot,
                    glassNavBlur = classicState.settings.liquidGlassNavBlur,
                    glassNavEdge = classicState.settings.liquidGlassNavEdge,
                    glassNavRefraction = classicState.settings.liquidGlassNavRefraction,
                    onGlassNavBlur = { v ->
                        appViewModel.updateSettings(
                            classicState.settings.copy(liquidGlassNavBlur = v)
                        )
                    },
                    onGlassNavEdge = { v ->
                        appViewModel.updateSettings(
                            classicState.settings.copy(liquidGlassNavEdge = v)
                        )
                    },
                    onGlassNavRefraction = { v ->
                        appViewModel.updateSettings(
                            classicState.settings.copy(liquidGlassNavRefraction = v)
                        )
                    },
                )
            }
        }

        // Detail rises over the current tab and scales it back slightly.
        AnimatedContent(
            targetState = detailItem,
            transitionSpec = {
                if (targetState != null) {
                    (
                        fadeIn(VyxelMotion.fade(180)) +
                            scaleIn(VyxelMotion.expressive(), initialScale = 0.93f)
                        ).togetherWith(fadeOut(VyxelMotion.fade(120)))
                } else {
                    fadeIn(VyxelMotion.fade(140)).togetherWith(
                        fadeOut(VyxelMotion.fade(160)) +
                            scaleOut(VyxelMotion.expressive(), targetScale = 0.93f)
                    )
                }
            },
            label = "detailOverlay",
        ) { current ->
            if (current != null) {
                // Drive Classic's release engine for this app: it owns the version
                // history, the ABI-aware APK picker and the installer, so Expressive
                // converts back rather than growing a second copy of all three.
                val classicRepo = remember(current.id) { current.toGitHubRepo() }
                val isModuleItem =
                    current.platform == com.vythera.vyxelapps.expressive.data.model.Platform.Module
                LaunchedEffect(classicRepo.id, isModuleItem) {
                    // Classic's release lookup is for APKs. A module's artefact is a
                    // flashable zip resolved by its own source, so asking here spends
                    // a request to populate a version list the module page does not
                    // show — and, worse, hands Classic's ABI-aware *APK* picker a
                    // chance to substitute an asset below.
                    if (!isModuleItem) appViewModel.fetchRelease(classicRepo)
                    // Repo hosts publish no screenshot metadata, so Classic digs them
                    // out of the project README. Expressive never asked for them,
                    // which is why GitHub/GitLab/Codeberg apps showed none at all
                    // while F-Droid entries (whose index carries them) were fine.
                    appViewModel.fetchScreenshots(classicRepo)
                    // Classic records a visit whenever an app is opened; without this
                    // the shared "recently viewed" list stayed empty for anyone who
                    // browses in Expressive.
                    appViewModel.addToHistory(classicRepo)
                }
                val classicInstall = appViewModel.installStates[classicRepo.id]

                /**
                 * The item, topped up from Classic's release lookup.
                 *
                 * Expressive resolves its own APK URL per source, and for repo hosts
                 * that lookup hits the live API and quietly fails on a rate limit or a
                 * release whose assets it can't read — leaving `downloadUrl` null and
                 * the button stuck on "No APK" even though the version list below it
                 * was listing real releases. Classic's resolver reads the CDN's static
                 * release cache and picks an ABI-aware asset, so when it has an answer
                 * and Expressive doesn't, use it. Filling the field rather than
                 * special-casing the button keeps Expressive's own download and
                 * progress pipeline in charge.
                 */
                val effectiveItem = remember(current, classicInstall?.apkAsset, isModuleItem) {
                    val asset = classicInstall?.apkAsset
                    // Never for a module: that picker chooses an APK for the device's
                    // ABI, and swapping one in for a module's zip would flash the
                    // wrong artefact entirely.
                    if (!isModuleItem && current.downloadUrl.isNullOrBlank() && asset != null &&
                        asset.browser_download_url.isNotBlank()
                    ) {
                        current.copy(
                            downloadUrl = asset.browser_download_url,
                            sizeBytes = asset.size.takeIf { it > 0 } ?: current.sizeBytes,
                            needsReleaseLookup = false,
                        )
                    } else current
                }
                val readmeShots = classicState.screenshots[classicRepo.id].orEmpty()

                /**
                 * The long-form description, when Classic resolved a better one.
                 *
                 * A card that reached this screen through the Search tab was built by
                 * Classic's engine, so its description is whatever the search response
                 * carried — for an Aptoide app that is the single signer line, and the
                 * About section rendered "Signed by Instagram Inc" as the entire
                 * write-up. Classic's per-source resolvers park the real body in
                 * `readmes`, keyed by the same repo id the screenshots above use.
                 *
                 * Longer wins rather than "non-empty wins", so a source that already
                 * supplied a full description is never overwritten by a shorter
                 * README blurb.
                 */
                val classicAbout = classicState.readmes[classicRepo.id].orEmpty()

                // The overlay needs an opaque floor of its own, or the tab underneath
                // shows through it. A flat scheme fill was doing that job for every
                // skin, which is why the detail page looked like it had dropped back to
                // the stock theme — the fill covered the wallpaper the cards blur
                // against. Premium skins get their own backdrop as the floor instead.
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (skin.isPremium) Modifier
                            else Modifier.background(MaterialTheme.colorScheme.background)
                        )
                ) {
                    if (skin.isPremium) SkinBackground(skin, Modifier.fillMaxSize())
                    DetailScreen(
                        // Carries Classic's resolved APK and README screenshots when
                        // Expressive's own source lookup came back empty-handed.
                        item = effectiveItem.let { base ->
                            val withShots =
                                if (readmeShots.isNotEmpty() && base.screenshots.isEmpty())
                                    base.copy(screenshots = readmeShots)
                                else base
                            if (classicAbout.length > withShots.description.length)
                                withShots.copy(description = classicAbout)
                            else withShots
                        },
                        resolving = detail.resolving,
                        installAction = viewModel.installAction(effectiveItem),
                        downloadState = downloadStates[current.id] ?: DownloadState.Idle,
                        onBack = viewModel::clearDetail,
                        onInstall = { viewModel.install(effectiveItem) },
                        onOpen = { viewModel.open(effectiveItem) },
                        onCancel = { viewModel.cancelDownload(current) },
                        onOpenUrl = { url -> context.openUrl(url) },
                        onCopy = { text -> context.copyToClipboard(text) },
                        contentPadding = contentPadding,
                        translatedDescription = detail.translatedDescription,
                        translatedChangelog = detail.translatedChangelog,
                        translating = detail.translating,
                        // The target language lives in Classic's settings, the single
                        // source of truth both shells read.
                        onTranslate = {
                            viewModel.translateDetailDescription(
                                classicState.settings.language
                            )
                        },
                        onUndoTranslate = viewModel::clearTranslation,
                        releases = classicInstall?.releases.orEmpty(),
                        selectedRelease = classicInstall?.release,
                        loadingReleases = classicInstall?.isLoadingRelease == true,
                        releaseError = classicInstall?.error,
                        onSelectRelease = { release ->
                            appViewModel.selectRelease(classicRepo.id, release)
                        },
                        onInstallRelease = { release ->
                            // Route through Classic so its ABI-aware APK picker,
                            // Shizuku path and install history all still apply.
                            // selectRelease updates the map synchronously, so the
                            // chosen asset is readable on the next line.
                            appViewModel.selectRelease(classicRepo.id, release)
                            appViewModel.installStates[classicRepo.id]?.apkAsset
                                ?.let { appViewModel.downloadAndInstall(classicRepo, it) }
                        },
                        // Favourites are Classic's list, keyed by the same repo id, so
                        // a heart tapped here shows up in the library on either shell.
                        isFavourite = classicState.favourites.any { it.id == classicRepo.id },
                        onToggleFavourite = { appViewModel.toggleFavourite(classicRepo) },
                        // Computed by Classic's release lookup; Expressive never
                        // surfaced it, so the same app looked unvetted here.
                        trustScore = classicInstall?.trustScore,
                        onUninstall = { viewModel.uninstall(effectiveItem) },
                        onDownloadZip = { viewModel.downloadModuleZip(effectiveItem) },
                        onInstallModule = { viewModel.installModule(effectiveItem) },
                        isHidden = effectiveItem.packageName in settings.hiddenPackages,
                        onToggleHidden = {
                            viewModel.setHidden(
                                effectiveItem,
                                effectiveItem.packageName !in settings.hiddenPackages,
                            )
                        },
                    )
                }
            }
        }

        // Sub-screens sit above the detail overlay: the library can be opened from
        // Settings while a detail page is still behind it.
        if (subScreen != SubScreen.None) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (skin.isPremium) Modifier
                        else Modifier.background(MaterialTheme.colorScheme.background)
                    )
            ) {
                if (skin.isPremium) SkinBackground(skin, Modifier.fillMaxSize())
                when (subScreen) {
                    SubScreen.TrackApp -> com.vythera.vyxelapps.TrackAppScreen(
                        onAppSelected = { pkg, name ->
                            trackPackage = pkg
                            trackAppName = name
                            appViewModel.searchForTracking(name)
                            subScreen = SubScreen.TrackRepoSearch
                        },
                        onDismiss = { closeTracking() },
                    )

                    SubScreen.TrackRepoSearch -> com.vythera.vyxelapps.TrackRepoSearchScreen(
                        appName = trackAppName,
                        packageName = trackPackage,
                        results = classicState.trackSearchResults,
                        isSearching = classicState.isTrackSearching,
                        onQueryChange = appViewModel::searchForTracking,
                        onRepoSelected = { repo ->
                            val fullName = repo.full_name
                                .ifBlank { "${repo.owner.login}/${repo.name}" }
                            appViewModel.addTrackedApp(
                                com.vythera.vyxelapps.TrackedApp(
                                    packageName = trackPackage,
                                    appName = trackAppName.ifBlank { repo.name },
                                    repoFullName = fullName,
                                    repoUrl = "https://github.com/$fullName",
                                )
                            )
                            closeTracking()
                        },
                        onEnterManually = { showEnterRepo = true },
                        onDismiss = { closeTracking() },
                    )

                    SubScreen.ManageRepos -> com.vythera.vyxelapps.ManageCustomReposScreen(
                        customRepos = classicState.customRepos,
                        onBack = { subScreen = SubScreen.None },
                        onAddNew = { subScreen = SubScreen.AddRepo },
                        onDelete = appViewModel::removeCustomRepo,
                    )

                    SubScreen.AddRepo -> com.vythera.vyxelapps.AddCustomRepoScreen(
                        onBack = { subScreen = SubScreen.ManageRepos },
                        onSave = { repo ->
                            appViewModel.addCustomRepo(repo)
                            subScreen = SubScreen.ManageRepos
                        },
                    )

                    // Favourites, browse history, install history and rollback — a
                    // whole feature area Expressive had no route to at all.
                    // Modules browse, opened from the card under the hero. Tapping a
                    // row closes this and opens Vyxel's own detail page, which
                    // already knows how to present something that isn't an APK.
                    SubScreen.Modules -> com.vythera.vyxelapps.expressive.ui.screens.ModulesScreen(
                        modules = moduleCatalog,
                        loading = modulesLoading,
                        onItemClick = { item ->
                            subScreen = SubScreen.None
                            viewModel.select(item)
                        },
                        onBack = { subScreen = SubScreen.None },
                        onRefresh = { viewModel.loadModules() },
                        contentPadding = contentPadding,
                    )

                    SubScreen.Library -> com.vythera.vyxelapps.ProfileScreen(
                        profile = classicState.profile,
                        history = classicState.history,
                        favourites = classicState.favourites,
                        installHistory = classicState.installHistory,
                        updates = classicState.updates,
                        onSave = appViewModel::updateProfile,
                        onAppClick = { repo -> viewModel.select(repo.toAppItem()) },
                        onCheckUpdates = appViewModel::checkForUpdatesNow,
                        onRollback = appViewModel::rollbackTo,
                    )

                    SubScreen.None -> Unit
                }
            }
        }

        // Typing a repo by hand — the escape hatch when the search can't find it.
        if (showEnterRepo) {
            com.vythera.vyxelapps.EnterRepoSheet(
                prefilledPackage = trackPackage,
                prefilledAppName = trackAppName,
                onConfirm = { tracked ->
                    appViewModel.addTrackedApp(tracked)
                    closeTracking()
                },
                onDismiss = { showEnterRepo = false },
            )
        }

        // Bottom bar hides while the detail overlay or a sub-screen is up.
        val barOffset by animateDpAsState(
            targetValue = if (detailItem == null && subScreen == SubScreen.None) 0.dp
            else 140.dp,
            animationSpec = VyxelMotion.expressive(),
            label = "barOffset",
        )
        // The bar gets its own glass trio: it floats over scrolling content rather
        // than over the wallpaper, and wants a heavier blur than a card does. Same
        // three settings Classic's GlassNavBar reads.
        CompositionLocalProvider(
            com.vythera.vyxelapps.LocalGlassBlur provides when {
                !skin.isGlass -> 10f
                isNeon -> com.vythera.vyxelapps.NP_GLASS_BLUR
                else -> glassSettings.liquidGlassNavBlur
            },
            com.vythera.vyxelapps.LocalGlassEdgeIntensity provides when {
                !skin.isGlass -> 1f
                isNeon -> com.vythera.vyxelapps.NP_GLASS_EDGE
                else -> glassSettings.liquidGlassNavEdge
            },
            com.vythera.vyxelapps.LocalGlassRefraction provides when {
                !skin.isGlass -> 1f
                isNeon -> com.vythera.vyxelapps.NP_GLASS_REFRACTION
                else -> glassSettings.liquidGlassNavRefraction
            },
        ) {
            VyxelNavBar(
                selected = tab,
                onSelect = { tab = it },
                updateCount = updates.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navPadding.calculateBottomPadding() + 16.dp)
                    .graphicsLayer { translationY = barOffset.toPx() },
            )
        }

        // Both banners are Classic's, shared rather than reimplemented. An Expressive
        // user was seeing neither — no announcements, and no prompt when a newer Vyxel
        // shipped, which is the one message that has to reach everyone.
        val selfUpdate = classicState.selfUpdateInfo
        if (selfUpdate != null && !classicState.selfUpdateDismissed) {
            com.vythera.vyxelapps.SelfUpdateBanner(
                info = selfUpdate,
                onDismiss = appViewModel::dismissSelfUpdate,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        com.vythera.vyxelapps.AnnouncementHost(
            announcement = classicState.announcement,
            onDismiss = appViewModel::dismissAnnouncement,
        )

        // Above everything, including the sub-screens: a flash in progress is the
        // one thing that must not be navigated away from half-done.
        if (moduleInstall.item != null) {
            com.vythera.vyxelapps.expressive.ui.components.ModuleInstallSheet(
                state = moduleInstall,
                onDismiss = viewModel::dismissModuleInstall,
                onReboot = viewModel::rebootDevice,
            )
        }

        snackbar?.let { message ->
            LaunchedEffect(message) {
                delay(3200)
                viewModel.dismissSnackbar()
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = navPadding.calculateBottomPadding() + 100.dp,
                    ),
                shape = VyxelShapeTokens.Card,
            ) { Text(message) }
        }
    }
    }
}

/**
 * Floating navigation bar.
 *
 * The selected item grows a filled pill behind its icon and reveals its label; the
 * others collapse to icon-only. That keeps the bar compact while still naming the
 * current destination.
 */
@Composable
private fun VyxelNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    updateCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 18.dp)
            .glassSurface(MaterialTheme.colorScheme.surfaceContainerHigh, VyxelShapeTokens.Pill)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tab.entries.forEach { entry ->
            NavItem(
                tab = entry,
                selected = entry == selected,
                badge = if (entry == Tab.Updates && updateCount > 0) updateCount else 0,
                onClick = { onSelect(entry) },
            )
        }
    }
}

@Composable
private fun NavItem(
    tab: Tab,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = VyxelMotion.bouncy(),
        label = "navIconScale",
    )

    val tabLabel = tab.label()

    Row(
        modifier = Modifier
            .clip(VyxelShapeTokens.Pill)
            .background(if (selected) scheme.primary else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (selected) 15.dp else 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tabLabel,
                tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            )
            if (badge > 0) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(VyxelShapeTokens.Pill)
                        .background(scheme.error)
                )
            }
        }

        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn(VyxelMotion.fade(180)) + scaleIn(VyxelMotion.expressive(), 0.7f))
                    .togetherWith(fadeOut(VyxelMotion.fade(100)))
            },
            label = "navLabel",
        ) { isSelected ->
            if (isSelected) {
                Row {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = tabLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onPrimary,
                        maxLines = 1,
                    )
                }
            } else {
                Spacer(Modifier.width(0.dp))
            }
        }
    }
}

private fun android.content.Context.openUrl(url: String) {
    runCatching {
        startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun android.content.Context.copyToClipboard(text: String) {
    val manager = getSystemService(android.content.ClipboardManager::class.java)
    manager?.setPrimaryClip(android.content.ClipData.newPlainText("Vyxel Store", text))
}
