package com.vythera.vyxelapps

import android.app.Application
import com.vythera.vyxelapps.api.AppEntry
import com.vythera.vyxelapps.api.MetadataManager
import com.vythera.vyxelapps.R
import kotlinx.coroutines.isActive
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vythera.vyxelapps.installer.ApkVerifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
// The AppItem-receiver extension from the Expressive bridge. Distinct from the
// AppEntry one defined in this file — Kotlin picks by receiver type.
import com.vythera.vyxelapps.expressive.data.toGitHubRepo
import com.vythera.vyxelapps.expressive.data.toAppItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import com.vythera.vyxelapps.updater.AppScanResult
import com.vythera.vyxelapps.updater.ScanLink
import com.vythera.vyxelapps.updater.UpdateScanEngine
import java.io.File

enum class AppPlatform(val label: String, val emoji: String = "", val iconRes: Int? = null) {
    ALL("All", "⬡"), ANDROID("Android", iconRes = R.drawable.ic_android_logo),
    WINDOWS("Windows", iconRes = R.drawable.ic_windows_logo), LINUX("Linux", iconRes = R.drawable.ic_linux_logo), TV("TV", iconRes = R.drawable.ic_tv_logo),
    IOS("iOS", iconRes = R.drawable.ic_ios_logo)
}

enum class AppSource(val label: String, val colorHex: Long) {
    GITHUB   ("GitHub",      0xFF24292EL),
    GITLAB   ("GitLab",      0xFFFC6D26L),
    CODEBERG ("Codeberg",    0xFF2185D0L),
    FDROID   ("F-Droid",     0xFF1976D2L),
    IZZY     ("IzzyOnDroid", 0xFF0D47A1L),
    FLATHUB  ("Flathub",     0xFFEEEEEEL),
    WINGET   ("Winget",      0xFFFFD966L),

    /**
     * The two sources Classic gained alongside the Expressive shell.
     *
     * Safe to append: `GitHubRepo.source` is nullable, so every `when` over it in the
     * Classic UI already has an `else` branch and none of them stop compiling.
     */
    APTOIDE  ("Aptoide",     0xFFF57C00L),
    MODULE   ("Module",      0xFF9B6BDFL)
}

data class GitHubRepo(
    val id: Long = 0, val name: String = "", val full_name: String = "",
    val description: String? = null, val stargazers_count: Int = 0,
    val forks_count: Int = 0, val html_url: String = "",
    val owner: RepoOwner = RepoOwner(), val language: String? = null,
    val updated_at: String = "",
    val source: AppSource? = AppSource.GITHUB,
    val apkUrl: String = "",
    val cdnVersion: String = "",
    // Upstream VCS repo (F-Droid/Izzy carry this): used to find screenshots in the
    // real project README when the store page itself has none reachable.
    val sourceCodeUrl: String = "",
    // Android package id from the CDN entry (F-Droid/Izzy). full_name is NOT this
    // for Izzy — it's owner/repo — so the F-Droid-style packages API needs this
    // field, and the installer uses it as the expected package to verify against.
    val packageId: String = "",
    // Download size in bytes, when the catalogue entry already knows it.
    //
    // Sources that hand over a direct APK URL (Aptoide, the CDN, modules) have no
    // GitHub release to read an asset size from, so `fetchRelease` synthesises one —
    // and it used to synthesise `size = 0`, which the detail page rendered literally
    // as "0 B" next to the version. Carrying the number the search response already
    // returned is cheaper than a HEAD request and correct before the download starts.
    //
    // A primitive Long, so Gson's Unsafe allocation leaves it 0 rather than null on
    // entries cached before this field existed. See the nullability note on
    // InstallHistoryEntry.iconUrl for why that distinction matters.
    val apkSize: Long = 0L,
)
/**
 * [type] is "User" or "Organization" as GitHub reports it, and it decides
 * whether [avatar_url] may stand in for an app icon.
 *
 * An organisation's avatar is a brand mark and reads as an icon. A user's avatar
 * is a photograph of a person, and a store whose grid mixes app icons with
 * contributors' selfies looks like a scraper rather than a catalogue. F-Droid
 * and the CDN supply genuine icons and set this to a blank/organisation value.
 */
data class RepoOwner(
    val login: String = "",
    val avatar_url: String = "",
    val type: String = "",
)

/**
 * The artwork to draw for this entry, or null to fall back to a monogram.
 *
 * Never returns a personal avatar. Callers must handle null rather than showing
 * a grey box — see [VyxelAppIcon].
 */
/**
 * Drops the entries whose package the user has hidden.
 *
 * Entries with no package id are always kept: a GitHub repo that never resolved to
 * an Android package cannot be the app someone hid, and silently dropping it would
 * make the hidden list look like it were eating unrelated results.
 */
fun List<GitHubRepo>.withoutHidden(hidden: Set<String>): List<GitHubRepo> =
    if (hidden.isEmpty()) this
    else filter { repo -> repo.packageId.isBlank() || repo.packageId !in hidden }

val GitHubRepo.iconUrlOrNull: String?
    get() {
        val url = owner.avatar_url
        if (url.isBlank()) return null
        // GitHub avatars are shown as-is, owner or organisation alike. Filtering
        // personal ones left the home screen almost entirely monograms, because
        // most Android projects are published by individuals rather than orgs —
        // a consistent grid, but a duller and less recognisable one. Product
        // call: a real picture beats a generated letter.
        if (source == null || source == AppSource.GITHUB) return url
        if (owner.type.equals("Organization", ignoreCase = true)) return url
        // Other repo hosts keep the filter — their avatars are far more often a
        // plain photograph, and they supply a small share of the catalogue.
        val isAvatarHost = url.contains("gravatar.com") ||
            url.contains("/avatars/") ||
            url.contains("gitlab.com/uploads/-/system/user/")
        return if (isAvatarHost) null else url
    }

/**
 * A human-readable name, even when upstream only gave us a package id.
 *
 * Unresolved entries were reaching the hero carousel as literal package names —
 * "com.harsh.shah…" as a featured title. Anything that looks like an id is
 * rewritten to its last segment, spaced and title-cased.
 */
val GitHubRepo.displayName: String
    get() {
        val raw = name.ifBlank { full_name.substringAfterLast('/') }
        if (!raw.matches(Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+){2,}$"))) return raw
        return raw.substringAfterLast('.')
            .replace('_', ' ')
            .split(' ')
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
data class SearchResponse(val items: List<GitHubRepo> = emptyList())
data class Release(
    val tag_name: String = "", val name: String? = "",
    val assets: List<ReleaseAsset> = emptyList(),
    val published_at: String? = "",
    // GitHub/Gitea return null for releases with no notes; Gson injects that null
    // over the Kotlin default, so these MUST be nullable to avoid NPE crashes.
    val body: String? = "",
    val prerelease: Boolean = false
)
data class ReleaseAsset(
    val name: String = "", val browser_download_url: String = "",
    val size: Long = 0, val content_type: String = ""
)

// ── Robust APK recognition ──────────────────────────────────────────────────
// An asset is an installable APK if its name OR its download-URL path ends in
// ".apk", OR it carries the Android package content_type. This covers GitLab /
// Codeberg / CDN assets where `name` is a human label ("Download APK") and the
// real ".apk" lives only in browser_download_url, plus blank-name CDN entries.
fun ReleaseAsset.isApk(): Boolean {
    val urlPath = browser_download_url.trim()
        .substringBefore('?')   // strip query (signed URLs, GitLab permalinks)
        .substringBefore('#')
    return name.trim().endsWith(".apk", ignoreCase = true) ||
           urlPath.endsWith(".apk", ignoreCase = true) ||
           content_type.equals("application/vnd.android.package-archive", ignoreCase = true)
}

/** The HTTP status behind a Retrofit failure, or null when it wasn't an HTTP error. */
internal fun githubHttpCode(e: Exception?): Int? =
    (e as? retrofit2.HttpException)?.code()

/**
 * What to tell the user when no release could be resolved.
 *
 * "No releases found." is a claim about the repo, and it must only be made when
 * GitHub actually answered. When the lookup failed instead — 403 for a spent rate
 * limit, 429, or no network — saying the repo has no releases is simply false, and
 * it is the reason an app with published APKs would appear to have none. Each case
 * now names itself, and the rate-limit case says when the budget comes back so the
 * user knows to wait rather than concluding the app is broken.
 */
internal fun releaseLookupError(failure: Exception?): String {
    if (failure == null) return "No releases found."
    return when (githubHttpCode(failure)) {
        403, 429 -> {
            val core = com.vythera.vyxelapps.api.GitHubRateLimit
                .buckets.value[com.vythera.vyxelapps.api.GitHubRateLimit.CORE]
            val wait = core?.secondsUntilReset()
            if (wait != null && wait > 0)
                "GitHub rate limit reached. Try again in ${formatWait(wait)}, or add a Personal Access Token in Settings to raise the limit."
            else
                "GitHub rate limit reached. Add a Personal Access Token in Settings to raise the limit."
        }
        401 -> "GitHub rejected the saved token. Replace or clear it in Settings."
        404 -> "No releases found."
        null -> "Couldn't reach GitHub. Check your connection and try again."
        else -> "GitHub returned an error (${githubHttpCode(failure)}). Try again shortly."
    }
}

/**
 * "45s" / "12 min" — short enough to sit inside an error line.
 *
 * Rounds to the nearest minute rather than up: a ceiling turns 2 min 1 s into "3 min",
 * which overstates the wait by almost a whole minute at exactly the moment the user is
 * deciding whether to wait at all. Below 90 s the seconds are shown outright, so the
 * minute form never has to represent a value small enough for the rounding to mislead.
 */
internal fun formatWait(seconds: Long): String =
    if (seconds < 90) "${seconds}s" else "${(seconds + 30) / 60} min"

// Matchable text for arch/variant detection — name + URL path, lower-cased.
// Lets ABI/universal matching work even when `name` is blank.
private fun ReleaseAsset.matchText(): String =
    (name + " " + browser_download_url.substringBefore('?').substringBefore('#')).lowercase()

data class InstallState(
    val isLoadingRelease : Boolean = false,
    val release          : Release? = null,
    val releases         : List<Release> = emptyList(),
    val apkAsset         : ReleaseAsset? = null,
    val smartInstall     : SmartInstallResult? = null,
    val trustScore       : TrustScore? = null,
    val downloadProgress : Float? = null,
    val isInstalled      : Boolean = false,
    val packageName      : String? = null,
    val error            : String? = null,
    val downloadId       : Long? = null,
    val repo             : GitHubRepo? = null,
    /** Set once a downloaded APK has been through [ApkVerifier]; drives the Integrity card. */
    val verification     : ApkVerifier.Result? = null,
    val isVerifying      : Boolean = false
)

data class UserProfile(
    val name     : String = "",
    val age      : String = "",
    val email    : String = "",
    val photoUri : String = "",
    val coverUri : String = ""
)
data class HistoryItem(val repo: GitHubRepo, val viewedAt: Long = System.currentTimeMillis())
data class AppSettings(
    val language                : String  = "English",
    val githubToken             : String  = "",
    val sortBy                  : String  = "Stars",
    val fontName                : String  = "Default",
    // First-install default: M3 Expressive Light theme (seed #6750A4 · purple accent)
    val themeMode               : String  = "Light",
    val amoledBlack             : Boolean = false,
    val liquidGlassWallpaperUri : String  = "",
    val liquidGlassBlur         : Float   = 7f,
    val liquidGlassEdge         : Float   = 1.0f,
    val liquidGlassRefraction   : Float   = 1.0f,
    val liquidGlassNavBlur      : Float   = 7f,
    val liquidGlassNavEdge      : Float   = 1.0f,
    val liquidGlassNavRefraction: Float   = 1.0f,
    val showPreReleases           : Boolean = false,
    val trackedApps               : List<TrackedApp> = emptyList(),
    val hasSeenOnboarding         : Boolean = false,
    val liquidGlassNavTextColor   : String  = "",
    val cyberpunkEffects          : Boolean = true,  // CYBERPUNK: grid/scanlines/particles
    // Which shell to render: "Classic" (this UI) or "Expressive" (M3 Expressive).
    // Mirrored into UiStylePrefs so MainActivity can read it before the first frame.
    val uiStyle                   : String  = "Classic"
)

// User-editable custom theme — accent is required, extra fields override auto-derived colors
data class CustomThemeData(
    val accentHex      : String  = "#D0BCFF",
    val isDark         : Boolean = true,
    val bgHex          : String  = "",   // "" = auto-derived from accent
    val surfaceHex     : String  = "",
    val onSurfaceHex   : String  = "",
    val secondaryHex   : String  = "",
    val tertiaryHex    : String  = ""
)

data class InstallHistoryEntry(
    val repoId      : Long,
    val repoName    : String,
    val ownerLogin  : String,
    val tagName     : String,
    val apkPath     : String,
    val installedAt : Long    = System.currentTimeMillis(),
    val packageName : String  = "",
    val source      : String? = null, // "github","fdroid","flathub","gitlab","codeberg","izzy","winget"
    /**
     * Artwork for the installed-apps list.
     *
     * The list previously rendered a generated monogram for every entry, because a
     * history record carried no icon and nothing re-joined it to the catalogue. New
     * entries capture the icon at install time; older ones keep their monogram.
     *
     * Nullable, like [source] above, and for the same reason: this history is
     * persisted with Gson, which allocates through `Unsafe` and never runs the Kotlin
     * constructor. A field absent from stored JSON therefore arrives as *null*
     * regardless of its declared default — so a non-null `String` here is a lie the
     * compiler believes, and the first `isNotBlank()` on a record written before this
     * field existed takes the whole app down on launch.
     */
    val iconUrl     : String? = null
)

data class CustomRepo(
    val id      : String = java.util.UUID.randomUUID().toString(),
    val name    : String = "",
    val url     : String = "",
    val iconUri : String = ""
)

data class TrackedApp(
    val id          : String = java.util.UUID.randomUUID().toString(),
    val packageName : String,
    val appName     : String,
    val repoFullName: String,  // "user/repo"
    val repoUrl     : String
)

data class UpdateInfo(
    val repoId     : Long,
    val repoName   : String,
    val currentTag : String,
    val latestTag  : String,
    val changelog  : String
)

data class SelfUpdateInfo(
    val latestVersion : String,
    val apkUrl        : String,
    val changelog     : String
)

/**
 * Apps pinned to the top of the home hero, served from the CDN's featured.json.
 *
 * Promotion is deliberately CDN-driven rather than hardcoded: a pin baked into the
 * APK could only change with a release, which makes it useless for promoting
 * anything. Edit featured.json in the appstore-metadata repo and every install
 * picks it up on next launch.
 *
 * Entries carry their own metadata rather than referencing the catalog, so a pinned
 * app shows up even when it isn't indexed yet and even when the source that hosts it
 * is having a bad day.
 */
data class FeaturedPins(
    val active : Boolean            = false,
    // Same client-enforced expiry as Announcement, and for the same reason: the
    // repo's workflow and Pages caching both lag, so the client decides.
    val expiresAt : String          = "",
    val apps   : List<FeaturedPin>  = emptyList()
) {
    val isExpired: Boolean
        get() = expiresAt.isNotBlank() && runCatching {
            java.time.OffsetDateTime.parse(expiresAt).toInstant().toEpochMilli() <
                System.currentTimeMillis()
        }.getOrDefault(false)
}

data class FeaturedPin(
    val repo        : String = "",   // "owner/name" on GitHub
    val name        : String = "",
    val packageName : String = "",
    val summary     : String = "",
    val iconUrl     : String = "",
    val apkUrl      : String = "",
    val version     : String = "",
    val license     : String = "",
    // Shown in place of "FEATURED" on the card. Keep it truthful: these slots are
    // promotion, and a store that hides that stops being trusted as a catalogue.
    val label       : String = "",
    /**
     * External listing for an app Vyxel cannot install itself.
     *
     * Some apps only exist on Google Play, so there is no APK to fetch and no version
     * to track. Rather than drop them, the card opens the listing — the same choice
     * the detail screen already makes for Flathub and WinGet entries, which show a
     * copyable install command instead of a dead Install button.
     *
     * When set, [apkUrl] is expected to be empty and the card is tap-to-open.
     */
    val storeUrl    : String = ""
) {
    /** True when this pin links out instead of installing. */
    val isExternal: Boolean get() = apkUrl.isBlank() && storeUrl.isNotBlank()

    fun toGitHubRepo(): GitHubRepo {
        val owner = repo.substringBefore('/', "")
        val short = repo.substringAfter('/', "").ifBlank { name }
        return GitHubRepo(
            id               = kotlin.math.abs("github:$repo".hashCode()).toLong(),
            name             = name.ifBlank { short },
            full_name        = repo,
            description      = summary,
            html_url         = "https://github.com/$repo",
            owner            = RepoOwner(login = owner, avatar_url = iconUrl),
            source           = AppSource.GITHUB,
            apkUrl           = apkUrl,
            cdnVersion       = version,
            sourceCodeUrl    = "https://github.com/$repo",
            packageId        = packageName
        )
    }
}

// Served from the CDN's announcement.json — shown once per announcement on app
// open. Edit announcement.json in the appstore-metadata repo to publish one; set
// active=false to retire it.
data class Announcement(
    val id          : String  = "",
    val active      : Boolean = false,
    val title       : String  = "",
    val message     : String  = "",
    val actionUrl   : String  = "",
    val actionLabel : String  = "",
    val dismissible : Boolean = true,
    val accentHex   : String  = "",
    // When the announcement stops being shown, ISO-8601 with an offset — either
    // "2026-08-20T16:00:00+05:30" (IST, no mental arithmetic) or the equivalent
    // "2026-08-20T10:30:00Z". Blank means it runs until switched off by hand.
    //
    // The client enforces this itself rather than trusting the CDN: the repo's
    // expire workflow only runs hourly and GitHub Pages caches for minutes on
    // top of that, so a server-only expiry would leave the banner up past the
    // deadline — and would never expire at all if Actions were disabled.
    val expiresAt   : String  = "",
    // Optional artwork. When set, the banner renders as the image itself and
    // tapping anywhere on it opens [actionUrl]; title/message become optional
    // captions. Must be an https URL — host it in the appstore-metadata repo so
    // it is served from the same CDN as this file.
    val imageUrl    : String  = "",
    // Width:height of the artwork, used to reserve the right space before the
    // image loads so the dialog doesn't jump. Defaults to a 16:9 banner.
    val imageAspect : Float   = 16f / 9f
) {
    /**
     * What "already seen" is keyed on.
     *
     * This used to be the raw [id]. That made editing an announcement a silent
     * no-op: anyone who had dismissed the previous text kept the id in their
     * seen-set forever, so the new message never appeared and the only fix was
     * remembering to also change the id. Hashing the visible content means any
     * real edit re-shows, while relaunching with unchanged text stays dismissed.
     */
    val signature: String
        get() = "$id|$title|$message|$imageUrl".hashCode().toString()

    /** Only https artwork is honoured; anything else falls back to the text banner. */
    val hasImage: Boolean
        get() = imageUrl.startsWith("https://", ignoreCase = true)

    /**
     * True once [expiresAt] has passed. Deliberately fails *open* (shows the
     * banner) when the stamp is missing or unparseable — a typo in a date should
     * not silently blank a live campaign, and the author still has `active`.
     */
    val isExpired: Boolean
        get() {
            if (expiresAt.isBlank()) return false
            return runCatching {
                java.time.OffsetDateTime.parse(expiresAt.trim())
                    .toInstant()
                    .isBefore(java.time.Instant.now())
            }.getOrDefault(false)
        }
}

// Exported/imported via Settings → Backup & Restore. Fields are nullable because
// Gson bypasses Kotlin defaults and injects null for anything missing in the file.
data class VyxelBackup(
    val version        : Int  = 1,
    val exportedAt     : Long = 0L,
    val favourites     : List<GitHubRepo>?          = null,
    val trackedApps    : List<TrackedApp>?          = null,
    val customRepos    : List<CustomRepo>?          = null,
    val installHistory : List<InstallHistoryEntry>? = null
)

// Daily-rotating home picks. `date` is "YYYY-MM-DD" — picks stay stable for a
// calendar day even across refreshes/restarts, so the widget and app agree.
data class TodayPicks(
    val appOfTheDay : GitHubRepo? = null,
    val hiddenGem   : GitHubRepo? = null,
    val date        : String      = ""
)

data class ReadmeResponse(
    val content  : String = "",
    val encoding : String = ""
)

enum class LicenseVerifyState { IDLE, LOADING, SUCCESS, INVALID, ERROR_EXHAUSTED, ERROR_NETWORK }


/**
 * Live state of a module flash, driving Classic's console dialog.
 *
 * The root manager's own stdout is the content rather than a spinner: module
 * installers print their compatibility checks and their reasons for refusing, and
 * reducing that to a verdict turns "your kernel is too old" into a silent failure.
 */
data class ModuleInstallUi(
    val name: String,
    val lines: List<String> = emptyList(),
    val running: Boolean = true,
    val success: Boolean? = null,
)

data class UiState(
    val refreshToken           : Int                     = 0,
    val trending               : List<GitHubRepo>        = emptyList(),
    val media                  : List<GitHubRepo>        = emptyList(),
    val tools                  : List<GitHubRepo>        = emptyList(),
    val games                  : List<GitHubRepo>        = emptyList(),
    val browsers               : List<GitHubRepo>        = emptyList(),
    val productivity           : List<GitHubRepo>        = emptyList(),
    val security               : List<GitHubRepo>        = emptyList(),
    val devtools               : List<GitHubRepo>        = emptyList(),
    val photoVideo             : List<GitHubRepo>        = emptyList(),
    val music                  : List<GitHubRepo>        = emptyList(),
    val finance                : List<GitHubRepo>        = emptyList(),
    val education              : List<GitHubRepo>        = emptyList(),
    val fitness                : List<GitHubRepo>        = emptyList(),
    val artDesign              : List<GitHubRepo>        = emptyList(),
    val news                   : List<GitHubRepo>        = emptyList(),
    val social                 : List<GitHubRepo>        = emptyList(),
    val cloudStorage           : List<GitHubRepo>        = emptyList(),
    val cooking                : List<GitHubRepo>        = emptyList(),
    val platformApps           : List<GitHubRepo>        = emptyList(),

    // ── Root modules ────────────────────────────────────────────────────────
    //
    // Loaded on demand when the Modules screen opens rather than at startup: the
    // CDN copy is one conditional GET, but the live fallback is expensive and most
    // sessions never open that screen.
    val modules                : List<GitHubRepo>        = emptyList(),
    val isLoadingModules       : Boolean                 = false,
    /** Live console for a module flash; null when no install is in progress. */
    val moduleInstall          : ModuleInstallUi?        = null,

    /**
     * Packages the user has hidden, matched across every source.
     *
     * Read from the same store the Expressive shell writes, so hiding an app in one
     * interface hides it in the other — a per-shell hidden list would mean the app
     * the user just dismissed reappears the moment they switch. Matching is on
     * package name for the same reason it is there: four indexes carry copies of the
     * same app, and hiding one copy is not what "hide" means in an aggregator.
     */
    val hiddenPackages         : Set<String>             = emptySet(),

    val searchResults          : List<GitHubRepo>        = emptyList(),
    val recommendations        : List<GitHubRepo>        = emptyList(),
    val seeAllTitle            : String                  = "",
    val seeAllQuery            : String                  = "",
    val seeAllApps             : List<GitHubRepo>        = emptyList(),
    val seeAllPage             : Int                     = 1,
    val seeAllSource           : String?                 = null,
    val isLoadingSeeAll        : Boolean                 = false,
    val isLoading              : Boolean                 = false,
    val isLoadingMore          : Boolean                 = false,
    val error                  : String?                 = null,
    val searchQuery            : String                  = "",
    val platform               : AppPlatform            = AppPlatform.ALL,
    val trendingPage           : Int                     = 1,
    val profile                : UserProfile             = UserProfile(),
    val history                : List<HistoryItem>       = emptyList(),
    val settings               : AppSettings             = AppSettings(),
    val themeName              : ThemeName               = ThemeName.DARK,
    val accentColor            : Color?                  = null,
    val customTheme            : CustomThemeData         = CustomThemeData(),
    val categoryViewCounts     : Map<String, Int>        = emptyMap(),
    val translatedDescriptions  : Map<Long, String>       = emptyMap(),
    val translatedReadmes       : Map<Long, String>       = emptyMap(),
    val isTranslating           : Map<Long, Boolean>      = emptyMap(),
    val translatedReleaseBodies : Map<Long, String>       = emptyMap(),
    val isTranslatingRelease    : Map<Long, Boolean>      = emptyMap(),
    val notifsDismissed         : Boolean                 = false,
    val favourites             : List<GitHubRepo>        = emptyList(),
    val githubUsername         : String                  = "",
    val installHistory         : List<InstallHistoryEntry> = emptyList(),
    val ignoredVersions        : Set<String>             = emptySet(),
    val updates                : List<UpdateInfo>        = emptyList(),
    val screenshots            : Map<Long, List<String>> = emptyMap(),
    val isCheckingUpdates      : Boolean                 = false,
    val compareTargetRepo      : GitHubRepo?             = null,
    val isSearching            : Boolean                 = false,
    val selectedSubCategories  : Set<String>             = emptySet(),
    val isFilterMenuOpen: Boolean = false,
    val activeSubMenuPlatform: AppPlatform? = null,
    val selectedSource  : AppSource?             = null,
    val gitlabApps      : List<GitHubRepo>        = emptyList(),
    val codebergApps    : List<GitHubRepo>        = emptyList(),
    val fdroidApps      : List<GitHubRepo>        = emptyList(),
    val izzyApps        : List<GitHubRepo>        = emptyList(),
    val flathubApps     : List<GitHubRepo>        = emptyList(),
    val wingetApps          : List<GitHubRepo>        = emptyList(),
    val selfUpdateInfo      : SelfUpdateInfo?         = null,
    val selfUpdateDismissed : Boolean                 = false,
    val customRepos         : List<CustomRepo>        = emptyList(),
    val readmes             : Map<Long, String>       = emptyMap(),
    val liquidGlassUnlocked : Boolean                = false,
    val licenseKeyInput     : String                 = "",
    val licenseVerifyState        : LicenseVerifyState     = LicenseVerifyState.IDLE,
    val multiSourceUpdates        : List<AppScanResult>    = emptyList(),
    val isMultiSourceScanning     : Boolean                = false,
    val lastScanDone              : Boolean                = false,
    val trackSearchResults        : List<GitHubRepo>       = emptyList(),
    val isTrackSearching          : Boolean                = false,
    val todayPicks                : TodayPicks             = TodayPicks(),
    val recentSearches            : List<String>           = emptyList(),
    val newlyLaunched             : List<GitHubRepo>       = emptyList(),
    val announcement              : Announcement?          = null,
    /** CDN-pinned hero apps, prepended to the featured carousel in both shells. */
    val featuredPins              : List<FeaturedPin>       = emptyList(),
    // GitHub repos confirmed to have NO installable APK in their releases. Home
    // rows filter these out so the feed only surfaces things you can install.
    // Starts empty and only grows as background verification confirms absences,
    // so the feed is never blanked while checks are still running.
    val apkAbsentIds              : Set<Long>              = emptySet()
)

interface GTranslateService {
    @GET("translate_a/single")
    suspend fun translate(
        @Query("client") client: String,
        @Query("sl") sl: String,
        @Query("tl") tl: String,
        @Query("dt") dt: String,
        @Query("q") q: String
    ): JsonArray
}
object GTranslateClient {
    val service: GTranslateService = Retrofit.Builder()
        .baseUrl("https://translate.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(GTranslateService::class.java)
}

/**
 * Maps a language display name as stored in `AppSettings.language` onto its ISO code.
 *
 * One definition shared by every caller — this `when` used to be copy-pasted at each
 * translation site, so a new language had to be remembered in several places at once.
 */
fun translationCodeFor(language: String): String = when (language) {
    "Hindi"      -> "hi"
    "Spanish"    -> "es"
    "French"     -> "fr"
    "German"     -> "de"
    "Japanese"   -> "ja"
    "Portuguese" -> "pt"
    "Italian"    -> "it"
    "Russian"    -> "ru"
    "Chinese"    -> "zh"
    "Korean"     -> "ko"
    "Arabic"     -> "ar"
    "Dutch"      -> "nl"
    "Turkish"    -> "tr"
    "Polish"     -> "pl"
    "Swedish"    -> "sv"
    else         -> "en"
}

/**
 * Runs one chunk of text through the public translate endpoint.
 *
 * The response is a nested array whose first element holds the translated segments;
 * a failed segment yields "" rather than aborting the whole string.
 */
suspend fun translateText(text: String, targetCode: String): String {
    val r = GTranslateClient.service.translate("gtx", "auto", targetCode, "t", text)
    return r[0].asJsonArray.joinToString("") { chunk ->
        runCatching { chunk.asJsonArray[0].asString }.getOrDefault("")
    }
}

// ── GitHub API ────────────────────────────────────────────────────────────────
interface GitHubService {
    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadme(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): ReadmeResponse

    @GET("user/starred")
    suspend fun getStarredRepos(
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubRepo>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10
    ): List<Release>

    @GET("search/repositories")
    suspend fun searchRepos(
        @Query("q") query: String,
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1
    ): SearchResponse

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Release

    // Direct repo lookup — GitHub's /search API can't find a repo by its full
    // "owner/repo" path, but this endpoint resolves it exactly.
    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepo
}

object RetrofitClient {
    @Volatile var authToken: String = ""
    private val httpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(3, 5, java.util.concurrent.TimeUnit.MINUTES))
        // Publishes GitHub's own X-RateLimit-* headers to the quota meter in Settings.
        // Network-level so it also sees the anonymous retry below, which spends from a
        // different (IP-based) budget than the authenticated attempt it replaced.
        .addNetworkInterceptor(com.vythera.vyxelapps.api.GitHubRateLimit.interceptor)
        .addInterceptor { chain ->
            val hasToken = authToken.isNotEmpty()
            val req = chain.request().newBuilder().apply {
                if (hasToken) addHeader("Authorization", "Bearer $authToken")
                addHeader("Accept", "application/vnd.github+json")
            }.build()
            val resp = chain.proceed(req)
            // A bad / expired / over-scoped token returns 401. Public release & search
            // data needs no auth, so transparently retry once without the token rather
            // than letting one bad token break every GitHub request.
            if (resp.code == 401 && hasToken) {
                resp.close()
                val anonReq = chain.request().newBuilder()
                    .removeHeader("Authorization")
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()
                chain.proceed(anonReq)
            } else resp
        }.build()

    val service: GitHubService = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GitHubService::class.java)
}

// ── GitLab API ────────────────────────────────────────────────────────────────
data class GitLabProject(
    val id: Int = 0, val name: String = "", val description: String? = null,
    val star_count: Int = 0, val forks_count: Int = 0, val web_url: String = "",
    val namespace: GitLabNamespace = GitLabNamespace(),
    val language: String? = null, val last_activity_at: String = "",
    // GitLab's `name` is a human display label (may contain spaces/caps);
    // the release/README APIs need the URL path, so capture it explicitly.
    val path: String = "", val path_with_namespace: String = "",
    // The project's own avatar. This was never parsed, so every GitLab entry fell
    // back to a guessed group-avatar URL built from the *project* id — a path that
    // does not exist — and no GitLab app ever showed artwork.
    val avatar_url: String? = null
)
// `kind` is "group" or "user"; it is what tells us whether the namespace avatar
// is a project/organisation mark or somebody's face.
data class GitLabNamespace(
    val name: String = "",
    val path: String = "",
    val avatar_url: String? = null,
    val kind: String = "",
)

fun GitLabProject.toUnifiedRepo() = GitHubRepo(
    id = id.toLong() + 9_000_000_000L,
    name = name,
    full_name = path_with_namespace.ifBlank {
        "${namespace.path.ifBlank { namespace.name }}/${path.ifBlank { name }}"
    },
    description = description, stargazers_count = star_count,
    forks_count = forks_count, html_url = web_url,
    owner = RepoOwner(
        namespace.name,
        // Namespace avatars come back as site-relative paths ("/uploads/…"), which
        // are not loadable as-is; absolutize them. A project with neither avatar gets
        // the placeholder rather than a URL that is certain to 404.
        (avatar_url ?: namespace.avatar_url)
            ?.let { if (it.startsWith("/")) "https://gitlab.com$it" else it }
            .orEmpty(),
        // A project avatar is the project's own mark and is always fine. Only a
        // namespace avatar risks being a person, so it is trusted for groups only.
        type = if (avatar_url != null || namespace.kind == "group") "Organization" else "User",
    ),
    language = language, updated_at = last_activity_at,
    source = AppSource.GITLAB
)

// GitLab release data classes (different structure from GitHub/Gitea)
data class GitLabRelease(
    val tag_name    : String              = "",
    val name        : String              = "",
    val description : String              = "",
    val released_at : String              = "",
    val assets      : GitLabReleaseAssets = GitLabReleaseAssets()
)
data class GitLabReleaseAssets(
    val links   : List<GitLabAssetLink>   = emptyList(),
    val sources : List<GitLabAssetSource> = emptyList()
)
data class GitLabAssetLink(
    val name             : String = "",
    val url              : String = "",
    val direct_asset_url : String = ""
)
// Auto-generated source-code archives (zip / tar.gz / tar.bz2 / tar). Many GitLab
// projects (e.g. Kali NetHunter) attach no link assets, so these are all there is.
data class GitLabAssetSource(val format: String = "", val url: String = "")
fun GitLabRelease.toRelease() = Release(
    tag_name     = tag_name,
    name         = name.ifBlank { tag_name },
    body         = description,
    published_at = released_at,
    assets       = assets.links.map { link ->
        ReleaseAsset(
            name                 = link.name,
            browser_download_url = link.direct_asset_url.ifBlank { link.url },
            size                 = 0L,
            content_type         = if (link.name.endsWith(".apk", ignoreCase = true) ||
                                       link.url.substringBefore('?').substringBefore('#').endsWith(".apk", ignoreCase = true))
                                       "application/vnd.android.package-archive" else ""
        )
    } + assets.sources.map { src ->
        ReleaseAsset(
            name                 = "$tag_name-source.${src.format}",
            browser_download_url = src.url,
            size                 = 0L,
            content_type         = ""
        )
    }
)

interface GitLabService {
    @GET("projects")
    suspend fun searchProjects(
        @Query("search")     query     : String  = "android",
        @Query("order_by")   orderBy   : String  = "star_count",
        @Query("sort")       sort      : String  = "desc",
        @Query("per_page")   perPage   : Int     = 20,
        @Query("page")       page      : Int     = 1,
        @Query("visibility") visibility: String  = "public",
        @Query("topic")      topic     : String? = null
    ): List<GitLabProject>

    // path must be URL-encoded, e.g. "owner%2Frepo"
    @GET("projects/{path}/releases")
    suspend fun getReleases(
        @Path("path", encoded = true) path    : String,
        @Query("per_page")            perPage : Int = 10
    ): List<GitLabRelease>
}

object GitLabClient {
    val service: GitLabService = Retrofit.Builder()
        .baseUrl("https://gitlab.com/api/v4/")
        .client(OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .addHeader("Accept", "application/json").build())
            }.build())
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(GitLabService::class.java)
}

