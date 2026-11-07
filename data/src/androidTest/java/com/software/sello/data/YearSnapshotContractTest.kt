package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.YearSnapshot
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.YearSnapshotState
import java.math.BigInteger
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tu año's read on real Room: one transaction, the first year, archived and damaged rows. */
@RunWith(AndroidJUnit4::class)
class YearSnapshotContractTest {
    @get:Rule
    val files = DatabaseFiles()
    private val ledger by lazy { TestLedger(files).also { it.start() } }

    /** Mercado, record 100. */
    private suspend fun category() {
        assertTrue(
            ledger.categories.submit(ledger.createCategory("Mercado")) is CommandOutcome.Committed
        )
    }

    private suspend fun spend(pesos: Long, date: String) {
        assertTrue(
            ledger.expenses.create(ledger.createExpense(100, date, pesos))
                is CommandOutcome.Committed
        )
    }

    private suspend fun income(pesos: Long, date: String) {
        assertTrue(
            ledger.incomes.create(
                CreateIncome(
                    ledger.operation(),
                    1,
                    TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
                    IncomeSource.of("salary", null).valueOrFail(),
                    LocalDate.parse(date),
                    null
                )
            ) is CommandOutcome.Committed
        )
    }

    private suspend fun read(year: Int): YearSnapshot = withTimeout(15_000) {
        val state = ledger.years.observe(year).first { it !is ReadState.Loading }
        (state as ReadState.Ready).value
    }

    @Test
    fun aNewInstallationIsTheCurrentYearWithNothingSpentAndTheReadWritesNothing() = runBlocking {
        val year = read(2026)

        assertEquals(2026, year.firstYear)
        assertEquals(Money.cop(0), year.spent)
        assertEquals(Money.cop(0), year.income)
        assertNull(year.comparison)
        assertEquals(12, year.months.size)
        assertEquals(0, year.expenseCount + year.incomeCount)
        assertEquals(0L, ledger.revision())
        for (table in listOf("income", "expense", "operation_receipt")) {
            assertEquals(0L, ledger.database.count(table))
        }
    }

    @Test
    fun theFirstYearComesFromAnIncomeAlone() = runBlocking {
        income(500_000, "2024-03-10")

        assertEquals(2024, read(2026).firstYear)
    }

    @Test
    fun theFirstYearComesFromExpensesAndADeletedOneDoesNotCount() = runBlocking {
        category()
        spend(10_000, "2023-06-01")
        spend(20_000, "2025-02-01")
        assertEquals(2023, read(2026).firstYear)

        assertTrue(
            ledger.expenses.delete(
                DeleteExpense(ledger.operation(), 1, ExpenseId.of(uuid(101)).valueOrFail(), 1)
            ) is CommandOutcome.Committed
        )

        assertEquals(2025, read(2026).firstYear)
    }

    @Test
    fun anArchivedCategoryIsListedAndTheYearBeforeIsComparedFromStoredRows() = runBlocking {
        category()
        spend(30_000, "2025-01-01")
        spend(20_000, "2025-10-09")
        // After 9 October: not the same days.
        spend(99_000, "2025-10-10")
        spend(40_000, "2026-03-01")
        assertTrue(
            ledger.categories.submit(
                ArchiveCategory(ledger.operation(), 1, ledger.categoryId(100), 1)
            ) is CommandOutcome.Committed
        )

        val year = read(2026)

        // 2026 to 9 October: 40.000. 2025 to 9 October: 30.000 + 20.000 = 50.000.
        // 10.000 less; 10.000 / 50.000 = 20,0 %.
        assertEquals(Money.cop(40_000), year.spent)
        val comparison = year.comparison!!
        assertEquals(Money.cop(50_000), comparison.previousSpent)
        assertEquals(Money.cop(10_000), comparison.difference)
        assertEquals(SpendingDirection.Less, comparison.direction)
        assertEquals(BigInteger.valueOf(200), comparison.percentTenths)
        val mercado = year.categories.single()
        assertEquals("Mercado", mercado.category.name.value)
        assertTrue(mercado.category.archived)
        assertEquals(Money.cop(40_000) to 100, mercado.spent to mercado.percent)
    }

    @Test
    fun simultaneousWritesCannotMixMonthsAndIncomesFromDifferentRevisions() = runBlocking {
        category()
        val seen = mutableListOf<YearSnapshot>()
        coroutineScope {
            // Expenses in February and incomes in September: months read apart would mix.
            val writer = launch(Dispatchers.Default) {
                repeat(12) {
                    spend(1_000, "2026-02-10")
                    income(2_000, "2026-09-10")
                }
            }
            launch(Dispatchers.Default) {
                while (writer.isActive) seen += read(2026)
                seen += read(2026)
            }
        }
        for (year in seen) {
            // Revision 1 is the category; then an expense, an income, an expense…
            val changes = year.revision - 1
            val spent = 1_000 * ((changes + 1) / 2)
            val received = 2_000 * (changes / 2)
            assertEquals(Money.cop(spent), year.spent)
            assertEquals(Money.cop(spent), year.months[1].spent)
            assertEquals(Money.cop(received), year.income)
            assertEquals(Money.cop(received - spent), year.netFlow)
        }
        assertEquals(25L, seen.last().revision)
    }

    @Test
    fun aDamagedRowFailsWithTheLastGoodYearNeverZeros() = runBlocking {
        category()
        spend(25_000, "2026-02-03")
        val states = Channel<YearSnapshotState>(Channel.UNLIMITED)
        val collector = launch { ledger.years.observe(2026).collect(states::send) }
        suspend fun next(matches: (YearSnapshotState) -> Boolean): YearSnapshotState =
            withTimeout(15_000) {
                var state = states.receive()
                while (!matches(state)) state = states.receive()
                state
            }
        try {
            val good = (next { it is ReadState.Ready } as ReadState.Ready).value
            assertEquals(Money.cop(25_000), good.spent)
            ledger.database.openHelper.writableDatabase.execSQL(
                "UPDATE expense SET amount_minor = 0"
            )
            ledger.goTo("2026-10-10")

            val failed = next { it is ReadState.Failed } as ReadState.Failed
            assertEquals(good, failed.lastGood)
            assertEquals(
                SnapshotFailure.Storage(
                    StorageFailure.Integrity("expense", uuid(101), "amount_minor")
                ),
                failed.failure
            )
        } finally {
            collector.cancel()
        }
    }
}
