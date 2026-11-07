package com.software.sello.domain.model

import java.time.LocalDate

/**
 * An expense the person records often (SELLO-199): one category and one note, folded as
 * search folds it, whatever the amounts. [note] is the note as it was last written and
 * [key] the folded note; with [categoryId] it names the group. [count] is how many times
 * it was recorded in the window and [usual] the median of its last amounts there.
 * [latestDate] and [latestSequence] place the most recent one.
 */
data class FrequentExpense(
    val categoryId: CategoryId,
    val note: Note,
    val key: String,
    val count: Int,
    val usual: TransactionAmount,
    val latestDate: LocalDate,
    val latestSequence: Long
)

/** Why the usual amount could not be worked out. Neither is ever repaired. */
sealed interface UsualAmountError {
    /** There were no amounts to take it from. */
    data object NoAmounts : UsualAmountError

    data class CurrencyMismatch(val expected: Currency, val actual: Currency) : UsualAmountError

    /** The figure is not a storable expense amount. */
    data class OutOfRange(val error: TransactionAmountError) : UsualAmountError
}
