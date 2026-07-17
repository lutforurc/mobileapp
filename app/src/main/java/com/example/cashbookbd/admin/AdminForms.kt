package com.example.cashbookbd.admin

/** Which admin action a form performs (chooses UI + payload). */
enum class AdminKind {
    /** `{current_date, next_date}` (dd/MM/yyyy) — close the day, advance the date. */
    DAY_CLOSE,

    /** `{start_date, end_date}` (yyyy-MM-dd) — approve all vouchers in a range. */
    VOUCHER_APPROVAL,

    /** `{remove_for_approval}` — un-approve a voucher by number. */
    APPROVAL_REMOVE,

    /** `{id, branch_id, voucher_type, voucher_number}` — change a voucher's type. */
    CHANGE_VOUCHER_TYPE,
}

/** An admin action form. */
data class AdminFormSpec(
    val key: String,
    val title: String,
    val kind: AdminKind,
    val endpoint: String,
    val actionLabel: String,
)

/** Registry of the buildable admin action forms (the lists/uploads aren't here). */
object AdminForms {

    val all: List<AdminFormSpec> = listOf(
        AdminFormSpec(
            key = "dayClose",
            title = "Day Close",
            kind = AdminKind.DAY_CLOSE,
            endpoint = "admin/dayclose",
            actionLabel = "Close Day",
        ),
        AdminFormSpec(
            key = "voucherApproval",
            title = "Voucher Approval",
            kind = AdminKind.VOUCHER_APPROVAL,
            endpoint = "admin/voucher/voucher-approval-all",
            actionLabel = "Approve Vouchers",
        ),
        AdminFormSpec(
            key = "approvalRemove",
            title = "Approval Remove",
            kind = AdminKind.APPROVAL_REMOVE,
            endpoint = "admin/voucher/remove-approval",
            actionLabel = "Remove Approval",
        ),
        AdminFormSpec(
            key = "changeVoucherType",
            title = "Change Voucher Type",
            kind = AdminKind.CHANGE_VOUCHER_TYPE,
            endpoint = "admin/voucher/voucher-type-change",
            actionLabel = "Change Type",
        ),
    )

    private val byKey: Map<String, AdminFormSpec> = all.associateBy { it.key }

    fun byKey(key: String?): AdminFormSpec? = key?.let { byKey[it] }
}
