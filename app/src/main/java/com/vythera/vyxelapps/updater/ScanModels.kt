package com.vythera.vyxelapps.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.security.MessageDigest

// ── Sources ───────────────────────────────────────────────────────────────────

data class UpdaterSource(val name: String)

val FdroidUpdaterSource  = UpdaterSource("F-Droid")
val IzzyUpdaterSource    = UpdaterSource("Izzy")
val GitHubUpdaterSource  = UpdaterSource("GitHub")
val GitLabUpdaterSource  = UpdaterSource("GitLab")
val ApkPureUpdaterSource = UpdaterSource("APKPure")
val AptoideUpdaterSource = UpdaterSource("Aptoide")

// ── Download link ─────────────────────────────────────────────────────────────

sealed class ScanLink {
    data object Empty : ScanLink()
    data class Url(val link: String, val size: Long = 0L) : ScanLink()
    data class Xapk(val link: String) : ScanLink()
}

// ── Core models ───────────────────────────────────────────────────────────────

data class AppScanResult(
    val appName: String,
    val packageName: String,
    val currentVersion: String,
    val newVersion: String,
    val source: UpdaterSource,
    val iconUrl: String = "",
    val link: ScanLink = ScanLink.Empty,
    val whatsNew: String = "",
    val hasUpdate: Boolean = true,
    val repoFullName: String = ""
)

data class ScannedApp(
    val name: String,
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val signature: String = "",
    val signatureSha256: String = ""
)

// ── List helpers ──────────────────────────────────────────────────────────────

fun List<ScannedApp>.getApp(pkg: String)         = find { it.packageName == pkg }
fun List<ScannedApp>.getVersionCode(pkg: String) = getApp(pkg)?.versionCode ?: 0L
fun List<ScannedApp>.getVersion(pkg: String)     = getApp(pkg)?.version ?: ""
fun List<ScannedApp>.getSignature(pkg: String)   = getApp(pkg)?.signature.orEmpty()

// ── Flow combine (ported from APKUpdater Extensions.kt) ───────────────────────

inline fun <reified T> List<Flow<T>>.combineFlows(crossinline block: suspend (Array<T>) -> Unit) =
    combine(this) { block(it) }

// ── Hash utilities ────────────────────────────────────────────────────────────

fun ByteArray.toSha1(): String = MessageDigest.getInstance("SHA-1")
    .digest(this).joinToString("") { "%02x".format(it) }

fun ByteArray.toSha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this).joinToString("") { "%02x".format(it) }

fun String.toSha1Aptoide(): String = chunked(2).joinToString(":") { it.uppercase() }

@Suppress("DEPRECATION")
fun PackageInfo.getSignatureBytes(): ByteArray = runCatching {
    if (Build.VERSION.SDK_INT >= 28)
        signingInfo!!.apkContentsSigners[0].toByteArray()
    else
        signatures!![0].toByteArray()
}.getOrDefault(ByteArray(0))

fun PackageInfo.scanSignatureSha1()   = getSignatureBytes().toSha1()
fun PackageInfo.scanSignatureSha256() = getSignatureBytes().toSha256()

// ── Version tag filter ────────────────────────────────────────────────────────

fun filterVersionTag(version: String) = version.replace(Regex("^\\D*"), "")

// ── Simple semantic version comparison ───────────────────────────────────────

fun isVersionNewer(latest: String, current: String): Boolean =
    com.vythera.vyxelapps.isVersionNewerThan(latest, current)

// ── PackageInfo → ScannedApp ──────────────────────────────────────────────────

@Suppress("DEPRECATION")
fun PackageInfo.toScannedApp(context: Context): ScannedApp {
    val label = applicationInfo?.loadLabel(context.packageManager)?.toString() ?: packageName
    val code  = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
    return ScannedApp(
        name            = label,
        packageName     = packageName,
        version         = versionName.orEmpty(),
        versionCode     = code,
        signature       = scanSignatureSha1(),
        signatureSha256 = scanSignatureSha256()
    )
}
