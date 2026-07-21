package com.example.cashbookbd.report

/**
 * One field of a report row: a humanized [label] and its display [value]. The
 * raw [key] is kept for summary cells so a report can colour its KPI cards by
 * the underlying field (e.g. the attendance summary's present/absent/…).
 */
data class ReportCell(
    val label: String,
    val value: String,
    val key: String = "",
)

/** One record of a report, rendered as a card of label/value cells. */
data class ReportRow(
    val cells: List<ReportCell>,
)

/**
 * A parsed, display-ready report. Because the backend's response shapes are not
 * uniform, the parser is defensive (see
 * [com.example.cashbookbd.data.repository.GenericReportRepository]): it extracts
 * a row list from whatever array it can find, and pulls out any scalar summary
 * fields for a header.
 */
data class ReportResult(
    val rows: List<ReportRow>,
    val summary: List<ReportCell> = emptyList(),
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}
