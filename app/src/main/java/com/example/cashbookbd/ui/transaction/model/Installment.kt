package com.example.cashbookbd.ui.transaction.model

import com.example.cashbookbd.core.AmountFormat

/** One receipt already taken against an installment (the "Details" popup rows). */
data class InstallmentPayment(
    val vrNo: String,
    val date: String,
    val amount: Double,
)

/**
 * One line of a customer's installment schedule
 * (`accounts/installment/details/{customerId}`), as the web's Installments
 * page reads it.
 */
data class InstallmentRow(
    val slNumber: String,
    val invoiceNo: String,
    val installmentId: String,
    val installmentNo: String,
    val dueDate: String,
    val amount: Double,
    val paidAmount: Double,
    val paidAt: String?,
    val dueAmount: Double,
    val status: String,
    val receivedDate: String,
    val earlyPaymentDiscount: Double,
    val earlyPaymentDate: String?,
    val earlyPaymentApplied: Boolean,
    val payments: List<InstallmentPayment>,
    // Only the branch-wide filter endpoint (Due Installments) sends these;
    // the per-customer schedule leaves them blank.
    val customerName: String = "",
    val father: String = "",
    val customerAddress: String = "",
    val customerMobile: String = "",
    val employee: String = "",
)

/** The Early Payment panel's numbers, computed exactly like the web page. */
data class EarlyPaymentSummary(
    val installmentId: String,
    val invoiceNo: String,
    val deadline: String,
    val invoiceTotal: Double,
    val discount: Double,
    val earlyPaymentAmount: Double,
    val paidBeforeDeadline: Double,
    val remaining: Double,
    val alreadyApplied: Boolean,
    val canApply: Boolean,
    val message: String,
)

/**
 * A date string reduced to a comparable "yyyyMMdd" key. The installment API
 * mixes `yyyy-MM-dd` and `dd/MM/yyyy`, optionally with a time part; anything
 * else is null (ignored, like the web's invalid-date guard).
 */
private fun dayKey(value: String?): String? {
    val datePart = value?.trim()?.takeIf { it.isNotEmpty() }?.substringBefore(' ') ?: return null
    Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(datePart)?.let { m ->
        return m.groupValues[1] + m.groupValues[2] + m.groupValues[3]
    }
    Regex("""^(\d{2})/(\d{2})/(\d{4})$""").find(datePart)?.let { m ->
        return m.groupValues[3] + m.groupValues[2] + m.groupValues[1]
    }
    return null
}

/**
 * Whether (and how) the first row's invoice qualifies for its early-payment
 * discount — a line-for-line port of the web page's `earlyPaymentSummary`:
 * the candidate is the first row; every row of the same invoice counts; a
 * payment counts only when taken on or before the deadline day.
 */
fun computeEarlyPaymentSummary(rows: List<InstallmentRow>): EarlyPaymentSummary? {
    val candidate = rows.firstOrNull() ?: return null
    if (candidate.earlyPaymentDiscount <= 0.0) return null
    val deadline = dayKey(candidate.earlyPaymentDate) ?: return null

    val invoiceRows = rows.filter { it.invoiceNo == candidate.invoiceNo }
    val invoiceTotal = invoiceRows.sumOf { it.amount }
    val earlyPaymentAmount = invoiceTotal - candidate.earlyPaymentDiscount

    val paidBeforeDeadline = invoiceRows.sumOf { row ->
        if (row.payments.isNotEmpty()) {
            row.payments.sumOf { payment ->
                val paidAt = dayKey(payment.date)
                if (paidAt != null && paidAt <= deadline) payment.amount else 0.0
            }
        } else {
            val receivedAt = dayKey(row.paidAt) ?: dayKey(row.receivedDate)
            if (receivedAt != null && receivedAt <= deadline) row.paidAmount else 0.0
        }
    }

    val remaining = earlyPaymentAmount - paidBeforeDeadline
    val canApply = !candidate.earlyPaymentApplied && remaining <= 0.0

    return EarlyPaymentSummary(
        installmentId = candidate.installmentId,
        invoiceNo = candidate.invoiceNo,
        deadline = candidate.earlyPaymentDate.orEmpty(),
        invoiceTotal = invoiceTotal,
        discount = candidate.earlyPaymentDiscount,
        earlyPaymentAmount = earlyPaymentAmount,
        paidBeforeDeadline = paidBeforeDeadline,
        remaining = remaining,
        alreadyApplied = candidate.earlyPaymentApplied,
        canApply = canApply,
        message = when {
            candidate.earlyPaymentApplied -> "Early payment already applied."
            canApply -> "Early payment condition is satisfied."
            else -> "Need ${AmountFormat.format(maxOf(remaining, 0.0))} more before early payment can be applied."
        },
    )
}
