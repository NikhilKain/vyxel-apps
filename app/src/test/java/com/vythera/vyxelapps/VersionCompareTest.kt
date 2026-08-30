package com.vythera.vyxelapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `plain numeric versions compare by component`() {
        assertTrue(isVersionNewerThan("1.0.7", "1.0.6"))
        assertTrue(isVersionNewerThan("1.1.0", "1.0.99"))
        assertTrue(isVersionNewerThan("2.0", "1.9.9"))
        assertFalse(isVersionNewerThan("1.0.6", "1.0.7"))
        assertFalse(isVersionNewerThan("1.0.6", "1.0.6"))
    }

    @Test
    fun `missing trailing components are zero`() {
        assertEquals(0, compareVersions("1.0", "1.0.0"))
        assertTrue(isVersionNewerThan("1.0.1", "1.0"))
    }

    @Test
    fun `leading v and release prefixes are ignored`() {
        assertTrue(isVersionNewerThan("v1.0.7", "1.0.6"))
        assertTrue(isVersionNewerThan("release-2.1", "v2.0.9"))
        assertEquals(0, compareVersions("v1.0.6", "1.0.6"))
    }

    /**
     * The regression this file exists for: the old parser dropped any component
     * carrying a suffix, so "1.0.7-beta" became [1, 0] and compared as OLDER than
     * "1.0.6" — the update was never offered.
     */
    @Test
    fun `pre-release of a higher version still beats a lower stable`() {
        assertTrue(isVersionNewerThan("1.0.7-beta", "1.0.6"))
        assertTrue(isVersionNewerThan("2.0.0-rc1", "1.9.4"))
    }

    @Test
    fun `stable outranks its own pre-releases`() {
        assertTrue(isVersionNewerThan("1.0.7", "1.0.7-rc2"))
        assertFalse(isVersionNewerThan("1.0.7-rc2", "1.0.7"))
    }

    @Test
    fun `pre-release stages order alpha beta rc`() {
        assertTrue(isVersionNewerThan("1.0.0-beta", "1.0.0-alpha"))
        assertTrue(isVersionNewerThan("1.0.0-rc", "1.0.0-beta"))
        assertTrue(isVersionNewerThan("1.0.0-beta2", "1.0.0-beta1"))
    }

    @Test
    fun `numeric pre-release identifiers compare numerically not lexically`() {
        assertTrue(isVersionNewerThan("1.0.0-10", "1.0.0-9"))
    }

    @Test
    fun `non numeric tags fall back to text comparison`() {
        assertFalse(isVersionNewerThan("nightly", "nightly"))
        assertTrue(isVersionNewerThan("1.0.0", "nightly"))
    }
}
