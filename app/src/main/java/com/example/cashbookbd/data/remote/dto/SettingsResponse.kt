package com.example.cashbookbd.data.remote.dto

import com.example.cashbookbd.session.Permission
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

/**
 * Mirrors `POST /settings/get-settings`, which the backend wraps with the same
 * `foundData()` helper as the dashboard (see [DashboardResponse]) — so the
 * payload is double-nested under `data.data`:
 *
 * {
 *   "success": true,
 *   "data": {
 *     "data": { "permissions": [ ... ] },   <-- [SettingsPayload]
 *     "transaction_date": ""
 *   },
 *   "error": { "code": 0 }
 * }
 */
data class SettingsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: SettingsEnvelope? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class SettingsEnvelope(
    @SerializedName("data") val payload: SettingsPayload? = null,
    @SerializedName("transaction_date") val transactionDate: String? = null,
)

data class SettingsPayload(
    @SerializedName("permissions") val permissions: List<PermissionDto>? = null,
    @SerializedName("branch") val branch: SettingsBranchDto? = null,
    @SerializedName("user") val user: SettingsUserDto? = null,
    /**
     * The branch's current transaction date, already formatted dd/MM/yyyy by the
     * backend (`us_to_bd_date`). Shown as "Trx. Dt." in the account menu, exactly
     * as the web's DropdownUser reads `settings.data.trx_dt`.
     */
    @SerializedName("trx_dt") val trxDt: String? = null,
    /**
     * The server's APP_ENV ("local"/"production"). The voucher-image URLs skip
     * the `/public` prefix on a local server, exactly like the web's ImagePopup.
     */
    @SerializedName("env") val env: String? = null,
    /**
     * `screen_key -> walkthrough URL`, for every screen an operator has
     * recorded one against **for this app**. Rides along with the settings
     * payload rather than having an endpoint of its own — there is no read
     * route for ordinary users. Rows without a URL are dropped server-side, so
     * a key that is missing here simply has no video yet.
     *
     * The payload carries the browser's links too, under `tutorial_videos`, and
     * this deliberately does not read them: one table holds a row per screen
     * with a column per platform, because a web walkthrough shows a mouse
     * moving around a page the app does not have.
     */
    @SerializedName("tutorial_videos_mobile") val tutorialVideos: Map<String, String>? = null,
)

/** The signed-in user, from `settings/get-settings`. Only the fields the app reads. */
data class SettingsUserDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null,
    /**
     * Absolute URL of the user's photo, or null when they never uploaded one.
     * The backend stores the whole URL (built with `asset()` at upload time), so
     * there is no base path to prepend — the web's DropdownUser likewise drops
     * `profile_photo` straight into an `<img src>`.
     */
    @SerializedName("profile_photo") val profilePhoto: String? = null,
)

