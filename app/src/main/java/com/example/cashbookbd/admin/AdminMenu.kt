package com.example.cashbookbd.admin

import com.example.cashbookbd.session.MenuPermissions
import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/**
 * One entry in the Admin menu, mirroring the web sidebar's "Admin" group.
 * [supported] is false for entries with no mobile equivalent yet (the lists,
 * role CRUD, orders, uploads) — they appear but open a "coming soon" screen.
 */
data class AdminItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
    val supported: Boolean,
)

/** The Admin menu registry and its permission rules. */
object AdminMenu {

    private val USER_VIEW = listOf("all.user.view", "user.view")

    val all: List<AdminItem> = listOf(
        AdminItem("branchList", "Branch List", listOf("branch.view"), supported = true),
        AdminItem("companyList", "Company List", listOf("branch.view"), supported = true),
        AdminItem("softwareInfo", "Software Information", listOf("branch.view"), supported = false),
        AdminItem("userList", "User List", USER_VIEW, supported = true),
        AdminItem("onlineUsers", "Online Users", USER_VIEW, supported = true),
        AdminItem("companyUser", "Company User", USER_VIEW, supported = true),
        AdminItem(
            "resellers", "Resellers",
            listOf("reseller.view", "subscription.view", "all.user.view"), supported = false,
        ),
        AdminItem("roles", "Roles", listOf("roles.view"), supported = true),
        AdminItem("addRoles", "Add Roles", listOf("roles.create"), supported = false),
        AdminItem("dayClose", "Day Close", listOf("dayclose.create"), supported = true),
        AdminItem("addGroupReport", "Add Group Report", listOf("group.report"), supported = false),
        AdminItem("orders", "Orders", listOf("order.view"), supported = true),
        AdminItem("orderWithTransaction", "Order With Transaction", listOf("order.view"), supported = false),
        AdminItem("averagePrice", "Average Price", listOf("order.avg.price"), supported = false),
        AdminItem("approvalCenter", "Approval Center", listOf("voucher.approval", "attendance.view"), supported = false),
        AdminItem("voucherApproval", "Voucher Approval", listOf("voucher.approval"), supported = true),
        AdminItem("approvalRemove", "Approval Remove", listOf("remove.approval"), supported = true),
        AdminItem("changeVoucherType", "Change Voucher Type", listOf("change.vourcher.type"), supported = true),
        AdminItem("voucherUpload", "Voucher Upload", listOf("voucher.photo.upload"), supported = false),
        AdminItem("bulkUpload", "Bulk Upload", listOf("bulk.photo.upload"), supported = false),
    )

    private val byKey: Map<String, AdminItem> = all.associateBy { it.key }

    fun byKey(key: String?): AdminItem? = key?.let { byKey[it] }

    /** True when the user can see the Admin parent section at all. */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        MenuPermissions.hasMenu(permissions, "admin")

    /** Admin entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<AdminItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
