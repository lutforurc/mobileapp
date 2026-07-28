package com.example.cashbookbd.ui.realestate

import com.example.cashbookbd.realestate.ReCrudField
import com.example.cashbookbd.realestate.ReCrudSpec
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * One field's live value. [value] is the wire value (id for pickers, "yyyy-MM-dd"
 * for dates, …); [display] carries a label the options list can't provide (an
 * async picker's selected name, prefilled from the edit record's relation).
 */
data class ReCrudFieldState(
    val field: ReCrudField,
    val value: String,
    val display: String = "",
)

data class RealEstateCrudFormUiState(
    val spec: ReCrudSpec? = null,
    val crudId: String? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,

    val fields: List<ReCrudFieldState> = emptyList(),

    /** Protected-branch options (only loaded when the form has a BRANCH field). */
    val branches: List<SelectorOption> = emptyList(),

    val isSaving: Boolean = false,
    val saveError: String? = null,
    /** Set when a save should pop back to the list (edits, and most creates). */
    val savedMessage: String? = null,
    /**
     * Set instead of [savedMessage] on a stay-in-place create (units): the form
     * has already been reset (keeping the chosen floor) and this one-shot text
     * feeds the in-place success snackbar.
     */
    val stayMessage: String? = null,

    val sessionExpired: Boolean = false,
) {
    val isEdit: Boolean get() = crudId != null

    val canSave: Boolean
        get() = spec != null && !isSaving &&
            fields.all { !it.field.required || it.value.isNotBlank() }
}
