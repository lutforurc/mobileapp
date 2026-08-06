package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** One ledger/product line of a Group Report section, with its month cells. */
data class GroupReportRow(
    val name: String,
    /**
     * "Jan-2026" → (left, right): Payment|Bill for Operating Cost,
     * Qty|Amount for Purchase Cost. Entries for one month are *summed*, as the
     * web's pairForMonth does — never first-match.
     */
    val byMonth: Map<String, Pair<Double, Double>>,
) {
    val lineLeft: Double get() = byMonth.values.sumOf { it.first }
    val lineRight: Double get() = byMonth.values.sumOf { it.second }
}

/** One section of the report ("Operating Cost" / "Purchase Cost"). */
data class GroupReportSection(
    val title: String,
    /** Purchase Cost shows Qty|Amount sub-headers; Operating Payment|Bill. */
    val isPurchase: Boolean,
    val rows: List<GroupReportRow>,
)

data class GroupReport(
    /** Distinct "MMM-yyyy" labels in call order (group-1 months first). */
    val months: List<String>,
    val sections: List<GroupReportSection>,
) {
    val isEmpty: Boolean get() = sections.all { it.rows.isEmpty() }
}

/**
 * The web's Group Report (`POST reports/group/report/data`): Operating Cost
 * (report_group 1, account-keyed Payment/Bill) and Purchase Cost (report_group
 * 2, product-keyed Qty/Amount). "All group" is client-side only — the server
 * rejects anything but 1|2 — so it runs both requests and merges, exactly as
 * the web's Promise.all does.
 */
class GroupReportRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** [reportGroup] "1", "2", or "0" for the merged all-group view. */
    suspend fun fetch(
        branchId: String,
        reportGroup: String,
        startDate: String,
        endDate: String,
    ): Resource<GroupReport> = withContext(ioDispatcher) {
        try {
            val groups = if (reportGroup == "0") listOf("1", "2") else listOf(reportGroup)
            // The web fires both group requests in parallel and merges.
            val results = coroutineScope {
                groups.map { g -> async { fetchOne(g, branchId, startDate, endDate) } }
                    .map { it.await() }
            }
            results.filterIsInstance<Resource.Error>().firstOrNull()?.let { return@withContext it }

            val months = LinkedHashSet<String>()
            val sections = mutableListOf<GroupReportSection>()
            results.filterIsInstance<Resource.Success<GroupReport>>().forEach { r ->
                months.addAll(r.data.months)
                sections.addAll(r.data.sections.filter { it.rows.isNotEmpty() })
            }
            Resource.Success(GroupReport(months = months.toList(), sections = sections))
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private suspend fun fetchOne(
        reportGroup: String,
        branchId: String,
        startDate: String,
        endDate: String,
    ): Resource<GroupReport> {
        val response = api.postAny(
            "reports/group/report/data",
            mapOf(
                "branch_id" to branchId,
                "report_group" to reportGroup,
                // Strict d/m/Y — anything else 422s.
                "startdate" to startDate,
                "enddate" to endDate,
            ),
        )
        if (response.code() == 401) {
            return Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        }
        if (response.code() == 422) {
            return Resource.Error("The date range is invalid — Start Date must not be after End Date.")
        }
        val body = unwrap(response.body()?.takeIf { it.isJsonObject }?.asJsonObject)
            ?: return Resource.Error("Server error (${response.code()}). Please try again later.")

        val months = body.get("monthNames")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
            .orEmpty()

        val isPurchase = reportGroup == "2"
        val dataKey = if (isPurchase) "purchases_data" else "report_data"
        // An empty map serialises as [] — tolerate both shapes.
        val grouped = body.get(dataKey)?.takeIf { it.isJsonObject }?.asJsonObject

        val rows = grouped?.entrySet().orEmpty().map { (name, value) ->
            val byMonth = LinkedHashMap<String, Pair<Double, Double>>()
            value.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                val entry = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val month = entry.text("year")
                if (month.isEmpty()) return@forEach
                val left = entry.dbl(if (isPurchase) "qty" else "debit")
                val right = entry.dbl(if (isPurchase) "total" else "credit")
                val prior = byMonth[month] ?: (0.0 to 0.0)
                byMonth[month] = (prior.first + left) to (prior.second + right)
            }
            GroupReportRow(name = name, byMonth = byMonth)
        }

        val title = if (isPurchase) "Purchase Cost" else "Operating Cost"
        return Resource.Success(
            GroupReport(
                months = months,
                sections = listOf(GroupReportSection(title = title, isPurchase = isPurchase, rows = rows)),
            )
        )
    }

    /** The payload arrives bare, but tolerate the foundData envelope too. */
    private fun unwrap(body: JsonObject?): JsonObject? {
        if (body == null) return null
        if (body.has("monthNames")) return body
        val data = body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return body
        if (data.has("monthNames")) return data
        return data.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: data
    }

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    /** SUM columns arrive as JSON strings ("12500.00"); commas tolerated. */
    private fun JsonObject.dbl(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
            ?.replace(",", "")?.toDoubleOrNull() ?: 0.0
}
