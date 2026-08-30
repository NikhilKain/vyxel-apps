package com.vythera.vyxelapps.expressive.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vythera.vyxelapps.expressive.data.CatalogRepository
import com.vythera.vyxelapps.expressive.data.Settings
import com.vythera.vyxelapps.expressive.data.SettingsStore
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.AppRail
import com.vythera.vyxelapps.expressive.data.model.Platform
import com.vythera.vyxelapps.expressive.data.model.SourceId
import com.vythera.vyxelapps.expressive.data.model.SourceState
import com.vythera.vyxelapps.expressive.install.ApkInstaller
import com.vythera.vyxelapps.expressive.install.DownloadManager
import com.vythera.vyxelapps.expressive.install.DownloadState
import com.vythera.vyxelapps.expressive.install.InstallOutcome
import com.vythera.vyxelapps.expressive.install.InstallResultReceiver
import com.vythera.vyxelapps.expressive.install.InstalledApp
import com.vythera.vyxelapps.expressive.install.InstalledApps
import com.vythera.vyxelapps.expressive.install.UpdateCandidate
import com.vythera.vyxelapps.expressive.ui.components.InstallAction
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val loading: Boolean = true,
    val rails: List<AppRail> = emptyList(),
    val hero: List<AppItem> = emptyList(),
    val error: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<AppItem> = emptyList(),
    val submitted: Boolean = false,
)

data class DetailUiState(
    val item: AppItem? = null,
    val resolving: Boolean = false,
    /** Non-null once the description has been translated into the chosen language. */
    val translatedDescription: String? = null,
    /**
     * The changelog in the chosen language.
     *
     * Translating only the description left the page half-translated: "What's New" is
     * often the longest prose on the screen and stayed in the author's language, which
     * reads like the translate button didn't work.
     */
    val translatedChangelog: String? = null,
    val translating: Boolean = false,
)


