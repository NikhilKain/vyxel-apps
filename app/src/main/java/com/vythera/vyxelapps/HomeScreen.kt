package com.vythera.vyxelapps

import android.graphics.Color as AndroidColor
import androidx.compose.animation.slideInHorizontally
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

enum class VAppTab { HOME, SEARCH, INSTALLED, PROFILE, SETTINGS }

private const val Q_TRENDING     = "topic:android apk stars:>500"
private const val Q_MEDIA        = "topic:android media player stars:>100"
private const val Q_TOOLS        = "topic:android utility tool stars:>100"
private const val Q_GAMES        = "topic:android game emulator stars:>100"
private const val Q_BROWSERS     = "topic:android browser privacy stars:>50"
private const val Q_PRODUCTIVITY = "topic:android productivity notes stars:>50"
private const val Q_SECURITY     = "topic:android security stars:>50"
private const val Q_DEVTOOLS     = "topic:android developer-tools stars:>50"
private const val Q_PHOTO        = "topic:android photo video editor stars:>100"
private const val Q_MUSIC        = "topic:android music audio stars:>100"
private const val Q_FINANCE      = "topic:android finance banking stars:>100"
private const val Q_EDUCATION    = "topic:android education learning stars:>100"
private const val Q_FITNESS      = "topic:android fitness health workout stars:>100"
private const val Q_ART          = "topic:android art design creative stars:>100"
private const val Q_NEWS         = "topic:android news reader stars:>100"
private const val Q_SOCIAL       = "topic:android social network stars:>100"
private const val Q_CLOUD        = "topic:android cloud storage files stars:>100"
private const val Q_COOKING      = "topic:android cooking food recipe stars:>50"



