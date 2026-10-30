package com.software.sello.domain.policy

import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryComparison
import com.software.sello.domain.model.CategoryHistoryMonth
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.domain.model.CategoryWeek
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.port.FinancialDay
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.YearMonth

/**
 * One month of a category's history. [carryover] is the category's carry input when a chain
 * covers [month] (ADR 0020 CP19: the remaining week and day figures use `C` and `R`); null
 * when it does not carry then.
 */
data class CategoryHistoryInput(
    val month: YearMonth,
    val category: CategoryInMonth?,
    val expenses: List<Expense>,
    val carryover: CategoryCarryInput? = null
)

/** Exact category actuals and bounded planning figures, under ADR 0015. */
object CategoryInsightsPolicy {
    fun monthsEndingIn(month: YearMonth): List<YearMonth> =
        (6 downTo 0).map { month.minusMonths(it.toLong()) }.filter { it.year >= 0 }

    fun snapshot(
        id: CategoryId,
        month: YearMonth,
        today: FinancialDay,
        generation: Long,
        revision: Long,
        inputs: List<CategoryHistoryInput>
    ): Outcome<CategoryInsights, SnapshotFailure> {
        if (month > YearMonth.from(today.date)) {
            return Outcome.Failure(SnapshotFailure.FutureMonth(YearMonth.from(today.date)))
        }
        require(inputs.map { it.month } == monthsEndingIn(month))
        val selected = inputs.last()
        val budgets = inputs.map { input ->
            when (
                val result = MonthlyBudgetPolicy.snapshot(
                    input.month,
                    today,
                    generation,
                    revision,
                    listOfNotNull(input.category),
                    input.expenses.filter { it.categoryId == id },
                    carryover = listOfNotNull(input.carryover)
                )
            ) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        }
        val budget = budgets.last()
        val current = month == YearMonth.from(today.date)
        val elapsed = if (current) today.date.dayOfMonth else month.lengthOfMonth()
        val counted = actuals(selected, id, elapsed)
        val spent = budget.spent.minorUnits.toBigInteger()
        val remaining = budget.categories.singleOrNull()?.budget?.remaining
        val allowance = remaining?.takeIf { current }?.let {
            MonthlyBudgetPolicy.allowance(it, month.lengthOfMonth() - elapsed + 1)
        }
        val weeks = (1..elapsed step 7).map { start ->
            val end = minOf(start + 6, month.lengthOfMonth())
            val isCurrent = current && elapsed in start..end
            val available = if (isCurrent && remaining != null) {
                val value = remaining.minorUnits.toBigInteger().max(BigInteger.ZERO)
                Money.cop(
                    (
                        value * (end - elapsed + 1).toBigInteger() /
                            (month.lengthOfMonth() - elapsed + 1).toBigInteger()
                        ).toLong()
                )
            } else {
                null
            }
            val recorded = sum(counted.filter { it.date.dayOfMonth in start..end })
            val ceiling = recorded + (available?.minorUnits ?: 0).toBigInteger()
            if (ceiling.bitLength() >=
                Long.SIZE_BITS
            ) {
                return Outcome.Failure(SnapshotFailure.Unrepresentable)
            }
            CategoryWeek(
                (start - 1) / 7 + 1,
                month.atDay(start),
                month.atDay(end),
                Money.cop(recorded.toLong()),
                isCurrent,
                available,
                Money.cop(ceiling.toLong())
            )
        }
        val completed = weeks.filter { !it.current }
        val previous = inputs.firstOrNull { it.month == month.minusMonths(1) }
        val previousKnown = previous?.category?.state != null &&
            previous.category.state != MonthBudgetState.Unconfigured
        val previousSpent = previous?.let {
            sum(
                actuals(
                    it,
                    id,
                    if (current) {
                        minOf(
                            elapsed,
                            it.month.lengthOfMonth()
                        )
                    } else {
                        it.month.lengthOfMonth()
                    }
                )
            )
        }
        val comparison = if (previousKnown && previousSpent != null && previousSpent.signum() > 0) {
            val percent = BigDecimal((spent - previousSpent) * BigInteger.valueOf(1000))
                .divide(BigDecimal(previousSpent), 0, RoundingMode.HALF_UP).toBigIntegerExact()
            CategoryComparison(previous.month, percent)
        } else {
            null
        }
        val history = mutableListOf<CategoryHistoryMonth>()
        for (input in inputs.filter { !current || it.month < month }.takeLast(6)) {
            if (input.category == null ||
                input.category.state == MonthBudgetState.Unconfigured
            ) {
                continue
            }
            val total = sum(actuals(input, id, input.month.lengthOfMonth()))
            if (total.bitLength() >=
                Long.SIZE_BITS
            ) {
                return Outcome.Failure(SnapshotFailure.Unrepresentable)
            }
            // A month a chain covers is judged on base + carry, as its own snapshot is (CP14).
            val carried = budgets[inputs.indexOf(input)].categories.singleOrNull()?.budget
                ?.takeIf { it.carry != null }
            val finite = carried?.limit ?: (
                (input.category.state as? MonthBudgetState.Limited)?.limit as?
                    com.software.sello.domain.model.BudgetLimit.Finite
                )?.amount
            history += CategoryHistoryMonth(
                input.month,
                Money.cop(total.toLong()),
                finite,
                carried?.carry
            )
        }
        val weekday = counted.groupBy { it.date.dayOfWeek }.entries
            .sortedBy { it.key.value }.maxByOrNull { sum(it.value) }?.key
        return Outcome.Success(
            CategoryInsights(
                budget, id, allowance, comparison, elapsed,
                counted.takeIf { it.isNotEmpty() }?.let { average(spent, it.size) },
                counted.maxWithOrNull(
                    compareBy<Expense> { it.amount.money.minorUnits }
                        .thenBy { it.date }.thenBy { it.sequence }
                ),
                weekday,
                completed.takeIf { it.isNotEmpty() }?.let {
                    average(
                        it.fold(BigInteger.ZERO) { total, week ->
                            total +
                                week.spent.minorUnits.toBigInteger()
                        },
                        it.size
                    )
                },
                weeks,
                history.takeIf { it.size >= 2 }?.let {
                    average(
                        it.fold(BigInteger.ZERO) { total, entry ->
                            total +
                                entry.spent.minorUnits.toBigInteger()
                        },
                        it.size
                    )
                },
                history
            )
        )
    }

    private fun actuals(input: CategoryHistoryInput, id: CategoryId, day: Int) =
        input.expenses.filter {
            it.categoryId == id && YearMonth.from(it.date) == input.month &&
                it.date.dayOfMonth <= day
        }

    private fun sum(expenses: List<Expense>): BigInteger = expenses.fold(BigInteger.ZERO) {
            total,
            expense
        ->
        total + expense.amount.money.minorUnits.toBigInteger()
    }

    private fun average(total: BigInteger, count: Int): Money = Money.cop(
        BigDecimal(total).divide(BigDecimal(count), 0, RoundingMode.HALF_UP).longValueExact()
    )
}
