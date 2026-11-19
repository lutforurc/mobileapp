package com.example.cashbookbd.report

/**
 * Does a report row belong to the product the Product box picked? (web
 * `matchesProduct`, commit 475c6ce5.)
 *
 * ⚠️ THE BOX IS A STRICT NARROWING, NOT A HINT: one picked product answers with
 * that product's rows and nothing else. The id decides whenever the row carries
 * one. Closing Stock's answer is a stocktake of every product on the branch and
 * may arrive without ids, so the picked option's own label (the product name) is
 * the fallback — without it a selection would answer with the whole branch,
 * which is the misreading the box exists to prevent.
 *
 * Pure, so the rule can be tested without a device.
 */
fun productMatches(
    /** What the box picked; blank means the box is not narrowing at all. */
    pickedId: String,
    /** The picked product's own name, the fallback when a row carries no id. */
    pickedLabel: String,
    /** The row's product id, when it has one. */
    rowId: String?,
    /** The row's product name, whatever wording it arrived in. */
    rowName: String?,
): Boolean {
    if (pickedId.isBlank()) return true

    val id = rowId?.trim().orEmpty()
    if (id.isNotEmpty()) return id == pickedId

    val label = pickedLabel.trim()
    if (label.isEmpty()) return false
    return rowName?.trim()?.equals(label, ignoreCase = true) == true
}
