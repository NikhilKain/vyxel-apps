package com.vythera.vyxelapps.expressive.data.source

import com.vythera.vyxelapps.expressive.core.net.Net
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Codeberg, via the Gitea API. The same implementation works against any Gitea or
 * Forgejo instance, which is why the base URL is a constructor parameter.
 */
class CodebergSource(
    private val baseUrl: String = "https://codeberg.org",
) : AppSource {

    override val id = SourceId.Codeberg

    private val releaseCache = mutableMapOf<String, AppItem>()

    // Four attempts, not two: Codeberg is a small volunteer-run instance and its repo
    // search sheds load with 503s several times a day. This runs in the background
    // behind an already-populated CDN rail, so spending a few extra seconds costs the
    // user nothing and is the difference between the rail refreshing and not.
    override suspend fun featured(): List<AppItem> =
        query("android", limit = 40, filterNoise = true, retries = 4)

    override suspend fun search(query: String): List<AppItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        // Codeberg's repo search is slow (30s+ is normal) and retries stack on that,
        // so interactive searches get one shot and a smaller page.
        return query(q, limit = 25, filterNoise = false, retries = 1)
    }

    private suspend fun query(
        term: String,
        limit: Int,
        filterNoise: Boolean,
        retries: Int,
    ): List<AppItem> {
        val encoded = java.net.URLEncoder.encode(term, "UTF-8")
        val body = Net.getString(
            "$baseUrl/api/v1/repos/search?q=$encoded&sort=stars&order=desc" +
                "&limit=$limit&is_private=false",
            retries = retries,
        )
        val parsed = Net.decodeOrNull<GiteaSearch>(body) ?: return emptyList()
        return parsed.data
            .filter { !filterNoise || looksLikeAndroidApp(it.name, it.description, emptyList()) }
            .map { it.toAppItem() }
    }

    override suspend fun resolve(item: AppItem): AppItem {
        val repo = item.sourceCodeUrl?.removePrefix("$baseUrl/") ?: return item
        releaseCache[repo]?.let { return it }

        val body = runCatching {
            Net.getString("$baseUrl/api/v1/repos/$repo/releases?limit=1")
        }.getOrElse { return item }

        val releases = Net.decodeOrNull<List<GiteaRelease>>(body) ?: return item
        val release = releases.firstOrNull() ?: return item
        val asset = pickBestApk(
            release.assets.map { ReleaseAsset(it.name, it.url, it.size) }
        )

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

    private fun GiteaRepo.toAppItem(): AppItem = AppItem(
        id = "${SourceId.Codeberg.name}:$fullName",
        source = SourceId.Codeberg,
        name = name,
        summary = description.orEmpty().take(240),
        description = description.orEmpty(),
        // The repo's own avatar first — that is a project mark. Only then the
        // owner's, and only when the owner is an organisation.
        iconUrl = avatarUrl?.takeIf { it.isNotBlank() } ?: owner?.iconUrl,
        author = owner?.login,
        stars = stars,
        updatedAt = updatedAt.isoToEpochMillis(),
        website = website?.takeIf { it.isNotBlank() },
        sourceCodeUrl = htmlUrl,
        needsReleaseLookup = true,
    )

    // ------------------------------------------------------------------ dtos

    @Serializable
    private data class GiteaSearch(val data: List<GiteaRepo> = emptyList())

    @Serializable
    private data class GiteaRepo(
        val name: String = "",
        @SerialName("full_name") val fullName: String = "",
        val description: String? = null,
        @SerialName("stars_count") val stars: Int = 0,
        @SerialName("html_url") val htmlUrl: String = "",
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        val website: String? = null,
        val owner: GiteaOwner? = null,
    )

    @Serializable
    private data class GiteaOwner(
        val login: String = "",
        @SerialName("avatar_url") val avatarUrl: String? = null,
        /** Gitea reports "organization" here; anything else is a person. */
        val type: String = "",
    ) {
        /**
         * Only an organisation avatar may stand in for an app icon. Gitea omits
         * the type on some search responses, and an unproven avatar is treated
         * as a person — a monogram is better than a stranger's face in a grid.
         */
        val iconUrl: String?
            get() = avatarUrl?.takeIf {
                it.isNotBlank() && type.equals("organization", ignoreCase = true)
            }
    }

    @Serializable
    private data class GiteaRelease(
        @SerialName("tag_name") val tagName: String? = null,
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<GiteaAsset> = emptyList(),
    )

    @Serializable
    private data class GiteaAsset(
        val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        val size: Long = 0L,
    )
}
