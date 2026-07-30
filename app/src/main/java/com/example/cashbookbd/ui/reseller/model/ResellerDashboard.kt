package com.example.cashbookbd.ui.reseller.model

import com.example.cashbookbd.data.remote.dto.ResellerCompanyDto
import com.example.cashbookbd.data.remote.dto.ResellerLedgerDto
import com.example.cashbookbd.data.remote.dto.ResellerOverviewDto
import com.example.cashbookbd.data.remote.dto.ResellerPaymentDto
import java.util.Locale

/**
 * Display-ready model for the reseller self-service dashboard. Everything is
 * already resolved to non-null, formatted values so Compose never parses or
 * null-checks. Mirrors the web `ResellerDashboard` page: a KPI [overview] plus
 * three tables (My Clients, Commission History, Payment Details).
 */
data class ResellerDashboard(
    val overview: ResellerOverview,
    val clients: List<ResellerClient>,
    val commissionHistory: List<ResellerCommissionRow>,
    val paymentDetails: List<ResellerPaymentRow>,
)

/** The six KPI tiles shown for the logged-in reseller. */
data class ResellerOverview(
    val clients: Int,
    val approvedPayments: Int,
    val approvedAmount: Double,
    val commission: Double,
    val paid: Double,
    val payable: Double,
)

/** "My Clients" row — a company assigned to this reseller. */
data class ResellerClient(
    val company: String,
    val plan: String,
    val subscription: String,
    val access: String,
)

/** "Commission History" row — a client payment with its commission. */
data class ResellerCommissionRow(
    val company: String,
    val payment: String,
    val amount: Double,
    val commission: Double,
    val currency: String,
)

/** "Payment Details" row — a commission payout received (a `paid` ledger entry). */
data class ResellerPaymentRow(
    val date: String,
    val method: String,
    val account: String,
    val reference: String,
    val amount: Double,
    val currency: String,
    val notes: String,
)

private fun String?.toAmount(): Double = this?.trim()?.toDoubleOrNull() ?: 0.0

/** COUNT() may arrive as "12" or 12.0; fold either to a whole number. */
private fun String?.toCount(): Int = this?.trim()?.toDoubleOrNull()?.toInt() ?: 0

private fun String?.clean(): String = this?.trim().orEmpty()

fun ResellerOverviewDto.toResellerOverview(): ResellerOverview = ResellerOverview(
    clients = assignedCompanies.toCount(),
    approvedPayments = approvedPayments.toCount(),
    approvedAmount = approvedAmount.toAmount(),
    commission = approvedCommission.toAmount(),
    paid = paidCommission.toAmount(),
    payable = payableCommission.toAmount(),
)

fun ResellerCompanyDto.toResellerClient(): ResellerClient = ResellerClient(
    company = name.clean().ifBlank { "—" },
    plan = planName.clean().ifBlank { "—" },
    subscription = subscriptionStatus.clean().labelOrDash(),
    access = accessStatus.clean().labelOrDash(),
)

fun ResellerPaymentDto.toResellerCommissionRow(): ResellerCommissionRow = ResellerCommissionRow(
    company = companyName.clean().ifBlank { "—" },
    payment = paymentStatus.clean().labelOrDash(),
    amount = amount.toAmount(),
    commission = commissionAmount.toAmount(),
    currency = currency.clean().ifBlank { "BDT" },
)

fun ResellerLedgerDto.toResellerPaymentRow(): ResellerPaymentRow = ResellerPaymentRow(
    // Web shows ledger_date, falling back to paid_at.
    date = reformatDate(ledgerDate.clean().ifBlank { paidAt.clean() }),
    method = paymentMethodLabel(paymentMethod),
    account = paidToAccount.clean().ifBlank { "—" },
    // Web shows payment_reference, falling back to reference_no.
    reference = paymentReference.clean().ifBlank { referenceNo.clean() }.ifBlank { "—" },
    amount = amount.toAmount(),
    currency = currency.clean().ifBlank { "BDT" },
    notes = notes.clean(),
)

/** bkash/nagad/bank/cash/other -> display label, mirroring the web. */
private fun paymentMethodLabel(raw: String?): String = when (raw?.trim()?.lowercase(Locale.US)) {
    "bkash" -> "bKash"
    "nagad" -> "Nagad"
    "bank" -> "Bank"
    "cash" -> "Cash"
    "other" -> "Other"
    null, "" -> "—"
    else -> raw.trim().replaceFirstChar { it.uppercase() }
}

/** Capitalise a status token (e.g. "approved" -> "Approved"); blank -> "—". */
private fun String.labelOrDash(): String =
    if (isBlank()) "—" else replaceFirstChar { it.uppercase() }

/** yyyy-MM-dd (or an ISO datetime) -> dd/MM/yyyy, matching the web's date column. */
private fun reformatDate(raw: String?): String {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return "—"
    val datePart = text.substringBefore('T').substringBefore(' ')
    val parts = datePart.split('-')
    if (parts.size == 3 && parts[0].length == 4 && parts.all { it.toIntOrNull() != null }) {
        val (y, m, d) = parts
        return "${d.padStart(2, '0')}/${m.padStart(2, '0')}/$y"
    }
    return text
}

/** Static sample for @Preview only — never shown at runtime. */
val previewResellerDashboard = ResellerDashboard(
    overview = ResellerOverview(
        clients = 12,
        approvedPayments = 34,
        approvedAmount = 485_000.0,
        commission = 48_500.0,
        paid = 30_000.0,
        payable = 18_500.0,
    ),
    clients = listOf(
        ResellerClient("Gulshan Traders", "Business Yearly", "Active", "Allowed"),
        ResellerClient("Banani Mart", "Standard Monthly", "Active", "Allowed"),
        ResellerClient("Mirpur Store", "Trial", "Expired", "Blocked"),
    ),
    commissionHistory = listOf(
        ResellerCommissionRow("Gulshan Traders", "Approved", 12_000.0, 1_200.0, "BDT"),
        ResellerCommissionRow("Banani Mart", "Approved", 6_000.0, 600.0, "BDT"),
        ResellerCommissionRow("Mirpur Store", "Pending", 3_000.0, 300.0, "BDT"),
    ),
    paymentDetails = listOf(
        ResellerPaymentRow("15/07/2026", "bKash", "01711-000000", "TRX12345", 20_000.0, "BDT", "July payout"),
        ResellerPaymentRow("02/06/2026", "Bank", "AC 1234567", "NEFT-889", 10_000.0, "BDT", ""),
    ),
)
