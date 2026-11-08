package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomHistoryStartReads
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.HistoryStart
import com.software.sello.domain.port.HistoryStartState
import com.software.sello.domain.port.ReadState
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Where the history begins, for the Guía hint and the backup reminder (SELLO-211). */
@RunWith(AndroidJUnit4::class)
class HistoryStartReadsTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val reads by lazy { RoomHistoryStartReads(ledger.database) }

    private fun read(): HistoryStartState = runBlocking {
        withTimeout(15_000) { reads.observe().first { it !is ReadState.Loading } }
    }

    private fun receive(date: String) = runBlocking {
        ledger.incomes.create(
            CreateIncome(
                operationId = ledger.operation(),
                generation = 1,
                amount = TransactionAmount.of(Money.cop(50_000)).valueOrFail(),
                source = IncomeSource.Salary,
                date = LocalDate.parse(date),
                note = Note.of(null).valueOrFail()
            )
        )
    }

    @Test
    fun anEmptyHistoryHasNoStart() {
        assertEquals(ReadState.Ready(HistoryStart(null, null)), read())
    }

    @Test
    fun anIncomeAloneIsAMovementButNoExpense() {
        receive("2026-09-15")

        assertEquals(ReadState.Ready(HistoryStart(null, LocalDate.of(2026, 9, 15))), read())
    }

    @Test
    fun theEarliestOfEachIsReadWhicheverWasAddedFirst() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 5_000))
        // Added later, dated earlier.
        ledger.expenses.create(ledger.createExpense(100, "2026-08-20", 7_000))
        receive("2026-09-01")

        assertEquals(
            ReadState.Ready(HistoryStart(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20))),
            read()
        )
    }

    @Test
    fun anIncomeBeforeEveryExpenseStartsTheMovements() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 5_000))
        receive("2026-07-31")

        assertEquals(
            ReadState.Ready(HistoryStart(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 7, 31))),
            read()
        )
    }

    @Test
    fun aStoredDateThatCannotBeReadFailsTheReadInsteadOfMeaningNoHistory() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 5_000))
        ledger.database.openHelper.writableDatabase
            .execSQL("UPDATE expense SET effective_date = '2026-13-40'")

        val read = read()

        assertTrue(read is ReadState.Failed)
        assertEquals(
            StorageFailure.Integrity("expense", null, "effective_date"),
            (read as ReadState.Failed).failure
        )
    }
}
