package com.vythera.vyxelapps.updater

import com.google.gson.annotations.SerializedName

data class AptoideApksData(
    @SerializedName("package") val packageName: String = "",
    val vercode: String = "0",
    val signature: String?,
    val isEnabled: Boolean = true
)

data class AptoideFile(
    val vername: String = "",
    val vercode: String = "0",
    val path: String = ""
)

data class AptoideStore(val name: String = "")

data class AptoideApp(
    val name: String = "",
    @SerializedName("package") val packageName: String = "",
    val icon: String? = null,
    val file: AptoideFile,
    val store: AptoideStore = AptoideStore()
)

fun AptoideApp.toAppScanResult(current: ScannedApp?) = AppScanResult(
    appName        = name,
    packageName    = packageName,
    currentVersion = current?.version ?: "?",
    newVersion     = file.vername,
    source         = AptoideUpdaterSource,
    iconUrl        = icon ?: "",
    link           = ScanLink.Url(file.path),
    whatsNew       = ""
)

data class AptoideDataList(val list: List<AptoideApp> = emptyList())

data class AptoideListAppsUpdatesRequest(
    val apks_data: List<AptoideApksData>,
    val q: String,
    val not_apk_tags: String = "alpha,beta",
    val store_ids: List<Long>? = listOf(15L, 711454L)
)

data class AptoideListAppUpdatesResponse(
    val list: List<AptoideApp> = emptyList(),
    val info: Any? = null,
    val errors: Any? = null
)

data class AptoideListSearchAppsRequest(
    val query: String = "",
    val limit: String = "10",
    val q: String,
    val not_apk_tags: String = "alpha,beta",
    val store_ids: List<Long>? = listOf(15L, 711454L)
)

data class AptoideListSearchAppsResponse(
    val datalist: AptoideDataList,
    val info: Any? = null,
    val errors: Any? = null
)

// Extension to convert ScannedApp to Aptoide's request format
fun ScannedApp.toApksData() = AptoideApksData(
    packageName = packageName,
    vercode     = versionCode.toString(),
    signature   = signature.toSha1Aptoide(),
    isEnabled   = true
)

/**
 * True for a build targeting something other than a phone.
 *
 * Shared with the APKPure path in spirit but kept here as its own function so neither
 * source has to import the other. See the note on `isForeignVariant` in ApkPureModels.
 */
fun isForeignVariantVersion(versionName: String?): Boolean {
    val name = versionName.orEmpty()
    return name.contains("CrOS", ignoreCase = true) ||
        name.contains("-tv", ignoreCase = true) ||
        name.startsWith("2000000000")
}
