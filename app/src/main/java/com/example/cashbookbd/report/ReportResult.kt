package com.example.cashbookbd.report

import com.example.cashbookbd.core.VoucherAttachment

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
    /** Raw notes/remarks text the highlight rules match against ("" = none). */
    val highlightText: String = "",
    /** Display label of the column whose cell gets the coloured box, when any. */
    val highlightLabel: String? = null,
    /**
     * The voucher's photo/document attachments, when the report declares a
     * [ReportConfig.voucherImages] spec — rendered as a tappable thumbnail
     * column behind the branch's `show_voucher_image` switch.
     */
    val voucherAttachments: List<VoucherAttachment> = emptyList(),
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
