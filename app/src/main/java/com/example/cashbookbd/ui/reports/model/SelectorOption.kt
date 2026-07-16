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
)
