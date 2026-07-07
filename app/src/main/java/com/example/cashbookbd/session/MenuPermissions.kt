package com.example.cashbookbd.session

/**
 * Maps each parent menu to the child permissions it contains, mirroring the web
 * app's `menuPermissions.ts`. A parent menu is visible when the user holds *any*
 * of its child permissions — see [hasMenu].
 */
object MenuPermissions {

    val map: Map<String, List<String>> = mapOf(
        "transaction" to listOf(
            "cash.received.create",
            "cash.payment.create",
            "bank.received.create",
            "bank.payment.create",
            "hrm.loan.create",
            "journal.create",
            "branch.transfer.create",
            "branch.received.create",
            "inventory.transfer.create",
            "inventory.received.create",
            "product.transfer.create",
            "product.received.create",
        ),
        "invoice" to listOf("purchase.create", "sales.create", "labour.invoice.create"),
        "reports" to listOf(
            "cashbook.view",
            "installment.create",
            "ledger.view",
            "ledger.labour",
            "ledger.due.view",
            "date.wise.total",
            "product.stock.view",
            "product.in.out",
            "purchase.ledger",
            "sales.ledger",
            "group.report",
            "mitch.match",
            "productwise.profit",
        ),
        "requisition" to listOf(
            "requisition.view",
            "requisition.create",
            "requisition.comparison",
        ),
        "products" to listOf("products.view"),
        "admin" to listOf(
            "check.register.view",
            "branch.view",
            "all.user.view",
            "dayclose.create",
            "order.view",
            "order.avg.price",
            "voucher.approval",
            "remove.approval",
            "change.vourcher.type",
            "voucher.photo.upload",
            "voucher.photo.delete",
            "bulk.photo.upload",
            "roles.view",
            "roles.create",
            "roles.edit",
            "roles.delete",
            "reseller.view",
        ),
        "voucher_settings" to listOf(
            "voucher.delete",
            "installment.delete",
            "voucher.date.change",
            "voucher.recycle",
            "voucher.history",
            "voucher.changes",
        ),
        "hrm" to listOf(
            "employee.view",
            "attendance.view",
            "attendance.create",
            "attendance.approve",
            "leave.view",
            "leave.approve",
            "holiday.view",
            "shift.view",
            "salary.generate",
            "salary.sheet.view",
            "employee.loan.ledger.view",
        ),
        "roles" to listOf("roles.view", "roles.create", "roles.edit", "roles.delete"),
        "customer" to listOf(
            "cs.delete",
            "cs.edit",
            "cs.information",
            "cs.ledger",
            "cs.photo.delete",
            "cs.photo.edit",
            "cs.photo.update",
            "cs.photo.view",
            "cs.update",
            "cs.view",
            "coa.l1.view",
            "coa.l2.view",
            "coa.l3.view",
            "coa.l4.view",
        ),
        "chart_of_accounts" to listOf("coa.l1.view", "coa.l2.view", "coa.l3.view", "coa.l4.view"),
        "analytics" to listOf("analytics.comparison"),
        "reseller" to listOf("reseller.dashboard.view"),
        "subscription_history" to listOf("subscription.view", "subscription.history"),
    )

    /** True when the user holds any child permission of [menuKey]. */
    fun hasMenu(permissions: List<Permission>?, menuKey: String): Boolean =
        Permissions.hasAny(permissions, map[menuKey].orEmpty())
}