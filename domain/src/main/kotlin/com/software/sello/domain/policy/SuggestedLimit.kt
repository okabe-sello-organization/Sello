package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount

/**
 * What the limit keypad can hold: plain pesos with no leading zero and at most
 * [TransactionAmount.MAX_DIGITS] digits. The keypad and [SuggestedLimit] both read this one
 * definition, so a limit suggested to the form is always one the keypad could type.
 */
object LimitKeypad {
    private val Pad = Regex("[1-9][0-9]{0,${TransactionAmount.MAX_DIGITS - 1}}")

    /** Whether [text] is digits the keypad could have typed, so it can be built on. */
    fun fits(text: String): Boolean = Pad.matches(text)
}

/**
 * The limit another screen suggests for a new category, as the pesos the limit field shows.
 * Only an amount the keypad could hold ([LimitKeypad], or zero) that is a valid finite limit
 * is taken; anything else is left out, never clipped or repaired, and the limit starts empty.
 */
object SuggestedLimit {
    /** [amount] as pesos text, or null when it is not a limit the field can hold. */
    fun of(amount: Money): String? {
        if (BudgetLimit.finite(amount) !is Outcome.Success) return null
        return amount.minorUnits.toString().takeIf(::accepts)
    }

    /** Whether [text], received as a hint, is a limit the field can hold. */
    fun accepts(text: String): Boolean {
        if (text != "0" && !LimitKeypad.fits(text)) return false
        val money = (CopAmountInput.parse(text) as? Outcome.Success)?.value ?: return false
        return BudgetLimit.finite(money) is Outcome.Success
    }
}
