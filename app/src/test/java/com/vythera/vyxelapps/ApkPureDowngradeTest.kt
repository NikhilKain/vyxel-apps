package com.vythera.vyxelapps

import com.vythera.vyxelapps.updater.isOlderVersionName
import com.vythera.vyxelapps.updater.numericVersionParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mirror offers builds older than the installed one, and comparing version
 * *codes* alone does not catch every case: apps that ship a build per ABI put the
 * ABI in the high digits, so an older arm64 build outranks a newer armv7 one.
 * These pin the name comparison that backs the code check up.
 */
class ApkPureDowngradeTest {

    @Test
    fun `the Amazon Music downgrade is rejected`() {
        // The exact pair the scan offered on device.
        assertTrue(isOlderVersionName("25.20.0", "26.29.1"))
    }

    @Test
    fun `a genuine upgrade is not rejected`() {
        assertFalse(isOlderVersionName("12.20.5-prod.01", "12.19.1-release.0"))
        assertFalse(isOlderVersionName("4.3.260824", "4.3.260820"))
    }

    @Test
    fun `the same version is not older`() {
        assertFalse(isOlderVersionName("3.1.4", "3.1.4"))
    }

    @Test
    fun `a shorter name is compared as trailing zeroes`() {
        // 3.1 == 3.1.0, so neither is older; 3.1 IS older than 3.1.1.
        assertFalse(isOlderVersionName("3.1", "3.1.0"))
        assertTrue(isOlderVersionName("3.1", "3.1.1"))
        assertFalse(isOlderVersionName("3.1.1", "3.1"))
    }

    @Test
    fun `components are compared numerically and not as text`() {
        // The bug this guards: "9" sorts after "10" as a string.
        assertTrue(isOlderVersionName("1.9.0", "1.10.0"))
        assertFalse(isOlderVersionName("1.10.0", "1.9.0"))
    }

    @Test
    fun `an unparseable name declines to judge`() {
        // No digits on either side means the version-code result stands alone,
        // rather than a real update being hidden by a guess.
        assertFalse(isOlderVersionName("nightly", "26.29.1"))
        assertFalse(isOlderVersionName("25.20.0", "latest"))
        assertFalse(isOlderVersionName(null, "26.29.1"))
        assertFalse(isOlderVersionName("25.20.0", null))
    }

    @Test
    fun `numeric parts take the leading digits of each component`() {
        assertEquals(listOf(12L, 20L, 5L, 1L), numericVersionParts("12.20.5-prod.01"))
        assertEquals(listOf(443L, 0L, 0L, 48L, 82L), numericVersionParts("443.0.0.48.82"))
        assertNull(numericVersionParts("nightly"))
        assertNull(numericVersionParts("   "))
    }
}
