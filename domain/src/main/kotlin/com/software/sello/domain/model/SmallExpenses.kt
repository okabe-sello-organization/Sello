package com.software.sello.domain.model

/**
 * Gastos hormiga's line: an expense at or under it counts as small. Whole pesos from
 * [MIN_PESOS] to [MAX_PESOS]; [DEFAULT] until the person sets one. The same line applies
 * to every month, past ones too. Made only through [of].
 */
@JvmInline
value class SmallExpenseLine private constructor(val pesos: Long) {
    val money: Money get() = Money.cop(pesos)

    companion object {
        const val MIN_PESOS = 1_000L
        const val MAX_PESOS = 100_000L

        val DEFAULT = SmallExpenseLine(20_000L)

        /** The widest line there can be: what any line could count as small. */
        val WIDEST = SmallExpenseLine(MAX_PESOS)

        /** [pesos] as a line, or null outside [MIN_PESOS] to [MAX_PESOS]: never clipped. */
        fun of(pesos: Long): SmallExpenseLine? =
            if (pesos in MIN_PESOS..MAX_PESOS) SmallExpenseLine(pesos) else null
    }
}

/**
 * The expenses behind Resumen's hero, as it counts them: [current] runs over the selected
 * month's days the hero adds (up to today in the month in progress, the whole month once
 * it has finished). [previous] runs over the same days of the month before, capped at its
 * length, or is null when that month has nothing recorded (Resumen's rule).
 */
data class SpendingPeriods(val current: List<Expense>, val previous: List<Expense>?) {
    companion object {
        val NONE = SpendingPeriods(emptyList(), null)
    }
}

/** How many expenses are at or under a line, and what they add up to. */
data class SmallTally(val count: Int, val total: Money)

/**
 * The Gastos hormiga slip. [sharePercent] is [total] over the period's spending, rounded half
 * up to a whole percent. [previousTotal] is the same days of the month before, or null when
 * that month has nothing recorded. [categories] are the three with the biggest sums at most.
 */
data class SmallExpenses(
    val line: SmallExpenseLine,
    val count: Int,
    val total: Money,
    val sharePercent: Int,
    val previousTotal: Money?,
    val categories: List<SmallExpenseCategory>
)

data class SmallExpenseCategory(val category: Category, val count: Int, val total: Money)
