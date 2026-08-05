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

    /**
     * What the setting does, said under the field itself — the web's 2026-08-03
     * change. Null keeps the field bare.
     */
    val description: String?

    /** Free text. [keyboard] picks the soft-keyboard layout. */
    data class Text(
        override val key: String,
        override val label: String,
        val keyboard: KeyboardType = KeyboardType.Text,
        /** A taller textarea (the letter signature block). */
        val multiline: Boolean = false,
        override val description: String? = null,
    ) : BranchField

    /** Pick one from [source]. */
    data class Choice(
        override val key: String,
        override val label: String,
        val source: BranchOptions,
        override val description: String? = null,
    ) : BranchField

    /** An on/off setting, posted as "1"/"0". */
    data class Toggle(
        override val key: String,
        override val label: String,
        override val description: String? = null,
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
    PAD_PRINT_MODE,
    DOWN_PAYMENT_BASE,
}

/**
 * One page of the wizard. A step with [anyOf] permissions is left out of the
 * list entirely for anyone holding none of them, so everyone else keeps the
 * same steps and the same numbering. Hiding it is only tidiness — the server
 * drops those settings from the save for a caller who lacks the permission.
 */
data class BranchStep(
    val title: String,
    val summary: String,
    val fields: List<BranchField>,
    val anyOf: List<String> = emptyList(),
)

object BranchForm {

