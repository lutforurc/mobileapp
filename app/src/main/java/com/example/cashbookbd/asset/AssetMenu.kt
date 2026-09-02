package com.example.cashbookbd.asset

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/** One entry of the Assets menu (the web sidebar's `asset` group). */
data class AssetItem(
    val key: String,
    val title: String,
    /** What this entry is for, shown under the title on the section home. */
    val hint: String,
    val anyOf: List<String>,
    /** false renders the entry greyed with "not available yet", as elsewhere. */
    val supported: Boolean = true,
)

/**
 * The web's Fixed Assets group. One sidebar child there ("Categories & Register")
 * opens a screen of seven tabs; on a phone each tab is its own screen, so the
 * tabs are listed here in the web's order — the order of the job.
 *
 * ⚠️ CATEGORIES COME FIRST because an asset cannot be entered without one: the
 * rate it wears out at comes from there. Depreciation is last of the accounting
 * three because it cannot be done until the other two are.
 *
 * ⚠️ PERMISSION ALONE, never a business type. Owning a lorry is not a line of
 * business, and the web gates this group the same way. Handovers and the count
 * answer to the REGISTER's permission rather than the one that writes vouchers:
 * whoever walks the building with a phone is not the person who posts the
 * yearly charge, and requiring that permission would put the count in the hands
 * of the one person too busy to do it.
 *
 * The route strings live here too, so the menu entry and the screen it opens
 * cannot drift apart.
 */
object AssetMenu {

    const val CATEGORIES_KEY = "assetCategories"
    const val REGISTER_KEY = "assetRegister"
    const val CWIP_KEY = "assetCwip"
    const val DEPRECIATION_KEY = "assetDepreciation"
    const val SCHEDULE_KEY = "assetSchedule"
    const val HANDOVERS_KEY = "assetHandovers"
    const val VERIFICATION_KEY = "assetVerification"

    const val PERM_CATEGORY_VIEW = "asset.category.view"
    const val PERM_REGISTER_VIEW = "asset.register.view"
    const val PERM_DEPRECIATION_RUN = "asset.depreciation.run"

    // ---- Routes -------------------------------------------------------------
    // Declared here rather than in the navigation graph's Routes object so that
    // an entry and its destination are written down once, together.

    const val ROUTE_HOME = "asset/home"
    const val ROUTE_CATEGORIES = "asset/categories"

    /** `id` of 0 means "a new one" — the argument is always present. */
    const val ARG_ID = "id"
    const val ARG_BRANCH = "branch"
    const val ARG_ASSET_ID = "assetId"

    const val ROUTE_CATEGORY_FORM = "asset/categories/form?id={id}"
    fun categoryForm(id: Long?): String = "asset/categories/form?id=${id ?: 0L}"

    const val ROUTE_REGISTER = "asset/register"
    const val ROUTE_REGISTER_FORM = "asset/register/form?id={id}&branch={branch}"
    fun registerForm(id: Long?, branchId: Long?): String =
        "asset/register/form?id=${id ?: 0L}&branch=${branchId ?: 0L}"

    const val ROUTE_DISPOSAL = "asset/register/dispose/{assetId}"
    fun disposal(assetId: Long): String = "asset/register/dispose/$assetId"

    const val ROUTE_CARE = "asset/care/{assetId}"
    fun care(assetId: Long): String = "asset/care/$assetId"

    const val ROUTE_DEPRECIATION = "asset/depreciation"
    const val ROUTE_SCHEDULE = "asset/schedule"
    const val ROUTE_HANDOVERS = "asset/handovers"
    const val ROUTE_VERIFICATION = "asset/verification"

    val all: List<AssetItem> = listOf(
        AssetItem(CATEGORIES_KEY, "Categories", "Rates and heads", listOf(PERM_CATEGORY_VIEW)),
        AssetItem(REGISTER_KEY, "Register", "What the company owns", listOf(PERM_REGISTER_VIEW)),
        // A thing being built is not in the register yet and is not depreciated
        // yet — so it sits between the two. Web only for the moment.
        AssetItem(
            CWIP_KEY, "Under construction", "Not an asset yet",
            listOf(PERM_REGISTER_VIEW), supported = false,
        ),
        AssetItem(DEPRECIATION_KEY, "Depreciation", "The yearly charge", listOf(PERM_DEPRECIATION_RUN)),
        AssetItem(SCHEDULE_KEY, "Schedule", "The year-end note", listOf(PERM_REGISTER_VIEW)),
        AssetItem(HANDOVERS_KEY, "Handovers", "Who has what", listOf(PERM_REGISTER_VIEW)),
        AssetItem(VERIFICATION_KEY, "Verification", "Is it still there", listOf(PERM_REGISTER_VIEW)),
    )

    /** The web's `asset` menuPermissions key, verbatim. */
    val PARENT_PERMISSIONS: List<String> = listOf(PERM_CATEGORY_VIEW, PERM_REGISTER_VIEW)

    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        Permissions.hasAny(permissions, PARENT_PERMISSIONS + all.flatMap { it.anyOf })

    fun visible(permissions: List<Permission>?): List<AssetItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
