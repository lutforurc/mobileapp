package com.example.cashbookbd.data.remote

import com.example.cashbookbd.data.remote.dto.ApiLedgerReportResponse
import com.example.cashbookbd.data.remote.dto.LedgerSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit endpoints for the Ledger report screen: the searchable ledger
 * dropdown and the ledger detail report. Both require
 * `Authorization: Bearer <token>` (added automatically by [AuthInterceptor]).
 */
interface LedgerApiService {

    /**
     * GET {BASE_URL}/chart_of_accounts/ddl/l4-list?searchName=&acType=
     *
     * Searchable ledger/account source. [searchName] is the typed keyword;
     * [acType] is an optional account-type filter (empty for now).
     */
    @GET("chart_of_accounts/ddl/l4-list")
    suspend fun searchLedgers(
        @Query("searchName") searchName: String,
        @Query("acType") acType: String = "",
    ): Response<LedgerSearchResponse>

    /**
     * GET {BASE_URL}/reports/api-ledger?branch_id=&ledger_id=&start_date=&end_date=&delay=
     *
     * Returns the selected ledger's detail (id, name, party mobile, account
     * group). Dates must be `yyyy-MM-dd`.
     */
    @GET("reports/api-ledger")
    suspend fun getLedgerReport(
        @Query("branch_id") branchId: Long,
        @Query("ledger_id") ledgerId: Long,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("delay") delay: String = "",
    ): Response<ApiLedgerReportResponse>
}
