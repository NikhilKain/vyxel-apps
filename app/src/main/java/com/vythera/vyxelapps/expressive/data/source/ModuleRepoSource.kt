package com.vythera.vyxelapps.expressive.data.source

import com.vythera.vyxelapps.expressive.core.net.Net
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Which index dialect a module repository speaks. */
enum class ModuleRepoFormat {
    /**
     * Magisk Modules Alt Repo: a deliberately thin index.
     *
     * Carries an id, a zip and a star count, and nothing a human reads — name,
     * author and description all live in a linked `module.prop`. Usable metadata
     * therefore costs a second pass over a few hundred tiny text files.
     */
    ALT_REPO,

    /** MMRL: one document carrying full metadata and version history. */
    MMRL,

    /**
     * Every repository in a GitHub organisation, one repo per module.
     *
     * This is where the volume is. The curated indexes hold a couple of hundred
     * modules between them — genuinely all there is in *reviewed* repos — while
     * `Xposed-Modules-Repo` alone is over a thousand. Listing an org is also a far
     * better deal than searching for its contents: no 1000-result ceiling, and it
     * draws on GitHub's 60/hour core limit rather than search's 10/minute.
     */
    GITHUB_ORG,
}

/**
 * Root modules — Magisk, Zygisk, LSPosed and KernelSU — as Vyxel catalogue entries.
 *
 * These are the two curated indexes that publish real metadata, which is also what
 * Modex reads, so the two apps agree on what a given module is. The uncurated GitHub
 * long tail is deliberately *not* here: reaching it means a star-sharded crawl against
 * a 10-requests-per-minute search limit, resumed across many syncs. That is a
 * reasonable thing for an app built around modules to do, and an unreasonable thing to
 * put behind a store's search box, where it would spend the whole rate limit and still
 * answer late.
 *
 * Everything lands as [com.vythera.vyxelapps.expressive.data.model.Platform.Module],
 * so nothing here can be mistaken for an installable APK.
 */
