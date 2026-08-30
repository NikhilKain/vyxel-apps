package com.vythera.vyxelapps.expressive.data.source

import android.util.JsonReader
import android.util.JsonToken
import com.vythera.vyxelapps.expressive.core.net.HttpException
import com.vythera.vyxelapps.expressive.core.net.Net
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Any F-Droid-protocol repository: f-droid.org itself, IzzyOnDroid, or a user-added
 * mirror. This is the same index format Droidify and the official client consume.
 *
 * `index-v1.jar` is used rather than `index-v2.json` on purpose: v2 is served
 * uncompressed (54 MB for f-droid.org) while the v1 jar is deflate-compressed to
 * ~12 MB for the same catalog. The index is streamed with [JsonReader] straight out
 * of the zip so the full document is never materialised in memory — only the
 * handful of fields the store actually renders are retained.
 */
class FdroidRepoSource(
    override val id: SourceId,
    private val repoUrl: String,
    private val cacheDir: File,
    /** How long a synced index stays fresh before a re-sync is attempted. */
    private val ttlMillis: Long = 12 * 60 * 60 * 1000L,
) : AppSource {

    private val mutex = Mutex()
    private val cacheFile: File get() = File(cacheDir, "index-${id.name.lowercase()}.json")

    @Volatile
    private var memory: List<AppItem>? = null

    /** Emits coarse progress (0f..1f) while an index is downloading/parsing. */
    var onProgress: ((Float, String) -> Unit)? = null

    override suspend fun featured(): List<AppItem> {
        val all = catalog()
        // Newest-updated entries make the best "what's happening" rail, but a repo
        // index is dominated by trivial version bumps, so prefer apps that at least
        // have artwork and a summary.
        return all.asSequence()
            .filter { it.iconUrl != null && it.summary.isNotBlank() }
            .sortedByDescending { it.updatedAt }
            .take(120)
            .toList()
    }

    override suspend fun search(query: String): List<AppItem> {
        if (query.isBlank()) return emptyList()
        // Scoring is shared with every other source so a local index result and a
        // remote API result are ranked on the same scale once merged.
        return catalog().asSequence()
            .mapNotNull { item ->
                val score = relevanceScore(
                    name = item.name,
                    packageName = item.packageName,
                    summary = item.summary,
                    description = item.description,
                    categories = item.categories,
                    query = query,
                )
                if (score > 0) item to score else null
            }
            .sortedWith(
                compareByDescending<Pair<AppItem, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .map { it.first }
            .take(150)
            .toList()
    }

    /** Cached catalog, syncing from the network when missing or stale. */
    suspend fun catalog(forceRefresh: Boolean = false): List<AppItem> {
        memory?.let { if (!forceRefresh) return it }
        return mutex.withLock {
            memory?.let { if (!forceRefresh) return@withLock it }

            if (!forceRefresh && isCacheFresh()) {
                readCache()?.let { cached ->
                    memory = cached
                    return@withLock cached
                }
            }

            val fetched = runCatching { downloadAndParse() }.getOrElse { error ->
                // A stale cache beats an empty screen.
                readCache()?.let { stale ->
                    memory = stale
                    return@withLock stale
                }
                throw error
            }
            writeCache(fetched)
            memory = fetched
            fetched
        }
    }

    private fun isCacheFresh(): Boolean =
        cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < ttlMillis

    private suspend fun readCache(): List<AppItem>? = withContext(Dispatchers.IO) {
        runCatching {
            if (!cacheFile.exists()) return@runCatching null
            Net.json.decodeFromString<List<AppItem>>(cacheFile.readText())
        }.getOrNull()
    }

    private suspend fun writeCache(items: List<AppItem>) = withContext(Dispatchers.IO) {
        runCatching {
            cacheDir.mkdirs()
            cacheFile.writeText(Net.json.encodeToString(items))
        }
    }

    // ---------------------------------------------------------------- parsing

    private suspend fun downloadAndParse(): List<AppItem> = withContext(Dispatchers.IO) {
        onProgress?.invoke(0.05f, "Contacting ${id.displayName}")
        val response = Net.execute("$repoUrl/index-v1.jar")
        response.use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code, repoUrl)
            val body = resp.body ?: throw HttpException(resp.code, repoUrl)

            onProgress?.invoke(0.15f, "Downloading index")
            ZipInputStream(body.byteStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "index-v1.json") {
                        onProgress?.invoke(0.3f, "Reading catalog")
                        return@withContext parseIndex(zip)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            throw IllegalStateException("index-v1.json missing from ${id.displayName} index")
        }
    }

    /**
     * Single forward pass over the index. `apps` carries the metadata and `packages`
     * carries the downloadable versions; the index always emits `apps` first, so the
     * two are joined as `packages` streams in.
     */
    private fun parseIndex(stream: java.io.InputStream): List<AppItem> {
        val apps = LinkedHashMap<String, AppItem>(4096)
        JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.isLenient = true
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "apps" -> readApps(reader, apps)
                    "packages" -> readPackages(reader, apps)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        onProgress?.invoke(1f, "Done")
        // Entries without a downloadable APK are noise in a store.
        return apps.values.filter { it.downloadUrl != null }
    }

    private fun readApps(reader: JsonReader, out: MutableMap<String, AppItem>) {
        reader.beginArray()
        var seen = 0
        while (reader.hasNext()) {
            val app = readApp(reader)
            if (app != null) out[app.packageName!!] = app
            if (++seen % 500 == 0) onProgress?.invoke(0.3f + (seen / 20000f).coerceAtMost(0.4f), "Reading catalog")
        }
        reader.endArray()
    }

    private fun readApp(reader: JsonReader): AppItem? {
        var packageName: String? = null
        var name: String? = null
        var summary = ""
        var description = ""
        var icon: String? = null
        var author: String? = null
        var license: String? = null
        var website: String? = null
        var sourceCode: String? = null
        var donate: String? = null
        var changelog: String? = null
        var lastUpdated = 0L
        var added = 0L
        var categories: List<String> = emptyList()
        var antiFeatures: List<String> = emptyList()
        var localizedName: String? = null
        var localizedSummary: String? = null
        var localizedDescription: String? = null
        var localizedIcon: String? = null
        var localizedWhatsNew: String? = null
        var screenshots: List<String> = emptyList()
        var localeUsed: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "packageName" -> packageName = reader.nextStringOrNull()
                "name" -> name = reader.nextStringOrNull()
                "summary" -> summary = reader.nextStringOrNull().orEmpty()
                "description" -> description = reader.nextStringOrNull().orEmpty()
                "icon" -> icon = reader.nextStringOrNull()
                "authorName" -> author = reader.nextStringOrNull()
                "license" -> license = reader.nextStringOrNull()
                "webSite" -> website = reader.nextStringOrNull()
                "sourceCode" -> sourceCode = reader.nextStringOrNull()
                "donate" -> donate = reader.nextStringOrNull()
                "changelog" -> changelog = reader.nextStringOrNull()
                "lastUpdated" -> lastUpdated = reader.nextLongOrZero()
                "added" -> added = reader.nextLongOrZero()
                "categories" -> categories = reader.readStringArray()
                "antiFeatures" -> antiFeatures = reader.readStringArray()
                "localized" -> {
                    val loc = readLocalized(reader)
                    localeUsed = loc.locale
                    localizedName = loc.name
                    localizedSummary = loc.summary
                    localizedDescription = loc.description
                    localizedIcon = loc.icon
                    localizedWhatsNew = loc.whatsNew
                    screenshots = loc.screenshots
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val pkg = packageName ?: return null
        val finalName = localizedName ?: name ?: pkg
        val iconUrl = when {
            localizedIcon != null && localeUsed != null ->
                "$repoUrl/$pkg/$localeUsed/$localizedIcon"
            icon != null -> "$repoUrl/icons-640/$icon"
            else -> null
        }
        val shotUrls = if (localeUsed != null) {
            screenshots.map { "$repoUrl/$pkg/$localeUsed/phoneScreenshots/$it" }
        } else emptyList()

        return AppItem(
            id = "${id.name}:$pkg",
            source = id,
            name = finalName,
            summary = (localizedSummary ?: summary).cleanup().take(240),
            description = (localizedDescription ?: description).stripHtml().take(6000),
            iconUrl = iconUrl,
            packageName = pkg,
            updatedAt = lastUpdated,
            addedAt = added,
            author = author,
            license = license,
            categories = categories,
            screenshots = shotUrls,
            website = website,
            sourceCodeUrl = sourceCode,
            donateUrl = donate,
            changelog = localizedWhatsNew?.stripHtml()?.take(2000) ?: changelog,
            antiFeatures = antiFeatures,
        )
    }

    private class Localized(
        val locale: String?,
        val name: String?,
        val summary: String?,
        val description: String?,
        val icon: String?,
        val whatsNew: String?,
        val screenshots: List<String>,
    )

    /**
     * Picks the best available locale, preferring the exact English variants the
     * indexes overwhelmingly use, then falling back to whatever came first.
     */
    private fun readLocalized(reader: JsonReader): Localized {
        var best: Localized? = null
        var bestRank = Int.MAX_VALUE

        reader.beginObject()
        while (reader.hasNext()) {
            val locale = reader.nextName()
            val rank = when (locale) {
                "en-US" -> 0
                "en" -> 1
                "en-GB" -> 2
                else -> 5
            }
            var name: String? = null
            var summary: String? = null
            var description: String? = null
            var icon: String? = null
            var whatsNew: String? = null
            var shots: List<String> = emptyList()

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "name" -> name = reader.nextStringOrNull()
                    "summary" -> summary = reader.nextStringOrNull()
                    "description" -> description = reader.nextStringOrNull()
                    "icon" -> icon = reader.nextStringOrNull()
                    "whatsNew" -> whatsNew = reader.nextStringOrNull()
                    "phoneScreenshots" -> shots = reader.readStringArray()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (rank < bestRank) {
                bestRank = rank
                best = Localized(locale, name, summary, description, icon, whatsNew, shots)
            }
        }
        reader.endObject()
        return best ?: Localized(null, null, null, null, null, null, emptyList())
    }

    /** Joins the newest version of each package onto the already-parsed metadata. */
    private fun readPackages(reader: JsonReader, apps: MutableMap<String, AppItem>) {
        reader.beginObject()
        while (reader.hasNext()) {
            val pkg = reader.nextName()
            var bestCode = -1L
            var bestVersion: String? = null
            var bestApk: String? = null
            var bestSize = 0L
            var bestMinSdk = 0

            reader.beginArray()
            while (reader.hasNext()) {
                var versionCode = 0L
                var versionName: String? = null
                var apkName: String? = null
                var size = 0L
                var minSdk = 0
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "versionCode" -> versionCode = reader.nextLongOrZero()
                        "versionName" -> versionName = reader.nextStringOrNull()
                        "apkName" -> apkName = reader.nextStringOrNull()
                        "size" -> size = reader.nextLongOrZero()
                        "minSdkVersion" -> minSdk = reader.nextLongOrZero().toInt()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (versionCode > bestCode && apkName != null) {
                    bestCode = versionCode
                    bestVersion = versionName
                    bestApk = apkName
                    bestSize = size
                    bestMinSdk = minSdk
                }
            }
            reader.endArray()

            val existing = apps[pkg]
            if (existing != null && bestApk != null) {
                apps[pkg] = existing.copy(
                    version = bestVersion,
                    versionCode = bestCode,
                    downloadUrl = "$repoUrl/$bestApk",
                    sizeBytes = bestSize,
                    minSdk = bestMinSdk,
                )
            }
        }
        reader.endObject()
    }
}

// ------------------------------------------------------------- reader helpers

private fun JsonReader.nextStringOrNull(): String? =
    if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()

/** Index fields are inconsistently typed (`suggestedVersionCode` is a string). */
private fun JsonReader.nextLongOrZero(): Long = when (peek()) {
    JsonToken.NULL -> { nextNull(); 0L }
    JsonToken.STRING -> nextString().toLongOrNull() ?: 0L
    JsonToken.NUMBER -> nextLong()
    else -> { skipValue(); 0L }
}

private fun JsonReader.readStringArray(): List<String> {
    if (peek() == JsonToken.NULL) { nextNull(); return emptyList() }
    if (peek() != JsonToken.BEGIN_ARRAY) { skipValue(); return emptyList() }
    val out = ArrayList<String>(4)
    beginArray()
    while (hasNext()) {
        when (peek()) {
            JsonToken.STRING -> out.add(nextString())
            JsonToken.NULL -> nextNull()
            else -> skipValue()
        }
    }
    endArray()
    return out
}

private fun String.cleanup(): String = replace(Regex("\\s+"), " ").trim()

/** Index descriptions are a small HTML subset; flatten them for Compose text. */
internal fun String.stripHtml(): String =
    replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("<li>", RegexOption.IGNORE_CASE), "\n  •  ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