@OptIn(FlowPreview::class)
class StoreViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    private val repository = CatalogRepository(app, settingsStore)
    val downloads = DownloadManager(app)
    private val installer = ApkInstaller(app)
    private val installedApps = InstalledApps(app)

    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val sourceStates: StateFlow<Map<SourceId, SourceState>> = repository.states

    /** Surfaced in Settings so a bad PAT is visible rather than just "no results". */
    val githubTokenRejected: StateFlow<Boolean> = repository.githubTokenRejected

    private val _home = MutableStateFlow(HomeUiState())
    val home: StateFlow<HomeUiState> = _home.asStateFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _detail = MutableStateFlow(DetailUiState())
    val detail: StateFlow<DetailUiState> = _detail.asStateFlow()

    private val _installed = MutableStateFlow<Map<String, InstalledApp>>(emptyMap())
    val installed: StateFlow<Map<String, InstalledApp>> = _installed.asStateFlow()

    private val _updates = MutableStateFlow<List<UpdateCandidate>>(emptyList())
    val updates: StateFlow<List<UpdateCandidate>> = _updates.asStateFlow()

    private val _scanningUpdates = MutableStateFlow(false)
    val scanningUpdates: StateFlow<Boolean> = _scanningUpdates.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    /**
     * Localized copy for messages raised outside composition.
     *
     * Snackbars are produced here, where there is no `CompositionLocal` to read, so
     * the shell pushes the active table down whenever the language changes rather
     * than the view model reaching for a context.
     */
    var strings: ExpressiveStrings = ExpressiveStrings()
        private set

    fun setStrings(value: ExpressiveStrings) { strings = value }

    /** Platform filter for search, mirroring the Classic UI's filter row. */
    private val _platformFilter = MutableStateFlow<Platform?>(null)
    val platformFilter: StateFlow<Platform?> = _platformFilter.asStateFlow()

    /** Narrows search to apps that drive Shizuku. */
    private val _shizukuOnly = MutableStateFlow(false)
    val shizukuOnly: StateFlow<Boolean> = _shizukuOnly.asStateFlow()

    fun setPlatformFilter(platform: Platform?) {
        _platformFilter.value = platform
        rerunQuery()
    }

    fun setShizukuOnly(enabled: Boolean) {
        _shizukuOnly.value = enabled
        rerunQuery()
    }

    /** Replays the current query so a filter change applies without retyping. */
    private fun rerunQuery() {
        val current = queryFlow.value
        if (current.isNotBlank()) {
            queryFlow.value = ""
            queryFlow.value = current
        }
    }

    /**
     * Everything the user hasn't hidden.
     *
     * Matching is on package name, so hiding an app in one source hides the copies
     * every other source carries too — which is what "hide" has to mean in a store
     * that aggregates four indexes of the same apps.
     */
    private fun visible(items: List<AppItem>): List<AppItem> {
        val hidden = settings.value.hiddenPackages
        if (hidden.isEmpty()) return items
        return items.filter { it.packageName !in hidden }
    }

    /**
     * The search filter row plus the hidden list, in one place.
     *
     * Public because the Expressive search tab renders Classic's engine output rather
     * than this view model's own — so this is the only place the filter row can be
     * applied to what the user actually sees. Before this the platform chips were
     * inert: they set state here, and the screen was drawing a list that had never
     * been through it.
     */
    fun applyFilters(items: List<AppItem>): List<AppItem> {
        val platform = _platformFilter.value
        return visible(items)
            .filter { platform == null || it.platform == platform }
            .filter { !_shizukuOnly.value || it.usesShizuku }
    }

    /**
     * Orders a merged result list by relevance to [query].
     *
     * Needed because the search tab stitches two engines together. Appending the
     * Expressive-only sources after Classic's list left them ranked by nothing at
     * all: searching "instagram" put the real Instagram somewhere past position
     * forty, under a pile of GitHub repos that merely mention it. Running the whole
     * merged list back through the same scorer the repository uses puts an exact
     * name match from a real store where it belongs.
     */
    fun rankResults(items: List<AppItem>, query: String): List<AppItem> =
        if (query.isBlank()) items else repository.rank(items, query)

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private var homeJob: Job? = null

    /**
     * Hits from the sources Classic's engine cannot reach.
     *
     * The search tab draws Classic's results; these are merged in beside them by the
     * shell. Without this Aptoide, Aurora and the module repos were wired up
     * everywhere except the one screen that would show them.
     */
    private val _extraResults = MutableStateFlow<List<AppItem>>(emptyList())
    val extraResults: StateFlow<List<AppItem>> = _extraResults.asStateFlow()

    private var extraJob: Job? = null

    /**
     * Runs the Expressive-only sources for [query].
     *
     * Driven from the search tab alongside Classic's own `onSearch`, and debounced
     * here rather than in the shell so a fast typist does not fire a request per
     * keystroke at Aptoide.
     */
    fun searchExtras(query: String) {
        extraJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _extraResults.value = emptyList()
            return
        }
        extraJob = viewModelScope.launch {
            kotlinx.coroutines.delay(220)
            val hits = runCatching { repository.searchExtras(trimmed, enabledSources()) }
                .getOrDefault(emptyList())
            _extraResults.value = hits
        }
    }

    /**
     * Installs started here but not yet reported on, keyed by [AppItem.id].
     *
     * The install outcome comes back through a broadcast that carries only that id,
     * so the item it belongs to has to be held somewhere until it lands.
     */
    private val pendingInstalls =
        java.util.concurrent.ConcurrentHashMap<String, Pair<AppItem, String>>()

    /**
     * Called once an install has actually succeeded, with the APK still on disk.
     *
     * The shell points this at Classic's `recordInstall`, which is what backs the
     * "Installed" list both shells read. Left as a hook rather than a direct
     * dependency so this view model keeps knowing nothing about Classic's own.
     *
     * The file path is part of the contract because Classic's history entries double
     * as its rollback source — an entry recorded without one lists fine and then fails
     * the moment the user tries to reinstall that version.
     */
    var onInstalled: ((AppItem, String) -> Unit)? = null

    init {
        // Keep the GitHub token in sync so rate limits lift as soon as it's entered.
        settings
            .onEach { repository.updateToken(it.githubToken) }
            .launchIn(viewModelScope)

        // Debounced live search. Results are re-published as each source answers, so
        // the fast sources are on screen while the slow ones are still working.
        queryFlow
            // Classic settles in ~120ms and feels instant; 320ms read as lag.
            .debounce(150)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                flow {
                    if (query.isBlank()) {
                        emit(SearchUiState(query = query))
                        return@flow
                    }
                    emit(SearchUiState(query = query, searching = true))

                    var latest = emptyList<AppItem>()
                    repository.searchStreaming(query, enabledSources()).collect { raw ->
                        val results = applyFilters(raw)
                        latest = results
                        emit(
                            SearchUiState(
                                query = query,
                                searching = true,
                                results = results,
                                submitted = true,
                            )
                        )
                    }
                    emit(
                        SearchUiState(
                            query = query,
                            searching = false,
                            results = latest,
                            submitted = true,
                        )
                    )
                }
            }
            .onEach { _search.value = it }
            .launchIn(viewModelScope)

        // Tell the user when GitHub refuses their token instead of failing silently.
        repository.githubTokenRejected
            .onEach { rejected ->
                if (rejected) {
                    _snackbar.value = strings.snackTokenRejected
                }
            }
            .launchIn(viewModelScope)

        // Reflect install outcomes on the button that started them.
        InstallResultReceiver.outcomes
            .onEach { outcome ->
                when (outcome) {
                    is InstallOutcome.Success -> {
                        downloads.setState(outcome.appId, DownloadState.Installed)
                        _snackbar.value = strings.installedLabel
                        // Hand the finished install back to Classic's history.
                        //
                        // Classic's "Installed" list *is* that history, and this shell
                        // never wrote to it — so anything installed from Expressive was
                        // missing from the list in both shells, and only Classic's own
                        // installs ever showed up. The item is looked up by the id the
                        // session was started with, since the outcome arrives long
                        // after the call that began it.
                        pendingInstalls.remove(outcome.appId)?.let { (item, apkPath) ->
                            onInstalled?.invoke(item, apkPath)
                        }
                        refreshInstalled()
                    }
                    is InstallOutcome.Failure -> {
                        pendingInstalls.remove(outcome.appId)
                        downloads.setState(
                            outcome.appId,
                            DownloadState.Failed(outcome.message),
                        )
                        _snackbar.value = outcome.message
                    }
                }
            }
            .launchIn(viewModelScope)

        loadHome()
        refreshInstalled()
    }

    private fun enabledSources(): Set<SourceId> {
        val current = settings.value
        // Only the genuinely un-installable platforms are gated. Root modules are
        // for this device, so they stay on whatever the desktop setting says.
        return current.enabledSources.filter { source ->
            current.showDesktopSources || !source.platform.isDesktop
        }.toSet()
    }

    // ------------------------------------------------------------------ home

    /**
     * Rails are appended as each source returns rather than replaced at the end, so
     * the fast sources are on screen while the F-Droid index is still downloading.
     */
    fun loadHome(force: Boolean = false) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _home.value = HomeUiState(loading = true, rails = emptyList(), hero = emptyList())

            // One seed per load, so the hero stays put while rails stream in and while
            // the user comes back from a detail page — and re-rolls on a real refresh.
            val heroSeed = kotlin.random.Random.nextInt()

            if (force) runCatching { repository.refreshIndexes() }

            // Search synonyms load alongside the catalogue, never in front of it —
            // search works without them, just less cleverly.
            launch {
                repository.primeSearchTerms(
                    ""
                )
            }

            // Keyed by source so a live rail replaces its CDN placeholder in place
            // instead of the screen growing a second copy of the same source.
            val collected = LinkedHashMap<SourceId, AppRail>()
            runCatching {
                repository.loadHomeStreaming(enabledSources()).collect { rail ->
                    val key = rail.source ?: SourceId.WinGet
                    collected[key] = rail
                    val ranked = collected.values
                        .sortedBy { repository.railRank(it.source ?: SourceId.WinGet) }
                        // Hidden apps are dropped here, before anything downstream
                        // sees them, so one filter covers the hero, the category
                        // rails and the source rails alike.
                        .map { r -> r.copy(items = visible(r.items)) }
                        // Modules get one doorway under the hero and a screen of
                        // their own. Leaving their source rails in the long tail as
                        // well would say the same thing twice, and put rows nobody
                        // can install between the reader and the apps.
                        .filterNot { it.source?.platform == Platform.Module }
                    val hero = repository.heroPicks(ranked, seed = heroSeed)

                    // Order: what's new, then what apps are FOR, then the
                    // source-shaped rails as a long tail. Categories carry the
                    // page — nobody arrives wanting a Codeberg app, they arrive
                    // wanting a podcast player — but the source rails still earn
                    // their place further down for people browsing by provenance.
                    val withNew = listOfNotNull(repository.newlyLaunched(ranked)) +
                        repository.categoryRails(ranked) +
                        listOfNotNull(repository.shizukuRail(ranked)) +
                        ranked

                    _home.value = HomeUiState(
                        loading = true,
                        // Each app appears once across the whole page, and never
                        // directly under its own hero card.
                        rails = repository.dedupeRails(
                            withNew,
                            hero.mapTo(HashSet()) { it.dedupeKey },
                        ),
                        hero = hero,
                    )
                }
            }.onFailure { error ->
                _home.value = _home.value.copy(
                    loading = false,
                    error = error.message ?: "Couldn't load the catalog",
                )
                return@launch
            }

            _home.value = _home.value.copy(
                loading = false,
                error = if (collected.isEmpty()) "No sources returned anything" else null,
            )
        }
    }

    // ---------------------------------------------------------------- search

    fun onQueryChange(query: String) {
        queryFlow.value = query
        _search.value = _search.value.copy(query = query)
    }

    fun clearSearch() {
        searchJob?.cancel()
        queryFlow.value = ""
        _search.value = SearchUiState()
    }

    // ---------------------------------------------------------------- detail

    fun select(item: AppItem) {
        // Resolve on the fact, not just on the flag.
        //
        // Trusting needsReleaseLookup alone meant one bad producer could leave an
        // Android entry permanently uninstallable — which is exactly what
        // happened when the CDN index dropped apk_url. If it is installable in
        // principle and has no download URL, it is worth one lookup.
        val mustResolve = item.needsReleaseLookup ||
            (item.platform == Platform.Android && item.downloadUrl == null)
        _detail.value = DetailUiState(item = item, resolving = mustResolve)
        if (mustResolve) {
            viewModelScope.launch {
                val resolved = runCatching { repository.resolve(item) }.getOrDefault(item)
                // Guard against a newer selection landing while this was in flight.
                if (_detail.value.item?.id == item.id) {
                    // Keep any translation already produced for this same app; the
                    // release lookup only changes version and download fields.
                    _detail.value = _detail.value.copy(item = resolved, resolving = false)
                }
            }
        }
    }

    fun clearDetail() { _detail.value = DetailUiState() }

    /**
     * Translates the open app's description into the language chosen in settings.
     *
     * Uses the same endpoint and language mapping as Classic, so both shells produce
     * identical text. The result is held on the detail state rather than cached
     * globally: descriptions are short, and a stale cache after a language change was
     * the bug that made Classic clear its translations on every settings write.
     */
    fun translateDetailDescription(language: String) {
        val current = _detail.value
        val item = current.item ?: return
        val source = item.description.ifBlank { item.summary }
        if (source.isBlank() || current.translating) return

        viewModelScope.launch {
            _detail.value = _detail.value.copy(translating = true)
            val target = com.vythera.vyxelapps.translationCodeFor(language)

            suspend fun translate(text: String?): String? =
                text?.takeIf { it.isNotBlank() }?.let {
                    runCatching {
                        com.vythera.vyxelapps.translateText(it.take(4000), target)
                    }.getOrNull()?.takeIf { out -> out.isNotBlank() }
                }

            val body = translate(source)
            // Sequential rather than parallel: the endpoint is a free public one and
            // firing both at once is what gets a client throttled.
            val notes = translate(item.changelog)

            // A different app may have been opened while the request was in flight.
            if (_detail.value.item?.id == item.id) {
                _detail.value = _detail.value.copy(
                    translating = false,
                    translatedDescription = body,
                    translatedChangelog = notes,
                )
            }
            if (body == null) _snackbar.value = strings.snackTranslationFailed
        }
    }

    /** Drops the translation so the author's own wording shows again. */
    fun clearTranslation() {
        _detail.value = _detail.value.copy(
            translatedDescription = null,
            translatedChangelog = null,
        )
    }

    // --------------------------------------------------------------- install

    fun installAction(item: AppItem): InstallAction {
        val pkg = item.packageName
        val onDevice = pkg?.let { _installed.value[it] }
        return when {
            item.platform != com.vythera.vyxelapps.expressive.data.model.Platform.Android ->
                InstallAction.Unavailable
            onDevice != null && item.versionCode > onDevice.versionCode -> InstallAction.Update
            onDevice != null -> InstallAction.Open
            item.downloadUrl == null -> InstallAction.Unavailable
            else -> InstallAction.Install
        }
    }

    fun install(item: AppItem) {
        if (!installer.canRequestInstalls()) {
            _snackbar.value = strings.snackEnableUnknownSources
            runCatching {
                val intent = installer.unknownSourcesIntent()
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(intent)
            }
            return
        }

        viewModelScope.launch {
            // A repo entry may not have its APK URL yet.
            val target = if (item.downloadUrl == null && item.needsReleaseLookup) {
                runCatching { repository.resolve(item) }.getOrDefault(item)
            } else item

            if (target.downloadUrl == null) {
                downloads.setState(item.id, DownloadState.Failed("No APK in latest release"))
                _snackbar.value = strings.snackNoApkPublished
                return@launch
            }

            downloads.download(target).onSuccess { file ->
                downloads.setState(item.id, DownloadState.Installing)
                // Remembered against the resolved item, so the history entry records
                // the version that was actually installed rather than the stale one
                // the card was showing.
                pendingInstalls[item.id] = target to file.absolutePath
                installer.install(file, item.id).onFailure { error ->
                    pendingInstalls.remove(item.id)
                    downloads.setState(
                        item.id,
                        DownloadState.Failed(error.message ?: "Install failed"),
                    )
                }
            }
        }
    }

    /**
     * Removes an installed app.
     *
     * Expressive could install but not uninstall, so undoing anything meant leaving
     * for Settings or for the Classic shell. Shizuku users get a silent removal; for
     * everyone else the system uninstaller appears and the package list is polled
     * until it confirms, because the system prompt reports nothing back.
     */
    fun uninstall(item: AppItem) {
        // Older history entries were written before package names were recorded, so
        // there is genuinely nothing to uninstall by. Saying so beats a dead button.
        val pkg = item.packageName?.takeIf { it.isNotBlank() } ?: run {
            _snackbar.value = strings.snackNoPackageToRemove
            return
        }
        viewModelScope.launch {
            val removedSilently = runCatching { installer.uninstall(pkg) }.getOrDefault(false)
            if (removedSilently) {
                downloads.clear(item.id)
                refreshInstalled()
                onUninstalled?.invoke(pkg)
                _snackbar.value = strings.snackUninstalled
                return@launch
            }

            // The system dialog gives no callback, so watch for the package going
            // away. Two minutes is long enough to read the prompt and decide.
            repeat(40) {
                kotlinx.coroutines.delay(3_000)
                if (!installedApps.isInstalled(pkg)) {
                    downloads.clear(item.id)
                    refreshInstalled()
                    onUninstalled?.invoke(pkg)
                    _snackbar.value = strings.snackUninstalled
                    return@launch
                }
            }
        }
    }

    /** Lets the shell prune Classic's install history when an app is removed. */
    var onUninstalled: ((String) -> Unit)? = null

    /** Hides an app across every source, or restores it. */
    fun setHidden(item: AppItem, hidden: Boolean) {
        val pkg = item.packageName?.takeIf { it.isNotBlank() } ?: run {
            _snackbar.value = strings.snackCannotHide
            return
        }
        viewModelScope.launch {
            settingsStore.setHidden(pkg, hidden)
            _snackbar.value = if (hidden) {
                String.format(strings.snackHidden, item.displayName)
            } else {
                String.format(strings.snackUnhidden, item.displayName)
            }
            // The home screen holds an already-filtered snapshot, so it has to be
            // rebuilt for the change to be visible there.
            loadHome()
            rerunQuery()
        }
    }

    fun clearHidden() =
        viewModelScope.launch {
            settingsStore.clearHidden()
            _snackbar.value = strings.snackHiddenCleared
            loadHome()
            rerunQuery()
        }

    fun setInstalledSort(sort: com.vythera.vyxelapps.expressive.data.InstalledSort) =
        viewModelScope.launch { settingsStore.setInstalledSort(sort) }

    // ---------------------------------------------------------- root modules

    private val _modules = MutableStateFlow<List<AppItem>>(emptyList())
    val modules: StateFlow<List<AppItem>> = _modules.asStateFlow()

    private val _modulesLoading = MutableStateFlow(false)
    val modulesLoading: StateFlow<Boolean> = _modulesLoading.asStateFlow()

    /**
     * Loads the module catalogue for the Modules screen.
     *
     * Called when that screen opens rather than at startup: the Alt Repo's second
     * phase is a hundred-odd small requests, and paying for it on every launch to
     * populate a screen most sessions never open would be a poor trade. Once loaded
     * it is kept, so reopening the screen is instant.
     */
    fun loadModules(force: Boolean = false) {
        if (!force && _modules.value.isNotEmpty()) return
        if (_modulesLoading.value) return
        viewModelScope.launch {
            _modulesLoading.value = true
            runCatching { repository.modules() }
                .onSuccess { _modules.value = visible(it) }
                .onFailure { _snackbar.value = it.message ?: strings.snackCatalogFailed }
            _modulesLoading.value = false
        }
    }

    /**
     * Saves a module zip into the public Downloads folder.
     *
     * Deliberately the *system* download manager rather than Vyxel's own: a module
     * has to end up somewhere a root manager's file picker can reach it, and Vyxel's
     * cache directory is private storage that Magisk cannot open. This also gets the
     * notification and the Downloads entry the user expects for a file they will go
     * looking for later.
     */
    fun downloadModuleZip(item: AppItem) {
        val url = item.downloadUrl ?: run {
            _snackbar.value = strings.snackModuleFailed
            return
        }
        val app = getApplication<Application>()
        val fileName = buildString {
            append(item.displayName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            item.version?.let { append('-').append(it) }
            append(".zip")
        }

        val ok = runCatching {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle(item.displayName)
                .setDescription("Root module · Vyxel Apps")
                .setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    fileName,
                )
            val manager = app.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
                as android.app.DownloadManager
            manager.enqueue(request)
        }.isSuccess

        _snackbar.value = if (ok) {
            String.format(strings.snackModuleDownloading, item.displayName)
        } else {
            strings.snackModuleFailed
        }
    }

    // ------------------------------------------------------- root module install

    /** Live state of a module flash, driving the console sheet. */
    data class ModuleInstallState(
        val item: AppItem? = null,
        val lines: List<String> = emptyList(),
        val running: Boolean = false,
        val success: Boolean? = null,
    )

    private val _moduleInstall = MutableStateFlow(ModuleInstallState())
    val moduleInstall: StateFlow<ModuleInstallState> = _moduleInstall.asStateFlow()

    private val _rootManager = MutableStateFlow<com.vythera.vyxelapps.root.RootManager>(
        com.vythera.vyxelapps.root.RootManager.None
    )
    val rootManager: StateFlow<com.vythera.vyxelapps.root.RootManager> = _rootManager.asStateFlow()

    private val _rootChecking = MutableStateFlow(false)
    val rootChecking: StateFlow<Boolean> = _rootChecking.asStateFlow()

    /**
     * Asks for root and identifies the manager.
     *
     * Only ever called from a deliberate tap — the Settings card's Check button, or
     * an Install on a module. Probing on launch would put an su prompt in front of
     * every user the first time they open the app, most of whom have no root at all.
     */
    fun checkRoot() {
        if (_rootChecking.value) return
        viewModelScope.launch {
            _rootChecking.value = true
            _rootManager.value = runCatching { com.vythera.vyxelapps.root.RootAccess.detectManager() }
                .getOrDefault(com.vythera.vyxelapps.root.RootManager.None)
            _rootChecking.value = false
        }
    }

    /**
     * Downloads a module and flashes it through the device's root manager.
     *
     * The manager's own stdout is streamed into [moduleInstall] rather than reduced
     * to a spinner and a verdict: module installers print their compatibility checks
     * and their reasons for refusing, and throwing that away turns "your kernel is
     * too old" into a silent no-op the user cannot debug.
     *
     * Rebooting afterwards is offered, never automatic.
     */
    fun installModule(item: AppItem) {
        if (_moduleInstall.value.running) return
        viewModelScope.launch {
            _moduleInstall.value = ModuleInstallState(item = item, running = true)

            fun log(line: String) {
                _moduleInstall.value = _moduleInstall.value.let {
                    it.copy(lines = it.lines + line)
                }
            }

            val manager = _rootManager.value.takeIf { it.available }
                ?: runCatching { com.vythera.vyxelapps.root.RootAccess.detectManager() }
                    .getOrDefault(com.vythera.vyxelapps.root.RootManager.None)
                    .also { _rootManager.value = it }

            if (!manager.available) {
                log("No root manager found on this device.")
                log("Magisk, KernelSU or APatch is needed to flash a module.")
                _moduleInstall.value = _moduleInstall.value.copy(running = false, success = false)
                return@launch
            }

            // A repo-listed module has no download until its release is looked up.
            val target = if (item.downloadUrl == null) {
                log("Looking up the latest release…")
                runCatching { repository.resolve(item) }.getOrDefault(item)
            } else item

            if (target.downloadUrl == null) {
                log("This module has no downloadable zip.")
                _moduleInstall.value = _moduleInstall.value.copy(running = false, success = false)
                return@launch
            }

            log("Downloading ${target.displayName}…")
            val file = downloads.download(target).getOrElse { error ->
                log("Download failed: ${error.message}")
                _moduleInstall.value = _moduleInstall.value.copy(running = false, success = false)
                return@launch
            }

            log("Installing with ${manager.label}…")
            val ok = com.vythera.vyxelapps.root.RootAccess.installModule(file, manager) { line ->
                log(line)
            }
            log(if (ok) "Done. Reboot to activate." else "Install failed.")
            _moduleInstall.value = _moduleInstall.value.copy(running = false, success = ok)
        }
    }

    fun dismissModuleInstall() {
        if (_moduleInstall.value.running) return
        _moduleInstall.value = ModuleInstallState()
    }

    /** Reboots, only ever from an explicit confirmation in the console sheet. */
    fun rebootDevice() {
        viewModelScope.launch { runCatching { com.vythera.vyxelapps.root.RootAccess.reboot() } }
    }

    fun open(item: AppItem) {
        val pkg = item.packageName ?: return
        if (!installedApps.launch(pkg)) {
            _snackbar.value = strings.snackNoLauncher
        }
    }

    fun cancelDownload(item: AppItem) {
        downloads.clear(item.id)
    }

    // --------------------------------------------------------------- updates

    private fun refreshInstalled() {
        viewModelScope.launch {
            _installed.value = installedApps.installed()
        }
    }

    fun scanUpdates() {
        viewModelScope.launch {
            _scanningUpdates.value = true
            runCatching {
                val catalog = repository.androidCatalog()
                installedApps.findUpdates(catalog)
            }.onSuccess { _updates.value = it }
                .onFailure { _snackbar.value = it.message ?: strings.snackUpdateScanFailed }
            _scanningUpdates.value = false
            refreshInstalled()
        }
    }

    // -------------------------------------------------------------- settings

    fun setSkin(skin: com.vythera.vyxelapps.expressive.ui.theme.VyxelSkin) =
        viewModelScope.launch { settingsStore.setSkin(skin) }

    fun setThemeMode(mode: com.vythera.vyxelapps.expressive.ui.theme.ThemeMode) =
        viewModelScope.launch { settingsStore.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { settingsStore.setDynamicColor(enabled) }

    fun setMotionIntensity(intensity: com.vythera.vyxelapps.expressive.ui.theme.MotionIntensity) =
        viewModelScope.launch { settingsStore.setMotionIntensity(intensity) }

    fun setGithubToken(token: String) =
        viewModelScope.launch {
            // Pasted tokens routinely carry whitespace or a trailing newline, which
            // GitHub rejects as malformed credentials.
            settingsStore.setGithubToken(token.trim())
            _snackbar.value = if (token.isBlank()) strings.snackTokenCleared else strings.snackTokenSaved
        }

    fun setShowDesktopSources(enabled: Boolean) =
        viewModelScope.launch {
            settingsStore.setShowDesktopSources(enabled)
            loadHome()
        }

    fun toggleSource(source: SourceId, enabled: Boolean) =
        viewModelScope.launch {
            settingsStore.toggleSource(source, enabled)
            loadHome()
        }

    // ------------------------------------------------------- backup / restore

    /**
     * Writes the tracked app list as JSON to a user-chosen document.
     *
     * The payload deliberately matches the Classic UI's export shape so a backup
     * taken in either shell restores in the other.
     */
    fun exportBackup(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val installedNow = installedApps.installed()
                val payload = BackupPayload(
                    version = 1,
                    exportedAt = System.currentTimeMillis(),
                    packages = installedNow.keys.sorted(),
                )
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(
                            kotlinx.serialization.json.Json.encodeToString(payload).toByteArray()
                        )
                    } ?: error("Could not open destination")
                }
            }.isSuccess
            _snackbar.value = if (ok) strings.snackBackupExported else strings.snackExportFailed
        }
    }

    /** Reads a backup and reports how many of its apps are missing on this device. */
    fun importBackup(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read file")
                }
                val payload = com.vythera.vyxelapps.expressive.core.net.Net.json
                    .decodeFromString<BackupPayload>(text)
                val onDevice = installedApps.installed().keys
                val missing = payload.packages.filterNot { it in onDevice }
                // Surface the restore set through the Updates tab's catalog matching.
                val catalog = repository.androidCatalog()
                _updates.value = catalog
                    .filter { it.packageName in missing && it.downloadUrl != null }
                    .distinctBy { it.dedupeKey }
                    .map { UpdateCandidate(InstalledApp(it.packageName.orEmpty(), "", 0L, false), it) }
                missing.size to _updates.value.size
            }
            result.onSuccess { (missing, found) ->
                _snackbar.value = when {
                    missing == 0 -> "All backed-up apps are already installed"
                    found == 0 -> "$missing missing, none available in your sources"
                    else -> "$found of $missing restorable — see Updates"
                }
            }.onFailure { _snackbar.value = String.format(strings.snackImportFailed, it.message?.take(60).orEmpty()) }
        }
    }

    @kotlinx.serialization.Serializable
    private data class BackupPayload(
        val version: Int = 1,
        val exportedAt: Long = 0L,
        val packages: List<String> = emptyList(),
    )

    fun clearDownloadCache() {
        val freed = downloads.clearCache()
        _snackbar.value = String.format(strings.snackFreed, formatSize(freed))
    }

    fun downloadCacheSize(): Long = downloads.cacheSize()

    fun dismissSnackbar() { _snackbar.value = null }
}
