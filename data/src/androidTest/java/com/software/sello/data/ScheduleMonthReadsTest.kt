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
import com.software.sello.domain.port.ScheduleMonthBookState
import com.software.sello.domain.port.ScheduleRecord
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        return (store.recordDue(1, d(today), 50) as CatchUpBatch.Recorded).occurrences
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

    /** Each record as kind, the date it is stored at, its occurrence's day and amount. */
    private fun summary(records: List<ScheduleRecord>) = records.map { record ->
        when (record) {
            is ScheduleRecord.Payment ->
                "expense|${record.expense.date}|${record.expense.origin?.occurrence?.date}|" +
                    "${record.expense.amount.money.minorUnits}"

            is ScheduleRecord.Earning ->
                "income|${record.income.date}|${record.income.origin?.occurrence?.date}|" +
                    "${record.income.amount.money.minorUnits}"
        }
    }

    private fun lines(read: ScheduleMonthBook, today: String) = ScheduleListPolicy.lines(
        read.book.schedules.filter { it.schedule.entry is ScheduledEntry.Payment },
        d(today),
        read.month
    )

    /** The payments' month on [today], worked out from the read as Pagos fijos does. */
    private fun payments(read: ScheduleMonthBook, today: String = "2026-10-22"): SchedulesMonth =
        ScheduleListPolicy.month(lines(read, today), read.records, read.month).valueOrFail()

    /** The rent row's "anotado" day on [today]. */
    private fun rentRecordedOn(read: ScheduleMonthBook, today: String) =
        ScheduleListPolicy.recordedOn(lines(read, today).single(), read.records, read.month)

    /**
     * RE17: rent on the 5th at 950.000, raised to 1.000.000 on 10 Oct. October went through
     * 950.000; edited to 900.000 and moved to 7 Oct it counts 900.000 there; deleted,
     * nothing. A manual expense and the salary are no payment of it.
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
            listOf("expense|2026-10-05|2026-10-05|950000", "income|2026-10-02|2026-10-02|3000000"),
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
        assertEquals(d("2026-10-07"), rentRecordedOn(read(), "2026-10-22"))

        ledger.expenses.delete(DeleteExpense(ledger.operation(), 1, expense, 2))
        assertEquals(listOf("income|2026-10-02|2026-10-02|3000000"), summary(read().records))
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
            listOf("expense|2026-09-05|2026-09-05|950000"),
            summary(read(YearMonth.of(2026, 9)).records)
        )
    }

    /** Rent from 1 Oct, caught up on mar 10 nov: its 5 Oct and 5 Nov entries, in order. */
    private suspend fun rentThroughNovember(): List<ExpenseId> {
        rentFrom("2026-10-01")
        ledger.goTo("2026-11-10")
        return catchUp("2026-11-10").filterIsInstance<RecordedOccurrence.Payment>()
            .map { it.expenseId }
    }

    /**
     * Ruling on #343 (R1): on 10 Nov the 5 Nov entry moved to 31 Oct counts in October, where
     * Recibo files it: November has nothing through for the rent; October 1.900.000.
     */
    @Test
    fun anEntryMovedBackToOctoberCountsInOctober() = runBlocking {
        val (_, fifthOfNovember) = rentThroughNovember()
        ledger.expenses.edit(edit(fifthOfNovember, 1, 950_000, "2026-10-31"))

        val november = read(YearMonth.of(2026, 11))
        assertEquals(emptyList<String>(), summary(november.records))
        assertEquals(
            SchedulesMonth(Money.cop(0), 0, Money.cop(0), 0, Money.cop(0), 1),
            payments(november, "2026-11-10")
        )
        assertEquals(null, rentRecordedOn(november, "2026-11-10"))
        // 950.000 + 950.000 = 1.900.000.
        assertEquals(
            SchedulesMonth(Money.cop(1_900_000), 2, Money.cop(0), 0, Money.cop(1_900_000), 1),
            payments(read(), "2026-11-10")
        )
        assertEquals(d("2026-10-31"), rentRecordedOn(read(), "2026-11-10"))
    }

    /**
     * Ruling on #343 (R1): on 10 Nov the 5 Oct entry moved to 7 Nov counts in November:
     * 950.000 + 950.000 = 1.900.000 from two entries, "anotado 7 nov"; October has none.
     */
    @Test
    fun anEntryMovedForwardToNovemberCountsInNovember() = runBlocking {
        val (fifthOfOctober, _) = rentThroughNovember()
        ledger.expenses.edit(edit(fifthOfOctober, 1, 950_000, "2026-11-07"))

        val november = read(YearMonth.of(2026, 11))
        assertEquals(
            listOf("expense|2026-11-05|2026-11-05|950000", "expense|2026-11-07|2026-10-05|950000"),
            summary(november.records)
        )
        assertEquals(
            SchedulesMonth(Money.cop(1_900_000), 2, Money.cop(0), 0, Money.cop(1_900_000), 1),
            payments(november, "2026-11-10")
        )
        assertEquals(d("2026-11-07"), rentRecordedOn(november, "2026-11-10"))
        assertEquals(emptyList<String>(), summary(read().records))
    }

    /**
     * One watcher of October sees the month read again after each commit, without asking:
     * catch-up's entry, then its edit to 900.000, then its delete.
     */
    @Test
    fun whoeverWatchesTheMonthSeesEveryChangeToItsEntries() = runBlocking {
        rentFrom("2026-10-01")
        ledger.goTo("2026-10-06")
        val states = Channel<ScheduleMonthBookState>(Channel.UNLIMITED)
        val watching = launch(Dispatchers.Default) {
            ledger.scheduleReads.observeMonth(october).collect { states.send(it) }
        }

        suspend fun recordsAt(revision: Long): List<String> = withTimeout(15_000) {
            var ready = states.receive() as? ReadState.Ready
            while (ready?.value?.book?.revision != revision) {
                ready = states.receive() as? ReadState.Ready
            }
            summary(ready.value.records)
        }

        assertEquals(ReadState.Loading, states.receive())
        val before = recordsAt(ledger.revision())
        val expense = catchUp("2026-10-06").filterIsInstance<RecordedOccurrence.Payment>()
            .single().expenseId
        val recorded = recordsAt(ledger.revision())
        ledger.expenses.edit(edit(expense, 1, 900_000, "2026-10-05"))
        val edited = recordsAt(ledger.revision())
        ledger.expenses.delete(DeleteExpense(ledger.operation(), 1, expense, 2))
        val deleted = recordsAt(ledger.revision())
        watching.cancel()

        assertEquals(
            listOf(
                emptyList(),
                listOf("expense|2026-10-05|2026-10-05|950000"),
                listOf("expense|2026-10-05|2026-10-05|900000"),
                emptyList<String>()
            ),
            listOf(before, recorded, edited, deleted)
        )
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
