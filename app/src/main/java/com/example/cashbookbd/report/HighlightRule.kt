package com.example.cashbookbd.report

/**
 * One entry of the company's configurable "phrase → coloured border" list
 * (`GET /highlight-rules/active`), mirroring the web app's `highlightRules.ts`.
 * Wherever the app applies these, a report line whose notes/remarks text
 * CONTAINS [phrase] (case-insensitive) is boxed in [color]'s border.
 */
data class HighlightRule(
    val id: Long,
    val phrase: String,
    /** Palette key (red/amber/green/blue/purple/pink/gray); unknown keys fall back to red. */
    val color: String,
    val priority: Int,
)

/**
 * One row of the admin management list (`GET /admin/highlight-rules`) — the
 * full record including the fields the /active endpoint omits.
 */
data class HighlightRuleRow(
    val id: Long,
    val phrase: String,
    val color: String,
    val priority: Int,
    val active: Boolean,
    val description: String,
)

/**
 * The rule that should style [text], or null. Rules arrive from the API already
 * sorted best-priority-first (priority DESC, id ASC), so the first phrase found
 * wins — highest priority, ties broken by earliest added. Matching is a
 * case-insensitive "contains".
 */
fun matchHighlightRule(text: String?, rules: List<HighlightRule>): HighlightRule? {
    val hay = text?.trim()?.lowercase().orEmpty()
    if (hay.isEmpty()) return null
    for (rule in rules) {
        val needle = rule.phrase.trim().lowercase()
        if (needle.isNotEmpty() && hay.contains(needle)) return rule
    }
    return null
}
