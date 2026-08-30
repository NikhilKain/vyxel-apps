package com.vythera.vyxelapps.updater

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class GitLabScanRepository(private val service: GitLabScanService) {

    suspend fun updates(apps: List<ScannedApp>) = flow {
        val checks = mutableListOf<Flow<List<AppScanResult>>>()
        GitLabApps.forEach { app ->
            apps.find { it.packageName == app.packageName }?.let { installed ->
                checks.add(checkApp(apps, app.user, app.repo, app.packageName, installed.version))
            }
        }
        if (checks.isEmpty()) emit(emptyList())
        else checks.combineFlows { all -> emit(all.flatMap { it }) }.collect()
    }.catch {
        emit(emptyList())
        Log.e("GitLabScanRepository", "Error fetching releases.", it)
    }

    private fun checkApp(
        apps: List<ScannedApp>,
        user: String,
        repo: String,
        packageName: String,
        currentVersion: String
    ) = flow {
        val releases = service.getReleases(user, repo).filter {
            isVersionNewer(filterVersionTag(it.tag_name), filterVersionTag(currentVersion))
        }
        if (releases.isNotEmpty()) {
            val release = releases[0]
            val app     = apps.getApp(packageName)
            emit(listOf(AppScanResult(
                appName        = repo,
                packageName    = packageName,
                currentVersion = app?.version ?: currentVersion,
                newVersion     = release.tag_name,
                source         = GitLabUpdaterSource,
                iconUrl        = release.author.avatar_url,
                link           = ScanLink.Url(getApkUrl(release)),
                whatsNew       = release.description
            )))
        } else {
            emit(emptyList())
        }
    }.catch {
        emit(emptyList())
        Log.e("GitLabScanRepository", "Error fetching releases for $packageName.", it)
    }

    private fun getApkUrl(release: GitLabRelease): String {
        release.assets.sources.find { it.url.endsWith(".apk", true) }?.let { return it.url }
        release.assets.links.find  { it.url.endsWith(".apk", true) }?.let { return it.url }
        return ""
    }
}