class ModuleRepoSource(
    override val id: SourceId,
    private val indexUrl: String,
    private val format: ModuleRepoFormat,
    /** Organisation name, for [ModuleRepoFormat.GITHUB_ORG]. */
    private val org: String = "",
    /**
     * The family every module in this source belongs to, when the source only ever
     * carries one.
     *
     * An Xposed module organisation holds Xposed modules whatever an individual
     * repo's description happens to mention, and that beats any keyword guess.
     */
    private val familyHint: String = "",
    /** Lifts GitHub's unauthenticated rate limit when the user has set a token. */
    private val tokenProvider: () -> String = { "" },
) : AppSource {

    private companion object {
        /**
         * Concurrent `module.prop` fetches during the Alt Repo's second phase.
         *
         * Eight at a time against raw.githubusercontent, which is what Modex settled
         * on. Higher gets throttled; lower makes a cold first sync drag.
         */
        const val PROP_CONCURRENCY = 8

        /** Alt Repo is ~130 modules; the cap is a guard, not a limit in practice. */
        const val MAX_ENRICH = 400

        const val ORG_PAGE_SIZE = 100

        /**
         * Ceiling on org pages per load — 1200 modules.
         *
         * Xposed-Modules-Repo is a little over a thousand repos, so this covers it
         * with room to grow. The cap is there so an org that balloons cannot spend
         * the whole hourly GitHub budget in a single screen open.
         */
        const val MAX_ORG_PAGES = 12
    }

    /**
     * Parsed catalogue, held for the process lifetime.
     *
     * These indexes are small and change daily at most, and search hits this on every
     * keystroke. Re-parsing — and for Alt Repo re-walking a hundred `module.prop`
     * files — per query would be absurd.
     */
    @Volatile
    private var cached: List<AppItem>? = null

    override suspend fun featured(): List<AppItem> = load()

    override suspend fun search(query: String): List<AppItem> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        return load().filter { item ->
            item.name.lowercase().contains(needle) ||
                item.packageName.orEmpty().lowercase().contains(needle) ||
                item.summary.lowercase().contains(needle) ||
                item.author.orEmpty().lowercase().contains(needle)
        }
    }

    private suspend fun load(): List<AppItem> {
        cached?.let { return it }
        val fetched = when (format) {
            ModuleRepoFormat.MMRL -> loadMmrl()
            ModuleRepoFormat.ALT_REPO -> loadAltRepo()
            ModuleRepoFormat.GITHUB_ORG -> loadOrg()
        }.sortedByDescending { it.stars }
        cached = fetched
        return fetched
    }

    // ------------------------------------------------------------------ MMRL

    private suspend fun loadMmrl(): List<AppItem> = withContext(Dispatchers.IO) {
        val body = Net.getString(indexUrl, retries = 2)
        val root = Net.json.parseToJsonElement(body)
        // Some repos publish a bare array; others wrap it in an object that also
        // names the repo. Both spellings are in the wild.
        val entries = runCatching { root.jsonObject["modules"]?.jsonArray }.getOrNull()
            ?: runCatching { root.jsonArray }.getOrNull()
            ?: return@withContext emptyList()

        entries.mapNotNull { element ->
            val entry = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val moduleId = entry.str("id") ?: return@mapNotNull null

            // Newest published build, which is where the zip usually lives.
            val versions = runCatching { entry["versions"]?.jsonArray }.getOrNull().orEmpty()
                .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                .sortedByDescending { it.num("versionCode") ?: 0L }
            val latest = versions.firstOrNull()

            val name = entry.str("name").orEmpty()
            val description = entry.str("description").orEmpty()

            moduleItem(
                moduleId = moduleId,
                name = name,
                version = entry.str("version") ?: latest.str("version").orEmpty(),
                author = entry.str("author").orEmpty(),
                description = description,
                zipUrl = latest.str("zipUrl") ?: entry.str("zipUrl"),
                stars = (entry.num("stars") ?: 0L).toInt(),
                sizeBytes = entry.num("size") ?: latest.num("size") ?: 0L,
                updatedAt = entry.epochMillis("timestamp"),
                homepage = runCatching { entry["track"]?.jsonObject }.getOrNull().str("source")
                    ?: entry.str("homepage"),
            )
        }
    }

    // -------------------------------------------------------------- Alt Repo

    private suspend fun loadAltRepo(): List<AppItem> = withContext(Dispatchers.IO) {
        val body = Net.getString(indexUrl, retries = 2)
        val root = Net.json.parseToJsonElement(body)
        val entries = runCatching { root.jsonObject["modules"]?.jsonArray }.getOrNull()
            ?: return@withContext emptyList()

        val stubs = entries.mapNotNull { element ->
            val entry = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val moduleId = entry.str("id") ?: return@mapNotNull null
            AltStub(
                moduleId = moduleId,
                propUrl = entry.str("prop_url"),
                zipUrl = entry.str("zip_url"),
                stars = (entry.num("stars") ?: 0L).toInt(),
                updatedAt = entry.num("last_update") ?: 0L,
            )
        }.take(MAX_ENRICH)

        // Second phase. Failures are per-module on purpose: a module whose prop is
        // missing still belongs in the catalogue under a name derived from its id,
        // which is far better than dropping it.
        val gate = Semaphore(PROP_CONCURRENCY)
        supervisorScope {
            stubs.map { stub ->
                async {
                    val props = stub.propUrl?.let { url ->
                        gate.withPermit {
                            runCatching { parseProps(Net.getString(url, retries = 1)) }.getOrNull()
                        }
                    }.orEmpty()

                    moduleItem(
                        moduleId = stub.moduleId,
                        name = props["name"].orEmpty(),
                        version = props["version"].orEmpty(),
                        author = props["author"].orEmpty(),
                        description = props["description"].orEmpty(),
                        zipUrl = stub.zipUrl,
                        stars = stub.stars,
                        sizeBytes = 0L,
                        updatedAt = stub.updatedAt,
                        homepage = "https://github.com/Magisk-Modules-Alt-Repo/${stub.moduleId}",
                    )
                }
            }.awaitAll()
        }
    }

    // ------------------------------------------------------------ GitHub org

    /**
     * Enumerates every repository in the organisation.
     *
     * Paged to [MAX_ORG_PAGES] × 100. `Xposed-Modules-Repo` is a little over a
     * thousand repos, so eleven requests covers it; the cap exists so a runaway org
     * cannot spend the whole hourly budget in one load.
     *
     * Sorted by last push, which means a partial enumeration is still the *useful*
     * part of the org — the modules people are actually still updating.
     */
    private suspend fun loadOrg(): List<AppItem> = withContext(Dispatchers.IO) {
        if (org.isBlank()) return@withContext emptyList()
        val headers = tokenProvider().takeIf { it.isNotBlank() }
            ?.let { mapOf("Authorization" to "Bearer $it") }
            .orEmpty() + mapOf("Accept" to "application/vnd.github+json")

        val out = mutableListOf<AppItem>()
        for (page in 1..MAX_ORG_PAGES) {
            val url = "https://api.github.com/orgs/$org/repos" +
                "?per_page=$ORG_PAGE_SIZE&page=$page&sort=pushed&direction=desc&type=public"
            val body = runCatching { Net.getString(url, headers, retries = 1) }.getOrNull()
                ?: break
            val repos = runCatching { Net.json.parseToJsonElement(body).jsonArray }.getOrNull()
                ?: break
            if (repos.isEmpty()) break

            repos.forEach { element ->
                val repo = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
                val name = repo.str("name") ?: return@forEach
                val description = repo.str("description").orEmpty()
                // A repo whose whole content is a README is not a module. The org
                // is curated enough that this is rare, but a placeholder repo in
                // the results is a dead row the user has to discover by tapping.
                if (repo.str("archived") == "true" && description.isBlank()) return@forEach

                out += moduleItem(
                    moduleId = name,
                    name = name,
                    version = "",
                    author = repo["owner"]?.jsonObject.str("login").orEmpty(),
                    description = description,
                    // Resolved lazily: one release lookup per repo during a load
                    // would be a thousand extra requests and instant rate limiting.
                    zipUrl = null,
                    stars = (repo.num("stargazers_count") ?: 0L).toInt(),
                    sizeBytes = 0L,
                    updatedAt = 0L,
                    homepage = repo.str("html_url"),
                )
            }
            if (repos.size < ORG_PAGE_SIZE) break
        }
        out
    }

    /**
     * Finds a module's zip on demand.
     *
     * Org-listed modules arrive with a homepage and no download, because resolving
     * every one during a load would be a request per repo. This is that request,
     * made once, on the module the user actually opened.
     */
    override suspend fun resolve(item: AppItem): AppItem {
        if (format != ModuleRepoFormat.GITHUB_ORG) return item
        if (item.downloadUrl != null) return item
        val repo = item.packageName?.takeIf { it.isNotBlank() } ?: return item

        return withContext(Dispatchers.IO) {
            runCatching {
                val headers = tokenProvider().takeIf { it.isNotBlank() }
                    ?.let { mapOf("Authorization" to "Bearer $it") }
                    .orEmpty() + mapOf("Accept" to "application/vnd.github+json")
                val body = Net.getString(
                    "https://api.github.com/repos/$org/$repo/releases/latest",
                    headers,
                    retries = 1,
                )
                val release = Net.json.parseToJsonElement(body).jsonObject
                val assets = release["assets"]?.jsonArray.orEmpty()
                    .mapNotNull { runCatching { it.jsonObject }.getOrNull() }

                // A module ships as a zip. An Xposed module ships as an APK, because
                // it *is* an app that LSPosed hooks — but it still installs through
                // the manager, so either is a valid artefact here.
                val asset = assets.firstOrNull { it.str("name")?.endsWith(".zip", true) == true }
                    ?: assets.firstOrNull { it.str("name")?.endsWith(".apk", true) == true }
                    ?: return@runCatching item

                item.copy(
                    downloadUrl = asset.str("browser_download_url") ?: item.downloadUrl,
                    version = release.str("tag_name")?.removePrefix("v") ?: item.version,
                    sizeBytes = asset.num("size") ?: item.sizeBytes,
                )
            }.getOrDefault(item)
        }
    }

    private data class AltStub(
        val moduleId: String,
        val propUrl: String?,
        val zipUrl: String?,
        val stars: Int,
        val updatedAt: Long,
    )

    /** `module.prop` is a flat `key=value` file; anything unparseable is skipped. */
    private fun parseProps(text: String): Map<String, String> =
        text.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith('#')) return@mapNotNull null
                val split = trimmed.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                trimmed.take(split).trim() to trimmed.substring(split + 1).trim()
            }
            .toMap()

    // ----------------------------------------------------------------- shared

    private fun moduleItem(
        moduleId: String,
        name: String,
        version: String,
        author: String,
        description: String,
        zipUrl: String?,
        stars: Int,
        sizeBytes: Long,
        updatedAt: Long,
        homepage: String?,
    ): AppItem {
        val title = readableModuleName(name, moduleId)
        return AppItem(
            id = "${id.name}:$moduleId",
            source = id,
            name = title,
            summary = description.take(240),
            description = description,
            // The module id doubles as the package name so dedupe, hiding and
            // "already have it" all key off the same string the repos use.
            packageName = moduleId,
            version = version.removePrefix("v").takeIf { it.isNotBlank() },
            author = author.removePrefix("@").takeIf { it.isNotBlank() },
            // Family first, so it reads as the leading chip on the card and feeds
            // search without needing a field of its own.
            categories = listOf(
                // A source that only carries one family wins over a keyword guess:
                // every repo under an Xposed org is an Xposed module, whatever its
                // description happens to mention.
                familyHint.ifBlank { moduleFamily(name, description, moduleId) }
            ),
            downloadUrl = zipUrl?.takeIf { it.isNotBlank() },
            stars = stars,
            sizeBytes = sizeBytes,
            updatedAt = updatedAt,
            sourceCodeUrl = homepage,
            website = homepage,
        )
    }
}

