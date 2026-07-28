package com.example.cashbookbd.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * Transport for the Real Estate master-data forms. The real-estate endpoints
 * share one foundData/notFound envelope but nest their payloads differently
 * (DDL rows and edit records both at `data.data`), so like [HrmApiService] this
 * takes paths via [Url] and returns the raw [JsonElement] tree for
 * [com.example.cashbookbd.data.repository.RealEstateCrudRepository] to parse
 * defensively.
 */
interface RealEstateApiService {

    @GET
    suspend fun get(
        @Url url: String,
        @QueryMap params: Map<String, String>,
    ): Response<JsonElement>

    @POST
    suspend fun post(
        @Url url: String,
        @Body body: JsonObject,
    ): Response<JsonElement>
}
