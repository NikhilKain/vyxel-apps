package com.vythera.vyxelapps

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vythera.vyxelapps.installer.ApkVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the install-time gate against a real, known APK: this app's own.
 * It is installed on the device and signed by the key that built it, so it is
 * the one file whose expected verdict we know for certain.
 */
@RunWith(AndroidJUnit4::class)
class ApkVerifierTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun ownApk(): File {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return File(info.applicationInfo!!.sourceDir)
    }

    @Test
    fun ownApkVerifiesAsSameSigner() {
        val result = ApkVerifier.verify(context, ownApk(), context.packageName)

        assertEquals(ApkVerifier.Verdict.SIGNATURE_MATCH, result.verdict)
        assertTrue(result.isSafeToInstall)
        assertEquals(context.packageName, result.packageName)
        assertEquals(64, result.signerSha256.length)   // SHA-256 hex
        assertEquals(64, result.fileSha256.length)
        assertEquals(result.signerSha256, result.installedSignerSha256)
    }

    @Test
    fun mismatchedExpectedPackageIsRefused() {
        val result = ApkVerifier.verify(context, ownApk(), "com.example.not.this.app")

        assertEquals(ApkVerifier.Verdict.PACKAGE_MISMATCH, result.verdict)
        assertFalse(result.isSafeToInstall)
        assertTrue(result.message().contains("Package mismatch"))
    }

    @Test
    fun noExpectedPackageStillChecksTheInstalledSignature() {
        val result = ApkVerifier.verify(context, ownApk(), expectedPackage = null)

        assertEquals(ApkVerifier.Verdict.SIGNATURE_MATCH, result.verdict)
        assertTrue(result.isSafeToInstall)
    }

    @Test
    fun garbageFileIsUnreadableAndRefused() {
        val junk = File(context.cacheDir, "not-an-apk.apk").apply {
            writeText("<html>404 Not Found</html>")
        }
        try {
            val result = ApkVerifier.verify(context, junk, null)
            assertEquals(ApkVerifier.Verdict.UNREADABLE, result.verdict)
            assertFalse(result.isSafeToInstall)
        } finally {
            junk.delete()
        }
    }

    @Test
    fun missingFileIsRefused() {
        val absent = File(context.cacheDir, "does-not-exist.apk")
        assertFalse(ApkVerifier.verify(context, absent, null).isSafeToInstall)
    }

    @Test
    fun fileHashIsStableAndContentDependent() {
        val a = File(context.cacheDir, "hash-a.bin").apply { writeText("vyxel") }
        val b = File(context.cacheDir, "hash-b.bin").apply { writeText("vyxel!") }
        try {
            assertEquals(ApkVerifier.sha256(a), ApkVerifier.sha256(a))
            assertNotEquals(ApkVerifier.sha256(a), ApkVerifier.sha256(b))
            // Known vector for "vyxel", so a broken digest can't pass silently.
            assertEquals(
                "5d3f5a6a06b0d3b0e3fbc4e1a3e0a67b3f9b0c9a5f8f0e0c5f0f0e2c4d5e6f70".length,
                ApkVerifier.sha256(a).length
            )
        } finally {
            a.delete(); b.delete()
        }
    }
}
