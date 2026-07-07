package com.example.cashbookbd.data.repository

import android.util.Log
import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.BalanceSheetGroup
import com.example.cashbookbd.ui.reports.model.BalanceSheetItem
import com.example.cashbookbd.ui.reports.model.BalanceSheetReport
import com.example.cashbookbd.ui.reports.model.BalanceSheetSection
import com.example.cashbookbd.ui.reports.model.BalanceSheetSummaryItem
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

/**
 * Backs the Balance Sheet report: `POST /reports/balance-sheet`.
 *
 * Mirrors the web app: the payload uses `branchId`/`startDate`/`endDate`
 * (camelCase; snake_case also sent for safety), and the response is a structured
 * object (`assets`/`liabilities`/`equity`/`totals`) under one of `data.data`,
 * `data`, or root — not a flat row array.
 */
class BalanceSheetRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val PATH = "reports/balance-sheet"
        private const val LOG_TAG = "BalanceSheet"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        // (response key, display title) for the three sections, in render order.
        private val SECTION_KEYS = listOf(
            "assets" to "Assets",
            "liabilities" to "Liabilities",
            "equity" to "Equity",
        )

        private val DESCRIPTION_KEYS = listOf("name", "group_name", "head", "description", "title")
        private val AMOUNT_KEYS = listOf("balance", "amount", "total", "value")
        private val GROUP_TITLE_KEYS = listOf("group_name", "name", "title")
        private val GROUP_TOTAL_KEYS = listOf("total", "amount", "balance", "group_total")
    }

    suspend fun fetch(
        branchId: Long,
        startDate: String,
        endDate: String,
    ): Resource<BalanceSheetReport> = withContext(ioDispatcher) {
        val body = mapOf(
            "branchId" to branchId.toString(),
            "startDate" to startDate,
            "endDate" to endDate,
            "branch_id" to branchId.toString(),
            "start_date" to startDate,
            "end_date" to endDate,
        )
        try {
            val response = api.post(PATH, body)
            when (response.code()) {
                HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> return@withContext Resource.Error(
                    "You do not have permission to view this report."
                )
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error(
                    "Server error (${response.code()}). Please try again later."
                )
            }
            parseBody(response.body())
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            when (e.code()) {
                HTTP_UNAUTHORIZED -> Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> Resource.Error("You do not have permission to view this report.")
                else -> Resource.Error("Server error (${e.code()}). Please try again later.")
            }
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private fun parseBody(root: JsonElement?): Resource<BalanceSheetReport> {
        if (root == null) return Resource.Error("Invalid response from server.")
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "balance-sheet response: $root")

        val apiData = locateApiData(root) ?: return Resource.Error(noDataMessage(root))

        val sections = SECTION_KEYS.mapNotNull { (key, title) ->
            val records = apiData.sectionRecords(key)
            if (records.isEmpty()) null else parseSection(title, records)
        }

        val totals = apiData.get("totals")?.asObjectOrNull()
        val assets = totals.numberOrNull("assets") ?: sectionTotal(sections, "Assets")
        val liabilities = totals.numberOrNull("liabilities") ?: sectionTotal(sections, "Liabilities")
        val equity = totals.numberOrNull("equity") ?: sectionTotal(sections, "Equity")
        val liabAndEquity = totals.numberOrNull("liabilities_and_equity")
            ?: ((liabilities ?: 0.0) + (equity ?: 0.0))
        val difference = totals.numberOrNull("difference")
            ?: ((assets ?: 0.0) - liabAndEquity)

        if (sections.isEmpty() && totals == null) {
            return Resource.Error(noDataMessage(root))
        }

        val summary = buildList {
            assets?.let { add(BalanceSheetSummaryItem("Assets", it)) }
            liabilities?.let { add(BalanceSheetSummaryItem("Liabilities", it)) }
            equity?.let { add(BalanceSheetSummaryItem("Equity", it)) }
            add(BalanceSheetSummaryItem("Liabilities + Equity", liabAndEquity))
            add(BalanceSheetSummaryItem("Difference", difference))
        }

        return Resource.Success(BalanceSheetReport(sections = sections, summary = summary))
    }

    private fun parseSection(title: String, records: List<JsonObject>): BalanceSheetSection {
        val groups = mutableListOf<BalanceSheetGroup>()
        val looseItems = mutableListOf<BalanceSheetItem>()

        for (record in records) {
            val itemEls = record.get("items")?.takeUnless { it.isJsonNull }
                ?.let { if (it.isJsonArray) it.asJsonArray else null }

            if (itemEls != null && itemEls.size() > 0) {
                val items = itemEls.mapNotNull { it.asObjectOrNull()?.toItem() }
                val groupTitle = record.fieldMap().string(GROUP_TITLE_KEYS)
                val groupTotal = record.fieldMap().numberOrNull(GROUP_TOTAL_KEYS)
                    ?: items.sumOf { it.amount }
                groups += BalanceSheetGroup(
                    title = groupTitle.ifBlank { null },
                    items = items,
                    total = groupTotal,
                )
            } else {
                looseItems += record.toItem()
            }
        }

        if (looseItems.isNotEmpty()) {
            groups += BalanceSheetGroup(
                title = null,
                items = looseItems,
                total = looseItems.sumOf { it.amount },
            )
        }

        return BalanceSheetSection(
            title = title,
            groups = groups,
            total = groups.sumOf { it.total },
        )
    }

    /** Locates the object carrying assets/liabilities/equity, unwrapping envelopes. */
    private fun locateApiData(root: JsonElement): JsonObject? {
        val rootObj = root.asObjectOrNull()
        val dataObj = rootObj?.get("data")?.asObjectOrNull()
        val dataDataObj = dataObj?.get("data")?.asObjectOrNull()
        return listOfNotNull(dataDataObj, dataObj, rootObj).firstOrNull {
            it.has("assets") || it.has("liabilities") || it.has("equity") || it.has("totals")
        }
    }

    /** A section's records, whether it's an array of rows or a PHP assoc object. */
    private fun JsonObject.sectionRecords(key: String): List<JsonObject> {
        val value = get(key)?.takeUnless { it.isJsonNull } ?: return emptyList()
        return when {
            value.isJsonArray -> value.asJsonArray.mapNotNull { it.asObjectOrNull() }
            value.isJsonObject && value.asJsonObject.entrySet().all { it.value.isJsonObject } ->
                value.asJsonObject.entrySet().map { it.value.asJsonObject }
            else -> emptyList()
        }
    }

    private fun JsonObject.toItem(): BalanceSheetItem {
        val f = fieldMap()
        return BalanceSheetItem(
            description = f.string(DESCRIPTION_KEYS),
            amount = f.numberOrNull(AMOUNT_KEYS) ?: 0.0,
        )
    }

    private fun sectionTotal(sections: List<BalanceSheetSection>, title: String): Double? =
        sections.firstOrNull { it.title == title }?.total

    private fun noDataMessage(root: JsonElement): String {
        if (!BuildConfig.DEBUG) return "No Balance Sheet data for this selection."
        val keys = root.asObjectOrNull()?.keySet()?.joinToString(", ")
            ?: if (root.isJsonArray) "<array>" else "<non-object>"
        return "No Balance Sheet data. Response keys: [$keys]"
    }

    // ---- helpers ------------------------------------------------------------

    private fun JsonObject.fieldMap(): Map<String, JsonElement> =
        entrySet().associate { it.key.lowercase(Locale.US) to it.value }

    private fun Map<String, JsonElement>.string(candidates: List<String>): String {
        for (key in candidates) {
            val el = get(key.lowercase(Locale.US))?.takeUnless { it.isJsonNull } ?: continue
            if (el.isJsonPrimitive) {
                val s = el.asString.trim()
                if (s.isNotEmpty()) return s
            }
        }
        return ""
    }

    private fun Map<String, JsonElement>.numberOrNull(candidates: List<String>): Double? {
        for (key in candidates) {
            val el = get(key.lowercase(Locale.US))?.takeUnless { it.isJsonNull } ?: continue
            if (el.isJsonPrimitive) {
                el.asString.replace(",", "").trim().toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun JsonObject?.numberOrNull(key: String): Double? {
        val el = this?.get(key)?.takeUnless { it.isJsonNull } ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString.replace(",", "").trim().toDoubleOrNull()
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
}
