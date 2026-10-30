package com.software.sello.feature.category

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetLimitError
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.CategoryBudgetPolicy
import com.software.sello.domain.policy.CategoryInMonth
import com.software.sello.domain.policy.CopAmountInput
import com.software.sello.domain.policy.LimitKeypad
import com.software.sello.domain.policy.MonthlyBudgetPolicy
import com.software.sello.domain.port.MonthBudget
import com.software.sello.presentation.money.MoneyFormatter

/**
 * What the limit section shows beside the draft, from the month the form read: the
 * suggested limits, and the month's total limit with the draft's in it. The figures
 * come from the domain; this only draws them.
 */
class LimitFigures(private val money: MoneyFormatter) {
    /** The person's most used limits, or the starting ones while they have too few. */
    fun suggestions(budget: MonthBudget?): List<LimitSuggestion> =
        CategoryBudgetPolicy.suggestedLimits(budget.inMonth(), STARTING_LIMITS.map(Money::cop))
            .map {
                val amount = money.format(it)
                LimitSuggestion(it.minorUnits, amount.digits, amount.spoken)
            }

    /**
     * Null when nothing was read, with "Sin límite" chosen, while the draft's limit is
     * empty or not an amount, when no category would have a limit, or when the total
     * cannot be held exactly. [categoryId] is the category being edited, null for a new one.
     */
    fun total(
        budget: MonthBudget?,
        categoryId: CategoryId?,
        unlimited: Boolean,
        limitText: String
    ): LimitTotal? {
        if (budget == null || unlimited) return null
        val draft = (CopAmountInput.parse(limitText) as? Outcome.Success)?.value
            ?.let { (BudgetLimit.finite(it) as? Outcome.Success)?.value }
            ?: return null
        val categories = budget.inMonth()
        val now = MonthlyBudgetPolicy.limitTotalWith(categories, null, null)
        val then = MonthlyBudgetPolicy.limitTotalWith(categories, categoryId, draft)
        if (now !is Outcome.Success || then !is Outcome.Success) return null
        val total = then.value ?: return null
        return LimitTotal(budget.month, money.format(total), changes = total != now.value)
    }

    /** [limitText] as the amount it is, or null while it is empty or not an amount. */
    fun amount(limitText: String): MoneyTextValue? =
        (CopAmountInput.parse(limitText) as? Outcome.Success)?.value?.let(money::format)

    private fun MonthBudget?.inMonth() =
        this?.categories.orEmpty().map { CategoryInMonth(it.category, it.state) }

    companion object {
        /** Limits offered before the person has three of their own, in pesos. */
        val STARTING_LIMITS = listOf(200_000L, 300_000L, 500_000L)
    }
}

/**
 * A limit written in the field as one the person is setting ([BudgetLimit.entered]): read
 * exactly as written, never clipped or repaired. The field keeps the text either way.
 */
internal object LimitEntry {
    /** [text] as the limit it sets, or why it cannot be set. */
    fun limit(text: String): Outcome<BudgetLimit.Finite, LimitError> {
        val money = (CopAmountInput.parse(text) as? Outcome.Success)?.value
            ?: return Outcome.Failure(LimitError.NotAnAmount)
        return when (val limit = BudgetLimit.entered(money)) {
            is Outcome.Success -> limit

            is Outcome.Failure -> Outcome.Failure(
                if (limit.error == BudgetLimitError.AboveMaximum) {
                    LimitError.TooLarge
                } else {
                    LimitError.NotAnAmount
                }
            )
        }
    }

    /**
     * Said whenever the field holds [text], as the expense amount does: an amount too large
     * to set, unless "Sin límite" is on and the amount is not sent. It is worked out from the
     * text, so a pasted, restored or switched-back amount says it alike. Text that is not an
     * amount is said on saving, as before.
     */
    fun reason(unlimited: Boolean, text: String): LimitError? = if (unlimited) {
        null
    } else {
        (limit(text) as? Outcome.Failure)?.error?.takeIf { it == LimitError.TooLarge }
    }
}

/**
 * The Sello pad typing a limit, through the same draft rules as an expense: no leading
 * zeros and no thirteenth digit. Unlike an expense, zero is a limit: 0 or 000 on an empty
 * limit types it, and a digit after it takes its place. A limit that is not plain pad
 * digits, such as a refused paste, is started again rather than built on.
 */
internal object LimitKeys {
    /** The limit after [press], or null when the press is refused for length. */
    fun press(text: String, press: (AmountDraft) -> AmountDraft.Change): String? {
        val change = press(draft(text))
        if (change.limitReached) return null
        // Only zeros pressed on nothing leave the draft empty: that is the zero limit.
        return change.draft.digits().ifEmpty { ZERO }
    }

    fun backspace(text: String): String = draft(text).backspace().digits()

    private fun draft(text: String): AmountDraft =
        if (LimitKeypad.fits(text)) AmountDraft.Digits(text) else AmountDraft.Empty

    private fun AmountDraft.digits() = (this as? AmountDraft.Digits)?.digits.orEmpty()

    private const val ZERO = "0"
}
