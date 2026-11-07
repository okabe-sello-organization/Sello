package com.software.sello.domain.policy

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SmallExpenseCategory
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.SmallExpenses
import com.software.sello.domain.model.SmallTally
import com.software.sello.domain.model.SnapshotFailure
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Gastos hormiga (SELLO-194): the expenses at or under the person's line, in every category,
 * fixed payments included, over the days Resumen's hero adds. Facts only: no warning, score
 * or target. Sums are checked [Money]; one that does not fit is
 * [SnapshotFailure.Unrepresentable], never wrapped or clipped.
 */
object SmallExpensePolicy {
    /** The slip shows once the period has this many small expenses. */
    const val MIN_COUNT = 5

    /** Donde más se juntan lists this many categories at most. */
    const val TOP_CATEGORIES = 3

    /** How many of [expenses] are at or under [line], and their checked sum. */
    fun tally(expenses: List<Expense>, line: SmallExpenseLine): Outcome<SmallTally, SnapshotFailure> {
        val small = expenses.filter { it.amount.money.minorUnits <= line.pesos }
        return when (val total = total(small)) {
            is Outcome.Success -> Outcome.Success(SmallTally(small.size, total.value))
            is Outcome.Failure -> total
        }
    }

    /**
     * The slip for [snapshot] with [line], or null when its period has fewer than [MIN_COUNT]
     * small expenses. The share is of the hero's spending, half up to a whole percent.
     */
    fun summary(
        snapshot: ResumenSnapshot,
        line: SmallExpenseLine
    ): Outcome<SmallExpenses?, SnapshotFailure> {
        val small = snapshot.periods.current.filter { it.amount.money.minorUnits <= line.pesos }
        if (small.size < MIN_COUNT) return Outcome.Success(null)
        val total = when (val sum = total(small)) {
            is Outcome.Success -> sum.value
            is Outcome.Failure -> return sum
        }
        // The hero counts every one of them; a period that says otherwise is not guessed at.
        if (total.minorUnits > snapshot.budget.spent.minorUnits) {
            return Outcome.Failure(SnapshotFailure.Unrepresentable)
        }
        val previous = snapshot.periods.previous?.let { expenses ->
            when (val tally = tally(expenses, line)) {
                is Outcome.Success -> tally.value.total
                is Outcome.Failure -> return tally
            }
        }
        val names = snapshot.budget.categories.associate { it.category.id to it.category }
        val categories = ArrayList<SmallExpenseCategory>()
        for ((id, of) in small.groupBy { it.categoryId }) {
            // A category Resumen does not list has no spending it counts; nothing is guessed.
            val category = names[id] ?: return Outcome.Failure(SnapshotFailure.Unrepresentable)
            val sum = when (val read = total(of)) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return read
            }
            categories += SmallExpenseCategory(category, of.size, sum)
        }
        val top = categories.sortedWith(
            compareByDescending<SmallExpenseCategory> { it.total.minorUnits }
                .thenByDescending { it.count }
                .thenBy { it.category.name.uniquenessKey }
                .thenBy { it.category.id.value }
        ).take(TOP_CATEGORIES)
        return Outcome.Success(
            SmallExpenses(
                line = line,
                count = small.size,
                total = total,
                sharePercent = percent(total, snapshot.budget.spent),
                previousTotal = previous,
                categories = top
            )
        )
    }

    /** [part] of [whole] in whole percent, half up; [whole] is never zero with a small expense. */
    fun percent(part: Money, whole: Money): Int =
        BigDecimal.valueOf(part.minorUnits).multiply(HUNDRED)
            .divide(BigDecimal.valueOf(whole.minorUnits), 0, RoundingMode.HALF_UP)
            .intValueExact()

    private val HUNDRED = BigDecimal.valueOf(100)

    private fun total(expenses: List<Expense>): Outcome<Money, SnapshotFailure> =
        when (val sum = ExactTotal.of(Currency.COP, expenses.map { it.amount.money }).toMoney()) {
            is Outcome.Success -> sum
            is Outcome.Failure -> Outcome.Failure(SnapshotFailure.Unrepresentable)
        }
}
