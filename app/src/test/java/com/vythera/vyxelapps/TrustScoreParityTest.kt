package com.vythera.vyxelapps

import com.vythera.vyxelapps.expressive.data.source.epochMillisToIso
import com.vythera.vyxelapps.expressive.data.source.isoToEpochMillis
import com.vythera.vyxelapps.expressive.data.toAppItem
import com.vythera.vyxelapps.expressive.data.toGitHubRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Vyxel Trust Score has to read the same in both shells.
 *
 * It is computed once, by Classic's resolver, from a [GitHubRepo]. In Expressive that
 * object is synthesised from an [com.vythera.vyxelapps.expressive.data.model.AppItem],
 * so every input the AppItem cannot carry arrives as zero — and the score reads zero as
 * "none", not as "unknown". Forks and the update date were both being dropped, which
 * cost up to 45 points and made the identical app look far less trustworthy in one
 * shell than the other.
 */
class TrustScoreParityTest {

    /** A repo shaped like Syncthing: the case the discrepancy was noticed on. */
    private fun popularRepo(updatedAt: String) = GitHubRepo(
        id = 299354207L,
        name = "syncthing",
        full_name = "syncthing/syncthing",
        description = "Open Source Continuous File Synchronization",
        stargazers_count = 88_100,
        forks_count = 5_400,
        html_url = "https://github.com/syncthing/syncthing",
        owner = RepoOwner("syncthing", ""),
        source = AppSource.GITHUB,
        updated_at = updatedAt,
    )

    private fun isoNow(): String = epochMillisToIso(System.currentTimeMillis())

    @Test
    fun `score survives a round trip through the Expressive model`() {
        val classic = popularRepo(isoNow())
        val expressive = classic.toAppItem().toGitHubRepo()

        assertEquals(
            calculateTrustScore(classic, 10).score,
            calculateTrustScore(expressive, 10).score,
        )
    }

    @Test
    fun `forks and update date survive the round trip`() {
        val classic = popularRepo(isoNow())
        val expressive = classic.toAppItem().toGitHubRepo()

        assertEquals(classic.forks_count, expressive.forks_count)
        assertEquals(classic.stargazers_count, expressive.stargazers_count)
        assertEquals(classic.updated_at, expressive.updated_at)
    }

    /**
     * Pins the actual regression: before the fix the round trip lost 20 points of
     * forks and 25 of recency, landing on 55 where Classic said 100.
     */
    @Test
    fun `a highly trusted repo does not degrade to moderate`() {
        val classic = popularRepo(isoNow())
        val expressive = classic.toAppItem().toGitHubRepo()

        assertEquals(100, calculateTrustScore(classic, 10).score)
        assertEquals(100, calculateTrustScore(expressive, 10).score)
        assertEquals("Highly Trusted", calculateTrustScore(expressive, 10).label)
    }

    /**
     * The date must come back in the exact format Classic parses.
     *
     * `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")` is exact, so a fractional second
     * would throw and be scored as 999 days stale — silently reinstating the bug the
     * round trip exists to fix.
     */
    @Test
    fun `iso output carries no fractional seconds`() {
        val iso = epochMillisToIso(1_700_000_000_123L)
        assertEquals("2023-11-14T22:13:20Z", iso)
        assertTrue(!iso.contains('.'))

        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .parse(iso)
        assertTrue(parsed != null)
    }

    @Test
    fun `an unset timestamp stays unset rather than becoming 1970`() {
        assertEquals("", epochMillisToIso(0L))
        assertEquals("", epochMillisToIso(-1L))
        assertEquals(0L, "".isoToEpochMillis())
    }

    /** A repo with genuinely nothing going for it should still score low. */
    @Test
    fun `the fix does not inflate a weak repo`() {
        val weak = GitHubRepo(
            id = 1L,
            name = "abandoned",
            full_name = "someone/abandoned",
            description = null,
            stargazers_count = 0,
            forks_count = 0,
            owner = RepoOwner("someone", ""),
            source = AppSource.GITHUB,
            updated_at = "",
        )
        val expressive = weak.toAppItem().toGitHubRepo()
        assertEquals(
            calculateTrustScore(weak, 0).score,
            calculateTrustScore(expressive, 0).score,
        )
        assertTrue(calculateTrustScore(expressive, 0).score < 25)
    }
}
