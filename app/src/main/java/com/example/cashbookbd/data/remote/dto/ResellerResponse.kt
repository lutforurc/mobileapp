package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Reseller SELF-SERVICE dashboard DTOs — the four `GET /reseller/…` endpoints the
 * web's ResellerDashboard reads (dashboard overview, companies, payments,
 * commission-ledgers).
 *
 * ⚠️ Unlike most of this API, `ResellerController` does NOT use `foundData()`: the
 * payload sits DIRECTLY under `data` — an object for the overview, a plain array
 * for the three lists. There is no inner `data.data` / `transaction_date` nesting
 * (that only appears if the ledger endpoint is called with `per_page`, which the
 * dashboard never does).
 *
 * Every numeric field is typed [String] and parsed in the mapping layer: money
 * comes from SQL `SUM()`/decimal columns and counts from `COUNT()`, and either can
 * serialize as a JSON number or string — a [String] target absorbs both without a
 * Gson crash.
 */

// --- GET /reseller/dashboard : overview object directly under `data` ---
data class ResellerOverviewResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: ResellerOverviewDto? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class ResellerOverviewDto(
    @SerializedName("assigned_companies") val assignedCompanies: String? = null,
    @SerializedName("approved_payments") val approvedPayments: String? = null,
    @SerializedName("approved_amount") val approvedAmount: String? = null,
    @SerializedName("approved_commission") val approvedCommission: String? = null,
    @SerializedName("paid_commission") val paidCommission: String? = null,
    @SerializedName("payable_commission") val payableCommission: String? = null,
)

// --- GET /reseller/companies : array directly under `data` ---
data class ResellerCompaniesResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<ResellerCompanyDto>? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class ResellerCompanyDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("plan_name") val planName: String? = null,
    @SerializedName("subscription_status") val subscriptionStatus: String? = null,
    @SerializedName("access_status") val accessStatus: String? = null,
)

// --- GET /reseller/payments : array directly under `data` ---
data class ResellerPaymentsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<ResellerPaymentDto>? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class ResellerPaymentDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("company_name") val companyName: String? = null,
    @SerializedName("plan_name") val planName: String? = null,
    @SerializedName("payment_status") val paymentStatus: String? = null,
    @SerializedName("amount") val amount: String? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("commission_amount") val commissionAmount: String? = null,
)

// --- GET /reseller/commission-ledgers (no per_page) : array directly under `data` ---
data class ResellerLedgersResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<ResellerLedgerDto>? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class ResellerLedgerDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("ledger_type") val ledgerType: String? = null,
    @SerializedName("payment_method") val paymentMethod: String? = null,
    @SerializedName("paid_to_account") val paidToAccount: String? = null,
    @SerializedName("payment_reference") val paymentReference: String? = null,
    @SerializedName("reference_no") val referenceNo: String? = null,
    @SerializedName("amount") val amount: String? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("ledger_date") val ledgerDate: String? = null,
    @SerializedName("paid_at") val paidAt: String? = null,
    @SerializedName("notes") val notes: String? = null,
)
