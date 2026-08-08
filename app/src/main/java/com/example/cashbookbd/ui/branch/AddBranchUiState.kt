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
    val inventorySystems: List<SelectorOption> = emptyList(),
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
    /**
     * Clearing this branch's opening balances. Offered only to those holding
     * `branch.opening.clear`, and only when editing -- a branch being created
     * has no openings to clear.
     */
    val canClearOpening: Boolean = false,
    val confirmClearOpening: Boolean = false,
    val isClearingOpening: Boolean = false,
    /** The server's count of what was cleared, shown until the screen leaves. */
    val clearedOpeningMessage: String? = null,
    /**
     * Withdrawing every voucher the branch holds from the books. Carries a
     * permission of its own (`branch.transaction.clear`) rather than riding
     * along on the one that clears openings.
     */
    val canClearTransactions: Boolean = false,
    val confirmClearTransactions: Boolean = false,
    val isClearingTransactions: Boolean = false,
    val clearedTransactionsMessage: String? = null,
    /**
     * A refusal the server marked severity "info" — a normal answer naming who
     * to call, not a fault. Shown inline in the notice voice, never as the red
     * snackbar error, and never both.
     */
    val clearNotice: String? = null,
    /**
     * The wizard for this user — [BranchForm.stepsFor]. The SaaS step is absent
     * for anyone who cannot reach anything on it.
     */
    val steps: List<BranchStep> = BranchForm.steps,
) {
    val isEditing: Boolean get() = branchId != null

    /**
     * Both clears show under the same two conditions: the permission, and a
     * branch still marked "Opening ongoing?". Past that point the branch is
     * trading for real and a wholesale clear is no longer a correction.
     */
    val showClearOpening: Boolean get() = canClearOpening && isEditing && flag("is_opening")

    val showClearTransactions: Boolean get() = canClearTransactions && isEditing && flag("is_opening")

    /** Either clear in flight disables both buttons. */
    val clearInFlight: Boolean get() = isClearingOpening || isClearingTransactions

    val screenTitle: String get() = if (isEditing) "Edit Branch" else "Add Branch"

    val saveLabel: String get() = if (isEditing) "Update Branch" else "Save Branch"

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
        BranchOptions.INVENTORY_SYSTEM -> inventorySystems
        BranchOptions.PAPER_SIZE -> paperSizes
        BranchOptions.STATUS -> BranchForm.statusOptions
        BranchOptions.PAD_HEADING -> BranchForm.padHeadingOptions
        BranchOptions.PRINT_SIZE -> BranchForm.printSizeOptions
        BranchOptions.MONEY_FORMAT -> BranchForm.moneyFormatOptions
        BranchOptions.PAD_PRINT_MODE -> BranchForm.padPrintModeOptions
        BranchOptions.DOWN_PAYMENT_BASE -> BranchForm.downPaymentBaseOptions
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
