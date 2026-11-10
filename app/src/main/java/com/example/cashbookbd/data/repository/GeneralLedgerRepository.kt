package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jBool
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.ui.reports.model.GeneralLedgerAccount
import com.example.cashbookbd.ui.reports.model.GeneralLedgerPagination
import com.example.cashbookbd.ui.reports.model.GeneralLedgerReport
import com.example.cashbookbd.ui.reports.model.GeneralLedgerRow
import com.example.cashbookbd.ui.reports.model.GeneralLedgerTotals
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * Backs General Ledger: `GET reports/general-ledger` and its Accounts Head
 * picker (`GET reports/general-ledger/accounts`).
 *
 * The payload is wrapped (`success` → `data.data`): a branch, the period, the
 * page of Accounts Heads with their posted rows, the totals over the whole
 * filtered set, and the pagination block. Totals are printed as the server
 * sends them — they cover every head, not just the visible page.
 */
class GeneralLedgerRepository(private val api: WebApi) {

    suspend fun fetch(
        branchId: Long,
        startDate: String,
        endDate: String,
        coa4Id: Long?,
        page: Int,
        perPage: Int = PER_PAGE,
    ): Resource<GeneralLedgerReport> {
        val params = mutableMapOf(
            "branch_id" to branchId.toString(),
            "start_date" to startDate,
            "end_date" to endDate,
            "page" to page.toString(),
            "per_page" to perPage.toString(),
        )
        if (coa4Id != null && coa4Id > 0) params["coa4_id"] = coa4Id.toString()
        return when (val result = api.get(PATH, params, emptyOnNotFound = true)) {
            is Resource.Success -> Resource.Success(parse(result.data.jPayload()))
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    /** The web's DdlMultiline source: active Level 4 heads matching [query]. */
    suspend fun searchAccounts(query: String): Resource<List<SelectorOption>> {
        val params = if (query.isBlank()) emptyMap() else mapOf("search" to query)
        return when (val result = api.get(ACCOUNTS_PATH, params, emptyOnNotFound = true)) {
            is Resource.Success -> Resource.Success(
                result.data.jRows().mapNotNull { row ->
                    val id = row.jLong("id")?.toString() ?: return@mapNotNull null
                    val name = row.jStr("name").ifBlank { "Account #$id" }
                    val group = row.jStr("group_name")
                    SelectorOption(id = id, label = name, sublabel = group.ifBlank { null })
                }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    private fun parse(payload: com.google.gson.JsonObject): GeneralLedgerReport {
        val branchName = payload.jObj("branch")?.jStr("name").orEmpty()
        val period = payload.jObj("period")
        val totals = payload.jObj("totals")
        val pagination = payload.jObj("pagination")
        return GeneralLedgerReport(
            branchName = branchName,
            startDate = period?.jStr("start_date").orEmpty(),
            endDate = period?.jStr("end_date").orEmpty(),
            accountsHeadName = payload.jObj("accounts_head")?.jStr("name").orEmpty(),
            accounts = payload.jObjs("accounts").map { it.toAccount() },
            totals = GeneralLedgerTotals(
                openingDebit = totals?.jNum("opening_debit") ?: 0.0,
                openingCredit = totals?.jNum("opening_credit") ?: 0.0,
                debit = totals?.jNum("debit") ?: 0.0,
                credit = totals?.jNum("credit") ?: 0.0,
                closingDebit = totals?.jNum("closing_debit") ?: 0.0,
                closingCredit = totals?.jNum("closing_credit") ?: 0.0,
            ),
            pagination = GeneralLedgerPagination(
                page = pagination?.jInt("page") ?: 1,
                perPage = pagination?.jInt("per_page") ?: PER_PAGE,
                totalAccounts = pagination?.jInt("total_accounts") ?: 0,
                lastPage = pagination?.jInt("last_page") ?: 1,
            ),
        )
    }

    private fun com.google.gson.JsonObject.toAccount(): GeneralLedgerAccount = GeneralLedgerAccount(
        coa4Id = jLong("coa4_id") ?: 0L,
        code = jStr("code"),
        name = jStr("name").ifBlank { "Account" },
        typeName = jStr("type_name"),
        group2Name = jStr("group2_name"),
        groupName = jStr("group_name"),
        openingBalance = jNum("opening_balance"),
        openingDebit = jNum("opening_debit"),
        openingCredit = jNum("opening_credit"),
        debit = jNum("debit"),
        credit = jNum("credit"),
        closingBalance = jNum("closing_balance"),
        rows = jObjs("rows").map { row ->
            GeneralLedgerRow(
                atdId = row.jLong("atd_id") ?: 0L,
                voucherNo = row.jStr("vr_no"),
                voucherDate = row.jStr("vr_date"),
                narration = row.jStr("narration"),
                debit = row.jNum("debit"),
                credit = row.jNum("credit"),
                balance = row.jNum("balance"),
                balanceType = row.jStr("balance_type"),
                isApproved = row.jBool("is_approved"),
                isBankVoucher = row.jBool("is_bank_voucher"),
            )
        },
    )

    companion object {
        const val PATH = "reports/general-ledger"
        const val ACCOUNTS_PATH = "reports/general-ledger/accounts"

        /** The web's PER_PAGE; the server caps at 200, 0 means everything. */
        const val PER_PAGE = 25
    }
}
