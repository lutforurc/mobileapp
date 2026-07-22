package com.example.cashbookbd.session

/**
 * The current user's app settings, loaded from `POST /settings/get-settings`.
 *
 * [permissions] drives all client-side gating. [inventorySystemId] is the
 * current branch's inventory system (1 general, 2 electronics, 3 construction,
 * 4 trading), which selects the Purchase/Sales invoice variant the same way the
 * web's index components switch on `currentBranch.inventory_system_id`.
 * [businessTypeId] remains only for the dashboard variant. More company/branch/
 * feature settings can be added here as the app needs them.
 */
data class Settings(
    val permissions: List<Permission> = emptyList(),
    val businessTypeId: Int? = null,
    val inventorySystemId: Int? = null,
    /** The signed-in user's branch id — e.g. the Head Office cash received form's default receiving branch. */
    val branchId: Long? = null,
    /** Branch category (`branch_types_id`): 1 = head office (forces the Head Office cash payment variant). */
    val branchTypesId: Int? = null,
    /** Signed-in user's display name, shown in the account menu header. */
    val userName: String? = null,
    val userEmail: String? = null,
    /** Absolute URL of the user's photo; null falls back to the avatar icon. */
    val userPhotoUrl: String? = null,
    /** Branch transaction date, pre-formatted dd/MM/yyyy by the backend. */
    val transactionDate: String? = null,
    /**
     * How many fraction digits the current branch shows on amounts. Null when the
     * branch has none set; [com.example.cashbookbd.core.AmountFormat] falls back
     * to its default. Drives every transaction figure in the app.
     */
    val decimalPlaces: Int? = null,
)