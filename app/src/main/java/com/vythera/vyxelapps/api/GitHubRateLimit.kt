package com.vythera.vyxelapps.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import okhttp3.Response

/**
 * What is left of the GitHub API budget, as GitHub itself reports it.
 *
 * Counting requests locally cannot work here: four call sites in two shells hit the
 * API (Classic's Retrofit client, the update scanner, [com.vythera.vyxelapps.expressive.data.source.GitHubSource]
 * and the module repo crawler), OkHttp serves some of them from its disk cache without
 * touching the network at all, and the same token may be in use on another device.
 * The only number that is true is the one in the response headers, so this reads those
 * and publishes them.
 *
 * Wired in as an OkHttp **network** interceptor rather than an application one, which is
 * what makes the "some requests never reach GitHub" problem disappear: a full cache hit
 * never runs a network interceptor, so a stale `X-RateLimit-Remaining` from a cached
 * response can never be mistaken for a fresh reading.
 */
object GitHubRateLimit {

    /**
     * One of GitHub's rate-limit buckets.
     *
     * GitHub meters the search endpoints separately from everything else — 10 requests/minute
     * anonymous and 30 with a token, against 60/hour and 5000/hour for the core API.
     * They are different budgets with different windows, so they are kept apart here
     * rather than collapsed into one "requests left" number that would be wrong for
     * whichever bucket it was not describing.
     */
    data class Bucket(
        /** GitHub's own name for the bucket: "search", "core", "graphql", … */
        val resource: String,
        val limit: Int,
        val remaining: Int,
        /** Unix time, in seconds, at which [remaining] returns to [limit]. */
        val resetEpochSeconds: Long,
        /** When this reading was taken, for deciding it has gone stale. */
        val observedAtMillis: Long,
    ) {
        val used: Int get() = (limit - remaining).coerceAtLeast(0)

        /** 0f when the budget is spent, 1f when it is untouched. */
        val fraction: Float
            get() = if (limit <= 0) 1f else (remaining.toFloat() / limit).coerceIn(0f, 1f)

        /** Seconds until the window rolls over; 0 once it has. */
        fun secondsUntilReset(nowMillis: Long = System.currentTimeMillis()): Long =
            (resetEpochSeconds - nowMillis / 1000).coerceAtLeast(0)

        /**
         * True once the window this reading describes has elapsed.
         *
         * A spent search bucket that reset two minutes ago is not information the user
         * should be looking at — the honest display is "full again", which callers get
         * by treating an expired reading as no reading.
         */
        fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
            secondsUntilReset(nowMillis) <= 0L
    }

    const val SEARCH = "search"
    const val CORE = "core"

    private val _buckets = MutableStateFlow<Map<String, Bucket>>(emptyMap())

    /** Latest reading per bucket. Empty until the first GitHub request comes back. */
    val buckets: StateFlow<Map<String, Bucket>> = _buckets.asStateFlow()

    /**
     * Records the headers on one response.
     *
     * Silently ignores anything that is not a GitHub API response carrying the full
     * header set — the interceptor is attached to clients that also talk to F-Droid,
     * GitLab and Aptoide, and none of those send these headers.
     */
    fun record(response: Response) {
        if (response.request.url.host != "api.github.com") return
        val limit = response.header("X-RateLimit-Limit")?.toIntOrNull() ?: return
        val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull() ?: return
        val reset = response.header("X-RateLimit-Reset")?.toLongOrNull() ?: return
        // The resource header has only been sent since 2022; fall back to the path,
        // which distinguishes the two buckets that actually matter here.
        val resource = response.header("X-RateLimit-Resource")
            ?: if (response.request.url.encodedPath.startsWith("/search/")) SEARCH else CORE

        val bucket = Bucket(
            resource = resource,
            limit = limit,
            remaining = remaining,
            resetEpochSeconds = reset,
            observedAtMillis = System.currentTimeMillis(),
        )
        _buckets.update { current ->
            // Responses land from several coroutines at once, and a reading can arrive
            // out of order when two requests overlap. Within one window the lower
            // `remaining` is always the later truth; a higher `reset` means the window
            // rolled over and the new reading supersedes regardless.
            val previous = current[resource]
            val keepPrevious = previous != null &&
                previous.resetEpochSeconds == bucket.resetEpochSeconds &&
                previous.remaining < bucket.remaining
            if (keepPrevious) current else current + (resource to bucket)
        }
    }

    /**
     * Drops every reading.
     *
     * Called when the token changes: the old numbers describe a different budget
     * entirely (60/hour anonymous vs 5000/hour authenticated), and leaving them on
     * screen would show the user a limit they no longer have.
     */
    fun reset() {
        _buckets.value = emptyMap()
    }

    /** Attach to any OkHttp client that may talk to api.github.com. */
    val interceptor: Interceptor = Interceptor { chain ->
        chain.proceed(chain.request()).also { runCatching { record(it) } }
    }
}
