package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CategoryInsightsState
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseOrder
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.SetDefaultLimit
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryInsightsContractTest {
    @get:Rule val files = DatabaseFiles()
    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val october = YearMonth.of(2026, 10)
    private val id get() = ledger.categoryId(100)

    private suspend fun category() {
        ledger.categories.submit(ledger.createCategory("Comida", finite(200_000)))
    }
    private suspend fun spend(pesos: Long, date: String = "2026-10-01", note: String? = null) {
        assertTrue(
            ledger.expenses.create(
                ledger.createExpense(100, date, pesos).copy(
                    note = note?.let {
                        Note.of(it).valueOrFail()
                    }
                )
            ) is CommandOutcome.Committed
        )
    }
    private suspend fun read() = ledger.categoryInsights.read(id, october).valueOrFail()

    @Test
    fun realHistoryBudgetAndStatisticsAgreeAndSurviveReopeningWithoutWriting() = runBlocking {
        ledger.goTo("2026-08-03")
        category()
        spend(20_000, "2026-08-03")
        ledger.goTo("2026-10-22")
        spend(100_000)
        val revision = ledger.revision()
        val result = read()
        assertEquals(Money.cop(100_000), result.budget.spent)
        assertEquals(Money.cop(100_000), result.averageExpense)
        assertEquals(Money.cop(70_000), result.weeks.last().remainingAllowance)
        assertEquals(Money.cop(10_000), result.historicalAverage)
        assertEquals(listOf(20_000L, 0L), result.history.map { it.spent.minorUnits })
        assertEquals(revision, result.budget.revision)
        assertEquals(revision, ledger.revision())
        assertEquals(0L, ledger.database.count("month_limit"))
        ledger.reopen()
        assertEquals(result, read())
    }

    @Test
    fun eachHistoricalMonthKeepsItsOwnLimit() = runBlocking {
        ledger.goTo("2026-08-03")
        category()
        ledger.goTo("2026-09-03")
        ledger.categories.submit(SetDefaultLimit(ledger.operation(), 1, id, 1, finite(300_000)))
        ledger.goTo("2026-10-22")
        assertEquals(listOf(200_000L, 300_000L), read().history.map { it.limit!!.minorUnits })
    }

    @Test
    fun concurrentWritesCannotMixTheCountAverageAndBudgetRevision() = runBlocking {
        category()
        ledger.goTo("2026-10-22")
        coroutineScope {
            val writer = launch(Dispatchers.Default) { repeat(12) { spend(1_000) } }
            launch(Dispatchers.Default) {
                do {
                    val result = read()
                    val count = result.budget.categories.single().expenseCount
                    assertEquals(1_000L * count, result.budget.spent.minorUnits)
                    assertEquals(1L + count, result.budget.revision)
                    if (count > 0) assertEquals(Money.cop(1_000), result.averageExpense)
                    assertEquals(
                        result.budget.spent.minorUnits,
                        result.weeks.sumOf {
                            it.spent.minorUnits
                        }
                    )
                } while (writer.isActive)
            }
        }
        assertEquals(Money.cop(12_000), read().budget.spent)
    }

    @Test
    fun corruptHistoryFailsRatherThanBecomingAZeroMonthAndClosedStorageIsTyped() = runBlocking {
        category()
        spend(20_000, "2026-09-03")
        ledger.database.openHelper.writableDatabase.execSQL("UPDATE expense SET amount_minor = 0")
        assertEquals(
            Outcome.Failure<SnapshotFailure>(
                SnapshotFailure.Storage(
                    StorageFailure.Integrity("expense", uuid(101), "amount_minor")
                )
            ),
            ledger.categoryInsights.read(id, october)
        )
        ledger.database.close()
        assertTrue(
            (
                ledger.categoryInsights.read(
                    id,
                    october
                ) as Outcome.Failure
                ).error is SnapshotFailure.Storage
        )
    }

    @Test
    fun observationRecomputesOnFinancialDayRolloverAndCancellationStopsCollection() = runBlocking {
        category()
        val states = Channel<CategoryInsightsState>(Channel.UNLIMITED)
        val collector =
            launch { ledger.categoryInsights.observe(id, october).collect(states::send) }
        suspend fun ready(): ReadState.Ready<CategoryInsights> = withTimeout(10_000) {
            var state = states.receive()
            while (state !is ReadState.Ready) state = states.receive()
            state
        }
        try {
            assertEquals(9, ready().value.elapsedDays)
            ledger.goTo("2026-10-22")
            assertEquals(22, ready().value.elapsedDays)
        } finally {
            collector.cancel()
            collector.join()
        }
        assertTrue(collector.isCancelled)
    }

    private suspend fun search(query: String) = ledger.categoryExpenseSearch.read(
        id,
        october,
        ExpenseOrder.Date,
        query,
        0,
        20
    ).valueOrFail().expenses.map { it.note!!.value }

    /**
     * Search ignores case and accents for every letter (SELLO-165): "\u00d1AME \u00c1REA"
     * is found as "\u00f1ame", "\u00e1rea", "name" and "area", and accents typed as
     * combining marks are ignored the same way.
     */
    @Test
    fun searchIgnoresCaseAndAccentsForEveryLetter() = runBlocking {
        category()
        spend(1_000, note = "\u00d1AME \u00c1REA")
        spend(2_000, note = "Cafe\u0301 con pan")
        spend(3_000, note = "Mercado")

        assertEquals(listOf("\u00d1AME \u00c1REA"), search("\u00f1ame"))
        assertEquals(listOf("\u00d1AME \u00c1REA"), search("\u00e1rea"))
        assertEquals(listOf("\u00d1AME \u00c1REA"), search("name"))
        assertEquals(listOf("\u00d1AME \u00c1REA"), search("AREA"))
        // Kept composed, as every note is.
        assertEquals(listOf("Caf\u00e9 con pan"), search("cafe\u0301"))
        assertEquals(listOf("Caf\u00e9 con pan"), search("CAFE"))
    }

    /** An edit or an undone delete finds the note as it is now, accents ignored. */
    @Test
    fun anEditedOrRestoredNoteIsFoundByWhatItSaysNow() = runBlocking {
        category()
        spend(1_000, note = "Mercado")
        val expense = ledger.categoryExpenseSearch.read(
            id,
            october,
            ExpenseOrder.Date,
            "",
            0,
            20
        ).valueOrFail().expenses.single()

        val edited = ledger.expenses.edit(
            EditExpense(
                ledger.operation(),
                1,
                expense.id,
                expense.version,
                id,
                expense.amount,
                expense.date,
                Note.of("Panader\u00eda").valueOrFail()
            )
        )
        assertTrue(edited is CommandOutcome.Committed)
        assertEquals(listOf("Panader\u00eda"), search("PANADERIA"))
        assertEquals(emptyList<String>(), search("mercado"))

        val deletion = ledger.operation()
        ledger.expenses.delete(DeleteExpense(deletion, 1, expense.id, expense.version + 1))
        assertEquals(emptyList<String>(), search("panaderia"))
        ledger.expenses.restore(RestoreExpense(ledger.operation(), 1, deletion))
        assertEquals(listOf("Panader\u00eda"), search("panaderia"))
    }

    /**
     * "Nombre" folds case and accents as search does (SELLO-173): "\u00c1lbum" sits between
     * "Agua" and "Bus", and "\u00d1ame" and "\u00f1ame" read as "name", between "Mesa"
     * and "Nube", never after "Zapato". SQLite's NOCASE folds only ASCII letters.
     */
    @Test
    fun nameOrderFoldsCaseAndAccentsAsSearchDoes() = runBlocking {
        category()
        listOf("Zapato", "\u00f1ame", "Nube", "Bus", "\u00c1lbum", "Mesa", "\u00d1ame", "Agua")
            .forEachIndexed { index, note -> spend(1_000L + index, note = note) }

        val byName = ledger.categoryExpenseSearch.read(
            id,
            october,
            ExpenseOrder.Name,
            "",
            0,
            20
        ).valueOrFail().expenses.map { it.note!!.value }

        assertEquals(
            listOf("Agua", "\u00c1lbum", "Bus", "Mesa", "\u00d1ame", "\u00f1ame", "Nube", "Zapato"),
            byName
        )
    }

    @Test
    fun searchAndOrderingApplyBeforePagingAcrossTheWholeMonth() = runBlocking {
        category()
        repeat(25) { index ->
            spend(
                1_000L + index,
                note = if (index ==
                    0
                ) {
                    "Zeta 100%"
                } else {
                    "Compra $index"
                }
            )
        }
        val first = ledger.categoryExpenseSearch.read(
            id,
            october,
            ExpenseOrder.Amount,
            "",
            0,
            20
        ).valueOrFail()
        val second = ledger.categoryExpenseSearch.read(
            id,
            october,
            ExpenseOrder.Amount,
            "",
            20,
            20
        ).valueOrFail()
        assertEquals(1_024L, first.expenses.first().amount.money.minorUnits)
        assertEquals(true, first.more)
        assertEquals(25L, first.totalCount)
        assertEquals(5, second.expenses.size)
        assertEquals(false, second.more)
        assertEquals(25, (first.expenses + second.expenses).map { it.id }.toSet().size)
        val found = ledger.categoryExpenseSearch.read(
            id,
            october,
            ExpenseOrder.Date,
            "100%",
            0,
            20
        ).valueOrFail()
        assertEquals(listOf("Zeta 100%"), found.expenses.map { it.note!!.value })
        assertEquals(1L, found.totalCount)
        assertEquals(ledger.revision(), found.revision)
        val byName = ledger.categoryExpenseSearch.read(
            id,
            october,
            ExpenseOrder.Name,
            "",
            0,
            20
        ).valueOrFail()
        assertEquals("Compra 1", byName.expenses.first().note!!.value)
        val absent = ledger.categoryExpenseSearch.read(
            id,
            october.minusMonths(1),
            ExpenseOrder.Date,
            "",
            0,
            20
        ).valueOrFail()
        assertEquals(emptyList<Any>(), absent.expenses)
    }
}
