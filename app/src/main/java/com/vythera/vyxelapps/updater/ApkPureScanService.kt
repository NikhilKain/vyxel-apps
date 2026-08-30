package com.vythera.vyxelapps.updater

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApkPureScanService {
    @Headers(
        "content-type: application/json",
        "ual-access-businessid: projecta"
    )
    @POST("v3/get_app_update")
    suspend fun getAppUpdate(
        @Header("ual-access-projecta") header: String,
        @Body request: ApkPureGetAppUpdate
    ): ApkPureGetAppUpdateResponse
}
