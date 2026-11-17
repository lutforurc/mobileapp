package com.example.cashbookbd.ui.reports.model

/**
 * One option in a report filter's remote dropdown (category, brand, somity,
 * product, labour). [id] is the value sent to the report endpoint; [label] is
 * shown to the user, with an optional [sublabel] (e.g. category/unit) on a second
 * muted line.
 */
data class SelectorOption(
    val id: String,
    val label: String,
    val sublabel: String? = null,
    /**
     * What a `sendLabel` report filter posts when the dropdown shows more than
     * the bare name (a product's "CODE - NAME"). Null posts [label].
     */
    val sendValue: String? = null,
)
