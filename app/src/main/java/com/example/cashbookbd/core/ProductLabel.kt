package com.example.cashbookbd.core

/**
 * The one wording for a product in a search box: "CODE - NAME" when the
 * product has a code, the bare name when it does not.
 *
 * Several products may share a name and differ only by code, so the code has to
 * be visible before one is picked. Every product search in the app goes through
 * here — the invoice and tiles forms, production, reports, requisitions, stock
 * slips, transfers and the hotel amenity screens — so the same product reads the
 * same way wherever it is found.
 */
object ProductLabel {
    fun of(code: String?, name: String?): String {
        val bareName = name.orEmpty().trim()
        val bareCode = code.orEmpty().trim()
        return when {
            bareCode.isBlank() -> bareName
            bareName.isBlank() -> bareCode
            else -> "$bareCode - $bareName"
        }
    }
}
