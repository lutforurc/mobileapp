package com.example.cashbookbd.data.repository

import android.util.Log
import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.ProfitLossAccountLine
import com.example.cashbookbd.ui.reports.model.ProfitLossReport
import com.example.cashbookbd.ui.reports.model.ProfitLossSummaryItem
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

/**
 * Backs the Profit & Loss report: `POST /reports/profit-loss`.
 *
 * Mirrors the web app exactly:
 * - The payload uses `startDate`/`endDate` (camelCase); we also send
 *   `start_date`/`end_date` for safety.
 * - The response carries a `trading[]` (Trading Account) and `netprofit[]`
 *   (Profit & Loss Account) array under one of `data.data`, `data`, or root.
 * - Trading heads are located by (coal3_id, coal4_id) and combined into
 *   Net Purchase / Net Sales / Gross Profit-Loss; the P&L account splits
 *   `netprofit` rows into income (credit>0) and expense (debit>0), yielding
 *   Net Profit / Net Loss.
 */
class ProfitLossRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val PATH = "reports/profit-loss"
        private const val LOG_TAG = "ProfitLoss"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    suspend fun fetch(
        branchId: Long,
        startDate: String,
        endDate: String,
    ): Resource<ProfitLossReport> = withContext(ioDispatcher) {
        // The web app sends startDate/endDate; send snake_case too, to be safe.
        val body = mapOf(
            "branch_id" to branchId.toString(),
            "startDate" to startDate,
            "endDate" to endDate,
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

    private fun parseBody(root: JsonElement?): Resource<ProfitLossReport> {
        if (root == null) return Resource.Error("Invalid response from server.")
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "profit-loss response: $root")

        val apiData = locateApiData(root)
            ?: return Resource.Error(noDataMessage(root))

        val trading = apiData.array("trading").mapNotNull { it.asObjectOrNull()?.toTradingRow() }
        val netProfit = apiData.array("netprofit").mapNotNull { it.asObjectOrNull()?.toNetProfitRow() }

        if (trading.isEmpty() && netProfit.isEmpty()) {
            return Resource.Error(noDataMessage(root))
        }

        // ---- Trading Account -------------------------------------------------
        val openingStock = trading.pick(coal3 = 29, coal4 = 18) { it.debit }
        val closingStock = trading.pick(coal3 = 29, coal4 = 21) { it.credit }

        val purchase = trading.pick(coal3 = 9, coal4 = 35) { it.debit }
        val purchaseReturn = trading.pick(coal3 = 9, coal4 = 16) { it.credit }
        val purchaseDiscount = trading.pick(coal3 = 8, coal4 = 40) { it.credit }
        val netPurchase = purchase - purchaseReturn - purchaseDiscount

        val sales = trading.pick(coal3 = 7, coal4 = 15) { it.credit }
        val salesDiscount = trading.pick(coal3 = 7, coal4 = 23) { it.debit }
        val salesReturn = trading.pick(coal3 = 7, coal4 = 19) { it.debit }
        val netSales = sales - salesDiscount - salesReturn

        val debitBase = openingStock + netPurchase
        val creditBase = closingStock + netSales
        val grossProfit = if (creditBase > debitBase) creditBase - debitBase else 0.0
        val grossLoss = if (debitBase > creditBase) debitBase - creditBase else 0.0

        // ---- Profit & Loss Account ------------------------------------------
        val expenseRows = netProfit.filter { it.debit > 0 }
        val incomeRows = netProfit.filter { it.credit > 0 }
        val totalExpense = expenseRows.sumOf { it.debit }
        val totalIncome = incomeRows.sumOf { it.credit }

        val debitPlBase = grossLoss + totalExpense
        val creditPlBase = grossProfit + totalIncome
        val netProfitValue = if (creditPlBase > debitPlBase) creditPlBase - debitPlBase else 0.0
        val netLossValue = if (debitPlBase > creditPlBase) debitPlBase - creditPlBase else 0.0
        val isNetProfit = netLossValue <= 0.0

        // ---- Build display model --------------------------------------------
        val tradingLines = buildList {
            add(ProfitLossAccountLine("Opening Stock", openingStock))
            add(ProfitLossAccountLine("Purchase", purchase))
            add(ProfitLossAccountLine("Less: Purchase Return", purchaseReturn))
            add(ProfitLossAccountLine("Less: Purchase Discount", purchaseDiscount))
            add(ProfitLossAccountLine("Net Purchase", netPurchase, emphasis = true))
            add(ProfitLossAccountLine("Sales", sales))
            add(ProfitLossAccountLine("Less: Sales Return", salesReturn))
            add(ProfitLossAccountLine("Less: Sales Discount", salesDiscount))
            add(ProfitLossAccountLine("Net Sales", netSales, emphasis = true))
            add(ProfitLossAccountLine("Closing Stock", closingStock))
            if (grossProfit > 0) add(ProfitLossAccountLine("Gross Profit", grossProfit, emphasis = true))
            if (grossLoss > 0) add(ProfitLossAccountLine("Gross Loss", grossLoss, emphasis = true))
        }

        val profitLossLines = buildList {
            if (grossProfit > 0) add(ProfitLossAccountLine("Gross Profit b/d", grossProfit, emphasis = true))
            if (grossLoss > 0) add(ProfitLossAccountLine("Gross Loss b/d", grossLoss, emphasis = true))
            incomeRows.forEach { add(ProfitLossAccountLine(it.name, it.credit)) }
            if (incomeRows.isNotEmpty()) add(ProfitLossAccountLine("Total Income", totalIncome, emphasis = true))
            expenseRows.forEach { add(ProfitLossAccountLine(it.name, it.debit)) }
            if (expenseRows.isNotEmpty()) add(ProfitLossAccountLine("Total Expense", totalExpense, emphasis = true))
        }

        val summary = buildList {
            add(ProfitLossSummaryItem("Opening Stock", openingStock))
            add(ProfitLossSummaryItem("Closing Stock", closingStock))
            add(ProfitLossSummaryItem("Net Purchase", netPurchase))
            add(ProfitLossSummaryItem("Net Sales", netSales))
            if (grossProfit > 0) add(ProfitLossSummaryItem("Gross Profit", grossProfit))
            if (grossLoss > 0) add(ProfitLossSummaryItem("Gross Loss", grossLoss))
            add(ProfitLossSummaryItem("Total Income", totalIncome))
            add(ProfitLossSummaryItem("Total Expense", totalExpense))
        }

        return Resource.Success(
            ProfitLossReport(
                trading = tradingLines,
                profitLoss = profitLossLines,
                summary = summary,
                netLabel = if (isNetProfit) "Net Profit" else "Net Loss",
                netAmount = if (isNetProfit) netProfitValue else netLossValue,
                isNetProfit = isNetProfit,
            )
        )
    }

    /**
     * Locates the object carrying `trading`/`netprofit`, unwrapping the nested
     * response envelopes the backend may use (data.data / data / root).
     */
    private fun locateApiData(root: JsonElement): JsonObject? {
        val rootObj = root.asObjectOrNull()
        val dataObj = rootObj?.get("data")?.asObjectOrNull()
        val dataDataObj = dataObj?.get("data")?.asObjectOrNull()

        return listOfNotNull(dataDataObj, dataObj, rootObj)
            .firstOrNull { it.has("trading") || it.has("netprofit") }
    }

    private fun noDataMessage(root: JsonElement): String {
        if (!BuildConfig.DEBUG) return "No Profit & Loss data for this selection."
        val keys = root.asObjectOrNull()?.keySet()?.joinToString(", ")
            ?: if (root.isJsonArray) "<array>" else "<non-object>"
        return "No Profit & Loss data. Response keys: [$keys]"
    }

    // ---- Row parsing --------------------------------------------------------

    private data class TradingRow(
        val coal3: Int?,
        val coal4: Int?,
        val debit: Double,
        val credit: Double,
    )

    private data class NetProfitRow(
        val name: String,
        val debit: Double,
        val credit: Double,
    )

    private fun JsonObject.toTradingRow(): TradingRow {
        val f = fieldMap()
        return TradingRow(
            coal3 = f.int("coal3_id", "coa3_id"),
            coal4 = f.int("coal4_id", "coa4_id"),
            debit = f.number("debit"),
            credit = f.number("credit"),
        )
    }

    private fun JsonObject.toNetProfitRow(): NetProfitRow {
        val f = fieldMap()
        return NetProfitRow(
            name = f.string("name", "coal4_name", "coa4_name", "head"),
            debit = f.number("debit"),
            credit = f.number("credit"),
        )
    }

    /** Selects the trading row for (coal3_id, coal4_id) and reads [side] (debit/credit). */
    private fun List<TradingRow>.pick(coal3: Int, coal4: Int, side: (TradingRow) -> Double): Double {
        val row = firstOrNull { it.coal3 == coal3 && it.coal4 == coal4 } ?: return 0.0
        return side(row)
    }

    private fun JsonObject.fieldMap(): Map<String, JsonElement> =
        entrySet().associate { it.key.lowercase(Locale.US) to it.value }

    private fun Map<String, JsonElement>.number(vararg keys: String): Double {
        for (key in keys) {
            val el = get(key.lowercase(Locale.US))?.takeUnless { it.isJsonNull } ?: continue
            if (el.isJsonPrimitive) el.asString.replace(",", "").trim().toDoubleOrNull()?.let { return it }
        }
        return 0.0
    }

    private fun Map<String, JsonElement>.int(vararg keys: String): Int? {
        for (key in keys) {
            val el = get(key.lowercase(Locale.US))?.takeUnless { it.isJsonNull } ?: continue
            if (el.isJsonPrimitive) el.asString.trim().toDoubleOrNull()?.let { return it.toInt() }
        }
        return null
    }

    private fun Map<String, JsonElement>.string(vararg keys: String): String {
        for (key in keys) {
            val el = get(key.lowercase(Locale.US))?.takeUnless { it.isJsonNull } ?: continue
            if (el.isJsonPrimitive) {
                val s = el.asString.trim()
                if (s.isNotEmpty()) return s
            }
        }
        return ""
    }

    private fun JsonObject.array(key: String): JsonArray =
        get(key)?.takeUnless { it.isJsonNull }?.let { if (it.isJsonArray) it.asJsonArray else null } ?: JsonArray()

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
}