// ── Codeberg API ──────────────────────────────────────────────────────────────
data class CodebergRepo(
    val id: Long = 0, val name: String = "", val description: String? = null,
    val stars_count: Int = 0, val forks_count: Int = 0, val html_url: String = "",
    val owner: CodebergOwner = CodebergOwner(),
    val language: String? = null, val updated: String = ""
)
// Gitea reports the owner kind here; "organization" mirrors GitHub's "Organization".
data class CodebergOwner(
    val login: String = "",
    val avatar_url: String = "",
    val type: String = "",
)
data class CodebergSearchResponse(val ok: Boolean = false, val data: List<CodebergRepo> = emptyList())

fun CodebergRepo.toUnifiedRepo() = GitHubRepo(
    id = id + 8_000_000_000L,
    name = name, full_name = "${owner.login}/$name",
    description = description, stargazers_count = stars_count,
    forks_count = forks_count, html_url = html_url,
    // Gitea omits the kind on some search responses; absent means we cannot prove
    // it is an organisation, and an unproven avatar is treated as a person.
    owner = RepoOwner(
        owner.login,
        owner.avatar_url,
        type = if (owner.type.equals("organization", true)) "Organization" else "User",
    ),
    language = language, updated_at = updated,
    source = AppSource.CODEBERG
)

/**
 * Repairs the F-Droid icon URLs the metadata CDN publishes.
 *
 * The generator joins each app's *relative* icon path onto a base that already ends
 * in that same path, so every localized icon comes through as
 * `…/repo/<pkg>/<locale>//<pkg>/<locale>/icon_<hash>.png` and 404s — which is why
 * F-Droid entries had no artwork in Classic while Expressive, which builds the URL
 * itself from the live index, showed them fine. Nearly the whole index is affected
 * (974 of the first 1000 entries), so this is repaired on read rather than waited on.
 */
private fun repairDuplicatedIconPath(raw: String, pkg: String): String {
    if (pkg.isEmpty()) return raw
    val duplicate = raw.indexOf("//$pkg/")
    if (duplicate < 0) return raw
    val repoRoot = raw.substring(0, duplicate).substringBefore("/$pkg/")
    return "$repoRoot/${raw.substring(duplicate + 2)}"
}

/**
 * Repairs the IzzyOnDroid icon URLs the metadata CDN publishes.
 *
 * F-Droid-protocol repos keep unlocalized icons under `icons-640/`, but the CDN emits
 * them flat as `…/fdroid/repo/<pkg>.<versionCode>.png`, which 404s. Only a minority of
 * Izzy entries carry an icon field at all; the rest fall back to the per-package
 * localized path, which every package serves.
 */
private fun repairIzzyIconPath(raw: String): String {
    val marker = "/fdroid/repo/"
    val cut = raw.indexOf(marker)
    if (cut < 0) return raw
    val tail = raw.substring(cut + marker.length)
    // Already directory-qualified — "icons-640/…" or "<pkg>/<locale>/…".
    if (tail.contains('/')) return raw
    return raw.substring(0, cut + marker.length) + "icons-640/" + tail
}

fun AppEntry.toGitHubRepo(): GitHubRepo {
    val appSource = when (source) {
        "fdroid"   -> AppSource.FDROID
        "gitlab"   -> AppSource.GITLAB
        "codeberg" -> AppSource.CODEBERG
        "flathub"  -> AppSource.FLATHUB
        "winget"   -> AppSource.WINGET
        "izzy"     -> AppSource.IZZY
        else       -> AppSource.GITHUB
    }
    val pkg = id.substringAfter(":")
    val repoId: Long = when (source) {
        "gitlab"   -> pkg.toLongOrNull()?.plus(9_000_000_000L)
                      ?: (kotlin.math.abs(id.hashCode()).toLong() + 9_000_000_000L)
        "codeberg" -> pkg.toLongOrNull()?.plus(8_000_000_000L)
                      ?: (kotlin.math.abs(id.hashCode()).toLong() + 8_000_000_000L)
        "fdroid"   -> kotlin.math.abs(id.hashCode()).toLong() + 7_000_000_000L
        "flathub"  -> kotlin.math.abs(id.hashCode()).toLong() + 6_000_000_000L
        "winget"   -> kotlin.math.abs(id.hashCode()).toLong() + 5_000_000_000L
        "github"   -> pkg.toLongOrNull() ?: kotlin.math.abs(id.hashCode()).toLong()
        "izzy"     -> pkg.toLongOrNull()?.plus(4_000_000_000L)
                      ?: (kotlin.math.abs(id.hashCode()).toLong() + 4_000_000_000L)
        else       -> kotlin.math.abs(id.hashCode()).toLong()
    }
    /**
     * "owner/repo" for GitHub-hosted entries, read from whichever field actually
     * carries a github.com URL.
     *
     * This used to look only at `homepage`, which is wrong twice over: plenty of
     * projects set homepage to their own website (or leave it blank), and when the
     * parse failed the owner fell back to the literal string "github" — so the
     * release lookup went out as `getReleases("github", name)` and 404'd, which is
     * what "No release found" was on apps that plainly had releases. It also built
     * full_name from the catalog's *display* name rather than the repo slug, so even
     * a correct owner could be paired with the wrong repo.
     *
     * `source_code` is the field that reliably holds the repo URL; homepage is only
     * a fallback for older entries that predate it.
     */
    val ghPath = if (source == "github" || source == "izzy") {
        listOf(sourceCode, homepage)
            .firstNotNullOfOrNull { url ->
                url.takeIf { it.contains("github.com", ignoreCase = true) }
                    ?.substringAfter("github.com/", "")
                    ?.substringBefore('?')
                    ?.trim('/')
                    ?.split('/')
                    ?.takeIf { parts -> parts.size >= 2 && parts.all { it.isNotBlank() } }
                    ?.let { it[0] to it[1].removeSuffix(".git") }
            }
    } else null
    val ghOwner = ghPath?.first
    val owner = when (source) {
        "gitlab", "codeberg" -> pkg.substringBefore("/").ifEmpty { source }
        "github", "izzy"     -> ghOwner ?: source
        else                 -> source
    }
    val fullName = when (source) {
        "github", "izzy" -> ghPath?.let { "${it.first}/${it.second}" } ?: pkg
        else             -> pkg
    }
    val resolvedUrl = when {
        source == "fdroid" && !homepage.contains("f-droid.org") ->
            "https://f-droid.org/packages/$pkg"
        else -> homepage
    }
    return GitHubRepo(
        id               = repoId,
        name             = name,
        full_name        = fullName,
        description      = summary.ifEmpty { null },
        stargazers_count = stars,
        html_url         = resolvedUrl,
        owner            = RepoOwner(
            login      = owner,
            avatar_url = repairDuplicatedIconPath(icon, pkg)
                .let { if (source == "izzy") repairIzzyIconPath(it) else it }
                .ifEmpty {
                    when (source) {
                        "fdroid"  -> "https://f-droid.org/repo/$pkg/en-US/icon.png"
                        // Most Izzy entries carry no icon field at all — 914 of the
                        // first 1000 CDN entries are empty — but every package serves
                        // this path, so derive it rather than showing nothing.
                        "izzy"    -> "https://apt.izzysoft.de/fdroid/repo/$pkg/en-US/icon.png"
                        "flathub" -> "https://dl.flathub.org/repo/appstream/$pkg.png"
                        // GitLab's `pkg` here is the numeric project id, so the old
                        // "gitlab.com/<pkg>.png" guess was never a real avatar. Projects
                        // without one just have no icon; the placeholder is the honest
                        // result, and a dead URL only makes Coil retry it.
                        else      -> ""
                    }
                }
        ),
        source        = appSource,
        apkUrl        = apkUrl,
        cdnVersion    = version,
        sourceCodeUrl = sourceCode,
        // Only meaningful for the package-keyed stores; harmless elsewhere.
        packageId     = if (source == "fdroid" || source == "izzy") pkg else ""
    )
}

interface CodebergService {
    @GET("repos/search")
    suspend fun searchRepos(
        @Query("q")     query : String  = "android",
        @Query("topic") topic : Boolean = false,
        @Query("sort")  sort  : String  = "stars",
        @Query("order") order : String  = "desc",
        @Query("limit") limit : Int     = 20,
        @Query("page")  page  : Int     = 1
    ): CodebergSearchResponse

    // Gitea release format closely matches GitHub's Release/ReleaseAsset data classes
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner : String,
        @Path("repo")  repo  : String,
        @Query("limit") limit: Int = 10
    ): List<Release>
}

object CodebergClient {
    val service: CodebergService = Retrofit.Builder()
        .baseUrl("https://codeberg.org/api/v1/")
        .client(OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", "VyxelApps/1.0")
                    .build())
            }.build())
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(CodebergService::class.java)
}

// ── Flathub API ───────────────────────────────────────────────────────────────
// All Flathub v2 endpoints return {"hits": [...]} — never a bare array.
// app_id is the canonical dotted ID (com.discordapp.Discord); `id` is underscore version.
data class FlathubApp(
    @com.google.gson.annotations.SerializedName("app_id")
    val appId    : String = "",
    val name     : String = "",
    val summary  : String = "",
    val icon     : String = "",
    @com.google.gson.annotations.SerializedName("installs_last_month")
    val installs : Int    = 0
)
data class FlathubResponse(val hits: List<FlathubApp>? = emptyList())
// Only send query — omitting filters avoids 422 from the backend's Filter object schema
data class FlathubSearchBody(val query: String, val hits_per_page: Int = 30)

fun FlathubApp.toUnifiedRepo(): GitHubRepo {
    val safeId   = appId   ?: ""
    val safeName = name    ?: ""
    val safeSum  = summary ?: ""
    val safeIcon = icon    ?: ""
    return GitHubRepo(
        id               = kotlin.math.abs(safeId.hashCode()).toLong() + 6_000_000_000L,
        name             = safeName.ifEmpty { safeId.substringAfterLast(".") },
        full_name        = safeId,
        description      = safeSum.ifEmpty { null },
        stargazers_count = installs / 100,
        html_url         = "https://flathub.org/apps/$safeId",
        owner            = RepoOwner(login = "flathub", avatar_url = safeIcon),
        source           = AppSource.FLATHUB
    )
}

interface FlathubService {
    @GET("collection/recently-added")
    suspend fun getPopular(): FlathubResponse

