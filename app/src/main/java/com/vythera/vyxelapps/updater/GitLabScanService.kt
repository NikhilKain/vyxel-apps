package com.vythera.vyxelapps.updater

import retrofit2.http.GET
import retrofit2.http.Path

interface GitLabScanService {
    @GET("api/v4/projects/{user}%2F{repo}/releases")
    suspend fun getReleases(
        @Path("user") user: String,
        @Path("repo") repo: String
    ): List<GitLabRelease>
}
