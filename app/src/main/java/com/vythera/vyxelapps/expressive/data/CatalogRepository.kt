package com.vythera.vyxelapps.expressive.data

import android.content.Context
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.AppRail
import com.vythera.vyxelapps.expressive.data.model.Platform
import com.vythera.vyxelapps.expressive.data.model.SourceId
import com.vythera.vyxelapps.expressive.data.model.SourceState
import com.vythera.vyxelapps.expressive.data.source.AppSource
import com.vythera.vyxelapps.expressive.data.source.CdnSource
import com.vythera.vyxelapps.expressive.data.source.CodebergSource
import com.vythera.vyxelapps.expressive.data.source.FdroidRepoSource
import com.vythera.vyxelapps.expressive.data.source.FlathubSource
import com.vythera.vyxelapps.expressive.data.source.GitHubSource
import com.vythera.vyxelapps.expressive.data.source.GitLabSource
import com.vythera.vyxelapps.expressive.data.source.AptoideSource
import com.vythera.vyxelapps.expressive.data.source.AuroraSource
import com.vythera.vyxelapps.expressive.data.source.ModuleRepoFormat
import com.vythera.vyxelapps.expressive.data.source.ModuleRepoSource
import com.vythera.vyxelapps.expressive.data.source.WingetSource
import com.vythera.vyxelapps.expressive.data.source.SearchDoc
import com.vythera.vyxelapps.expressive.data.source.normalizeText
import com.vythera.vyxelapps.expressive.data.source.relevanceScore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.io.File

/**
 * Fans requests out to every enabled source and merges the results.
 *
 * Sources are always awaited independently: a rate-limited GitHub or an unreachable
 * mirror degrades to a single empty rail with an error chip rather than failing the
 * whole screen.
 */
