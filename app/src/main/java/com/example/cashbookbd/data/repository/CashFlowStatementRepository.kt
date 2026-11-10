package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jBool
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.ui.reports.model.CashFlowLine
import com.example.cashbookbd.ui.reports.model.CashFlowNonCash
import com.example.cashbookbd.ui.reports.model.CashFlowOperating
import com.example.cashbookbd.ui.reports.model.CashFlowReconciliation
import com.example.cashbookbd.ui.reports.model.CashFlowSection
import com.example.cashbookbd.ui.reports.model.CashFlowStatementReport

/**
 * Backs the Cash Flow Statement: `GET reports/cash-flow-statement`.
 *
 * The server does every classification and every sum; this only lays the
 * sections out. Branch and period travel as snake_case `yyyy-MM-dd`, the shape
 * the controller parses.
 */
class CashFlowStatementRepository(private val api: WebApi) {

    suspend fun fetch(
        branchId: Long,
        startDate: String,
        endDate: String,
    ): Resource<CashFlowStatementReport> {
        val params = mapOf(
            "branch_id" to branchId.toString(),
            "start_date" to startDate,
            "end_date" to endDate,
        )
        return when (val result = api.get(PATH, params, emptyOnNotFound = true)) {
            is Resource.Success -> Resource.Success(parse(result.data.jPayload()))
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    private fun parse(payload: com.google.gson.JsonObject): CashFlowStatementReport {
        val period = payload.jObj("period")
        val operating = payload.jObj("operating")
        val nonCash = operating?.jObj("non_cash")
        val reconciliation = payload.jObj("reconciliation")

        return CashFlowStatementReport(
            branchName = payload.jObj("branch")?.jStr("name").orEmpty(),
            startDate = period?.jStr("start_date").orEmpty(),
            endDate = period?.jStr("end_date").orEmpty(),
            depreciationSupported = payload.jObj("basis")?.jBool("depreciation_supported") == true,
            operating = CashFlowOperating(
                netProfit = operating?.jNum("net_profit") ?: 0.0,
                nonCash = CashFlowNonCash(
                    depreciation = nonCash?.jNum("depreciation") ?: 0.0,
                    otherExpense = nonCash?.jNum("other_expense") ?: 0.0,
                    income = nonCash?.jNum("income") ?: 0.0,
                    other = nonCash?.jNum("other") ?: 0.0,
                    total = nonCash?.jNum("total") ?: 0.0,
                ),
                workingCapital = operating?.jObjs("working_capital")?.map { it.toLine() }.orEmpty(),
                workingCapitalTotal = operating?.jNum("working_capital_total") ?: 0.0,
                total = operating?.jNum("total") ?: 0.0,
            ),
            investing = payload.jObj("investing").toSection(),
            financing = payload.jObj("financing").toSection(),
            unclassified = payload.jObj("unclassified").toSection(),
            reconciliation = CashFlowReconciliation(
                openingCash = reconciliation?.jNum("opening_cash") ?: 0.0,
                netIncrease = reconciliation?.jNum("net_increase") ?: 0.0,
                closingCash = reconciliation?.jNum("closing_cash") ?: 0.0,
                ledgerClosingCash = reconciliation?.jNum("ledger_closing_cash") ?: 0.0,
                difference = reconciliation?.jNum("difference") ?: 0.0,
                // Absent from an older server: the web's `!== false` treats that
                // as reconciled rather than raising a warning it cannot support.
                reconciles = reconciliation?.get("reconciles")?.takeIf { it.isJsonPrimitive }
                    ?.let { it.asString != "false" && it.asString != "0" } ?: true,
            ),
        )
    }

    private fun com.google.gson.JsonObject?.toSection(): CashFlowSection {
        val lines = this?.jObjs("lines")?.map { it.toLine() }.orEmpty()
        return CashFlowSection(
            lines = lines,
            total = this?.jNum("total") ?: lines.sumOf { it.amount },
        )
    }

    private fun com.google.gson.JsonObject.toLine(): CashFlowLine = CashFlowLine(
        key = jStr("key"),
        group = jStr("group").ifBlank { "Unnamed" },
        type = jStr("type"),
        amount = jNum("amount"),
    )

    companion object {
        const val PATH = "reports/cash-flow-statement"
    }
}