/** The current branch, from `settings/get-settings`. Only the fields the app reads. */
data class SettingsBranchDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("business_type_id") val businessTypeId: Int? = null,
    /**
     * Which inventory system the branch runs (1 general, 2 electronics,
     * 3 construction, 4 trading) — the key the invoice variants switch on.
     * The settings `branch` is the same `Branch::find()` row the web reads via
     * `user/current-branch`, so no separate call is needed.
     */
    @SerializedName("inventory_system_id") val inventorySystemId: Int? = null,
    /** Branch category: 1 = head office — forces the Head Office cash payment variant. */
    @SerializedName("branch_types_id") val branchTypesId: Int? = null,
    /**
     * Branch meta flag: shows the Combined Invoice's Both/Purchase/Sales notes
     * switch. A meta() string — "" when never set; the mapper derives a Boolean.
     */
    @SerializedName("combined_invoice_note") val combinedInvoiceNote: String? = null,
    /**
     * Read as a string, not an Int: it comes from `meta()`, which returns "" for
     * a branch that never set it, and an empty string coerced to Int would fail
     * the whole settings parse. The mapper turns it into an Int or null.
     */
    @SerializedName("decimal_places") val decimalPlaces: String? = null,
    /**
     * "Opening ongoing" branch flag — 1 while the business is still entering
     * opening balances. Kept a string for the same lenient-parse reason as
     * [decimalPlaces]; the mapper derives a Boolean.
     */
    @SerializedName("is_opening") val isOpening: String? = null,
    /** Branch column: the product forms carry the warranty/guarantee fields. */
    @SerializedName("warranty_controll") val warrantyControll: String? = null,
    /**
     * "Use Bangla" — the customer form asks for a Bangla name too. A column on
     * the branch rather than a meta, so it arrives as a number, never "1".
     */
    @SerializedName("use_bangla") val useBangla: String? = null,
    /** Branch column: the stock report (and ledger product cells) group by
     *  Brand → Category → Item, so product names carry their category prefix. */
    @SerializedName("stock_report_type") val stockReportType: String? = null,
    /**
     * "1" shows the tutorial-video links on the list screens (web gates its
     * YouTube icons on this). New branches default to on server-side.
     */
    @SerializedName("need_demo_tutorial") val needDemoTutorial: String? = null,
    // The 2026-07 branch metas. Each arrives as "1"/"0", or the boolean `false`
    // when the meta row was never written (Gson reads that into "false") — the
    // mapper treats only "1" as on.
    /** Customer form shows the Select Area field. */
    @SerializedName("need_customer_area") val needCustomerArea: String? = null,
    /** Customer form shows the Sex field. */
    @SerializedName("need_customer_sex") val needCustomerSex: String? = null,
    /** Customer form shows Date of Birth. */
    @SerializedName("need_customer_date_of_birth") val needCustomerDateOfBirth: String? = null,
    /** Customer form shows Occupation. */
    @SerializedName("need_customer_occupation") val needCustomerOccupation: String? = null,
    /** Customer form shows Permanent Address. */
    @SerializedName("need_customer_permanent_address") val needCustomerPermanentAddress: String? = null,
    /** Customer form shows the Photo field. */
    @SerializedName("need_customer_photo") val needCustomerPhoto: String? = null,
    /** Customer form shows the National ID field. */
    @SerializedName("need_customer_national_id") val needCustomerNationalId: String? = null,
    /** A nominee row also carries a photo. */
    @SerializedName("need_nominee_photo") val needNomineePhoto: String? = null,
    /** Customer form shows Mother's Name. */
    @SerializedName("need_customer_mother_name") val needCustomerMotherName: String? = null,
    /** Customer form shows Contact Person + Contact Number. */
    @SerializedName("need_customer_contact_person") val needCustomerContactPerson: String? = null,
    /** Customer form shows Relation + Relation's Name. */
    @SerializedName("need_relation_info") val needRelationInfo: String? = null,
    /** Customer form carries the Guarantor panel. */
    @SerializedName("have_is_guaranter") val haveIsGuaranter: String? = null,
    /** Customer form carries the Nominee panel. */
    @SerializedName("have_customer_nominee") val haveCustomerNominee: String? = null,
    /** Branch COLUMN (numeric, like use_bangla): shows Customer Number. */
    @SerializedName("have_customer_sl") val haveCustomerSl: String? = null,
    /** One order may carry several products (the multi-product order grid). */
    @SerializedName("multi_product_order") val multiProductOrder: String? = null,
    /** Ledger/cashbook reports show the voucher image column. */
    @SerializedName("show_voucher_image") val showVoucherImage: String? = null,
    /** How mobile numbers are grouped on screen ("#####-######"); text meta. */
    @SerializedName("mobile_number_format") val mobileNumberFormat: String? = null,
    /** Allotment-letter reference prefix (e.g. "BST/ALLOT"); text meta. */
    @SerializedName("letter_ref_prefix") val letterRefPrefix: String? = null,
    /** The date the branch's letters carry (yyyy-MM-dd); text meta. */
    @SerializedName("letter_ref_date") val letterRefDate: String? = null,
)

/**
 * A permission as sent by the backend. It may arrive as an object
 * (`{ id, name, group_name }`) or as a bare string (`"cash.received.create"`);
 * [PermissionDtoDeserializer] normalizes both into this shape.
 */
data class PermissionDto(
    val id: Long? = null,
    val name: String? = null,
    val groupName: String? = null,
) {
    /** Maps to the domain [Permission], dropping entries without a usable name. */
    fun toPermission(): Permission? =
        name?.takeIf { it.isNotBlank() }?.let { Permission(id = id, name = it, groupName = groupName) }
}

/**
 * Accepts either a JSON string or a `{ id, name, group_name }` object for a
 * permission, matching the web helper's dual-shape support. Registered on the
 * shared Gson in [com.example.cashbookbd.data.remote.NetworkModule].
 */
class PermissionDtoDeserializer : JsonDeserializer<PermissionDto> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): PermissionDto = when {
        json.isJsonPrimitive -> PermissionDto(name = json.asString)
        json.isJsonObject -> {
            val obj = json.asJsonObject
            fun field(key: String): JsonElement? = obj.get(key)?.takeUnless { it.isJsonNull }
            PermissionDto(
                id = field("id")?.asLong,
                name = field("name")?.asString,
                groupName = field("group_name")?.asString,
            )
        }
        else -> PermissionDto()
    }
}