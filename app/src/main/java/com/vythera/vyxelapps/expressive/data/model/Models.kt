package com.vythera.vyxelapps.expressive.data.model

import kotlinx.serialization.Serializable

/**
 * Which ecosystem an entry can actually be installed into.
 *
 * [Module] is its own platform rather than a flavour of [Android] precisely because
 * a root module is *not* an APK: it is a zip that a root manager flashes. Marking
 * these Android would put an Install button on them, hand a zip to `PackageInstaller`
 * and fail in a way that reads like a broken store. Keeping the distinction in the
 * type means the UI cannot make that mistake.
 */
enum class Platform {
    Android, Linux, Windows, Module;

    /**
     * Whether this platform is something you cannot install from the phone.
     *
     * The "show desktop sources" setting keys off this rather than off
     * `!= Android`, because [Module] is neither Android nor desktop: root modules
     * are for the device in your hand, so hiding them along with WinGet would be
     * wrong — but they still are not APKs.
     */
    val isDesktop: Boolean get() = this == Linux || this == Windows
}

enum class SourceId(
    /** Short label for the badge on a card, where it shares a row with a figure. */
    val displayName: String,
    val platform: Platform,
    /** Repo-style sources expose stars; catalog sources expose install counts. */
    val isRepoHost: Boolean = false,
    /**
     * The repository's real name, for the Sources screen.
     *
     * Blank means [displayName] already is the real name — only the two module repos
     * are abbreviated for the badge, and only they need spelling out again.
     */
    private val longName: String = "",
    /**
     * Whether this source contributes to search but not to the home screen.
     *
     * Kept as a property rather than "returns an empty list from featured()" so the
     * Sources screen can say *search only* instead of reporting "0 apps synced",
     * which reads as a broken source rather than a deliberate one.
     */
    val searchOnly: Boolean = false,
    /**
     * Whether loading this source costs many requests rather than one.
     *
     * The module organisations are enumerated a page of 100 repos at a time — a
     * dozen requests against GitHub's 60/hour budget. That is a fine price for a
     * screen the user deliberately opened and that shows a spinner while it fills,
     * and a terrible one to pay on every app launch or every keystroke in the main
     * search box. Sources marked here are reached only from the Modules screen.
     */
    val heavyCrawl: Boolean = false,
    /**
     * Whether this source only answers "is my installed app out of date".
     *
     * Some mirrors expose a bulk update check but no browsable catalogue, so they can
     * never contribute a rail or a search result. Marked so the Sources screen says
     * what the source is *for* rather than reporting it as synced with zero apps.
     */
    val scanOnly: Boolean = false,
) {
    FDroid("F-Droid", Platform.Android),
    IzzyOnDroid("IzzyOnDroid", Platform.Android),
    GitHub("GitHub", Platform.Android, isRepoHost = true),
    GitLab("GitLab", Platform.Android, isRepoHost = true),
    Codeberg("Codeberg", Platform.Android, isRepoHost = true),
    Flathub("Flathub", Platform.Linux),
    WinGet("WinGet", Platform.Windows),

    /**
     * Aurora OSS's own build server.
     *
     * A small, hand-maintained set — Aurora Store, AuroraDroid, AppWarden — served
     * from one JSON tree. Notably *not* a Google Play catalogue: Aurora reaches Play
     * from the client at runtime, so what is downloadable here is Aurora's own
     * software and nothing else.
     */
    Aurora("Aurora OSS", Platform.Android, longName = "Aurora OSS build server"),

    /**
     * The one general-purpose store in the list, and so the only place the
     * proprietary apps people ask for by name actually exist.
     *
     * Search-only. Its "most downloaded" listing is dominated by preinstalled system
     * packages — Private Compute Services, Samsung My Files — which are nobody's idea
     * of a discovery rail, and that endpoint does not return the malware ranking this
     * source is gated on, so entries from it could not be vouched for anyway. Aptoide
     * earns its place by answering "where is Instagram", which is a search.
     */
    Aptoide("Aptoide", Platform.Android, searchOnly = true),

    /**
     * APKPure, used for update scanning only.
     *
     * It answers "is there a newer build of this installed app" for apps that exist
     * nowhere else — the Play-only half of a typical phone, which every other source
     * here is blind to by construction. It is not browsable: there is no catalogue
     * call, only a bulk "here are my packages, what is newer" query, so it has no
     * rail and no search results and says so on the Sources screen.
     */
    ApkPure("APKPure", Platform.Android, scanOnly = true),

    /**
     * Root modules, from the two curated repositories that carry real metadata.
     *
     * These are the same indexes Modex reads, so a module found here is the module
     * found there. Light enough to join the main search: two requests for about a
     * hundred and seventy modules with full descriptions.
     *
     * Names are kept short deliberately. The badge and the popularity figure share
     * one row on a card, and the full repository names pushed the star count into
     * wrapping down the side of the card one digit per line. Anything longer than
     * "IzzyOnDroid" does not fit; the Sources screen has room for the real names.
     */
    MagiskAlt("Magisk Alt", Platform.Module, longName = "Magisk Modules Alt Repo"),
    Googlers("Googlers", Platform.Module, longName = "Googlers Repo"),

    /**
     * The two module organisations, where the actual volume lives.
     *
     * The reviewed indexes above hold a couple of hundred modules between them —
     * that is genuinely all there is in curated repos. These orgs exist purely to
     * hold modules, one repo per module, and are over a thousand between them.
     */
    XposedRepo(
        "Xposed", Platform.Module,
        longName = "Xposed Modules Repository", heavyCrawl = true,
    ),
    MagiskLegacy(
        "Magisk Repo", Platform.Module,
        longName = "Magisk Modules Repo (original)", heavyCrawl = true,
    );

    /** Name for places with room for it — currently the Sources screen. */
    val fullName: String get() = longName.ifBlank { displayName }
}

