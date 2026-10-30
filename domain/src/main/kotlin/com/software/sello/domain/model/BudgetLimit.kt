package com.software.sello.domain.model

import java.time.YearMonth

sealed interface BudgetLimitError {
    data object Negative : BudgetLimitError

    data class UnsupportedCurrency(val currency: Currency) : BudgetLimitError

    /** Only a limit being set is held to this; see [BudgetLimit.entered]. */
    data object AboveMaximum : BudgetLimitError
}

/**
 * What a category may spend in a month. A finite limit of zero is a real budget of
 * nothing; [Unlimited] is the explicit absence of a limit. Neither is the same as a
 * month with no configuration at all, which has no [BudgetLimit].
 */
sealed interface BudgetLimit {
    /** Zero or more. Only a limit being set is held to the transaction maximum ([entered]). */
    @ConsistentCopyVisibility
    data class Finite internal constructor(val amount: Money) : BudgetLimit

    data object Unlimited : BudgetLimit

    companion object {
        fun finite(amount: Money): Outcome<Finite, BudgetLimitError> = when {
            !amount.currency.isMvpEntryCurrency ->
                Outcome.Failure(BudgetLimitError.UnsupportedCurrency(amount.currency))

            amount.minorUnits < 0 -> Outcome.Failure(BudgetLimitError.Negative)

            else -> Outcome.Success(Finite(amount))
        }

        /**
         * A limit a person sets: a [finite] one no larger than one expense may be,
         * [TransactionAmount.MAX_UNITS]. Stored and restored limits are read with [finite],
         * so a larger one kept from before this rule still reads (ADR 0003).
         */
        fun entered(amount: Money): Outcome<Finite, BudgetLimitError> =
            when (val limit = finite(amount)) {
                is Outcome.Failure -> limit

                is Outcome.Success -> if (amount.minorUnits > TransactionAmount.MAX_UNITS) {
                    Outcome.Failure(BudgetLimitError.AboveMaximum)
                } else {
                    limit
                }
            }
    }
}

/** What a category is given automatically each month. */
sealed interface AutomaticBudget {
    data class Limit(val limit: BudgetLimit) : AutomaticBudget

    /** Nothing: the category is archived, so no budget is created for it. */
    data object Paused : AutomaticBudget
}

/**
 * What a category gets from [effectiveMonth] onwards, until a later entry replaces
 * it. Entries are never rewritten for past months, which is how an earlier month
 * keeps the limit it had even if nobody opened the app then.
 */
data class DefaultLimit(
    val categoryId: CategoryId,
    val effectiveMonth: YearMonth,
    val budget: AutomaticBudget
)

/** The limit set for one category in one month. It wins over any default for that month. */
data class MonthLimit(val categoryId: CategoryId, val month: YearMonth, val limit: BudgetLimit)
