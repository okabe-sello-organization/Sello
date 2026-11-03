package com.software.sello.data

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomCategoryRemovals
import com.software.sello.data.repository.carryRows
import com.software.sello.data.repository.monthExpenses
import com.software.sello.data.repository.monthIncomes
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Note
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.Disposal
import com.software.sello.domain.port.ExpenseOrder
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * December 9999 is the last month a date can be stored in. Every read bounded by a month
 * finds its entries there, and November 9999 keeps only its own (#278).
 */
@RunWith(AndroidJUnit4::class)
class LastStorableMonthTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }

    private val november = YearMonth.of(9999, 11)
    private val december = YearMonth.of(9999, 12)

    /** Mercado (100) with expenses and incomes on both sides of the November edge. */
    private fun entriesAroundTheLastMonth() = runBlocking {
        ledger.goTo("9999-12-31")
        ledger.categories.submit(ledger.createCategory("Mercado"))
        listOf(
            "9999-11-30" to 1_000L,
            "9999-12-01" to 2_000L,
            "9999-12-31" to 3_000L
        ).forEach { (date, pesos) ->
            ledger.expenses.create(ledger.createExpense(100, date, pesos))
        }
        listOf("9999-11-30" to 40_000L, "9999-12-31" to 50_000L).forEach { (date, pesos) ->
            ledger.incomes.create(
                CreateIncome(
                    ledger.operation(),
                    1,
                    transactionPesos(pesos),
                    IncomeSource.of("salary", null).valueOrFail(),
                    LocalDate.parse(date),
                    Note.of(null).valueOrFail()
                )
            )
        }
    }

    private fun List<Pair<LocalDate, Long>>.shown() = map { (date, pesos) -> "$date $pesos" }

    @Test
    fun theMonthReadsBehindRecibosAndResumensFiguresFindDecember() = runBlocking {
        entriesAroundTheLastMonth()

        suspend fun expenses(month: YearMonth) = ledger.database.monthExpenses(month).valueOrFail()
            .map { it.date to it.amount.money.minorUnits }.shown()
        suspend fun incomes(month: YearMonth) = ledger.database.monthIncomes(month).valueOrFail()
            .map { it.date to it.amount.money.minorUnits }.shown()

        assertEquals(listOf("9999-12-01 2000", "9999-12-31 3000"), expenses(december))
        assertEquals(listOf("9999-11-30 1000"), expenses(november))
        assertEquals(listOf("9999-12-31 50000"), incomes(december))
        assertEquals(listOf("9999-11-30 40000"), incomes(november))
    }

    @Test
    fun aCategoryMonthAndItsSearchListDecember() = runBlocking {
        entriesAroundTheLastMonth()

        suspend fun listed(month: YearMonth) = ledger.expenseReads
            .inCategory(ledger.categoryId(100), month, after = null, limit = 50).valueOrFail()
            .expenses.map { it.date to it.amount.money.minorUnits }.shown()
        suspend fun searched(month: YearMonth) = ledger.categoryExpenseSearch
            .read(ledger.categoryId(100), month, ExpenseOrder.Date, "", 0, 50).valueOrFail()

        assertEquals(listOf("9999-12-31 3000", "9999-12-01 2000"), listed(december))
        assertEquals(listOf("9999-11-30 1000"), listed(november))
        assertEquals(2L, searched(december).totalCount)
        assertEquals(
            listOf("9999-12-31 3000", "9999-12-01 2000"),
            searched(december).expenses.map { it.date to it.amount.money.minorUnits }.shown()
        )
        assertEquals(1L, searched(november).totalCount)
    }

    @Test
    fun incomeHistoryAndItsMonthTotalCountDecember() = runBlocking {
        entriesAroundTheLastMonth()

        suspend fun listed(month: YearMonth) = ledger.incomeReads
            .inMonth(month, after = null, limit = 50).valueOrFail()
            .incomes.map { it.date to it.amount.money.minorUnits }.shown()

        assertEquals(listOf("9999-12-31 50000"), listed(december))
        assertEquals(listOf("9999-11-30 40000"), listed(november))
        val decemberTotal = ledger.incomeReads.month(december).valueOrFail()
        assertEquals(50_000L, decemberTotal.total.minorUnits)
        assertEquals(1, decemberTotal.count)
        assertEquals(40_000L, ledger.incomeReads.month(november).valueOrFail().total.minorUnits)
    }

    @Test
    fun carryRowsAndTheDeletePreviewCountDecember() = runBlocking {
        entriesAroundTheLastMonth()

        val carried = ledger.database.withTransaction {
            ledger.database.carryRows(ledger.categoryId(100), emptyList(), november, december)
        }.valueOrFail().expenses.map { it.date to it.amount.money.minorUnits }.shown()
        val preview = RoomCategoryRemovals(ledger.database, ledger.clock)
            .preview(ledger.categoryId(100), Disposal.DeleteAll).valueOrFail()

        assertEquals(listOf("9999-11-30 1000", "9999-12-01 2000", "9999-12-31 3000"), carried)
        assertEquals(december, preview.month)
        assertEquals(5_000L, preview.spentThisMonth.minorUnits)
        assertEquals(3, preview.expenses)
        assertEquals(6_000L, preview.total.minorUnits)
    }
}
