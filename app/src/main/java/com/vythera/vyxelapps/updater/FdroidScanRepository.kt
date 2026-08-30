package com.vythera.vyxelapps.updater

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.io.InputStream
import java.util.jar.JarInputStream

class FdroidScanRepository(
    private val service: FdroidScanService,
    private val url: String,
    private val source: UpdaterSource
) {
    private val arch = Build.SUPPORTED_ABIS.toSet()
    private val api  = Build.VERSION.SDK_INT

    suspend fun updates(apps: List<ScannedApp>) = flow {
        val data = service.getJar("${url}index-v1.jar").use { jarToJson(it.byteStream()) }
        val pkgNames = apps.map { it.packageName }
        val results  = data.apps
            .asSequence()
            .filter { pkgNames.contains(it.packageName) }
            .filter { filterSignature(apps.getApp(it.packageName)!!, it) }
            .mapNotNull { app ->
                val pkgs = data.packages[app.packageName]
                if (pkgs.isNullOrEmpty()) null
                else FdroidUpdate(pkgs[0], app)
            }
            .filter { it.apk.minSdkVersion <= api }
            .filter { filterArch(it) }
            .map { update ->
                val hasUpdate = update.apk.versionCode > apps.getVersionCode(update.app.packageName)
                update.toAppScanResult(apps.getApp(update.app.packageName), source, url, hasUpdate)
            }
            .toList()
        emit(results)
    }.catch {
        emit(emptyList())
        Log.e("FdroidScanRepository", "Error looking for updates ($url).", it)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun filterSignature(installed: ScannedApp, update: FdroidApp) = true

    private fun filterArch(update: FdroidUpdate) = when {
        update.apk.nativecode.isEmpty()                     -> true
        update.apk.nativecode.intersect(arch).isNotEmpty() -> true
        else                                                -> false
    }

    private fun jarToJson(stream: InputStream): FdroidData {
        val jar   = JarInputStream(stream)
        var entry = jar.nextJarEntry
        while (entry != null) {
            if (entry.name == "index-v1.json") {
                return Gson().fromJson(jar.reader(), FdroidData::class.java)
            }
            entry = jar.nextJarEntry
        }
        return FdroidData()
    }
}
