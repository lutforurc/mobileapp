package com.example.cashbookbd.inventory

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/** One entry of the Branch Transfer menu. */
data class BranchTransferItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
)

/**
 * The web's "Branch Transfer" sidebar group: the three movement forms, the
 * challan register, and the three transfer reports — everything about stock
 * crossing a branch line, in one place instead of scattered between Invoice
 * and Reports (where the web itself kept them until 2026-08-02).
 */
object BranchTransferMenu {

    // The movement forms keep the keys InventoryMovementScreen dispatches on.
    const val BRANCH_ISSUE_KEY = "branchIssue"
    const val BRANCH_RECEIVE_KEY = "branchReceive"
    const val MATERIAL_ISSUE_KEY = "materialIssue"
    const val TRANSFER_LIST_KEY = "transferList"

    val all: List<BranchTransferItem> = listOf(
        BranchTransferItem(
            BRANCH_ISSUE_KEY, "Branch Issue",
            listOf("branch.transfer.create", "inventory.transfer.create", "product.transfer.create"),
        ),
        BranchTransferItem(
            BRANCH_RECEIVE_KEY, "Branch Receive",
            listOf("branch.received.create", "inventory.received.create", "product.received.create"),
        ),
        BranchTransferItem(
            MATERIAL_ISSUE_KEY, "Material Issue",
            listOf("material.issue.create", "inventory.issue.create", "purchase.create"),
        ),
        BranchTransferItem(
            TRANSFER_LIST_KEY, "Branch Transfer List",
            listOf("branch.transfer.create", "branch.received.create"),
        ),
        // The three reports ride the generic report engine by their config keys.
        BranchTransferItem(
            "branchTransferReport", "Branch Transfer Report",
            listOf("branch.transfer.create"),
        ),
        BranchTransferItem(
            "branchReceiveReport", "Branch Receive Report",
            listOf("branch.received.create"),
        ),
        BranchTransferItem(
            "branchStockReport", "Branch Stock",
            listOf("product.stock.view"),
        ),
    )

    private val byKey: Map<String, BranchTransferItem> = all.associateBy { it.key }

    fun byKey(key: String?): BranchTransferItem? = key?.let { byKey[it] }

    /**
     * The web gates the group on the transfer/receive permissions alone —
     * someone with only product.stock.view keeps Branch Stock reachable
     * through nothing (as on the web, where the section hides).
     */
    private val PARENT_PERMISSIONS = listOf(
        "branch.transfer.create", "inventory.transfer.create", "product.transfer.create",
        "branch.received.create", "inventory.received.create", "product.received.create",
    )

    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        Permissions.hasAny(permissions, PARENT_PERMISSIONS)

    /** Entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<BranchTransferItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
