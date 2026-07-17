package com.example.cashbookbd.vrsettings

/** Which VR-settings action a form performs (chooses UI + payload). */
enum class VrKind {
    /** `{voucher_no, confirm}` → soft-delete a voucher (two-step confirm). */
    VOUCHER_DELETE,

    /** `{voucher_no}` → delete a voucher's installments. */
    INSTALLMENT_DELETE,

    /** `{branch_id, voucher_type, present_date, change_date, start_voucher, end_voucher}`. */
    DATE_CHANGE,
}

/** A VR-settings action form. */
data class VrSettingsSpec(
    val key: String,
    val title: String,
    val kind: VrKind,
    val endpoint: String,
    val actionLabel: String,
)

/** Registry of the buildable VR-settings action forms (the read-only lists aren't here). */
object VrSettingsForms {

    val all: List<VrSettingsSpec> = listOf(
        VrSettingsSpec(
            key = "voucherDelete",
            title = "Voucher Delete",
            kind = VrKind.VOUCHER_DELETE,
            endpoint = "voucher-settings/destroy",
            actionLabel = "Delete Voucher",
        ),
        VrSettingsSpec(
            key = "installmentDelete",
            title = "Installment Delete",
            kind = VrKind.INSTALLMENT_DELETE,
            endpoint = "voucher-settings/installment-destroy",
            actionLabel = "Delete Installment",
        ),
        VrSettingsSpec(
            key = "voucherDateChange",
            title = "Voucher Date Change",
            kind = VrKind.DATE_CHANGE,
            endpoint = "admin/voucher/date-change",
            actionLabel = "Change Date",
        ),
    )

    private val byKey: Map<String, VrSettingsSpec> = all.associateBy { it.key }

    fun byKey(key: String?): VrSettingsSpec? = key?.let { byKey[it] }
}
