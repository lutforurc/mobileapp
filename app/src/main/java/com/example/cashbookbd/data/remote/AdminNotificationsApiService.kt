package com.example.cashbookbd.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PUT
import retrofit2.http.Url

/**
 * The two verbs the admin-extras endpoints need that none of the @Url-based
 * services offer: a DELETE (admin notifications) and a PUT whose typed JSON
 * body can carry explicit nulls (permission rename). Like [ReportApiService],
 * the path arrives via [Url] (relative to `BuildConfig.BASE_URL`) and the raw
 * [JsonElement] tree is returned for the repository to parse defensively.
 */
interface AdminNotificationsApiService {

    @DELETE
    suspend fun delete(@Url url: String): Response<JsonElement>

    @PUT
    suspend fun put(
        @Url url: String,
        @Body body: JsonObject,
    ): Response<JsonElement>
}