    @POST("search")
    suspend fun search(@Body body: FlathubSearchBody): FlathubResponse
}

object FlathubClient {
    val service: FlathubService = Retrofit.Builder()
        .baseUrl("https://flathub.org/api/v2/")
        .client(OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .addHeader("Accept", "application/json").build())
            }.build())
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(FlathubService::class.java)
}

// ── IzzyOnDroid live client ───────────────────────────────────────────────────
// Streams the F-Droid index-v1.json and returns the first N apps.
// The streaming parser skips the large "packages" object without loading it.
data class IzzyFDroidApp(
    val packageName : String  = "",
    val name        : String  = "",
    val summary     : String? = null,
    val icon        : String? = null,
    val sourceCode  : String? = null,
    val webSite     : String? = null
)

object IzzyOnDroidClient {
    private val http = OkHttpClient.Builder()
        // Short connect timeout on purpose. apt.izzysoft.de has been observed
        // wholly unreachable from some networks (DNS resolves, TCP never
        // completes) — with a 30s connect timeout that stalled the whole source
        // for half a minute before quietly yielding nothing. A host that has not
        // answered in 12s is not going to.
        .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        // Generous by comparison, because the index legitimately streams slowly.
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        // A ceiling on the whole call, not just the gap between bytes. A slow but
        // steadily trickling index would otherwise never trip the read timeout and
        // could hold the source in "loading" indefinitely.
        .callTimeout(75, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .addHeader("User-Agent", "VyxelApps/1.0").build())
        }.build()

    @Volatile private var cache   : List<GitHubRepo>? = null
    @Volatile private var cacheAt : Long              = 0L
    private val CACHE_TTL = 6 * 60 * 60 * 1000L

    suspend fun getApps(limit: Int = 50): List<GitHubRepo> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val c = cache
            if (c != null && System.currentTimeMillis() - cacheAt < CACHE_TTL)
                return@withContext c.take(limit)
            try {
                val req  = okhttp3.Request.Builder()
                    .url("https://apt.izzysoft.de/fdroid/repo/index-v1.json").build()
                val resp = http.newCall(req).execute()
                // Closed in the finally below rather than only on the happy path:
                // because parsing now abandons the document early, and because any
                // parse failure used to leave the response open, this connection
                // could leak and tie up the pool for subsequent refreshes.
                try {
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body ?: return@withContext emptyList()
                val gson = Gson()
                val jr   = com.google.gson.stream.JsonReader(body.charStream())
                val apps = mutableListOf<IzzyFDroidApp>()
                jr.beginObject()
                while (jr.hasNext()) {
                    if (jr.nextName() == "apps") {
                        jr.beginArray()
                        while (jr.hasNext() && apps.size < limit) {
                            try { apps.add(gson.fromJson(jr, IzzyFDroidApp::class.java)) }
                            catch (_: Exception) { try { jr.skipValue() } catch (_: Exception) {} }
                        }
                        // Stop the moment we have enough.
                        //
                        // index-v1.json is tens of megabytes, and "apps" is followed
                        // by "packages", which lists every version of every app.
                        // Draining the rest of the array and then skipping the
                        // remaining top-level keys — purely to close the document
                        // cleanly — meant downloading and parsing the entire index to
                        // obtain 50 entries. On mobile data that reliably exceeded the
                        // 60s read timeout and the source silently returned nothing.
                        //
                        // Abandoning the reader mid-document is intentional: closing
                        // the body aborts the transfer, and nothing here needs the
                        // JSON to be well-formed past this point.
                        break
                    }
                    try { jr.skipValue() } catch (_: Exception) {}
                }
                body.close()
                val result = apps.filter { it.packageName.isNotEmpty() }.map { app ->
                    // index-v1 keeps the icon under localized.<locale>.icon as the
                    // bare name "icon.png"; there is no top-level `icon` for almost
                    // any app, so this used to resolve to "" and no artwork loaded.
                    // Every package serves <pkg>/en-US/icon.png, so derive that.
                    val icon = app.icon?.takeIf { it.isNotBlank() }
                        ?.let { "https://apt.izzysoft.de/fdroid/repo/${app.packageName}/en-US/$it" }
                        ?: "https://apt.izzysoft.de/fdroid/repo/${app.packageName}/en-US/icon.png"
                    val home = app.sourceCode?.takeIf { it.isNotBlank() }
                        ?: app.webSite?.takeIf { it.isNotBlank() }
                        ?: "https://apt.izzysoft.de/fdroid/index/apk/${app.packageName}"
                    GitHubRepo(
                        id               = kotlin.math.abs(app.packageName.hashCode()).toLong() + 4_000_000_000L,
                        name             = app.name.ifEmpty { app.packageName },
                        full_name        = app.packageName,
                        description      = app.summary?.takeIf { it.isNotBlank() },
                        stargazers_count = 0,
                        html_url         = home,
                        owner            = RepoOwner(
                            login      = app.packageName.substringAfterLast("."),
                            avatar_url = icon
                        ),
                        source           = AppSource.IZZY
                    )
                }
                cache   = result
                cacheAt = System.currentTimeMillis()
                result
                } finally { resp.close() }
            } catch (_: Exception) { emptyList() }
        }
}

