package com.example.cashbookbd.report

/**
 * The remote "dropdown list" sources the report filters can draw from. Each maps
 * to a Laravel DDL endpoint; the fetch/parse rules live in
 * [com.example.cashbookbd.data.repository.SelectorRepository].
 *
 * - [CATEGORY], [BRAND], [SOMITY] return their whole list at once (loaded when the
 *   screen opens) — the user picks from a plain dropdown.
 * - [PRODUCT], [LABOUR] require a typed keyword (`q`) and search server-side — the
 *   user types into a searchable dropdown.
 */
enum class ReportSelectorSource(val searchable: Boolean) {
    CATEGORY(searchable = false),
    BRAND(searchable = false),
    SOMITY(searchable = false),
    PRODUCT(searchable = true),
    LABOUR(searchable = true),
    EMPLOYEE(searchable = true),
}

/**
 * One extra filter a report needs beyond branch/date/ledger/choice — a remote
 * dropdown backed by a [source]. The chosen option's value is sent to the report
 * endpoint under [paramKey] (its id, or its label text when [sendLabel] is true,
 * e.g. Product Stock's `product_name`).
 */
data class ReportSelector(
    val paramKey: String,
    val label: String,
    val source: ReportSelectorSource,
    val required: Boolean = true,
    /** Send the option's display label instead of its id (e.g. `product_name`). */
    val sendLabel: Boolean = false,
)
