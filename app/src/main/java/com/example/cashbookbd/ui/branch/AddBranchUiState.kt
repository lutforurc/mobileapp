package com.example.cashbookbd.ui.branch

import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * The branch wizard's state.
 *
 * Field values live in [values], keyed by the API name from [BranchForm], rather
 * than as forty named properties: the form is driven by configuration, and the
 * payload is a map of exactly these keys, so a map is what the state should be.
 */
data class AddBranchUiState(
    /** The branch being edited, or null when creating. */
    val branchId: String? = null,
    /**
     * Every field the server returned for this branch. Posted back with the
     * edits merged over it, so settings the form does not show survive.
     */
    val existingFields: Map<String, String> = emptyMap(),
    /** Current form values, keyed by API name. */
    val values: Map<String, String> = emptyMap(),
    val currentStep: Int = 0,
    val branchTypes: List<SelectorOption> = emptyList(),
    val businessTypes: List<SelectorOption> = emptyList(),
    val paperSizes: List<SelectorOption> = emptyList(),
    /** True while the pickers (and, when editing, the branch) are being fetched. */
    val isLoadingOptions: Boolean = false,
    val optionsError: String? = null,
    val isSaving: Boolean = false,
    /** Failure reason from the last save attempt. */
    val error: String? = null,
    /** Set once the branch is saved; the screen navigates back. */
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isEditing: Boolean get() = branchId != null

    val screenTitle: String get() = if (isEditing) "Edit Branch" else "Add Branch"

    val saveLabel: String get() = if (isEditing) "Update Branch" else "Save Branch"

    val steps: List<BranchStep> get() = BranchForm.steps

    val step: BranchStep get() = steps[currentStep.coerceIn(steps.indices)]

    val isFirstStep: Boolean get() = currentStep == 0

    val isLastStep: Boolean get() = currentStep == steps.lastIndex

    fun text(key: String): String = values[key].orEmpty()

    fun flag(key: String): Boolean = values[key].isOn()

    fun option(key: String, from: List<SelectorOption>): SelectorOption? =
        from.firstOrNull { it.id == values[key] }

    fun options(source: BranchOptions): List<SelectorOption> = when (source) {
        BranchOptions.BRANCH_TYPE -> branchTypes
        BranchOptions.BUSINESS_TYPE -> businessTypes
        BranchOptions.PAPER_SIZE -> paperSizes
        BranchOptions.STATUS -> BranchForm.statusOptions
        BranchOptions.PAD_HEADING -> BranchForm.padHeadingOptions
        BranchOptions.PRINT_SIZE -> BranchForm.printSizeOptions
        BranchOptions.MONEY_FORMAT -> BranchForm.moneyFormatOptions
    }

    /**
     * Required fields are spread across steps, so saving is gated on all of
     * them — not just the ones on the page in view. [missingLabel] names the
     * first gap so the screen can say which.
     */
    val missingKey: String? get() = BranchForm.requiredKeys.firstOrNull { values[it].isNullOrBlank() }

    val missingLabel: String?
        get() = missingKey?.let { key ->
            steps.flatMap { it.fields }.firstOrNull { it.key == key }?.label ?: key
        }

    val canSave: Boolean get() = !isSaving && missingKey == null
}

/** Reads a flag that may arrive as 1/0, "1"/"0" or true/false. */
internal fun String?.isOn(): Boolean {
    val text = this?.trim().orEmpty()
    return text.equals("true", ignoreCase = true) || text.toDoubleOrNull()?.let { it != 0.0 } == true
}
