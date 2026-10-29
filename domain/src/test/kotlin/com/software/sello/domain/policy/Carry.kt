package com.software.sello.domain.policy

import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CarryMonth
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.SpendingHistory
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Shared inputs for the signed carryover tests. Every figure asserted in those tests is
 * copied from docs/testing/signed-carryover-examples.md, except the review round 1 tests,
 * whose hand-worked values carry their arithmetic in a comment; nothing here calculates one.
 */
internal object Carry {
    private val recorded = Instant.parse("2027-01-09T15:04:05Z")
    private var nextExpense = 5_000

    val comida = id(1)
    val transporte = id(2)
    val hogar = id(3)

    fun id(number: Int) =
        CategoryId.of("00000000-0000-4000-8000-%012d".format(number)).valueOrFail()

    fun cop(pesos: Long) = Money.cop(pesos)

    fun m(year: Int, month: Int): YearMonth = YearMonth.of(year, month)

    val ene = m(2027, 1)
    val feb = m(2027, 2)
    val mar = m(2027, 3)
    val abr = m(2027, 4)

    fun finite(pesos: Long) = BudgetLimit.finite(cop(pesos)).valueOrFail()

    fun category(id: CategoryId, name: String, archived: Boolean = false) = Category(
        id,
        CategoryName.of(name).valueOrFail(),
        IconKey.of("home").valueOrFail(),
        archived,
        version = 1,
        createdAt = recorded,
        updatedAt = recorded
    )

    fun monthly(from: YearMonth, pesos: Long, of: CategoryId = comida) =
        DefaultLimit(of, from, AutomaticBudget.Limit(finite(pesos)))

    fun unlimitedFrom(from: YearMonth, of: CategoryId = comida) =
        DefaultLimit(of, from, AutomaticBudget.Limit(BudgetLimit.Unlimited))

    fun pausedFrom(from: YearMonth, of: CategoryId = comida) =
        DefaultLimit(of, from, AutomaticBudget.Paused)

    fun own(month: YearMonth, limit: BudgetLimit, of: CategoryId = comida) =
        MonthLimit(of, month, limit)

    fun expense(of: CategoryId, date: String, pesos: Long) = Expense(
        id = ExpenseId.of("00000000-0000-4000-8000-%012d".format(nextExpense)).valueOrFail(),
        categoryId = of,
        date = LocalDate.parse(date),
        sequence = (nextExpense++).toLong(),
        amount = TransactionAmount.of(cop(pesos)).valueOrFail(),
        note = null,
        version = 1,
        createdAt = recorded,
        updatedAt = recorded
    )

    fun chain(start: YearMonth, end: YearMonth? = null, of: CategoryId = comida) =
        CarryChain.of(of, start, end).valueOrFail()

    fun history(
        of: CategoryId,
        from: YearMonth,
        through: YearMonth,
        asOf: String,
        vararg expenses: Expense
    ) = SpendingHistory.of(
        of,
        from,
        through,
        LocalDate.parse(asOf),
        expenses.toList()
    ).valueOrFail()

    /** Comida with base 100.000 in every month, the chains given and [spent] as (date, pesos). */
    fun comidaInput(
        chains: List<CarryChain>,
        from: YearMonth,
        through: YearMonth,
        asOf: String,
        spent: List<Pair<String, Long>>,
        defaults: List<DefaultLimit> = listOf(monthly(ene, 100_000)),
        overrides: List<MonthLimit> = emptyList(),
        of: CategoryId = comida
    ) = CategoryCarryInput(
        of,
        chains,
        defaults,
        overrides,
        history(
            of,
            from,
            through,
            asOf,
            *spent.map {
                expense(of, it.first, it.second)
            }.toTypedArray()
        )
    )

    fun CarryMonth.figures() = listOf(base, carryIn, capacity, spent, remaining).map {
        it.minorUnits
    }
}
