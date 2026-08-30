package com.vythera.vyxelapps.expressive.data.source

import com.vythera.vyxelapps.expressive.core.net.Net
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitLab.com projects and their releases.
 *
 * GitLab attaches release binaries as `assets.links`, which are arbitrary URLs
 * rather than uploaded files, so sizes are usually unknown until download starts.
 */
class GitLabSource(
    private val baseUrl: String = "https://gitlab.com",
) : AppSource {

    override val id = SourceId.GitLab

    /** Keyed by project path, so a cache hit does not depend on who created the item. */
    private val releaseCache = mutableMapOf<String, AppItem>()

    override suspend fun featured(): List<AppItem> =
        query("android", perPage = 40, filterNoise = true, retries = 2)

    override suspend fun search(query: String): List<AppItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return query(q, perPage = 30, filterNoise = false, retries = 1)
    }

    private suspend fun query(
        term: String,
        perPage: Int,
        filterNoise: Boolean,
        retries: Int,
    ): List<AppItem> {
        val encoded = java.net.URLEncoder.encode(term, "UTF-8")
        val body = Net.getString(
            // Do NOT add `search_namespaces=true` here: combined with `order_by=
            // star_count` GitLab returns a 500 for every query.
            "$baseUrl/api/v4/projects?search=$encoded&order_by=star_count&sort=desc" +
                "&per_page=$perPage&archived=false",
            retries = retries,
        )
        val parsed = Net.decodeOrNull<List<GlProject>>(body) ?: return emptyList()
        return parsed
            .filter { !filterNoise || looksLikeAndroidApp(it.name, it.description, it.topics) }
            .map { it.toAppItem() }
    }

    /**
     * Resolves the newest release that actually ships an APK. Mirrors Classic.
     *
     * Three things here used to make GitLab entries show no download at all:
     *
     * 1. The project was looked up through an in-memory id map filled only by this
     *    instance's own `query()`. Anything else — an item restored from cache, or one
     *    handed over by the Classic shell — was absent from it, so `resolve` returned
     *    unchanged and the app was permanently APK-less. GitLab accepts a URL-encoded
     *    project path in place of the numeric id, and the path is already in `item.id`.
     * 2. Only the newest release was fetched. Projects routinely tag several releases
     *    with no binaries attached, so the search has to walk back like Classic's does.
     * 3. A link asset's `url` may point at a landing page; `direct_asset_url` is the
     *    file itself.
     */
    override suspend fun resolve(item: AppItem): AppItem {
        val path = item.id.substringAfter("${SourceId.GitLab.name}:", "")
        if (path.isBlank() || !path.contains('/')) return item
        releaseCache[path]?.let { return it }

        val encoded = java.net.URLEncoder.encode(path, "UTF-8")
        val body = runCatching {
            Net.getString("$baseUrl/api/v4/projects/$encoded/releases?per_page=30")
        }.getOrElse { return item }

        val releases = Net.decodeOrNull<List<GlRelease>>(body) ?: return item
        if (releases.isEmpty()) return item

        var chosen = releases.first()
        var asset: ReleaseAsset? = null
        for (release in releases) {
            val hit = pickBestApk(release.assets?.links.orEmpty().map { it.toAsset() })
            if (hit != null) {
                chosen = release
                asset = hit
                break
            }
        }

        val resolved = item.copy(
            version = chosen.tagName?.removePrefix("v") ?: item.version,
            downloadUrl = asset?.url,
            changelog = chosen.description?.take(4000) ?: item.changelog,
            updatedAt = chosen.releasedAt.isoToEpochMillis().takeIf { it > 0 } ?: item.updatedAt,
            needsReleaseLookup = false,
        )
        releaseCache[path] = resolved
        return resolved
    }

    private fun GlProject.toAppItem(): AppItem {
        val itemId = "${SourceId.GitLab.name}:$pathWithNamespace"
        return AppItem(
            id = itemId,
            source = SourceId.GitLab,
            name = name,
            summary = description.orEmpty().take(240),
            description = description.orEmpty(),
            iconUrl = avatarUrl,
            author = pathWithNamespace.substringBefore('/'),
            categories = topics.take(6),
            stars = stars,
            updatedAt = lastActivityAt.isoToEpochMillis(),
            sourceCodeUrl = webUrl,
            needsReleaseLookup = true,
        )
    }

    // ------------------------------------------------------------------ dtos

    @Serializable
    private data class GlProject(
        val id: Long = 0L,
        val name: String = "",
        val description: String? = null,
        @SerialName("star_count") val stars: Int = 0,
        @SerialName("web_url") val webUrl: String = "",
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("last_activity_at") val lastActivityAt: String? = null,
        @SerialName("path_with_namespace") val pathWithNamespace: String = "",
        val topics: List<String> = emptyList(),
    )

    @Serializable
    private data class GlRelease(
        @SerialName("tag_name") val tagName: String? = null,
        val description: String? = null,
        @SerialName("released_at") val releasedAt: String? = null,
        val assets: GlAssets? = null,
    )

    @Serializable
    private data class GlAssets(val links: List<GlLink> = emptyList())

    @Serializable
    private data class GlLink(
        val name: String = "",
        val url: String = "",
        @SerialName("direct_asset_url") val directAssetUrl: String? = null,
    ) {
        /**
         * A link's display name is free text ("Download APK", "arm64 build"), so the
         * `.apk` test has to fall back to the filename in the URL or the picker
         * discards perfectly good assets.
         */
        fun toAsset(): ReleaseAsset {
            val href = directAssetUrl?.takeIf { it.isNotBlank() } ?: url
            val fileName = href.substringBefore('?').substringBefore('#').substringAfterLast('/')
            val label = if (name.endsWith(".apk", ignoreCase = true)) name else fileName
            return ReleaseAsset(label, href, 0L)
        }
    }
}
