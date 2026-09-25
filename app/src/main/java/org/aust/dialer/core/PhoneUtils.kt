package org.aust.dialer.core

import java.text.Normalizer

/** Pure string helpers for phone numbers and T9 matching. No Android dependencies (unit-testable). */
object PhoneUtils {

    /** Converts Arabic-Indic and Extended Arabic-Indic digits to ASCII digits. */
    fun toAsciiDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun digitsOnly(s: String): String = toAsciiDigits(s).filter { it in '0'..'9' }

    /** Keeps only characters that can be dialed: 0-9 * # and a leading +. */
    fun sanitizeDialable(s: String): String {
        val ascii = toAsciiDigits(s)
        val sb = StringBuilder(ascii.length)
        for (ch in ascii) {
            when {
                ch in '0'..'9' || ch == '*' || ch == '#' -> sb.append(ch)
                ch == '+' && sb.isEmpty() -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** True for private / unknown callers (the call log stores them as an empty or non-numeric number). */
    fun isUnknown(number: String?): Boolean {
        if (number == null) return true
        // Some devices store presentation codes (-1 unknown, -2 private, -3 payphone) instead of a number.
        if (number.trim() in PRESENTATION_CODES) return true
        return digitsOnly(number).isEmpty()
    }

    private val PRESENTATION_CODES = setOf("-1", "-2", "-3")

    /**
     * Key used to match the same subscriber across formats (+963 9xx…, 09xx…, 9xx…):
     * the last 9 digits (or all digits when shorter).
     */
    fun matchKey(number: String): String {
        val d = digitsOnly(number)
        return if (d.length > 9) d.takeLast(9) else d
    }

    /** Stable key for SMS senders. Numeric senders use phone matching; alphanumeric sender IDs
     * (common for advertising/service SMS) are preserved in normalized form. */
    fun senderKey(address: String): String {
        val raw = address.trim()
        val phone = matchKey(raw)
        if (phone.isNotEmpty()) return "phone:$phone"
        return "text:" + fold(raw).replace(Regex("\\s+"), " ").trim()
    }

    /** Lower-cases and strips diacritics (including Arabic tashkeel) for searching. */
    fun fold(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        val sb = StringBuilder(n.length)
        for (c in n) {
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) sb.append(c)
        }
        return sb.toString().lowercase()
    }

    private fun t9Char(c: Char): Char? = when (c) {
        in 'a'..'c' -> '2'
        in 'd'..'f' -> '3'
        in 'g'..'i' -> '4'
        in 'j'..'l' -> '5'
        in 'm'..'o' -> '6'
        in 'p'..'s' -> '7'
        in 't'..'v' -> '8'
        in 'w'..'z' -> '9'
        in '0'..'9' -> c
        else -> null
    }

    /** T9 digits of a single word. Letters without a T9 mapping (e.g. Arabic) are dropped. */
    fun t9Word(word: String): String {
        val folded = fold(word)
        val sb = StringBuilder(folded.length)
        for (c in folded) t9Char(c)?.let { sb.append(it) }
        return sb.toString()
    }

    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    fun t9Words(name: String): List<String> =
        name.split(WORD_SPLIT).map { t9Word(it) }.filter { it.isNotEmpty() }

    /** True when [query] (digits) is a T9 prefix of any word, or of the whole name without spaces. */
    fun t9Matches(words: List<String>, query: String): Boolean {
        if (query.isEmpty() || words.isEmpty()) return false
        if (words.any { it.startsWith(query) }) return true
        return words.joinToString("").startsWith(query)
    }
}
