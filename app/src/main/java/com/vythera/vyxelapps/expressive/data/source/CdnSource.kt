package com.vythera.vyxelapps.expressive.data.source

import android.content.Context
import com.vythera.vyxelapps.api.AppEntry
import com.vythera.vyxelapps.api.MetadataManager
import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.Platform
import com.vythera.vyxelapps.expressive.data.model.SourceId

/**
 * The pre-aggregated CDN index, used as the first tier in front of the live APIs.
 *
 * The lookup order across the app is **CDN -> original source**. The CDN is a single
 * conditional GET against a static host that returns every source at once, so it
 * populates the whole home screen in one round trip instead of seven, and it keeps
 * working offline from its disk cache. The live sources then refine it: they carry
 * fields the index doesn't (screenshots, changelogs, exact APK URLs) and they're
 * always fresher than the last index build.
 *
 * This is not a [SourceId] of its own. Every entry keeps the identity of the source
 * it originally came from, so a CDN-sourced F-Droid app and a live F-Droid app are
 * the same thing to the rest of the app and deduplicate against each other.
 */
class CdnSource(context: Context) {

    private val client = MetadataManager.init(context)

    /** All CDN entries, grouped by the source they originally came from. */
    suspend fun byOriginalSource(): Map<SourceId, List<AppItem>> =
        runCatching { client.search("") }
            .getOrDefault(emptyList())
            .mapNotNull { it.toAppItem() }
            .groupBy { it.source }

    suspend fun search(query: String): List<AppItem> =
        runCatching { client.search(query) }
            .getOrDefault(emptyList())
            .mapNotNull { it.toAppItem() }

    /**
     * Root modules from the CDN's pre-built catalogue.
     *
     * Empty when the build has not published `modules.json` yet, which the caller
     * treats as "scrape the repositories live instead".
     */
    suspend fun modules(): List<AppItem> =
        runCatching { client.getModules() }
            .getOrDefault(emptyList())
            .mapNotNull { it.toAppItem() }

    /** One CDN module record as a catalogue entry. */
    private fun com.vythera.vyxelapps.api.ModuleEntry.toAppItem(): AppItem? {
        val moduleId = id.orEmptySafe().takeIf { it.isNotBlank() } ?: return null
        val mapped = moduleSourceIdOf(source.orEmptySafe())
        return AppItem(
            id = "${mapped.name}:$moduleId",
            source = mapped,
            name = name.orEmptySafe().ifBlank { moduleId },
            summary = summary.orEmptySafe().take(240),
            description = summary.orEmptySafe(),
            // The module id doubles as the package name, so hiding, dedupe and
            // "already installed" all key off the string the repos themselves use.
            packageName = moduleId,
            version = version.orEmptySafe().removePrefix("v").takeIf { it.isNotBlank() },
            author = author.orEmptySafe().removePrefix("@").takeIf { it.isNotBlank() },
            categories = listOf(family.orEmptySafe().ifBlank { "Magisk" }),
            downloadUrl = zipUrl.orEmptySafe().takeIf { it.isNotBlank() },
            stars = stars,
            sizeBytes = size,
            updatedAt = updated,
            website = homepage.orEmptySafe().takeIf { it.isNotBlank() },
            sourceCodeUrl = homepage.orEmptySafe().takeIf { it.isNotBlank() },
        )
    }

    /** The build writes Vyxel's own source ids; anything unknown lands on Alt Repo. */
    private fun moduleSourceIdOf(raw: String): SourceId =
        when (raw.lowercase().replace("-", "").replace("_", "")) {
            "googlers", "googlersrepo" -> SourceId.Googlers
            "xposed", "xposedrepo" -> SourceId.XposedRepo
            "magisklegacy", "magiskrepo" -> SourceId.MagiskLegacy
            else -> SourceId.MagiskAlt
        }

    /** Richer per-app record, when the index build produced one. */
    suspend fun detail(item: AppItem): AppItem? {
        val key = cdnKey(item) ?: return null
        val entry = client.getDetail(key) ?: return null
        val mapped = entry.toAppItem() ?: return null
        // Only fill gaps — never overwrite fields the live source already resolved.
        return item.copy(
            summary = item.summary.ifBlank { mapped.summary },
            description = item.description.ifBlank { mapped.description },
            iconUrl = item.iconUrl ?: mapped.iconUrl,
            version = item.version ?: mapped.version,
            downloadUrl = item.downloadUrl ?: mapped.downloadUrl,
            license = item.license ?: mapped.license,
            sourceCodeUrl = item.sourceCodeUrl ?: mapped.sourceCodeUrl,
            website = item.website ?: mapped.website,
        )
    }