/**
 * Which root manager a module plugs into, read out of its own text.
 *
 * No index publishes this as a field, so it comes from the name and blurb. Order
 * matters: a module mentioning both Zygisk and Magisk is a Zygisk module that is
 * saying what it needs, not a Magisk one.
 */
internal fun moduleFamily(vararg text: String?): String {
    val haystack = text.filterNotNull().joinToString(" ").lowercase()
    return when {
        "lsposed" in haystack || "xposed" in haystack -> "LSPosed"
        "zygisk" in haystack -> "Zygisk"
        "kernelsu" in haystack || "kernel su" in haystack -> "KernelSU"
        else -> "Magisk"
    }
}

/**
 * A title for a module, recovering one from the id when none was published.
 *
 * A large minority of entries carry no usable name — some ship none, and more ship
 * the id again because the upstream index puts it in the field the parser reads. The
 * result is rows reading `1_MARS_SOM_BASE-GEAR_FIRST`, which is an address rather
 * than a name and too long for a card besides.
 *
 * This cannot invent information that was never published: `abootloop` stays one
 * word. But "Abootloop" is a name and `abootloop` is an identifier, and the ids in
 * these repos are overwhelmingly camel-cased or underscore-separated English.
 */
internal fun readableModuleName(published: String, moduleId: String): String {
    val given = published.trim()
    val looksLikeId = given.isEmpty() ||
        given == moduleId ||
        (given.none { it.isWhitespace() } && (given.count { it == '.' } >= 2 || '/' in given))
    if (!looksLikeId) return given

    val tail = moduleId.substringAfterLast('/').trim().ifEmpty { return moduleId }
    val segments = tail.split('.').filter { it.isNotBlank() }
    val meaningful = when {
        segments.size <= 1 -> segments
        // "hook", "app", "mod" and friends need their qualifier to mean anything.
        segments.last().length <= 4 -> segments.takeLast(2)
        else -> listOf(segments.last())
    }

    val words = meaningful.flatMap { splitModuleWords(it) }.filter { it.isNotBlank() }
    if (words.isEmpty()) return tail

    return words.joinToString(" ") { word ->
        // Acronyms the author already capitalised stay put; DNS must not become Dns.
        if (word.length > 1 && word.all { it.isUpperCase() || it.isDigit() }) word
        else word.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Splits on separators, then on camel-case boundaries.
 *
 * A boundary is an upper-case letter following a lower-case letter or a digit, which
 * breaks `adGuardDNS4Magisk` into `ad Guard DNS4 Magisk`. Deliberately one-sided:
 * splitting between a letter and a following digit as well would turn every `v2` and
 * `x86` suffix in the ecosystem into two words.
 */
private fun splitModuleWords(segment: String): List<String> =
    segment.split('_', '-', ' ').filter { it.isNotBlank() }.flatMap { chunk ->
        val out = mutableListOf<String>()
        val current = StringBuilder()
        chunk.forEachIndexed { index, ch ->
            val previous = chunk.getOrNull(index - 1)
            val boundary = previous != null &&
                (previous.isLowerCase() || previous.isDigit()) &&
                ch.isUpperCase()
            if (boundary && current.isNotEmpty()) {
                out += current.toString()
                current.clear()
            }
            current.append(ch)
        }
        if (current.isNotEmpty()) out += current.toString()
        out
    }

// ---- lenient JSON accessors -------------------------------------------------
//
// These indexes disagree with themselves: `versionCode` is documented as an integer
// and routinely published as a string, and timestamps arrive as seconds, milliseconds
// or floating-point seconds. Every accessor degrades to null rather than throwing, so
// one malformed entry costs one module instead of the whole source.

private fun JsonObject?.str(key: String): String? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        ?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject?.num(key: String): Long? =
    this?.get(key)?.let { element ->
        runCatching { element.jsonPrimitive }.getOrNull()?.let { primitive ->
            primitive.longOrNull ?: primitive.contentOrNull?.trim()?.toDoubleOrNull()?.toLong()
        }
    }

/** Normalises the three timestamp conventions these repos use into millis. */
private fun JsonObject?.epochMillis(key: String): Long {
    val raw = num(key) ?: return 0L
    // A value small enough to be seconds since 1970 is seconds; anything larger is
    // already millis. The boundary sits far from any plausible real date either way.
    return if (raw in 1..99_999_999_999L) raw * 1000 else raw
}