@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun HomeScreen(viewModel: AppViewModel = viewModel()) {

    val rawState = viewModel.state   // ← must be FIRST

    /**
     * The catalogue with hidden apps removed, once, before anything reads it.
     *
     * Filtering here rather than at each of the dozen render sites means a source
     * added later cannot forget to honour the hidden list — the screens below never
     * see a hidden entry at all. The detail page is deliberately still reachable
     * (it is opened from an object already in hand), which is what makes "Unhide"
     * possible without a separate management screen.
     */
    val state = remember(rawState) {
        val hidden = rawState.hiddenPackages
        if (hidden.isEmpty()) rawState
        else rawState.copy(
            trending = rawState.trending.withoutHidden(hidden),
            gitlabApps = rawState.gitlabApps.withoutHidden(hidden),
            codebergApps = rawState.codebergApps.withoutHidden(hidden),
            fdroidApps = rawState.fdroidApps.withoutHidden(hidden),
            izzyApps = rawState.izzyApps.withoutHidden(hidden),
            flathubApps = rawState.flathubApps.withoutHidden(hidden),
            wingetApps = rawState.wingetApps.withoutHidden(hidden),
            searchResults = rawState.searchResults.withoutHidden(hidden),
            recommendations = rawState.recommendations.withoutHidden(hidden),
            seeAllApps = rawState.seeAllApps.withoutHidden(hidden),
            platformApps = rawState.platformApps.withoutHidden(hidden),
        )
    }

    val view         = LocalView.current
    val context      = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val homeListState = rememberLazyListState()

    // Scroll states hoisted here so positions survive DETAIL navigation — the
    // AnimatedContent below disposes these screens while a detail is open.
    // SeeAll: keyed on the browse target, so opening a different list starts at
    // the top while returning from a detail restores the old position.
    val seeAllListState    = remember(state.seeAllTitle, state.seeAllQuery, state.seeAllSource) { LazyListState() }
    val searchGridState    = remember { LazyGridState() }
    val searchBentoPattern = rememberBentoPattern()
    val searchScrollKey    = remember { mutableStateOf("") }

    var selectedTab        by remember { mutableStateOf(VAppTab.HOME) }
    var selectedRepo       by remember { mutableStateOf<GitHubRepo?>(null) }
    var showSeeAll         by remember { mutableStateOf(false) }
    var showCompare        by remember { mutableStateOf(false) }
    var lastBackPress      by remember { mutableLongStateOf(0L) }
    var dockVisible        by remember { mutableStateOf(true) }
    var settingsNav        by remember { mutableStateOf("MAIN") }
    var showTrackApp        by remember { mutableStateOf(false) }
    var showModules         by remember { mutableStateOf(false) }
    var showTrackRepoSearch by remember { mutableStateOf(false) }
    var showEnterManually   by remember { mutableStateOf(false) }
    var trackPrefilledPkg   by remember { mutableStateOf("") }
    var trackPrefilledName  by remember { mutableStateOf("") }

    // ── Theme ─────────────────────────────────────────────────────────────
    val isLiquidGlass = state.settings.themeMode == "Liquid Glass Dark" ||
                        state.settings.themeMode == "Liquid Glass Light" ||
                        state.settings.themeMode == NEON_PUNK_MODE       // NEON-PUNK (glass variant)

    // CYBERPUNK: keep the HUD dock pinned like the glass themes — it must not
    // slide away on scroll (see the AnimatedVisibility gate below).
    val isCyberpunkTheme = state.settings.themeMode == CYBERPUNK_MODE

    val baseTheme: AppThemeColors = when {
        state.settings.themeMode == CYBERPUNK_MODE       -> CyberpunkTheme   // CYBERPUNK
        state.settings.themeMode == "Dynamic"            ->
            if (android.os.Build.VERSION.SDK_INT >= 31)
                dynamicAppThemeColors(context, isSystemDark)
            else if (isSystemDark) DarkTheme else LightTheme
        state.settings.themeMode == "Custom"             -> state.customTheme.toAppThemeColors()
        state.settings.themeMode == "AMOLED"             -> AmoledTheme
        state.settings.amoledBlack                       -> AmoledTheme
        state.settings.themeMode == "Light"              -> LightTheme
        state.settings.themeMode == "Dark"               -> DarkTheme
        state.settings.themeMode == "Minimal"            -> MinimalTheme
        state.settings.themeMode == "Sunset"             -> SunsetTheme
        state.settings.themeMode == "Liquid Glass Dark"  -> LiquidGlassDarkTheme
        state.settings.themeMode == "Liquid Glass Light" -> LiquidGlassLightTheme
        state.settings.themeMode == NEON_PUNK_MODE       -> NeonPunkTheme   // NEON-PUNK
        state.settings.themeMode == "System"             -> if (isSystemDark) DarkTheme else LightTheme
        else                                              -> DarkTheme
    }

    val theme: AppThemeColors = run {
        // NEON-PUNK / CYBERPUNK: fixed accent — the user accent override doesn't apply
        if (baseTheme.isNeonPunk() || baseTheme.isCyberpunk()) return@run baseTheme
        val manualAccent = state.accentColor
        val eff = manualAccent ?: baseTheme.accent
        if (manualAccent != null) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(
                android.graphics.Color.rgb(
                    (eff.red   * 255).toInt(),
                    (eff.green * 255).toInt(),
                    (eff.blue  * 255).toInt()
                ), hsv
            )
            val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
            val alt = Color(android.graphics.Color.HSVToColor(floatArrayOf(
                (h + 35f) % 360f, (s * 0.85f).coerceIn(0f, 1f), v
            )))
            val tertiary = Color(android.graphics.Color.HSVToColor(floatArrayOf(
                (h + 70f) % 360f, (s * 0.75f).coerceIn(0f, 1f), (v * 0.90f).coerceIn(0f, 1f)
            )))
            val container = Color(android.graphics.Color.HSVToColor(floatArrayOf(
                h, (s * 0.50f).coerceIn(0f, 1f), (v * 0.55f).coerceIn(0f, 1f)
            )))
            baseTheme.copy(
                accent          = eff,
                dockForeground  = eff,
                accentAlt       = alt,
                accentTertiary  = tertiary,
                accentContainer = container
            )
        } else {
            baseTheme.copy(accent = eff, dockForeground = eff)
        }
    }
    var fontFamily by remember(state.settings.fontName) {
        mutableStateOf(fontFamilyFor(state.settings.fontName.ifEmpty { "Default" }))
    }
    LaunchedEffect(state.settings.fontName) {
        val name = state.settings.fontName.ifEmpty { "Default" }
        if (name in googleFontNames) {
            fontFamily = loadGoogleFont(context, name)
        }
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !theme.isDark
        }
    }

    // Reading the snapshot map here subscribes to it directly — no remember() key
    // needed, and unrelated UiState changes no longer invalidate it.
    val installedSet = viewModel.installStates
        .filterValues { it.isInstalled }
        .keys

    // Preloaded multi-source apps, so a source chip in Search works even before
    // the user types anything. GitHub repos load with source=null (Gson drops the
    // Kotlin default), so normalize them here to match the chip's AppSource.GITHUB.
    val searchSourcePool = remember(
        state.trending, state.gitlabApps, state.codebergApps,
        state.fdroidApps, state.izzyApps, state.flathubApps, state.wingetApps
    ) {
        (state.trending + state.gitlabApps + state.codebergApps +
            state.fdroidApps + state.izzyApps + state.flathubApps + state.wingetApps)
            .map { if (it.source == null) it.copy(source = AppSource.GITHUB) else it }
            .distinctBy { it.id }
    }

    // ── Back ──────────────────────────────────────────────────────────────
    BackHandler {
        when {
            showEnterManually -> { showEnterManually = false }
            showTrackRepoSearch -> { showTrackRepoSearch = false; viewModel.clearTrackSearch() }
            showTrackApp -> showTrackApp = false
            showModules -> showModules = false
            showCompare -> { showCompare = false; viewModel.setCompareTarget(null) }
            selectedRepo != null -> {
                viewModel.refreshInstall(selectedRepo!!.id)
                selectedRepo = null
            }
            showSeeAll -> showSeeAll = false
            selectedTab == VAppTab.SEARCH &&
                (state.platform != AppPlatform.ALL || state.selectedSubCategories.isNotEmpty()) -> {
                viewModel.clearSearchFilter()
            }
            selectedTab != VAppTab.HOME -> selectedTab = VAppTab.HOME
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPress < 2000) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackPress = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    LaunchedEffect(selectedRepo) {
        selectedRepo?.let {
            viewModel.fetchRelease(it)
            viewModel.fetchScreenshots(it)
        }
    }
    LaunchedEffect(selectedRepo, state.settings.language) {
        if (selectedRepo != null && state.settings.language != "English") {
            viewModel.translateDescription(selectedRepo!!)
        }
    }

    val glassBackdrop = rememberLayerBackdrop { drawContent() }
    val bgBackdrop    = rememberLayerBackdrop { drawContent() }

    // CYBERPUNK: one shared clock (seconds) drives cheap draw-phase name glitches
    val cyberClock = if (state.settings.themeMode == CYBERPUNK_MODE && state.settings.cyberpunkEffects) {
        val tr = rememberInfiniteTransition(label = "cyberClk")
        tr.animateFloat(0f, 300f, infiniteRepeatable(tween(300_000, easing = LinearEasing), RepeatMode.Restart), label = "cyberClk")
    } else null

    CompositionLocalProvider(
        LocalTheme provides theme,
        LocalApkAbsentIds provides state.apkAbsentIds,
        LocalStrings provides stringsForLanguage(state.settings.language),
        LocalCyberpunkFx provides state.settings.cyberpunkEffects,   // CYBERPUNK
        LocalCyberClock provides cyberClock,                         // CYBERPUNK
        LocalIsLiquidGlass provides isLiquidGlass,
        LocalGlassBackdrop     provides if (isLiquidGlass) glassBackdrop else null,
        LocalGlassBgBackdrop   provides if (isLiquidGlass) bgBackdrop    else null,
        LocalGlassWallpaperUri     provides if (isLiquidGlass) state.settings.liquidGlassWallpaperUri     else "",
        // NEON-PUNK: fixed tuned glass values; other glass themes stay user-adjustable
        LocalGlassBlur             provides when {
            !isLiquidGlass                                 -> 10f
            state.settings.themeMode == NEON_PUNK_MODE     -> NP_GLASS_BLUR
            else                                           -> state.settings.liquidGlassBlur
        },
        LocalGlassEdgeIntensity    provides when {
            !isLiquidGlass                                 -> 1f
            state.settings.themeMode == NEON_PUNK_MODE     -> NP_GLASS_EDGE
            else                                           -> state.settings.liquidGlassEdge
        },
        LocalGlassRefraction       provides when {
            !isLiquidGlass                                 -> 1f
            state.settings.themeMode == NEON_PUNK_MODE     -> NP_GLASS_REFRACTION
            else                                           -> state.settings.liquidGlassRefraction
        },
        LocalGlassNavBlur          provides if (isLiquidGlass) state.settings.liquidGlassNavBlur      else 10f,
        LocalGlassNavEdgeIntensity provides if (isLiquidGlass) state.settings.liquidGlassNavEdge      else 1f,
        LocalGlassNavRefraction    provides if (isLiquidGlass) state.settings.liquidGlassNavRefraction else 1f,
        LocalGlassNavTextColor     provides if (isLiquidGlass && state.settings.liquidGlassNavTextColor.isNotEmpty()) {
            runCatching { Color(AndroidColor.parseColor(state.settings.liquidGlassNavTextColor)) }.getOrNull()
        } else null
    ) {
        val m3Colors = if (theme.isDark) darkColorScheme(
            primary              = theme.accent,
            primaryContainer     = theme.accentContainer,
            onPrimaryContainer   = theme.onAccentContainer,
            secondary            = theme.accentAlt,
            tertiary             = theme.accentTertiary,
            tertiaryContainer    = theme.accentTertiaryContainer,
            surface              = theme.bgSurface,
            surfaceContainer     = theme.bgSurfaceAlt,
            surfaceContainerHigh = theme.bgSurfaceHigh,
            background           = theme.bgPrimary,
            onBackground         = theme.textPrimary,
            onSurface            = theme.textPrimary,
            onSurfaceVariant     = theme.textSecondary,
            outline              = theme.border,
            outlineVariant       = theme.borderVariant
        ) else lightColorScheme(
            primary              = theme.accent,
            primaryContainer     = theme.accentContainer,
            onPrimaryContainer   = theme.onAccentContainer,
            secondary            = theme.accentAlt,
            tertiary             = theme.accentTertiary,
            tertiaryContainer    = theme.accentTertiaryContainer,
            surface              = theme.bgSurface,
            surfaceContainer     = theme.bgSurfaceAlt,
            surfaceContainerHigh = theme.bgSurfaceHigh,
            background           = theme.bgPrimary,
            onBackground         = theme.textPrimary,
            onSurface            = theme.textPrimary,
            onSurfaceVariant     = theme.textSecondary,
            outline              = theme.border,
            outlineVariant       = theme.borderVariant
        )

        // CYBERPUNK: sharp shapes + Orbitron/mono typography override the user's
        // font choice; every other theme keeps the fontFamily-based typography.
        val isCyberpunk = state.settings.themeMode == CYBERPUNK_MODE
        val m3Typography = if (isCyberpunk) cyberpunkTypography(MaterialTheme.typography)
        else MaterialTheme.typography.run {
                copy(
                    displayLarge   = displayLarge.copy(fontFamily   = fontFamily),
                    displayMedium  = displayMedium.copy(fontFamily  = fontFamily),
                    displaySmall   = displaySmall.copy(fontFamily   = fontFamily),
                    headlineLarge  = headlineLarge.copy(fontFamily  = fontFamily),
                    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
                    headlineSmall  = headlineSmall.copy(fontFamily  = fontFamily),
                    titleLarge     = titleLarge.copy(fontFamily     = fontFamily),
                    titleMedium    = titleMedium.copy(fontFamily    = fontFamily),
                    titleSmall     = titleSmall.copy(fontFamily     = fontFamily),
                    bodyLarge      = bodyLarge.copy(fontFamily      = fontFamily),
                    bodyMedium     = bodyMedium.copy(fontFamily     = fontFamily),
                    bodySmall      = bodySmall.copy(fontFamily      = fontFamily),
                    labelLarge     = labelLarge.copy(fontFamily     = fontFamily),
                    labelMedium    = labelMedium.copy(fontFamily    = fontFamily),
                    labelSmall     = labelSmall.copy(fontFamily     = fontFamily)
                )
            }
        MaterialTheme(
            colorScheme = m3Colors,
            shapes      = if (isCyberpunk) CyberpunkShapes else MaterialTheme.shapes,   // CYBERPUNK
            typography  = m3Typography
        ) {
            // Hoist screen state so bottomBar can read it
            var lastRepo by remember { mutableStateOf<GitHubRepo?>(null) }

            // ── CDN announcement (announcement.json) — shown once per announcement ──
            // The expiry re-check matters for a session that was already open when
            // the deadline passed: fetch only runs at launch, so without this the
            // banner would linger until the app was restarted.
            AnnouncementHost(
                announcement = state.announcement,
                onDismiss    = { viewModel.dismissAnnouncement() }
            )
            if (selectedRepo != null) lastRepo = selectedRepo

            // Module install console, above everything: a flash in progress is the
            // one thing that must not be navigated away from half-done.
            state.moduleInstall?.let { ui ->
                ModuleInstallDialog(
                    ui        = ui,
                    onDismiss = { viewModel.dismissModuleInstall() },
                    onReboot  = { viewModel.rebootDevice() }
                )
            }

            val currentScreen = when {
                showCompare -> "COMPARE"
                selectedRepo != null -> "DETAIL"
                showSeeAll -> "SEE_ALL"
                showTrackRepoSearch -> "TRACK_REPO_SEARCH"
                showTrackApp -> "TRACK_APP"
                showModules -> "MODULES"
                else -> "TABS"
            }

            Scaffold(
                containerColor      = Color.Transparent,
                contentWindowInsets = WindowInsets(0)
            ) { _ ->

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

                    // Outer wrapper: captures blob gradient + all content so navbar blurs both layers
                    Box(modifier = Modifier.fillMaxSize().then(
                        if (isLiquidGlass) Modifier.layerBackdrop(glassBackdrop) else Modifier
                    )) {
                    // Glass background captured here so cards/chips/search can blur it
                    if (isLiquidGlass) {
                        GlassScreenBackground(
                            modifier = Modifier.fillMaxSize().layerBackdrop(bgBackdrop)
                        )
                    }

                    AnimatedContent(
                        targetState  = currentScreen,
                        transitionSpec = {
                            val order   = listOf("TABS", "SEE_ALL", "DETAIL", "COMPARE", "TRACK_APP", "TRACK_REPO_SEARCH")
                            val forward = order.indexOf(targetState) > order.indexOf(initialState)
                            if (forward) {
                                (slideInHorizontally(tween(320)) { it } + fadeIn(tween(250))) togetherWith
                                        (slideOutHorizontally(tween(280)) { -it } + fadeOut(tween(180)))
                            } else {
                                (slideInHorizontally(tween(320)) { -it } + fadeIn(tween(250))) togetherWith
                                        (slideOutHorizontally(tween(280)) { it } + fadeOut(tween(180)))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label    = "screen_nav"
                    ) { screen ->
                        val repo = lastRepo
                        when (screen) {
// ── Compare ───────────────────────────────────
                            "COMPARE" -> {
                                if (repo != null) {
                                    CompareScreen(
                                        leftRepo      = repo,
                                        rightRepo     = state.compareTargetRepo,
                                        searchResults = state.searchResults,
                                        onSearch      = { viewModel.onSearch(it) },
                                        onPickRight   = { viewModel.setCompareTarget(it) },
                                        onBack        = { showCompare = false; viewModel.setCompareTarget(null) }
                                    )
                                }
                            }
// ── Detail ────────────────────────────────────
                            "DETAIL" -> {
                                if (repo != null) {
                                    val installState = viewModel.installStates[repo.id] ?: InstallState()
                                    AppDetailScreen(
                                        repo                  = repo,
                                        installState          = installState,
                                        isFavourite           = state.favourites.any { f -> f.id == repo.id },
                                        translatedDesc        = state.translatedDescriptions[repo.id],
                                        translatedReadme      = state.translatedReadmes[repo.id],
                                        isTranslating         = state.isTranslating[repo.id] ?: false,
                                        translatedReleaseBody = state.translatedReleaseBodies[repo.id],
                                        isTranslatingRelease  = state.isTranslatingRelease[repo.id] ?: false,
                                        state                 = state,
                                        screenshots           = state.screenshots[repo.id] ?: emptyList(),
                                        readme                = state.readmes[repo.id],
                                        onInstall             = { installState.apkAsset?.let { a -> viewModel.downloadAndInstall(repo, a) } },
                                        onDownloadOnly        = { installState.apkAsset?.let { a -> viewModel.downloadOnly(repo, a) } },
                                        onUninstall           = { viewModel.uninstall(repo) },
                                        onCancelDownload      = { viewModel.cancelDownload(repo) },
                                        onTranslate           = { viewModel.translateDescription(repo) },
                                        onTranslateRelease    = { viewModel.translateReleaseBody(repo) },
                                        onToggleFavourite     = { viewModel.toggleFavourite(repo) },
                                        onIgnoreVersion       = {
                                            installState.release?.tag_name?.let { viewModel.ignoreVersion(repo.id, it) }
                                        },
                                        onCompare             = { showCompare = true },
                                        onToggleHidden        = { hide -> viewModel.setHidden(repo, hide) },
                                        onSelectRelease       = { rel -> viewModel.selectRelease(repo.id, rel) },
                                        onSelectAsset         = { asset -> viewModel.selectAsset(repo.id, asset) },
                                        onBack                = { viewModel.refreshInstall(repo.id); selectedRepo = null }
                                    )
                                }
                            }
// ── See All ───────────────────────────────────
                            "SEE_ALL" -> SeeAllScreen(
                                title         = state.seeAllTitle,
                                apps          = state.seeAllApps,
                                installed     = installedSet,
                                isLoading     = state.isLoadingSeeAll,
                                useTileColors = true,
                                listState     = seeAllListState,
                                onLoadMore    = { viewModel.loadMoreSeeAll() },
                                onAppClick    = { r -> viewModel.addToHistory(r); selectedRepo = r },
                                onBack        = { showSeeAll = false }
                            )
// ── Modules ───────────────────────────────────
                            "MODULES" -> ModulesScreen(
                                modules   = state.modules,
                                isLoading = state.isLoadingModules,
                                onBack    = { showModules = false },
                                onRefresh = { viewModel.loadModules() },
                                onInstall = { viewModel.installModule(it) }
                            )
// ── Track App ─────────────────────────────────
                            "TRACK_APP" -> TrackAppScreen(
                                onAppSelected = { pkg, name ->
                                    trackPrefilledPkg   = pkg
                                    trackPrefilledName  = name
                                    viewModel.searchForTracking(name)
                                    showTrackApp        = false
                                    showTrackRepoSearch = true
                                },
                                onDismiss = { showTrackApp = false }
                            )
// ── Track Repo Search ─────────────────────────
                            "TRACK_REPO_SEARCH" -> TrackRepoSearchScreen(
                                appName        = trackPrefilledName,
                                packageName    = trackPrefilledPkg,
                                results        = state.trackSearchResults,
                                isSearching    = state.isTrackSearching,
                                onQueryChange  = { viewModel.searchForTracking(it) },
                                onRepoSelected = { repo ->
                                    val fullName = repo.full_name.ifBlank { "${repo.owner.login}/${repo.name}" }
                                    viewModel.addTrackedApp(TrackedApp(
                                        packageName   = trackPrefilledPkg,
                                        appName       = trackPrefilledName.ifBlank { repo.name },
                                        repoFullName  = fullName,
                                        repoUrl       = "https://github.com/$fullName"
                                    ))
                                    viewModel.clearTrackSearch()
                                    showTrackRepoSearch = false
                                    trackPrefilledPkg   = ""
                                    trackPrefilledName  = ""
                                },
                                onEnterManually = { showEnterManually = true },
                                onDismiss       = { showTrackRepoSearch = false; viewModel.clearTrackSearch() }
                            )
// ── Tabs ──────────────────────────────────────
                            else -> Box(modifier = Modifier.fillMaxSize()) {
                             AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    val toSearch   = targetState  == VAppTab.SEARCH
                                    val fromSearch = initialState == VAppTab.SEARCH
                                    when {
                                        // HOME → SEARCH: home drifts up, search rises from below
                                        toSearch   -> (slideInVertically(tween(420, easing = EaseOutQuart)) { it / 4 } + fadeIn(tween(360, easing = FastOutSlowInEasing))) togetherWith
                                                      (slideOutVertically(tween(380, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(300, easing = FastOutSlowInEasing)))
                                        // SEARCH → HOME: gentle reverse
                                        fromSearch -> (slideInVertically(tween(420, easing = EaseOutQuart)) { -it / 4 } + fadeIn(tween(360, easing = FastOutSlowInEasing))) togetherWith
                                                      (slideOutVertically(tween(380, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(300, easing = FastOutSlowInEasing)))
                                        else -> {
                                            val goingRight = targetState.ordinal > initialState.ordinal
                                            if (goingRight) {
                                                (slideInHorizontally(tween(280)) { it } + fadeIn(tween(200))) togetherWith
                                                        (slideOutHorizontally(tween(280)) { -it } + fadeOut(tween(160)))
                                            } else {
                                                (slideInHorizontally(tween(280)) { -it } + fadeIn(tween(200))) togetherWith
                                                        (slideOutHorizontally(tween(280)) { it } + fadeOut(tween(160)))
                                            }
                                        }
                                    }
                                },
                                label = "tab_switch"
                            ) { tab ->
                                when (tab) {
                                    VAppTab.HOME -> HomeTab(
                                        state          = state,
                                        viewModel      = viewModel,
                                        installed      = installedSet,
                                        listState      = homeListState,
                                        onAppClick     = { r -> viewModel.addToHistory(r); selectedRepo = r },
                                        onSeeAll       = { showSeeAll = true },
                                        onSearchClick  = { selectedTab = VAppTab.SEARCH },
                                        onScrollChange = { scrolling -> dockVisible = !scrolling },
                                        onProfileClick = { selectedTab = VAppTab.PROFILE},
                                        onOpenModules  = { showModules = true }
                                    )
                                    VAppTab.SEARCH -> SearchScreen(
                                        query                 = state.searchQuery,
                                        results               = state.searchResults,
                                        platform              = state.platform,
                                        selectedSubCategories = state.selectedSubCategories,
                                        installed             = installedSet,
                                        suggestions           = state.trending.take(10),
                                        sourcePool            = searchSourcePool,
                                        recentSearches        = state.recentSearches,
                                        onRecentClick         = { viewModel.onSearch(it) },
                                        onClearRecent         = { viewModel.clearRecentSearches() },
                                        isSearching           = state.isSearching,
                                        gridState             = searchGridState,
                                        bentoPattern          = searchBentoPattern,
                                        scrollResetKey        = searchScrollKey,
                                        onQueryChange         = { viewModel.onSearch(it) },
                                        onPlatformChange      = { viewModel.setPlatform(it) },
                                        onSubCategoryToggle   = { viewModel.toggleSubCategory(it) },
                                        onAppClick            = { r -> viewModel.addToHistory(r); selectedRepo = r },
                                        isFilterMenuOpen      = state.isFilterMenuOpen,
                                        activeSubMenuPlatform = state.activeSubMenuPlatform,
                                        onToggleFilterMenu    = { viewModel.toggleFilterMenu(it) },
                                        onSetSubMenuPlatform  = { viewModel.setSubMenuPlatform(it) }
                                    )
                                    VAppTab.INSTALLED -> InstalledScreen(
                                        installHistory       = state.installHistory,
                                        installStates        = viewModel.installStates,
                                        updates              = state.updates,
                                        scanResults          = state.multiSourceUpdates,
                                        isScanning           = state.isMultiSourceScanning,
                                        onAppClick           = { r -> selectedRepo = r },
                                        onCheckUpdates       = { viewModel.checkForUpdatesNow() },
                                        onUpdateAll          = { viewModel.updateAll() },
                                        onClearRemoved       = { viewModel.clearRemovedApps() },
                                        onScanAll            = { viewModel.scanAllApps() },
                                        onUpdateScanResult   = { viewModel.downloadFromScanResult(it) },
                                        onOpenScanResult     = { result ->
                                            if (result.repoFullName.isNotEmpty()) {
                                                val parts = result.repoFullName.split("/")
                                                selectedRepo = GitHubRepo(
                                                    id        = result.packageName.hashCode().toLong().let { if (it < 0) -it else it },
                                                    name      = parts.getOrElse(1) { result.appName },
                                                    full_name = result.repoFullName,
                                                    owner     = RepoOwner(login = parts.getOrElse(0) { "" })
                                                )
                                            }
                                        },
                                        isCheckingUpdates    = state.isCheckingUpdates,
                                        trackedApps          = state.settings.trackedApps,
                                        onRemoveTracked      = { viewModel.removeTrackedApp(it) },
                                        onTrackApp           = { showTrackApp = true }
                                    )
                                    VAppTab.PROFILE -> ProfileScreen(
                                        profile         = state.profile,
                                        history         = state.history,
                                        favourites      = state.favourites,
                                        installHistory  = state.installHistory,
                                        updates         = state.updates,
                                        onSave          = { viewModel.updateProfile(it) },
                                        onAppClick      = { r -> selectedRepo = r },
                                        onCheckUpdates  = { viewModel.checkForUpdatesNow() },
                                        onRollback      = { entry -> viewModel.rollbackTo(entry) }
                                    )
                                    VAppTab.SETTINGS -> when (settingsNav) {
                                        "MANAGE_REPOS" -> ManageCustomReposScreen(
                                            customRepos = state.customRepos,
                                            onBack      = { settingsNav = "MAIN" },
                                            onAddNew    = { settingsNav = "ADD_REPO" },
                                            onDelete    = { viewModel.removeCustomRepo(it) }
                                        )
                                        "ADD_REPO" -> AddCustomRepoScreen(
                                            onBack = { settingsNav = "MANAGE_REPOS" },
                                            onSave = { repo ->
                                                viewModel.addCustomRepo(repo)
                                                settingsNav = "MANAGE_REPOS"
                                            }
                                        )
                                        else -> SettingsScreen(
                                            settings             = state.settings,
                                            currentAccent        = state.accentColor,
                                            customTheme          = state.customTheme,
                                            customRepos          = state.customRepos,
                                            liquidGlassUnlocked  = state.liquidGlassUnlocked,
                                            licenseKeyInput      = state.licenseKeyInput,
                                            licenseVerifyState   = state.licenseVerifyState,
                                            onSave               = { viewModel.updateSettings(it) },
                                            onAccentSelect       = { viewModel.setAccentColor(it) },
                                            onCustomThemeSave    = { viewModel.setCustomTheme(it) },
                                            onManageCustomRepos  = { settingsNav = "MANAGE_REPOS" },
                                            onLicenseKeyInput    = { viewModel.setLicenseKeyInput(it) },
                                            onVerifyLicense      = { viewModel.verifyLicenseKey() },
                                            onExportBackup       = { viewModel.exportBackupJson() },
                                            onImportBackup       = { viewModel.importBackupJson(it) },
                                            hiddenPackages       = state.hiddenPackages,
                                            onClearHidden        = { viewModel.clearHidden() }
                                        )
                                    }
                                }
                            }
                            } // end Box(paddingValues)
                        }
                    }
                    } // end glassBackdrop wrapper

                    // Reset dock visibility when sub-screens open
                    LaunchedEffect(selectedRepo, showSeeAll, showCompare) {
                        if (selectedRepo != null || showSeeAll || showCompare) dockVisible = true
                    }

                    // ── Floating dock overlay ──────────────────────────
                    AnimatedVisibility(
                        visible  = (isLiquidGlass || isCyberpunkTheme || dockVisible) && currentScreen == "TABS",
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter    = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
                        exit     = slideOutVertically(tween(180)) { it } + fadeOut(tween(140))
                    ) {
                        FloatingNavBar(
                            selectedTab = selectedTab,
                            updateCount = state.updates.size,
                            onTabSelect = { tab ->
                                if (selectedTab == VAppTab.SEARCH && tab != VAppTab.SEARCH) {
                                    viewModel.clearSearchFilter()
                                }
                                selectedTab  = tab
                                selectedRepo = null
                                showSeeAll   = false
                                dockVisible  = true
                                settingsNav  = "MAIN"
                            }
                        )
                    }

                    // ── Self-update banner ─────────────────────────────
                    val selfUpdate = state.selfUpdateInfo
                    if (selfUpdate != null && !state.selfUpdateDismissed) {
                        SelfUpdateBanner(
                            info      = selfUpdate,
                            onDismiss = { viewModel.dismissSelfUpdate() },
                            modifier  = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }

    // ── Enter Repo sheet (shown over any screen) ──────────────────────────
    if (showEnterManually) {
        EnterRepoSheet(
            prefilledPackage = trackPrefilledPkg,
            prefilledAppName = trackPrefilledName,
            onConfirm = { tracked ->
                viewModel.addTrackedApp(tracked)
                showEnterManually  = false
                trackPrefilledPkg  = ""
                trackPrefilledName = ""
            },
            onDismiss = {
                showEnterManually  = false
                trackPrefilledPkg  = ""
                trackPrefilledName = ""
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// HOME TAB
// ─────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(
    state          : UiState,
    viewModel      : AppViewModel,
    onProfileClick: () -> Unit,
    installed      : Set<Long>,
    listState      : LazyListState,
    onAppClick     : (GitHubRepo) -> Unit,
    onSeeAll       : () -> Unit,
    onSearchClick  : () -> Unit        = {},
    onScrollChange : (Boolean) -> Unit = {},
    /** Opens the root-module browser. */
    onOpenModules  : () -> Unit        = {}
) {
    val theme = LocalTheme.current
    val strings = LocalStrings.current
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) isRefreshing = false
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            onScrollChange(true)
        } else {
            delay(300)
            onScrollChange(false)
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd && !state.isLoadingMore && !state.isLoading && state.platform == AppPlatform.ALL) {
            viewModel.loadMoreTrending()
        }
    }

    fun openSeeAll(title: String, query: String) {
        viewModel.openSeeAll(title, query)
        onSeeAll()
    }

    val isGlassMode = LocalIsLiquidGlass.current
    Box(modifier = Modifier.fillMaxSize().background(
        if (isGlassMode) Color.Transparent else theme.bgPrimary
    )) {
        ScreenBackground(ScreenBg.HOME)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true; viewModel.loadAll()
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        // Deduped category sections, hoisted out of the LazyColumn because
        // remember() cannot be called from LazyListScope.
        //
        // The category queries overlap heavily — a popular media app is also
        // Trending, and frequently Tools as well — so the page used to serve the
        // same apps over and over on the way down. Each app now appears in the
        // first section that carries it. Computed once per data change rather
        // than mutated during composition, which would re-filter on recompose.
        val dedupedSections = remember(
            state.refreshToken, state.recommendations, state.trending,
            state.media, state.tools, state.games, state.browsers,
            state.productivity, state.security, state.devtools,
            state.photoVideo, state.music, state.finance,
            state.education, state.fitness, state.artDesign,
            state.news, state.social, state.cloudStorage, state.cooking,
        ) {
            val seen = HashSet<Long>()
            fun uniq(items: List<GitHubRepo>) = items.filter { seen.add(it.id) }
            listOf(
                strings.sectionRecommended to uniq(state.recommendations),
                strings.sectionTrending to uniq(state.trending),
                strings.sectionMedia to uniq(state.media),
                strings.sectionTools to uniq(state.tools),
                strings.sectionGames to uniq(state.games),
                strings.sectionBrowsers to uniq(state.browsers),
                strings.sectionProductivity to uniq(state.productivity),
                strings.sectionSecurity to uniq(state.security),
                strings.sectionDevTools to uniq(state.devtools),
                strings.sectionPhotoVideo to uniq(state.photoVideo),
                strings.sectionMusic to uniq(state.music),
                strings.sectionFinance to uniq(state.finance),
                strings.sectionEducation to uniq(state.education),
                strings.sectionFitness to uniq(state.fitness),
                strings.sectionArtDesign to uniq(state.artDesign),
                strings.sectionNews to uniq(state.news),
                strings.sectionSocial to uniq(state.social),
                strings.sectionCloudStorage to uniq(state.cloudStorage),
                strings.sectionCooking to uniq(state.cooking),
                // A row of one or two cards reads as a glitch, not a section.
            ).filter { it.second.size >= 3 }
        }
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            item(key = "discover_header") {
                DiscoverHeader(
                    profile        = state.profile,
                    onProfileClick = onProfileClick
                )
            }
            item(key = "search_bar") {
                HomeSearchBar(
                    onSearchClick = onSearchClick,
                    modifier      = Modifier.padding(top = 14.dp)
                )
            }
            item(key = "source_chips") {
                HomeSourceChipsRow(
                    selectedSource = state.selectedSource,
                    onSourceSelect = { viewModel.setSourceFilter(it) },
                    modifier       = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

                if (state.error != null) {
                    item(key = "error") { ErrorPlaceholder(state.error) { viewModel.loadAll() } }
                } else if (state.platform != AppPlatform.ALL) {
                    if (state.platformApps.isEmpty()) {
                        item(key = "plat_load") { LoadingPlaceholder() }
                    } else {
                        item(key = "plat_grid") {
                            PlatformGrid(
                                platform = state.platform,
                                apps = state.platformApps,
                                installed = installed,
                                onAppClick = onAppClick
                            )
                        }
                    }
                } else if (state.isLoading) {
                    item(key = "loading") { LoadingPlaceholder() }
                } else {
                    // Hero banner, collections, sources — hidden when a source filter is active
                    item(key = "featured") {
                        AnimatedVisibility(
                            visible = state.selectedSource == null,
                            enter   = expandVertically(tween(420)) + fadeIn(tween(300, delayMillis = 80)),
                            exit    = slideOutVertically(tween(320)) { -it / 3 } + shrinkVertically(tween(360)) + fadeOut(tween(260))
                        ) {
                            // Seeded by refreshToken so the order is fixed for a
                            // given load: opening an app detail disposes this whole
                            // tab, and without a stable seed the pool re-randomised
                            // on the way back, moving the card the user was aiming
                            // for. Only an actual refresh reshuffles.
                            val featuredPool = remember(
                                state.refreshToken, state.trending, state.fdroidApps, state.izzyApps
                            ) {
                                (state.trending + state.fdroidApps + state.izzyApps)
                                    .filter { it.iconUrlOrNull != null }
                                    .shuffled(kotlin.random.Random(state.refreshToken.toLong()))
                            }
                            // CDN-pinned promos ride at the front of the carousel and
                            // are excluded from the shuffled pool so a pinned app can't
                            // also turn up organically on a later page.
                            val pinned = remember(state.featuredPins) {
                                state.featuredPins.map { it.toGitHubRepo() }
                            }
                            val pinnedLabels = remember(state.featuredPins) {
                                state.featuredPins
                                    .filter { it.label.isNotBlank() }
                                    .associate { it.toGitHubRepo().id to it.label }
                            }
                            // Store-only pins can't open a detail page — there is no
                            // APK and no release history behind them — so they go
                            // straight to the listing instead.
                            val externalPins = remember(state.featuredPins) {
                                state.featuredPins
                                    .filter { it.isExternal }
                                    .associate { it.toGitHubRepo().id to it.storeUrl }
                            }
                            val ctx = LocalContext.current
                            FeaturedCard(
                                apps         = featuredPool,
                                seed         = state.refreshToken,
                                pinned       = pinned,
                                pinnedLabels = pinnedLabels,
                                onAppClick   = { repo ->
                                    val store = externalPins[repo.id]
                                    if (store != null) {
                                        runCatching {
                                            ctx.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(store)
                                                ).addFlags(
                                                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                                )
                                            )
                                        }
                                    } else onAppClick(repo)
                                }
                            )
                        }
                    }
                    // One doorway to the module catalogue, above the collections.
                    //
                    // Modules are a mode, not a category: someone who wants one wants
                    // to filter by family and search properly, and someone who does
                    // not should be able to skip the whole subject in a glance rather
                    // than scroll a rail of things their phone may not be able to
                    // flash.
                    item(key = "modules_entry") {
                        AnimatedVisibility(
                            visible = state.selectedSource == null,
                            enter   = expandVertically(tween(400)) + fadeIn(tween(300, delayMillis = 40)),
                            exit    = slideOutVertically(tween(280)) { -it / 3 } + shrinkVertically(tween(330)) + fadeOut(tween(230))
                        ) {
                            GlassCard(
                                onClick        = onOpenModules,
                                modifier       = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier          = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Modules",
                                            style      = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color      = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Magisk, Zygisk, LSPosed and KernelSU",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                    item(key = "collections") {
                        AnimatedVisibility(
                            visible = state.selectedSource == null,
                            enter   = expandVertically(tween(400)) + fadeIn(tween(300, delayMillis = 40)),
                            exit    = slideOutVertically(tween(280)) { -it / 3 } + shrinkVertically(tween(330)) + fadeOut(tween(230))
                        ) {
                            CollectionsRow { collection ->
                                viewModel.openCollection(collection)
                                onSeeAll()
                            }
                        }
                    }
                    item(key = "sources") {
                        AnimatedVisibility(
                            visible = state.selectedSource == null,
                            enter   = expandVertically(tween(380)) + fadeIn(tween(280)),
                            exit    = slideOutVertically(tween(240)) { -it / 3 } + shrinkVertically(tween(300)) + fadeOut(tween(200))
                        ) {
                            SourcesRow(
                                gitlabCount       = state.gitlabApps.size,
                                codebergCount     = state.codebergApps.size,
                                fdroidCount       = state.fdroidApps.size,
                                flathubCount      = state.flathubApps.size,
                                wingetCount       = state.wingetApps.size,
                                izzyCount         = state.izzyApps.size,
                                customRepos       = state.customRepos,
                                onSourceClick     = { source ->
                                    viewModel.openSourceBrowse(source)
                                    onSeeAll()
                                },
                                onCustomRepoClick = { repo ->
                                    viewModel.openCustomRepoBrowse(repo)
                                    onSeeAll()
                                }
                            )
                        }
                    }
                    // Newly Launched — month-old apps, right below Browse by source
                    if (state.newlyLaunched.isNotEmpty()) {
                        item(key = "newly_launched") {
                            AnimatedVisibility(
                                visible = state.selectedSource == null,
                                enter   = expandVertically(tween(380)) + fadeIn(tween(280)),
                                exit    = slideOutVertically(tween(240)) { -it / 3 } + shrinkVertically(tween(300)) + fadeOut(tween(200))
                            ) {
                                AppRow(strings.sectionNewlyLaunched, state.newlyLaunched, installed, refreshToken = state.refreshToken) { onAppClick(it) }
                            }
                        }
                    }
                    // App cards filtered by the selected source chip
                    when (state.selectedSource) {
                        AppSource.FDROID -> item(key = "fdroid_apps") {
                            AppRow("F-Droid Apps", state.fdroidApps, installed, refreshToken = state.refreshToken) { onAppClick(it) }
                        }
                        AppSource.GITLAB -> item(key = "gitlab_apps") {
                            AppRow("GitLab Apps", state.gitlabApps, installed, refreshToken = state.refreshToken) { onAppClick(it) }
                        }
                        AppSource.CODEBERG -> item(key = "codeberg_apps") {
                            AppRow("Codeberg Apps", state.codebergApps, installed, refreshToken = state.refreshToken) { onAppClick(it) }
                        }
                        AppSource.FLATHUB -> item(key = "flathub_apps") {
                            AppRow("Flathub Apps", state.flathubApps, installed, refreshToken = state.refreshToken) { onAppClick(it) }
                        }
                        AppSource.WINGET -> item(key = "winget_apps") {
                            AppRow("Winget Apps", state.wingetApps, installed, refreshToken = state.refreshToken) { onAppClick(it) }
                        }
                        AppSource.IZZY -> item(key = "izzy_apps") {
                            AppRow("IzzyOnDroid Apps", state.izzyApps, installed, refreshToken = state.refreshToken) { onAppClick(it) }
                        }
                        else -> {
                            // null (All Sources) or GITHUB — deduped category rows,
                            // computed above the LazyColumn (see dedupedSections).
                            dedupedSections.forEach { (title, items) ->
                                item(key = "sec_$title") {
                                    AppRow(title, items, installed, refreshToken = state.refreshToken) { onAppClick(it) }
                                }
                            }
                            if (state.isLoadingMore) {
                                item(key = "load_more") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = theme.accent,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


    }
    } // Box
}
/**
 * "A newer Vyxel is available" banner.
 *
 * Shared with the Expressive shell for the same reason as [AnnouncementHost]: an
 * Expressive user was never being offered the update at all, which is the one banner
 * that must reach everybody.
 */
@Composable
fun SelfUpdateBanner(
    info      : SelfUpdateInfo,
    onDismiss : () -> Unit,
    modifier  : Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarSpace()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ElevatedCard(
            shape     = MaterialTheme.shapes.large,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            colors    = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier  = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Update available: ${info.latestVersion}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (info.changelog.isNotBlank()) {
                        Text(
                            info.changelog.lines().firstOrNull()?.take(60) ?: "",
                            color    = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            style    = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(info.apkUrl)
                    )
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) {
                    Text("Update")
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Renders the CDN announcement, in whichever form it was published.
 *
 * Extracted from the Classic home so the Expressive shell can show the same thing —
 * announcements are how the app talks to its users, and an Expressive user was simply
 * never being told. Both shells call this; there is one implementation.
 *
 * The expiry re-check matters for a session that was already open when the deadline
 * passed: the fetch only runs at launch, so without it the banner would linger until
 * the app was restarted.
 */
@Composable
fun AnnouncementHost(
    announcement : Announcement?,
    onDismiss    : () -> Unit
) {
    val ann     = announcement?.takeIf { !it.isExpired } ?: return
    val context = LocalContext.current
    val accent  = if (ann.accentHex.isNotBlank())
        hexToColor(ann.accentHex, MaterialTheme.colorScheme.primary)
    else MaterialTheme.colorScheme.primary

    val openAction: () -> Unit = {
        if (ann.actionUrl.isNotBlank()) {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(ann.actionUrl)
                    )
                )
            }
        }
        onDismiss()
    }

    if (ann.hasImage) {
        // Artwork banner: the image IS the button — tapping anywhere on it follows
        // actionUrl (the Telegram channel).
        AnnouncementImageBanner(
            announcement = ann,
            accent       = accent,
            onOpen       = openAction,
            onDismiss    = onDismiss
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                ann.title,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        text = { Text(ann.message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            if (ann.actionUrl.isNotBlank()) {
                Button(
                    onClick = openAction,
                    colors  = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(ann.actionLabel.ifBlank { "Open" })
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LocalStrings.current.cancel) }
        }
    )
}

/**
 * Startup announcement rendered as artwork rather than text. The whole image is
 * the tap target (it opens `actionUrl`), with a close affordance in the corner
 * and an optional caption underneath for anyone who can't read the image.
 */
@Composable
private fun AnnouncementImageBanner(
    announcement : Announcement,
    accent       : Color,
    onOpen       : () -> Unit,
    onDismiss    : () -> Unit
) {
    val aspect = announcement.imageAspect.takeIf { it > 0.1f } ?: (16f / 9f)
    // Posters carry fine print, so the artwork is never cropped — and the card is
    // capped at 92% of the screen and scrolls, so a tall poster can't overflow
    // (and take its close button off-screen) on a short device.
    val maxCardHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.92f

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            // Non-dismissible announcements still need a way out, so the close
            // button stays; this only stops an accidental tap-outside.
            dismissOnBackPress    = announcement.dismissible,
            dismissOnClickOutside = announcement.dismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Surface(
                shape       = RoundedCornerShape(24.dp),
                color       = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier    = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxCardHeight)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    AppImage(
                        url                = announcement.imageUrl,
                        contentDescription = announcement.title.ifBlank { "Announcement" },
                        contentScale       = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier           = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .clickable(enabled = announcement.actionUrl.isNotBlank()) { onOpen() }
                    )

                    // title/message are deliberately NOT drawn here: the artwork
                    // already carries the wording, and duplicating it just makes
                    // the card taller. They stay in announcement.json purely as
                    // the fallback for older app versions, which predate
                    // imageUrl and can only render the text dialog.

                    if (announcement.actionUrl.isNotBlank()) {
                        Button(
                            onClick = onOpen,
                            colors  = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 14.dp)
                                .height(46.dp)
                        ) {
                            Text(announcement.actionLabel.ifBlank { "Open" })
                        }
                    } else {
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }

            // Close affordance — sits over the artwork's top-right corner.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss announcement",
                    tint     = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun LoadingPlaceholder() {
    val t = LocalTheme.current
    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = t.accent)
            Spacer(Modifier.height(14.dp))
            Text("Loading apps from all sources…", color = t.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
fun ErrorPlaceholder(message: String, onRetry: () -> Unit) {
    val t = LocalTheme.current
    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("⚠️", fontSize = 44.sp)
            Text(message, color = t.textSecondary, fontSize = 14.sp)
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = t.accent)) {
                Text("Try Again")
            }
        }
    }
}
