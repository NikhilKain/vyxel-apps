package com.vythera.vyxelapps

import com.vythera.vyxelapps.expressive.data.source.relevanceScore

/**
 * Shared search ranking for the Classic UI.
 *
 * Classic previously ordered every search purely by `stargazers_count`. That has the
 * same failure the Expressive UI had: a starry repo that barely matches outranks the
 * app the user actually typed, and conversely an exact-name match on an abandoned
 * 2-star repo can outrank a well-known app.
 *
 * This reuses the Expressive scorer so both shells rank identically: text relevance
 * (all query tokens must match; exact name > prefix > word > summary) *multiplied* by
 * a quality factor. Multiplying rather than adding is the key — an exact name match
 * scores ~1000 and no additive popularity bonus can meaningfully compete with that.
 */
fun List<GitHubRepo>.rankByRelevance(query: String): List<GitHubRepo> {
    if (query.isBlank()) return sortedByDescending { it.stargazers_count }

    return asSequence()
        .map { repo -> repo to repo.searchScore(query) }
        .filter { it.second > 0 }
        .sortedWith(
            compareByDescending<Pair<GitHubRepo, Int>> { it.second }
                .thenByDescending { it.first.stargazers_count }
        )
        .map { it.first }
        .toList()
}

private fun GitHubRepo.searchScore(query: String): Int {
    val base = relevanceScore(
        name = name,
        packageName = null,
        summary = description.orEmpty(),
        description = description.orEmpty(),
        categories = emptyList(),
        query = query,
    )
    if (base == 0) return 0
    return (base * qualityMultiplier()).toInt()
}

/**
 * How much to trust an entry independent of the query.
 *
 * Curated stores have already filtered out abandoned projects, so they skip the
 * star-count penalty that raw git forges need.
 */
private fun GitHubRepo.qualityMultiplier(): Float = when (source) {
    AppSource.FDROID -> 1.9f
    AppSource.IZZY -> 1.75f
    AppSource.FLATHUB -> 1.4f
    AppSource.WINGET -> 1.0f

    // Aptoide and the module repos need their own tiers, not the star ladder.
    //
    // Neither publishes a star count — Aptoide reports install counts and carries
    // none at all — so both landed in the fallback below and scored 0.25, the rung
    // meant for a repo nobody has starred. Searching "instagram" therefore put the
    // real Instagram, an exact name match from a general store, *below* every
    // popular GitHub repo that merely mentions it in a description.
    AppSource.APTOIDE -> 1.6f
    AppSource.MODULE -> when {
        stargazers_count >= 500 -> 1.3f
        stargazers_count >= 50 -> 1.1f
        // Ranked just under the app sources on purpose: a plain query should answer
        // with apps first and offer the module as an alternative, not lead with
        // something that needs root.
        else -> 0.9f
    }

    else -> when {
        stargazers_count >= 5_000 -> 1.7f
        stargazers_count >= 1_000 -> 1.35f
        stargazers_count >= 200 -> 1.05f
        stargazers_count >= 50 -> 0.7f
        stargazers_count >= 10 -> 0.45f
        else -> 0.25f
    }
}
