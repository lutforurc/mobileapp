package com.example.cashbookbd.core

/**
 * How a branch chooses to see a mobile number (the web's mobileFormat.ts,
 * dc17c5a / api 39bc4a49).
 *
 * Numbers are stored as the digits somebody typed and nothing else — that is
 * what a phone dials, what the duplicate check compares, and what an SMS
 * gateway wants. The grouping is a display choice held per branch (the
 * `mobile_number_format` branch meta): `#` stands for one digit and everything
 * else stands for itself, so `#####-######` shows 01973-190490. An empty
 * pattern means the branch has not asked for one, and the number is shown
 * exactly as entered.
 */
object MobileFormat {

    private const val PLACEHOLDER = '#'

    /**
     * The number grouped by [pattern] — or handed back untouched when there is
     * no pattern, no digits, or the value carries a letter (an "N/A" in a
     * mobile column is not a number and must not be minced into one).
     *
     * Digits the pattern has no room for are appended rather than dropped: a
     * pattern written for eleven digits meeting a twelve-digit number should
     * show the whole number badly, never most of it silently. Separators are
     * only drawn while digits are still coming, so a pattern longer than the
     * number leaves no trailing dash hanging.
     */
    fun format(value: String?, pattern: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty() || pattern.isNullOrEmpty()) return raw
        if (raw.any { it.isLetter() }) return raw

        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return raw

        val out = StringBuilder()
        var index = 0
        for (character in pattern) {
            if (character == PLACEHOLDER) {
                if (index >= digits.length) break
                out.append(digits[index])
                index++
            } else if (index < digits.length) {
                out.append(character)
            }
        }
        if (index < digits.length) out.append(digits.substring(index))
        return out.toString()
    }
}
