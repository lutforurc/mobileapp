package com.example.cashbookbd.session

/**
 * Maps each parent menu to the child permissions it contains, mirroring the web
 * app's `menuPermissions.ts`. A parent menu is visible when the user holds *any*
 * of its child permissions — see [hasMenu].
 */
object MenuPermissions {

    val map: Map<String, List<String>> = mapOf(
        "transaction" to listOf(
            // The four accounts screens the web hangs here (acd3d4f5).
            "bank.reconciliation.view",
            "cheque.register.view",
            "year.closing.run",
            "budget.view",
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
        // The Labour Items master-data group (web menuPermissions.ts, b1cfc84).
        "labour_items" to listOf("labour.category.view", "labour.item.view"),
        // The fixed-asset register (web menuPermissions.ts:168) — permission
        // alone, never a business type; granted to nobody until --asset-grant.
        "asset" to listOf("asset.category.view", "asset.register.view"),
        "reports" to listOf(
            // The two accounts reports the web lists first (acd3d4f5).
            "ageing.report.view",
            "audit.trail.view",
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
        // The web gates the Products menu on products.view alone, but its sidebar
        // items (Brand/Category/Product/Unit) each carry their own permission. The
        // mobile Products section surfaces all four, so the parent is the union —
        // a superset of the web gate — to avoid orphaning an item the user can open.
        "products" to listOf("products.view", "brand.list", "category.view", "product.unit"),
        "admin" to listOf(
            "check.register.view",
            "branch.view",
            "company.view",
            "software.information",
            "all.user.view",
            "user.view",
            "online.users",
            "user.login.log",
            "product.tracking.settings.view",
            "company.user",
            "highlight.rules",
            "dayclose.create",
            "group.report",
            "order.view",
            "order.avg.price",
            "approval.center",
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
            "subscription.view",
        ),
        "voucher_settings" to listOf(
            "voucher.delete",
            "installment.delete",
            "voucher.date.change",
            "voucher.recycle",
            "voucher.history",
            "log.changes",
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
            // Bank Opening lives in this menu but answers to a permission of its
            // own, so whoever holds only that one still gets the menu it sits in.
            "bank.opening.view",
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