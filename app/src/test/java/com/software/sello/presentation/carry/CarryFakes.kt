package com.software.sello.presentation.carry

import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SpendingHistory
import com.software.sello.domain.policy.CategoryInMonth
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.policy.MonthlyBudgetPolicy
import com.software.sello.domain.port.FinancialDay
import com.software.sello.feature.expense.categoryId
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * A month worked out by the real policy with carry: each category in [chains] carries from
 * its start month, with its limit as the default from then on. The rows read are what the
 * SELLO-038 consumer boundary names: the chain from its start through the month before.
 * Tests assert hand-copied figures from docs/testing/signed-carryover-examples.md.
 */
fun carryMonthOf(
    today: String,
    categories: List<CategoryInMonth>,
    expenses: List<Expense>,
    chains: Map<Int, YearMonth>,
    month: YearMonth = YearMonth.from(LocalDate.parse(today))
): MonthlySnapshot {
    val day = FinancialDay(LocalDate.parse(today), ZoneId.of("America/Bogota"))
    val inputs = chains.map { (number, start) ->
        carryInput(categories, expenses, number, start, month, today)
    }
    return (
        MonthlyBudgetPolicy.snapshot(
            month,
            day,
            generation = 1,
            revision = 1,
            categories = categories,
            expenses = expenses,
            carryover = inputs
        ) as Outcome.Success
        ).value
}

/**
 * [number]'s carry input for a [month] snapshot: its chain from [start], its limit as the
 * default from then on, and its expenses from the start through the month before.
 */
fun carryInput(
    categories: List<CategoryInMonth>,
    expenses: List<Expense>,
    number: Int,
    start: YearMonth,
    month: YearMonth,
    today: String
): CategoryCarryInput {
    val line = categories.first { it.category.id == categoryId(number) }
    val base = (line.state as MonthBudgetState.Limited).limit as BudgetLimit.Finite
    val id = line.category.id
    val history = SpendingHistory.of(
        id,
        start,
        maxOf(start, month.minusMonths(1)),
        LocalDate.parse(today),
        expenses.filter { it.categoryId == id }
    ) as Outcome.Success
    return CategoryCarryInput(
        id,
        listOf((CarryChain.of(id, start) as Outcome.Success).value),
        listOf(DefaultLimit(id, start, AutomaticBudget.Limit(base))),
        emptyList(),
        history.value
    )
}
