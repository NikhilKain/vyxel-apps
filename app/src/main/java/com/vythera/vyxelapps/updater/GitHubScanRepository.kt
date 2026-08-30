package com.vythera.vyxelapps.updater

import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

class GitHubScanRepository(private val service: GitHubScanService) {

    suspend fun updates(apps: List<ScannedApp>, extraApps: List<GHApp> = emptyList()) = flow {
        val checks = mutableListOf<Flow<List<AppScanResult>>>()
        (GitHubApps + extraApps).forEach { app ->
            apps.find { it.packageName == app.packageName }?.let { installed ->
                checks.add(
                    checkApp(apps, app.user, app.repo, app.packageName, installed.version, app.extra)
                        .onStart { emit(emptyList()) }.catch { emit(emptyList()) }
                )
            }
        }
        if (checks.isEmpty()) {
            emit(emptyList())
        } else {
            checks.combineFlows { all -> emit(all.flatMap { it }) }.collect()
        }
    }.catch {
        emit(emptyList())
        Log.e("GitHubScanRepository", "Error fetching releases.", it)
    }

    private fun checkApp(
        apps: List<ScannedApp>,
        user: String,
        repo: String,
        packageName: String,
        currentVersion: String,
        extra: Regex?
    ) = flow {
        val releases = service.getReleases(user, repo)
            .filter { !it.prerelease }
            .filter { findApkAsset(it.assets).isNotEmpty() }

        // Pick highest version (not just most-recently-published — patches for old versions
        // can be published after newer major releases and would otherwise appear first)
        val latestRelease = releases.maxWithOrNull(Comparator { a, b ->
            val av = filterVersionTag(a.tag_name)
            val bv = filterVersionTag(b.tag_name)
            when {
                isVersionNewer(av, bv) ->  1
                isVersionNewer(bv, av) -> -1
                else                  ->  0
            }
        }) ?: run { emit(emptyList()); return@flow }
        val latestVer = filterVersionTag(latestRelease.tag_name)
        val currVer   = filterVersionTag(currentVersion)
        val hasUpdate = isVersionNewer(latestVer, currVer)
        val app       = apps.getApp(packageName)
        val asset     = if (hasUpdate) findApkAssetArch(latestRelease.assets, extra) else GHReleaseAsset(0L, "")

        emit(listOf(AppScanResult(
            appName        = repo,
            packageName    = packageName,
            currentVersion = app?.version ?: currentVersion,
            newVersion     = latestRelease.tag_name,
            source         = GitHubUpdaterSource,
            iconUrl        = latestRelease.author.avatar_url,
            link           = if (hasUpdate) ScanLink.Url(asset.browser_download_url, asset.size) else ScanLink.Empty,
            whatsNew       = if (hasUpdate) latestRelease.body else "",
            hasUpdate      = hasUpdate,
            repoFullName   = "$user/$repo"
        )))
    }.catch {
        emit(emptyList())
        Log.e("GitHubScanRepository", "Error fetching releases for $packageName.", it)
    }

    private fun findApkAsset(assets: List<GHReleaseAsset>) = assets
        .filter { it.browser_download_url.endsWith(".apk", true) }
        .maxByOrNull { it.size }
        ?.browser_download_url
        .orEmpty()

    private fun findApkAssetArch(assets: List<GHReleaseAsset>, extra: Regex?): GHReleaseAsset {
        val apks = assets
            .filter { it.browser_download_url.endsWith(".apk", true) }
            .filter { extra == null || it.browser_download_url.matches(extra) }

        if (apks.isEmpty()) return GHReleaseAsset(0L, "")
        if (apks.size == 1) return apks.first()

        Build.SUPPORTED_ABIS.forEach { arch ->
            apks.forEach { apk ->
                if (apk.browser_download_url.contains(arch, true)) return apk
            }
        }
        if (Build.SUPPORTED_ABIS.contains("arm64-v8a"))
            apks.find { it.browser_download_url.contains("arm64", true) }?.let { return it }
        if (Build.SUPPORTED_ABIS.contains("x86_64"))
            apks.find { it.browser_download_url.contains("x64", true) }?.let { return it }
        if (Build.SUPPORTED_ABIS.contains("armeabi-v7a"))
            apks.find { it.browser_download_url.contains("arm", true) }?.let { return it }

        return apks.maxByOrNull { it.size } ?: GHReleaseAsset(0L, "")
    }
}
