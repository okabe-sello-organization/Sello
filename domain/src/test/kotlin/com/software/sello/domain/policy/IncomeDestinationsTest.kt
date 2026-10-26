package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where October's income went (SELLO-123), on vie 9 oct 2026. Every sum below was worked
 * out by hand. Only spending counts: goal and investment records are not destinations
 * (ADR 0012 BG4).
 */
class IncomeDestinationsTest {
    private val october = YearMonth.of(2026, 10)
    private val recorded = Instant.parse("2026-10-09T15:04:05Z")
    private val today = FinancialDay(LocalDate.of(2026, 10, 9), ZoneId.of("America/Bogota"))

    private fun uuid(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun id(number: Int) = CategoryId.of(uuid(number)).valueOrFail()

    private fun amount(pesos: Long) = TransactionAmount.of(Money.cop(pesos)).valueOrFail()

    private fun category(number: Int, limited: Boolean = true) = CategoryInMonth(
        Category(
            id(number),
            CategoryName.of("Categoría $number").valueOrFail(),
            IconKey.of("shopping_cart").valueOrFail(),
            false,
            number.toLong(),
            recorded,
            recorded
        ),
        MonthBudgetState.Limited(
            if (limited) {
                BudgetLimit.finite(
                    Money.cop(100_000)
                ).valueOrFail()
            } else {
                BudgetLimit.Unlimited
            },
            explicit = false
        )
    )

    private var expenses = 0

    private fun spent(category: Int, pesos: Long, date: String = "2026-10-02") = Expense(
        ExpenseId.of(uuid(100 + ++expenses)).valueOrFail(),
        id(category),
        LocalDate.parse(date),
        expenses.toLong(),
        amount(pesos),
        null,
        1,
        recorded,
        recorded
    )

    private fun income(number: Int, pesos: Long, date: String) = Income(
        IncomeId.of(uuid(200 + number)).valueOrFail(),
        LocalDate.parse(date),
        number.toLong(),
        amount(pesos),
        IncomeSource.of("salary", null).valueOrFail(),
        null,
        1,
        recorded,
        recorded
    )

    private fun read(
        categories: List<CategoryInMonth>,
        expenses: List<Expense>,
        incomes: List<Income> = emptyList()
    ) = ResumenPolicy.snapshot(
        october,
        today,
        1,
        1,
        ResumenPolicy.monthsEndingIn(october).map { ResumenMonthInput(it, categories, expenses) },
        incomes
    ).valueOrFail()

    private fun tops(snapshot: com.software.sello.domain.model.ResumenSnapshot) =
        snapshot.destinations.top.map { it.category.id to it.spent }

    @Test
    fun theThreeThatTookMostComeFirstAndTheOthersTogether() {
        val snapshot = read(
            (1..5).map { category(it, limited = it != 4) },
            listOf(
                spent(1, 20_000),
                spent(2, 150_000),
                spent(2, 50_000, "2026-10-05"),
                spent(3, 80_000),
                spent(4, 60_000),
                spent(5, 40_000)
            )
        )

        // 200.000, 80.000 and 60.000 (unlimited, still counted); 20.000 + 40.000 together.
        assertEquals(
            listOf(
                id(2) to Money.cop(200_000),
                id(3) to Money.cop(80_000),
                id(4) to Money.cop(60_000)
            ),
            tops(snapshot)
        )
        assertEquals(Money.cop(60_000), snapshot.destinations.rest)
        assertEquals(2, snapshot.destinations.restCount)
    }

    @Test
    fun tiesKeepTheCategoryOrder() {
        val snapshot = read(
            (1..4).map { category(it) },
            (4 downTo 1).map { spent(it, 50_000) }
        )

        assertEquals(listOf(id(1), id(2), id(3)), tops(snapshot).map { it.first })
        assertEquals(Money.cop(50_000), snapshot.destinations.rest)
        assertEquals(1, snapshot.destinations.restCount)
    }

    @Test
    fun threeOrFewerLeaveNothingTogetherAndUntouchedCategoriesAreNotDestinations() {
        val two = read((1..3).map { category(it) }, listOf(spent(3, 30_000), spent(1, 10_000)))
        assertEquals(
            listOf(id(3) to Money.cop(30_000), id(1) to Money.cop(10_000)),
            tops(two)
        )
        assertEquals(Money.cop(0), two.destinations.rest)
        assertEquals(0, two.destinations.restCount)

        val none = read((1..3).map { category(it) }, emptyList())
        assertEquals(emptyList<Pair<CategoryId, Money>>(), tops(none))
        assertEquals(Money.cop(0), none.destinations.rest)
        assertEquals(0, none.destinations.restCount)
    }

    @Test
    fun theIncomeCountIsTheMonthsRecordedIncomesUpToToday() {
        val snapshot = read(
            listOf(category(1)),
            emptyList(),
            listOf(
                income(1, 1_600_000, "2026-10-01"),
                income(2, 250_000, "2026-10-09"),
                // Dated after today: not counted, as the total leaves it out.
                income(3, 150_000, "2026-10-20"),
                income(4, 100_000, "2026-09-30")
            )
        )

        assertEquals(2, snapshot.incomeCount)
        assertEquals(Money.cop(1_850_000), snapshot.income)
    }
}
