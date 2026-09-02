package com.example.cashbookbd.data.repository

import android.util.Log
import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.BalanceSheetColumns
import com.example.cashbookbd.ui.reports.model.BalanceSheetGroup
import com.example.cashbookbd.ui.reports.model.BalanceSheetSubsection
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
        private val GROUP_TITLE_KEYS = listOf("group_name", "name", "title")

        // The web's fallback chains: item closing = closing || balance,
        // group closing = closing || total (older keys kept as extra fallbacks).
        private val ITEM_CLOSING_KEYS = listOf("closing", "balance", "amount", "total", "value")
        private val GROUP_CLOSING_KEYS = listOf("closing", "total", "amount", "balance", "group_total")
        private val OPENING_KEYS = listOf("opening")
        private val MOVEMENT_KEYS = listOf("movement")

        /** The web's 0.01 threshold for "this column is effectively zero". */
        private const val EPSILON = 0.01
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

        var sections = SECTION_KEYS.mapNotNull { (key, title) ->
            val records = apiData.sectionRecords(key)
            if (records.isEmpty()) null else parseSection(title, records)
        }
        if (sections.isEmpty()) return Resource.Error(noDataMessage(root))

        // The web folds the totals' opening difference into the Equity section's
        // Net Profit/Loss group before computing any figure.
        val openingDifference = apiData.get("totals")?.asObjectOrNull()
            ?.get("difference_columns")?.asObjectOrNull()
            .numberOrNull("opening") ?: 0.0
        sections = applyEquityAdjustment(sections, openingDifference)

        // Column totals are summed client-side, like the web's sumReportColumns;
        // the headline summary cards read the closing column.
        val assets = sections.firstOrNull { it.title == "Assets" }
        val liabilities = sections.firstOrNull { it.title == "Liabilities" }
        val equity = sections.firstOrNull { it.title == "Equity" }
        val liabAndEquity = (liabilities?.closing ?: 0.0) + (equity?.closing ?: 0.0)
        val difference = (assets?.closing ?: 0.0) - liabAndEquity

        val summary = listOf(
            BalanceSheetSummaryItem("Total Assets", assets?.closing ?: 0.0),
            BalanceSheetSummaryItem("Liabilities + Equity", liabAndEquity),
            BalanceSheetSummaryItem("Difference", difference),
        )

        return Resource.Success(
            BalanceSheetReport(
                sections = sections,
                summary = summary,
                sectioned = parseSectioned(apiData),
            )
        )
    }

    /**
     * The `sections` key (api a30c16aa): each side's groups hung under their
     * level-2 heads, classified by the chart rather than by the sign of the
     * balance. Absent on an older server — the caller then draws the flat
     * lists, readable without the Current/Fixed split.
     */
    private fun parseSectioned(apiData: JsonObject): Map<String, List<BalanceSheetSubsection>> {
        val root = apiData.get("sections")?.asObjectOrNull() ?: return emptyMap()
        return SECTION_KEYS.associate { (key, title) ->
            val list = root.get(key)?.takeUnless { it.isJsonNull }
                ?.let { if (it.isJsonArray) it.asJsonArray.mapNotNull { e -> e.asObjectOrNull() } else emptyList() }
                .orEmpty()
            title to list.map { it.toSubsection() }
        }
    }

    private fun JsonObject.toSubsection(): BalanceSheetSubsection {
        val f = fieldMap()
        val groups = get("groups")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.asObjectOrNull() }
            ?.map { record ->
                val items = record.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { it.asObjectOrNull()?.toItem() }.orEmpty()
                val rf = record.fieldMap()
                BalanceSheetGroup(
                    title = rf.string(GROUP_TITLE_KEYS),
                    items = items,
                    opening = rf.numberOrNull(OPENING_KEYS) ?: items.sumOf { it.opening },
                    movement = rf.numberOrNull(MOVEMENT_KEYS) ?: items.sumOf { it.movement },
                    closing = rf.numberOrNull(GROUP_CLOSING_KEYS) ?: items.sumOf { it.closing },
                    isContra = record.get("is_contra")?.takeUnless { it.isJsonNull }
                        ?.takeIf { it.isJsonPrimitive }?.asString
                        ?.let { it == "true" || it == "1" } == true,
                )
            }
            .orEmpty()
        val hasContra = get("has_contra")?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.let { it == "true" || it == "1" } == true
        return BalanceSheetSubsection(
            name = f.string(listOf("name")),
            groups = groups,
            columns = get("columns")?.asObjectOrNull().toColumns()
                ?: BalanceSheetColumns(
                    groups.sumOf { it.opening }, groups.sumOf { it.movement }, groups.sumOf { it.closing },
                ),
            hasContra = hasContra,
            depreciation = if (hasContra) get("depreciation_columns")?.asObjectOrNull().toColumns() else null,
        )
    }

    private fun JsonObject?.toColumns(): BalanceSheetColumns? {
        val o = this ?: return null
        return BalanceSheetColumns(
            opening = o.numberOrNull("opening") ?: 0.0,
            movement = o.numberOrNull("movement") ?: 0.0,
            closing = o.numberOrNull("closing") ?: 0.0,
        )
    }

    private fun parseSection(title: String, records: List<JsonObject>): BalanceSheetSection {
        val groups = mutableListOf<BalanceSheetGroup>()

        for (record in records) {
            val itemEls = record.get("items")?.takeUnless { it.isJsonNull }
                ?.let { if (it.isJsonArray) it.asJsonArray else null }

            if (itemEls != null && itemEls.size() > 0) {
                val items = itemEls.mapNotNull { it.asObjectOrNull()?.toItem() }
                val f = record.fieldMap()
                groups += BalanceSheetGroup(
                    title = f.string(GROUP_TITLE_KEYS),
                    items = items,
                    opening = f.numberOrNull(OPENING_KEYS) ?: items.sumOf { it.opening },
                    movement = f.numberOrNull(MOVEMENT_KEYS) ?: items.sumOf { it.movement },
                    closing = f.numberOrNull(GROUP_CLOSING_KEYS) ?: items.sumOf { it.closing },
                )
            } else {
                // A record with no items renders as its own single-item group,
                // so every table row keeps the group-with-breakdown shape.
                val item = record.toItem()
                groups += BalanceSheetGroup(
                    title = item.description,
                    items = listOf(item),
                    opening = item.opening,
                    movement = item.movement,
                    closing = item.closing,
                )
            }
        }

        return sectionOf(title, groups)
    }

    /** A section whose column totals are the sums of its groups. */
    private fun sectionOf(title: String, groups: List<BalanceSheetGroup>): BalanceSheetSection =
        BalanceSheetSection(
            title = title,
            groups = groups,
            opening = groups.sumOf { it.opening },
            movement = groups.sumOf { it.movement },
            closing = groups.sumOf { it.closing },
        )

    /**
     * The web's equity adjustment, verbatim: when the totals carry a non-zero
     * opening difference, fold it into the Equity section's "Net Profit"/"Net
     * Loss" group (only if that group's own opening is still zero) — adjusting
     * the group's opening/closing and its first item — or append a synthetic
     * Net Profit/Net Loss group when none matches.
     */
    private fun applyEquityAdjustment(
        sections: List<BalanceSheetSection>,
        openingDifference: Double,
    ): List<BalanceSheetSection> {
        if (kotlin.math.abs(openingDifference) < EPSILON) return sections
        return sections.map { section ->
            if (section.title != "Equity") return@map section

            var matched = false
            val groups = section.groups.map { group ->
                val name = group.title.trim().lowercase(Locale.US)
                val isNetHead = name == "net profit" || name == "net loss"
                if (matched || !isNetHead || kotlin.math.abs(group.opening) >= EPSILON) {
                    group
                } else {
                    matched = true
                    val items = group.items.toMutableList()
                    if (items.isNotEmpty()) {
                        val first = items[0]
                        items[0] = first.copy(
                            opening = first.opening + openingDifference,
                            closing = first.closing + openingDifference,
                        )
                    }
                    group.copy(
                        opening = group.opening + openingDifference,
                        closing = group.closing + openingDifference,
                        items = items,
                    )
                }
            }

            val adjusted = if (matched) groups else {
                val name = if (openingDifference >= 0) "Net Profit" else "Net Loss"
                groups + BalanceSheetGroup(
                    title = name,
                    items = listOf(
                        BalanceSheetItem(
                            description = name,
                            opening = openingDifference,
                            movement = 0.0,
                            closing = openingDifference,
                        )
                    ),
                    opening = openingDifference,
                    movement = 0.0,
                    closing = openingDifference,
                )
            }
            sectionOf(section.title, adjusted)
        }
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
            opening = f.numberOrNull(OPENING_KEYS) ?: 0.0,
            movement = f.numberOrNull(MOVEMENT_KEYS) ?: 0.0,
            closing = f.numberOrNull(ITEM_CLOSING_KEYS) ?: 0.0,
        )
    }

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