    /**
     * The step everyone sees. The SaaS step is appended by [stepsFor] only for
     * whoever runs the platform.
     */
    val steps: List<BranchStep> = listOf(
        BranchStep(
            title = "Basic Info",
            summary = "Branch identity, contact details, and status.",
            fields = listOf(
                BranchField.Text(
                    "name", "Branch Name",
                    description = "How the branch is named everywhere it is listed, and on its printed papers.",
                ),
                BranchField.Choice(
                    "branch_types_id", "Branch Type", BranchOptions.BRANCH_TYPE,
                    description = "Head office or an ordinary branch. The head office sees company-wide figures and lists the others do not.",
                ),
                BranchField.Choice(
                    "business_type_id", "Business Type", BranchOptions.BUSINESS_TYPE,
                    description = "The trade the branch is in. It decides which dashboard the branch opens on.",
                ),
                BranchField.Text(
                    "email", "Email", KeyboardType.Email,
                    description = "The branch's own address, printed on its papers so customers can write back.",
                ),
                BranchField.Text(
                    "phone", "Phone", KeyboardType.Phone,
                    description = "The number printed on invoices and letters from this branch.",
                ),
                BranchField.Text(
                    "contact_person", "Contact Person",
                    description = "Who to ask for at this branch — the manager or whoever answers for it.",
                ),
                BranchField.Text(
                    "address", "Address",
                    description = "Where the branch sits. It goes under the heading on its printed papers.",
                ),
                BranchField.Text(
                    "notes", "Notes",
                    description = "For the office's own remarks about this branch. Nothing here is printed.",
                ),
                BranchField.Choice(
                    "status", "Status", BranchOptions.STATUS,
                    description = "An inactive branch stays on record with all its figures, but nobody can work in it.",
                ),
            ),
        ),
        BranchStep(
            title = "Print Setup",
            summary = "Print preferences, page size, and letterhead setup.",
            fields = listOf(
                BranchField.Choice(
                    "pad_heading_print", "Print Heading", BranchOptions.PAD_HEADING,
                    description = "Whose letterhead the software draws at the top — this branch's, the company's, or an uploaded image.",
                ),
                BranchField.Choice(
                    "print_size", "Printer Settings", BranchOptions.PRINT_SIZE,
                    description = "A normal printer prints a full page; a POS printer prints the narrow roll used at a counter.",
                ),
                BranchField.Choice(
                    "paper_size", "Invoice Page Size", BranchOptions.PAPER_SIZE,
                    description = "The paper an invoice is laid out for, so it fills the sheet the branch actually prints on.",
                ),
                // The web's "Pad Head" section: pre-printed pads skip the drawn
                // letterhead and leave a measured blank space at the top.
                BranchField.Choice("pad_print_mode", "Pad Head Printing", BranchOptions.PAD_PRINT_MODE),
                BranchField.Text("preprinted_pad_height", "Blank Space at Top (px)", KeyboardType.Number),
                BranchField.Text(
                    "salutation_male", "Salutation (Male)",
                    description = "How a letter greets a male customer. Left blank, the software's own wording is used.",
                ),
                BranchField.Text(
                    "salutation_female", "Salutation (Female)",
                    description = "How a letter greets a female customer. Left blank, the software's own wording is used.",
                ),
                BranchField.Text(
                    "salutation_other", "Salutation (Other / Not Set)",
                    description = "Used when the customer's sex is not recorded, so a letter never goes out addressed wrongly.",
                ),
                // The web edits this with a rich-text editor; the raw HTML is
                // editable here so the block still saves from the phone.
                BranchField.Text("letter_signature", "Letter Signature Block", multiline = true),
            ),
        ),
        BranchStep(
            title = "Invoice Setup",
            summary = "Invoice labels, notes, formatting, and display options.",
            fields = listOf(
                BranchField.Text(
                    "purchase_note", "Purchase Invoice Note",
                    description = "Standing wording printed at the foot of every purchase invoice — terms, conditions, whatever the branch always says.",
                ),
                BranchField.Text(
                    "sales_note", "Sales Invoice Note",
                    description = "The same, printed at the foot of every sales invoice.",
                ),
                BranchField.Choice(
                    "money_format", "Money Format", BranchOptions.MONEY_FORMAT,
                    description = "Where the word Taka sits when the amount is written out — before the words, after them, or wrapped in Only.",
                ),
                BranchField.Text(
                    "invoice_label", "Invoice Label",
                    description = "What the paper calls itself at the top — Invoice, Cash Memo, Bill, whatever the branch issues.",
                ),
                BranchField.Text(
                    "device_identifier_text", "Device Identifier Text",
                    description = "The word printed before a serial number on the invoice, such as IMEI or Engine No. Left blank, the number stands alone.",
                ),
                BranchField.Text(
                    "decimal_places", "Decimal Places", KeyboardType.Number,
                    description = "How many digits after the point every amount is shown with. 0 rounds to whole Taka.",
                ),
                BranchField.Text(
                    "dashboard_top_sales_days", "Dashboard Top Sales Days", KeyboardType.Number,
                    description = "How many days back the dashboard's top-selling list counts. 1 means today alone; left empty it looks back 7 days.",
                ),
                BranchField.Toggle(
                    "show_spelling_of_money", "Show spelling of money in invoice",
                    description = "Prints the invoice total in words beneath the figure.",
                ),
                BranchField.Toggle(
                    "show_instalment_list", "Show instalment list in invoice",
                    description = "Prints the instalment schedule, with its dates and amounts, on an instalment sale.",
                ),
                BranchField.Toggle(
                    "show_description_in_invoice", "Show description in invoice",
                    description = "Prints each item's description line under its name.",
                ),
                BranchField.Toggle(
                    "show_brand_in_invoice", "Show brand in invoice",
                    description = "Prints the brand of each item beside its name.",
                ),
                BranchField.Toggle(
                    "show_category_in_invoice", "Show category in invoice",
                    description = "Prints the category of each item beside its name.",
                ),
                BranchField.Toggle(
                    "combined_invoice_note", "Show combined invoice note",
                    description = "Offers a note box on the combined trading entry, so one remark covers the whole invoice.",
                ),
            ),
        ),
        BranchStep(
            title = "Customer Setup",
            summary = "Which extra fields the customer form asks for.",
            fields = listOf(
                BranchField.Toggle(
                    "need_customer_area", "Need Customer Area?",
                    description = "Adds the area field to the customer form, so customers can be grouped by locality.",
                ),
                BranchField.Toggle(
                    "need_customer_sex", "Need Customer Sex?",
                    description = "Adds the sex field, which also decides which salutation a letter uses for the customer.",
                ),
            ),
        ),
        // The web's 2026-08-03 split: order and stock settings on a step of
        // their own, out of Real Estate Setup and Feature Controls.
        BranchStep(
            title = "Product Setup",
            summary = "How products are ordered and priced in this branch.",
            fields = listOf(
                BranchField.Toggle(
                    "multi_product_order", "Multi Product Order?",
                    description = "On: one order can carry several products. Off: the original single-product order form.",
                ),
                BranchField.Toggle(
                    "report_zero_bal", "Stock with zero",
                    description = "Keeps items with no balance on the stock report, so what has run out is still visible.",
                ),
                BranchField.Toggle(
                    "stock_report_type", "Stock: Brand → Category → Item",
                    description = "Groups the stock report by brand, then category, then item, instead of listing items straight.",
                ),
                BranchField.Toggle(
                    "warranty_controll", "Warranty control",
                    description = "Products carry a warranty period, asked for when the product is set up and tracked from the sale.",
                ),
                BranchField.Toggle(
                    "share_product_with_other_branch", "Product share",
                    description = "Products entered anywhere in the company can be picked here. Off, this branch sees only its own.",
                ),
            ),
        ),
        BranchStep(
            title = "Real Estate Setup",
            summary = "The allotment letter's payment schedule and references.",
            fields = listOf(
                BranchField.Choice(
                    "down_payment_base",
                    "Down Payment Calculated On",
                    BranchOptions.DOWN_PAYMENT_BASE,
                ),
                BranchField.Text("down_payment_percent", "Down Payment (%)", KeyboardType.Number),
                BranchField.Text(
                    "delay_charge_percent",
                    "Delay Charge (% per annum)",
                    KeyboardType.Number,
                ),
                // What the letter's reference number starts with (year + sale
                // serial are appended, e.g. BST/ALLOT/2026/036).
                BranchField.Text("letter_ref_prefix", "Letter Reference Prefix (e.g. BST/ALLOT)"),
                // The date the branch's letters carry, yyyy-MM-dd; blank offers
                // the day the letter is issued.
                BranchField.Text("letter_ref_date", "Letter Reference Date (yyyy-MM-dd)"),
            ),
        ),
        BranchStep(
            title = "Feature Controls",
            summary = "Operational controls, sharing options, and SMS preferences.",
            fields = listOf(
                BranchField.Toggle(
                    "manufactur_control", "Control manufacture",
                    description = "Opens the production side, so an item can be built from other items instead of only bought and sold.",
                ),
                BranchField.Toggle(
                    "have_warehouse", "Multiple warehouse",
                    description = "Vouchers say which warehouse stock moved in or out of. Off, everything sits in one.",
                ),
                BranchField.Toggle(
                    "share_customer_with_other_branch", "Customer share",
                    description = "Customers entered anywhere in the company can be picked here. Off, this branch sees only its own.",
                ),
                BranchField.Toggle(
                    "have_customer_sl", "Use customer serial",
                    description = "Gives every customer a serial number of its own on the customer form.",
                ),
                BranchField.Toggle(
                    "use_bangla", "Use Bangla",
                    description = "Adds Bangla name fields beside the English ones, for papers that have to carry both.",
                ),
                BranchField.Toggle(
                    "is_opening", "Opening ongoing",
                    description = "The branch is still entering its opening figures, so products and parties can take an opening balance. Switch off once the books are settled.",
                ),
                BranchField.Toggle(
                    "have_is_guaranter", "Use guarantor",
                    description = "Opens the guarantor section on the customer form — who stands behind the customer's dues.",
                ),
                BranchField.Toggle(
                    "have_is_nominee", "Use nominee",
                    description = "Opens the nominee section on the customer form — who inherits the customer's claim.",
                ),
                BranchField.Toggle(
                    "need_demo_tutorial", "Need demo tutorial",
                    description = "Offers the guided walkthrough to users of this branch. Turn off once the staff know their way around.",
                ),
                BranchField.Toggle(
                    "need_relation_info", "Need relation's information",
                    description = "Adds the father's/husband's name and relation fields to the customer form.",
                ),
                BranchField.Toggle(
                    "need_mother_name", "Need mother's name",
                    description = "Adds the mother's name field to the customer form.",
                ),
                BranchField.Toggle(
                    "need_contact_person", "Need contact person",
                    description = "Adds a contact person and their number, for customers reached through someone else.",
                ),
                BranchField.Toggle(
                    "due_list_with_address", "Report due list with address",
                    description = "Prints each party's address and mobile beside the name on the Due List, so the sheet can be worked from in the field.",
                ),
                BranchField.Toggle(
                    "sms_service", "SMS service",
                    description = "The master switch for this branch. Off, none of the messages below go out however they are set.",
                ),
                BranchField.Toggle(
                    "received_sms", "Received SMS",
                    description = "Texts the party when money received from them is posted.",
                ),
                BranchField.Toggle(
                    "sales_sms", "Sales SMS",
                    description = "Texts the customer when a sale is invoiced to them.",
                ),
                BranchField.Toggle(
                    "purchase_sms", "Purchase SMS",
                    description = "Texts the supplier when a purchase is posted against them.",
                ),
                BranchField.Toggle(
                    "payment_sms", "Payment SMS",
                    description = "Texts the party when money paid to them is posted.",
                ),
                BranchField.Toggle(
                    "show_voucher_image", "Show Voucher Image?",
                    description = "Shows the image attached to a voucher in the Cash Book, Sales Ledger and Purchase Ledger.",
                ),
            ),
        ),
        // The platform operator's own step. The settings sit on the branch that
        // runs the platform; set on a customer's branch nothing reads them.
        BranchStep(
            title = "SaaS Setup",
            summary = "Platform settings. Only this account sees them.",
            anyOf = listOf("registration.alert.manage"),
            fields = listOf(
                BranchField.Toggle(
                    "registration_alert", "Notify on registration?",
                    description = "Puts a notice in this branch's bell, on the web and in the app. Costs nothing and cannot fail.",
                ),
                BranchField.Toggle(
                    "registration_alert_sms", "Also send SMS?",
                    description = "Reaches you without opening anything, and is charged per message.",
                ),
                BranchField.Text(
                    "registration_alert_mobile", "Alert Mobile Number(s)", KeyboardType.Phone,
                    description = "Separate several with commas. Left empty, no SMS is sent however the switch is set.",
                ),
            ),
        ),
    )

    /**
     * The wizard for one user: steps whose permissions they hold. Built rather
     * than fixed, so the numbering and the "of N" count stay right for everyone
     * who does not see the extra step.
     */
    fun stepsFor(permissions: List<com.example.cashbookbd.session.Permission>?): List<BranchStep> =
        steps.filter { step ->
            step.anyOf.isEmpty() ||
                com.example.cashbookbd.session.Permissions.hasAny(permissions, step.anyOf)
        }

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

    val padPrintModeOptions = listOf(
        SelectorOption(id = "software", label = "Software Generated Pad Head"),
        SelectorOption(id = "preprinted", label = "Pre-printed Pad (leave blank space)"),
    )

    val downPaymentBaseOptions = listOf(
        SelectorOption(id = "total", label = "Total Property Value"),
        SelectorOption(id = "net_payable", label = "Net Payable Balance after Booking Money"),
    )
}
