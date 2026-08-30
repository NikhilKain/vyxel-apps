package com.vythera.vyxelapps.expressive.data.source

import com.vythera.vyxelapps.expressive.core.net.Net
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Flathub (Linux/Flatpak).
 *
 * These entries are browsable and searchable but not installable from Android — the
 * detail screen offers the `flatpak install` command and a link instead of a button.
 */
class FlathubSource : AppSource {

    override val id = SourceId.Flathub

    private val base = "https://flathub.org/api/v2"

    override suspend fun featured(): List<AppItem> {
        val body = Net.getString("$base/collection/popular?page=1&per_page=40")
        return (Net.decodeOrNull<FhResponse>(body)?.hits).orEmpty().map { it.toAppItem() }
    }

    suspend fun recentlyUpdated(): List<AppItem> {
        val body = Net.getString("$base/collection/recently-updated?page=1&per_page=30")
        return (Net.decodeOrNull<FhResponse>(body)?.hits).orEmpty().map { it.toAppItem() }
    }

    override suspend fun search(query: String): List<AppItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val payload = Net.json.encodeToString(FhSearchRequest(query = q))
        val body = Net.postJson("$base/search?page=1&per_page=40", payload)
        return (Net.decodeOrNull<FhResponse>(body)?.hits).orEmpty().map { it.toAppItem() }
    }

    private fun FhHit.toAppItem(): AppItem = AppItem(
        id = "${SourceId.Flathub.name}:$appId",
        source = SourceId.Flathub,
        name = name,
        summary = summary.orEmpty().take(240),
        description = description.orEmpty().stripHtml().take(6000),
        iconUrl = icon,
        packageName = appId,
        author = developerName,
        license = projectLicense?.substringBefore('='),
        categories = buildList {
            mainCategories?.let { add(it.replaceFirstChar(Char::uppercase)) }
            addAll(subCategories.orEmpty())
        }.take(6),
        updatedAt = (updatedAt ?: 0L) * 1000L,
        installs = installsLastMonth ?: 0L,
        verified = verified ?: false,
        website = "https://flathub.org/apps/$appId",
        installCommand = "flatpak install flathub $appId",
    )

    // ------------------------------------------------------------------ dtos

    @Serializable
    private data class FhSearchRequest(
        val query: String,
        val filters: List<String> = emptyList(),
    )

    @Serializable
    private data class FhResponse(val hits: List<FhHit> = emptyList())

    @Serializable
    private data class FhHit(
        val name: String = "",
        val summary: String? = null,
        val description: String? = null,
        @SerialName("app_id") val appId: String = "",
        val icon: String? = null,
        @SerialName("main_categories") val mainCategories: String? = null,
        @SerialName("sub_categories") val subCategories: List<String>? = null,
        @SerialName("developer_name") val developerName: String? = null,
        @SerialName("project_license") val projectLicense: String? = null,
        @SerialName("verification_verified") val verified: Boolean? = null,
        @SerialName("updated_at") val updatedAt: Long? = null,
        @SerialName("installs_last_month") val installsLastMonth: Long? = null,
    )
}
