package com.example.cashbookbd.ui.dashboard.model

import com.example.cashbookbd.data.remote.dto.DashboardPayload

/**
 * UI-facing dashboard model. Everything is already resolved to display-ready,
 * non-null values so the Compose layer never deals with parsing or nullability.
 */
data class Dashboard(
    val branchName: String,
    val transactionDate: String,
    val todayReceived: Double,
    val todayPayment: Double,
    val balance: Double,
    val lastUpdate: String,
    /** Header for the H/O section, e.g. "Received Details from H/O". */
    val receiveDetailsTitle: String,
    val topPurchaseDays: Int,
    val topPurchases: List<TopPurchase>,
    val receivedFromHeadOffice: List<ReceivedFromHo>,
)

data class TopPurchase(
    val name: String,
    val quantity: Double,
)

data class ReceivedFromHo(
    val branch: String,
    val voucherNo: String,
    val date: String,
    val amount: Double,
    val remarks: String,
)

/** Lenient money parser: decimal strings, nulls and blanks all fold to 0.0. */
private fun String?.toAmount(): Double = this?.trim()?.toDoubleOrNull() ?: 0.0

/** Maps the raw API payload into the display model, computing derived values. */
fun DashboardPayload.toDashboard(): Dashboard {
    val received = todayReceived?.debit.toAmount()
    val payment = todayReceived?.credit.toAmount()
    // Cumulative balance up to the transaction date = total debit - total credit.
    val balance = totalTransaction?.debit.toAmount() - totalTransaction?.credit.toAmount()

    val purchases = topProductsPurchase.orEmpty().map {
        TopPurchase(
            name = it.name?.trim().orEmpty().ifBlank { "Unnamed product" },
            quantity = it.qty.toAmount(),
        )
    }

    // receivedDetails is keyed by pay-branch id; flatten every branch's rows.
    val receivedRows = receiveDetails?.receivedDetails
        ?.values
        ?.flatten()
        ?.map {
            ReceivedFromHo(
                branch = it.branch?.toString().orEmpty(),
                voucherNo = it.vrNo?.trim().orEmpty(),
                date = it.vrDate?.trim().orEmpty(),
                amount = it.debit.toAmount(),
                remarks = it.remarks?.trim().orEmpty(),
            )
        }
        .orEmpty()

    return Dashboard(
        branchName = branch?.name?.trim().orEmpty().ifBlank { "—" },
        transactionDate = trDate?.trim().orEmpty().ifBlank { "—" },
        todayReceived = received,
        todayPayment = payment,
        balance = balance,
        lastUpdate = lastUpdate?.trim().orEmpty().ifBlank { "—" },
        receiveDetailsTitle = transactionText?.trim().orEmpty().ifBlank { "Received Details from H/O" },
        topPurchaseDays = topProductDays ?: 0,
        topPurchases = purchases,
        receivedFromHeadOffice = receivedRows,
    )
}

/**
 * Static sample used only for @Preview / design testing — never shown to users
 * at runtime.
 */
val previewDashboard: Dashboard = Dashboard(
    branchName = "Gulshan Branch",
    transactionDate = "06/07/2026",
    todayReceived = 125_000.0,
    todayPayment = 87_500.0,
    balance = 342_900.0,
    lastUpdate = "5 minutes ago",
    receiveDetailsTitle = "Received Details from H/O",
    topPurchaseDays = 7,
    topPurchases = listOf(
        TopPurchase("Basmati Rice 50kg", 320.0),
        TopPurchase("Soybean Oil 5L", 210.0),
        TopPurchase("Sugar 25kg", 175.0),
        TopPurchase("Red Lentil 25kg", 140.0),
    ),
    receivedFromHeadOffice = listOf(
        ReceivedFromHo("Head Office", "RV-1042", "05/07/2026", 100_000.0, "July remittance"),
        ReceivedFromHo("Head Office", "RV-1039", "03/07/2026", 60_000.0, "Advance"),
    ),
)