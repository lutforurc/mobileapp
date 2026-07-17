package com.example.cashbookbd.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Transport for the transaction (voucher) entry forms. Bodies vary per form — a
 * bare JSON array (cash vouchers) or an object (bank/journal/loan) — so this
 * service exposes both, plus a GET for the bank-account dropdown. Paths are
 * relative to `BuildConfig.BASE_URL`.
 */
interface TransactionApiService {

    @POST
    suspend fun postObject(
        @Url url: String,
        @Body body: JsonObject,
    ): Response<JsonElement>

    @POST
    suspend fun postArray(
        @Url url: String,
        @Body body: JsonArray,
    ): Response<JsonElement>

    @GET
    suspend fun get(@Url url: String): Response<JsonElement>
}
