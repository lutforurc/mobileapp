package com.example.cashbookbd.data.remote

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * Generic transport for the many report endpoints. Because reports differ only
 * in URL, method and parameters — not in a fixed response schema — this service
 * takes the path via [Url] and returns the raw [JsonElement] tree for the
 * repository to parse defensively.
 *
 * Paths are relative to `BuildConfig.BASE_URL` (see [ReportEndpoints]).
 */
interface ReportApiService {

    @GET
    suspend fun get(
        @Url url: String,
        @QueryMap params: Map<String, String>,
    ): Response<JsonElement>

    @POST
    suspend fun post(
        @Url url: String,
        @Body body: Map<String, String>,
    ): Response<JsonElement>
}
