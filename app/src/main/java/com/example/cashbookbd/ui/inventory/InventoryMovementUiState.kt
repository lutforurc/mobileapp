package com.example.cashbookbd.ui.inventory

import com.example.cashbookbd.data.repository.BranchTransferLine
import com.example.cashbookbd.data.repository.InventoryProduct
import com.example.cashbookbd.data.repository.MaterialIssueLine
import com.example.cashbookbd.inventory.InventoryFormKind
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

/**
 * Branch Receive's extra first "From Branch" option: receiving into the same
 * branch the stock left from is booked with a null `from_branch_id`, and the
 * from≠to rule doesn't apply.
 */
val SELF_BRANCH_OPTION = SelectorOption(id = "", label = "Self Branch")

data class InventoryMovementUiState(
    val title: String = "",
    val kind: InventoryFormKind = InventoryFormKind.BRANCH_ISSUE,
    val isSupported: Boolean = true,
    val dateLabel: String = "Date",
    val fromLabel: String = "From Branch",
    val toLabel: String = "To Branch",

    val date: SimpleDate = SimpleDate.today(),

    // Branch Issue / Receive header. On issue "from" is the user's protected
    // branches and "to" is every branch; on receive it's the reverse, with
    // [SELF_BRANCH_OPTION] prepended to "from".
    val fromOptions: List<SelectorOption> = emptyList(),
    val toOptions: List<SelectorOption> = emptyList(),
    val fromBranch: SelectorOption? = null,
    val toBranch: SelectorOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,
    val challanNumber: String = "",
    val receiverName: String = "",
    val receiverMobile: String = "",
    // The web's one Transport input became Driver Name + Driver Mobile —
    // together they are one thing, but the server reads them apart.
    val driverName: String = "",
    val driverMobile: String = "",
    val note: String = "",

    // Material Issue header.
    val warehouses: List<SelectorOption> = emptyList(),
    val selectedWarehouse: SelectorOption? = null,
    val projects: List<SelectorOption> = emptyList(),
    val selectedProject: SelectorOption? = null,
    val receivedBy: String = "",

    // Current (not-yet-added) line entry.
    val product: InventoryProduct? = null,
    val qty: String = "",
    val damagedQty: String = "",
    val shortQty: String = "",
    val building: String = "",
    val workItem: String = "",
    val lineNote: String = "",

    // The pending batch — only the variant's own list is ever populated.
    val transferLines: List<BranchTransferLine> = emptyList(),
    val materialLines: List<MaterialIssueLine> = emptyList(),

    /** Non-null shows the stock-shortage "Transfer anyway?" confirm dialog. */
    val shortages: List<String>? = null,

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val isMaterial: Boolean get() = kind == InventoryFormKind.MATERIAL_ISSUE

    /** Batch total (qty × display rate) for the transfer variants. */
    val total: Double get() = transferLines.sumOf { it.amount }

    /** "Self Branch" (empty id) sends `from_branch_id` null and skips from≠to. */
    val isSelfFrom: Boolean get() = fromBranch != null && fromBranch.id.isEmpty()

    /** Mobile is optional, but a typed one must be 6–32 digits. */
    val mobileOk: Boolean get() = receiverMobile.isBlank() || receiverMobile.length in 6..32

    private val branchesOk: Boolean
        get() = toBranch != null && fromBranch != null &&
            (isSelfFrom || fromBranch.id != toBranch.id)

    val canAdd: Boolean
        get() = product != null && (qty.toDoubleOrNull() ?: 0.0) > 0.0

    val canSave: Boolean
        get() = !isSubmitting && if (isMaterial) {
            materialLines.isNotEmpty() && selectedProject != null
        } else {
            transferLines.isNotEmpty() && branchesOk && mobileOk
        }
}
