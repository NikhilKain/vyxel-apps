package com.vythera.vyxelapps.updater

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.util.UUID

class AptoideScanRepository(
    private val context: Context,
    private val service: AptoideScanService
) {
    companion object {
        val UserAgent: String by lazy {
            val terminal = "${Build.MODEL.replace(";", " ")}(${Build.PRODUCT.replace(";", " ")});" +
                    "v${Build.VERSION.RELEASE.replace(";", " ")};${System.getProperty("os.arch")}"
            "aptoide-9.20.6.1;$terminal;0x0;id:${UUID.randomUUID()};;"
        }
    }

    private val query: String by lazy { computeFilters(context) }

    suspend fun updates(apps: List<ScannedApp>) = flow {
        val data = apps.map { it.toApksData() }
        val r    = service.findUpdates(AptoideListAppsUpdatesRequest(data, query))
        val updates = r.list
            // Same guard as APKPure: a ChromeOS or TV build shares the package name
            // but is not a phone update, and its inflated version beats every real
            // one when compared as text.
            .filterNot { isForeignVariantVersion(it.file.vername) }
            .filter { isVersionNewer(it.file.vername, apps.getVersion(it.packageName)) }
            .map    { it.toAppScanResult(apps.getApp(it.packageName)) }
        emit(updates)
    }.catch {
        emit(emptyList())
        Log.e("AptoideScanRepository", "Error looking for updates.", it)
    }

    private fun computeFilters(context: Context): String {
        val size  = Resources.getSystem().configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val sizes = listOf("notfound", "small", "normal", "large", "xlarge")
        val am    = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val gles  = am.deviceConfigurationInfo.glEsVersion
        val leanback = if (context.packageManager.hasSystemFeature("android.software.leanback")) "1" else "0"
        val dpi   = Resources.getSystem().displayMetrics.densityDpi.let { d ->
            when { d <= 120 -> 120; d <= 160 -> 160; d <= 213 -> 213
                   d <= 240 -> 240; d <= 320 -> 320; d <= 480 -> 480; else -> 640 }
        }
        val filters = "maxSdk=${Build.VERSION.SDK_INT}&maxScreen=${sizes[size]}" +
                      "&maxGles=$gles&myCPU=${Build.SUPPORTED_ABIS.joinToString(",")}" +
                      "&leanback=$leanback&myDensity=$dpi"
        return Base64.encodeToString(filters.toByteArray(), 0)
            .replace("=", "").replace("/", "*").replace("+", "_").replace("\n", "")
    }
}
