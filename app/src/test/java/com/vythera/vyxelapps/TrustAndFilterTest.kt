package com.vythera.vyxelapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TrustAndFilterTest {

    private fun daysAgo(days: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(System.currentTimeMillis() - days * 86_400_000L))
    }

    private fun repo(
        name        : String = "cool-app",
        description : String = "A nice little Android app",
        stars       : Int    = 0,
        forks       : Int    = 0,
        updatedAt   : String = daysAgo(1)
    ) = GitHubRepo(
        id                = 1L,
        name              = name,
        full_name         = "someone/$name",
        description       = description,
        stargazers_count  = stars,
        forks_count       = forks,
        updated_at        = updatedAt
    )

    // ── Trust score ──────────────────────────────────────────────────────────

    @Test
    fun `trust score stays within 0 and 100`() {
        val best  = calculateTrustScore(repo(stars = 50_000, forks = 9_000), releaseCount = 50)
        val worst = calculateTrustScore(repo(stars = 0, forks = 0, updatedAt = daysAgo(2000)), releaseCount = 0)
        assertTrue(best.score in 0..100)
        assertTrue(worst.score in 0..100)
        assertTrue(best.score > worst.score)
    }

    @Test
    fun `more stars never lowers the score`() {
        val low  = calculateTrustScore(repo(stars = 50), releaseCount = 3).score
        val high = calculateTrustScore(repo(stars = 20_000), releaseCount = 3).score
        assertTrue(high >= low)
    }

    @Test
    fun `a stale repo scores below a freshly updated one`() {
        val fresh = calculateTrustScore(repo(stars = 500, updatedAt = daysAgo(2)), releaseCount = 5).score
        val stale = calculateTrustScore(repo(stars = 500, updatedAt = daysAgo(800)), releaseCount = 5).score
        assertTrue(stale < fresh)
    }

    @Test
    fun `unparseable update timestamp does not throw`() {
        val t = calculateTrustScore(repo(updatedAt = "not-a-date"), releaseCount = 1)
        assertTrue(t.score in 0..100)
    }

    // ── Junk filter ──────────────────────────────────────────────────────────

    @Test
    fun `documentation repos are filtered out of app shelves`() {
        assertFalse(repo(name = "awesome-android").isLikelyApp())
        assertFalse(repo(name = "android-interview-questions").isLikelyApp())
        assertFalse(repo(name = "flutter-roadmap").isLikelyApp())
        assertFalse(repo(description = "A curated list of Android libraries").isLikelyApp())
    }

    @Test
    fun `a real app described with the word awesome survives`() {
        assertTrue(repo(name = "melodia", description = "An awesome music player for Android").isLikelyApp())
    }

    // ── Release / asset helpers ──────────────────────────────────────────────

    @Test
    fun `isApk matches apk assets only`() {
        assertTrue(ReleaseAsset(name = "app-release.apk").isApk())
        assertTrue(ReleaseAsset(name = "App-Release.APK").isApk())
        assertFalse(ReleaseAsset(name = "app-release.aab").isApk())
        assertFalse(ReleaseAsset(name = "sources.zip").isApk())
    }

    @Test
    fun `pre-release filter hides betas but never empties the list`() {
        val stable = Release(tag_name = "1.0", prerelease = false)
        val beta   = Release(tag_name = "1.1-beta", prerelease = true)

        assertEquals(listOf(stable), listOf(stable, beta).filterByPreReleasePref(showPre = false))
        assertEquals(listOf(stable, beta), listOf(stable, beta).filterByPreReleasePref(showPre = true))
        // Only pre-releases exist: show them rather than an empty release list.
        assertEquals(listOf(beta), listOf(beta).filterByPreReleasePref(showPre = false))
    }

    @Test
    fun `detectBestApk returns null when a release ships no apk`() {
        assertNull(detectBestApk(listOf(ReleaseAsset(name = "sources.zip"))))
    }

    @Test
    fun `detectBestApk picks the only apk without consulting device abis`() {
        val only = ReleaseAsset(name = "app-universal-release.apk")
        val result = detectBestApk(listOf(only, ReleaseAsset(name = "checksums.txt")))
        assertEquals(only, result?.asset)
    }
}
