package com.example.cashbookbd.data.remote

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
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

    /**
     * A GET whose answer is a page, not JSON — the delivery challan arrives as
     * ready-to-render HTML (`sales/challan/{id}`), served behind the bearer
     * token precisely so a token-authenticated client can open it.
     */
    @GET
    suspend fun getRaw(@Url url: String): Response<okhttp3.ResponseBody>

    @POST
    suspend fun post(
        @Url url: String,
        @Body body: Map<String, String>,
    ): Response<JsonElement>

    /**
     * A PUT whose body may hold non-string values (e.g. an array of ids), for
     * endpoints like role permission assignment. [JvmSuppressWildcards] keeps
     * Retrofit from generating a wildcard body type Gson can't serialize.
     */
    @PUT
    suspend fun put(
        @Url url: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Response<JsonElement>

    /**
     * A POST whose body may hold non-string values. Needed where the backend
     * type-checks: Spatie's syncRoles treats a string "1" as a role NAME (and
     * fails "There is no role named `1`"), so role ids must travel as numbers.
     */
    @POST
    suspend fun postAny(
        @Url url: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Response<JsonElement>

    /** A POST with a raw JSON object body (nulls preserved) — campaign saves. */
    @POST
    suspend fun postObjectRaw(
        @Url url: String,
        @Body body: com.google.gson.JsonObject,
    ): Response<JsonElement>

    /** Multipart image upload (field name `image`) — the profile photo. */
    @retrofit2.http.Multipart
    @POST
    suspend fun uploadImage(
        @Url url: String,
        @retrofit2.http.Part image: okhttp3.MultipartBody.Part,
    ): Response<JsonElement>

    /**
     * A general multipart POST: text fields plus any file parts — the company
     * update with its light/dark logo uploads.
     */
    @retrofit2.http.Multipart
    @POST
    suspend fun postMultipart(
        @Url url: String,
        @retrofit2.http.PartMap fields: Map<String, @JvmSuppressWildcards okhttp3.RequestBody>,
        @retrofit2.http.Part parts: List<okhttp3.MultipartBody.Part>,
    ): Response<JsonElement>

    /** A bodiless DELETE — withdrawing an issued allotment letter/booking form. */
    @DELETE
    suspend fun delete(@Url url: String): Response<JsonElement>

    /** A PATCH with mixed-type values — the product-tracking toggle. */
    @PATCH
    suspend fun patchAny(
        @Url url: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Response<JsonElement>

    /**
     * A PATCH with a raw JSON object body (nulls preserved) — the todo update,
     * where an explicit null clears a reminder or takes an assignment back.
     */
    @PATCH
    suspend fun patchObjectRaw(
        @Url url: String,
        @Body body: com.google.gson.JsonObject,
    ): Response<JsonElement>

    /** A PUT whose body may hold non-string values (inventory-system update). */
    @PUT
    suspend fun putAny(
        @Url url: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Response<JsonElement>
}
