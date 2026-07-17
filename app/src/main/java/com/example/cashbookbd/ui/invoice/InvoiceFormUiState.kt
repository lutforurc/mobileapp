package com.example.cashbookbd.ui.invoice

import com.example.cashbookbd.data.repository.TradingExtras
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.ui.invoice.model.InstallmentInput
import com.example.cashbookbd.ui.invoice.model.InvoiceLine
import com.example.cashbookbd.ui.invoice.model.InvoiceProduct
import com.example.cashbookbd.ui.invoice.model.OrderOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

/** The fixed weight-variance directions (web `SelectWeightVariance`). */
val VARIANCE_TYPES: List<SelectorOption> = listOf(
    SelectorOption(id = "", label = "Not Applicable"),
    SelectorOption(id = "+", label = "(+) Increase"),
    SelectorOption(id = "-", label = "(-) Decrease"),
)

data class InvoiceFormUiState(
    val title: String = "Invoice",
    val isSupported: Boolean = true,
    val partyLabel: String = "Select Party",
    val amountLabel: String = "Amount",
    val autoFillPrice: Boolean = false,
    val showInvoiceNo: Boolean = false,
    val showInvoiceDate: Boolean = false,
    /** Electronics (Computer and Accessories) sales: adds a per-line serial field. */
    val isElectronics: Boolean = false,
    /** Trading sales: adds vehicle/order pickers and per-line warehouse/bag/variance. */
    val isTrading: Boolean = false,

    val party: TxnSelection? = null,

    // Trading invoice-level extras.
    val vehicleNumber: String = "",
    val warehouses: List<SelectorOption> = emptyList(),
    val purchaseOrder: OrderOption? = null,
    val salesOrder: OrderOption? = null,

    // Current (not-yet-added) product line entry.
    val selectedProduct: InvoiceProduct? = null,
    val qty: String = "",
    val price: String = "",
    val serialNo: String = "",
    // Trading per-line entry fields.
    val selectedWarehouse: SelectorOption? = null,
    val bag: String = "",
    val variance: String = "",
    val varianceType: SelectorOption = VARIANCE_TYPES.first(),

    val lines: List<InvoiceLine> = emptyList(),

    val amount: String = "",
    val discount: String = "",
    val notes: String = "",
    val invoiceNo: String = "",
    val invoiceDate: SimpleDate = SimpleDate.today(),

    // Installment plan (Electronics sales, non-Cash customer).
    val isInstallment: Boolean = false,
    val installmentAmount: String = "",
    val installmentsNo: String = "",
    val installmentStartDate: SimpleDate? = null,
    val isEarlyPayment: Boolean = false,
    val earlyDiscount: String = "",
    val earlyPaymentDate: SimpleDate? = null,

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val total: Double get() = lines.sumOf { it.amount }

    val canAddLine: Boolean
        get() = selectedProduct != null &&
            (qty.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (price.toDoubleOrNull() ?: 0.0) > 0.0

    /** When installment is on, amount and count are required (web's rule). */
    private val installmentReady: Boolean
        get() = !isInstallment ||
            ((installmentAmount.toDoubleOrNull() ?: 0.0) > 0.0 &&
                (installmentsNo.toIntOrNull() ?: 0) > 0)

    val canSubmit: Boolean
        get() = isSupported &&
            !isSubmitting &&
            party != null &&
            lines.isNotEmpty() &&
            amount.isNotBlank() &&
            installmentReady

    /** The weight-variance input is only usable once a direction is chosen. */
    val varianceEnabled: Boolean get() = varianceType.id.isNotEmpty()

    /** The Trading invoice-level extras as the API payload expects them. */
    fun toTradingExtras(): TradingExtras = TradingExtras(
        vehicleNumber = vehicleNumber.trim(),
        salesOrderNumber = salesOrder?.id.orEmpty(),
        salesOrderText = salesOrder?.orderNumber.orEmpty(),
        purchaseOrderNumber = purchaseOrder?.id.orEmpty(),
        purchaseOrderText = purchaseOrder?.orderNumber.orEmpty(),
    )

    /** The current installment fields as the API payload's `installmentData`. */
    fun toInstallmentInput(): InstallmentInput = InstallmentInput(
        amount = installmentAmount.toDoubleOrNull() ?: 0.0,
        numberOfInstallments = installmentsNo.toIntOrNull() ?: 0,
        // Optional — the server defaults the start to next month when blank.
        startDate = installmentStartDate?.toApi().orEmpty(),
        isEarlyPayment = isEarlyPayment,
        earlyDiscount = earlyDiscount.toDoubleOrNull() ?: 0.0,
        earlyPaymentDate = earlyPaymentDate?.toApi().orEmpty(),
    )
}
