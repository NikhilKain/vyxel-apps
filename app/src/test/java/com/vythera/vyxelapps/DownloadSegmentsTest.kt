package com.vythera.vyxelapps

import com.vythera.vyxelapps.expressive.install.DownloadSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parallel downloads write straight into offsets of one file, so the ranges have to
 * tile the file exactly: no gaps, no overlaps, nothing past the end.
 *
 * A gap becomes a run of zero bytes in the middle of an APK — which does not fail the
 * download, it fails signature verification later with nothing pointing back here.
 */
class DownloadSegmentsTest {

    private val maxSegments = 4
    private val minSegment = 2L * 1024 * 1024

    private fun ranges(total: Long) =
        DownloadSegments.ranges(total, maxSegments, minSegment)

    /** The property that actually matters, checked across a spread of sizes. */
    @Test
    fun rangesTileTheFileExactly() {
        val sizes = listOf(
            1L, 999L, minSegment, minSegment + 1,
            6L * 1024 * 1024, 8L * 1024 * 1024,
            37_123_457L, 104_857_600L,
        )
        for (total in sizes) {
            val parts = ranges(total)
            assertEquals("first byte, total=$total", 0L, parts.first().first)
            assertEquals("last byte, total=$total", total - 1, parts.last().last)

            var expectedNext = 0L
            var covered = 0L
            for (part in parts) {
                assertEquals("contiguous, total=$total", expectedNext, part.first)
                assertTrue("non-empty, total=$total", part.last >= part.first)
                covered += part.last - part.first + 1
                expectedNext = part.last + 1
            }
            assertEquals("full coverage, total=$total", total, covered)
        }
    }

    @Test
    fun smallFileStaysOnOneConnection() {
        assertEquals(1, ranges(500_000L).size)
    }

    /** Segment count is capped however large the file gets. */
    @Test
    fun neverExceedsTheCap() {
        assertEquals(maxSegments, ranges(4L * 1024 * 1024 * 1024).size)
    }

    /** Just under two minimum segments is still a single connection. */
    @Test
    fun doesNotSplitBelowTwoMinimumSegments() {
        assertEquals(1, ranges(minSegment * 2 - 1).size)
    }

    @Test
    fun emptyFileProducesNoRanges() {
        assertTrue(ranges(0L).isEmpty())
    }
}
