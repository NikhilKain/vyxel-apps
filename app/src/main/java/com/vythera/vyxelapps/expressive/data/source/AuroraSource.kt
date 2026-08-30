package com.vythera.vyxelapps.expressive.data.source

import com.vythera.vyxelapps.compareVersions
import com.vythera.vyxelapps.expressive.core.net.Net
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Aurora OSS's build server.
 *
 * `auroraoss.com/api/files` returns its whole `/downloads` tree as one nested JSON
 * document — every build of every Aurora project, plus device configs and artwork.
 * This walks it and keeps the newest stable APK of each app.
 *
 * **What this is not.** Aurora Store is the well-known way to install Google Play
 * apps, so it is easy to assume this endpoint carries Play's catalogue. It does not,
 * and nothing on Aurora's servers does. Aurora Store reaches Play from the *client*
 * at run time, over Google's private protobuf API, authenticated either with the
 * user's own Google account or with Aurora's pool of shared anonymous ones. What is
 * hosted here is Aurora's own software: the store itself, AuroraDroid and AppWarden.
 * Listing them means a Vyxel user can find and install Aurora Store from Vyxel — it
 * does not make Vyxel a Play client.
 */
class AuroraSource : AppSource {

    override val id: SourceId = SourceId.Aurora

    private companion object {
        const val INDEX_URL = "https://auroraoss.com/api/files"
        const val BASE_URL = "https://auroraoss.com"

        /**
         * Only stable builds, and only the primary variant.
         *
         * `Nightly` is a dated build per day — hundreds of them, and not what anyone
         * browsing a store wants. `huawei` and `preload` are same-version variants for
         * specific situations; surfacing them as separate entries would put three
         * near-identical Aurora Stores in the results.
         */
        val RELEASE_PATH = Regex("""^/downloads/([^/]+)/Release/[^/]+\.apk$""")

        /** `AuroraStore-4.8.4.apk`, `AppWarden_v1.0.2.apk`. */
        val VERSION_IN_NAME = Regex("""[-_]v?(\d+(?:\.\d+)+)\.apk$""", RegexOption.IGNORE_CASE)

        /**
         * Blurbs, because the file server publishes none.
         *
         * A store row with no description is a dead row, and these three apps do not
         * change often enough for a hand-written line to go stale.
         */
        val BLURBS = mapOf(
            "AuroraStore" to ("Aurora Store" to
                "An unofficial, privacy-respecting client for Google Play. Browse and " +
                    "install Play apps without the Play Store, signed in or anonymously."),
            "AuroraDroid" to ("Aurora Droid" to
                "A full-featured F-Droid client with multi-repo support, built by the " +
                    "Aurora team. No longer actively developed."),
            "AppWarden" to ("AppWarden" to
                "Blocks apps from phoning home to trackers and from nagging about " +
                    "updates. Needs root."),
        )
    }

    @Volatile
    private var cached: List<AppItem>? = null

    override suspend fun featured(): List<AppItem> = load()

    override suspend fun search(query: String): List<AppItem> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        return load().filter {
            it.name.lowercase().contains(needle) || it.summary.lowercase().contains(needle)
        }
    }

    private suspend fun load(): List<AppItem> {
        cached?.let { return it }
        val fetched = runCatching { fetch() }.getOrElse { throw it }
        cached = fetched
        return fetched
    }

    private suspend fun fetch(): List<AppItem> = withContext(Dispatchers.IO) {
        val body = Net.getString(INDEX_URL, retries = 2)
        val root = runCatching { Net.json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext emptyList()

        val files = mutableListOf<AuroraFile>()
        collect(root, files)

        files.asSequence()
            .mapNotNull { file ->
                val app = RELEASE_PATH.find(file.path)?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val version = VERSION_IN_NAME.find(file.name)?.groupValues?.get(1)
                    ?: return@mapNotNull null
                Triple(app, version, file)
            }
            // Several published versions per app; keep the highest, comparing
            // numerically rather than by string so 4.10 would beat 4.9.
            .groupBy { it.first }
            .mapNotNull { (app, builds) ->
                val (_, version, file) = builds.maxWith(
                    Comparator { a, b -> compareVersions(a.second, b.second) }
                )
                val (title, blurb) = BLURBS[app] ?: (app to "")
                AppItem(
                    id = "Aurora:$app",
                    source = SourceId.Aurora,
                    name = title,
                    summary = blurb,
                    description = blurb,
                    version = version,
                    downloadUrl = BASE_URL + file.path,
                    sizeBytes = file.size,
                    updatedAt = file.lastModified,
                    website = "https://auroraoss.com",
                    sourceCodeUrl = SOURCE_URLS[app],
                    verified = true,
                )
            }
            .sortedBy { it.name }
            .toList()
    }

    private data class AuroraFile(
        val name: String,
        val path: String,
        val size: Long,
        val lastModified: Long,
    )

    /**
     * Flattens the nested tree.
     *
     * Recursive rather than iterative because the tree is a handful of levels deep and
     * a few hundred nodes wide — there is no depth here to blow a stack on.
     */
    private fun collect(node: JsonObject, out: MutableList<AuroraFile>) {
        val isDirectory = node["isDirectory"]?.let {
            runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull()
        } ?: false
        val path = node["path"]?.let {
            runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
        }.orEmpty()

        if (!isDirectory) {
            val name = node["name"]?.let {
                runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
            }.orEmpty()
            if (name.isNotBlank() && path.isNotBlank()) {
                out += AuroraFile(
                    name = name,
                    path = path,
                    size = node.longOr("size", 0L),
                    // Timestamps come back as floating-point millis.
                    lastModified = node.longOr("lastModified", 0L),
                )
            }
            return
        }

        val contents = runCatching { node["contents"]?.jsonArray }.getOrNull() ?: return
        contents.forEach { child ->
            runCatching { child.jsonObject }.getOrNull()?.let { collect(it, out) }
        }
    }
}

private val SOURCE_URLS = mapOf(
    "AuroraStore" to "https://gitlab.com/AuroraOSS/AuroraStore",
    "AuroraDroid" to "https://gitlab.com/AuroraOSS/AuroraDroid",
    "AppWarden" to "https://gitlab.com/AuroraOSS/AppWarden",
)

private fun JsonObject.longOr(key: String, fallback: Long): Long =
    this[key]?.let { element ->
        runCatching { element.jsonPrimitive }.getOrNull()?.let { primitive ->
            primitive.longOrNull ?: primitive.contentOrNull?.toDoubleOrNull()?.toLong()
        }
    } ?: fallback
