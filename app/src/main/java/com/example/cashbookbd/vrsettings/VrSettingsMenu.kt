package com.example.cashbookbd.vrsettings

import com.example.cashbookbd.session.MenuPermissions
import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/**
 * One entry in the "VR Settings" (voucher settings) menu, mirroring the web
 * sidebar. [supported] is false for entries with no mobile equivalent yet
 * (the read-only lists) — they appear but open a "coming soon" screen.
 */
data class VrSettingsItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
    val supported: Boolean,
)

/** The VR Settings menu registry and its permission rules. */
object VrSettingsMenu {

    val all: List<VrSettingsItem> = listOf(
        VrSettingsItem("voucherDelete", "Voucher Delete", listOf("voucher.delete"), supported = true),
        VrSettingsItem("installmentDelete", "Installment Delete", listOf("installment.delete"), supported = true),
        VrSettingsItem("voucherDateChange", "Voucher Date Change", listOf("voucher.date.change"), supported = true),
        VrSettingsItem("recycleBin", "Recycle Bin", listOf("voucher.recycle"), supported = true),
        VrSettingsItem("history", "History", listOf("voucher.history"), supported = false),
        VrSettingsItem("logChanges", "Log Changes", listOf("voucher.changes"), supported = true),
    )

    private val byKey: Map<String, VrSettingsItem> = all.associateBy { it.key }

    fun byKey(key: String?): VrSettingsItem? = key?.let { byKey[it] }

    /** True when the user can see the VR Settings parent section at all. */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        MenuPermissions.hasMenu(permissions, "voucher_settings")

    /** VR Settings entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<VrSettingsItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
