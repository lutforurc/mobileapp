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
 * Transport for the HRM screens. The hrms endpoints share no fixed response
 * schema (foundData single/double/triple nesting, plus a few raw-json salary
 * routes), so like the report service this takes paths via [Url] and returns the
 * raw [JsonElement] tree for [com.example.cashbookbd.data.repository.HrmRepository]
 * to parse defensively. POST bodies carry mixed types (nested employee arrays),
 * hence [JsonObject] rather than a string map.
 */
interface HrmApiService {

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
