package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** One area row of the Connected Member report. */
data class ConnectedMemberArea(
    val areaCode: String,
    val areaNameEng: String,
    val areaNameBng: String,
    val connectedMember: Double,
    val gtMember: Double,
    val collection: Double,
    val downpayment: Double,
    val outstanding: Double,
)

/** One employee's group of areas, in server (emp_id) order. */
data class ConnectedMemberGroup(
    val employeeName: String,
    val areas: List<ConnectedMemberArea>,
)

/**
 * The web's Connected Member report (`POST reports/connected-member-data`):
 * the body is a bare object keyed by employee name → area rows (an empty
 * result serialises as `[]`). All the derived columns (DP+Coll, the 5% salary,
 * the percentage strings) are computed client-side, mirroring the web's
 * summarizeRows/percentage helpers in the screen layer.
 */
class ConnectedMemberRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun fetch(
        branchId: String,
        startDate: String,
        endDate: String,
    ): Resource<List<ConnectedMemberGroup>> = withContext(ioDispatcher) {
        try {
            val response = api.postAny(
                "reports/connected-member-data",
                mapOf(
                    "branch_id" to branchId,
                    "startdate" to startDate,
                    "enddate" to endDate,
                ),
            )
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            val body = response.body()
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            // [] is the server's empty result.
            if (body.isJsonArray) return@withContext Resource.Success(emptyList())
            val groupsJson = unwrap(body.takeIf { it.isJsonObject }?.asJsonObject)
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")

            val groups = groupsJson.entrySet().mapNotNull { (employee, value) ->
                val rows = value.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.toArea() }
                    .orEmpty()
                if (rows.isEmpty()) null else ConnectedMemberGroup(employeeName = employee, areas = rows)
            }
            Resource.Success(groups)
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private fun JsonObject.toArea(): ConnectedMemberArea = ConnectedMemberArea(
        areaCode = text("area_code"),
        areaNameEng = text("area_name_eng"),
        areaNameBng = text("area_name_bng"),
        connectedMember = dbl("connected_member"),
        gtMember = dbl("gt_member"),
        collection = dbl("collection"),
        // Left join — may be null.
        downpayment = dbl("downpayment"),
        outstanding = dbl("out_standing"),
    )

    /** The payload arrives bare; tolerate the foundData envelope regardless. */
    private fun unwrap(body: JsonObject?): JsonObject? {
        if (body == null) return null
        if (!body.has("data") || !(body.has("success") || body.has("message"))) return body
        val data = body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return data.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: data
    }

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    /** Sums arrive as strings; the web's toNumber strips commas → 0 on NaN. */
    private fun JsonObject.dbl(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
            ?.replace(",", "")?.toDoubleOrNull() ?: 0.0
}
