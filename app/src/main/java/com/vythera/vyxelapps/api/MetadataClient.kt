package com.vythera.vyxelapps.api

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppEntry(
    val id          : String       = "",
    val source      : String       = "",
    val name        : String       = "",
    val summary     : String       = "",
    val icon        : String       = "",
    val stars       : Int          = 0,
    val version     : String       = "",
    val homepage    : String       = "",
    @SerializedName(value = "apkUrl", alternate = ["apk_url", "apk"])
    val apkUrl      : String       = "",
    // NB: do NOT add "source" as an alternate here — it collides with the
    // `source` field above, and Gson then refuses to build a type adapter for
    // AppEntry at all, throwing on EVERY parse. That silently emptied every
    // CDN source (F-Droid/GitLab/…); GitHub only survived via its live-API
    // fallback. The CDN key is "source_code".
    @SerializedName(value = "sourceCode", alternate = ["source_code"])
    val sourceCode  : String       = "",
    val license     : String       = "",
    val categories  : List<String> = emptyList(),
    @SerializedName(value = "isLive", alternate = ["is_live"])
    val isLive      : Boolean      = false
)

/**
 * One root module as the CDN publishes it.
 *
 * Every field carries a default because this is parsed by Gson, which allocates
 * through `Unsafe` and never runs the Kotlin constructor — a key the build omits
 * would otherwise land as null inside a non-null type and throw on first read.
 */
data class ModuleEntry(
    val id: String = "",
    val name: String = "",
    val summary: String = "",
    val version: String = "",
    val author: String = "",
    /** Magisk / Zygisk / LSPosed / KernelSU. */
    val family: String = "",
    /** Which repository it came from, matching Vyxel's own source ids. */
    val source: String = "",
    val stars: Int = 0,
    @SerializedName(value = "zipUrl", alternate = ["zip_url", "zip"])
    val zipUrl: String = "",
    val homepage: String = "",
    val size: Long = 0L,
    val updated: Long = 0L,
)

data class BrowseResult(val apps: List<AppEntry>, val total: Int, val pages: Int)

data class MetaInfo(
    val total       : Int               = 0,
    val sources     : Map<String, Int>  = emptyMap(),
    val lastUpdated : String            = ""
)

class MetadataClient(private val context: Context, private val cdnBase: String) {

    private val gson  = Gson()

    /** Only bookkeeping now — timestamps and ETags. Payloads live in [cacheDir]. */
    private val prefs = context.getSharedPreferences("metadata_cache", Context.MODE_PRIVATE)

    /**
     * index.json runs to megabytes. SharedPreferences holds every value in
     * memory for the life of the process and rewrites the entire file on each
     * commit, so storing payloads there cost memory on every launch and a full
     * rewrite on every refresh. Plain files, read on demand.
     */
    private val cacheDir = File(context.cacheDir, "metadata").apply { mkdirs() }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var indexCache     : List<AppEntry>? = null
    private var indexLoadedAt  : Long            = 0L

    private val CACHE_TTL = 60 * 60 * 1000L  // 1 hour

    init {
        // One-time migration: drop the old payload-in-prefs cache (v1).
        if (prefs.getInt("cache_version", 1) < 2) {
            runCatching { prefs.edit().clear().putInt("cache_version", 2).apply() }
        }
    }

    // ── public ────────────────────────────────────────────────────────────

    suspend fun init() = withContext(Dispatchers.IO) {
        try { refreshIndex() } catch (e: Exception) {
            Log.w("MetadataClient", "init refresh failed: ${e.message}")
        }
    }

    suspend fun refresh() = refreshIndex()

    suspend fun search(query: String, source: String? = null): List<AppEntry> {
        val index = ensureIndex()
        val q     = query.lowercase().trim()
        return index.filter { app ->
            (source == null || app.source == source) &&
            (q.isEmpty() ||
                app.name.lowercase().contains(q) ||
                app.summary.lowercase().contains(q))
        }
    }

    suspend fun browseSource(source: String, page: Int = 1): BrowseResult =
        withContext(Dispatchers.IO) {
            val pageKey     = "source_${source}_p$page"
            val manifestKey = "manifest_$source"

            val pageJson     = cachedFetch(pageKey,     "$cdnBase/data/sources/$source/page-$page.json")
            val manifestJson = cachedFetch(manifestKey, "$cdnBase/data/sources/$source/manifest.json")

            val type : java.lang.reflect.Type = object : TypeToken<List<AppEntry>>() {}.type
            val apps : List<AppEntry> = try { gson.fromJson(pageJson, type) } catch (_: Exception) { emptyList() }

            val manifest = try { gson.fromJson(manifestJson, JsonObject::class.java) } catch (_: Exception) { null }
            val total    = manifest?.get("total")?.asInt ?: apps.size
            val pages    = manifest?.get("pages")?.asInt ?: 1

            BrowseResult(apps, total, pages)
        }

    suspend fun getDetail(appId: String): AppEntry? = withContext(Dispatchers.IO) {
        try {
            val parts = appId.split(":", limit = 2)
            if (parts.size != 2) return@withContext null
            val (src, pkg) = parts
            val json = cachedFetch("detail_${src}_$pkg", "$cdnBase/data/detail/$src/$pkg.json")
            gson.fromJson(json, AppEntry::class.java)
        } catch (_: Exception) { null }
    }

