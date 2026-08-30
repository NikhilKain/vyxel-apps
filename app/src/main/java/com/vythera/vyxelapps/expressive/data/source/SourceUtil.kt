package com.vythera.vyxelapps.expressive.data.source

import android.os.Build
import java.time.Instant

/** Parses the ISO-8601 timestamps every git forge returns. */
internal fun String?.isoToEpochMillis(): Long {
    if (this.isNullOrBlank()) return 0L
    return runCatching { Instant.parse(this).toEpochMilli() }.getOrElse {
        // GitLab sometimes returns an offset like `2026-07-29T01:01:58.000+00:00`.
        runCatching {
            java.time.OffsetDateTime.parse(this).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }
}

/**
 * The inverse of [isoToEpochMillis], for handing a timestamp back to Classic.
 *
 * Deliberately truncated to whole seconds. Classic's trust score parses dates with
 * `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")`, which is exact — a fractional part
 * makes it throw, and the catch scores the repo as 999 days stale. `Instant.toString`
 * omits the fraction only when it is zero, so truncating is what keeps the two sides
 * agreeing rather than silently re-introducing the bug this carries the value to fix.
 *
 * Returns "" for an unset timestamp so the score treats the date as unknown instead
 * of dating the repo to 1970.
 */
internal fun epochMillisToIso(millis: Long): String {
    if (millis <= 0L) return ""
    return runCatching {
        Instant.ofEpochMilli(millis)
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
            .toString()
    }.getOrDefault("")
}

/** Candidate release asset, normalised across forges. */
internal data class ReleaseAsset(val name: String, val url: String, val size: Long)

/**
 * Chooses the APK a release should install.
 *
 * Multi-ABI releases ship several APKs; picking the one matching the device's
 * primary ABI avoids handing the user a build that won't run. Universal builds are
 * the safe middle ground, and anything obviously not a release build is dropped.
 */
internal fun pickBestApk(assets: List<ReleaseAsset>): ReleaseAsset? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    if (apks.isEmpty()) return null
    if (apks.size == 1) return apks.first()

    val rejected = Regex("debug|androidTest|unsigned", RegexOption.IGNORE_CASE)
    val usable = apks.filterNot { rejected.containsMatchIn(it.name) }.ifEmpty { apks }

    val abis = Build.SUPPORTED_ABIS.orEmpty()
    for (abi in abis) {
        usable.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
    }
    usable.firstOrNull { it.name.contains("universal", ignoreCase = true) }?.let { return it }
    usable.firstOrNull { it.name.contains("all", ignoreCase = true) }?.let { return it }

    // Largest remaining build is usually the universal one.
    return usable.maxByOrNull { it.size } ?: usable.first()
}

/**
 * Repo *browsing* results are dominated by libraries and toy projects, so the
 * featured rails filter them out.
 *
 * This is deliberately NOT applied to explicit searches: if someone types "retrofit
 * wrapper" they want the wrapper, and silently dropping it looks like the search is
 * broken.
 */
internal fun looksLikeAndroidApp(name: String, description: String?, topics: List<String>): Boolean {
    val haystack = (name + " " + description.orEmpty() + " " + topics.joinToString(" ")).lowercase()
    val libraryish = Regex(
        "\\b(library|sdk|plugin|sample|demo|tutorial|awesome|template|boilerplate|" +
            "cheatsheet|roadmap|interview|course|book|dotfiles|wrapper|binding)\\b"
    )
    if (libraryish.containsMatchIn(haystack)) return false
    return true
}

// --------------------------------------------------------------- relevance

/**
 * Compiled once.
 *
 * This used to be an inline `Regex(...)` inside [normalizeText], so a new pattern was
 * compiled on every call — five per item, for every item, on every re-rank. With a
 * few hundred results and one re-rank per source that ran into tens of thousands of
 * regex compilations per keystroke and was the main reason search felt slow.
 */
private val NonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")

/** Lowercases and strips punctuation so "F-Droid", "f droid" and "FDroid" all match. */
internal fun normalizeText(input: String): String =
    input.lowercase().replace(NonAlphanumeric, " ").trim()

internal fun queryTokens(query: String): List<String> =
    normalizeText(query).split(' ').filter { it.isNotBlank() }

/**
 * An entry's searchable text, normalised once.
 *
 * Results accumulate as sources answer and the whole list is re-ranked on each
 * arrival, so without this the same item's fields were re-normalised on every pass.
 * Built once per item and reused for the life of a query.
 */
internal class SearchDoc(
    val normName: String,
    val normPkg: String,
    val normSummary: String,
    val normCategories: String,
    val normDescription: String,
    val normSearchTerms: String = "",
) {
    val nameWords: List<String> = normName.split(' ')
    val haystack: String =
        "$normName $normPkg $normSummary $normCategories $normDescription"

    /**
     * Distinct words worth spell-checking a query token against.
     *
     * Deliberately excludes the description: fuzzy matching is O(words) per token,
     * and a typo that only resembles a word buried in paragraph three is far more
     * likely to be noise than intent.
     */
    val fuzzyWords: Set<String> = buildSet {
        addAll(nameWords)
        addAll(normSummary.split(' '))
        addAll(normCategories.split(' '))
        // Extra terms describing what the app is FOR, supplied by the index
        // rather than by upstream prose. See [searchTerms].
        addAll(normSearchTerms.split(' '))
    }.filterTo(HashSet()) { it.length >= 3 }

    companion object {
        fun of(
            name: String,
            packageName: String?,
            summary: String,
            description: String,
            categories: List<String>,
            searchTerms: List<String> = emptyList(),
        ) = SearchDoc(
            normName = normalizeText(name),
            normPkg = packageName?.lowercase().orEmpty(),
            normSummary = normalizeText(summary),
            normCategories = normalizeText(categories.joinToString(" ")),
            // Descriptions are long; only the head is worth scanning.
            normDescription = normalizeText(description.take(600)),
            normSearchTerms = normalizeText(searchTerms.joinToString(" ")),
        )
    }
}

