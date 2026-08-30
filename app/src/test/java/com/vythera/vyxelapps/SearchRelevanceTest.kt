package com.vythera.vyxelapps

import com.vythera.vyxelapps.expressive.data.source.SearchDoc
import com.vythera.vyxelapps.expressive.data.source.relevanceScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search scorer, which was rewritten from strict AND to coverage-weighted
 * scoring with spelling tolerance.
 *
 * These are the cases that used to return nothing at all: a query with one word
 * the entry doesn't carry, and a query with a typo. Both are what people
 * actually type.
 */
class SearchRelevanceTest {

    private fun doc(
        name: String,
        pkg: String = "",
        summary: String = "",
        categories: List<String> = emptyList(),
        terms: List<String> = emptyList(),
    ) = SearchDoc.of(name, pkg, summary, "", categories, terms)

    private val newPipe = doc(
        name = "NewPipe",
        pkg = "org.schabi.newpipe",
        summary = "A libre lightweight streaming front-end for Android",
        categories = listOf("Multimedia", "Video"),
    )
    private val firefox = doc(
        name = "Firefox",
        pkg = "org.mozilla.firefox",
        summary = "Fast, private and safe web browser",
        categories = listOf("Internet", "Browser"),
    )
    private val antennaPod = doc(
        name = "AntennaPod",
        pkg = "de.danoeh.antennapod",
        summary = "Podcast manager that works offline",
        categories = listOf("Multimedia", "Audio"),
    )

    // ── exact behaviour must not regress ──────────────────────────────────────

    @Test
    fun exactNameStillWinsBigly() {
        val exact = relevanceScore(firefox, "firefox")
        val incidental = relevanceScore(newPipe, "firefox")
        assertTrue("exact name should score high, was $exact", exact > 500)
        assertEquals("unrelated entry must not match", 0, incidental)
    }

    @Test
    fun unrelatedQueryStillScoresZero() {
        assertEquals(0, relevanceScore(firefox, "spreadsheet"))
    }

    // ── the AND filter fix ────────────────────────────────────────────────────

    @Test
    fun partialMultiWordQueryNowMatches() {
        // "offline podcast player": AntennaPod has "offline" and "podcast" but
        // never the word "player". Strict AND scored this 0.
        val score = relevanceScore(antennaPod, "offline podcast player")
        assertTrue("partial match should now surface, was $score", score > 0)
    }

    @Test
    fun fullMatchOutranksPartialMatch() {
        val full = relevanceScore(antennaPod, "podcast offline")
        val partial = relevanceScore(antennaPod, "podcast offline player quicksilver")
        assertTrue(
            "coverage must still order results ($full vs $partial)",
            full > partial,
        )
    }

    @Test
    fun oneWordInCommonIsNotEnoughForALongQuery() {
        // Only "web" matches; four-token query needs 60% coverage.
        assertEquals(0, relevanceScore(firefox, "web spreadsheet ledger accounting"))
    }

    // ── spelling tolerance ────────────────────────────────────────────────────

    @Test
    fun singleCharacterTypoStillFindsTheApp() {
        assertTrue("firefx should find Firefox", relevanceScore(firefox, "firefx") > 0)
        assertTrue("newpip should find NewPipe", relevanceScore(newPipe, "newpip") > 0)
    }

    @Test
    fun transposedLettersStillFindTheApp() {
        assertTrue("frefiox should find Firefox", relevanceScore(firefox, "frefiox") > 0)
    }

    @Test
    fun correctSpellingOutranksTypo() {
        val clean = relevanceScore(firefox, "firefox")
        val typo = relevanceScore(firefox, "firefx")
        assertTrue("a guess must rank below a literal hit ($clean vs $typo)", clean > typo)
    }

    @Test
    fun shortTokensAreNotFuzzyMatched() {
        // Three letters or fewer get no edit budget — otherwise "vlc" matches
        // half the catalogue.
        assertEquals(0, relevanceScore(firefox, "vlc"))
    }

    // ── build-time AI terms ───────────────────────────────────────────────────

    @Test
    fun generatedSearchTermsMakeIntentQueriesWork() {
        val runTracker = doc(
            name = "OpenTracks",
            pkg = "de.dennisguse.opentracks",
            summary = "Record your outdoor activities",
            categories = listOf("Sports"),
            terms = listOf("running", "jogging", "gps", "workout", "fitness", "exercise"),
        )
        assertTrue(
            "the word 'running' appears nowhere in the app's own text",
            relevanceScore(runTracker, "running") > 0,
        )
        assertTrue(relevanceScore(runTracker, "jogging tracker") > 0)
    }
}