class CatalogRepository(
    context: Context,
    private val settingsStore: SettingsStore,
) {

    private val cacheDir = File(context.cacheDir, "catalog").apply { mkdirs() }

    private companion object {
        const val TAG = "VyxelCatalog"

        /** Below this a rail looks broken rather than curated, so it is dropped. */
        const val MIN_RAIL_ITEMS = 4

        /**
         * Below this the published module catalogue is treated as missing.
         *
         * A handful of entries means the build partially failed or the file is a
         * placeholder, and scraping live is better than showing a stub.
         */
        const val MIN_CDN_MODULES = 50

        /**
         * Purpose-based sections, matched against each source's own category
         * vocabulary. Deliberately few: six sections a reader can hold in their
         * head beats eighteen they scroll past.
         */
        val CATEGORY_RAILS: List<Triple<String, String, (List<String>) -> Boolean>> = listOf(
            Triple("Privacy & security", "Keep your data yours") { c: List<String> ->
                c.any { it.contains("security") || it.contains("privacy") }
            },
            Triple("Media & players", "Music, video and podcasts") { c: List<String> ->
                c.any {
                    it.contains("multimedia") || it.contains("music") ||
                        it.contains("video") || it.contains("audio")
                }
            },
            Triple("Reading & writing", "Notes, readers and documents") { c: List<String> ->
                c.any {
                    it.contains("writing") || it.contains("reading") ||
                        it.contains("book") || it.contains("note")
                }
            },
            Triple("Internet & messaging", "Browsers, chat and feeds") { c: List<String> ->
                c.any {
                    it.contains("internet") || it.contains("browser") ||
                        it.contains("messaging") || it.contains("social")
                }
            },
            Triple("Getting things done", "Calendars, tasks and money") { c: List<String> ->
                c.any {
                    it.contains("time") || it.contains("money") ||
                        it.contains("productivity") || it.contains("finance")
                }
            },
            Triple("Games", "Something to play") { c: List<String> ->
                c.any { it.contains("game") || it.contains("emulator") }
            },
        )

        /** Hard ceiling per source per search, so one hung endpoint can't stall it. */
        const val SEARCH_TIMEOUT_MS = 12_000L

        /**
         * Sources with no counterpart in Classic's `AppSource` enum.
         *
         * Classic's engine covers the original seven; these are Expressive's own, and
         * so are the ones its search tab would otherwise never show. Keep in step with
         * [SourceId] — a new source that Classic does not know about belongs here or
         * it will be unsearchable.
         */
        val EXPRESSIVE_ONLY = listOf(
            SourceId.Aptoide,
            SourceId.Aurora,
            SourceId.MagiskAlt,
            SourceId.Googlers,
            SourceId.XposedRepo,
            SourceId.MagiskLegacy,
        )

        val ANDROID_HINTS = listOf(
            "android", "apk", "kotlin", "jetpack", "compose", "material you",
            "fdroid", "f-droid", "mobile app", "phone",
        )
    }

    /** True once GitHub has rejected the configured token. */
    private val _githubTokenRejected = MutableStateFlow(false)
    val githubTokenRejected: StateFlow<Boolean> = _githubTokenRejected.asStateFlow()

    @Volatile
    private var githubToken: String = ""

    val fdroid = FdroidRepoSource(
        id = SourceId.FDroid,
        repoUrl = "https://f-droid.org/repo",
        cacheDir = cacheDir,
    )

    val izzy = FdroidRepoSource(
        id = SourceId.IzzyOnDroid,
        repoUrl = "https://apt.izzysoft.de/fdroid/repo",
        cacheDir = cacheDir,
    )

    private val flathub = FlathubSource()

    /** Tier 1 in front of every live source. See [CdnSource]. */
    private val cdn = CdnSource(context)

    private val github = GitHubSource(
        tokenProvider = { githubToken },
        onTokenRejected = { _githubTokenRejected.value = true },
    )

    private val sources: Map<SourceId, AppSource> = mapOf(
        SourceId.FDroid to fdroid,
        SourceId.IzzyOnDroid to izzy,
        SourceId.GitHub to github,
        SourceId.GitLab to GitLabSource(),
        SourceId.Codeberg to CodebergSource(),
        SourceId.Flathub to flathub,
        SourceId.WinGet to WingetSource(),
        SourceId.Aurora to AuroraSource(),
        SourceId.Aptoide to AptoideSource(),
        // Root modules, from the two indexes that publish real metadata. Same
        // sources Modex reads, so the two apps describe a module identically.
        SourceId.MagiskAlt to ModuleRepoSource(
            id = SourceId.MagiskAlt,
            indexUrl = "https://raw.githubusercontent.com/Magisk-Modules-Alt-Repo/json/main/modules.json",
            format = ModuleRepoFormat.ALT_REPO,
        ),
        SourceId.Googlers to ModuleRepoSource(
            id = SourceId.Googlers,
            indexUrl = "https://raw.githubusercontent.com/Googlers-Repo/gmr/master/json/modules.json",
            format = ModuleRepoFormat.MMRL,
        ),
        // The orgs. These carry the thousand-plus modules the curated indexes
        // don't, and share the GitHub token so a user who set one gets the
        // higher rate limit here too.
        SourceId.XposedRepo to ModuleRepoSource(
            id = SourceId.XposedRepo,
            indexUrl = "",
            format = ModuleRepoFormat.GITHUB_ORG,
            org = "Xposed-Modules-Repo",
            familyHint = "LSPosed",
            tokenProvider = { githubToken },
        ),
        SourceId.MagiskLegacy to ModuleRepoSource(
            id = SourceId.MagiskLegacy,
            indexUrl = "",
            format = ModuleRepoFormat.GITHUB_ORG,
            org = "Magisk-Modules-Repo",
            familyHint = "Magisk",
            tokenProvider = { githubToken },
        ),
    )

    private val _states = MutableStateFlow<Map<SourceId, SourceState>>(
        SourceId.entries.associateWith { SourceState.Idle }
    )
    val states: StateFlow<Map<SourceId, SourceState>> = _states.asStateFlow()

    init {
        // Surface index sync progress on the Sources screen.
        fdroid.onProgress = { p, note -> setState(SourceId.FDroid, SourceState.Loading(p, note)) }
        izzy.onProgress = { p, note -> setState(SourceId.IzzyOnDroid, SourceState.Loading(p, note)) }
    }

    fun updateToken(token: String) {
        if (token == githubToken) return
        githubToken = token
        // A newly entered token deserves a fresh attempt even if the last one failed.
        _githubTokenRejected.value = false
        github.onTokenChanged()
        // The quota meter's readings belong to the token that earned them; a different
        // token has a different ceiling entirely.
        com.vythera.vyxelapps.api.GitHubRateLimit.reset()
    }

    fun source(id: SourceId): AppSource? = sources[id]

    /**
     * Records one source's state.
     *
     * `update` rather than an assignment: every enabled source reports from its own
     * coroutine, and a read-modify-write on `value` lets two of them interleave —
     * both copy the map, both write, and the slower write silently drops the faster
     * one's entry. That is why the search-only and heavy-crawl sources, which set
     * their state once and synchronously before the live sources are launched, sat
     * on "Waiting to sync" forever: their entry was overwritten by a copy taken
     * before it landed. `update` retries on conflict, so no report is lost.
     */
    private fun setState(id: SourceId, state: SourceState) {
        _states.update { current -> current + (id to state) }
    }

    /** Order rails appear in once they've loaded, regardless of who finishes first. */
    private val railOrder = listOf(
        SourceId.FDroid, SourceId.IzzyOnDroid, SourceId.GitHub,
        SourceId.Aptoide, SourceId.Codeberg, SourceId.GitLab, SourceId.Aurora,
        SourceId.Flathub, SourceId.WinGet,
    )

    fun railRank(id: SourceId): Int =
        railOrder.indexOf(id).takeIf { it >= 0 } ?: railOrder.size

    /**
     * Emits each source's rail the moment that source finishes.
     *
     * Awaiting all of them before showing anything meant the F-Droid index — a
     * ~12 MB download plus a full parse — held up the five sources that had already
     * returned in under a second. Now the screen fills in progressively.
     */
    fun loadHomeStreaming(enabled: Set<SourceId>): Flow<AppRail> = channelFlow {
        // Which sources the CDN already covered.
        //
        // Codeberg and GitLab hand out sporadic 502/503/504s under load, and when the
        // live call finally gave up the source was marked Failed — painting a red
        // "Server returned 503 · Try Again" chip directly above a rail the CDN had
        // already populated. The rail worked; only the banner said otherwise. A live
        // failure is only worth reporting when it actually left the user with nothing.
        val cdnCovered = java.util.Collections.synchronizedSet(mutableSetOf<SourceId>())

        // Tier 1: one request to the CDN fills every rail at once. Rails are keyed by
        // source downstream, so when a live source answers it replaces its own CDN
        // rail in place rather than appending a duplicate.
        launch {
            val started = System.currentTimeMillis()
            val grouped: Map<SourceId, List<AppItem>> =
                runCatching { cdn.byOriginalSource() }.getOrElse { error ->
                    android.util.Log.w(TAG, "CDN failed: ${error.message}")
                    emptyMap()
                }
            if (grouped.isNotEmpty()) {
                android.util.Log.i(
                    TAG,
                    "CDN: ${grouped.values.sumOf { it.size }} items across " +
                        "${grouped.size} sources in ${System.currentTimeMillis() - started}ms"
                )
            }
            // Keep the parsed index for the search path — it is already in memory
            // here, and re-reading it from disk per keystroke is what made search
            // feel slow.
            if (grouped.isNotEmpty()) warmIndex = grouped.values.flatten()

            grouped.forEach { (id, items) ->
                if (id in enabled && items.isNotEmpty()) {
                    cdnCovered += id
                    send(railFor(id, items.sortedByDescending { it.stars }.take(120)))
                }
            }
        }

        // Tier 2: the live sources, which carry what the index build can't.
        enabled.forEach { id ->
            // Neither search-only nor heavy-crawl sources are asked for a rail.
            // Calling them here would spend requests every launch on a list that is
            // then discarded — and for the module orgs that is a dozen of them.
            if (id.scanOnly) {
                setState(id, SourceState.ScanOnly)
                return@forEach
            }
            if (id.searchOnly || id.heavyCrawl) {
                setState(id, SourceState.SearchOnly)
                return@forEach
            }
            launch {
                setState(id, SourceState.Loading())
                val started = System.currentTimeMillis()
                runCatching { sources[id]?.featured().orEmpty() }
                    .onSuccess { items ->
                        android.util.Log.i(
                            TAG,
                            "${id.name}: ${items.size} items in ${System.currentTimeMillis() - started}ms"
                        )
                        setState(id, SourceState.Ready(items.size, System.currentTimeMillis()))
                        if (items.isNotEmpty()) send(railFor(id, items))
                    }
                    .onFailure {
                        android.util.Log.w(TAG, "${id.name} failed: ${it.javaClass.simpleName}: ${it.message}")
                        // Report the failure only if it left the rail empty. When the
                        // CDN already covered this source the user has working content
                        // and does not need to be told an upstream we also asked was
                        // briefly unavailable.
                        if (id in cdnCovered) {
                            setState(id, SourceState.Ready(0, System.currentTimeMillis()))
                        } else {
                            setState(id, SourceState.Failed(it.friendlyMessage()))
                        }
                    }
            }
        }
    }
        // Same trap as the search path: `channelFlow` children inherit the
        // collector's context, so mapping and grouping the CDN's 5000 entries ran on
        // the main thread and froze the first frames of every launch.
        .flowOn(Dispatchers.Default)

    private fun railFor(id: SourceId, items: List<AppItem>): AppRail = when (id) {
        SourceId.FDroid ->
            AppRail("Fresh on F-Droid", "Just updated in the main repo", id, items)
        SourceId.IzzyOnDroid ->
            AppRail("IzzyOnDroid picks", "Built straight from upstream", id, items)
        SourceId.GitHub ->
            AppRail("Starred on GitHub", "Open-source apps by star count", id, items)
        SourceId.Codeberg ->
            AppRail("Made on Codeberg", "Community-run, no tracking", id, items)
        SourceId.GitLab ->
            AppRail("Over on GitLab", "Projects shipping release builds", id, items)
        SourceId.Flathub ->
            AppRail("Popular on Flathub", "For your Linux desktop", id, items)
        SourceId.WinGet ->
            AppRail("WinGet catalog", "For your Windows machine", id, items)
        SourceId.Aurora ->
            AppRail("From Aurora OSS", "The Play client and its siblings", id, items)
        SourceId.Aptoide ->
            AppRail("Popular on Aptoide", "Mainstream apps, TRUSTED only", id, items)
        // Never reaches here — scan-only, so it produces no rail — but the
        // compiler is right to insist every source has an answer.
        SourceId.ApkPure ->
            AppRail("APKPure", "Update checks only", id, items)
        // Both module repos also feed one merged "Root modules" section higher up
        // the page; these rails are the by-provenance view in the long tail.
        SourceId.MagiskAlt ->
            AppRail("Magisk Alt Repo", "Reviewed community modules", id, items)
        SourceId.Googlers ->
            AppRail("Googlers Repo", "Modules with full version history", id, items)
        SourceId.XposedRepo ->
            AppRail("Xposed Modules Repository", "Over a thousand LSPosed modules", id, items)
        SourceId.MagiskLegacy ->
            AppRail("Magisk Modules Repo", "The original, archived but installable", id, items)
    }

    /** Items for the hero carousel — the most presentable entries across all rails. */
    /**
     * Apps for the home hero carousel.
     *
     * Ranking alone made this a fixed list: the highest-starred entries in the
     * catalog barely move, so every launch showed the same handful of apps forever
     * and the carousel stopped being discovery. Classic solved this years ago by
     * shuffling a seeded pool, so the same approach applies here — rank down to a
     * pool of genuinely good candidates, then pick from it at random.
     *
     * [seed] is fixed for a given load so returning from an app detail doesn't
     * reshuffle the card the user was aiming for; only an actual refresh re-rolls.
     */
    fun heroPicks(rails: List<AppRail>, count: Int = 8, seed: Int = 0): List<AppItem> {
        val pool = rails.asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.iconUrl != null && it.summary.length > 20 }
            .distinctBy { it.dedupeKey }
            .sortedByDescending { it.stars.toLong() + it.installs / 1000L }
            // Wide enough that the picks visibly change between refreshes, tight
            // enough that everything in it still deserves the front page.
            .take(count * 8)
            .toList()
        return pool.shuffled(kotlin.random.Random(seed.toLong())).take(count)
    }

    /**
     * A rail of apps that are genuinely new, not merely freshly updated.
     *
     * Every source rail is ordered by *last updated*, which on a repo index is
     * dominated by routine version bumps — so the same well-established apps
     * surface again and again and nothing ever looks new. `added` is the first
     * publication date, which is a different question and a much better one for
     * discovery.
     *
     * Only the F-Droid-protocol sources report it; everything else leaves it at 0
     * and is skipped rather than being treated as ancient.
     */
    fun newlyLaunched(
        rails: List<AppRail>,
        count: Int = 24,
        withinDays: Long = 120,
        now: Long = System.currentTimeMillis(),
    ): AppRail? {
        val cutoff = now - withinDays * 24 * 60 * 60 * 1000L
        val items = rails.asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.addedAt in (cutoff + 1)..now }
            .filter { it.iconUrl != null && it.summary.isNotBlank() }
            .distinctBy { it.dedupeKey }
            .sortedByDescending { it.addedAt }
            .take(count)
            .toList()
        // A rail of two cards reads as a glitch rather than a section.
        if (items.size < MIN_RAIL_ITEMS) return null
        return AppRail(
            title = "Newly launched",
            subtitle = "First published in the last ${withinDays / 30} months",
            source = null,
            items = items,
        )
    }

    /**
     * Rails grouped by what an app is *for*, not where its file came from.
     *
     * "Fresh on F-Droid" / "Made on Codeberg" / "WinGet catalog" organise the page
     * by plumbing — which repository happened to serve the bytes. Nobody arrives
     * wanting a Codeberg app; they arrive wanting a podcast player. Source stays
     * useful as a filter and as the badge on each card, but it is a poor spine for
     * a storefront.
     *
     * Categories come from the sources' own metadata (F-Droid categories, GitHub
     * topics), so this needs no curation to stay current.
     */
    fun categoryRails(rails: List<AppRail>, minItems: Int = MIN_RAIL_ITEMS): List<AppRail> {
        val pool = rails.asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.platform == Platform.Android }
            .distinctBy { it.dedupeKey }
            .toList()

        return CATEGORY_RAILS.mapNotNull { (title, subtitle, match) ->
            val items = pool.asSequence()
                .filter { item -> match(item.categories.map { it.lowercase() }) }
                .sortedByDescending { it.stars.toLong() + it.installs / 1000L }
                .take(24)
                .toList()
            if (items.size < minItems) null
            else AppRail(title = title, subtitle = subtitle, source = null, items = items)
        }
    }

    /**
     * Apps that drive Shizuku, gathered into one rail.
     *
     * These are genuinely hard to find: they are scattered across every source, none
     * of the upstream indexes has a field for "needs Shizuku", and searching for the
     * word turns up Shizuku itself plus a pile of unrelated results. The detection is
     * text-based ([AppItem.usesShizuku]) rather than a curated list, which means it
     * stays current on its own — a new Shizuku app is in the rail the day its
     * description lands in an index, with no app update and nothing to maintain.
     *
     * A curated list — awesome-shizuku and the like — would raise precision further,
     * but that belongs in the CDN index as an extra field rather than baked into the
     * APK, so that corrections ship without a release.
     */
    fun shizukuRail(rails: List<AppRail>, minItems: Int = MIN_RAIL_ITEMS): AppRail? {
        val items = rails.asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.platform == Platform.Android }
            .filter { it.usesShizuku }
            .distinctBy { it.dedupeKey }
            .sortedByDescending { it.stars.toLong() + it.installs / 1000L }
            .take(24)
            .toList()
        if (items.size < minItems) return null
        return AppRail(
            title = "Works with Shizuku",
            subtitle = "Extra powers, no root",
            source = null,
            items = items,
        )
    }

    /**
     * Drops repeats so the same app is not shown over and over down the page.
     *
     * Rails come from independent sources that overlap heavily — a popular app is
     * typically on F-Droid *and* IzzyOnDroid *and* GitHub — and nothing previously
     * reconciled them, so scrolling the home screen meant meeting the same handful
     * of apps in rail after rail. Each app now appears once, in the highest-ranked
     * rail that carries it, which also gives the lower rails a reason to exist.
     *
     * [alreadyShown] lets the caller suppress what the hero carousel is already
     * displaying above the fold.
     */
    fun dedupeRails(rails: List<AppRail>, alreadyShown: Set<String> = emptySet()): List<AppRail> {
        val seen = HashSet(alreadyShown)
        return rails.mapNotNull { rail ->
            val kept = rail.items.filter { seen.add(it.dedupeKey) }
            // Deduping can gut a rail whose whole catalogue is covered upstream.
            // Showing a near-empty section is worse than omitting it.
            if (kept.size < MIN_RAIL_ITEMS) null else rail.copy(items = kept)
        }
    }

    /**
     * Cross-source search that publishes a re-ranked list every time a source
     * answers.
     *
     * Awaiting all sources meant every search ran at the speed of the slowest one —
     * Codeberg's repo search alone regularly takes 30-70s, so results the user could
     * have had in under a second sat behind it. Each source also gets a hard timeout
     * so a hung endpoint can never pin the query open.
     */
    fun searchStreaming(query: String, enabled: Set<SourceId>): Flow<List<AppItem>> = channelFlow {
        val gathered = mutableListOf<AppItem>()
        val lock = Mutex()
        docCache.clear()

        // Answer from memory before touching disk or network.
        //
        // The home screen has already parsed the whole CDN index, so the answer to
        // most queries is sitting in RAM — waiting on file I/O and then on HTTP to
        // show it is what made typing feel laggy. This emits on the first frame
        // after a keystroke; everything below refines it.
        val warm = warmIndex
        if (warm.isNotEmpty()) {
            val hits = warm.asSequence()
                .filter { it.source in enabled }
                .filter { it.matchesLoosely(query) }
                .take(60)
                .toList()
            if (hits.isNotEmpty()) {
                lock.withLock {
                    gathered += hits
                    send(rank(gathered, query))
                }
            }
        }

        // The CDN index is already on disk, so it answers in milliseconds and gives
        // the user something to look at while the live APIs are still connecting.
        launch {
            val hits = runCatching { cdn.search(query) }.getOrDefault(emptyList())
                .filter { it.source in enabled }
            if (hits.isNotEmpty()) {
                lock.withLock {
                    gathered += hits
                    send(rank(gathered, query))
                }
            }
        }

        enabled.forEach { id ->
            // The module organisations sit out the main search box: enumerating a
            // thousand repos to answer one query would spend the whole GitHub budget
            // and bury the apps under LSPosed modules. They are reachable in full
            // from the Modules screen, which has its own search over the same data.
            if (id.heavyCrawl) return@forEach
            launch {
                val items = runCatching {
                    withTimeout(SEARCH_TIMEOUT_MS) { sources[id]?.search(query).orEmpty() }
                }.getOrElse { error ->
                    if (error !is CancellationException) {
                        android.util.Log.w(TAG, "${id.name} search failed: ${error.message}")
                    }
                    emptyList()
                }
                if (items.isEmpty()) return@launch
                lock.withLock {
                    gathered += items
                    send(rank(gathered, query))
                }
            }
        }
    }
        // Dedupe + scoring + sorting of several hundred items used to run on
        // whatever thread collected the flow — the main thread — and re-ran on every
        // source arrival. That, not the network, is what made typing feel glitchy.
        .flowOn(Dispatchers.Default)
        // The UI cannot render faster than it recomposes; when four sources land in
        // the same frame only the newest list matters.
        .conflate()

    /**
     * Every CDN entry the home load already parsed, kept for instant search.
     *
     * ~5000 items, populated once per home load and read on the search path. It
     * costs a few MB of heap and buys a search that answers before the keystroke
     * has finished animating.
     */
    @Volatile
    private var warmIndex: List<AppItem> = emptyList()

    /**
     * Cheap prefilter — any token is enough to be a candidate.
     *
     * Deliberately generous: [rank] applies the real coverage and spelling rules,
     * so anything this lets through that should not survive is dropped there. A
     * strict prefilter here would hide the very partial and misspelled queries the
     * scorer was rewritten to handle.
     */
    private fun AppItem.matchesLoosely(query: String): Boolean {
        val hay = "${name.lowercase()} ${packageName.orEmpty().lowercase()} " +
            summary.lowercase() + " " + categories.joinToString(" ").lowercase()
        return query.trim().lowercase()
            .split(' ')
            .filter { it.isNotBlank() }
            .any { hay.contains(it) }
    }

    /**
     * Normalised text per item, reused across every re-rank within a query.
     * Cleared when the query changes.
     */
    private val docCache = HashMap<String, SearchDoc>()

    private fun docFor(item: AppItem): SearchDoc = docCache.getOrPut(item.id) {
        SearchDoc.of(
            name = item.name,
            packageName = item.packageName,
            summary = item.summary,
            description = item.description,
            categories = item.categories,
            // Model-generated synonyms from the index build — the vocabulary an
            // app's own description never uses. See server/enrich.js.
            searchTerms = searchTerms[item.packageName ?: item.name].orEmpty(),
        )
    }

    /**
     * packageName -> search synonyms, generated once at index time.
     *
     * Fetched and cached on disk; absent simply means search behaves exactly as
     * it did before, so a failed fetch or a fresh install degrades to the plain
     * lexical scorer rather than to nothing.
     */
    @Volatile
    private var searchTerms: Map<String, List<String>> = emptyMap()

    private val termsFile = File(cacheDir, "search-terms.json")

    /**
     * Loads synonyms from disk, then refreshes them in the background.
     *
     * Deliberately not blocking: search must work on the first keystroke of a
     * cold start, with or without this.
     */
    suspend fun primeSearchTerms(baseUrl: String) {
        if (searchTerms.isEmpty() && termsFile.exists()) {
            runCatching { searchTerms = parseTerms(termsFile.readText()) }
        }
        if (baseUrl.isEmpty()) return
        runCatching {
            val fresh = com.vythera.vyxelapps.expressive.core.net.Net
                .getString("${baseUrl.trimEnd('/')}/v1/search/terms")
            val parsed = parseTerms(fresh)
            if (parsed.isNotEmpty()) {
                searchTerms = parsed
                termsFile.writeText(fresh)
                android.util.Log.i(TAG, "search terms: ${parsed.size} apps enriched")
            }
        }.onFailure {
            android.util.Log.w(TAG, "search terms unavailable: ${it.message}")
        }
    }

    private fun parseTerms(raw: String): Map<String, List<String>> = buildMap {
        val root = org.json.JSONObject(raw)
        root.keys().forEach { key ->
            val arr = root.optJSONArray(key) ?: return@forEach
            val terms = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            if (terms.isNotEmpty()) put(key, terms)
        }
    }

    /** Deduplicates across sources, scores against the query, and orders the result. */
    fun rank(items: List<AppItem>, query: String): List<AppItem> {
        val seenPackages = HashSet<String>()
        val seenNames = HashSet<String>()
        val unique = ArrayList<AppItem>(items.size)

        // Walking in source-trust order means the copy that survives deduplication is
        // the curated one (F-Droid) rather than whichever git mirror answered first.
        items.sortedBy { sourceRank(it.source) }.forEach { item ->
            val packageKey = item.packageName?.lowercase()
            val nameKey = normalizeText(item.name)

            // A package id is an identity; a title is a coincidence.
            //
            // Same package, any source: the same app, so collapse it. Different
            // packages that merely share a title are *different apps* and must both
            // survive — the store is full of near-collisions. Searching "instagram"
            // returned neither Instagram nor the GitHub tool called "Instagram-",
            // because the repo sorts first on source trust, claimed the shared
            // normalised name, and silently ate the real app.
            //
            // Name matching still earns its keep for an entry with no package at all
            // — a bare GitHub repo alongside the F-Droid build of the same project —
            // which is exactly the case it was written for.
            val duplicate = when {
                packageKey != null -> !seenPackages.add(packageKey)
                else -> nameKey in seenNames
            }
            if (duplicate) return@forEach

            seenNames += nameKey
            unique += item
        }

        return unique.asSequence()
            .map { it to totalScore(it, query) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<AppItem, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .map { it.first }
            .take(200)
            .toList()
    }

    /**
     * Text relevance scaled by how likely the entry is to be a real, usable app.
     *
     * Additive popularity doesn't work here. Searching "podcast" surfaced a 2-star
     * GitLab repo literally named `podcast` above AntennaPod, because an exact name
     * match scores ~1000 and no realistic additive bonus closes that gap. Quality is
     * therefore a multiplier: a near-abandoned repo keeps a quarter of its text
     * score, while a curated store entry keeps nearly double.
     */
    private fun totalScore(item: AppItem, query: String): Int {
        val base = relevanceScore(docFor(item), query)
        if (base == 0) return 0
        return (base * qualityMultiplier(item)).toInt()
    }

    /**
     * How much to trust an entry, independent of the query.
     *
     * Repo hosts index every hobby project ever pushed, so star count is the only
     * available proxy for "someone other than the author uses this". Curated stores
     * have already done that filtering, so they skip the penalty entirely.
     */
    private fun qualityMultiplier(item: AppItem): Float = when (item.source) {
        // Reviewed, packaged, installable, real metadata.
        SourceId.FDroid -> 1.9f
        SourceId.IzzyOnDroid -> 1.75f

        SourceId.Flathub -> when {
            item.installs >= 100_000 -> 1.7f
            item.installs >= 10_000 -> 1.4f
            item.verified -> 1.3f
            else -> 1.1f
        }

        // Curated manifests but no popularity signal at all.
        SourceId.WinGet -> 1.0f

        // Three hand-maintained apps. If one of them matches the query it is
        // almost certainly the thing being searched for.
        SourceId.Aurora -> 1.9f

        // A general store: broad, but user-uploaded and only TRUSTED entries get
        // this far. Ranked below the reviewed FOSS repos and above the raw repo
        // hosts, which is where it belongs on how vetted an entry is.
        SourceId.Aptoide -> 1.6f

        // Scan-only: never appears in a ranked search result list.
        SourceId.ApkPure -> 1.0f

        // Reviewed module repositories. Ranked below app sources on purpose: a
        // plain query like "adblock" should answer with apps first and offer the
        // module as an alternative, not lead with something that needs root.
        SourceId.MagiskAlt, SourceId.Googlers,
        SourceId.XposedRepo, SourceId.MagiskLegacy -> when {
            item.stars >= 500 -> 1.3f
            item.stars >= 50 -> 1.1f
            else -> 0.9f
        }

        SourceId.GitHub, SourceId.GitLab, SourceId.Codeberg -> {
            val popularity = when {
                item.stars >= 5_000 -> 1.7f
                item.stars >= 1_000 -> 1.35f
                item.stars >= 200 -> 1.05f
                item.stars >= 50 -> 0.7f
                item.stars >= 10 -> 0.45f
                else -> 0.25f
            }
            // This is an app store. A popular repo with no sign it produces an
            // Android app (a Python library, a docs site) is still the wrong answer,
            // so it's discounted rather than allowed to win on stars alone.
            popularity * if (looksAndroidish(item)) 1f else 0.45f
        }
    }

    /** Cheap check for an Android signal, without paying for a release lookup. */
    private fun looksAndroidish(item: AppItem): Boolean {
        val haystack = (
            item.categories.joinToString(" ") + " " +
                item.summary + " " + item.name
            ).lowercase()
        return ANDROID_HINTS.any { haystack.contains(it) }
    }

    private fun sourceRank(id: SourceId): Int = when (id) {
        SourceId.FDroid -> 0
        SourceId.IzzyOnDroid -> 1
        SourceId.GitHub -> 2
        SourceId.Codeberg -> 3
        SourceId.GitLab -> 4
        // Curated and small: when one of these matches a query it is almost always
        // the thing being looked for, so it outranks the desktop catalogues.
        SourceId.Aptoide -> 5
        SourceId.ApkPure -> 5
        SourceId.Aurora -> 6
        SourceId.Googlers -> 7
        SourceId.MagiskAlt -> 8
        SourceId.XposedRepo -> 11
        SourceId.MagiskLegacy -> 12
        SourceId.Flathub -> 9
        SourceId.WinGet -> 10
    }

    /**
     * Fills in an entry for the detail screen, CDN first then the live source.
     *
     * The CDN pass is cheap (usually a cached file) and often supplies everything
     * needed; the live lookup still runs because only it can resolve a repo's newest
     * release into a concrete APK URL.
     */
    suspend fun resolve(item: AppItem): AppItem {
        val enriched = runCatching { cdn.detail(item) }.getOrNull() ?: item
        if (!enriched.needsReleaseLookup && enriched.downloadUrl != null) return enriched
        return runCatching { sources[item.source]?.resolve(enriched) }.getOrNull() ?: enriched
    }

    /** Forces a re-sync of the F-Droid-protocol indexes. */
    suspend fun refreshIndexes() = supervisorScope {
        listOf(
            async { runCatching { fdroid.catalog(forceRefresh = true) } },
            async { runCatching { izzy.catalog(forceRefresh = true) } },
        ).forEach { it.await() }
    }

    /**
     * Searches only the sources Classic's engine has never heard of.
     *
     * The Expressive search tab renders *Classic's* results — one engine, so both
     * shells answer a query identically. That was fine while every source existed on
     * both sides, and stopped being fine the moment Vyxel grew sources Classic has no
     * enum value for: Aptoide, Aurora and the module repos were searchable in theory
     * and invisible in practice, which is why "instagram" returned GitHub repos about
     * Instagram and not Instagram.
     *
     * Rather than teach Classic about them or run the whole Expressive engine in
     * parallel and search F-Droid twice, this covers exactly the gap. Results are
     * merged into Classic's by the shell.
     */
    suspend fun searchExtras(query: String, enabled: Set<SourceId>): List<AppItem> =
        supervisorScope {
            EXPRESSIVE_ONLY
                .filter { it in enabled && !it.heavyCrawl }
                .map { id ->
                    async {
                        runCatching {
                            withTimeout(SEARCH_TIMEOUT_MS) {
                                sources[id]?.search(query).orEmpty()
                            }
                        }.getOrElse { error ->
                            if (error !is CancellationException) {
                                android.util.Log.w(TAG, "${id.name} search failed: ${error.message}")
                            }
                            emptyList()
                        }
                    }
                }
                .flatMap { it.await() }
        }

    /**
     * Every root module both repositories carry, merged.
     *
     * Backs the dedicated Modules screen, which browses modules on their own terms
     * rather than as stray rows among apps. Deduplicated on module id because the
     * same module is often in both indexes — keeping the copy with the higher star
     * count, since that is the one whose repo page people actually landed on.
     */
    suspend fun modules(): List<AppItem> = supervisorScope {
        // The CDN's pre-built catalogue first: one conditional GET, usually a 304.
        //
        // Building this on the device means two index downloads, a fan-out over a
        // hundred-odd individual module.prop files and up to twelve paged GitHub org
        // listings — around fifteen seconds cold, and a real bite out of the hourly
        // GitHub budget. The build server does it once a day instead.
        val cached = runCatching { cdn.modules() }.getOrDefault(emptyList())
        if (cached.size >= MIN_CDN_MODULES) {
            android.util.Log.i(TAG, "modules: ${cached.size} from CDN")
            return@supervisorScope cached
        }

        // No modules.json published yet, or it came back suspiciously thin. Scrape
        // the repositories live — slower, but the screen still works.
        android.util.Log.i(TAG, "modules: CDN empty, scraping live")
        listOf(
            SourceId.MagiskAlt, SourceId.Googlers,
            SourceId.XposedRepo, SourceId.MagiskLegacy,
        )
            .map { id -> async { runCatching { sources[id]?.featured() }.getOrNull().orEmpty() } }
            .flatMap { it.await() }
            .groupBy { it.packageName?.lowercase() ?: it.id }
            .map { (_, dupes) -> dupes.maxBy { it.stars } }
            .sortedByDescending { it.stars }
    }

    /** Every Android-installable entry, for update scanning. */
    suspend fun androidCatalog(): List<AppItem> = supervisorScope {
        val a = async { runCatching { fdroid.catalog() }.getOrDefault(emptyList()) }
        val b = async { runCatching { izzy.catalog() }.getOrDefault(emptyList()) }
        (a.await() + b.await()).distinctBy { it.dedupeKey }
    }
}

/**
 * What to tell the reader, in their terms.
 *
 * "Server returned 503" and a raw exception message describe our problem, not
 * theirs, and neither suggests what to do next. Each case here names something
 * the reader can either act on or safely ignore.
 */
private fun Throwable.friendlyMessage(): String = when {
    this is com.vythera.vyxelapps.expressive.core.net.HttpException && code == 403 ->
        "GitHub is rate limiting us — add a token in Settings to lift the limit"
    this is com.vythera.vyxelapps.expressive.core.net.HttpException && code >= 500 ->
        "This source is having trouble — everything else still works"
    this is com.vythera.vyxelapps.expressive.core.net.HttpException ->
        "This source turned us away — everything else still works"
    this is java.net.UnknownHostException ->
        "You appear to be offline — showing what was saved"
    this is java.net.SocketTimeoutException ->
        "This source is slow to answer right now"
    else -> "This source is unavailable — everything else still works"
}
