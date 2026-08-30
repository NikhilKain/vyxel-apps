package com.vythera.vyxelapps

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

/**
 * Hosts both UIs.
 *
 * Two image loaders are configured on purpose: the Classic UI is written against
 * Coil 2 and the Expressive UI against Coil 3. They are separate artifacts in
 * separate packages, so each gets its own singleton and neither has to be rewritten.
 */
class VyxelApp : Application(), ImageLoaderFactory, coil3.SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        com.vythera.vyxelapps.expressive.core.net.Net.init(this)
        createNotificationChannel()
        scheduleUpdateChecks()
    }

    /** Coil 3 loader used by the Expressive UI; shares the app's OkHttp client. */
    override fun newImageLoader(context: coil3.PlatformContext): coil3.ImageLoader =
        coil3.ImageLoader.Builder(context)
            .components {
                add(
                    coil3.network.okhttp.OkHttpNetworkFetcherFactory(
                        callFactory = { com.vythera.vyxelapps.expressive.core.net.Net.client() }
                    )
                )
            }
            .memoryCache {
                coil3.memory.MemoryCache.Builder().maxSizePercent(context, 0.25).build()
            }
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("img3").toOkioPath())
                    .maxSizeBytes(192L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    /** Coil 2 loader used by the Classic UI. */
    override fun newImageLoader() = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.30).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("img"))
                .maxSizeBytes(150L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "vyxel_updates",
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications for available app updates" }
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    private fun scheduleUpdateChecks() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(8, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "vyxel_update_checker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}