package com.vythera.vyxelapps.expressive.install

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import com.vythera.vyxelapps.expressive.data.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
)

/** An update available for something already on the device. */
data class UpdateCandidate(
    val installed: InstalledApp,
    val available: AppItem,
)

/**
 * Reads the device's package list and matches it against the catalog.
 *
 * Only F-Droid-protocol sources are matched, because they're the only ones that
 * publish a real `versionCode` — comparing a git tag against an installed build
 * would produce false positives.
 */
class InstalledApps(private val context: Context) {

    suspend fun installed(): Map<String, InstalledApp> = withContext(Dispatchers.IO) {
        runCatching {
            context.packageManager
                .getInstalledPackages(0)
                .associate { info -> info.packageName to info.toInstalledApp() }
        }.getOrDefault(emptyMap())
    }

    suspend fun isInstalled(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            }.getOrDefault(false)
        }
    }

    /** Catalog entries that are newer than what's installed. */
    suspend fun findUpdates(catalog: List<AppItem>): List<UpdateCandidate> {
        val onDevice = installed()
        if (onDevice.isEmpty()) return emptyList()

        return catalog.asSequence()
            .filter { it.versionCode > 0 && it.downloadUrl != null }
            .mapNotNull { candidate ->
                val pkg = candidate.packageName ?: return@mapNotNull null
                val current = onDevice[pkg] ?: return@mapNotNull null
                if (current.isSystem) return@mapNotNull null
                if (candidate.versionCode > current.versionCode) {
                    UpdateCandidate(current, candidate)
                } else null
            }
            // The same package can appear in several repos; keep the highest version.
            .groupBy { it.installed.packageName }
            .map { (_, dupes) -> dupes.maxBy { it.available.versionCode } }
            .sortedBy { it.available.name.lowercase() }
    }

    private fun PackageInfo.toInstalledApp(): InstalledApp {
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
        val system = applicationInfo?.let {
            (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } ?: false
        return InstalledApp(
            packageName = packageName,
            versionName = versionName.orEmpty(),
            versionCode = code,
            isSystem = system,
        )
    }

    /** Launches an installed app, if it exposes a launcher activity. */
    fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
