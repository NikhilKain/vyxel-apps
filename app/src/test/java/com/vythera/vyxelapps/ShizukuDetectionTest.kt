package com.vythera.vyxelapps

import com.vythera.vyxelapps.expressive.data.model.AppItem
import com.vythera.vyxelapps.expressive.data.model.SourceId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Shizuku filter is only as good as this detection.
 *
 * There is no upstream field for "needs Shizuku", so it is read off the text the
 * authors write. The cases below are the shapes that actually appear in F-Droid
 * summaries, IzzyOnDroid descriptions and GitHub topics — plus the false positives
 * that a naive substring match would produce.
 */
class ShizukuDetectionTest {

    private fun item(
        name: String = "Example",
        summary: String = "",
        description: String = "",
        categories: List<String> = emptyList(),
    ) = AppItem(
        id = "x",
        source = SourceId.FDroid,
        name = name,
        summary = summary,
        description = description,
        categories = categories,
    )

    @Test
    fun detectsMentionInSummary() {
        assertTrue(item(summary = "Requires Shizuku to work").usesShizuku)
    }

    @Test
    fun detectsMentionInDescription() {
        assertTrue(
            item(description = "Grant permissions via ADB or shizuku.").usesShizuku
        )
    }

    /** GitHub topics land in `categories`, and `shizuku` is a common one. */
    @Test
    fun detectsGithubTopic() {
        assertTrue(item(categories = listOf("android", "shizuku")).usesShizuku)
    }

    @Test
    fun detectionIsCaseInsensitive() {
        assertTrue(item(summary = "Works with SHIZUKU").usesShizuku)
    }

    @Test
    fun matchesAcrossPunctuation() {
        assertTrue(item(summary = "Needs root or Shizuku/Sui.").usesShizuku)
    }

    @Test
    fun plainAppIsNotFlagged() {
        assertFalse(item(summary = "A simple offline notepad").usesShizuku)
    }

    /**
     * "Sui" is deliberately not matched on its own — three letters that occur in
     * ordinary prose would flag unrelated apps, and anything supporting Sui says
     * Shizuku too.
     */
    @Test
    fun bareSuiIsNotEnough() {
        assertFalse(item(summary = "Sui generis design tool").usesShizuku)
    }

    @Test
    fun emptyItemIsNotFlagged() {
        assertFalse(item().usesShizuku)
    }
}
