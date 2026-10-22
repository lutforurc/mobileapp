package com.example.cashbookbd.ui.stockslip

/**
 * Product Out (write-off) and Product In (stock that arrived outside a
 * purchase) — the web's two modules (product-out / product-in, 2026-09-24 …
 * 10-21) are one design with a handful of differences, and those differences
 * are this enum; everything else is shared so the two cannot drift apart.
 *
 * Saving moves the stock; approval is a separate sign-off through the
 * Approval Center's own endpoint and moves nothing. No Date field: a slip is
 * always dated on the branch's transaction date, which the server reads.
 */
enum class StockSlipKind(
    val base: String,
    val title: String,
    val listTitle: String,
    val registerTitle: String,
    val reasonsTitle: String,
    /** "write-off" / "receipt": the noun the confirms and toasts use. */
    val noun: String,
    val numberKey: String,
    val dateKey: String,
    val numberHeader: String,
    val warehouseKey: String,
    val warehouseLabel: String,
    val reasonPlaceholder: String,
    val partyLabel: String,
    val partyHeader: String,
    val partyRequired: String,
    val createPermission: String,
    val approvePermission: String,
    val reportPermission: String,
    val reasonsPermission: String,
    val reasonExample: String,
    val byReasonTitle: String,
    val emptyRegister: String,
    val emptyGroup: String,
    /** Product In prices each line; Product Out does not. */
    val priced: Boolean,
    /** Product Out takes photos of the damage. */
    val photos: Boolean,
    /** Product Out asks before overdrawing the shelf. */
    val checksShortage: Boolean,
    val approvedNote: String,
) {
    OUT(
        base = "product-out",
        title = "Product Out",
        listTitle = "Write-off List",
        registerTitle = "Product Out Register",
        reasonsTitle = "Product Out Reasons",
        noun = "write-off",
        numberKey = "out_no",
        dateKey = "out_date",
        numberHeader = "OUT NO",
        warehouseKey = "from_warehouse_id",
        warehouseLabel = "From Warehouse",
        reasonPlaceholder = "Why did it go?",
        partyLabel = "Went back to",
        partyHeader = "WENT BACK TO",
        partyRequired = "This reason has to say who it went back to",
        createPermission = "product.out.create",
        approvePermission = "product.out.approve",
        reportPermission = "product.out.report.view",
        reasonsPermission = "product.out.reason.manage",
        reasonExample = "e.g. ভেঙে গেছে",
        byReasonTitle = "Why it went",
        emptyRegister = "Nothing written off in this period",
        emptyGroup = "Nothing written off",
        priced = false,
        photos = true,
        checksShortage = true,
        approvedNote = "The stock already left the shelf at save; this does not change it.",
    ),
    IN(
        base = "product-in",
        title = "Product In",
        listTitle = "Receipt List",
        registerTitle = "Product In Register",
        reasonsTitle = "Product In Reasons",
        noun = "receipt",
        numberKey = "in_no",
        dateKey = "in_date",
        numberHeader = "IN NO",
        warehouseKey = "to_warehouse_id",
        warehouseLabel = "To Warehouse",
        reasonPlaceholder = "Where did it come from?",
        partyLabel = "Came from",
        partyHeader = "CAME FROM",
        partyRequired = "This reason has to say who it came from",
        createPermission = "product.in.create",
        approvePermission = "product.in.approve",
        reportPermission = "product.in.report.view",
        reasonsPermission = "product.in.reason.manage",
        reasonExample = "e.g. গুদামে হিসাব করে পাওয়া গেছে",
        byReasonTitle = "Where it came from",
        emptyRegister = "Nothing came in during this period",
        emptyGroup = "Nothing came in",
        priced = true,
        photos = false,
        checksShortage = false,
        approvedNote = "The stock went on the shelf at save; this does not change it.",
    );

    /** The register's money column: Product Out costs, Product In values. */
    val moneyKey: String get() = if (this == OUT) "cost" else "value"
    val moneyHeader: String get() = if (this == OUT) "COST" else "VALUE"
    val totalMoneyKey: String get() = if (this == OUT) "total_cost" else "total_value"
}
