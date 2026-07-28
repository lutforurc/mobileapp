package com.example.cashbookbd.ui.customer

import com.example.cashbookbd.ui.reports.model.SelectorOption

/** The Name field's server-side limit; input is capped to match. */
const val COA_L3_NAME_MAX = 100

data class AddCoaL3UiState(
    /** True when the screen opened for an existing CoA L3 (edit mode). */
    val isEdit: Boolean = false,
    val isFormLoading: Boolean = true,
    /** Options/prefill load failure — shown full-screen with a Retry. */
    val formError: String? = null,
    val l2Options: List<SelectorOption> = emptyList(),
    /** Business sources; empty hides the Source select entirely. */
    val sources: List<SelectorOption> = emptyList(),
    val selectedL2: SelectorOption? = null,
    val selectedSource: SelectorOption? = null,
    val name: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** L2 and Name are required; Source too, whenever the tenant has sources. */
    val canSave: Boolean
        get() = !isSaving && !isFormLoading &&
            selectedL2 != null &&
            (sources.isEmpty() || selectedSource != null) &&
            name.isNotBlank()
}