/**
 * One app, normalised across every source.
 *
 * Sources populate what they can and leave the rest at defaults, so the UI can render
 * a consistent card whether the entry came from an F-Droid index or a GitHub release.
 */
@Serializable
data class AppItem(
    val id: String,
    val source: SourceId,
    val name: String,
    val summary: String = "",
    val description: String = "",
    val iconUrl: String? = null,
    val packageName: String? = null,
    val version: String? = null,
    val versionCode: Long = 0L,
    val updatedAt: Long = 0L,
    /**
     * When the app was FIRST published, as opposed to last updated.
     *
     * Only the F-Droid-protocol sources carry this (`added` in index-v1); the CDN
     * index and the repo hosts leave it at 0, which simply excludes them from the
     * "Newly launched" rail rather than dating them to 1970.
     */
    val addedAt: Long = 0L,
    val author: String? = null,
    val license: String? = null,
    val categories: List<String> = emptyList(),
    val screenshots: List<String> = emptyList(),
    /** Direct APK URL. Null for desktop catalogs and for repos not yet resolved. */
    val downloadUrl: String? = null,
    val sizeBytes: Long = 0L,
    val website: String? = null,
    val sourceCodeUrl: String? = null,
    val donateUrl: String? = null,
    val changelog: String? = null,
    val stars: Int = 0,
    /**
     * Fork count, for repo hosts that publish one.
     *
     * Carried purely so the Vyxel Trust Score comes out the same in both shells. The
     * score is computed once, by Classic's resolver, from a [com.vythera.vyxelapps.GitHubRepo] —
     * and on this side that object is synthesised from an AppItem. Any input the
     * AppItem cannot carry is read as zero, which the score treats as "no forks"
     * rather than "not known", so the identical app scored lower here than in Classic.
     */
    val forks: Int = 0,
    val installs: Long = 0L,
    val verified: Boolean = false,
    /** Shell command for desktop sources (`winget install ...`, `flatpak install ...`). */
    val installCommand: String? = null,
    val antiFeatures: List<String> = emptyList(),
    val minSdk: Int = 0,
    /**
     * Overline shown on the hero card in place of "FEATURED".
     *
     * Set only for CDN-pinned promo slots. It exists so a promoted entry can say what
     * it is — a store that presents paid or self-promotion as neutral editorial stops
     * being trustworthy as a catalogue.
     */
    val promoLabel: String? = null,
    /**
     * True when the entry is a repository that *might* ship an APK release, but the
     * release hasn't been resolved yet. Detail screen resolves it lazily.
     */
    val needsReleaseLookup: Boolean = false,
) {
    val platform: Platform get() = source.platform

    /**
     * A human-readable name, even when upstream only gave us a package id.
     *
     * Unresolved entries were reaching the hero carousel as literal package
     * names — "com.sweak.unlo…" as a featured title. Anything shaped like an id
     * is rewritten to its last segment, spaced and title-cased.
     */
    val displayName: String
        get() {
            if (!name.matches(Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+){2,}$"))) return name
            return name.substringAfterLast('.')
                .replace('_', ' ')
                .split(' ')
                .joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
        }

    /**
     * Summary trimmed to something that fits a card.
     *
     * Upstream text arrives with leading emoji, mixed scripts and full
     * paragraphs; cards then truncated mid-word. This keeps the first sentence,
     * drops decorative leading characters, and breaks on a word boundary.
     */
    val displaySummary: String
        get() {
            val cleaned = summary.trim()
                .trimStart { !it.isLetterOrDigit() }
                .replace(Regex("\\s+"), " ")
            val firstSentence = cleaned.substringBefore(". ").trim()
            val base = if (firstSentence.length in 20..140) firstSentence else cleaned
            if (base.length <= 110) return base
            return base.take(110).substringBeforeLast(' ').trimEnd(',', ';', ':', '-') + "…"
        }

    /**
     * True when the app drives Shizuku (or its rootless sibling, Sui).
     *
     * There is no metadata field for this anywhere upstream, so it is read off the
     * text the authors already write. That works because Shizuku is a *setup
     * requirement*: an app that needs it has to say so, or its users can't get past
     * the first screen — so it lands in the summary, the description, or a `shizuku`
     * GitHub topic essentially without exception.
     *
     * Matching is deliberately narrow. "shizuku" as a bare substring also matches
     * Japanese names and a handful of unrelated projects, so the match is anchored to
     * a word boundary, which is enough to keep the false positives down without
     * needing a curated list shipped in the APK.
     */
    val usesShizuku: Boolean
        get() = SHIZUKU_MENTION.containsMatchIn(
            buildString {
                append(name).append(' ')
                append(summary).append(' ')
                append(description).append(' ')
                categories.forEach { append(it).append(' ') }
            }
        )

    val canInstall: Boolean
        get() = platform == Platform.Android && downloadUrl != null

    /** Deduplication key — same package from two repos collapses into one entry. */
    val dedupeKey: String
        get() = packageName?.lowercase() ?: "${source.name}:${name.lowercase()}"

    /** First letter used for the generated fallback tile when a source has no icons. */
    val monogram: String
        get() = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
}

/**
 * Word-anchored Shizuku mention.
 *
 * Only "shizuku" is matched, not its Sui fork: "Sui" is three letters that occur
 * inside plenty of unrelated prose, and apps supporting Sui say "Shizuku/Sui" almost
 * without exception anyway, so the narrow pattern loses nothing and misfires far less.
 */
private val SHIZUKU_MENTION = Regex("""\bshizuku\b""", RegexOption.IGNORE_CASE)

/** A catalog row: a titled, horizontally scrolling group of apps. */
data class AppRail(
    val title: String,
    val subtitle: String? = null,
    val source: SourceId?,
    val items: List<AppItem>,
)

/** Per-source load state, so one failing source never blanks the whole screen. */
sealed interface SourceState {
    data object Idle : SourceState
    data class Loading(val progress: Float? = null, val note: String? = null) : SourceState
    data class Ready(val count: Int, val syncedAt: Long) : SourceState
    data class Failed(val message: String) : SourceState

    /** A [SourceId.searchOnly] source, which has nothing to sync until you type. */
    data object SearchOnly : SourceState

    /** A [SourceId.scanOnly] source: it answers update checks and nothing else. */
    data object ScanOnly : SourceState
}

enum class SortOrder(val label: String) {
    Relevance("Relevance"),
    Updated("Recently updated"),
    Name("Name"),
    Popularity("Popularity"),
}