    /** The CDN addresses details as "<source>:<package>". */
    private fun cdnKey(item: AppItem): String? {
        val pkg = item.packageName ?: return null
        val src = when (item.source) {
            SourceId.FDroid -> "fdroid"
            SourceId.IzzyOnDroid -> "izzyondroid"
            SourceId.GitHub -> "github"
            SourceId.GitLab -> "gitlab"
            SourceId.Codeberg -> "codeberg"
            SourceId.Flathub -> "flathub"
            SourceId.WinGet -> "winget"
            // Not in the CDN index. These sources are small and answer from their
            // own endpoint in one request, so there is nothing for the CDN to
            // pre-warm — and inventing a key here would send every module detail
            // lookup off to fetch a 404.
            SourceId.Aurora, SourceId.Aptoide, SourceId.ApkPure,
            SourceId.MagiskAlt, SourceId.Googlers,
            SourceId.XposedRepo, SourceId.MagiskLegacy -> return null
        }
        return "$src:$pkg"
    }

    /**
     * Every field is read through [orEmptySafe].
     *
     * Gson populates these via Unsafe allocation, so a JSON `null` — or a key the
     * index build omitted — lands as a real null inside a Kotlin `String` that the
     * type system swears is non-null. Calling `isBlank()` on one throws
     * "Parameter specified as non-null is null" and took out the whole CDN tier.
     */
    private fun AppEntry.toAppItem(): AppItem? {
        val mapped = sourceIdOf(source.orEmptySafe()) ?: return null
        val safeName = name.orEmptySafe()
        if (safeName.isBlank()) return null

        val safeApk = apkUrl.orEmptySafe()
        val safeSummary = summary.orEmptySafe()
        // Index ids are "<source>:<package>"; the package half is what matters.
        val pkg = id.orEmptySafe().substringAfter(':', "").takeIf { it.isNotBlank() }

        return AppItem(
            id = "${mapped.name}:${pkg ?: safeName}",
            source = mapped,
            name = safeName,
            summary = safeSummary.take(240),
            description = safeSummary,
            iconUrl = icon.orEmptySafe().takeIf { it.isNotBlank() },
            packageName = pkg,
            version = version.orEmptySafe().takeIf { it.isNotBlank() },
            license = license.orEmptySafe().takeIf { it.isNotBlank() },
            categories = categories ?: emptyList(),
            stars = stars,
            downloadUrl = safeApk.takeIf { it.isNotBlank() },
            website = homepage.orEmptySafe().takeIf { it.isNotBlank() },
            sourceCodeUrl = sourceCode.orEmptySafe().takeIf { it.isNotBlank() },
            // A repo entry with no APK in the index still needs a release lookup.
            // Anything installable that arrived without an APK needs resolving.
            //
            // This was `&& mapped.isRepoHost`, which was wrong once index.json
            // stopped carrying apk_url at all: F-Droid and IzzyOnDroid entries are
            // not repo hosts, so they were flagged as needing nothing, never
            // resolved, and showed no install button forever. Searching the same
            // app worked because live sources return a download URL directly.
            needsReleaseLookup = safeApk.isBlank() && mapped.platform == Platform.Android,
        )
    }

    /** Guards against Gson writing null into a declared non-null String. */
    @Suppress("USELESS_ELVIS")
    private fun String?.orEmptySafe(): String = this ?: ""

    /** The index has used a few spellings per source over time; accept them all. */
    private fun sourceIdOf(raw: String): SourceId? = when (raw.lowercase().replace("-", "")) {
        "fdroid" -> SourceId.FDroid
        "izzyondroid", "izzy" -> SourceId.IzzyOnDroid
        "github" -> SourceId.GitHub
        "gitlab" -> SourceId.GitLab
        "codeberg" -> SourceId.Codeberg
        "flathub" -> SourceId.Flathub
        "winget" -> SourceId.WinGet
        else -> null
    }
}
