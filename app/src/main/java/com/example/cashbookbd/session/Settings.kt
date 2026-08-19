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
    /**
     * The signed-in user's numeric id. Gates the User List's temporary-password
     * action, which the web shows only for the global super admin (`user.id === 1`).
     */
    val userId: Long? = null,
    /**
     * The colours this user picked for themselves (api a7484e3e..f6f353f5,
     * `user_theme_settings`, one row per user, riding the session user in
     * get-settings). Per user, not per branch — taste, not policy. One set
     * per mode, because a colour that reads on a light screen rarely reads
     * on a dark one. Empty fields mean "the software's own colour".
     */
    val userThemeLight: UserThemeMode = UserThemeMode(),
    val userThemeDark: UserThemeMode = UserThemeMode(),
    // (UserThemeMode is declared at the bottom of this file.)
    val businessTypeId: Int? = null,
    val inventorySystemId: Int? = null,
    /** The signed-in user's branch id — e.g. the Head Office cash received form's default receiving branch. */
    val branchId: Long? = null,
    /** Branch category (`branch_types_id`): 1 = head office (forces the Head Office cash payment variant). */
    val branchTypesId: Int? = null,
    /** Branch setting: the Combined Invoice shows its notes apply-to (Both/Purchase/Sales) switch. */
    val combinedInvoiceNote: Boolean = false,
    /**
     * Branch flag `is_opening` ("Opening ongoing"): the business is still keying
     * in its opening balances. The Product List then offers the per-row opening
     * stock entry (IMEI/qty/rate), exactly as the web shows its inline columns
     * only while `branch.is_opening == 1`.
     */
    val openingOngoing: Boolean = false,
    /**
     * Branch column `warranty_controll` (server's spelling): the product forms
     * carry the warranty/guarantee type and days fields.
     */
    val warrantyControll: Boolean = false,
    /**
     * Branch column `use_bangla`: the branch keeps its ledgers in Bangla, so the
     * customer form asks for a Bangla name beside the English one.
     */
    val useBangla: Boolean = false,
    /** Branch column `stock_report_type`: product cells carry a category prefix. */
    val stockReportTypeGrouped: Boolean = false,
    /** Branch meta: shows the tutorial-video links on the list screens. */
    val needDemoTutorial: Boolean = false,
    /**
     * `screen_key -> walkthrough URL` for this app, as the settings payload
     * sends it. The links used to be compiled in; they are the operator's to
     * change now, and a screen with no row here simply shows no link.
     *
     * These are the app's own recordings, not the browser's — the table keeps a
     * column for each and the payload hands each app only its own.
     */
    val tutorialVideos: Map<String, String> = emptyMap(),
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
    /** Branch meta: the customer form shows the Select Area field. */
    val needCustomerArea: Boolean = false,
    /** Branch meta: the customer form shows the Sex field. */
    val needCustomerSex: Boolean = false,
    /** Branch meta: the customer form shows Date of Birth. */
    val needCustomerDateOfBirth: Boolean = false,
    /** Branch meta: the customer form shows Occupation (free text). */
    val needCustomerOccupation: Boolean = false,
    /** Branch meta: the customer form shows Permanent Address. */
    val needCustomerPermanentAddress: Boolean = false,
    /** Branch meta: the customer form shows the Photo field. */
    val needCustomerPhoto: Boolean = false,
    /** Branch meta: the customer form shows the National ID field. */
    val needCustomerNationalId: Boolean = false,
    /** Branch meta: a nominee row also carries a photo. */
    val needNomineePhoto: Boolean = false,
    /** Branch meta: the customer form shows Mother's Name. */
    val needCustomerMotherName: Boolean = false,
    /** Branch meta: the customer form shows Contact Person + Contact Number. */
    val needCustomerContactPerson: Boolean = false,
    /** Branch meta: the customer form shows Relation + Relation's Name. */
    val needRelationInfo: Boolean = false,
    /** Branch meta: the customer form carries the Guarantor panel. */
    val haveIsGuaranter: Boolean = false,
    /** Branch meta: the customer form carries the Nominee panel. */
    val haveCustomerNominee: Boolean = false,
    /** Branch COLUMN (numeric): the customer form shows Customer Number (idfr_code). */
    val haveCustomerSl: Boolean = false,
    /** Branch meta: one order may carry several products (multi-product grid). */
    val multiProductOrder: Boolean = false,
    /** Branch meta: ledger/cashbook reports show the voucher image column. */
    val showVoucherImage: Boolean = false,
    /**
     * True when the API runs with APP_ENV=local — voucher-image URLs then skip
     * the `/public` path segment, matching the web's ImagePopup rule.
     */
    val isLocalEnv: Boolean = false,
    /**
     * Branch meta: what an allotment letter's reference number starts with
     * (e.g. "BST/ALLOT") — the Generate dialog suggests prefix/year/serial.
     * Null when unset (the server derives from the project's initials).
     */
    val letterRefPrefix: String? = null,
    /**
     * Branch meta: how mobile numbers are grouped wherever one is READ rather
     * than entered ("#" = a digit, all else literal — "#####-######" shows
     * 01973-190490). Empty = shown exactly as stored. Display only; what is
     * stored, dialled and texted stays the bare digits.
     */
    val mobileNumberFormat: String = "",
    /**
     * Branch meta: the date the branch's letters carry (yyyy-MM-dd). Null
     * offers the day the letter is issued instead.
     */
    val letterRefDate: String? = null,
)

/**
 * One mode's worth of a user's own colours, as "#RRGGBB" strings straight off
 * the wire — parsed into [androidx.compose.ui.graphics.Color] only at the
 * theme layer, so a malformed value falls back to the shipped palette there
 * rather than crashing a parse here. Null/blank = "the software's own colour".
 *
 * Deliberately a subset of what the web stores: the roles that translate to
 * this app's [com.example.cashbookbd.ui.theme.BrandPalette] cleanly. The
 * web-furniture ones (sidebar/header/table-header), the text inks (contrast
 * is this app's own responsibility) and the control height/radius stay
 * web-only.
 */
data class UserThemeMode(
    val primary: String? = null,
    val secondary: String? = null,
    val success: String? = null,
    val danger: String? = null,
    val warning: String? = null,
    val info: String? = null,
    /** The web's page_bg — this app's screen backdrop. */
    val screen: String? = null,
    val card: String? = null,
) {
    val isEmpty: Boolean
        get() = listOfNotNull(primary, secondary, success, danger, warning, info, screen, card)
            .all { it.isBlank() }
}