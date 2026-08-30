package com.vythera.vyxelapps

import com.vythera.vyxelapps.api.GitHubRateLimit
import org.junit.Assert.assertEquals
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the arithmetic the quota meter renders and the error text that replaced the
 * blanket "No releases found." — both are cases where being wrong is silent.
 */
class GitHubRateLimitTest {

    private fun bucket(
        resource: String = GitHubRateLimit.SEARCH,
        limit: Int = 30,
        remaining: Int = 30,
        resetInSeconds: Long = 60,
        now: Long = 1_700_000_000_000L,
    ) = GitHubRateLimit.Bucket(
        resource = resource,
        limit = limit,
        remaining = remaining,
        resetEpochSeconds = now / 1000 + resetInSeconds,
        observedAtMillis = now,
    )

    private val now = 1_700_000_000_000L

    @Test
    fun `fraction is remaining over limit`() {
        assertEquals(1f, bucket(limit = 30, remaining = 30).fraction, 0.001f)
        assertEquals(0.5f, bucket(limit = 30, remaining = 15).fraction, 0.001f)
        assertEquals(0f, bucket(limit = 30, remaining = 0).fraction, 0.001f)
    }

    /**
     * GitHub has been known to answer 0 for the limit on an error response. Dividing
     * by it would paint an empty bar on a budget that is actually untouched, so the
     * unknown case reads as full rather than spent.
     */
    @Test
    fun `a zero limit does not produce an empty bar`() {
        assertEquals(1f, bucket(limit = 0, remaining = 0).fraction, 0.001f)
    }

    @Test
    fun `used is limit minus remaining and never negative`() {
        assertEquals(12, bucket(limit = 30, remaining = 18).used)
        // A reading taken across a window boundary can report more remaining than the
        // limit; "-2 used" is not a thing a meter should ever display.
        assertEquals(0, bucket(limit = 30, remaining = 32).used)
    }

    @Test
    fun `countdown reaches zero and stops there`() {
        val b = bucket(resetInSeconds = 45, now = now)
        assertEquals(45L, b.secondsUntilReset(now))
        assertEquals(0L, b.secondsUntilReset(now + 60_000))
        assertFalse(b.isExpired(now))
        assertTrue(b.isExpired(now + 46_000))
    }

    /**
     * The window elapsing means the budget refilled, so the reading is stale, not a
     * report of an empty bucket — the meter drops it rather than showing "0 left".
     */
    @Test
    fun `a spent bucket expires once its window passes`() {
        val spent = bucket(remaining = 0, resetInSeconds = 30, now = now)
        assertFalse(spent.isExpired(now))
        assertTrue(spent.isExpired(now + 31_000))
    }

    @Test
    fun `wait is seconds under ninety and nearest minutes above`() {
        assertEquals("45s", formatWait(45))
        assertEquals("89s", formatWait(89))
        assertEquals("2 min", formatWait(90))
        // 2 min 1 s is two minutes, not three — rounding up here would overstate the
        // wait by nearly a full minute just as the user decides whether to bother.
        assertEquals("2 min", formatWait(121))
        assertEquals("3 min", formatWait(150))
        assertEquals("60 min", formatWait(3600))
    }

    @Test
    fun `a null failure still reports the repo as having no releases`() {
        assertEquals("No releases found.", releaseLookupError(null))
    }

    /**
     * The regression this fixes: a spent quota used to be reported as "No releases
     * found.", which is a claim about the repo rather than about the request, and is
     * why an app with published APKs looked like it had none.
     */
    @Test
    fun `a rate-limited failure is not reported as a missing release`() {
        val text = releaseLookupError(httpException(403))
        assertFalse(text.contains("No releases"))
        assertTrue(text.contains("rate limit", ignoreCase = true))
        assertTrue(text.contains("Personal Access Token"))
    }

    @Test
    fun `a 404 is a genuinely missing release`() {
        assertEquals("No releases found.", releaseLookupError(httpException(404)))
    }

    @Test
    fun `a rejected token says so rather than blaming the repo`() {
        assertTrue(releaseLookupError(httpException(401)).contains("token"))
    }

    @Test
    fun `a non-HTTP failure is reported as a connectivity problem`() {
        val text = releaseLookupError(java.io.IOException("no route to host"))
        assertTrue(text.contains("Couldn't reach GitHub"))
    }

    @Test
    fun `githubHttpCode reads the status and ignores other exceptions`() {
        assertTrue(githubHttpCode(httpException(429)) == 429)
        assertNull(githubHttpCode(java.io.IOException("boom")))
        assertNull(githubHttpCode(null))
    }

    private fun httpException(code: Int) = retrofit2.HttpException(
        retrofit2.Response.error<Any>(
            code,
            "".toResponseBody(null),
        )
    )
}
