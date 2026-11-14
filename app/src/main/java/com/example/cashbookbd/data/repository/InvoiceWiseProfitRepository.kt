package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.ui.reports.model.InvoiceWiseProfitPagination
import com.example.cashbookbd.ui.reports.model.InvoiceWiseProfitReport
import com.example.cashbookbd.ui.reports.model.InvoiceWiseProfitRow
import com.example.cashbookbd.ui.reports.model.InvoiceWiseProfitSummary
import com.google.gson.JsonObject

/**
 * Backs Invoice Wise Profit (`POST reports/invoice-wise-profit`, web `f0028a3b`
 * / `c59b2cfb`).
 *
 * The server filters, orders (newest invoice first, then the newer id), foots
 * the whole filtered result and only then cuts the page. `per_page = 0` means
 * "every row", which is what a print sheet would ask for — the app's print
 * policy keeps the paper on the web, so only paged reads are made here.
 */
class InvoiceWiseProfitRepository(private val api: WebApi) {

    suspend fun fetch(
        branchId: Long,
        startDate: String,
        endDate: String,
        search: String,
        page: Int,
        perPage: Int = PER_PAGE,
    ): Resource<InvoiceWiseProfitReport> {
        val body = JsonObject().apply {
            addProperty("branch_id", branchId)
            addProperty("startDate", startDate)
            addProperty("endDate", endDate)
            addProperty("search", search.trim())
            addProperty("page", page)
            addProperty("per_page", perPage)
        }
        return when (val result = api.post(PATH, body)) {
            is Resource.Success -> Resource.Success(parse(result.data.jPayload()))
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    private fun parse(payload: JsonObject): InvoiceWiseProfitReport {
        val summary = payload.get("summary")?.takeIf { it.isJsonObject }?.asJsonObject
        val pagination = payload.get("pagination")?.takeIf { it.isJsonObject }?.asJsonObject
        return InvoiceWiseProfitReport(
            rows = payload.jObjs("items").map { row ->
                InvoiceWiseProfitRow(
                    sl = row.jInt("sl") ?: 0,
                    mid = row.jLong("mid"),
                    voucherNo = row.jStr("vr_no"),
                    voucherDate = row.jStr("vr_date"),
                    customerName = row.jStr("customer_name"),
                    invoiceAmount = row.jNum("invoice_amount"),
                    receivedAmount = row.jNum("received_amount"),
                    discount = row.jNum("discount"),
                    profit = row.jNum("profit"),
                )
            },
            summary = InvoiceWiseProfitSummary(
                invoiceAmount = summary?.jNum("invoice_amount") ?: 0.0,
                receivedAmount = summary?.jNum("received_amount") ?: 0.0,
                discount = summary?.jNum("discount") ?: 0.0,
                profit = summary?.jNum("profit") ?: 0.0,
            ),
            pagination = InvoiceWiseProfitPagination(
                page = pagination?.jInt("current_page") ?: 1,
                perPage = pagination?.jInt("per_page") ?: PER_PAGE,
                total = pagination?.jInt("total") ?: 0,
                lastPage = pagination?.jInt("last_page") ?: 1,
            ),
        )
    }

    companion object {
        const val PATH = "reports/invoice-wise-profit"

        /** The web's PAGE_SIZE; the server also accepts 0 for "every row". */
        const val PER_PAGE = 10
    }
}