/**
 * Is [candidate] reachable from [token] within [max] single-character edits?
 *
 * Two-row Levenshtein with a length-difference early exit, so a mistyped query
 * still finds the app. Bounded deliberately: unlimited edit distance turns every
 * short query into a match for everything.
 */
private fun withinEditDistance(token: String, candidate: String, max: Int): Boolean {
    val lenDiff = token.length - candidate.length
    if (lenDiff > max || -lenDiff > max) return false
    if (token == candidate) return true

    var prev = IntArray(candidate.length + 1) { it }
    var cur = IntArray(candidate.length + 1)
    for (i in 1..token.length) {
        cur[0] = i
        var rowMin = cur[0]
        for (j in 1..candidate.length) {
            val cost = if (token[i - 1] == candidate[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            if (cur[j] < rowMin) rowMin = cur[j]
        }
        // Every path through this row already exceeds the budget.
        if (rowMin > max) return false
        val swap = prev; prev = cur; cur = swap
    }
    return prev[candidate.length] <= max
}

/** How many edits to forgive — proportional to length, so "vlc" is not fuzzy at all. */
private fun editBudget(token: String): Int = when {
    token.length <= 3 -> 0
    token.length <= 6 -> 1
    else -> 2
}

/** 3 = strong, 2 = solid, 1 = only via spelling correction, 0 = absent. */
private fun tokenQuality(doc: SearchDoc, token: String): Int {
    if (doc.nameWords.any { it == token || it.startsWith(token) }) return 3
    if (doc.haystack.contains(token)) return 2
    val budget = editBudget(token)
    if (budget > 0 && doc.fuzzyWords.any { withinEditDistance(token, it, budget) }) return 1
    return 0
}

/** Scores a pre-normalised document; see [relevanceScore] for the semantics. */
internal fun relevanceScore(doc: SearchDoc, query: String): Int {
    val tokens = queryTokens(query)
    if (tokens.isEmpty()) return 0

    // Coverage-weighted, not all-or-nothing.
    //
    // This used to demand that EVERY token appear literally, which is why
    // "offline podcast player" returned nothing at all and any typo returned
    // nothing at all. Pure OR is the wrong fix — it buries the exact match under
    // everything sharing one common word — so instead each token contributes a
    // quality, and the total is scaled by how much of the query was actually
    // matched. Full matches still outrank partial ones by construction.
    var qualitySum = 0
    var matched = 0
    var fuzzyOnly = 0
    for (token in tokens) {
        val q = tokenQuality(doc, token)
        if (q > 0) { matched++; qualitySum += q }
        if (q == 1) fuzzyOnly++
    }
    if (matched == 0) return 0

    val coverage = matched.toFloat() / tokens.size
    // A single unmatched word in a two-word query is fine; matching only one of
    // four is noise.
    val minCoverage = if (tokens.size <= 2) 0.5f else 0.6f
    if (coverage < minCoverage) return 0

    val whole = normalizeText(query)
    var score = when {
        doc.normName == whole -> 1000
        doc.normName.startsWith(whole) -> 720
        doc.nameWords.any { it == whole } -> 640
        doc.normName.contains(whole) -> 520
        tokens.all { token -> doc.nameWords.any { it.startsWith(token) } } -> 400
        tokens.all { doc.normName.contains(it) } -> 320
        doc.normPkg.contains(whole) -> 240
        doc.normSummary.contains(whole) -> 260
        tokens.all { doc.normSummary.contains(it) } -> 200
        doc.normCategories.contains(whole) -> 110
        doc.normSearchTerms.contains(whole) -> 150
        else -> 60
    }
    if (doc.normName.length <= whole.length + 3) score += 40

    // Partial matches rank below complete ones rather than being excluded.
    score = (score * coverage).toInt()
    // A spelling-corrected hit is a guess, so it sits under a literal one — but
    // it still appears, which is the whole point.
    if (fuzzyOnly > 0) score = score * 70 / 100
    // Reward tokens matched strongly (in the name) over ones merely present.
    score += qualitySum * 12
    return score
}

/**
 * Ranks one entry against a query.
 *
 * Returns 0 when the entry should not appear at all. Tokens are scored and the
 * result scaled by coverage rather than gated by it: strict AND meant a single
 * unmatched word — or one typo — returned nothing, while plain OR would bury the
 * exact match under everything sharing a common word.
 */
internal fun relevanceScore(
    name: String,
    packageName: String?,
    summary: String,
    description: String,
    categories: List<String>,
    query: String,
): Int = relevanceScore(
    SearchDoc.of(name, packageName, summary, description, categories),
    query,
)
