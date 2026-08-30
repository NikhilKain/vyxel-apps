package com.vythera.vyxelapps.expressive.data.source

import com.vythera.vyxelapps.expressive.core.net.Net
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Aptoide — the third-party store that actually carries mainstream apps.
 *
 * This is the source that answers "where is Instagram". Every other source Vyxel
 * aggregates is open-source-only by construction: F-Droid and IzzyOnDroid build from
 * source and will not package a proprietary app, and the repo hosts only have what
 * their authors pushed. Aptoide is a general store, so it has the apps people
 * actually ask for by name, with direct APK downloads.
 *
 * **Only TRUSTED entries are shown.** Aptoide's catalogue is user-uploaded into
 * per-user "stores", which is exactly the property that makes it broad and exactly
 * the property that makes an unfiltered version of it irresponsible to put in front
 * of anyone. Every app carries a `malware.rank` from Aptoide's own scanning
 * pipeline, and anything not ranked TRUSTED is dropped here rather than shown with a
 * warning — a store that surfaces something it cannot vouch for has already failed,
 * whatever the label next to it says.
 *
 * The APK's signing certificate owner comes back in the same payload and is carried
 * through to the detail page, so the reader can see that the Instagram they are about
 * to install is signed by Instagram and not by whoever uploaded it.
 */
class AptoideSource : AppSource {

    override val id: SourceId = SourceId.Aptoide

    private companion object {
        const val API = "https://ws75.aptoide.com/api/7"

        /** Aptoide's own verdict. Anything else is not shown at all. */
        const val TRUSTED = "TRUSTED"

        const val SEARCH_LIMIT = 30
    }

    /**
     * Nothing. See [SourceId.searchOnly].
     *
     * Aptoide's `listApps` endpoint omits `malware` entirely, so entries from it
     * cannot clear the TRUSTED gate this source is built on — and its most-downloaded
     * list is preinstalled system packages anyway. Rather than relax the gate for the
     * home screen, this source simply does not appear there.
     */
    override suspend fun featured(): List<AppItem> = emptyList()

    override suspend fun search(query: String): List<AppItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
        val body = Net.getString("$API/apps/search?query=$encoded&limit=$SEARCH_LIMIT", retries = 2)
        parseList(body)
    }

    /**
     * Pulls the store page for one app: real description and screenshots.
     *
     * The list endpoints carry no prose at all, so without this an Aptoide entry
     * opens to a detail screen with a title and nothing to read.
     */
    override suspend fun resolve(item: AppItem): AppItem = withContext(Dispatchers.IO) {
        val pkg = item.packageName?.takeIf { it.isNotBlank() } ?: return@withContext item
        runCatching {
            val body = Net.getString("$API/app/getMeta?package_name=$pkg", retries = 1)
            val node = Net.json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
                ?: return@runCatching item

            val media = node["media"]?.jsonObject
            val shots = media?.get("screenshots")?.jsonArray.orEmpty()
                .mapNotNull { runCatching { it.jsonObject.str("url") }.getOrNull() }

            item.copy(
                description = media.str("description") ?: item.description,
                screenshots = shots.ifEmpty { item.screenshots },
            )
        }.getOrDefault(item)
    }

    private fun parseList(body: String): List<AppItem> {
        val list = runCatching {
            Net.json.parseToJsonElement(body).jsonObject["datalist"]?.jsonObject
                ?.get("list")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return list.mapNotNull { element ->
            val entry = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val pkg = entry.str("package") ?: return@mapNotNull null
            val file = entry["file"]?.jsonObject

            // The safety gate. Not a warning badge — an exclusion.
            val rank = file?.get("malware")?.jsonObject.str("rank")
            if (!rank.equals(TRUSTED, ignoreCase = true)) return@mapNotNull null

            val download = file.str("path") ?: file.str("path_alt") ?: return@mapNotNull null
            val signer = file?.get("signature")?.jsonObject.str("owner")

            AppItem(
                id = "Aptoide:$pkg",
                source = SourceId.Aptoide,
                name = entry.str("name") ?: pkg,
                // Populated properly by resolve(); until then the signer is the most
                // useful thing that can be said about a proprietary binary.
                summary = signer?.let { "Signed by ${it.commonName()}" }.orEmpty(),
                description = "",
                iconUrl = entry.str("icon"),
                packageName = pkg,
                version = file.str("vername"),
                versionCode = file?.num("vercode") ?: 0L,
                updatedAt = entry.str("updated").toEpochMillisOrZero(),
                author = entry["developer"]?.jsonObject.str("name"),
                downloadUrl = download,
                sizeBytes = entry.num("size") ?: file?.num("filesize") ?: 0L,
                installs = entry["stats"]?.jsonObject.num("downloads") ?: 0L,
                // Only TRUSTED entries reach here, so the badge is honest by
                // construction rather than by a second guess.
                verified = true,
                screenshots = listOfNotNull(entry.str("graphic")),
            )
        }
    }
}

/**
 * The human-readable half of an X.500 distinguished name.
 *
 * Certificates come back as `CN=Kevin Systrom, O=Instagram Inc, L=San Francisco, …`.
 * The organisation is what a reader wants to check; failing that the common name.
 */
private fun String.commonName(): String {
    fun field(key: String) = split(',')
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return field("O") ?: field("CN") ?: this
}

/** Aptoide publishes `"2026-08-26 04:54:17"` in UTC. */
private fun String?.toEpochMillisOrZero(): Long {
    if (this.isNullOrBlank()) return 0L
    return runCatching {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        format.parse(this)?.time ?: 0L
    }.getOrDefault(0L)
}

private fun JsonObject?.str(key: String): String? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        ?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject?.num(key: String): Long? =
    this?.get(key)?.let { element ->
        runCatching { element.jsonPrimitive }.getOrNull()?.let { primitive ->
            primitive.longOrNull ?: primitive.contentOrNull?.trim()?.toDoubleOrNull()?.toLong()
        }
    }
