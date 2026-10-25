package com.software.sello.domain.policy

/**
 * A figure typed on the Sello pad with the decimal comma (investment rules 2 and 11): a
 * USD amount or the USD rate. [whole] holds the digits before the comma, at most
 * [maxWholeDigits] and with no leading zero, and [cents] at most two digits after it.
 * It only moves keys; the amount or rate it becomes is still validated by its own type.
 */
data class DecimalDraft(
    val maxWholeDigits: Int,
    val whole: String = "",
    val comma: Boolean = false,
    val cents: String = ""
) {
    init {
        require(maxWholeDigits in 1..MAX_WHOLE_DIGITS)
    }

    /** [draft] after a key press; [limitReached] when the press was refused. */
    data class Change(val draft: DecimalDraft, val limitReached: Boolean = false)

    val empty: Boolean get() = whole.isEmpty() && !comma

    /** A digit 0–9: after the comma up to two, before it up to [maxWholeDigits]. */
    fun digit(digit: Int): Change {
        require(digit in 0..9) { "not a keypad digit: $digit" }
        return when {
            comma && cents.length < SCALE -> Change(copy(cents = cents + digit))

            comma -> Change(this, limitReached = true)

            // A leading zero is replaced by the next digit, never kept before it.
            whole == "0" || whole.isEmpty() -> Change(copy(whole = digit.toString()))

            whole.length >= maxWholeDigits -> Change(this, limitReached = true)

            else -> Change(copy(whole = whole + digit))
        }
    }

    /** The comma, once; typed first it reads "0,". */
    fun comma(): Change = if (comma) {
        Change(this, limitReached = true)
    } else {
        Change(copy(whole = whole.ifEmpty { "0" }, comma = true))
    }

    fun backspace(): DecimalDraft = when {
        cents.isNotEmpty() -> copy(cents = cents.dropLast(1))
        comma -> copy(comma = false, whole = whole.takeUnless { it == "0" }.orEmpty())
        else -> copy(whole = whole.dropLast(1))
    }

    /** The figure in hundredths (USD cents, or centavos of the rate), or null when empty. */
    fun hundredths(): Long? {
        if (empty) return null
        return (whole.ifEmpty { "0" } + cents.padEnd(SCALE, '0')).toLong()
    }

    companion object {
        const val SCALE = 2

        /** Ten whole digits keep every draft below Long's range in hundredths. */
        const val MAX_WHOLE_DIGITS = 10

        /** A draft holding [hundredths], as when a saved figure is edited. */
        fun of(hundredths: Long, maxWholeDigits: Int): DecimalDraft {
            val whole = hundredths / 100
            val cents = hundredths % 100
            return DecimalDraft(
                maxWholeDigits,
                whole.toString(),
                comma = cents != 0L,
                cents = if (cents == 0L) "" else cents.toString().padStart(SCALE, '0').trimEnd('0')
            )
        }
    }
}
