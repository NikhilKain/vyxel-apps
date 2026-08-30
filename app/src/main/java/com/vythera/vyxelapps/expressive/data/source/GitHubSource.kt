package com.vythera.vyxelapps.expressive.data.source

import com.vythera.vyxelapps.expressive.core.net.HttpException
import com.vythera.vyxelapps.expressive.core.net.Net
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub releases as an app source.
 *
 * Resolving every repo's latest release up-front would burn the (60 req/hour)
 * unauthenticated rate limit within a single screen, so the list view shows repos
 * and [resolve] fetches the actual APK only when the user opens one. Supplying a
 * personal access token in Settings raises the ceiling to 5000 req/hour.
 */
class GitHubSource(
    private val tokenProvider: () -> String? = { null },
    /** Invoked when GitHub rejects the configured token, so the UI can say so. */
    private val onTokenRejected: () -> Unit = {},
) : AppSource {

    override val id = SourceId.GitHub

    private val releaseCache = mutableMapOf<String, AppItem>()

    /** Set once a token has been rejected, so we stop resending a known-bad one. */
    @Volatile
    private var tokenIsBad = false

    private fun headers(token: String?): Map<String, String> = buildMap {
        put("Accept", "application/vnd.github+json")
        put("X-GitHub-Api-Version", "2022-11-28")
        token?.let { put("Authorization", "Bearer $it") }
    }

    private fun activeToken(): String? =
        if (tokenIsBad) null else tokenProvider()?.takeIf { it.isNotBlank() }

    /**
     * GET that survives a bad token.
     *
     * An expired or mistyped PAT makes GitHub return 401 for every request, which
     * would otherwise take the whole source offline even though anonymous access
     * still works fine. On a 401 the request is retried without credentials and the
     * token is flagged so the user can fix it.
     */
    private suspend fun get(url: String, retries: Int = 3): String {
        val token = activeToken()
        if (token == null) return Net.getString(url, headers(null), retries)
        return try {
            Net.getString(url, headers(token), retries)
        } catch (e: HttpException) {
            if (e.code != 401) throw e
            tokenIsBad = true
            onTokenRejected()
            Net.getString(url, headers(null), retries)
        }
    }

    /** Called when the user saves a new token, so a previous rejection is forgotten. */
    fun onTokenChanged() { tokenIsBad = false }

    override suspend fun featured(): List<AppItem> = query(
        "topic:android-app+stars:%3E200&sort=stars&order=desc&per_page=40",
        filterNoise = true,
    )

    override suspend fun search(query: String): List<AppItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        // One request, not two: the previous topic-constrained query plus fallback
        // burned two of the ten searches a minute that anonymous callers get.
        // Ranking happens locally, so a broad query costs nothing in quality.
        return query(
            "$encoded+fork:false&sort=stars&order=desc&per_page=30",
            filterNoise = false,
            retries = 1,
        )
    }

    private suspend fun query(
        rawQuery: String,
        filterNoise: Boolean,
        retries: Int = 3,
    ): List<AppItem> {
        val body = get("https://api.github.com/search/repositories?q=$rawQuery", retries)
        val parsed = Net.decodeOrNull<GhSearch>(body) ?: return emptyList()
        return parsed.items
            .filter { !filterNoise || looksLikeAndroidApp(it.name, it.description, it.topics) }
            .map { it.toAppItem() }
    }

    override suspend fun resolve(item: AppItem): AppItem {
        val repo = item.sourceCodeUrl?.removePrefix("https://github.com/") ?: return item
        releaseCache[repo]?.let { return it }

        val body = runCatching {
            get("https://api.github.com/repos/$repo/releases/latest")
        }.getOrElse { return item }

        val release = Net.decodeOrNull<GhRelease>(body) ?: return item
        val asset = pickBestApk(release.assets.map { ReleaseAsset(it.name, it.url, it.size) })

        val resolved = item.copy(
            version = release.tagName?.removePrefix("v") ?: item.version,
            downloadUrl = asset?.url,
            sizeBytes = asset?.size ?: 0L,
            changelog = release.body?.take(4000) ?: item.changelog,
            updatedAt = release.publishedAt.isoToEpochMillis().takeIf { it > 0 } ?: item.updatedAt,
            needsReleaseLookup = false,
        )
        releaseCache[repo] = resolved
        return resolved
    }

    private fun GhRepo.toAppItem(): AppItem = AppItem(
        id = "${SourceId.GitHub.name}:$fullName",
        source = SourceId.GitHub,
        name = name,
        summary = description.orEmpty().take(240),
        description = description.orEmpty(),
        iconUrl = owner?.iconUrl,
        author = owner?.login,
        license = license?.spdxId,
        categories = topics.take(6),
        stars = stars,
        forks = forks,
        updatedAt = pushedAt.isoToEpochMillis(),
        website = homepage?.takeIf { it.isNotBlank() },
        sourceCodeUrl = htmlUrl,
        needsReleaseLookup = true,
    )

    // ------------------------------------------------------------------ dtos

    @Serializable
    private data class GhSearch(val items: List<GhRepo> = emptyList())

    @Serializable
    private data class GhRepo(
        val name: String = "",
        @SerialName("full_name") val fullName: String = "",
        val description: String? = null,
        @SerialName("stargazers_count") val stars: Int = 0,
        // Feeds the trust score via AppItem.forks; see the note there.
        @SerialName("forks_count") val forks: Int = 0,
        @SerialName("html_url") val htmlUrl: String = "",
        @SerialName("pushed_at") val pushedAt: String? = null,
        val homepage: String? = null,
        val owner: GhOwner? = null,
        val license: GhLicense? = null,
        val topics: List<String> = emptyList(),
    )

    @Serializable
    private data class GhOwner(
        val login: String = "",
        @SerialName("avatar_url") val avatarUrl: String? = null,
        /** "User" or "Organization" — see [iconUrl]. */
        val type: String = "",
    ) {
        /**
         * An organisation's avatar is a brand mark and passes for an app icon.
         * A user's avatar is a photograph of a person, and a grid mixing app
         * icons with contributors' selfies reads as a scraper, not a catalogue.
         * Returning null lets AppIcon draw its generated monogram instead.
         */
        /**
         * Shown as-is, owner or organisation alike.
         *
         * Filtering personal avatars left the grid almost entirely monograms —
         * most Android projects are published by individuals, not orgs — which
         * was consistent but duller and less recognisable. The monogram remains
         * the fallback for entries that genuinely carry no artwork.
         */
        val iconUrl: String?
            get() = avatarUrl?.takeIf { it.isNotBlank() }
    }

    @Serializable
    private data class GhLicense(@SerialName("spdx_id") val spdxId: String? = null)

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tagName: String? = null,
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<GhAsset> = emptyList(),
    )

    @Serializable
    private data class GhAsset(
        val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        val size: Long = 0L,
    )
}
