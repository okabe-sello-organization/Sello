package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount

/**
 * The limit another screen suggests for a new category, as the pesos the limit field shows.
 * Only an amount the field could hold is taken (up to [TransactionAmount.MAX_DIGITS] digits,
 * no leading zeros) and that is a valid finite limit; anything else is left out, never
 * clipped or repaired, and the limit starts empty.
 */
object SuggestedLimit {
    private val Plain = Regex("0|[1-9][0-9]{0,${TransactionAmount.MAX_DIGITS - 1}}")

    /** [amount] as pesos text, or null when it is not a limit the field can hold. */
    fun of(amount: Money): String? {
        if (BudgetLimit.finite(amount) !is Outcome.Success) return null
        return amount.minorUnits.toString().takeIf(::accepts)
    }

    /** Whether [text], received as a hint, is a limit the field can hold. */
    fun accepts(text: String): Boolean {
        if (!Plain.matches(text)) return false
        val money = (CopAmountInput.parse(text) as? Outcome.Success)?.value ?: return false
        return BudgetLimit.finite(money) is Outcome.Success
    }
}
