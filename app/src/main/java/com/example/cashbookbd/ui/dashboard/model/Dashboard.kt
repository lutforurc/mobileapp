package com.example.cashbookbd.ui.dashboard.model

import com.example.cashbookbd.data.remote.dto.BranchDto
import com.example.cashbookbd.data.remote.dto.DashboardPayload
import java.util.Locale

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
    /** Grand total across all branch groups, shown in the header badge. */
    val receivedTotal: Double,
    /** Received rows grouped by branch, mirroring the web H/O panel. */
    val receivedGroups: List<ReceivedBranchGroup>,
    val topPurchaseDays: Int,
    val topPurchases: List<TopProduct>,
    /**
     * Top-selling products. Only the non-construction dashboards show this; it
     * comes from `dashboard/branch/monthly-purchase-sales`, so it stays empty
     * until that call lands (see DashboardViewModel).
     */
    val topSales: List<TopProduct> = emptyList(),
)

data class TopProduct(
    val name: String,
    val quantity: Double,
)

/** One branch's block in the H/O panel: its heading, rows and subtotal. */
data class ReceivedBranchGroup(
    val branchName: String,
    val subtotal: Double,
    val rows: List<ReceivedFromHo>,
)

data class ReceivedFromHo(
    /** Source head-office voucher id — the stable key for the receive action. */
    val mtmId: Int,
    /** Head office's branch id, sent back verbatim in the receive request. */
    val branchId: Int,
    val voucherNo: String,
    val date: String,
    val amount: Double,
    val remarks: String,
    /** True when the remittance is already received (green check); false = pending. */
    val confirmed: Boolean,
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
        TopProduct(
            name = it.name?.trim().orEmpty().ifBlank { "Unnamed product" },
            quantity = it.qty.toAmount(),
        )
    }

    // receivedDetails is keyed by pay-branch id; keep the grouping the web panel shows.
    val authBranch = receiveDetails?.authBranch
    val receivedGroups = receiveDetails?.receivedDetails
        ?.entries
        ?.map { (branchId, items) ->
            val rows = items.map {
                ReceivedFromHo(
                    mtmId = it.mtmId ?: 0,
                    branchId = it.branchId ?: 0,
                    voucherNo = it.vrNo?.trim().orEmpty(),
                    date = reformatDate(it.vrDate),
                    amount = it.debit.toAmount(),
                    remarks = it.remarks?.trim().orEmpty(),
                    confirmed = it.remittance.isConfirmed(),
                )
            }
            ReceivedBranchGroup(
                branchName = resolveBranchName(branchId, authBranch),
                subtotal = rows.sumOf { it.amount },
                rows = rows,
            )
        }
        .orEmpty()
    val receivedTotal = receivedGroups.sumOf { it.subtotal }

    return Dashboard(
        branchName = branch?.name?.trim().orEmpty().ifBlank { "—" },
        transactionDate = trDate?.trim().orEmpty().ifBlank { "—" },
        todayReceived = received,
        todayPayment = payment,
        balance = balance,
        lastUpdate = lastUpdate?.trim().orEmpty().ifBlank { "—" },
        receiveDetailsTitle = transactionText?.trim().orEmpty().ifBlank { "Received Details from H/O" },
        receivedTotal = receivedTotal,
        receivedGroups = receivedGroups,
        topPurchaseDays = topProductDays ?: 0,
        topPurchases = purchases,
    )
}

/** PHP `meta()` truthiness: a non-blank value that isn't `false`/`0`. */
private fun String?.isConfirmed(): Boolean {
    val v = this?.trim()?.lowercase(Locale.US) ?: return false
    return v.isNotEmpty() && v != "false" && v != "0"
}

/**
 * The received panel's only group is the user's own branch, so its name comes
 * from [authBranch]; other ids fall back to a readable placeholder.
 */
private fun resolveBranchName(branchId: String, authBranch: BranchDto?): String {
    if (authBranch?.id?.toString() == branchId) {
        val name = authBranch?.name?.trim().orEmpty()
        if (name.isNotEmpty()) return name
    }
    return "Branch $branchId"
}

/** Backend `vr_date` arrives as yyyy-MM-dd (SQL date); show it as dd/MM/yyyy like the web. */
private fun reformatDate(raw: String?): String {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return ""
    val datePart = text.substringBefore('T').substringBefore(' ')
    val parts = datePart.split('-')
    if (parts.size == 3 && parts[0].length == 4 &&
        parts.all { it.toIntOrNull() != null }
    ) {
        val (y, m, d) = parts
        return "${d.padStart(2, '0')}/${m.padStart(2, '0')}/$y"
    }
    return text
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
    receivedTotal = 688_300.0,
    receivedGroups = listOf(
        ReceivedBranchGroup(
            branchName = "Pagla STP Project, WD-1",
            subtotal = 688_300.0,
            rows = listOf(
                ReceivedFromHo(260700030, 1, "2-260700030", "15/07/2026", 14_500.0, "", confirmed = false),
                ReceivedFromHo(260700027, 1, "2-260700027", "13/07/2026", 539_000.0, "", confirmed = true),
                ReceivedFromHo(260700019, 1, "2-260700019", "08/07/2026", 8_300.0, "", confirmed = true),
                ReceivedFromHo(260700013, 1, "2-260700013", "05/07/2026", 105_000.0, "", confirmed = true),
                ReceivedFromHo(260700006, 1, "2-260700006", "02/07/2026", 21_500.0, "", confirmed = true),
            ),
        ),
    ),
    topPurchaseDays = 7,
    topPurchases = listOf(
        TopProduct("Basmati Rice 50kg", 320.0),
        TopProduct("Soybean Oil 5L", 210.0),
        TopProduct("Sugar 25kg", 175.0),
        TopProduct("Red Lentil 25kg", 140.0),
    ),
)