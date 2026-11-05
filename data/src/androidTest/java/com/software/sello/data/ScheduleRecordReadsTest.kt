package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomScheduleCatchUp
import com.software.sello.data.repository.RoomScheduleRecordReads
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordedOccurrence
import com.software.sello.domain.port.ScheduleRecord
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the catch-up notice lists, read from the real database (recurrence-examples rule
 * 14): every entry a schedule recorded after the last Listo, by date, and nothing else.
 * Hogar is record 100; schedules come next, then entries.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleRecordReadsTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val reads by lazy { RoomScheduleRecordReads(ledger.database) }
    private val notice = NoticeSeen()

    private fun d(text: String) = LocalDate.parse(text)

    private suspend fun catchUp(today: String, limit: Int = 50): List<RecordedOccurrence> {
        val store = RoomScheduleCatchUp(ledger.database, { ledger.audit }, {
            uuid(ledger.nextRecord++)
        }, notice)
        return (store.recordDue(1, d(today), limit) as CatchUpBatch.Recorded).occurrences
    }

    private suspend fun schedule(start: String, rule: RepeatRule, entry: ScheduledEntry) {
        ledger.goTo(start)
        ledger.schedules.submit(CreateSchedule(ledger.operation(), 1, entry, rule, d(start), null))
    }

    private suspend fun rentAndSalary() {
        ledger.categories.submit(ledger.createCategory("Hogar"))
        schedule(
            "2026-10-01",
            RepeatRule.Monthly.of(5).valueOrFail(),
            ScheduledEntry.Payment(
                ledger.categoryId(100),
                transactionPesos(950_000),
                Note.of("Arriendo").valueOrFail()
            )
        )
        schedule(
            "2026-10-01",
            RepeatRule.Monthly.of(2).valueOrFail(),
            ScheduledEntry.Income(IncomeSource.Salary, transactionPesos(3_000_000), null)
        )
    }

    private fun summary(records: List<ScheduleRecord>) = records.map { record ->
        when (record) {
            is ScheduleRecord.Payment ->
                "expense|${record.expense.date}|${record.expense.amount.money.minorUnits}"

            is ScheduleRecord.Earning ->
                "income|${record.income.date}|${record.income.amount.money.minorUnits}"
        }
    }

    private suspend fun recordedAfter(since: Instant?) =
        summary((reads.recordedAfter(since) as Outcome.Success).value)

    /** RE10, opened on 24 October: both recorded entries, by date; a manual one never. */
    @Test
    fun everyEntryASchedulePutDownIsListedByDateAndAManualOneIsNot() = runBlocking {
        rentAndSalary()
        ledger.goTo("2026-10-24")
        ledger.expenses.create(ledger.createExpense(100, "2026-10-20", 950_000))
        ledger.audit = Instant.parse("2026-10-24T13:00:00Z")

        catchUp("2026-10-24")

        assertEquals(
            listOf("income|2026-10-02|3000000", "expense|2026-10-05|950000"),
            recordedAfter(null)
        )
    }

    /**
     * Listo at 14:00 on 24 October keeps what was there out; the week after, only what
     * catch-up recorded then shows. A recorded entry deleted meanwhile leaves the list.
     */
    @Test
    fun onlyWhatWasRecordedAfterListoShowsAndADeletedOneLeaves() = runBlocking {
        rentAndSalary()
        ledger.audit = Instant.parse("2026-10-24T13:00:00Z")
        catchUp("2026-10-24")
        val listo = Instant.parse("2026-10-24T14:00:00Z")

        assertEquals(emptyList<String>(), recordedAfter(listo))

        ledger.goTo("2026-11-06")
        ledger.audit = Instant.parse("2026-11-06T12:00:00Z")
        val november = catchUp("2026-11-06")
        assertEquals(
            listOf("income|2026-11-02|3000000", "expense|2026-11-05|950000"),
            recordedAfter(listo)
        )

        ledger.audit = Instant.parse("2026-11-06T12:05:00Z")
        ledger.expenses.delete(
            DeleteExpense(
                ledger.operation(),
                1,
                november.filterIsInstance<RecordedOccurrence.Payment>().single().expenseId,
                1
            )
        )
        assertEquals(listOf("income|2026-11-02|3000000"), recordedAfter(listo))
    }

    /** How far Listo marks seen on the list read after [since]: its latest stamp. */
    private suspend fun listo(since: Instant?): Instant =
        (reads.recordedAfter(since) as Outcome.Success).value.maxOf { record ->
            when (record) {
                is ScheduleRecord.Payment -> record.expense.createdAt
                is ScheduleRecord.Earning -> record.income.createdAt
            }
        }

    /**
     * #340: October is caught up at 13:00 on 24 October and Listo marks it seen; the
     * clock is then set back to 09:00 that day. What catch-up records for November is
     * still listed, although the clock says it came before the Listo.
     */
    @Test
    fun aClockSetBackAfterListoStillListsWhatCatchUpRecordsLater() = runBlocking {
        rentAndSalary()
        ledger.goTo("2026-10-24")
        ledger.audit = Instant.parse("2026-10-24T13:00:00Z")
        catchUp("2026-10-24")
        val seen = listo(null)
        assertEquals(Instant.parse("2026-10-24T13:00:00Z"), seen)

        ledger.goTo("2026-11-06")
        ledger.audit = Instant.parse("2026-10-24T09:00:00Z")
        catchUp("2026-11-06")

        assertEquals(
            listOf("income|2026-11-02|3000000", "expense|2026-11-05|950000"),
            recordedAfter(seen)
        )
    }

    /**
     * #340: Listo at 13:00 marks October seen, then both October entries are deleted, so
     * no entry holds that moment any more. With the clock set back, November still lists.
     */
    @Test
    fun aClockSetBackAfterTheSeenEntriesAreDeletedStillLists() = runBlocking {
        rentAndSalary()
        ledger.goTo("2026-10-24")
        ledger.audit = Instant.parse("2026-10-24T13:00:00Z")
        val october = catchUp("2026-10-24")
        val seen = listo(null)
        ledger.expenses.delete(
            DeleteExpense(
                ledger.operation(),
                1,
                october.filterIsInstance<RecordedOccurrence.Payment>().single().expenseId,
                1
            )
        )
        ledger.incomes.delete(
            DeleteIncome(
                ledger.operation(),
                1,
                october.filterIsInstance<RecordedOccurrence.Income>().single().incomeId,
                1
            )
        )

        ledger.goTo("2026-11-06")
        ledger.audit = Instant.parse("2026-10-24T09:00:00Z")
        catchUp("2026-11-06")

        assertEquals(
            listOf("income|2026-11-02|3000000", "expense|2026-11-05|950000"),
            recordedAfter(seen)
        )
    }

    /**
     * #340: the notice was marked seen at 13:00 on 24 October up to entries no longer
     * stored. Caught up with the clock at 09:00, October is stamped just after 13:00 and
     * listed.
     */
    @Test
    fun aBatchIsStampedAfterTheSeenMarkEvenWhenNothingStoredHoldsIt() = runBlocking {
        rentAndSalary()
        notice.seen = Instant.parse("2026-10-24T13:00:00Z")
        ledger.goTo("2026-10-24")
        ledger.audit = Instant.parse("2026-10-24T09:00:00Z")

        catchUp("2026-10-24")

        assertEquals(Instant.parse("2026-10-24T13:00:00.001Z"), listo(null))
        assertEquals(
            listOf("income|2026-10-02|3000000", "expense|2026-10-05|950000"),
            recordedAfter(notice.seen)
        )
    }

    /**
     * The same with the mark unreadable: recording never waits on it, and with nothing
     * stored to hold 13:00 the batch keeps the clock's 09:00 (ADR 0025's residual).
     */
    @Test
    fun anUnreadableMarkNeverStopsRecording() = runBlocking {
        rentAndSalary()
        notice.seen = Instant.parse("2026-10-24T13:00:00Z")
        notice.unreadable = true
        ledger.goTo("2026-10-24")
        ledger.audit = Instant.parse("2026-10-24T09:00:00Z")

        assertEquals(2, catchUp("2026-10-24").size)
        assertEquals(Instant.parse("2026-10-24T09:00:00Z"), listo(null))
    }

    /** A mark at the largest moment cannot be followed: the batch is refused, nothing written. */
    @Test
    fun aMarkNothingCanFollowRefusesTheBatchWithNothingWritten() = runBlocking {
        rentAndSalary()
        notice.seen = Instant.ofEpochMilli(Long.MAX_VALUE)
        ledger.goTo("2026-10-24")
        val store = RoomScheduleCatchUp(ledger.database, { ledger.audit }, {
            uuid(ledger.nextRecord++)
        }, notice)

        assertEquals(
            CatchUpBatch.Failed(StorageFailure.Integrity("catch_up", null, "created_at")),
            store.recordDue(1, d("2026-10-24"), 50)
        )
        assertEquals(emptyList<String>(), recordedAfter(null))
    }

    /** With the clock never set back, a batch keeps the clock's time: never moved later. */
    @Test
    fun aBatchAfterTheLastListoKeepsTheClocksTime() = runBlocking {
        rentAndSalary()
        notice.seen = Instant.parse("2026-10-24T13:00:00Z")
        ledger.goTo("2026-10-24")
        ledger.audit = Instant.parse("2026-10-24T13:00:00.002Z")

        catchUp("2026-10-24")

        assertEquals(Instant.parse("2026-10-24T13:00:00.002Z"), listo(null))
    }

    /** The list is read again after every commit, catch-up's included: Loading first. */
    @Test
    fun theListIsReadAgainWhenCatchUpCommits() = runBlocking {
        rentAndSalary()
        ledger.goTo("2026-10-24")
        val observed = reads.observeRecordedAfter(null)

        assertEquals(ReadState.Loading, observed.first())
        assertEquals(
            ReadState.Ready(emptyList<ScheduleRecord>()),
            observed.take(2).toList().last()
        )
        catchUp("2026-10-24")
        val after = observed.take(2).toList().last() as ReadState.Ready
        assertEquals(
            listOf("income|2026-10-02|3000000", "expense|2026-10-05|950000"),
            summary(after.value)
        )
    }

    /**
     * Bounded however long nothing was seen (SELLO-165). A daily payment caught up over
     * 2025 is 365 entries in 7 batches of 60, each batch in one millisecond. The first
     * read stops at the 250th, inside the 5th batch, with all of its moment: batches 1 to
     * 5, 300 entries, 1 January to 27 October. Listo there leaves the last 65, 28 October
     * to 31 December, for the next read.
     */
    @Test
    fun aLongCatchUpIsReadInBoundedPartsThatNeverSplitAMoment() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Hogar"))
        schedule(
            "2025-01-01",
            RepeatRule.EveryDays.of(1).valueOrFail(),
            ScheduledEntry.Payment(ledger.categoryId(100), transactionPesos(10_000), null)
        )
        ledger.goTo("2025-12-31")
        val stamps = (1L..7L).map { Instant.parse("2026-01-01T00:00:00Z").plusSeconds(it) }
        for (stamp in stamps) {
            ledger.audit = stamp
            catchUp("2025-12-31", limit = 60)
        }

        val first = (reads.recordedAfter(null) as Outcome.Success).value
            .map { (it as ScheduleRecord.Payment).expense.date }
        assertEquals(300, first.size)
        assertEquals(d("2025-01-01") to d("2025-10-27"), first.first() to first.last())

        val rest = (reads.recordedAfter(stamps[4]) as Outcome.Success).value
            .map { (it as ScheduleRecord.Payment).expense.date }
        assertEquals(65, rest.size)
        assertEquals(d("2025-10-28") to d("2025-12-31"), rest.first() to rest.last())
    }
}
