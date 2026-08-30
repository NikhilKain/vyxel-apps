package com.vythera.vyxelapps.updater

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

class ApkPureScanRepository(
    private val service: ApkPureScanService,
    gson: Gson
) {
    private val header = gson.toJson(ApkPureDeviceHeader())

    suspend fun updates(apps: List<ScannedApp>) = flow {
        val info = apps.map { ApkPureAppInfoForUpdate(it.packageName, it.versionCode) }
        val r    = service.getAppUpdate(header, ApkPureGetAppUpdate(info))
        val updates = r.app_update_response.orEmpty()
            .filter { filterSignature(it.sign, apps.getSignature(it.package_name.orEmpty())) }
            .mapNotNull { it.toAppScanResultOrNull(apps.getApp(it.package_name.orEmpty())) }
        emit(updates)
    }.catch {
        Log.e("ApkPureScanRepository", it.message, it)
        emit(emptyList())
    }

    /**
     * Only offer a build signed by the same key as the one already installed.
     *
     * This is the whole reason a general mirror is safe to take updates from. Package
     * names are not owned by anyone — a repackaged build can claim `com.instagram.
     * android` — but Android will refuse to install over an app whose signing
     * certificate differs, so an update that fails this check could never install
     * anyway and is a red flag besides. An entry that publishes no signature at all
     * is passed through, because the installer's own check still stands behind it.
     */
    private fun filterSignature(signatures: List<String>?, signature: String) = when {
        signatures.isNullOrEmpty()     -> true
        signatures.contains(signature) -> true
        else                           -> false
    }
}
