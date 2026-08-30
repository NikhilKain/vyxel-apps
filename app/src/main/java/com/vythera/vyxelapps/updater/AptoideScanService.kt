package com.vythera.vyxelapps.updater

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface AptoideScanService {
    @POST("listAppsUpdates")
    suspend fun findUpdates(
        @Body request: AptoideListAppsUpdatesRequest,
        @Header("X-Bypass-Cache") bypassCache: Boolean = true,
        @Query("aab") showAabs: Boolean = false
    ): AptoideListAppUpdatesResponse
}
