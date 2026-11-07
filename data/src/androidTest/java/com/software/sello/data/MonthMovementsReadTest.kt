package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomMonthMovements
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.Movement
import com.software.sello.domain.port.MovementFilter
import com.software.sello.domain.port.MovementPage
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Día a día's month read (SELLO-192): expenses and incomes as one list, on a real file. */
@RunWith(AndroidJUnit4::class)
class MonthMovementsReadTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val movements by lazy { RoomMonthMovements(ledger.database) }
    private val october = YearMonth.of(2026, 10)
    private var second = 0L

    /** Each write a second after the one before, so "added last" is told apart. */
    private fun nextMoment() {
        ledger.audit = Instant.parse("2026-11-01T12:00:00Z").plusSeconds(second++)
    }

    private fun spend(category: Int, date: String, pesos: Long) = runBlocking {
        nextMoment()
        ledger.expenses.create(ledger.createExpense(category, date, pesos))
    }

    private fun receive(date: String, pesos: Long) = runBlocking {
        nextMoment()
        ledger.incomes.create(
            CreateIncome(
                operationId = ledger.operation(),
                generation = 1,
                amount = TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
                source = IncomeSource.Salary,
                date = LocalDate.parse(date),
                note = Note.of(null).valueOrFail()
            )
        )
    }

    /** Mercado (100) and Transporte (101, archived at the end), around October's edges. */
    private fun october() = runBlocking {
        ledger.goTo("2026-11-30")
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.categories.submit(ledger.createCategory("Transporte"))
        spend(100, "2026-10-06", 18_000)
        spend(101, "2026-10-06", 2_950)
        receive("2026-10-03", 2_500_000)
        spend(100, "2026-10-06", 8_000)
        spend(100, "2026-09-30", 1_000)
        receive("2026-10-06", 650_000)
        spend(101, "2026-10-01", 15_000)
        spend(100, "2026-11-01", 4_000)
        ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, ledger.categoryId(101), 1))
    }

    private fun read(filter: MovementFilter, offset: Int = 0, limit: Int = 50) = runBlocking {
        movements.read(october, filter, offset, limit).valueOrFail()
    }

    private fun MovementPage.shown() = movements.map {
        when (it) {
            is Movement.Spent ->
                "${it.date} ${it.category.name.value} ${it.expense.amount.money.minorUnits}"

            is Movement.Received -> "${it.date} + ${it.income.amount.money.minorUnits}"
        }
    }

    @Test
    fun theMonthListsExpensesAndIncomesNewestDayFirstThenAddedLastFirst() {
        october()

        val all = read(MovementFilter.All)

        assertEquals(
            listOf(
                "2026-10-06 + 650000",
                "2026-10-06 Mercado 8000",
                "2026-10-06 Transporte 2950",
                "2026-10-06 Mercado 18000",
                "2026-10-03 + 2500000",
                "2026-10-01 Transporte 15000"
            ),
            all.shown()
        )
        assertEquals(6L, all.totalCount)
        assertFalse(all.more)
        assertEquals(ledger.revision(), all.revision)
        // The archived category's expenses stay listed under its name.
        assertTrue((all.movements.last() as Movement.Spent).category.archived)
    }

    @Test
    fun aDaySumsEveryExpenseOfItsDayAndAnIncomeOnlyDayHasNone() {
        october()

        val days = read(MovementFilter.All, limit = 1).days

        // 18.000 + 2.950 + 8.000 = 28.950, worked by hand; the 650.000 income is not netted.
        assertEquals(
            mapOf(
                LocalDate.parse("2026-10-06") to Money.cop(28_950),
                LocalDate.parse("2026-10-01") to Money.cop(15_000)
            ),
            days
        )
    }

    @Test
    fun theFiltersListOnlyExpensesOrOnlyIncomes() {
        october()

        val expenses = read(MovementFilter.Expenses)
        val incomes = read(MovementFilter.Incomes)

        assertEquals(
            listOf(
                "2026-10-06 Mercado 8000",
                "2026-10-06 Transporte 2950",
                "2026-10-06 Mercado 18000",
                "2026-10-01 Transporte 15000"
            ),
            expenses.shown()
        )
        assertEquals(4L, expenses.totalCount)
        assertEquals(listOf("2026-10-06 + 650000", "2026-10-03 + 2500000"), incomes.shown())
        assertEquals(2L, incomes.totalCount)
    }

    @Test
    fun pagesFollowEachOtherWithoutRepeatingOrSkipping() {
        october()

        val pages = listOf(0, 2, 4).map { read(MovementFilter.All, offset = it, limit = 2) }

        assertEquals(listOf(true, true, false), pages.map { it.more })
        assertEquals(read(MovementFilter.All).shown(), pages.flatMap { it.shown() })
    }

    @Test
    fun anEmptyMonthIsAnEmptyListNotAFailure() {
        october()

        val august = runBlocking {
            movements.read(YearMonth.of(2026, 8), MovementFilter.All, 0, 50).valueOrFail()
        }

        assertEquals(emptyList<String>(), august.shown())
        assertEquals(0L, august.totalCount)
        assertEquals(emptyMap<LocalDate, Money>(), august.days)
    }
}
