package com.vythera.vyxelapps.updater

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

class AppScanRepository(private val context: Context) {

    /**
     * Stores whose builds are usually signed by the store rather than the upstream
     * developer.
     *
     * These are no longer dropped from the scan. Excluding them meant that on a
     * device where most apps came from Play, the updater surfaced almost nothing and
     * looked broken. They are reported like any other app; where the signing key
     * differs the install itself will refuse the update, which is a far more useful
     * outcome than silently pretending the app isn't installed.
     */
    private val storeInstallers = setOf(
        "com.android.vending",          // Google Play Store
        "com.google.android.feedback",  // Play Store variant
        "com.amazon.venezia",           // Amazon Appstore
        "com.apkpure.aegon",            // APKPure
        "com.apkpure.app"               // APKPure variant
    )

    suspend fun getApps() = flow {
        val pm = context.packageManager
        val apps = pm
            .getInstalledPackages(PackageManager.MATCH_ALL + signatureFlag())
            .asSequence()
            .filter { (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0 }
            .filter { it.applicationInfo?.enabled != false }
            .filter { pkg ->
                // Never scan ourselves — Vyxel updates through its own release flow.
                pkg.packageName != context.packageName
            }
            .map { it.toScannedApp(context) }
            .sortedBy { it.name }
            .toList()
        emit(Result.success(apps))
    }.catch {
        Log.e("AppScanRepository", "Error getting apps.", it)
        emit(Result.failure(it))
    }

    @Suppress("DEPRECATION")
    private fun signatureFlag(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
}