// ── Preferences persistence ───────────────────────────────────────────────────
class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("vyxel_prefs", Context.MODE_PRIVATE)
    private val gson  = Gson()

    private val tokenPrefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vyxel_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) { null }

    private fun saveToken(token: String) =
        tokenPrefs?.edit()?.putString("github_token", token)?.apply()
            ?: prefs.edit().putString("github_token_fallback", token).apply()

    private fun loadToken(): String =
        tokenPrefs?.getString("github_token", null)
            ?: prefs.getString("github_token_fallback", "") ?: ""

    fun saveLanguageCode(code: String) = prefs.edit().putString("user_language_code", code).apply()

    fun saveProfile(p: UserProfile) = prefs.edit().putString("profile", gson.toJson(p)).apply()
    fun loadProfile(): UserProfile  = fromJson("profile") ?: UserProfile()

    fun saveSettings(s: AppSettings) {
        saveToken(s.githubToken)
        prefs.edit().putString("settings", gson.toJson(s.copy(githubToken = ""))).apply()
    }
    fun loadSettings(): AppSettings {
        val s = fromJson<AppSettings>("settings")
            ?: fromJson<AppSettings>("app_settings_v2")
            ?: AppSettings()
        return s.copy(githubToken = loadToken())
    }

    fun saveTheme(t: ThemeName)  = prefs.edit().putString("theme", t.name).apply()
    fun loadTheme(): ThemeName   = try {
        ThemeName.valueOf(prefs.getString("theme", ThemeName.DARK.name)!!)
    } catch (_: Exception) { ThemeName.DARK }

    fun saveAccentColor(c: Color?) =
        prefs.edit().putString("accent", c?.value?.toString() ?: "").apply()
    fun loadAccentColor(): Color? = try {
        val s = prefs.getString("accent", "") ?: ""
        if (s.isEmpty()) null else Color(s.toULong())
    } catch (_: Exception) { null }

    fun saveSearchPlatform(p: AppPlatform) = prefs.edit().putString("search_platform", p.name).apply()
    fun loadSearchPlatform(): AppPlatform = try {
        AppPlatform.valueOf(prefs.getString("search_platform", AppPlatform.ALL.name)!!)
    } catch (_: Exception) { AppPlatform.ALL }

    fun saveSearchSubCategories(subs: Set<String>) = prefs.edit().putStringSet("search_subs", subs).apply()
    fun loadSearchSubCategories(): Set<String> = prefs.getStringSet("search_subs", emptySet()) ?: emptySet()

    fun saveCustomTheme(d: CustomThemeData) =
        prefs.edit().putString("custom_theme", gson.toJson(d)).apply()
    fun loadCustomTheme(): CustomThemeData = fromJson("custom_theme") ?: CustomThemeData()

    fun saveHistory(h: List<HistoryItem>) =
        prefs.edit().putString("history", gson.toJson(h.take(50))).apply()
    fun loadHistory(): List<HistoryItem> {
        val json = prefs.getString("history", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<HistoryItem>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun saveCategoryViews(m: Map<String, Int>) =
        prefs.edit().putString("catviews", gson.toJson(m)).apply()
    fun loadCategoryViews(): Map<String, Int> {
        val json = prefs.getString("catviews", null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
    }

    private inline fun <reified T> fromJson(key: String): T? = try {
        val json = prefs.getString(key, null) ?: return null
        gson.fromJson(json, T::class.java)
    } catch (_: Exception) { null }

    fun saveInstallHistory(h: List<InstallHistoryEntry>) =
        prefs.edit().putString("install_history", gson.toJson(h.takeLast(60))).apply()
    fun loadInstallHistory(): List<InstallHistoryEntry> {
        val json = prefs.getString("install_history", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<InstallHistoryEntry>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun saveIgnoredVersions(v: Set<String>) =
        prefs.edit().putStringSet("ignored_versions", v).apply()
    fun loadIgnoredVersions(): Set<String> =
        prefs.getStringSet("ignored_versions", emptySet()) ?: emptySet()

    fun saveNotifsDismissed(v: Boolean) = prefs.edit().putBoolean("notifs_dismissed", v).apply()
    fun loadNotifsDismissed(): Boolean  = prefs.getBoolean("notifs_dismissed", false)

    fun saveUpdates(updates: List<UpdateInfo>) =
        prefs.edit().putString("cached_updates", gson.toJson(updates)).apply()
    fun loadUpdates(): List<UpdateInfo> {
        val json = prefs.getString("cached_updates", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<UpdateInfo>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun saveFavourites(favs: List<GitHubRepo>) =
        prefs.edit().putString("favourites", gson.toJson(favs)).apply()
    fun loadFavourites(): List<GitHubRepo> {
        val json = prefs.getString("favourites", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<GitHubRepo>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun saveCustomRepos(repos: List<CustomRepo>) =
        prefs.edit().putString("custom_repos", gson.toJson(repos)).apply()
    fun loadCustomRepos(): List<CustomRepo> {
        val json = prefs.getString("custom_repos", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<CustomRepo>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // Signed entitlement token (see Entitlement.kt). This is the real record of
    // entitlement; the legacy boolean below is only kept for migration.
    fun saveEntitlementToken(token: String) =
        tokenPrefs?.edit()?.putString("ent_token", token)?.apply()
            ?: prefs.edit().putString("ent_token_fb", token).apply()
    fun loadEntitlementToken(): String =
        tokenPrefs?.getString("ent_token", null)
            ?: prefs.getString("ent_token_fb", "") ?: ""
    fun clearEntitlementToken() {
        tokenPrefs?.edit()?.remove("ent_token")?.apply()
        prefs.edit().remove("ent_token_fb").apply()
    }

    /**
     * One-time migration window for users who unlocked before tokens existed.
     * Their install has the legacy boolean but no token, and forcing them to
     * re-enter a key on update would be hostile — so the boolean is honoured
     * until this deadline or until a token is obtained, whichever comes first.
     * After it passes the boolean carries no weight at all.
     */
    fun legacyUnlockDeadline(): Long {
        val existing = prefs.getLong("legacy_unlock_deadline", 0L)
        if (existing != 0L) return existing
        val deadline = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        prefs.edit().putLong("legacy_unlock_deadline", deadline).apply()
        return deadline
    }

    // License key — stored in EncryptedSharedPreferences (same as GitHub token)
    fun saveLiquidGlassUnlocked(v: Boolean) =
        tokenPrefs?.edit()?.putBoolean("lg_unlocked", v)?.apply()
            ?: prefs.edit().putBoolean("lg_unlocked_fb", v).apply()
    fun loadLiquidGlassUnlocked(): Boolean =
        tokenPrefs?.getBoolean("lg_unlocked", false)
            ?: prefs.getBoolean("lg_unlocked_fb", false)
    fun saveUsedLicenseKey(key: String) =
        tokenPrefs?.edit()?.putString("lg_used_key", key)?.apply()
            ?: prefs.edit().putString("lg_used_key_fb", key).apply()
    fun loadUsedLicenseKey(): String =
        tokenPrefs?.getString("lg_used_key", null)
            ?: prefs.getString("lg_used_key_fb", "") ?: ""

    fun saveTodayPicks(p: TodayPicks) =
        prefs.edit().putString("today_picks", gson.toJson(p)).apply()
    fun loadTodayPicks(): TodayPicks = fromJson("today_picks") ?: TodayPicks()

    fun saveSeenAnnouncements(ids: Set<String>) =
        prefs.edit().putStringSet("seen_announcements", ids).apply()
    fun loadSeenAnnouncements(): Set<String> =
        prefs.getStringSet("seen_announcements", emptySet()) ?: emptySet()

    fun saveRecentSearches(q: List<String>) =
        prefs.edit().putString("recent_searches", gson.toJson(q.take(10))).apply()
    fun loadRecentSearches(): List<String> {
        val json = prefs.getString("recent_searches", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
}

// ── Junk filter for homepage rows ────────────────────────────────────────────
// GitHub search surfaces docs/collections (interview questions, awesome-lists,
// setup guides) that aren't installable apps. Name patterns are broad;
// description patterns only match strong phrases so a real app described as
// "an awesome music player" survives. Mirrors looks_like_app() in the CDN's
// fetch_metadata.py — keep the two in sync.
private val junkNameRegex = Regex(
    "(?i)awesome|interview|roadmap|cheat.?sheet|leetcode|tutorial|guide|course|" +
    "handbook|questions|e-?books?|wallpapers?$|-docs$|-notes$|study"
)
private val junkDescRegex = Regex(
    "(?i)interview question|curated list|list of |collection of |cheat.?sheet|" +
    "roadmap|tutorials?\\b|setup guide|study plan|e-?books?|learning path|" +
    "course material|sample code|code samples"
)

fun GitHubRepo.isLikelyApp(): Boolean =
    !junkNameRegex.containsMatchIn(name) &&
    !junkDescRegex.containsMatchIn(description ?: "")

fun com.vythera.vyxelapps.api.AppEntry.isLikelyApp(): Boolean =
    !junkNameRegex.containsMatchIn(name) &&
    !junkDescRegex.containsMatchIn(summary)

// ── Smart APK detection ───────────────────────────────────────────────────────
data class SmartInstallResult(
    val asset     : ReleaseAsset,
    val reason    : String,
    val isOptimal : Boolean = true
)

fun detectBestApk(assets: List<ReleaseAsset>): SmartInstallResult? {
    val apks = assets.filter { it.isApk() }
    if (apks.isEmpty()) return null
    if (apks.size == 1) return SmartInstallResult(apks.first(), "Only available package")

    val deviceAbis = android.os.Build.SUPPORTED_ABIS.toList()
    val abiMap = mapOf(
        "arm64-v8a"   to listOf("arm64-v8a", "arm64", "aarch64"),
        "armeabi-v7a" to listOf("armeabi-v7a", "armeabi", "armv7"),
        "x86_64"      to listOf("x86_64", "x64"),
        "x86"         to listOf("x86", "i686")
    )

    apks.firstOrNull { it.matchText().contains("universal") }
        ?.let { return SmartInstallResult(it, "Universal — works on all devices") }

    for (abi in deviceAbis) {
        val keywords = abiMap[abi] ?: continue
        for (kw in keywords) {
            apks.firstOrNull { it.matchText().contains(kw) }
                ?.let { return SmartInstallResult(it, "Optimised for $abi (your device)") }
        }
    }

    val fallback = apks.maxByOrNull { it.size } ?: apks.first()
    return SmartInstallResult(fallback, "Default package", isOptimal = false)
}

// Respect the "Show pre-releases" toggle. When off, hide pre-releases — but never
// hide everything: if a repo has ONLY pre-releases (e.g. nightly-only projects),
// still show them so the release list is never empty.
fun List<Release>.filterByPreReleasePref(showPre: Boolean): List<Release> {
    if (showPre) return this
    val stable = filter { !it.prerelease }
    return stable.ifEmpty { this }
}

// F-Droid and IzzyOnDroid are both F-Droid repositories: same packages API and the
// same "{repo}/{pkg}_{versionCode}.apk" download scheme, different base URLs. Returns
// one synthetic Release per published version, newest first. Versions above the
// repo's suggestedVersionCode are flagged as pre-release (beta/nightly).
suspend fun fetchFdroidStyleReleases(pkg: String, apiBase: String, repoBase: String): List<Release> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val json = OkHttpClient().newCall(
                okhttp3.Request.Builder().url("$apiBase/api/v1/packages/$pkg").build()
            ).execute().use { if (!it.isSuccessful) return@withContext emptyList(); it.body?.string() ?: return@withContext emptyList() }
            val obj       = com.google.gson.JsonParser.parseString(json).asJsonObject
            val suggested = obj.get("suggestedVersionCode")?.takeIf { !it.isJsonNull }?.asLong
            val pkgs      = obj.getAsJsonArray("packages") ?: return@withContext emptyList()
            pkgs.mapNotNull { el ->
                val o  = el.asJsonObject
                val vc = o.get("versionCode")?.takeIf { !it.isJsonNull }?.asLong ?: return@mapNotNull null
                val vn = o.get("versionName")?.takeIf { !it.isJsonNull }?.asString ?: vc.toString()
                Release(
                    tag_name     = vn,
                    name         = vn,
                    assets       = listOf(ReleaseAsset(
                        name                 = "${pkg}_$vc.apk",
                        browser_download_url = "$repoBase/repo/${pkg}_$vc.apk",
                        size                 = o.get("size")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                        content_type         = "application/vnd.android.package-archive"
                    )),
                    published_at = "",
                    body         = "",
                    prerelease   = suggested != null && vc > suggested
                )
            }.distinctBy { it.tag_name to it.assets.firstOrNull()?.name }
        } catch (_: Exception) { emptyList() }
    }

// ── Trust Score ───────────────────────────────────────────────────────────────
data class TrustScore(
    val score           : Int,
    val daysSinceUpdate : Int,
    val releaseCount    : Int,
    val forks           : Int,
    val stars           : Int
) {
    val label: String get() = when {
        score >= 85 -> "Highly Trusted"
        score >= 65 -> "Trusted"
        score >= 45 -> "Moderate"
        score >= 25 -> "Low Trust"
        else        -> "Unverified"
    }
    val safeColor: Color get() = when {
        score >= 85 -> Color(0xFF1DB954.toInt())
        score >= 65 -> Color(0xFF4CAF50.toInt())
        score >= 45 -> Color(0xFFFF9800.toInt())
        score >= 25 -> Color(0xFFFF5722.toInt())
        else        -> Color(0xFF9E9E9E.toInt())
    }
}

fun calculateTrustScore(repo: GitHubRepo, releaseCount: Int): TrustScore {
    var score = 0

    score += when {
        repo.stargazers_count >= 10_000 -> 30
        repo.stargazers_count >= 1_000  -> 24
        repo.stargazers_count >= 500    -> 18
        repo.stargazers_count >= 100    -> 12
        repo.stargazers_count >= 10     -> 6
        else                            -> 0
    }

    val days = try {
        val sdf     = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        val updated = sdf.parse(repo.updated_at)?.time ?: 0L
        ((System.currentTimeMillis() - updated) / 86_400_000L).toInt()
    } catch (_: Exception) { 999 }
    score += when {
        days < 7   -> 25
        days < 30  -> 20
        days < 90  -> 14
        days < 180 -> 8
        days < 365 -> 3
        else       -> 0
    }

    score += when {
        repo.forks_count >= 1_000 -> 20
        repo.forks_count >= 200   -> 15
        repo.forks_count >= 50    -> 10
        repo.forks_count >= 10    -> 5
        repo.forks_count >= 1     -> 2
        else                      -> 0
    }

    score += when {
        releaseCount >= 10 -> 20
        releaseCount >= 5  -> 14
        releaseCount >= 2  -> 9
        releaseCount >= 1  -> 5
        else               -> 0
    }

    if (!repo.description.isNullOrEmpty()) score += 5

    return TrustScore(minOf(100, score), days, releaseCount, repo.forks_count, repo.stargazers_count)
}

// ── Curated collections ───────────────────────────────────────────────────────
// Added `subtitle` field — used by CollectionsRow in M3 Expressive tile layout
data class AppCollection(
    val emoji    : String,
    val title    : String,
    val query    : String,
    val subtitle : String = "",
    val iconRes:Int? = null
)

val COLLECTIONS = listOf(
    AppCollection("🔒", "Privacy Essentials",  "topic:android privacy",              "Essential privacy tools", R.drawable.ic_priv),
    AppCollection("🎵", "Best Media Apps",      "topic:android media player",         "The best for your media", R.drawable.ic_med),
    AppCollection("🛠",  "Root & Magisk Tools", "topic:android magisk root",           "Unlock your device", R.drawable.ic_mag),
    AppCollection("📖", "Reading & E-Books",    "topic:android ebook reader",          "Books and reading apps", R.drawable.ic_book),
    AppCollection("🌐", "Browsers",             "topic:android browser privacy",       "Open-source browsers", R.drawable.ic_brow),
    AppCollection("💬", "Messaging",            "topic:android messaging privacy",     "Private messaging apps", R.drawable.ic_mes),
    AppCollection("📸", "Camera & Gallery",     "topic:android camera",                "Camera and photo apps", R.drawable.ic_cam),
    AppCollection("🎮", "Emulators",            "topic:android emulator game",         "Game emulators", R.drawable.ic_emu),
    AppCollection("🔧", "Dev Tools",            "topic:android developer-tools",       "Tools for developers", R.drawable.ic_dev),
    AppCollection("☁️", "Sync & Backup",        "topic:android backup sync",           "Backup your data", R.drawable.ic_syc),
    AppCollection("🌐", "PWA Apps",          "topic:pwa progressive-web-app stars:>100",  "Progressive web apps", R.drawable.ic_pwa),
    AppCollection("🤖", "AI & ML Apps",       "topic:android machine-learning stars:>100", "AI-powered apps",R.drawable.ic_ai),
    AppCollection("🎨", "Customization",       "topic:android launcher theme stars:>50",    "Launchers & themes", R.drawable.ic_cus),
)

private fun stripMarkdown(text: String): String = text
    .lines()
    .filterNot { line ->
        val t = line.trimStart()
        t.startsWith("![") || t.startsWith("<img") ||
        t.startsWith("<!--") || t.startsWith("[!") ||
        t.startsWith("[![")
    }
    .joinToString("\n")
    .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
    .replace(Regex("\\*{1,3}(.+?)\\*{1,3}"), "$1")
    .replace(Regex("_{1,2}(.+?)_{1,2}"), "$1")
    .replace(Regex("```[\\s\\S]*?```"), "")
    .replace(Regex("`(.+?)`"), "$1")
    // Linked badges `[![alt](img)](href)` must go BEFORE plain images, else the
    // image strip leaves a bare "[](" behind — which was showing up at the top
    // of the Description on repos whose title line carries badges.
    .replace(Regex("\\[!\\[[^]]*]\\([^)]*\\)]\\([^)]*\\)"), "")
    .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("\\[\\s*]\\([^)]*\\)"), "")   // empty-text link remnants
    .replace(Regex("^>+\\s*", RegexOption.MULTILINE), "")
    .replace(Regex("^[-*]{3,}$", RegexOption.MULTILINE), "")
    .replace(Regex("<[^>]+>"), "")
    .replace(Regex("https?://\\S+"), "")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

// ── README screenshot extraction ──────────────────────────────────────────────
// Shared by every README-based source. The old per-source regex only matched
// markdown `![](…)` and kept everything, so cards filled with shields.io badges,
// CI/coverage icons, donate buttons and logos — the "empty" or junk images users
// saw. This matches markdown AND raw <img> tags, drops the badge/logo noise, and
// resolves relative paths against the repo's default branch (HEAD), so master-
// branch repos resolve too.

// Hosts and filename fragments that are never app screenshots.
private val screenshotJunkRegex = Regex(
    "(?i)shields\\.io|badgen|img\\.shields|travis-ci|appveyor|circleci|codecov|" +
    "coveralls|sonarcloud|sonarqube|githubusercontent\\.com/.*/(badge|badges)/|" +
    "/badge|badge\\.|\\.svg(\\?|$)|forthebadge|ko-?fi|patreon|liberapay|paypal|" +
    "opencollective|buymeacoffee|hits\\.|visitor|poweredby|gitpod|f-droid\\.org/badge|" +
    "play\\.google\\.com/intl|githubusercontent\\.com/u/|/logo|logo\\.|/icon|icon\\.|" +
    "banner\\.|/banner|favicon|avatars\\.githubusercontent"
)

// Positive hints — paths a real screenshot tends to live in or be named after.
private val screenshotHintRegex = Regex(
    "(?i)screenshot|screen[-_/]?shot|/screens?/|preview|demo|fastlane.*phonescreenshots|" +
    "metadata/.*/images|\\.gif(\\?|$)"
)

// GitHub's own attachment hosts carry NO file extension — dragging an image into
// a README/issue yields e.g. github.com/<owner>/<repo>/assets/<id>/<uuid>. These
// are overwhelmingly screenshots, and requiring an extension silently dropped
// every one of them (apkupdater's whole gallery went missing this way).
private val githubAttachmentRegex = Regex(
    "(?i)^https://(user-images\\.githubusercontent\\.com/|" +
    "github\\.com/user-attachments/assets/|" +
    "github\\.com/[^/]+/[^/]+/assets/)"
)

private val mdImageRegex  = Regex("""!\[[^\]]*]\(\s*<?([^)\s>]+)""")
private val htmlImgRegex  = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

/**
 * Pull likely screenshot URLs out of a README.
 * @param rawBase e.g. "https://raw.githubusercontent.com/owner/repo/HEAD" — used
 *        to absolutise relative image paths.
 */
fun extractScreenshots(markdown: String, rawBase: String): List<String> {
    if (markdown.isBlank()) return emptyList()

    val raw = (mdImageRegex.findAll(markdown).map { it.groupValues[1] } +
               htmlImgRegex.findAll(markdown).map { it.groupValues[1] })
        .map { it.trim().trim('"', '\'', '<', '>') }
        .filter { it.isNotBlank() }
        .map { url ->
            when {
                url.startsWith("http", ignoreCase = true) -> url
                url.startsWith("//")                      -> "https:$url"
                else -> "${rawBase.trimEnd('/')}/${url.trimStart('/', '.')}"
            }
        }
        // Keep real raster images (or gifs) plus extension-less GitHub attachments;
        // drop badges/logos/svg outright.
        .filter { url ->
            val clean = url.substringBefore('?').substringBefore('#')
            val looksImage = Regex("(?i)\\.(png|jpe?g|webp|gif)$").containsMatchIn(clean) ||
                             githubAttachmentRegex.containsMatchIn(clean)
            looksImage && !screenshotJunkRegex.containsMatchIn(url)
        }
        .distinct()
        .toList()

    // Prefer screenshot-ish URLs, but fall back to any surviving image so an app
    // that just embeds a plain demo picture still shows something.
    val preferred = raw.filter {
        screenshotHintRegex.containsMatchIn(it) || githubAttachmentRegex.containsMatchIn(it)
    }
    return (if (preferred.isNotEmpty()) preferred + raw.filterNot { it in preferred } else raw)
        .take(8)
}

// ── ViewModel ─────────────────────────────────────────────────────────────────
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private var loadPage = 1

    fun openCollection(collection: AppCollection) {
        openSeeAll(collection.emoji + " " + collection.title, collection.query)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[a.length][b.length]
    }

    private fun fuzzyMatch(text: String, query: String): Boolean {
        val t       = text.lowercase()
        val q       = query.lowercase()
        if (t.contains(q)) return true
        val words   = t.split(" ", "-", "_", ".")
        val allowed = maxOf(1, q.length / 4)
        return words.any { levenshtein(it, q) <= allowed } ||
                levenshtein(t.take(q.length + 2), q) <= allowed
    }
    fun toggleFilterMenu(isOpen: Boolean) {
        state = state.copy(isFilterMenuOpen = isOpen)
    }

    fun setSubMenuPlatform(platform: AppPlatform?) {
        state = state.copy(activeSubMenuPlatform = platform)
    }

    fun setSourceFilter(source: AppSource?) {
        state = state.copy(selectedSource = source)
        // Fetch on demand if the live list hasn't been loaded yet
        when (source) {
            AppSource.GITLAB   -> if (state.gitlabApps.isEmpty())   fetchGitLabApps()
            AppSource.CODEBERG -> if (state.codebergApps.isEmpty()) fetchCodebergApps()
            AppSource.FLATHUB  -> if (state.flathubApps.isEmpty())  fetchFlathubApps()
            else               -> {}
        }
    }

    private fun fetchGitLabApps() = viewModelScope.launch {
        try {
            val apps = GitLabClient.service.searchProjects(perPage = 40).map { it.toUnifiedRepo() }
            if (apps.isNotEmpty()) state = state.copy(gitlabApps = apps)
        } catch (_: Exception) {}
    }

    private fun fetchCodebergApps() = viewModelScope.launch {
        try {
            val apps = CodebergClient.service.searchRepos(query = "android", topic = true, limit = 40).data.map { it.toUnifiedRepo() }
            if (apps.isNotEmpty()) state = state.copy(codebergApps = apps)
        } catch (_: Exception) {}
    }

    private fun fetchFlathubApps() = viewModelScope.launch {
        try {
            val hits = FlathubClient.service.getPopular().hits?.map { it.toUnifiedRepo() } ?: emptyList()
            if (hits.isNotEmpty()) {
                state = state.copy(flathubApps = hits)
            } else {
                val fallback = MetadataManager.get().search("", source = "flathub").take(50).map { it.toGitHubRepo() }
                if (fallback.isNotEmpty()) state = state.copy(flathubApps = fallback)
            }
        } catch (_: Exception) {
            try {
                val fallback = MetadataManager.get().search("", source = "flathub").take(50).map { it.toGitHubRepo() }
                if (fallback.isNotEmpty()) state = state.copy(flathubApps = fallback)
            } catch (_: Exception) {}
        }
    }

    fun openSourceBrowse(source: AppSource) {
        val cdnKey = when (source) {
            AppSource.FDROID   -> "fdroid"
            AppSource.GITLAB   -> "gitlab"
            AppSource.CODEBERG -> "codeberg"
            AppSource.FLATHUB  -> "flathub"
            AppSource.WINGET   -> "winget"
            AppSource.GITHUB   -> "github"
            AppSource.IZZY     -> "izzy"
            else               -> null
        }
        if (cdnKey != null) {
            // Show whatever we already loaded on the home screen instantly
            val preloaded = when (source) {
                AppSource.FDROID   -> state.fdroidApps
                AppSource.GITLAB   -> state.gitlabApps
                AppSource.CODEBERG -> state.codebergApps
                AppSource.FLATHUB  -> state.flathubApps
                AppSource.WINGET   -> state.wingetApps
                AppSource.IZZY     -> state.izzyApps
                AppSource.GITHUB   -> (state.trending + state.media + state.tools + state.games +
                                       state.browsers + state.productivity + state.security + state.devtools +
                                       state.photoVideo + state.music + state.finance + state.education +
                                       state.fitness + state.artDesign + state.news + state.social +
                                       state.cloudStorage + state.cooking).distinctBy { it.id }
                else               -> emptyList()
            }
            state = state.copy(
                seeAllTitle     = "${source.label} Apps",
                seeAllApps      = preloaded,
                seeAllQuery     = "",
                seeAllPage      = 1,
                seeAllSource    = cdnKey,
                isLoadingSeeAll = true
            )
            viewModelScope.launch {
                val mutex = Mutex()
                val seen  = preloaded.map { it.id }.toHashSet()

                suspend fun mergeSource(apps: List<GitHubRepo>) {
                    val fresh = apps.filter { seen.add(it.id) }
                    if (fresh.isEmpty()) return
                    state = state.copy(
                        seeAllApps      = (state.seeAllApps + fresh).sortedByDescending { it.stargazers_count },
                        isLoadingSeeAll = false
                    )
                }

                // CDN and live API fire simultaneously; first to return updates the UI
                val j1 = launch {
                    try {
                        val result = MetadataManager.get().browseSource(cdnKey, 1)
                        mutex.withLock { mergeSource(result.apps.map { it.toGitHubRepo() }) }
                    } catch (_: Exception) {}
                }
                val j2 = when (source) {
                    AppSource.GITLAB -> launch {
                        try {
                            val projects = GitLabClient.service.searchProjects(perPage = 30)
                            mutex.withLock { mergeSource(projects.map { it.toUnifiedRepo() }) }
                        } catch (_: Exception) {}
                    }
                    AppSource.CODEBERG -> launch {
                        try {
                            val repos = CodebergClient.service.searchRepos(query = "android", topic = true, limit = 30).data
                            mutex.withLock { mergeSource(repos.map { it.toUnifiedRepo() }) }
                        } catch (_: Exception) {}
                    }
                    // Flathub: live API as fallback
                    AppSource.FLATHUB -> launch {
                        try {
                            val apps = FlathubClient.service.getPopular().hits
                                ?.map { it.toUnifiedRepo() } ?: emptyList()
                            mutex.withLock { mergeSource(apps) }
                        } catch (_: Exception) {
                            // last-ditch: filter global CDN index
                            try {
                                val hits = MetadataManager.get().search("", source = "flathub")
                                    .take(50).map { it.toGitHubRepo() }
                                mutex.withLock { mergeSource(hits) }
                            } catch (_: Exception) {}
                        }
                    }
                    // Winget/F-Droid: global CDN index filtered by source
                    AppSource.WINGET, AppSource.FDROID -> launch {
                        try {
                            val hits = MetadataManager.get().search("", source = cdnKey)
                                .take(50).map { it.toGitHubRepo() }
                            mutex.withLock { mergeSource(hits) }
                        } catch (_: Exception) {}
                    }
                    // GitHub: live API fallback (uses user's token for 5000/hr)
                    AppSource.GITHUB -> launch {
                        try {
                            val items = RetrofitClient.service.searchRepos(
                                "topic:android stars:>100", perPage = 30
                            ).items.map { it.copy(source = AppSource.GITHUB) }
                            mutex.withLock { mergeSource(items) }
                        } catch (_: Exception) {}
                    }
                    // IzzyOnDroid: stream directly from F-Droid index (CDN fallback)
                    AppSource.IZZY -> launch {
                        try {
                            val apps = IzzyOnDroidClient.getApps(100)
                            mutex.withLock { mergeSource(apps) }
                        } catch (_: Exception) {}
                    }
                    else -> null
                }
                j1.join(); j2?.join()
                // Guarantee spinner always stops and falls back to preloaded if nothing arrived
                state = state.copy(
                    seeAllApps      = if (state.seeAllApps.isEmpty()) preloaded else state.seeAllApps,
                    isLoadingSeeAll = false
                )
            }
        } else {
            openSeeAll(source.label, "topic:android stars:>50")
        }
    }

    private fun loadMoreCdnSource(cdnKey: String) {
        if (state.isLoadingSeeAll) return
        val next = state.seeAllPage + 1
        viewModelScope.launch {
            state = state.copy(isLoadingSeeAll = true)
            try {
                val result = MetadataManager.get().browseSource(cdnKey, next)
                state = state.copy(
                    seeAllApps      = state.seeAllApps + result.apps.map { it.toGitHubRepo() },
                    seeAllPage      = next,
                    isLoadingSeeAll = false
                )
            } catch (_: Exception) {
                state = state.copy(isLoadingSeeAll = false)
            }
        }
    }

    fun toggleFavourite(repo: GitHubRepo) {
        val fav    = state.favourites
        val newFav = if (fav.any { it.id == repo.id }) fav.filter { it.id != repo.id }
        else listOf(repo) + fav
        state = state.copy(favourites = newFav)
        prefs.saveFavourites(newFav)
    }

    fun setGithubUsername(name: String) { state = state.copy(githubUsername = name) }

    fun addCustomRepo(repo: CustomRepo) {
        val updated = state.customRepos + repo
        state = state.copy(customRepos = updated)
        prefs.saveCustomRepos(updated)
    }

    fun removeCustomRepo(id: String) {
        val updated = state.customRepos.filter { it.id != id }
        state = state.copy(customRepos = updated)
        prefs.saveCustomRepos(updated)
    }

    fun openCustomRepoBrowse(repo: CustomRepo) {
        val parsed   = try { Uri.parse(repo.url.trim()) } catch (_: Exception) { null }
        val host     = parsed?.host?.lowercase() ?: ""
        val segments = parsed?.pathSegments?.filter { it.isNotBlank() } ?: emptyList()
        when {
            // github.com/owner/reponame — single repo
            (host == "github.com" || host == "www.github.com") && segments.size >= 2 -> {
                val owner    = segments[0]
                val repoName = segments[1]
                openSeeAll(repo.name, "repo:$owner/$repoName")
            }
            // github.com/username — user or org repos
            (host == "github.com" || host == "www.github.com") && segments.size == 1 -> {
                openSeeAll(repo.name, "user:${segments[0]}")
            }
            // gitlab.com — search their API
            host.contains("gitlab.com") -> {
                state = state.copy(
                    seeAllTitle = repo.name, seeAllApps = emptyList(),
                    seeAllQuery = "", seeAllPage = 1, seeAllSource = null,
                    isLoadingSeeAll = true
                )
                viewModelScope.launch {
                    try {
                        val q = segments.lastOrNull() ?: repo.name
                        val hits = safeApi { GitLabClient.service.searchProjects(query = q, perPage = 30) }
                            ?: emptyList()
                        state = state.copy(seeAllApps = hits.map { it.toUnifiedRepo() }, isLoadingSeeAll = false)
                    } catch (_: Exception) { state = state.copy(isLoadingSeeAll = false) }
                }
            }
            // codeberg.org — search their API
            host.contains("codeberg.org") -> {
                state = state.copy(
                    seeAllTitle = repo.name, seeAllApps = emptyList(),
                    seeAllQuery = "", seeAllPage = 1, seeAllSource = null,
                    isLoadingSeeAll = true
                )
                viewModelScope.launch {
                    try {
                        val q = segments.lastOrNull() ?: repo.name
                        val hits = safeApi { CodebergClient.service.searchRepos(query = q, limit = 30) }
                            ?.data?.map { it.toUnifiedRepo() } ?: emptyList()
                        state = state.copy(seeAllApps = hits, isLoadingSeeAll = false)
                    } catch (_: Exception) { state = state.copy(isLoadingSeeAll = false) }
                }
            }
            // fallback — GitHub search by name
            else -> openSeeAll(repo.name, repo.name)
        }
    }

    private val ctx   = app.applicationContext
    private val prefs      = PreferencesManager(ctx)
    private val httpClient = OkHttpClient()
    private val downloadJobs = mutableMapOf<Long, kotlinx.coroutines.Job>()
    private var searchJob  : kotlinx.coroutines.Job? = null
    private var loadJob    : kotlinx.coroutines.Job? = null
    private var platformJob: kotlinx.coroutines.Job? = null

    var state by mutableStateOf(UiState())
        private set

    /**
     * Per-repo install/download state, deliberately kept OUT of [UiState].
     *
     * Download progress ticks ~2.5×/second while an install runs. As a UiState
     * field every tick replaced the whole state object, recomposing every
     * composable that reads `viewModel.state` — i.e. the entire home tree. As a
     * snapshot map, a composable reading `installStates[id]` subscribes to that
     * one key and nothing else recomposes.
     */
    val installStates = mutableStateMapOf<Long, InstallState>()

    init {
        val loadedSettings = prefs.loadSettings()
        // The premium skins are not part of the open-core build, so a stored
        // selection naming one — from Auto Backup, or from a previous install of the
        // paid build — has nothing to render and falls back to Light.
        val savedSettings  = if (loadedSettings.themeMode in PREMIUM_THEME_MODES) {
            loadedSettings.copy(themeMode = "Light").also { prefs.saveSettings(it) }
        } else loadedSettings
        RetrofitClient.authToken = savedSettings.githubToken
        MetadataManager.init(ctx)
        state = state.copy(
            settings           = savedSettings,
            profile            = prefs.loadProfile(),
            history            = prefs.loadHistory(),
            favourites         = prefs.loadFavourites(),
            accentColor        = prefs.loadAccentColor(),
            themeName          = prefs.loadTheme(),
            customTheme        = prefs.loadCustomTheme(),
            categoryViewCounts = prefs.loadCategoryViews(),
            installHistory     = prefs.loadInstallHistory(),
            ignoredVersions    = prefs.loadIgnoredVersions(),
            updates            = prefs.loadUpdates(),
            notifsDismissed    = prefs.loadNotifsDismissed(),
            platform           = prefs.loadSearchPlatform(),
            selectedSubCategories = prefs.loadSearchSubCategories(),
            customRepos         = prefs.loadCustomRepos(),
            liquidGlassUnlocked = false,
            recentSearches      = prefs.loadRecentSearches()
        )
        viewModelScope.launch {
            try { MetadataManager.get().init() } catch (_: Exception) {}
        }
        reconstructInstallStatesFromHistory()
        computeTodayPicks()   // restores today's cached picks instantly
        loadAll()
        checkSelfUpdate()
        fetchAnnouncement()
        fetchFeaturedPins()
    }

    /**
     * Pulls the CDN's pinned hero apps.
     *
     * Silent on every failure: a missing or malformed featured.json means no
     * promotion, which is the correct outcome — the home screen is perfectly usable
     * without it and must never be held up by it.
     */
    private fun fetchFeaturedPins() = viewModelScope.launch {
        try {
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val req = Request.Builder()
                    .url("https://nikhilkain.github.io/appstore-metadata/featured.json")
                    .build()
                httpClient.newCall(req).execute().use { it.body?.string() ?: "" }
            }
            val pins = Gson().fromJson(body, FeaturedPins::class.java) ?: return@launch
            if (!pins.active || pins.isExpired) return@launch
            // A pin needs somewhere to send the user: either an APK Vyxel can install
            // or an external listing it can open. One with neither is a dead card, so
            // it is dropped rather than shown.
            state = state.copy(
                featuredPins = pins.apps.filter {
                    it.repo.isNotBlank() && (it.apkUrl.isNotBlank() || it.storeUrl.isNotBlank())
                }
            )
        } catch (_: Exception) { /* no promotion is never an error */ }
    }

    // announcement.json lives next to the metadata on the CDN. Shown once per
    // id (dismissed ids persist); set active=false server-side to retire early.
    private fun fetchAnnouncement() = viewModelScope.launch {
        try {
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val req = Request.Builder()
                    .url("https://nikhilkain.github.io/appstore-metadata/announcement.json")
                    .build()
                httpClient.newCall(req).execute().use { it.body?.string() ?: "" }
            }
            val ann = Gson().fromJson(body, Announcement::class.java) ?: return@launch
            if (ann.active && !ann.isExpired && ann.id.isNotBlank() &&
                ann.signature !in prefs.loadSeenAnnouncements()) {
                state = state.copy(announcement = ann)
            }
        } catch (_: Exception) { /* no announcement is never an error */ }
    }

    fun dismissAnnouncement() {
        state.announcement?.let { ann ->
            // Non-dismissible announcements reappear on every launch until
            // retired server-side; dismissible ones are remembered per content
            // signature, so editing the text republishes it.
            if (ann.dismissible) {
                prefs.saveSeenAnnouncements(prefs.loadSeenAnnouncements() + ann.signature)
            }
        }
        state = state.copy(announcement = null)
    }

    // Deterministic daily picks: same repo all day (seeded by epoch-day), cached in
    // prefs so the home feed and widget agree and survive process death.
    private fun computeTodayPicks() {
        val today = java.time.LocalDate.now().toString()
        if (state.todayPicks.date == today && state.todayPicks.appOfTheDay != null) return
        val cached = prefs.loadTodayPicks()
        if (cached.date == today && cached.appOfTheDay != null) {
            state = state.copy(todayPicks = cached)
            return
        }
        val pool = allLoadedRepos().filter {
            !it.description.isNullOrBlank() && it.owner.avatar_url.isNotEmpty()
        }
        if (pool.isEmpty()) return
        val rnd  = kotlin.random.Random(java.time.LocalDate.now().toEpochDay())
        val hero = pool.filter { it.stargazers_count >= 300 }.ifEmpty { pool }
        val pick = hero[rnd.nextInt(hero.size)]
        val gems = pool.filter { it.stargazers_count in 30..500 && it.id != pick.id }
        val gem  = if (gems.isEmpty()) null else gems[rnd.nextInt(gems.size)]
        val picks = TodayPicks(appOfTheDay = pick, hiddenGem = gem, date = today)
        prefs.saveTodayPicks(picks)
        state = state.copy(todayPicks = picks)
        TodayWidgetProvider.refreshAll(ctx)
    }

    /**
     * Set when GitHub answers 403/429. Unauthenticated search is 10 requests per
     * minute and the home screen fans out to ~19 — hitting the ceiling used to
     * leave empty rows and no explanation at all.
     */
    @Volatile private var rateLimitNotice: String? = null

    private suspend fun <T> safeApi(block: suspend () -> T): T? = try {
        block()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        noteApiFailure(e)
        null
    }

    private fun noteApiFailure(e: Exception) {
        val code = (e as? retrofit2.HttpException)?.code() ?: return
        if (code == 403 || code == 429) {
            rateLimitNotice = if (state.settings.githubToken.isEmpty())
                "GitHub rate limit reached. Add a GitHub token in Settings to raise the limit " +
                "from 10 to 30 searches per minute, or try again in a minute."
            else
                "GitHub rate limit reached. Please try again in a minute."
        }
    }

    private val cache          = java.util.concurrent.ConcurrentHashMap<String, Pair<List<GitHubRepo>, Long>>()
    private val CACHE_MS       = 15 * 60 * 1000L   // 15 min: in-memory TTL (pull-to-refresh stays fresh)
    private val DISK_CACHE_MS  = 90 * 60 * 1000L   // 90 min: disk TTL (cold restarts skip API calls)
    private val searchCachePrefs = ctx.getSharedPreferences("gh_search_cache", Context.MODE_PRIVATE)
    private val cacheGson = Gson()

    // Live GitHub search is capped at 10 requests/min without a token. Every
    // keystroke-driven query used to spend TWO of those (broad + `in:name`), so a
    // handful of searches exhausted the budget and further live lookups silently
    // returned nothing ("direct to source not working"). Cache each query's
    // results in memory so repeats are free, and fold the two calls into one.
    private val ghSearchCache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<GitHubRepo>, Long>>()
    private val GH_SEARCH_TTL = 5 * 60 * 1000L   // 5 min

    /**
     * One cached GitHub repo search. Serves an in-memory hit when fresh; otherwise
     * hits the API once (a single broad, star-sorted query already matches names,
     * so the old separate `in:name` call was redundant) and caches the result.
     * On rate-limit / failure it falls back to the last cached list for this query,
     * so a momentary 403 doesn't blank out results the user just saw.
     */
    private suspend fun githubSearchCached(finalQuery: String): List<GitHubRepo> {
        val key = finalQuery.trim()
        ghSearchCache[key]?.let { (hits, at) ->
            if (System.currentTimeMillis() - at < GH_SEARCH_TTL) return hits
        }
        val live = safeApi { RetrofitClient.service.searchRepos(key, perPage = 25) }
            ?.items?.map { it.copy(source = AppSource.GITHUB) }
        return if (live != null) {
            ghSearchCache[key] = live to System.currentTimeMillis()
            live
        } else {
            // Rate-limited or offline — reuse a stale cached hit if we have one.
            ghSearchCache[key]?.first ?: emptyList()
        }
    }

    // ── GitHub APK-presence cache ─────────────────────────────────────────────
    // The CDN "github" feed has no apk_url, so it lists frameworks and desktop
    // tools (flutter, scrcpy) alongside real apps. This confirms, per repo,
    // whether a release actually ships an .apk, so the home feed can hide the
    // ones that don't. Verified in the background, cached on disk for 14 days
    // (true=has apk, false=none), so it's paid at most once per repo per window.
    private val apkPresence      = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()
    private val apkPresencePrefs = ctx.getSharedPreferences("apk_presence", Context.MODE_PRIVATE)
    private val APK_PRESENCE_MS   = 14L * 24 * 60 * 60 * 1000L
    @Volatile private var apkVerifyRunning = false
    private val apkVerifyHttp = OkHttpClient.Builder()
        .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()

    /**
     * GitHub releases for a repo from the CDN's static cache
     * (data/releases/github/{owner}/{name}.json). No auth, no GitHub rate limit —
     * it's github.io. Returns null when the repo isn't cached. This is the SAME
     * source [fetchRelease] tries first; the background APK check reuses it so it
     * never touches the live API (which was starving foreground release fetches
     * and producing spurious "No releases found").
     */
    private suspend fun cdnReleasesFor(owner: String, name: String): List<Release>? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = "https://NikhilKain.github.io/appstore-metadata/data/releases/github/$owner/$name.json"
                apkVerifyHttp.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val body = r.body?.string()
                    if (body.isNullOrBlank()) null
                    else Gson().fromJson(body, object : TypeToken<List<Release>>() {}.type)
                }
            } catch (_: Exception) { null }
        }

    /** Completes when the disk cache has been read into memory; see [warmSearchCache]. */
    private val searchCacheWarm = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** The shared hidden-apps store, written by whichever shell the user is in. */
    private val sharedSettings =
        com.vythera.vyxelapps.expressive.data.SettingsStore(ctx)

    init {
        viewModelScope.launch { warmSearchCache() }
        viewModelScope.launch { warmApkPresence() }
        // Follows the store rather than reading it once, so a hide performed in the
        // Expressive shell takes effect here without a restart.
        viewModelScope.launch {
            sharedSettings.settings.collect { settings ->
                if (settings.hiddenPackages != state.hiddenPackages) {
                    state = state.copy(hiddenPackages = settings.hiddenPackages)
                }
            }
        }
    }

    /** Hides or restores one package across every source, in both shells. */
    fun setHidden(repo: GitHubRepo, hidden: Boolean) {
        val pkg = repo.packageId.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch { sharedSettings.setHidden(pkg, hidden) }
    }

    fun clearHidden() {
        viewModelScope.launch { sharedSettings.clearHidden() }
    }

    /** Load the apk-presence booleans off disk once, dropping expired ones. */
    private suspend fun warmApkPresence() {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val now  = System.currentTimeMillis()
                val all  = apkPresencePrefs.all
                val dead = mutableListOf<String>()
                all.keys.filter { !it.endsWith("_ts") }.forEach { key ->
                    val ts = (all["${key}_ts"] as? Long) ?: 0L
                    val id = key.toLongOrNull()
                    if (id != null && now - ts < APK_PRESENCE_MS) {
                        (all[key] as? Boolean)?.let { apkPresence[id] = it }
                    } else dead += key
                }
                if (dead.isNotEmpty()) apkPresencePrefs.edit().apply {
                    dead.forEach { remove(it); remove("${it}_ts") }
                }.apply()
            }
        } catch (_: Exception) {}
    }

    private fun rememberApkPresence(id: Long, hasApk: Boolean) {
        apkPresence[id] = hasApk
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                apkPresencePrefs.edit()
                    .putBoolean(id.toString(), hasApk)
                    .putLong("${id}_ts", System.currentTimeMillis())
                    .apply()
            } catch (_: Exception) {}
        }
    }

    /**
     * Confirm which of the currently-shown GitHub repos actually ship an APK, and
     * add the ones that don't to [UiState.apkAbsentIds] so the home rows drop them.
     *
     * Checks the CDN release cache ONLY — never the live GitHub API. The previous
     * version fired up to 120 live getReleases per load; that tripped GitHub's
     * secondary (concurrency) rate limit and 403'd the user's own foreground
     * release fetch, so most detail pages showed "No releases found". github.io is
     * static and unmetered, so this can't starve anything. Repos not in the CDN
     * cache are left shown (optimistic) rather than probed live.
     */
    private fun verifyGithubApks() {
        // Apply what the cache already knows straight away — cheap and offline.
        val known = allLoadedRepos()
            .filter { it.source == AppSource.GITHUB || it.source == null }
            .filter { apkPresence[it.id] == false }
            .map { it.id }
            .toSet()
        if (known.isNotEmpty()) {
            state = state.copy(apkAbsentIds = state.apkAbsentIds + known)
        }

        if (apkVerifyRunning) return

        val toCheck = allLoadedRepos()
            .filter { (it.source == AppSource.GITHUB || it.source == null) }
            .filter { it.id !in apkPresence.keys }
            .filter { it.owner.login.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.id }
            .take(120)
        if (toCheck.isEmpty()) return

        apkVerifyRunning = true
        viewModelScope.launch {
            try {
                // Small head start so it doesn't fight the first screen's image
                // loads for bandwidth; no GitHub API involved either way.
                delay(2_500)
                val semaphore = Semaphore(4)
                val absent    = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
                toCheck.map { repo ->
                    launch {
                        semaphore.withPermit {
                            // null = not in the CDN cache → leave it shown.
                            val releases = cdnReleasesFor(repo.owner.login, repo.name)
                                ?: return@withPermit
                            val hasApk = releases.any { rel -> rel.assets.any { it.isApk() } }
                            rememberApkPresence(repo.id, hasApk)
                            if (!hasApk) absent += repo.id
                        }
                    }
                }.joinAll()
                if (isActive && absent.isNotEmpty()) {
                    state = state.copy(apkAbsentIds = state.apkAbsentIds + absent)
                }
            } catch (_: Exception) {
            } finally {
                apkVerifyRunning = false
            }
        }
    }

    /**
     * Warm the in-memory cache from disk so cold restarts skip the 19 GitHub API
     * calls — off the main thread, because `prefs.all` reads *and parses* the
     * whole file, which used to happen before the first frame.
     *
     * Expired entries are dropped from disk here too. They were previously only
     * skipped, so the file grew without bound (keys include the page number) and
     * every launch paid to parse more of it.
     */
    private suspend fun warmSearchCache() {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val type: java.lang.reflect.Type = object : TypeToken<List<GitHubRepo>>() {}.type
                val now     = System.currentTimeMillis()
                val all     = searchCachePrefs.all
                val expired = mutableListOf<String>()
                all.keys.filter { !it.endsWith("_ts") }.forEach { key ->
                    val ts = (all["${key}_ts"] as? Long) ?: 0L
                    if (now - ts < DISK_CACHE_MS) {
                        val json = all[key] as? String ?: return@forEach
                        try { cache[key] = cacheGson.fromJson<List<GitHubRepo>>(json, type) to ts }
                        catch (_: Exception) { expired += key }
                    } else expired += key
                }
                if (expired.isNotEmpty()) {
                    searchCachePrefs.edit().apply {
                        expired.forEach { remove(it); remove("${it}_ts") }
                    }.apply()
                }
            }
        } catch (_: Exception) {
        } finally {
            searchCacheWarm.complete(Unit)
        }
    }

    private suspend fun searchCached(
        query   : String,
        perPage : Int = 20,
        page    : Int = 1
    ): SearchResponse? {
        searchCacheWarm.await()
        val key    = "$query|$page|$perPage"
        val cached = cache[key]
        if (cached != null && System.currentTimeMillis() - cached.second < CACHE_MS)
            return SearchResponse(cached.first)
        val result    = safeApi { RetrofitClient.service.searchRepos(query, perPage = perPage, page = page) }
            ?: return null
        val safeItems = try { result.items } catch (_: Throwable) { null } ?: emptyList()
        val now       = System.currentTimeMillis()
        cache[key]    = safeItems to now
        // Persist to disk so the next cold start skips this API call. Serialising
        // 20 repos is real CPU work — it does not belong on the main thread.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                searchCachePrefs.edit()
                    .putString(key, cacheGson.toJson(safeItems))
                    .putLong("${key}_ts", now)
                    .apply()
            } catch (_: Exception) {}
        }
        return SearchResponse(safeItems)
    }

    fun loadAll() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(80)
            loadPage = (loadPage % 8) + 1
            state = state.copy(isLoading = true, error = null, trendingPage = 1, refreshToken = state.refreshToken + 1)

            // ── Phase 1: CDN sources — GitHub awaited, others fire-and-forget ──
            // Non-GitHub sources update state as they arrive; only GitHub blocks
            // because home-screen category rows depend on it.
            // IzzyOnDroid live fetch is intentionally excluded here — it downloads
            // a large JSON and is only loaded when the user opens Browse → Izzy.
            try {
                val c = MetadataManager.get()

                // requireApk: F-Droid and IzzyOnDroid publish a real apk_url per
                // entry, so we can reliably drop anything without an installable
                // APK (e.g. desktop-only projects that slip into the feed).
                suspend fun cdnSource(key: String, requireApk: Boolean = false): List<GitHubRepo> =
                    try {
                        c.browseSource(key, 1).apps
                            .filter { it.isLikelyApp() }
                            .map { it.toGitHubRepo() }
                            .filter { !requireApk || it.apkUrl.endsWith(".apk", ignoreCase = true) }
                    } catch (_: Exception) { emptyList() }

                // Non-critical sources: update state independently as each finishes
                launch { val gl = cdnSource("gitlab"); if (isActive && gl.isNotEmpty()) state = state.copy(gitlabApps = gl) }
                launch { val cb = cdnSource("codeberg"); if (isActive && cb.isNotEmpty()) state = state.copy(codebergApps = cb) }
                launch { val fd = cdnSource("fdroid", requireApk = true); if (isActive && fd.isNotEmpty()) state = state.copy(fdroidApps = fd) }
                launch { val fh = cdnSource("flathub"); if (isActive && fh.isNotEmpty()) state = state.copy(flathubApps = fh) }
                launch { val wg = cdnSource("winget"); if (isActive && wg.isNotEmpty()) state = state.copy(wingetApps = wg) }
                launch { val iz = cdnSource("izzy", requireApk = true); if (isActive && iz.isNotEmpty()) state = state.copy(izzyApps = iz) }
                launch {
                    val nl = cdnSource("newly-launched")
                    if (isActive && nl.isNotEmpty()) state = state.copy(newlyLaunched = nl)
                }

                // GitHub is awaited — home screen category rows depend on it
                val ghEntries = try { c.browseSource("github", 1).apps }
                                catch (_: Exception) { emptyList<com.vythera.vyxelapps.api.AppEntry>() }
                val ghList = ghEntries.ifEmpty {
                    try { c.search("", source = "github").take(200) } catch (_: Exception) { emptyList() }
                }

                if (!isActive) { state = state.copy(isLoading = false); return@launch }

                // Homepage rows must be installable apps, not docs/collections
                val ghClean = ghList.filter { it.isLikelyApp() }

                fun List<com.vythera.vyxelapps.api.AppEntry>.toRows(vararg kws: String): List<GitHubRepo> {
                    val matched = filter { e ->
                        kws.any { k ->
                            e.categories.any { cat -> k in cat.lowercase() } ||
                            k in e.summary.lowercase() || k in e.name.lowercase()
                        }
                    }
                    return (if (matched.isNotEmpty()) matched else this).shuffled().take(20).map { it.toGitHubRepo() }
                }

                state = if (ghClean.isNotEmpty()) {
                    state.copy(
                        isLoading    = false,
                        trending     = ghClean.shuffled().take(20).map { it.toGitHubRepo() },
                        media        = ghClean.toRows("media", "player", "video", "stream"),
                        tools        = ghClean.toRows("tool", "utility", "manager"),
                        games        = ghClean.toRows("game", "emulat"),
                        browsers     = ghClean.toRows("browser"),
                        productivity = ghClean.toRows("productivity", "note", "office", "task"),
                        security     = ghClean.toRows("security", "privacy", "encrypt"),
                        devtools     = ghClean.toRows("developer", "dev-tool", "terminal", "ssh"),
                        photoVideo   = ghClean.toRows("photo", "camera", "gallery", "image"),
                        music        = ghClean.toRows("music", "podcast", "radio"),
                        finance      = ghClean.toRows("finance", "banking", "budget", "money"),
                        education    = ghClean.toRows("education", "learn", "study", "language"),
                        fitness      = ghClean.toRows("fitness", "health", "workout", "exercise"),
                        artDesign    = ghClean.toRows("art", "design", "draw", "creative"),
                        news         = ghClean.toRows("news", "rss", "feed", "reader"),
                        social       = ghClean.toRows("social", "messag", "chat", "network"),
                        cloudStorage = ghClean.toRows("cloud", "storage", "backup", "sync"),
                        cooking      = ghClean.toRows("cooking", "food", "recipe")
                    )
                } else {
                    state.copy(isLoading = false)
                }
            } catch (e: Exception) {
                state = state.copy(isLoading = false)
                if (e is kotlinx.coroutines.CancellationException) throw e
            }

            updateRecommendations()
            computeTodayPicks()

            // ── Phase 2: Live API fallback for GitHub category rows ───────────
            // Only fires when CDN github source returned no data.
            if (state.trending.isEmpty() && isActive) {
                // Batch 1: primary rows
                try {
                    val tD = async { searchCached("topic:android apk stars:>100",          perPage = 20, page = loadPage) }
                    val mD = async { searchCached("topic:android media player stars:>50",  perPage = 20, page = loadPage) }
                    val uD = async { searchCached("topic:android utility tool stars:>50",  perPage = 20, page = loadPage) }
                    val gD = async { searchCached("topic:android game emulator stars:>50", perPage = 20, page = loadPage) }
                    val t = tD.await(); val m = mD.await(); val u = uD.await(); val g = gD.await()
                    if (!isActive) return@launch
                    state = state.copy(
                        trending = t?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        media    = m?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        tools    = u?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        games    = g?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList()
                    )
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }

                updateRecommendations()

                // Batch 2: secondary categories
                if (!isActive) return@launch
                try {
                    val brD = async { searchCached("topic:android browser privacy stars:>50",    perPage = 20, page = loadPage) }
                    val prD = async { searchCached("topic:android productivity notes stars:>50", perPage = 20, page = loadPage) }
                    val seD = async { searchCached("topic:android security stars:>50",           perPage = 20, page = loadPage) }
                    val deD = async { searchCached("topic:android developer-tools stars:>50",    perPage = 20, page = loadPage) }
                    val br = brD.await(); val pr = prD.await(); val se = seD.await(); val de = deD.await()
                    if (!isActive) return@launch
                    state = state.copy(
                        browsers     = br?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        productivity = pr?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        security     = se?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        devtools     = de?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList()
                    )
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }

                // Batch 3: entertainment / lifestyle
                if (!isActive) return@launch
                try {
                    val pvD = async { searchCached("topic:android photo video editor stars:>100", perPage = 20, page = loadPage) }
                    val muD = async { searchCached("topic:android music audio stars:>100",        perPage = 20, page = loadPage) }
                    val fiD = async { searchCached("topic:android finance banking stars:>100",    perPage = 20, page = loadPage) }
                    val edD = async { searchCached("topic:android education learning stars:>100", perPage = 20, page = loadPage) }
                    val pv = pvD.await(); val mu = muD.await(); val fi = fiD.await(); val ed = edD.await()
                    if (!isActive) return@launch
                    state = state.copy(
                        photoVideo = pv?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        music      = mu?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        finance    = fi?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        education  = ed?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList()
                    )
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }

                // Batch 4: remaining rows
                if (!isActive) return@launch
                try {
                    val ftD = async { searchCached("topic:android fitness health workout stars:>100", perPage = 20, page = loadPage) }
                    val arD = async { searchCached("topic:android art design creative stars:>100",   perPage = 20, page = loadPage) }
                    val nwD = async { searchCached("topic:android news reader stars:>100",           perPage = 20, page = loadPage) }
                    val scD = async { searchCached("topic:android social network stars:>100",        perPage = 20, page = loadPage) }
                    val csD = async { searchCached("topic:android cloud storage files stars:>100",   perPage = 20, page = loadPage) }
                    val ckD = async { searchCached("topic:android cooking food recipe stars:>50",    perPage = 20, page = loadPage) }
                    val ft = ftD.await(); val ar = arD.await(); val nw = nwD.await()
                    val sc = scD.await(); val cs = csD.await(); val ck = ckD.await()
                    if (!isActive) return@launch
                    state = state.copy(
                        fitness      = ft?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        artDesign    = ar?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        news         = nw?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        social       = sc?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        cloudStorage = cs?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList(),
                        cooking      = ck?.items?.filter { it.isLikelyApp() }?.shuffled() ?: emptyList()
                    )
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
            }

            // Newly Launched — live GitHub search fallback if the CDN shelf is
            // missing/empty (e.g. before the CDN workflow first publishes it).
            if (state.newlyLaunched.isEmpty() && isActive) {
                val cutoff = java.time.LocalDate.now().minusDays(45).toString()
                val fresh  = searchCached("topic:android created:>$cutoff stars:>4", perPage = 30)
                val apps   = fresh?.items?.filter { it.isLikelyApp() }.orEmpty()
                if (state.newlyLaunched.isEmpty() && apps.isNotEmpty()) {
                    state = state.copy(newlyLaunched = apps)
                }
            }

            updateRecommendations()
            computeTodayPicks()

            // Every source failed and nothing was cached: say why instead of
            // leaving the user staring at empty shelves.
            if (isActive && allLoadedRepos().isEmpty()) {
                state = state.copy(
                    error = rateLimitNotice
                        ?: "Couldn't reach GitHub or the Vyxel index. Check your connection and try again."
                )
            }
            rateLimitNotice = null

            verifyGithubApks()
            checkForUpdatesNow()
        }
    }

    private fun updateRecommendations() {
        val counts      = state.categoryViewCounts
        val categoryMap = mapOf(
            "trending"    to state.trending,    "media"    to state.media,
            "tools"       to state.tools,       "games"    to state.games,
            "browsers"    to state.browsers,    "productivity" to state.productivity,
            "photo"       to state.photoVideo,  "music"    to state.music,
            "finance"     to state.finance,     "education" to state.education,
            "fitness"     to state.fitness,     "artDesign" to state.artDesign
        )
        val recs = if (counts.isEmpty()) {
            (state.trending + state.media).shuffled().take(20)
        } else {
            counts.entries.sortedByDescending { it.value }.take(4)
                .flatMap { categoryMap[it.key]?.take(8) ?: emptyList() }
                .distinctBy { it.id }.shuffled().take(20)
        }
        state = state.copy(recommendations = recs)
    }

    fun trackCategory(category: String) {
        val counts = state.categoryViewCounts.toMutableMap()
        counts[category] = (counts[category] ?: 0) + 1
        state = state.copy(categoryViewCounts = counts)
        prefs.saveCategoryViews(counts)
        updateRecommendations()
    }

    fun loadMoreTrending() {
        if (state.isLoadingMore) return
        val next = state.trendingPage + 1
        viewModelScope.launch {
            state = state.copy(isLoadingMore = true)
            try {
                val more = RetrofitClient.service.searchRepos("topic:android apk stars:>500", perPage = 20, page = next)
                state = state.copy(
                    trending      = state.trending + more.items.filter { it.isLikelyApp() },
                    trendingPage  = next,
                    isLoadingMore = false
                )
            } catch (_: Exception) { state = state.copy(isLoadingMore = false) }
        }
    }

    fun setPlatform(p: AppPlatform) {
        val platformChanged = p != state.platform
        val newSubs = if (platformChanged) emptySet() else state.selectedSubCategories
        state = state.copy(platform = p, selectedSubCategories = newSubs)
        prefs.saveSearchPlatform(p)
        if (platformChanged) prefs.saveSearchSubCategories(emptySet())
        if (p != AppPlatform.ALL) loadPlatformApps(p)
        onSearch(state.searchQuery)
    }

    fun clearSearchFilter() {
        state = state.copy(platform = AppPlatform.ALL, selectedSubCategories = emptySet())
        prefs.saveSearchPlatform(AppPlatform.ALL)
        prefs.saveSearchSubCategories(emptySet())
        onSearch(state.searchQuery)
    }

    fun toggleSubCategory(sub: String) {
        val current = state.selectedSubCategories
        val updated = if (current.contains(sub)) current - sub else current + sub
        state = state.copy(selectedSubCategories = updated)
        prefs.saveSearchSubCategories(updated)
        onSearch(state.searchQuery)
    }

    private fun loadPlatformApps(platform: AppPlatform) {
        platformJob?.cancel()
        platformJob = viewModelScope.launch {
            state = state.copy(isLoading = true, error = null, platformApps = emptyList())
            val q = when (platform) {
                AppPlatform.ANDROID -> "topic:android apk stars:>200"
                AppPlatform.WINDOWS -> "topic:windows stars:>200"
                AppPlatform.LINUX   -> "topic:linux stars:>200"
                AppPlatform.TV      -> "topic:android-tv stars:>30"
                AppPlatform.IOS     -> "topic:ios stars:>30"
                else                -> return@launch
            }
            try {
                val r = RetrofitClient.service.searchRepos(q, perPage = 30)
                state = state.copy(platformApps = r.items, isLoading = false)
            } catch (e: Exception) {
                state = state.copy(
                    isLoading = false,
                    error     = "Could not load ${platform.label} apps. Check your connection."
                )
            }
        }
    }

    // Recognise a pasted GitHub URL or a bare "owner/repo" so we can resolve it
    // directly — GitHub's search endpoint returns nothing for a full repo path.
    private fun parseOwnerRepo(input: String): Pair<String, String>? {
        var s = input.trim()
        val ghIdx = s.indexOf("github.com/")
        if (ghIdx >= 0) s = s.substring(ghIdx + "github.com/".length)
        s = s.trim('/').substringBefore('?').substringBefore('#')
        val parts = s.split("/")
        if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank() &&
            !parts[0].contains(' ') && !parts[1].contains(' ')) {
            return parts[0] to parts[1].removeSuffix(".git")
        }
        return null
    }

    private var recentSearchJob: kotlinx.coroutines.Job? = null

    // Records the query once typing settles — the job is cancelled by the next
    // keystroke, so intermediate prefixes never land in the list.
    private fun scheduleRecentSearch(q: String) {
        recentSearchJob?.cancel()
        val query = q.trim()
        if (query.length < 3) return
        recentSearchJob = viewModelScope.launch {
            delay(1500)
            val list = (listOf(query) + state.recentSearches.filter {
                !it.equals(query, ignoreCase = true) &&
                !query.startsWith(it, ignoreCase = true)   // drop prefixes of the new term
            }).take(10)
            state = state.copy(recentSearches = list)
            prefs.saveRecentSearches(list)
        }
    }

    fun clearRecentSearches() {
        state = state.copy(recentSearches = emptyList())
        prefs.saveRecentSearches(emptyList())
    }

    // ── Backup & restore ──────────────────────────────────────────────────────
    fun exportBackupJson(): String = Gson().toJson(VyxelBackup(
        exportedAt     = System.currentTimeMillis(),
        favourites     = state.favourites,
        trackedApps    = state.settings.trackedApps,
        customRepos    = state.customRepos,
        installHistory = state.installHistory
    ))

    // Merges (never replaces) the backup into current data, so importing an old
    // file can't wipe anything added since. Returns false for unreadable files.
    fun importBackupJson(json: String): Boolean {
        return try {
            val b = Gson().fromJson(json, VyxelBackup::class.java) ?: return false
            if (b.version < 1) return false
            val favs    = (state.favourites + b.favourites.orEmpty()).distinctBy { it.id }
            val tracked = (state.settings.trackedApps + b.trackedApps.orEmpty())
                .distinctBy { it.packageName }
            val repos   = (state.customRepos + b.customRepos.orEmpty()).distinctBy { it.url }
            val hist    = (state.installHistory + b.installHistory.orEmpty())
                .distinctBy { "${it.repoId}:${it.tagName}" }
            val newSettings = state.settings.copy(trackedApps = tracked)
            state = state.copy(
                favourites     = favs,
                customRepos    = repos,
                installHistory = hist,
                settings       = newSettings
            )
            prefs.saveFavourites(favs)
            prefs.saveCustomRepos(repos)
            prefs.saveInstallHistory(hist)
            prefs.saveSettings(newSettings)
            reconstructInstallStatesFromHistory()
            true
        } catch (_: Exception) { false }
    }

    fun onSearch(q: String) {
        state = state.copy(searchQuery = q)
        scheduleRecentSearch(q)
        if (q.isBlank() && state.selectedSubCategories.isEmpty() && state.platform == AppPlatform.ALL) {
            state = state.copy(searchResults = emptyList(), isSearching = false)
            return
        }

        val platformQuery = when (state.platform) {
            AppPlatform.ANDROID -> "topic:android"
            AppPlatform.WINDOWS -> "topic:windows"
            AppPlatform.LINUX   -> "topic:linux"
            AppPlatform.TV      -> "topic:android-tv"
            AppPlatform.IOS     -> "topic:ios"
            else                -> ""
        }
        val subQueries = state.selectedSubCategories.joinToString(" ") { "topic:${it.lowercase().replace(" ", "-")}" }
        val finalQuery = listOf(q, platformQuery, subQueries).filter { it.isNotBlank() }.joinToString(" ")

        val allLoaded = (state.trending + state.media + state.tools + state.games +
                state.browsers + state.productivity + state.security + state.devtools +
                state.photoVideo + state.music + state.finance + state.education +
                state.fitness + state.artDesign + state.news + state.social +
                state.cloudStorage + state.cooking +
                state.gitlabApps + state.codebergApps +
                state.fdroidApps + state.izzyApps + state.flathubApps + state.wingetApps
        ).distinctBy { it.id }

        // Normalize: GitHub repos loaded from the API have source=null (Gson ignores Kotlin defaults)
        val allNormalized = allLoaded.map { if (it.source == null) it.copy(source = AppSource.GITHUB) else it }

        val localMatches = if (q.isNotBlank()) {
            val qt = q.trim().lowercase()
            allNormalized.filter { repo ->
                fuzzyMatch(repo.name, qt) ||
                        fuzzyMatch(repo.owner.login, qt) ||
                        (!repo.description.isNullOrEmpty() && fuzzyMatch(repo.description, qt))
            }.rankByRelevance(q)
        } else {
            // No text — show loaded apps that match the active platform filter
            val platformSources: Set<AppSource>? = when (state.platform) {
                AppPlatform.ANDROID -> setOf(AppSource.GITHUB, AppSource.IZZY, AppSource.FDROID)
                AppPlatform.WINDOWS -> setOf(AppSource.WINGET)
                AppPlatform.LINUX   -> setOf(AppSource.FLATHUB, AppSource.CODEBERG, AppSource.GITLAB)
                AppPlatform.TV      -> setOf(AppSource.GITHUB, AppSource.IZZY)
                AppPlatform.IOS     -> emptySet()
                else                -> null   // ALL with no text → keep empty (default state)
            }
            if (platformSources != null)
                allNormalized.filter { it.source in platformSources }
                             .sortedByDescending { it.stargazers_count }
            else emptyList()
        }

        state = state.copy(searchResults = localMatches, isSearching = q.isNotBlank())

        if (q.isBlank()) return  // live API only fires with text; platform filter uses local data above

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(120)
            val mutex = Mutex()
            val seen  = localMatches.map { it.id }.toHashSet()

            // Merges new results into state; must be called inside mutex.withLock
            suspend fun merge(newItems: List<GitHubRepo>) {
                val fresh = newItems.filter { seen.add(it.id) }
                if (fresh.isEmpty()) return
                state = state.copy(
                    searchResults = (state.searchResults + fresh).rankByRelevance(q)
                )
            }

            val qt = q.trim()

            // All searches fire simultaneously; each updates the UI as it finishes.
            // GitHub goes through the cached single-call helper to conserve the
            // 10/min anonymous search budget (see githubSearchCached).
            val j1 = launch {
                val hits = githubSearchCached(finalQuery)
                if (hits.isNotEmpty()) mutex.withLock { merge(hits) }
            }
            val j3 = launch {
                safeApi { GitLabClient.service.searchProjects(query = qt, perPage = 15) }
                    ?.map { it.toUnifiedRepo() }
                    ?.let { mutex.withLock { merge(it) } }
            }
            val j4 = launch {
                safeApi { CodebergClient.service.searchRepos(query = qt, limit = 15) }
                    ?.data?.map { it.toUnifiedRepo() }
                    ?.let { mutex.withLock { merge(it) } }
            }
            val j5 = launch {
                // F-Droid / Winget / GitLab / Codeberg from CDN index
                try {
                    val cdnHits = MetadataManager.get().search(qt).take(20).map { it.toGitHubRepo() }
                    mutex.withLock { merge(cdnHits) }
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
            }
            // Aptoide and the root-module catalogue.
            //
            // Classic's own sources are open-source-only by construction, so a
            // search for a proprietary app by name — Instagram, WhatsApp — returned
            // GitHub repos *about* it and never the app. These are the same two
            // sources the Expressive shell gained; running them here means both
            // shells answer the query the same way instead of Classic being the one
            // that cannot find anything from Play.
            val j8 = launch {
                try {
                    val hits = com.vythera.vyxelapps.expressive.data.source
                        .AptoideSource().search(qt)
                        .map { it.toGitHubRepo() }
                    if (hits.isNotEmpty()) mutex.withLock { merge(hits) }
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
            }
            val j9 = launch {
                try {
                    // Straight off the CDN's pre-built catalogue — one conditional
                    // GET, so this costs nothing next to the live sources above.
                    val needle = qt.lowercase()
                    val hits = com.vythera.vyxelapps.expressive.data.source
                        .CdnSource(ctx).modules()
                        .filter { m ->
                            m.name.lowercase().contains(needle) ||
                                m.packageName.orEmpty().lowercase().contains(needle) ||
                                m.summary.lowercase().contains(needle)
                        }
                        .take(20)
                        .map { it.toGitHubRepo() }
                    if (hits.isNotEmpty()) mutex.withLock { merge(hits) }
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
            }

            val j6 = launch {
                // Flathub live search (not in CDN index)
                try {
                    val hits = FlathubClient.service.search(FlathubSearchBody(qt)).hits?.map { it.toUnifiedRepo() } ?: emptyList()
                    mutex.withLock { merge(hits) }
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
            }
            // Direct GitHub repo lookup for "owner/repo" or a pasted GitHub URL —
            // covers repos the search ranking can't surface (e.g. MorpheApp/MicroG-RE).
            val j7 = launch {
                val or = parseOwnerRepo(qt) ?: return@launch
                safeApi { RetrofitClient.service.getRepo(or.first, or.second) }
                    ?.copy(source = AppSource.GITHUB)
                    ?.let { mutex.withLock { merge(listOf(it)) } }
            }

            j1.join(); j3.join(); j4.join(); j5.join(); j6.join(); j7.join(); j8.join(); j9.join()
            state = state.copy(isSearching = false)
        }
    }

    fun openSeeAll(title: String, query: String) {
        state = state.copy(
            seeAllTitle     = title,
            seeAllQuery     = query,
            seeAllApps      = emptyList(),
            seeAllPage      = 1,
            seeAllSource    = null,
            isLoadingSeeAll = true
        )
        viewModelScope.launch {
            try {
                val r = RetrofitClient.service.searchRepos(query, perPage = 30, page = 1)
                state = state.copy(seeAllApps = r.items, isLoadingSeeAll = false)
            } catch (e: Exception) {
                state = state.copy(isLoadingSeeAll = false)
            }
        }
    }

    fun loadMoreSeeAll() {
        val cdnKey = state.seeAllSource
        if (cdnKey != null) { loadMoreCdnSource(cdnKey); return }
        if (state.isLoadingSeeAll || state.seeAllQuery.isEmpty()) return
        val next = state.seeAllPage + 1
        viewModelScope.launch {
            state = state.copy(isLoadingSeeAll = true)
            try {
                val r = RetrofitClient.service.searchRepos(state.seeAllQuery, perPage = 30, page = next)
                state = state.copy(
                    seeAllApps      = state.seeAllApps + r.items,
                    seeAllPage      = next,
                    isLoadingSeeAll = false
                )
            } catch (_: Exception) {
                state = state.copy(isLoadingSeeAll = false)
            }
        }
    }

    fun updateProfile(p: UserProfile) { state = state.copy(profile = p); prefs.saveProfile(p) }

    fun addToHistory(repo: GitHubRepo) {
        val filtered = state.history.filter { it.repo.id != repo.id }
        val newH     = listOf(HistoryItem(repo)) + filtered
        state = state.copy(history = newH); prefs.saveHistory(newH)
    }
    fun clearHistory() { state = state.copy(history = emptyList()); prefs.saveHistory(emptyList()) }

    fun setTheme(t: ThemeName)          { state = state.copy(themeName = t); prefs.saveTheme(t) }
    fun setAccentColor(c: Color?)       { state = state.copy(accentColor = c); prefs.saveAccentColor(c) }
    fun setCustomTheme(d: CustomThemeData) { state = state.copy(customTheme = d); prefs.saveCustomTheme(d) }

    fun setLicenseKeyInput(v: String) {
        state = state.copy(licenseKeyInput = v, licenseVerifyState = LicenseVerifyState.IDLE)
    }

    /**
     * No-op in the open-core build.
     *
     * Licence verification and the entitlement service belong to the paid build.
     * The entry point is kept so the settings path compiles unchanged, and it
     * reports INVALID rather than pretending to succeed — there is nothing here to
     * unlock, and a build that answered "unlocked" would be lying to the user.
     */
    fun verifyLicenseKey() {
        state = state.copy(licenseVerifyState = LicenseVerifyState.INVALID)
    }


    private fun allLoadedRepos() = (state.trending + state.media + state.tools + state.games +
        state.browsers + state.productivity + state.security + state.devtools +
        state.photoVideo + state.music + state.finance + state.education +
        state.fitness + state.artDesign + state.news + state.social +
        state.cloudStorage + state.cooking + state.gitlabApps + state.codebergApps +
        state.fdroidApps + state.izzyApps + state.flathubApps + state.wingetApps
    ).distinctBy { it.id }

    private fun reconstructInstallStatesFromHistory() {
        viewModelScope.launch {
            val pm      = ctx.packageManager
            val history = state.installHistory
            if (history.isEmpty()) return@launch
            // Keep only the latest install per repo
            val latest  = history.groupBy { it.repoId }.mapValues { (_, v) -> v.maxByOrNull { it.installedAt }!! }
            val newStates = installStates.toMutableMap()
            for ((repoId, entry) in latest) {
                if (newStates[repoId]?.repo != null) continue  // already have full data
                val isInstalled = if (entry.packageName.isNotEmpty()) {
                    try { pm.getPackageInfo(entry.packageName, 0); true }
                    catch (_: PackageManager.NameNotFoundException) { false }
                } else false
                val minRepo = GitHubRepo(
                    id        = repoId,
                    name      = entry.repoName,
                    full_name = "${entry.ownerLogin}/${entry.repoName}",
                    owner     = RepoOwner(login = entry.ownerLogin)
                )
                newStates[repoId] = (newStates[repoId] ?: InstallState()).copy(
                    isInstalled = isInstalled,
                    packageName = entry.packageName.takeIf { it.isNotEmpty() },
                    repo        = minRepo
                )
            }
            if (newStates.isNotEmpty()) installStates.putAll(newStates)
        }
    }

    fun updateSettings(s: AppSettings)  {
        val languageChanged = s.language != state.settings.language
        val tokenChanged    = s.githubToken != state.settings.githubToken
        state = state.copy(settings = s)
        if (languageChanged) state = state.copy(translatedDescriptions = emptyMap(), translatedReadmes = emptyMap())
        RetrofitClient.authToken = s.githubToken
        // The stored readings describe the budget the *old* token had — 60/hour
        // anonymous against 5000 authenticated. Keeping them would show a ceiling the
        // user no longer has; they refill on the next request either way.
        if (tokenChanged) com.vythera.vyxelapps.api.GitHubRateLimit.reset()
        prefs.saveSettings(s)
    }

    fun fetchRelease(repo: GitHubRepo) {
        if (installStates[repo.id]?.release != null) return
        viewModelScope.launch {
            // ── F-Droid & IzzyOnDroid: resolve every published version from the
            // F-Droid packages API into a full release list. ─────────────────────
            if (repo.source == AppSource.FDROID || repo.source == AppSource.IZZY) {
                updateInstall(repo.id) { copy(isLoadingRelease = true, error = null) }
                val (apiBase, repoBase) = if (repo.source == AppSource.IZZY)
                    "https://apt.izzysoft.de/fdroid" to "https://apt.izzysoft.de/fdroid"
                else "https://f-droid.org" to "https://f-droid.org"
                // packageId first: for Izzy, full_name is "owner/repo", which the
                // packages API can only 404 on — that's why Izzy apps showed
                // "No releases found". F-Droid's full_name IS the package, so the
                // fallback keeps working for older cached entries.
                val pkg  = repo.packageId.ifBlank { repo.full_name.ifBlank { repo.name } }
                val rels = fetchFdroidStyleReleases(pkg, apiBase, repoBase)
                    .filterByPreReleasePref(state.settings.showPreReleases)
                if (rels.isNotEmpty()) {
                    val def   = rels.firstOrNull { !it.prerelease } ?: rels.first()
                    val smart = detectBestApk(def.assets)
                    updateInstall(repo.id) {
                        copy(isLoadingRelease = false, releases = rels, release = def,
                             apkAsset = smart?.asset ?: def.assets.firstOrNull(),
                             smartInstall = smart, error = null)
                    }
                } else if (repo.apkUrl.isNotBlank()) {
                    val synthetic = Release(
                        tag_name = repo.cdnVersion.ifBlank { "Latest" }, name = repo.name,
                        assets = listOf(ReleaseAsset("${repo.name}.apk", repo.apkUrl, repo.apkSize, "application/vnd.android.package-archive")),
                        published_at = repo.updated_at, body = ""
                    )
                    updateInstall(repo.id) {
                        copy(isLoadingRelease = false, release = synthetic, apkAsset = synthetic.assets.first(),
                             smartInstall = detectBestApk(synthetic.assets), error = null)
                    }
                } else {
                    updateInstall(repo.id) { copy(isLoadingRelease = false, error = "No releases found.") }
                }
                return@launch
            }

            // CDN-backed sources: use CDN apkUrl when available; otherwise query the source API
            val isCdnSource = repo.source != null &&
                    repo.source != AppSource.GITHUB &&
                    repo.source != AppSource.IZZY
            if (isCdnSource) {
                if (repo.apkUrl.isNotBlank()) {
                    // Fast path: CDN already has the download URL
                    val synthetic = Release(
                        tag_name     = repo.cdnVersion.ifBlank { "Latest" },
                        name         = repo.name,
                        assets       = listOf(ReleaseAsset(
                            name                 = "${repo.name}.apk",
                            browser_download_url = repo.apkUrl,
                            size                 = repo.apkSize,
                            content_type         = "application/vnd.android.package-archive"
                        )),
                        published_at = repo.updated_at,
                        body         = ""
                    )
                    val smart = detectBestApk(synthetic.assets)
                    updateInstall(repo.id) {
                        copy(
                            isLoadingRelease = false,
                            release          = synthetic,
                            apkAsset         = synthetic.assets.first(),
                            smartInstall     = smart,
                            error            = null
                        )
                    }
                    return@launch
                }

                // CDN has no APK URL — query the source's release API
                updateInstall(repo.id) { copy(isLoadingRelease = true, error = null) }
                try {
                    var foundRelease : Release?      = null
                    var foundApk     : ReleaseAsset? = null
                    var allReleases  : List<Release> = emptyList()

                    when (repo.source) {
                        AppSource.GITLAB -> {
                            try {
                                val encoded  = java.net.URLEncoder.encode(repo.full_name, "UTF-8")
                                val releases = GitLabClient.service.getReleases(encoded, 30)
                                allReleases  = releases.map { it.toRelease() }
                                    .filterByPreReleasePref(state.settings.showPreReleases)
                                for (rel in allReleases) {
                                    if (foundRelease == null) foundRelease = rel
                                    val apk = detectBestApk(rel.assets)?.asset
                                    if (apk != null) { foundRelease = rel; foundApk = apk; break }
                                }
                            } catch (_: Exception) {}
                        }
                        AppSource.CODEBERG -> {
                            try {
                                val parts = repo.full_name.split("/", limit = 2)
                                if (parts.size == 2) {
                                    val releases = CodebergClient.service.getReleases(parts[0], parts[1], 30)
                                    allReleases  = releases.filterByPreReleasePref(state.settings.showPreReleases)
                                    for (rel in allReleases) {
                                        if (foundRelease == null) foundRelease = rel
                                        val apk = detectBestApk(rel.assets)?.asset
                                        if (apk != null) { foundRelease = rel; foundApk = apk; break }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        else -> {} // F-Droid, Flathub, Winget: no public APK release API
                    }

                    if (foundRelease != null) {
                        val smart = detectBestApk(foundRelease.assets)
                        val trust = calculateTrustScore(repo, allReleases.size.coerceAtLeast(1))
                        updateInstall(repo.id) {
                            copy(
                                isLoadingRelease = false,
                                releases         = allReleases,
                                release          = foundRelease,
                                apkAsset         = smart?.asset ?: foundApk ?: foundRelease.assets.firstOrNull(),
                                smartInstall     = smart,
                                trustScore       = trust,
                                error            = null
                            )
                        }
                    } else {
                        // No API releases found — show placeholder release with no assets
                        val placeholder = Release(
                            tag_name     = repo.cdnVersion.ifBlank { "Latest" },
                            name         = repo.name,
                            assets       = emptyList(),
                            published_at = repo.updated_at,
                            body         = ""
                        )
                        updateInstall(repo.id) {
                            copy(isLoadingRelease = false, release = placeholder, error = null)
                        }
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException)
                        updateInstall(repo.id) { copy(isLoadingRelease = false, error = "Could not load release info.") }
                }
                return@launch
            }

            // GitHub / IzzyOnDroid: try CDN-cached releases first, fall back to direct API
            updateInstall(repo.id) { copy(isLoadingRelease = true, error = null) }
            try {
                var foundRelease : Release?      = null
                var foundApk     : ReleaseAsset? = null
                var allReleases  : List<Release> = emptyList()

                // Address the repo by its full_name ("owner/repo" exactly as the host
                // spells it) rather than owner.login + name. `name` is the catalog's
                // display label — often title-cased or renamed — and owner.login can
                // be a placeholder when the entry carried no usable URL. Pairing the
                // two produced requests like getReleases("github", "Some App"), which
                // 404 every time and surfaced as "No release found" on apps that
                // clearly had releases.
                val slug     = repo.full_name.split('/').filter { it.isNotBlank() }
                val ghOwner  = if (slug.size >= 2) slug[0] else repo.owner.login
                val ghRepo   = if (slug.size >= 2) slug[1].removeSuffix(".git") else repo.name

                // 1. Try CDN-pre-cached releases (no token needed, populated by fetch_releases.py)
                try {
                    val cdnUrl = "https://NikhilKain.github.io/appstore-metadata/data/releases/github" +
                            "/$ghOwner/$ghRepo.json"
                    val cdnReleases: List<Release>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val r = okhttp3.OkHttpClient().newCall(
                            okhttp3.Request.Builder().url(cdnUrl).build()
                        ).execute()
                        try {
                            if (!r.isSuccessful) null
                            else {
                                val body = r.body?.string()
                                if (body.isNullOrBlank()) null
                                else {
                                    val type = object : TypeToken<List<Release>>() {}.type
                                    val parsed: List<Release> = Gson().fromJson(body, type)
                                    parsed.takeIf { it.isNotEmpty() }
                                }
                            }
                        } finally { r.close() }
                    }
                    if (cdnReleases != null) {
                        // List releases honouring the pre-release toggle (never empty).
                        allReleases  = cdnReleases.filterByPreReleasePref(state.settings.showPreReleases)
                        // Default selection: prefer a stable release that has an installable
                        // APK; otherwise any release with an APK; otherwise the newest.
                        val ordered  = allReleases.sortedBy { it.prerelease }  // stable first (stable sort keeps recency)
                        foundRelease = ordered.firstOrNull()
                        for (rel in ordered) {
                            val apk = detectBestApk(rel.assets)?.asset
                            if (apk != null) { foundRelease = rel; foundApk = apk; break }
                        }
                    }
                } catch (_: Exception) {}

                // 2. Fall back to direct GitHub API if CDN didn't have this repo pre-cached
                //
                // Why the failures are kept rather than swallowed: the CDN release cache
                // only covers part of the catalogue (rustdesk and scrcpy, for two, are
                // not in it), so those repos always come down this path — where an
                // anonymous caller has 60 core requests an *hour*. Browsing the home
                // rows and opening a few apps can exhaust that, and every call after it
                // 403s. Both catches used to discard the reason, so a spent quota and a
                // repo with genuinely no releases produced the identical, wrong message
                // "No releases found." — which is why an app would show no APK from the
                // home page and then install fine from search a minute later.
                var lastFailure: Exception? = null
                if (foundRelease == null) {
                    // Primary: full release list (stable + pre-release), deep fetch.
                    try {
                        val fetched = RetrofitClient.service.getReleases(ghOwner, ghRepo, 30)
                        allReleases  = fetched.filterByPreReleasePref(state.settings.showPreReleases)
                        val ordered  = allReleases.sortedBy { it.prerelease }  // prefer stable for default pick
                        foundRelease = ordered.firstOrNull()
                        for (r in ordered) {
                            val apk = detectBestApk(r.assets)?.asset
                            if (apk != null) { foundRelease = r; foundApk = apk; break }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        lastFailure = e
                    }

                    // Redundant fallback: /releases/latest. Restores prior behaviour —
                    // covers cases where the list call failed (rate limit / transient)
                    // or didn't surface the APK that the "latest" endpoint exposes.
                    if (foundApk == null) {
                        try {
                            val latest = RetrofitClient.service.getLatestRelease(ghOwner, ghRepo)
                            val apk    = detectBestApk(latest.assets)?.asset
                            if (apk != null || foundRelease == null) {
                                foundRelease = latest
                                foundApk     = apk
                                if (allReleases.none { it.tag_name == latest.tag_name }) {
                                    allReleases = listOf(latest) + allReleases
                                }
                            }
                            lastFailure = null
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            // A 404 here is the honest answer for a repo that has never
                            // cut a release, so it must not masquerade as an outage.
                            if (lastFailure == null && githubHttpCode(e) != 404) lastFailure = e
                        }
                    }
                }

                if (foundRelease != null) {
                    val smart = detectBestApk(foundRelease.assets)
                    val trust = calculateTrustScore(repo, allReleases.size.coerceAtLeast(1))
                    updateInstall(repo.id) {
                        copy(
                            isLoadingRelease = false,
                            releases         = allReleases,
                            release          = foundRelease,
                            apkAsset         = smart?.asset ?: foundApk ?: foundRelease.assets.firstOrNull(),
                            smartInstall     = smart,
                            trustScore       = trust
                        )
                    }
                } else {
                    updateInstall(repo.id) {
                        copy(
                            isLoadingRelease = false,
                            releases         = allReleases,
                            error            = releaseLookupError(lastFailure),
                        )
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException)
                    updateInstall(repo.id) { copy(isLoadingRelease = false, error = "Could not load release info.") }
            }
        }
    }

    /**
     * The package this listing is expected to install, when we can know it:
     * what a previous install of this repo actually put on the device, or — for
     * F-Droid/IzzyOnDroid, where the "repo" *is* a package — the package id.
     * Null for a first GitHub install, where nothing is claimed and so nothing
     * can be contradicted.
     */
    private fun expectedPackageFor(repo: GitHubRepo): String? =
        installStates[repo.id]?.packageName
            ?: state.installHistory.lastOrNull { it.repoId == repo.id && it.packageName.isNotEmpty() }?.packageName
            ?: repo.packageId.takeIf { it.isNotBlank() }
            ?: repo.full_name.takeIf { repo.source == AppSource.FDROID }

    private fun launchSystemInstaller(file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun downloadAndInstall(repo: GitHubRepo, asset: ReleaseAsset) {
        // An APK fetched over plain HTTP can be swapped in flight by anything on
        // the path; there is no recovering from that after the fact.
        if (!asset.browser_download_url.startsWith("https://", ignoreCase = true)) {
            updateInstall(repo.id) {
                copy(
                    downloadProgress = null,
                    repo             = repo,
                    error            = "Refused: this download link is not HTTPS."
                )
            }
            return
        }
        val job = viewModelScope.launch {
            updateInstall(repo.id) { copy(downloadProgress = 0f, error = null, verification = null, repo = repo) }
            val outFile = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "${repo.name}_${asset.name}")
            try {
                // Vyxel's own downloader rather than the system one.
                //
                // `android.app.DownloadManager` is always a single connection and
                // offers no way to change that, and the mirrors here shape per
                // connection — so a large APK arrived at a fraction of the link's
                // real speed. This is the same segmented transfer the Expressive
                // shell uses, so both shells now download at the same rate.
                //
                // Everything after the bytes land is unchanged: the signature check
                // below still gates the installer, which is Classic's most important
                // property and one the old path shared.
                com.vythera.vyxelapps.expressive.install.FastDownloader.download(
                    url = asset.browser_download_url,
                    target = outFile,
                    knownSize = asset.size,
                ) { done, total ->
                    if (total > 0) {
                        updateInstall(repo.id) { copy(downloadProgress = done.toFloat() / total) }
                    }
                }

                updateInstall(repo.id) { copy(downloadProgress = null, isVerifying = true) }
                val check = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ApkVerifier.verify(ctx, outFile, expectedPackageFor(repo))
                }
                val pkg = check.packageName.takeIf { it.isNotEmpty() }

                // A failed check means the bytes are not what this listing promised.
                // Nothing gets handed to any installer, and the file is removed so a
                // later "rollback" can't resurrect it.
                if (!check.isSafeToInstall) {
                    try { outFile.delete() } catch (_: Exception) {}
                    updateInstall(repo.id) {
                        copy(
                            downloadProgress = null,
                            downloadId       = null,
                            isVerifying      = false,
                            verification     = check,
                            error            = check.message()
                        )
                    }
                    return@launch
                }

                updateInstall(repo.id) {
                    copy(
                        downloadProgress = null,
                        downloadId       = null,
                        isVerifying      = false,
                        verification     = check,
                        packageName      = pkg ?: packageName
                    )
                }

                if (ShizukuInstaller.isAvailable() && ShizukuInstaller.hasPermission()) {
                    ShizukuInstaller.install(ctx, outFile,
                        onSuccess = {
                            if (pkg != null) updateInstall(repo.id) { copy(isInstalled = true) }
                        },
                        onFailure = {
                            // Fall back to standard installer on any Shizuku error
                            try { launchSystemInstaller(outFile) } catch (_: Exception) {}
                        }
                    )
                } else {
                    launchSystemInstaller(outFile)
                }
                val tag = installStates[repo.id]?.release?.tag_name ?: "unknown"
                recordInstall(repo, tag, outFile.absolutePath, pkg ?: "")
                if (pkg != null) {
                    viewModelScope.launch {
                        repeat(30) {
                            delay(2000)
                            if (installed(pkg)) { updateInstall(repo.id) { copy(isInstalled = true) }; return@launch }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                updateInstall(repo.id) { copy(downloadProgress = null, error = "Error: ${e.message}") }
            }
        }
        downloadJobs[repo.id] = job
    }

    fun downloadOnly(repo: GitHubRepo, asset: ReleaseAsset) {
        if (!asset.browser_download_url.startsWith("https://", ignoreCase = true)) {
            updateInstall(repo.id) {
                copy(downloadProgress = null, repo = repo, error = "Refused: this download link is not HTTPS.")
            }
            return
        }
        val job = viewModelScope.launch {
            updateInstall(repo.id) { copy(downloadProgress = 0f, error = null, repo = repo) }
            try {
                val dm  = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val req = DownloadManager.Request(Uri.parse(asset.browser_download_url))
                    .setTitle("Downloading ${repo.name}").setDescription("Saved to Downloads")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, asset.name)
                    .setAllowedOverMetered(true).setAllowedOverRoaming(true)
                val dlId = dm.enqueue(req)
                updateInstall(repo.id) { copy(downloadId = dlId) }
                while (true) {
                    val cur    = dm.query(DownloadManager.Query().setFilterById(dlId))
                    if (!cur.moveToFirst()) { cur.close(); break }
                    val status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done   = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total  = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    cur.close()
                    if (total > 0) updateInstall(repo.id) { copy(downloadProgress = done.toFloat() / total) }
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> { updateInstall(repo.id) { copy(downloadProgress = null, downloadId = null) }; break }
                        DownloadManager.STATUS_FAILED     -> { updateInstall(repo.id) { copy(downloadProgress = null, downloadId = null, error = "Download failed.") }; break }
                        else -> delay(400)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                updateInstall(repo.id) { copy(downloadProgress = null, error = "Error: ${e.message}") }
            }
        }
        downloadJobs[repo.id] = job
    }

    fun cancelDownload(repo: GitHubRepo) {
        val dlId = installStates[repo.id]?.downloadId
        updateInstall(repo.id) { copy(downloadProgress = null, downloadId = null, error = null) }
        downloadJobs[repo.id]?.cancel()
        downloadJobs.remove(repo.id)
        if (dlId != null) {
            try { (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(dlId) }
            catch (_: Exception) {}
        }
    }

    fun uninstall(repo: GitHubRepo) {
        val installState = installStates[repo.id] ?: return
        val pkg          = installState.packageName
        if (pkg.isNullOrEmpty()) {
            val apkFile     = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?.listFiles()?.firstOrNull { it.name.contains(repo.name, ignoreCase = true) }
            val detectedPkg = apkFile?.let {
                try { ctx.packageManager.getPackageArchiveInfo(it.absolutePath, 0)?.packageName }
                catch (_: Exception) { null }
            }
            if (detectedPkg != null) {
                updateInstall(repo.id) { copy(packageName = detectedPkg) }
                launchUninstall(repo.id, detectedPkg)
            }
            return
        }
        launchUninstall(repo.id, pkg)
    }

    private fun launchUninstall(repoId: Long, pkg: String) {
        try {
            ctx.startActivity(
                android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                    data = android.net.Uri.parse("package:$pkg")
                    putExtra(android.content.Intent.EXTRA_RETURN_RESULT, false)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            viewModelScope.launch {
                repeat(40) {
                    delay(2000)
                    try { ctx.packageManager.getPackageInfo(pkg, 0) }
                    catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                        updateInstall(repoId) { copy(isInstalled = false) }
                        return@launch
                    }
                }
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                updateInstall(repoId) { copy(error = "Could not launch uninstaller: ${e.message}") }
            }
        }
    }

    fun refreshInstall(id: Long) {
        val pkg = installStates[id]?.packageName ?: return
        updateInstall(id) { copy(isInstalled = installed(pkg)) }
    }

    fun translateDescription(repo: GitHubRepo) {
        val desc   = repo.description ?: return
        val readme = state.readmes[repo.id]
        val lang = translationCodeFor(state.settings.language)
        viewModelScope.launch {
            state = state.copy(isTranslating = state.isTranslating + (repo.id to true))
            try {
                val translated = translateText(desc, lang)
                state = state.copy(
                    translatedDescriptions = state.translatedDescriptions + (repo.id to translated),
                    isTranslating          = state.isTranslating + (repo.id to false)
                )
                // Also translate the readme if available
                if (!readme.isNullOrBlank()) {
                    val chunks = readme.chunked(1500)
                    val translatedChunks = chunks.map { chunk ->
                        runCatching { translateText(chunk, lang) }.getOrDefault(chunk)
                    }
                    state = state.copy(
                        translatedReadmes = state.translatedReadmes + (repo.id to translatedChunks.joinToString(" "))
                    )
                }
            } catch (_: Exception) {
                state = state.copy(isTranslating = state.isTranslating + (repo.id to false))
            }
        }
    }

    fun translateReleaseBody(repo: GitHubRepo) {
        val body = installStates[repo.id]?.release?.body?.takeIf { it.isNotBlank() } ?: return
        val lang = translationCodeFor(state.settings.language)
        viewModelScope.launch {
            state = state.copy(isTranslatingRelease = state.isTranslatingRelease + (repo.id to true))
            try {
                val translated = translateText(body.take(500), lang)
                state = state.copy(
                    translatedReleaseBodies = state.translatedReleaseBodies + (repo.id to translated),
                    isTranslatingRelease    = state.isTranslatingRelease + (repo.id to false)
                )
            } catch (_: Exception) {
                state = state.copy(isTranslatingRelease = state.isTranslatingRelease + (repo.id to false))
            }
        }
    }

    fun clearNotifications() {
        prefs.saveNotifsDismissed(true)
        state = state.copy(notifsDismissed = true)
    }

    fun selectRelease(repoId: Long, release: Release) {
        val bestApk = detectBestApk(release.assets)?.asset
            ?: release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: release.assets.firstOrNull()
        updateInstall(repoId) { copy(release = release, apkAsset = bestApk) }
    }

    fun selectAsset(repoId: Long, asset: ReleaseAsset) {
        updateInstall(repoId) { copy(apkAsset = asset) }
    }

    private fun installed(pkg: String) = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun updateInstall(id: Long, block: InstallState.() -> InstallState) {
        val cur = installStates[id] ?: InstallState()
        installStates[id] = cur.block()
    }

    /**
     * Given an upstream repo URL (GitHub/GitLab/Codeberg), fetch its README and
     * extract screenshots. Used to rescue F-Droid/Izzy entries whose store page
     * exposes no reachable screenshots. Returns empty on any miss — callers fall
     * back to their own source-specific probe.
     */
    private suspend fun fetchScreenshotsFromSourceRepo(sourceUrl: String): List<String> {
        if (sourceUrl.isBlank()) return emptyList()
        val uri  = try { Uri.parse(sourceUrl) } catch (_: Exception) { return emptyList() }
        val host = uri.host ?: return emptyList()
        val segs = uri.pathSegments?.filter { it.isNotBlank() } ?: return emptyList()
        if (segs.size < 2) return emptyList()
        val owner = segs[0]
        val name  = segs[1].removeSuffix(".git")

        val (readmeUrl, rawBase) = when {
            host.contains("github.com")   ->
                "https://raw.githubusercontent.com/$owner/$name/HEAD/README.md" to
                "https://raw.githubusercontent.com/$owner/$name/HEAD"
            host.contains("gitlab.com")   ->
                "https://gitlab.com/$owner/$name/-/raw/HEAD/README.md" to
                "https://gitlab.com/$owner/$name/-/raw/HEAD"
            host.contains("codeberg.org") ->
                "https://codeberg.org/$owner/$name/raw/branch/HEAD/README.md" to
                "https://codeberg.org/$owner/$name/raw/branch/HEAD"
            else -> return emptyList()
        }
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val client = okhttp3.OkHttpClient.Builder()
                    .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                val md = client.newCall(okhttp3.Request.Builder().url(readmeUrl).build())
                    .execute().use { if (it.isSuccessful) it.body?.string() ?: "" else "" }
                extractScreenshots(md, rawBase)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun fetchScreenshots(repo: GitHubRepo) {
        if (state.screenshots.containsKey(repo.id)) return

        // Aptoide: a store listing, so there is no README to scrape — but there is
        // a getMeta call that returns the publisher's own description and
        // screenshots. Search results carry only the signer line, which is why a
        // Play app's About section read as one sentence next to a GitHub app's
        // full README. Expressive already calls this resolver; Classic did not.
        if (repo.source == AppSource.APTOIDE) {
            viewModelScope.launch {
                try {
                    val resolved = com.vythera.vyxelapps.expressive.data.source
                        .AptoideSource()
                        .resolve(repo.toAppItem())
                    val shots = resolved.screenshots.take(6)
                    val about = resolved.description.trim().take(3000)
                    state = state.copy(
                        screenshots = state.screenshots + (repo.id to shots),
                        readmes = if (about.isNotBlank()) state.readmes + (repo.id to about)
                        else state.readmes,
                    )
                } catch (_: Exception) {
                    state = state.copy(screenshots = state.screenshots + (repo.id to emptyList()))
                }
            }
            return
        }

        // F-Droid: the store page's phoneScreenshots dir uses content-hash file
        // names we can't guess, so screenshots almost never resolved. The CDN
        // entry carries source_code — parse the real project README first, which
        // is where the screenshots actually live.
        if (repo.source == AppSource.FDROID) {
            viewModelScope.launch {
                val fromSource = fetchScreenshotsFromSourceRepo(repo.sourceCodeUrl)
                if (fromSource.isNotEmpty()) {
                    state = state.copy(screenshots = state.screenshots + (repo.id to fromSource))
                    return@launch
                }
                // Fallback: the fastlane-style numbered names some repos do use.
                try {
                    val client  = okhttp3.OkHttpClient.Builder().callTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()
                    val baseUrl = "https://f-droid.org/repo/${repo.full_name}/en-US/phoneScreenshots/"
                    val candidates = (1..8).flatMap { listOf("$it.png", "$it.jpg") }
                    val found = candidates.map { name ->
                        async(kotlinx.coroutines.Dispatchers.IO) {
                            val url = "$baseUrl$name"
                            try {
                                val resp = client.newCall(okhttp3.Request.Builder().url(url).head().build()).execute()
                                val ok   = resp.isSuccessful
                                resp.close()
                                if (ok) url else null
                            } catch (_: Exception) { null }
                        }
                    }.mapNotNull { it.await() }.take(6)
                    state = state.copy(screenshots = state.screenshots + (repo.id to found))
                } catch (_: Exception) {
                    state = state.copy(screenshots = state.screenshots + (repo.id to emptyList()))
                }
            }
            return
        }

        // Flathub: fetch screenshots from Flathub AppStream API
        if (repo.source == AppSource.FLATHUB) {
            val appId = repo.full_name
            viewModelScope.launch {
                try {
                    val rawJson = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val client = okhttp3.OkHttpClient.Builder()
                            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                        client.newCall(
                            okhttp3.Request.Builder()
                                .url("https://flathub.org/api/v2/appstream/$appId")
                                .build()
                        ).execute().use { it.body?.string() ?: "" }
                    }
                    val json   = com.google.gson.JsonParser.parseString(rawJson).asJsonObject
                    val shots  = mutableListOf<String>()
                    json.getAsJsonArray("screenshots")?.forEach { elem ->
                        val obj = elem.asJsonObject
                        // prefer "default" size if present, else first available
                        val url = obj.getAsJsonArray("sizes")
                            ?.firstOrNull()?.asJsonObject?.get("url")?.asString
                            ?: obj.get("url")?.asString
                        if (!url.isNullOrBlank()) shots.add(url)
                    }
                    state = state.copy(screenshots = state.screenshots + (repo.id to shots.take(6)))
                } catch (_: Exception) {
                    state = state.copy(screenshots = state.screenshots + (repo.id to emptyList()))
                }
            }
            return
        }

        // GitLab: fetch raw README and parse markdown images
        if (repo.source == AppSource.GITLAB) {
            val fullName = repo.full_name
            viewModelScope.launch {
                try {
                    val md = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val client = okhttp3.OkHttpClient.Builder()
                            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                        // Try main branch, then master
                        listOf("main", "master", "HEAD").firstNotNullOfOrNull { branch ->
                            try {
                                val resp = client.newCall(
                                    okhttp3.Request.Builder()
                                        .url("https://gitlab.com/$fullName/-/raw/$branch/README.md")
                                        .build()
                                ).execute()
                                if (resp.isSuccessful) resp.body?.string() else null
                            } catch (_: Exception) { null }
                        } ?: ""
                    }
                    if (md.isBlank()) {
                        state = state.copy(screenshots = state.screenshots + (repo.id to emptyList()))
                        return@launch
                    }
                    val urls = extractScreenshots(md, "https://gitlab.com/$fullName/-/raw/HEAD")
                    val stripped = stripMarkdown(md).take(3000)
                    state = state.copy(
                        screenshots = state.screenshots + (repo.id to urls),
                        readmes     = if (stripped.isNotBlank()) state.readmes + (repo.id to stripped) else state.readmes
                    )
                } catch (_: Exception) {
                    state = state.copy(screenshots = state.screenshots + (repo.id to emptyList()))
                }
            }
            return
        }

        // Codeberg: fetch raw README and parse markdown images
        if (repo.source == AppSource.CODEBERG) {
            val fullName = repo.full_name
            viewModelScope.launch {
                try {
                    val md = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val client = okhttp3.OkHttpClient.Builder()
                            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                        listOf("main", "master").firstNotNullOfOrNull { branch ->
                            try {
                                val resp = client.newCall(
                                    okhttp3.Request.Builder()
                                        .url("https://codeberg.org/$fullName/raw/branch/$branch/README.md")
                                        .build()
                                ).execute()
                                if (resp.isSuccessful) resp.body?.string() else null
                            } catch (_: Exception) { null }
                        } ?: ""
                    }
                    if (md.isBlank()) {
                        state = state.copy(screenshots = state.screenshots + (repo.id to emptyList()))
                        return@launch
                    }
                    val urls = extractScreenshots(md, "https://codeberg.org/$fullName/raw/branch/HEAD")
                    val stripped = stripMarkdown(md).take(3000)
                    state = state.copy(
                        screenshots = state.screenshots + (repo.id to urls),
                        readmes     = if (stripped.isNotBlank()) state.readmes + (repo.id to stripped) else state.readmes
                    )
                } catch (_: Exception) {
                    state = state.copy(screenshots = state.screenshots + (repo.id to emptyList()))
                }
            }
            return
        }

        // Resolve GitHub owner/name — handle IzzyOnDroid apps whose html_url is a GitHub link
        val (ghOwner, ghName) = when (repo.source) {
            AppSource.GITHUB, null -> repo.owner.login to repo.name
            AppSource.IZZY -> {
                val uri  = try { Uri.parse(repo.html_url) } catch (_: Exception) { null }
                val segs = uri?.pathSegments?.filter { it.isNotBlank() } ?: emptyList()
                if (segs.size >= 2) segs[0] to segs[1] else return
            }
            else -> return  // Winget has no useful screenshot source
        }
        if (ghOwner.isBlank() || ghName.isBlank()) return

        viewModelScope.launch {
            try {
                // raw.githubusercontent.com first: it needs no auth and isn't on the
                // 60-req/hr unauthenticated REST budget, so screenshots and the
                // README load for token-less users too — via the API they simply
                // 403'd and the detail page looked empty. The API stays as a
                // fallback for the rare repo whose README isn't named README.md.
                val md = fetchRawReadme(ghOwner, ghName) ?: run {
                    val readme = RetrofitClient.service.getReadme(ghOwner, ghName)
                    if (readme.encoding == "base64")
                        String(android.util.Base64.decode(readme.content.replace("\n", ""), android.util.Base64.DEFAULT))
                    else readme.content
                }

                // Screenshots — HEAD resolves the repo's real default branch, so
                // master-only repos work too (the old hard-coded /main/ 404'd).
                // Built from the resolved owner/name (Izzy's full_name isn't it).
                val urls = extractScreenshots(md, "https://raw.githubusercontent.com/$ghOwner/$ghName/HEAD")

                // Stripped description text
                val stripped = stripMarkdown(md).take(3000)

                state = state.copy(
                    screenshots = state.screenshots + (repo.id to urls),
                    readmes     = if (stripped.isNotBlank())
                                      state.readmes + (repo.id to stripped)
                                  else state.readmes
                )
            } catch (_: Exception) {
                state = state.copy(screenshots = state.screenshots + (repo.id to emptyList()))
            }
        }
    }

    /**
     * README straight off raw.githubusercontent.com — no token, no REST quota.
     * Tries the handful of names GitHub actually renders. Null when none exist,
     * so the caller can fall back to the API.
     */
    private suspend fun fetchRawReadme(owner: String, name: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
            listOf("README.md", "readme.md", "README.MD", "README.markdown", "README.rst", "README")
                .firstNotNullOfOrNull { file ->
                    try {
                        client.newCall(
                            okhttp3.Request.Builder()
                                .url("https://raw.githubusercontent.com/$owner/$name/HEAD/$file")
                                .build()
                        ).execute().use { resp ->
                            if (resp.isSuccessful) resp.body?.string()?.takeIf { it.isNotBlank() } else null
                        }
                    } catch (_: Exception) { null }
                }
        }

    fun checkForUpdatesNow() {
        if (state.isCheckingUpdates) return
        viewModelScope.launch {
            state = state.copy(isCheckingUpdates = true)
            val history = state.installHistory
                .groupBy { it.repoId }
                .mapValues { (_, v) -> v.maxByOrNull { it.installedAt }!! }
                .values.toList()
            val updates = mutableListOf<UpdateInfo>()
            val httpClient = okhttp3.OkHttpClient.Builder()
                .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()

            for (entry in history) {
                val src = entry.source.orEmpty().ifEmpty {
                    // infer source from ownerLogin for pre-source-tracking installs
                    when (entry.ownerLogin) {
                        "fdroid"  -> "fdroid"
                        "flathub" -> "flathub"
                        "winget"  -> "winget"
                        else      -> "github"
                    }
                }
                try {
                    val (latestTag, changelog) = when (src) {
                        "fdroid" -> {
                            // packageName = Android package ID = F-Droid package ID
                            val pkg = entry.packageName.ifEmpty { entry.repoName }
                            if (pkg.isEmpty()) continue
                            val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                httpClient.newCall(
                                    okhttp3.Request.Builder()
                                        .url("https://f-droid.org/api/v1/packages/$pkg")
                                        .build()
                                ).execute().use { it.body?.string() ?: "" }
                            }
                            val packages = com.google.gson.JsonParser.parseString(json)
                                .asJsonObject.getAsJsonArray("packages")
                            val latest = packages?.firstOrNull()?.asJsonObject
                            val ver = latest?.get("versionName")?.asString ?: ""
                            ver to (latest?.get("whatsNew")?.asString ?: "")
                        }
                        "flathub" -> {
                            val appId = entry.packageName.ifEmpty { entry.repoName }
                            if (appId.isEmpty()) continue
                            val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                httpClient.newCall(
                                    okhttp3.Request.Builder()
                                        .url("https://flathub.org/api/v2/appstream/$appId")
                                        .build()
                                ).execute().use { it.body?.string() ?: "" }
                            }
                            val releases = com.google.gson.JsonParser.parseString(json)
                                .asJsonObject.getAsJsonArray("releases")
                            val latest = releases?.firstOrNull()?.asJsonObject
                            val ver = latest?.get("version")?.asString ?: ""
                            ver to ""
                        }
                        "gitlab" -> {
                            val fullName = "${entry.ownerLogin}/${entry.repoName}"
                            val encoded  = java.net.URLEncoder.encode(fullName, "UTF-8")
                            val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                httpClient.newCall(
                                    okhttp3.Request.Builder()
                                        .url("https://gitlab.com/api/v4/projects/$encoded/releases?per_page=1")
                                        .build()
                                ).execute().use { it.body?.string() ?: "" }
                            }
                            val arr = com.google.gson.JsonParser.parseString(json).asJsonArray
                            val latest = arr?.firstOrNull()?.asJsonObject
                            val tag = latest?.get("tag_name")?.asString ?: ""
                            val desc = latest?.get("description")?.asString ?: ""
                            tag to desc
                        }
                        "codeberg" -> {
                            val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                httpClient.newCall(
                                    okhttp3.Request.Builder()
                                        .url("https://codeberg.org/api/v1/repos/${entry.ownerLogin}/${entry.repoName}/releases?limit=1")
                                        .build()
                                ).execute().use { it.body?.string() ?: "" }
                            }
                            val arr = com.google.gson.JsonParser.parseString(json).asJsonArray
                            val latest = arr?.firstOrNull()?.asJsonObject
                            val tag = latest?.get("tag_name")?.asString ?: ""
                            val body = latest?.get("body")?.asString ?: ""
                            tag to body
                        }
                        "winget" -> continue  // no reliable version API
                        else -> {  // github / izzy — both use GitHub releases API
                            val r = RetrofitClient.service.getLatestRelease(entry.ownerLogin, entry.repoName)
                            r.tag_name to (r.body ?: "")
                        }
                    }
                    // Same trap as UpdateCheckWorker: comparing tag strings reports an
                    // update for any difference at all, including a "v" prefix or a
                    // downgrade, and it never clears because the stored tag keeps not
                    // matching. Order the versions instead.
                    if (latestTag.isNotBlank() && isVersionNewerThan(latestTag, entry.tagName)) {
                        val key = "${entry.repoId}:$latestTag"
                        if (key !in state.ignoredVersions) {
                            updates.add(UpdateInfo(entry.repoId, entry.repoName, entry.tagName, latestTag, changelog))
                        }
                    }
                } catch (_: Exception) {}
            }

            val newUpdates = updates.distinctBy { it.repoId }
            val hasNewUpdates = newUpdates.any { n ->
                state.updates.none { o -> o.repoId == n.repoId && o.latestTag == n.latestTag }
            }
            val newDismissed = if (hasNewUpdates) false else state.notifsDismissed
            state = state.copy(
                updates           = newUpdates,
                isCheckingUpdates = false,
                notifsDismissed   = newDismissed
            )
            prefs.saveUpdates(newUpdates)
            if (hasNewUpdates) prefs.saveNotifsDismissed(false)
            TodayWidgetProvider.refreshAll(ctx)
        }
    }

    fun updateAll() {
        if (state.updates.isEmpty()) return
        viewModelScope.launch {
            val history = state.installHistory
                .groupBy { it.repoId }
                .mapValues { (_, v) -> v.maxByOrNull { it.installedAt }!! }
            for (update in state.updates) {
                val entry = history[update.repoId] ?: continue
                try {
                    val release  = RetrofitClient.service.getLatestRelease(entry.ownerLogin, entry.repoName)
                    val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                                   ?: continue
                    val repo = installStates[entry.repoId]?.repo ?: GitHubRepo(
                        id        = entry.repoId,
                        name      = entry.repoName,
                        full_name = "${entry.ownerLogin}/${entry.repoName}",
                        owner     = RepoOwner(login = entry.ownerLogin)
                    )
                    updateInstall(repo.id) { copy(release = release, apkAsset = apkAsset, repo = repo) }
                    downloadAndInstall(repo, apkAsset)
                } catch (_: Exception) {}
            }
        }
    }

    fun clearRemovedApps() {
        val history = state.installHistory
            .groupBy { it.repoId }
            .mapValues { (_, v) -> v.maxByOrNull { it.installedAt }!! }
        val removedIds = history.values
            .filter { entry ->
                // Ask the package manager first, and only fall back to the in-memory
                // install state.
                //
                // `installStates` is populated by whichever install flow ran *this
                // session*, so an entry the user installed last week — or installed
                // from the Expressive shell, which keeps its own state — had no entry
                // here at all and could never be pruned. The device's own package list
                // is the authority on whether an app is still there, and it doesn't
                // care which shell put it there.
                val pkg = entry.packageName.takeIf { it.isNotBlank() }
                if (pkg != null) return@filter !installed(pkg)
                val iState = installStates[entry.repoId]
                iState != null && !iState.isInstalled
            }
            .map { it.repoId }
            .toSet()
        if (removedIds.isEmpty()) return
        val pruned = state.installHistory.filter { it.repoId !in removedIds }
        removedIds.forEach { installStates.remove(it) }
        state = state.copy(
            installHistory = pruned,
            updates        = state.updates.filter { it.repoId !in removedIds }
        )
        prefs.saveInstallHistory(pruned)
        prefs.saveUpdates(state.updates)
    }

    // ── Root modules ─────────────────────────────────────────────────────────

    /**
     * Loads the module catalogue for Classic's Modules screen.
     *
     * Prefers the CDN's pre-built `modules.json` — one conditional GET — and only
     * scrapes the repositories live when that has not been published yet. Kept once
     * loaded, so reopening the screen is instant.
     */
    fun loadModules(force: Boolean = false) {
        if (!force && state.modules.isNotEmpty()) return
        if (state.isLoadingModules) return
        viewModelScope.launch {
            state = state.copy(isLoadingModules = true)
            val loaded = runCatching {
                val cdn = com.vythera.vyxelapps.expressive.data.source.CdnSource(ctx).modules()
                val items = if (cdn.size >= 50) cdn else {
                    // No published catalogue: fall back to the same live sources the
                    // Expressive shell uses, which is slower but still works.
                    com.vythera.vyxelapps.expressive.data.CatalogRepository(
                        ctx,
                        com.vythera.vyxelapps.expressive.data.SettingsStore(ctx),
                    ).modules()
                }
                items.map { it.toGitHubRepo() }
            }.getOrDefault(emptyList())
            state = state.copy(modules = loaded, isLoadingModules = false)
        }
    }

    /**
     * Downloads a module and flashes it through the device's root manager.
     *
     * Deliberately not routed through [downloadAndInstall]: that path verifies an APK
     * signature and hands the file to `PackageInstaller`, which would reject a
     * flashable zip with a parse error. A module goes to the root manager instead.
     */
    fun installModule(repo: GitHubRepo) {
        if (state.moduleInstall?.running == true) return
        viewModelScope.launch {
            fun log(line: String) {
                state = state.copy(
                    moduleInstall = state.moduleInstall?.let { it.copy(lines = it.lines + line) }
                )
            }
            state = state.copy(moduleInstall = ModuleInstallUi(name = repo.name))

            val manager = runCatching { com.vythera.vyxelapps.root.RootAccess.detectManager() }
                .getOrDefault(com.vythera.vyxelapps.root.RootManager.None)
            if (!manager.available) {
                log("No root manager found on this device.")
                log("Magisk, KernelSU or APatch is needed to flash a module.")
                state = state.copy(
                    moduleInstall = state.moduleInstall?.copy(running = false, success = false)
                )
                return@launch
            }

            // Repo-listed modules carry no zip until their release is looked up.
            var zip = repo.apkUrl
            if (zip.isBlank()) {
                log("Looking up the latest release…")
                zip = runCatching {
                    com.vythera.vyxelapps.expressive.data.CatalogRepository(
                        ctx,
                        com.vythera.vyxelapps.expressive.data.SettingsStore(ctx),
                    ).resolve(repo.toAppItem()).downloadUrl.orEmpty()
                }.getOrDefault("")
            }
            if (zip.isBlank()) {
                log("This module has no downloadable zip.")
                state = state.copy(
                    moduleInstall = state.moduleInstall?.copy(running = false, success = false)
                )
                return@launch
            }

            log("Downloading ${repo.name}…")
            val target = File(ctx.cacheDir, "modules/${repo.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}.zip")
            val ok = runCatching {
                com.vythera.vyxelapps.expressive.install.FastDownloader.download(zip, target)
            }.isSuccess
            if (!ok) {
                log("Download failed.")
                state = state.copy(
                    moduleInstall = state.moduleInstall?.copy(running = false, success = false)
                )
                return@launch
            }

            log("Installing with ${manager.label}…")
            val flashed = com.vythera.vyxelapps.root.RootAccess.installModule(target, manager) { line ->
                log(line)
            }
            log(if (flashed) "Done. Reboot to activate." else "Install failed.")
            state = state.copy(
                moduleInstall = state.moduleInstall?.copy(running = false, success = flashed)
            )
        }
    }

    fun dismissModuleInstall() {
        if (state.moduleInstall?.running == true) return
        state = state.copy(moduleInstall = null)
    }

    /** Reboots, only from an explicit confirmation in the console dialog. */
    fun rebootDevice() {
        viewModelScope.launch { runCatching { com.vythera.vyxelapps.root.RootAccess.reboot() } }
    }

    fun downloadFromScanResult(result: AppScanResult) {
        val downloadUrl = when (val link = result.link) {
            is ScanLink.Url  -> link.link
            is ScanLink.Xapk -> link.link
            else             -> return
        }
        if (downloadUrl.isEmpty()) return
        val repoId = kotlin.math.abs(result.packageName.hashCode()).toLong() + 3_000_000_000L
        val repo   = GitHubRepo(
            id        = repoId,
            name      = result.appName,
            full_name = result.packageName,
            owner     = RepoOwner(login = result.source.name, avatar_url = result.iconUrl)
        )
        val asset = ReleaseAsset(
            name                 = "${result.appName}.apk",
            browser_download_url = downloadUrl,
            size                 = 0L,
            content_type         = "application/vnd.android.package-archive"
        )
        downloadAndInstall(repo, asset)
    }

    fun scanAllApps() {
        if (state.isMultiSourceScanning) return
        state = state.copy(isMultiSourceScanning = true, multiSourceUpdates = emptyList(), lastScanDone = false)
        viewModelScope.launch {
            try {
                val extraGH = state.settings.trackedApps.mapNotNull { t ->
                    val parts = t.repoFullName.split("/")
                    if (parts.size >= 2) com.vythera.vyxelapps.updater.GHApp(t.packageName, parts[0], parts[1]) else null
                }
                UpdateScanEngine(ctx, state.settings.githubToken, extraGH).scan().collect { updates ->
                    state = state.copy(multiSourceUpdates = updates)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Previously this caught and discarded everything, so a failed scan
                // was indistinguishable from "no updates found": empty list, no
                // spinner, no message. Log it and keep whatever was already found.
                android.util.Log.e("VyxelScan", "scanAllApps failed", e)
            } finally {
                state = state.copy(isMultiSourceScanning = false, lastScanDone = true)
            }
        }
    }

    fun addTrackedApp(app: TrackedApp) {
        val newSettings = state.settings.copy(trackedApps = state.settings.trackedApps + app)
        state = state.copy(settings = newSettings)
        prefs.saveSettings(newSettings)
        scanAllApps()
    }

    fun removeTrackedApp(id: String) {
        val newSettings = state.settings.copy(trackedApps = state.settings.trackedApps.filter { it.id != id })
        state = state.copy(settings = newSettings)
        prefs.saveSettings(newSettings)
    }

    private var trackSearchJob: kotlinx.coroutines.Job? = null

    fun searchForTracking(query: String) {
        trackSearchJob?.cancel()
        if (query.isBlank()) {
            state = state.copy(trackSearchResults = emptyList(), isTrackSearching = false)
            return
        }
        state = state.copy(isTrackSearching = true, trackSearchResults = emptyList())
        trackSearchJob = viewModelScope.launch {
            delay(120)
            val seen      = mutableSetOf<Long>()
            val mutex     = Mutex()
            val collected = mutableListOf<GitHubRepo>()
            val qt        = query.trim()

            suspend fun merge(items: List<GitHubRepo>) {
                val fresh = items.filter { seen.add(it.id) }
                if (fresh.isEmpty()) return
                collected.addAll(fresh)
                state = state.copy(trackSearchResults = collected.rankByRelevance(qt))
            }

            // Anything already in memory (F-Droid, IzzyOnDroid, Flathub, WinGet and
            // the CDN index) answers instantly and costs no request.
            val local = (state.fdroidApps + state.izzyApps + state.flathubApps +
                state.wingetApps + state.gitlabApps + state.codebergApps +
                state.trending + state.tools + state.media)
                .distinctBy { it.id }
                .filter { repo ->
                    fuzzyMatch(repo.name, qt.lowercase()) ||
                        (!repo.description.isNullOrEmpty() && fuzzyMatch(repo.description, qt.lowercase()))
                }
            if (local.isNotEmpty()) mutex.withLock { merge(local) }

            // Previously this searched GitHub twice and nothing else, so tracking an
            // app that lives on F-Droid, GitLab or Codeberg silently returned nothing
            // — and the two calls burned the 10/min anonymous GitHub budget.
            val j1 = launch {
                safeApi { RetrofitClient.service.searchRepos(qt, perPage = 15) }
                    ?.items?.map { it.copy(source = AppSource.GITHUB) }
                    ?.let { mutex.withLock { merge(it) } }
            }
            val j2 = launch {
                safeApi { GitLabClient.service.searchProjects(query = qt, perPage = 10) }
                    ?.map { it.toUnifiedRepo() }
                    ?.let { mutex.withLock { merge(it) } }
            }
            val j3 = launch {
                safeApi { CodebergClient.service.searchRepos(query = qt, limit = 10) }
                    ?.data?.map { it.toUnifiedRepo() }
                    ?.let { mutex.withLock { merge(it) } }
            }
            j1.join(); j2.join(); j3.join()
            state = state.copy(isTrackSearching = false)
        }
    }

    fun clearTrackSearch() {
        trackSearchJob?.cancel()
        state = state.copy(trackSearchResults = emptyList(), isTrackSearching = false)
    }

    fun ignoreVersion(repoId: Long, tag: String) {
        val key     = "$repoId:$tag"
        val updated = state.ignoredVersions + key
        state = state.copy(
            ignoredVersions = updated,
            updates         = state.updates.filter { "${it.repoId}:${it.latestTag}" != key }
        )
        prefs.saveIgnoredVersions(updated)
    }

    fun recordInstall(repo: GitHubRepo, tagName: String, apkPath: String, packageName: String = "") {
        val srcStr   = repo.source?.name?.lowercase() ?: "github"
        val entry    = InstallHistoryEntry(repo.id, repo.name, repo.owner.login, tagName, apkPath, packageName = packageName, source = srcStr, iconUrl = repo.iconUrlOrNull)
        val sameRepo = state.installHistory.filter { it.repoId == repo.id } + entry
        val others   = state.installHistory.filter { it.repoId != repo.id }
        if (sameRepo.size > 3) {
            sameRepo.dropLast(3).forEach { try { java.io.File(it.apkPath).delete() } catch (_: Exception) {} }
        }
        val pruned = others + sameRepo.takeLast(3)
        state = state.copy(installHistory = pruned)
        prefs.saveInstallHistory(pruned)
    }

    fun rollbackTo(entry: InstallHistoryEntry) {
        viewModelScope.launch {
            try {
                val file = java.io.File(entry.apkPath)
                if (!file.exists()) { state = state.copy(error = "APK file not found for rollback"); return@launch }

                // Same gate as a fresh install: a cached APK is still an APK, and
                // a downgrade signed by a different key is exactly what we block.
                val check = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ApkVerifier.verify(ctx, file, entry.packageName.takeIf { it.isNotEmpty() })
                }
                if (!check.isSafeToInstall) {
                    updateInstall(entry.repoId) { copy(verification = check, error = check.message()) }
                    state = state.copy(error = check.message())
                    return@launch
                }
                updateInstall(entry.repoId) { copy(verification = check) }

                if (ShizukuInstaller.isAvailable() && ShizukuInstaller.hasPermission()) {
                    ShizukuInstaller.install(ctx, file,
                        onSuccess = { updateInstall(entry.repoId) { copy(isInstalled = true) } },
                        onFailure = { try { launchSystemInstaller(file) } catch (_: Exception) {} }
                    )
                } else {
                    launchSystemInstaller(file)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state = state.copy(error = "Rollback failed: ${e.message}")
            }
        }
    }

    fun syncStarredRepos() {
        if (state.settings.githubToken.isEmpty()) {
            state = state.copy(error = "Sign in with GitHub token first"); return
        }
        viewModelScope.launch {
            try {
                val starred = RetrofitClient.service.getStarredRepos(perPage = 50)
                val merged  = (starred + state.favourites).distinctBy { it.id }
                state = state.copy(favourites = merged)
                prefs.saveFavourites(merged)
            } catch (e: Exception) {
                state = state.copy(error = "Could not sync starred: ${e.message}")
            }
        }
    }

    fun setCompareTarget(repo: GitHubRepo?) { state = state.copy(compareTargetRepo = repo) }

    fun checkSelfUpdate() {
        val rawPrefs  = ctx.getSharedPreferences("vyxel_prefs", android.content.Context.MODE_PRIVATE)
        val lastCheck = rawPrefs.getLong("last_update_check", 0L)
        if (System.currentTimeMillis() - lastCheck < 6 * 60 * 60 * 1000L) return
        viewModelScope.launch {
            try {
                val release = RetrofitClient.service.getLatestRelease("NikhilKain", "vyxel-apps")
                val latest  = release.tag_name.trimStart('v', 'V')
                val current = BuildConfig.VERSION_NAME
                if (isNewerVersion(latest, current)) {
                    val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    state = state.copy(
                        selfUpdateInfo = SelfUpdateInfo(
                            latestVersion = release.tag_name,
                            apkUrl        = apkAsset?.browser_download_url
                                ?: "https://github.com/NikhilKain/vyxel-apps/releases/latest",
                            changelog     = release.body ?: ""
                        )
                    )
                }
                rawPrefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
            } catch (_: Exception) {}
        }
    }

    fun dismissSelfUpdate() {
        state = state.copy(selfUpdateDismissed = true)
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