    suspend fun getMeta(): MetaInfo? = withContext(Dispatchers.IO) {
        try {
            val json = cachedFetch("meta", "$cdnBase/data/meta.json")
            gson.fromJson(json, MetaInfo::class.java)
        } catch (_: Exception) { null }
    }

    /**
     * The pre-built root-module catalogue.
     *
     * Assembling this on the device is what made the Modules screen slow: two index
     * downloads, a bounded fan-out over ~130 individual `module.prop` files, and up to
     * twelve paged GitHub org listings — fifteen-odd seconds on a cold cache, and a
     * meaningful bite out of GitHub's hourly budget every time. The same work done
     * once a day on a build server collapses to a single conditional GET here, which
     * is usually a 304.
     *
     * Empty when the CDN has no `modules.json` yet, which is the caller's signal to
     * fall back to scraping the repositories live.
     */
    suspend fun getModules(): List<ModuleEntry> = withContext(Dispatchers.IO) {
        try {
            val json = cachedFetch("modules", "$cdnBase/data/modules.json")
            val root = com.google.gson.JsonParser.parseString(json)
            val type: java.lang.reflect.Type = object : TypeToken<List<ModuleEntry>>() {}.type
            // Accept a bare array as well as the wrapped object, the same way the app
            // index does — the two have drifted apart before.
            when {
                root.isJsonArray -> gson.fromJson(root, type)
                root.isJsonObject -> gson.fromJson(root.asJsonObject.get("modules"), type)
                else -> emptyList()
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w("MetadataClient", "modules.json unavailable: ${e.message}")
            emptyList()
        }
    }

    // ── private ───────────────────────────────────────────────────────────

    private suspend fun refreshIndex() = withContext(Dispatchers.IO) {
        val json : String = fetch("$cdnBase/data/index.json", "index")
        indexCache    = parseIndex(json)
        indexLoadedAt = System.currentTimeMillis()
        Log.d("MetadataClient", "Index refreshed — ${indexCache?.size ?: 0} apps")
    }

    // index.json is an object: { last_updated, total_all, total_index, apps: [...] }.
    // Older builds wrote a bare array, so accept both.
    private fun parseIndex(json: String): List<AppEntry> {
        val type : java.lang.reflect.Type = object : TypeToken<List<AppEntry>>() {}.type
        return try {
            val root = com.google.gson.JsonParser.parseString(json)
            when {
                root.isJsonArray  -> gson.fromJson(root, type)
                root.isJsonObject -> gson.fromJson(root.asJsonObject.get("apps"), type) ?: emptyList()
                else              -> emptyList()
            }
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun ensureIndex(): List<AppEntry> {
        val mem = indexCache
        if (mem != null && System.currentTimeMillis() - indexLoadedAt < CACHE_TTL) return mem

        val disk = withContext(Dispatchers.IO) { loadCache("index") }
        if (disk != null && isFresh("index")) {
            return parseIndex(disk).also {
                indexCache    = it
                indexLoadedAt = System.currentTimeMillis()
            }
        }

        return try {
            withContext(Dispatchers.IO) { refreshIndex() }
            indexCache ?: emptyList()
        } catch (e: Exception) {
            // Offline with a stale copy on disk: serve it rather than nothing.
            Log.w("MetadataClient", "index refresh failed, using stale cache: ${e.message}")
            disk?.let { parseIndex(it).also { parsed -> indexCache = parsed } } ?: emptyList()
        }
    }

    private suspend fun cachedFetch(key: String, url: String): String =
        withContext(Dispatchers.IO) {
            if (isFresh(key)) loadCache(key)?.let { return@withContext it }
            try {
                fetch(url, key)
            } catch (e: Exception) {
                // Stale data beats an empty screen when the CDN is unreachable.
                loadCache(key) ?: throw e
            }
        }

    /**
     * Conditional GET. `URL.openStream()` — what this used to use — has no
     * connect or read timeout at all, so a stalled CDN hung the caller forever
     * with a spinner up. The stored ETag also turns most refreshes into a 304
     * with no body.
     */
    private fun fetch(url: String, key: String? = null): String {
        val builder = Request.Builder().url(url)
        val etag    = key?.let { prefs.getString("${it}_etag", null) }
        if (etag != null && cacheFile(key!!).exists()) {
            builder.addHeader("If-None-Match", etag)
        }

        http.newCall(builder.build()).execute().use { response ->
            if (response.code == 304 && key != null) {
                loadCache(key)?.let { cached ->
                    touch(key)
                    return cached
                }
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body?.string() ?: throw IOException("Empty body for $url")
            if (key != null) saveCache(key, body, response.header("ETag"))
            return body
        }
    }

    private fun cacheFile(key: String): File = File(cacheDir, "${key.hashCode()}.json")

    private fun isFresh(key: String): Boolean =
        System.currentTimeMillis() - prefs.getLong("${key}_ts", 0L) < CACHE_TTL &&
            cacheFile(key).exists()

    private fun touch(key: String) =
        prefs.edit().putLong("${key}_ts", System.currentTimeMillis()).apply()

    private fun saveCache(key: String, data: String, etag: String? = null) {
        runCatching { cacheFile(key).writeText(data) }
        prefs.edit()
            .putLong("${key}_ts", System.currentTimeMillis())
            .apply { if (etag != null) putString("${key}_etag", etag) }
            .apply()
    }

    private fun loadCache(key: String): String? =
        runCatching { cacheFile(key).takeIf { it.exists() }?.readText() }.getOrNull()
}
