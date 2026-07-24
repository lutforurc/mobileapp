package com.example.cashbookbd.ui.customer

import com.example.cashbookbd.ui.reports.model.SelectorOption

/** Party types, mirroring the web's ClientType constant. */
val CUSTOMER_TYPES = listOf(
    SelectorOption("1", "Customer"),
    SelectorOption("2", "Supplier"),
    SelectorOption("3", "Supplier & Customer"),
    SelectorOption("4", "Advance"),
)

data class AddCustomerUiState(
    val type: SelectorOption? = null,
    val name: String = "",
    val address: String = "",
    val mobile: String = "",
    val ledgerPage: String = "",
    val nationalId: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** The web requires type, name, address and mobile; the rest are optional. */
    val canSave: Boolean
        get() = !isSaving &&
            type != null &&
            name.isNotBlank() &&
            address.isNotBlank() &&
            mobile.isNotBlank()
}
