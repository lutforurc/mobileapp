package com.example.cashbookbd.inventory

/** Which of the three inventory-movement forms a spec drives. */
enum class InventoryFormKind { BRANCH_ISSUE, BRANCH_RECEIVE, MATERIAL_ISSUE }

/**
 * One inventory-movement form's fixed wiring: its menu key/title, permission
 * gate, store endpoint and the header labels that differ between the transfer
 * variants (issue sends stock out, receive books it in).
 */
data class InventoryFormSpec(
    val key: String,
    val title: String,
    val anyOf: List<String>,
    val kind: InventoryFormKind,
    /** The POST store endpoint (relative to BASE_URL). */
    val endpoint: String,
    val dateLabel: String,
    val fromLabel: String = "",
    val toLabel: String = "",
)

/**
 * The inventory-movement form registry, keyed like the other menu registries
 * ([com.example.cashbookbd.transaction.TransactionMenu] et al.). Ports the web's
 * Branch Transfer / Warehouse Received / Material Issue entry forms.
 */
object InventoryForms {

    val branchIssue = InventoryFormSpec(
        key = "branchIssue",
        title = "Branch Issue",
        anyOf = listOf(
            "branch.transfer.create",
            "inventory.transfer.create",
            "product.transfer.create",
        ),
        kind = InventoryFormKind.BRANCH_ISSUE,
        endpoint = "warehouse/transfer/issue",
        dateLabel = "Challan Date",
        fromLabel = "From Branch",
        toLabel = "To Branch",
    )

    val branchReceive = InventoryFormSpec(
        key = "branchReceive",
        title = "Branch Receive",
        anyOf = listOf(
            "branch.received.create",
            "inventory.received.create",
            "product.received.create",
        ),
        kind = InventoryFormKind.BRANCH_RECEIVE,
        endpoint = "warehouse/transfer/receive",
        dateLabel = "Receive Date",
        fromLabel = "From Branch",
        toLabel = "Receive Branch",
    )

    val materialIssue = InventoryFormSpec(
        key = "materialIssue",
        title = "Material Issue",
        anyOf = listOf(
            "material.issue.create",
            "inventory.issue.create",
            "purchase.create",
        ),
        kind = InventoryFormKind.MATERIAL_ISSUE,
        endpoint = "material-issue/store",
        dateLabel = "Issue Date",
    )

    val all: List<InventoryFormSpec> = listOf(branchIssue, branchReceive, materialIssue)

    private val byKey: Map<String, InventoryFormSpec> = all.associateBy { it.key }

    fun byKey(key: String?): InventoryFormSpec? = key?.let { byKey[it] }
}
