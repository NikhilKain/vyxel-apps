package com.vythera.vyxelapps.expressive.data.source

import com.vythera.vyxelapps.expressive.core.net.Net
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * WinGet (Windows), via the community winget.run API over the winget-pkgs manifests.
 *
 * Browsable only, like Flathub — the detail screen surfaces the `winget install`
 * command. WinGet manifests carry no icons, so the UI falls back to a generated
 * monogram tile for these entries.
 */
class WingetSource : AppSource {

    override val id = SourceId.WinGet

    private val base = "https://api.winget.run/v2"

    override suspend fun featured(): List<AppItem> {
        // The API rejects unknown sort/order keys with a 400; plain paging is the
        // only listing form it accepts.
        val body = Net.getString("$base/packages?page=1&take=40")
        return (Net.decodeOrNull<WgResponse>(body)?.packages).orEmpty().map { it.toAppItem() }
    }

    override suspend fun search(query: String): List<AppItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        val body = Net.getString("$base/packages?query=$encoded&take=30", retries = 1)
        return (Net.decodeOrNull<WgResponse>(body)?.packages).orEmpty().map { it.toAppItem() }
    }

    private fun WgPackage.toAppItem(): AppItem {
        val latest = this.latest
        return AppItem(
            id = "${SourceId.WinGet.name}:$packageId",
            source = SourceId.WinGet,
            name = latest?.name?.takeIf { it.isNotBlank() } ?: packageId.substringAfterLast('.'),
            summary = latest?.description.orEmpty().take(240),
            description = latest?.description.orEmpty(),
            packageName = packageId,
            author = latest?.publisher,
            license = latest?.license,
            categories = latest?.tags.orEmpty().take(6),
            version = versions.firstOrNull(),
            website = latest?.homepage,
            installCommand = "winget install --id $packageId",
        )
    }

    // ------------------------------------------------------------------ dtos
    // The API mixes PascalCase and camelCase keys; only the PascalCase set is
    // mapped here since that is what carries the package data.

    @Serializable
    private data class WgResponse(@SerialName("Packages") val packages: List<WgPackage> = emptyList())

    @Serializable
    private data class WgPackage(
        @SerialName("Id") val packageId: String = "",
        @SerialName("Versions") val versions: List<String> = emptyList(),
        @SerialName("Latest") val latest: WgLatest? = null,
    )

    @Serializable
    private data class WgLatest(
        @SerialName("Name") val name: String? = null,
        @SerialName("Publisher") val publisher: String? = null,
        @SerialName("Description") val description: String? = null,
        @SerialName("Homepage") val homepage: String? = null,
        @SerialName("License") val license: String? = null,
        @SerialName("Tags") val tags: List<String> = emptyList(),
    )
}
