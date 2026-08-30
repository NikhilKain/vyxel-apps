package com.vythera.vyxelapps.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Integrity checks that run between "APK finished downloading" and "hand it to
 * the installer".
 *
 * Two things are checked, in order of importance:
 *
 *  1. **Signer identity.** If the package is already installed, the downloaded
 *     APK must be signed by the same certificate (or one in its rotation
 *     lineage). Android would reject a mismatched update anyway, but only after
 *     the user has tapped through the installer and with a generic error — and
 *     more importantly, a mismatch means the APK is *not* from the publisher
 *     who signed what's on the device (hijacked repo, redirected asset URL,
 *     look-alike release). Blocking here is the whole point of the store.
 *
 *  2. **Package identity.** The APK must declare the package name we expected.
 *     Stops a listing for app A shipping app B.
 *
 * The SHA-256 of the file itself is always computed so the UI can show it —
 * there is nothing to compare it against (GitHub publishes no checksums for
 * release assets), but it lets a user verify against a publisher's own
 * published hash.
 */
object ApkVerifier {

    enum class Verdict {
        /** Not installed yet — nothing to compare a signature against. */
        NEW_INSTALL,
        /** Installed, and the download is signed by the same key. */
        SIGNATURE_MATCH,
        /** Installed, but signed by a different key — refuse. */
        SIGNATURE_MISMATCH,
        /** APK declares a different package than the listing — refuse. */
        PACKAGE_MISMATCH,
        /** Not a parseable APK (truncated download, HTML error page, …). */
        UNREADABLE
    }

    data class Result(
        val verdict               : Verdict,
        val packageName           : String  = "",
        val versionName           : String  = "",
        val fileSha256            : String  = "",
        val signerSha256          : String  = "",
        val installedSignerSha256 : String? = null,
        val expectedPackage       : String? = null
    ) {
        val isSafeToInstall: Boolean
            get() = verdict == Verdict.NEW_INSTALL || verdict == Verdict.SIGNATURE_MATCH

        /** Colon-grouped fingerprint, truncated — full 32 groups is unreadable on a phone. */
        val shortSigner: String
            get() = signerSha256.chunked(2).take(8).joinToString(":").uppercase()

        val shortFileHash: String
            get() = fileSha256.take(16).uppercase()

        /** User-facing reason, empty when the APK passed. */
        fun message(): String = when (verdict) {
            Verdict.SIGNATURE_MISMATCH ->
                "Signature mismatch — this APK is signed by a different key than the version " +
                "already installed. It is not an update from the same publisher. Install refused."
            Verdict.PACKAGE_MISMATCH ->
                "Package mismatch — this APK installs \"$packageName\", but this listing is for " +
                "\"$expectedPackage\". Install refused."
            Verdict.UNREADABLE ->
                "The downloaded file could not be read as an APK. It may be corrupt or incomplete."
            else -> ""
        }
    }

    /**
     * @param expectedPackage package the listing claims, when known (recorded from a
     *        previous install, or the F-Droid/IzzyOnDroid package id). Null skips check 2.
     */
    fun verify(context: Context, apk: File, expectedPackage: String? = null): Result {
        val pm      = context.packageManager
        val archive = readArchive(pm, apk) ?: return Result(
            verdict     = Verdict.UNREADABLE,
            fileSha256  = runCatching { sha256(apk) }.getOrDefault(""),
            expectedPackage = expectedPackage
        )

        val pkg          = archive.packageName.orEmpty()
        val fileHash     = runCatching { sha256(apk) }.getOrDefault("")
        val apkSigners   = signerDigests(archive)
        val apkSigner    = apkSigners.firstOrNull().orEmpty()

        if (!expectedPackage.isNullOrEmpty() && pkg.isNotEmpty() && pkg != expectedPackage) {
            return Result(
                verdict         = Verdict.PACKAGE_MISMATCH,
                packageName     = pkg,
                versionName     = archive.versionName.orEmpty(),
                fileSha256      = fileHash,
                signerSha256    = apkSigner,
                expectedPackage = expectedPackage
            )
        }

        val installed = runCatching { pm.getPackageInfo(pkg, signatureFlag()) }.getOrNull()
            ?: return Result(
                verdict         = Verdict.NEW_INSTALL,
                packageName     = pkg,
                versionName     = archive.versionName.orEmpty(),
                fileSha256      = fileHash,
                signerSha256    = apkSigner,
                expectedPackage = expectedPackage
            )

        val installedSigners = signerDigests(installed)
        // Key rotation: Android accepts an update signed by any certificate in the
        // installed app's lineage, so an intersection — not equality — is the test.
        val matches = apkSigners.isNotEmpty() &&
            installedSigners.isNotEmpty() &&
            apkSigners.any { it in installedSigners }

        return Result(
            verdict               = if (matches) Verdict.SIGNATURE_MATCH else Verdict.SIGNATURE_MISMATCH,
            packageName           = pkg,
            versionName           = archive.versionName.orEmpty(),
            fileSha256            = fileHash,
            signerSha256          = apkSigner,
            installedSignerSha256 = installedSigners.firstOrNull(),
            expectedPackage       = expectedPackage
        )
    }

    /**
     * getPackageArchiveInfo() returns an ApplicationInfo whose sourceDir is unset,
     * which makes signature extraction fail on some OEM builds — point it at the
     * file before touching signingInfo.
     */
    private fun readArchive(pm: PackageManager, apk: File): PackageInfo? = runCatching {
        if (!apk.exists() || apk.length() == 0L) return null
        @Suppress("DEPRECATION")
        pm.getPackageArchiveInfo(apk.absolutePath, signatureFlag())?.also { info ->
            info.applicationInfo?.let {
                it.sourceDir       = apk.absolutePath
                it.publicSourceDir = apk.absolutePath
            }
        }
    }.getOrNull()

    /** Every certificate that can legitimately sign this package, SHA-256 hex. */
    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): List<String> = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo ?: return emptyList()
            val certs = if (si.hasMultipleSigners()) si.apkContentsSigners
                        else si.signingCertificateHistory ?: si.apkContentsSigners
            certs.orEmpty().map { it.toByteArray().sha256Hex() }
        } else {
            info.signatures.orEmpty().filterNotNull().map { it.toByteArray().sha256Hex() }
        }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun signatureFlag(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    /** Streaming digest — release APKs run to hundreds of MB. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256").digest(this).toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
