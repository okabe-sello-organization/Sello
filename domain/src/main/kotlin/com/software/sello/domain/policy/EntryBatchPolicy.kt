package com.software.sello.domain.policy

import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome

/**
 * The rules of a receipt of several entries (Anotar varios, SELLO-E23): how many lines one
 * receipt may hold, and which lines a month's total cannot take in.
 */
object EntryBatchPolicy {
    /** At most this many lines on one receipt. */
    const val MAX_LINES = 30

    /** Whether one receipt may hold [count] lines: at least one, at most [MAX_LINES]. */
    fun holds(count: Int): Boolean = count in 1..MAX_LINES

    /**
     * The 0-based positions of [amounts] that the month's total cannot hold. [recorded] is
     * what the month holds already; the lines are added to it in their order, and a line is
     * refused when the total with it and every line before it is no longer [Money]. As every
     * amount is positive, each line after the first refused one is refused too.
     */
    fun overflowing(recorded: ExactTotal, amounts: List<Money>): List<Int> {
        var total = recorded
        return amounts.indices.filter { position ->
            total += amounts[position]
            total.toMoney() is Outcome.Failure
        }
    }
}
