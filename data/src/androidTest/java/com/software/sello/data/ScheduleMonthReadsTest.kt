package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomScheduleCatchUp
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.policy.ScheduleListPolicy
import com.software.sello.domain.policy.SchedulesMonth
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordedOccurrence
import com.software.sello.domain.port.ScheduleMonthBook
import com.software.sello.domain.port.ScheduleRecord
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Pagos fijos reads for a month on the real database (SELLO-148): the schedules and,
 * in the same read, every entry they recorded for an occurrence in the month, as stored
 * now. Hogar is record 100, the rent 101 and the salary 102; entries come next.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleMonthReadsTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val rent by lazy { ScheduleId.of(uuid(101)).valueOrFail() }
    private val october = YearMonth.of(2026, 10)

    private fun d(text: String) = LocalDate.parse(text)

    private fun monthly(day: Int) = RepeatRule.Monthly.of(day).valueOrFail()

    private fun payment(pesos: Long) = ScheduledEntry.Payment(
        ledger.categoryId(100),
        transactionPesos(pesos),
        Note.of("Arriendo").valueOrFail()
    )

    private suspend fun rentFrom(start: String, end: String? = null) {
        ledger.goTo(start)
        ledger.categories.submit(ledger.createCategory("Hogar"))
        val created = ledger.schedules.submit(
            CreateSchedule(
                ledger.operation(),
                1,
                payment(950_000),
                monthly(5),
                d(start),
                end?.let(::d)
            )
        )
        assertTrue("$created", created is Committed)
    }

    private suspend fun catchUp(today: String): List<RecordedOccurrence> {
        val store = RoomScheduleCatchUp(ledger.database, { ledger.audit }, {
            uuid(ledger.nextRecord++)
        })
        return (store.recordDue(d(today), 50) as CatchUpBatch.Recorded).occurrences
    }

    private suspend fun changeRent(today: String, pesos: Long, start: String, end: String?) {
        ledger.goTo(today)
        val changed = ledger.schedules.submit(
            ChangeSchedule(
                ledger.operation(),
                1,
                rent,
                1,
                payment(pesos),
                monthly(5),
                d(start),
                end?.let(::d)
            )
        )
        assertTrue("$changed", changed is Committed)
    }

    private suspend fun read(month: YearMonth = october): ScheduleMonthBook =
        (ledger.scheduleReads.month(month) as Outcome.Success).value

    private fun summary(records: List<ScheduleRecord>) = records.map { record ->
        when (record) {
            is ScheduleRecord.Payment ->
                "expense|${record.expense.origin?.occurrence?.date}|" +
                    "${record.expense.amount.money.minorUnits}"

            is ScheduleRecord.Earning ->
                "income|${record.income.origin?.occurrence?.date}|" +
                    "${record.income.amount.money.minorUnits}"
        }
    }

    /** The payments' month on 22 Oct, worked out from the read as Pagos fijos does. */
    private fun payments(read: ScheduleMonthBook): SchedulesMonth {
        val lines = ScheduleListPolicy.lines(
            read.book.schedules.filter { it.schedule.entry is ScheduledEntry.Payment },
            d("2026-10-22"),
            october
        )
        return ScheduleListPolicy.month(lines, read.records, october).valueOrFail()
    }

    /**
     * RE17: rent on the 5th at 950.000, raised to 1.000.000 on 10 Oct. October went through
     * 950.000; edited to 900.000 (and moved to 7 Oct) it counts 900.000 under its 5 Oct
     * occurrence; deleted, nothing. A manual expense and the salary are no payment of it.
     */
    @Test
    fun theMonthIsReadFromTheEntriesAsStoredNow() = runBlocking {
        rentFrom("2026-10-01")
        val salary = ledger.schedules.submit(
            CreateSchedule(
                ledger.operation(),
                1,
                ScheduledEntry.Income(IncomeSource.Salary, transactionPesos(3_000_000), null),
                monthly(2),
                d("2026-10-01"),
                null
            )
        )
        assertTrue("$salary", salary is Committed)
        ledger.goTo("2026-10-06")
        val recorded = catchUp("2026-10-06")
        val expense = recorded.filterIsInstance<RecordedOccurrence.Payment>().single().expenseId
        ledger.expenses.create(ledger.createExpense(100, "2026-10-06", 45_000))
        changeRent("2026-10-10", 1_000_000, "2026-10-01", null)

        val raised = read()
        assertEquals(
            listOf("expense|2026-10-05|950000", "income|2026-10-02|3000000"),
            summary(raised.records)
        )
        assertEquals(
            Money.cop(1_000_000),
            raised.book.schedules.single { it.schedule.id == rent }.schedule.entry.amount.money
        )
        assertEquals(
            SchedulesMonth(Money.cop(950_000), 1, Money.cop(0), 0, Money.cop(950_000), 1),
            payments(raised)
        )

        ledger.expenses.edit(edit(expense, 1, 900_000, "2026-10-07"))
        assertEquals(
            SchedulesMonth(Money.cop(900_000), 1, Money.cop(0), 0, Money.cop(900_000), 1),
            payments(read())
        )

        ledger.expenses.delete(DeleteExpense(ledger.operation(), 1, expense, 2))
        assertEquals(listOf("income|2026-10-02|3000000"), summary(read().records))
        assertEquals(
            SchedulesMonth(Money.cop(0), 0, Money.cop(0), 0, Money.cop(0), 1),
            payments(read())
        )
        // November holds nothing of October's.
        assertEquals(emptyList<String>(), summary(read(YearMonth.of(2026, 11)).records))
    }

    /**
     * Ruling on #329 (R1): rent ended 30 Sep, its end removed on 20 Oct. The checkpoint
     * jumps to 20 Oct and 5 Oct was never recorded: October holds nothing of it, and its
     * 5 Sep entry is September's.
     */
    @Test
    fun aScheduleRunAgainHasNothingForWhatItNeverRecorded() = runBlocking {
        rentFrom("2026-09-01", end = "2026-09-30")
        ledger.goTo("2026-09-06")
        catchUp("2026-09-06")
        changeRent("2026-10-20", 950_000, "2026-09-01", null)
        catchUp("2026-10-20")

        val read = read()
        assertEquals(emptyList<String>(), summary(read.records))
        assertEquals(
            SchedulesMonth(Money.cop(0), 0, Money.cop(0), 0, Money.cop(0), 1),
            payments(read)
        )
        assertEquals(
            listOf("expense|2026-09-05|950000"),
            summary(read(YearMonth.of(2026, 9)).records)
        )
    }

    /** The month is read again after every commit: Loading first, then each read. */
    @Test
    fun theMonthIsReadAgainWhenAnEntryChanges() = runBlocking {
        rentFrom("2026-10-01")
        ledger.goTo("2026-10-06")
        val observed = ledger.scheduleReads.observeMonth(october)

        assertEquals(ReadState.Loading, observed.first())
        val before = observed.take(2).toList().last() as ReadState.Ready
        assertEquals(emptyList<String>(), summary(before.value.records))
        catchUp("2026-10-06")
        val after = observed.take(2).toList().last() as ReadState.Ready
        assertEquals(listOf("expense|2026-10-05|950000"), summary(after.value.records))
    }

    private fun edit(expense: ExpenseId, version: Long, pesos: Long, date: String) = EditExpense(
        operationId = ledger.operation(),
        generation = 1,
        expenseId = expense,
        expectedVersion = version,
        categoryId = ledger.categoryId(100),
        amount = transactionPesos(pesos),
        date = d(date),
        note = Note.of("Arriendo").valueOrFail()
    )
}
