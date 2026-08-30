package com.vythera.vyxelapps.updater

data class FdroidLocalized(
    val name: String = "",
    val summary: String = "",
    val whatsNew: String = ""
)

data class FdroidApp(
    val packageName: String = "",
    val name: String = "",
    val icon: String = "",
    val suggestedVersionCode: String = "",
    val suggestedVersionName: String = "",
    val added: Long = 0L,
    val lastUpdated: Long = 0L,
    val allowedAPKSigningKeys: List<String> = emptyList(),
    val localized: Map<String, FdroidLocalized> = emptyMap()
)

data class FdroidPackage(
    val apkName: String = "",
    val versionCode: Long = 0,
    val versionName: String = "",
    val minSdkVersion: Int = 0,
    val nativecode: List<String> = emptyList(),
    val hash: String = "",
    val hashType: String = ""
)

data class FdroidData(
    val packages: Map<String, List<FdroidPackage>> = emptyMap(),
    val apps: List<FdroidApp> = emptyList()
)

data class FdroidUpdate(
    val apk: FdroidPackage,
    val app: FdroidApp
)

fun FdroidUpdate.toAppScanResult(current: ScannedApp?, source: UpdaterSource, baseUrl: String, hasUpdate: Boolean = true) = AppScanResult(
    appName        = app.localized["en-US"]?.name ?: app.name,
    packageName    = app.packageName,
    currentVersion = current?.version ?: "?",
    newVersion     = apk.versionName,
    source         = source,
    iconUrl        = if (app.icon.isEmpty()) "" else "${baseUrl}icons-640/${app.icon}",
    link           = if (hasUpdate) ScanLink.Url("$baseUrl${apk.apkName}") else ScanLink.Empty,
    whatsNew       = if (hasUpdate) app.localized["en-US"]?.whatsNew.orEmpty() else "",
    hasUpdate      = hasUpdate
)
