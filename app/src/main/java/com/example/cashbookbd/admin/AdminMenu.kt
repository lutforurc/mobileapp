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

    /** Routes to the dedicated Highlight Rules screen (see AdminHomeScreen). */
    const val HIGHLIGHT_RULES_KEY = "highlightRules"

    /** Routes to the dedicated User List screen (list + edit + temp password). */
    const val USER_LIST_KEY = "userList"

    /** Routes to the dedicated Role & Permission screen (role picker + toggles). */
    const val ROLES_KEY = "roles"

    /** Routes to the Add Role form. */
    const val ADD_ROLES_KEY = "addRoles"

    private val USER_VIEW = listOf("all.user.view", "user.view")

    val all: List<AdminItem> = listOf(
        // Each item is gated on its OWN web-sidebar permission, not a broad
        // branch.view / all.user.view (which was leaking items into the menu).
        AdminItem("companyList", "Company List", listOf("company.view"), supported = true),
        AdminItem("branchList", "Branch List", listOf("branch.view"), supported = true),
        AdminItem("softwareInfo", "Software Information", listOf("software.information"), supported = true),
        AdminItem("userList", "User List", USER_VIEW, supported = true),
        AdminItem("onlineUsers", "Online Users", listOf("online.users", "user.view"), supported = true),
        AdminItem("companyUser", "Company User", listOf("company.user", "user.view"), supported = true),
        AdminItem(
            "resellers", "Resellers",
            listOf("reseller.view", "subscription.view", "all.user.view"), supported = false,
        ),
        // Platform broadcasts; the server additionally restricts to company 1.
        AdminItem(
            "adminNotifications", "Admin Notifications",
            listOf("reseller.view", "subscription.view", "all.user.view"), supported = true,
        ),
        AdminItem("smsLogs", "SMS Logs", listOf("sms.logs"), supported = true),
        AdminItem("smsTemplates", "SMS Templates", listOf("sms.templates"), supported = true),
        // Same slot as the web sidebar (just before Roles); gated on highlight.rules.
        AdminItem(HIGHLIGHT_RULES_KEY, "Highlight Rules", listOf("highlight.rules"), supported = true),
        AdminItem("roles", "Roles", listOf("roles.view"), supported = true),
        AdminItem("addRoles", "Add Roles", listOf("roles.create"), supported = true),
        // Global Spatie permissions; the server restricts writes to company 1.
        AdminItem("addPermission", "Add Permission", listOf("roles.create"), supported = true),
        AdminItem("dayClose", "Day Close", listOf("dayclose.create"), supported = true),
        AdminItem("addGroupReport", "Add Group Report", listOf("group.report"), supported = true),
        AdminItem("orders", "Orders", listOf("order.view"), supported = true),
        AdminItem("orderWithTransaction", "Order With Transaction", listOf("order.view"), supported = true),
        AdminItem("averagePrice", "Average Price", listOf("order.avg.price"), supported = true),
        AdminItem("approvalCenter", "Approval Center", listOf("approval.center"), supported = false),
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
