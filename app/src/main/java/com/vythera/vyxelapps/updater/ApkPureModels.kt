package com.vythera.vyxelapps.updater

import android.content.res.Resources
import android.os.Build
import kotlin.random.Random

data class ApkPureAppInfoForUpdate(
    val package_name: String,
    val version_code: Long,
    val is_system: Boolean = false,
    val version_id: String = "",
    val cached_size: Int = -1
)

data class ApkPureDeviceInfo(
    val abis: List<String> = Build.SUPPORTED_ABIS.toList(),
    val android_id: String = Random.nextLong().toString(16),
    val os_ver: String = Build.VERSION.SDK_INT.toString(),
    val os_ver_name: String = Build.VERSION.RELEASE,
    val platform: Int = 1,
    val screen_height: Int = Resources.getSystem().displayMetrics.heightPixels,
    val screen_width: Int = Resources.getSystem().displayMetrics.widthPixels
)

data class ApkPureDeviceHeader(val device_info: ApkPureDeviceInfo = ApkPureDeviceInfo())

data class ApkPureGetAppUpdate(
    val app_info_for_update: List<ApkPureAppInfoForUpdate> = emptyList(),
    val android_id: String = Random.nextLong().toString(16),
    val application_id: String = "com.apkpure.aegon",
    val cached_size: Long = -1
)

// Nullable throughout, for the Gson/Unsafe reason spelled out on the response below.
data class ApkPureUpdateResponseAsset(
    val type: String? = null,
    val url: String? = null,
)

data class ApkPureUpdateResponseIconData(
    val height: String? = null,
    val width: String? = null,
    val url: String? = null,
)

data class ApkPureUpdateResponseIcon(
    val original: ApkPureUpdateResponseIconData? = null,
    val thumbnail: ApkPureUpdateResponseIconData? = null,
)

/**
 * Every field here is nullable on purpose.
 *
 * Gson allocates through `Unsafe` and never runs the Kotlin constructor, so a key the
 * response omits — or sends as an explicit `null`, which this API does for `icon` on
 * plenty of entries — lands as null inside a type the compiler swears is non-null.
 * The first dereference then dies with the `Object.getClass()` NPE that R8 lowers
 * `checkNotNull` into, and takes the whole scan with it.
 */
data class ApkPureUpdateResponse(
    val package_name: String? = null,
    val version_code: Long = 0L,
    val version_name: String? = null,
    val sign: List<String>? = null,
    val whatsnew: String? = null,
    val description_short: String? = null,
    val label: String? = null,
    val asset: ApkPureUpdateResponseAsset? = null,
    val icon: ApkPureUpdateResponseIcon? = null
)

/**
 * Null when the entry carries no usable download.
 *
 * An update with no asset is not an update the user can act on, and a row whose
 * button does nothing is worse than no row.
 */
fun ApkPureUpdateResponse.toAppScanResultOrNull(current: ScannedApp?): AppScanResult? {
    val pkg = package_name?.takeIf { it.isNotBlank() } ?: return null
    val url = asset?.url?.takeIf { it.isNotBlank() }?.replace("http://", "https://")
        ?: return null

    // Desktop and console variants are not builds for this device.
    //
    // The mirrors carry ChromeOS and Android-TV packages under the same package name,
    // with version numbers deliberately set absurdly high so their own clients rank
    // them last. Compared as text, `2000000000.0.0-CrOS` beats every real version, so
    // an unfiltered scan offered a Chromebook build of Pinterest as a phone update.
    if (isForeignVariant(version_name)) return null

    // XAPK is a zip of split APKs, not an APK. Flagging it here is what lets the
    // installer open a multi-APK session instead of handing a zip to the package
    // manager and failing with a parse error.
    val isBundle = asset.type.equals("XAPK", ignoreCase = true) || url.contains("/XAPK")

    return AppScanResult(
        appName        = label?.takeIf { it.isNotBlank() } ?: pkg,
        packageName    = pkg,
        currentVersion = current?.version ?: "?",
        newVersion     = version_name.orEmpty(),
        source         = ApkPureUpdaterSource,
        iconUrl        = icon?.thumbnail?.url.orEmpty(),
        link           = if (isBundle) ScanLink.Xapk(url) else ScanLink.Url(url),
        whatsNew       = (if (current == null) description_short else whatsnew).orEmpty(),
        // Compared on versionCode, not on the version *string*.
        //
        // `hasUpdate` defaults to true, and this source did not set it — so every
        // entry the mirror returned was presented as an upgrade, including builds
        // older than the installed one. Amazon Music was offered a 26.28.2 → 25.20.0
        // "update", which would have been refused by the installer at best.
        // Version codes are integers and monotonic; version names are marketing.
        hasUpdate      = version_code > (current?.versionCode ?: 0L) &&
            !isOlderVersionName(version_name, current?.version),
    )
}

/**
 * True when [candidate] is plainly an older release than [installed] by version name.
 *
 * The version *code* comparison above is the right primary test, but it is not
 * sufficient on its own: apps that publish a build per ABI encode the ABI in the
 * high digits of the code, so an older arm64 build carries a larger number than a
 * newer armv7 one. Amazon Music does exactly this, and a code-only check still
 * offered `26.29.1 → 25.20.0`. Comparing the marketing name numerically, component
 * by component, catches the case the code cannot.
 *
 * Deliberately conservative: unparseable on either side means "don't know", which
 * leaves the version-code result standing rather than hiding a real update.
 */
internal fun isOlderVersionName(candidate: String?, installed: String?): Boolean {
    val a = numericVersionParts(candidate) ?: return false
    val b = numericVersionParts(installed) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0L }
        val y = b.getOrElse(i) { 0L }
        if (x != y) return x < y
    }
    return false
}

/**
 * The leading numeric run of each dot/dash separated component.
 *
 * "12.20.5-prod.01" becomes [12, 20, 5, 1]; a name with no digits at all returns
 * null so the caller can decline to judge.
 */
internal fun numericVersionParts(version: String?): List<Long>? {
    val text = version?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parts = text.split('.', '-', '_', '+', ' ')
        .map { segment -> segment.takeWhile { it.isDigit() } }
        .filter { it.isNotEmpty() }
        .mapNotNull { it.toLongOrNull() }
    return parts.takeIf { it.isNotEmpty() }
}

/**
 * True for builds targeting something other than this phone.
 *
 * These share a package name with the real app but are not interchangeable with it,
 * and their versions are deliberately inflated so the mirror's own client sorts them
 * away. Matching on the suffix is crude, but it is the only marker the payload gives.
 */
private fun isForeignVariant(versionName: String?): Boolean {
    val name = versionName.orEmpty()
    return name.contains("CrOS", ignoreCase = true) ||
        name.contains("-tv", ignoreCase = true) ||
        name.startsWith("2000000000")
}

data class ApkPureGetAppUpdateResponse(
    val retcode: Int = 0,
    val app_update_response: List<ApkPureUpdateResponse>? = null,
)
