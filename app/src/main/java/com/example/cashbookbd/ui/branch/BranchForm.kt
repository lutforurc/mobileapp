package com.example.cashbookbd.ui.branch

import androidx.compose.ui.text.input.KeyboardType
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * The branch form's shape, mirroring the web's four-step AddBranch wizard.
 *
 * Declared as data rather than laid out by hand: the form carries roughly forty
 * fields, and spelling each one out in the screen would bury the handful of
 * things that are actually per-field. [AddBranchScreen] walks this list, so
 * adding a setting the backend gains is one entry here.
 *
 * Every [BranchField.key] is the exact name `branch/branch-store` and
 * `branch/branch-update` expect, which is what lets the form post as a map.
 */
sealed interface BranchField {
    val key: String
    val label: String

    /** Free text. [keyboard] picks the soft-keyboard layout. */
    data class Text(
        override val key: String,
        override val label: String,
        val keyboard: KeyboardType = KeyboardType.Text,
    ) : BranchField

    /** Pick one from [source]. */
    data class Choice(
        override val key: String,
        override val label: String,
        val source: BranchOptions,
    ) : BranchField

    /** An on/off setting, posted as "1"/"0". */
    data class Toggle(
        override val key: String,
        override val label: String,
    ) : BranchField
}

/**
 * Where a [BranchField.Choice] gets its options. The first three are fetched
 * from `settings/get-branch-settings`; the rest are fixed lists the web keeps in
 * its DataConstant module and are repeated here so the two stay in step.
 */
enum class BranchOptions {
    BRANCH_TYPE,
    BUSINESS_TYPE,
    PAPER_SIZE,
    STATUS,
    PAD_HEADING,
    PRINT_SIZE,
    MONEY_FORMAT,
}

/** One page of the wizard. */
data class BranchStep(
    val title: String,
    val summary: String,
    val fields: List<BranchField>,
)

object BranchForm {

    val steps: List<BranchStep> = listOf(
        BranchStep(
            title = "Basic Info",
            summary = "Branch identity, contact details, and status.",
            fields = listOf(
                BranchField.Text("name", "Branch Name"),
                BranchField.Choice("branch_types_id", "Branch Type", BranchOptions.BRANCH_TYPE),
                BranchField.Choice("business_type_id", "Business Type", BranchOptions.BUSINESS_TYPE),
                BranchField.Text("email", "Email", KeyboardType.Email),
                BranchField.Text("phone", "Phone", KeyboardType.Phone),
                BranchField.Text("contact_person", "Contact Person"),
                BranchField.Text("address", "Address"),
                BranchField.Text("notes", "Notes"),
                BranchField.Choice("status", "Status", BranchOptions.STATUS),
            ),
        ),
        BranchStep(
            title = "Print Setup",
            summary = "Print preferences, page size, and letterhead setup.",
            fields = listOf(
                BranchField.Choice("pad_heading_print", "Print Heading", BranchOptions.PAD_HEADING),
                BranchField.Choice("print_size", "Printer Settings", BranchOptions.PRINT_SIZE),
                BranchField.Choice("paper_size", "Invoice Page Size", BranchOptions.PAPER_SIZE),
            ),
        ),
        BranchStep(
            title = "Invoice Setup",
            summary = "Invoice labels, notes, formatting, and display options.",
            fields = listOf(
                BranchField.Text("purchase_note", "Purchase Invoice Note"),
                BranchField.Text("sales_note", "Sales Invoice Note"),
                BranchField.Choice("money_format", "Money Format", BranchOptions.MONEY_FORMAT),
                BranchField.Text("invoice_label", "Invoice Label"),
                BranchField.Text("device_identifier_text", "Device Identifier Text"),
                BranchField.Text("decimal_places", "Decimal Places", KeyboardType.Number),
                BranchField.Text("dashboard_top_sales_days", "Dashboard Top Sales Days", KeyboardType.Number),
                BranchField.Toggle("show_spelling_of_money", "Show spelling of money in invoice"),
                BranchField.Toggle("show_instalment_list", "Show instalment list in invoice"),
                BranchField.Toggle("show_description_in_invoice", "Show description in invoice"),
                BranchField.Toggle("show_brand_in_invoice", "Show brand in invoice"),
                BranchField.Toggle("show_category_in_invoice", "Show category in invoice"),
                BranchField.Toggle("combined_invoice_note", "Show combined invoice note"),
            ),
        ),
        BranchStep(
            title = "Feature Controls",
            summary = "Operational controls, sharing options, and SMS preferences.",
            fields = listOf(
                BranchField.Toggle("report_zero_bal", "Stock with zero"),
                BranchField.Toggle("manufactur_control", "Control manufacture"),
                BranchField.Toggle("warranty_controll", "Warranty control"),
                BranchField.Toggle("have_warehouse", "Multiple warehouse"),
                BranchField.Toggle("share_product_with_other_branch", "Product share"),
                BranchField.Toggle("share_customer_with_other_branch", "Customer share"),
                BranchField.Toggle("have_customer_sl", "Use customer serial"),
                BranchField.Toggle("use_bangla", "Use Bangla"),
                BranchField.Toggle("is_opening", "Opening ongoing"),
                BranchField.Toggle("stock_report_type", "Stock: Brand → Category → Item"),
                BranchField.Toggle("have_is_guaranter", "Use guarantor"),
                BranchField.Toggle("have_is_nominee", "Use nominee"),
                BranchField.Toggle("need_demo_tutorial", "Need demo tutorial"),
                BranchField.Toggle("need_relation_info", "Need relation's information"),
                BranchField.Toggle("need_mother_name", "Need mother's name"),
                BranchField.Toggle("need_contact_person", "Need contact person"),
                BranchField.Toggle("due_list_with_address", "Report due list with address"),
                BranchField.Toggle("sms_service", "SMS service"),
                BranchField.Toggle("received_sms", "Received SMS"),
                BranchField.Toggle("sales_sms", "Sales SMS"),
                BranchField.Toggle("purchase_sms", "Purchase SMS"),
                BranchField.Toggle("payment_sms", "Payment SMS"),
            ),
        ),
    )

    /** Keys the server rejects as blank — the create/update validation rules. */
    val requiredKeys: List<String> = listOf(
        "name", "branch_types_id", "business_type_id",
        "address", "phone", "contact_person",
        "pad_heading_print", "print_size",
    )

    /** Every toggle, so a create can post the full set rather than only the on ones. */
    val toggleKeys: List<String> =
        steps.flatMap { it.fields }.filterIsInstance<BranchField.Toggle>().map { it.key }

    // The web's DataConstant lists, repeated so the two forms offer the same choices.

    val statusOptions = listOf(
        SelectorOption(id = "1", label = "Active"),
        SelectorOption(id = "0", label = "Inactive"),
    )

    val padHeadingOptions = listOf(
        SelectorOption(id = "1", label = "Branch Pad Heading"),
        SelectorOption(id = "2", label = "Company Pad Heading"),
        SelectorOption(id = "3", label = "Custom Image Pad"),
    )

    val printSizeOptions = listOf(
        SelectorOption(id = "1", label = "Normal Printer"),
        SelectorOption(id = "2", label = "POS Printer"),
    )

    val moneyFormatOptions = listOf(
        SelectorOption(id = "1", label = "Taka … Only"),
        SelectorOption(id = "2", label = "… Taka Only"),
        SelectorOption(id = "3", label = "Only … Taka"),
        SelectorOption(id = "4", label = "Only Taka …"),
    )
}
