package com.vythera.vyxelapps.updater

import retrofit2.http.GET
import retrofit2.http.Path

data class GHAuthor(val avatar_url: String = "")

data class GHReleaseAsset(
    val size: Long,
    val browser_download_url: String
)

data class GHRelease(
    val name: String = "",
    val prerelease: Boolean = false,
    val assets: List<GHReleaseAsset> = emptyList(),
    val tag_name: String,
    val author: GHAuthor = GHAuthor(),
    val body: String = ""
)

data class GHApp(
    val packageName: String,
    val user: String,
    val repo: String,
    val extra: Regex? = null
)

interface GitHubScanService {
    @GET("/repos/{user}/{repo}/releases")
    suspend fun getReleases(
        @Path("user") user: String,
        @Path("repo") repo: String
    ): List<GHRelease>
}
