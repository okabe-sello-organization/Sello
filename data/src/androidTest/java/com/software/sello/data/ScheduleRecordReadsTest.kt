package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomScheduleCatchUp
import com.software.sello.data.repository.RoomScheduleRecordReads
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.RecordedOccurrence
import com.software.sello.domain.port.ScheduleRecord
import com.software.sello.domain.port.ScheduleRecordsState
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

    private fun d(text: String) = LocalDate.parse(text)

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()

    private suspend fun catchUp(today: String): List<RecordedOccurrence> {
        val store = RoomScheduleCatchUp(ledger.database, { ledger.audit }, {
            uuid(ledger.nextRecord++)
        })
        return (store.recordDue(d(today), 50) as CatchUpBatch.Recorded).occurrences
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
            ScheduledEntry.Payment(ledger.categoryId(100), pesos(950_000), Note.of("Arriendo").valueOrFail())
        )
        schedule(
            "2026-10-01",
            RepeatRule.Monthly.of(2).valueOrFail(),
            ScheduledEntry.Income(IncomeSource.Salary, pesos(3_000_000), null)
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

    /** The list is read again after every commit, catch-up's included: Loading first. */
    @Test
    fun theListIsReadAgainWhenCatchUpCommits() = runBlocking {
        rentAndSalary()
        ledger.goTo("2026-10-24")
        val observed = reads.observeRecordedAfter(null)

        assertEquals(ScheduleRecordsState.Loading, observed.first())
        assertEquals(
            ScheduleRecordsState.Ready(emptyList()),
            observed.take(2).toList().last()
        )
        catchUp("2026-10-24")
        val after = observed.take(2).toList().last() as ScheduleRecordsState.Ready
        assertEquals(
            listOf("income|2026-10-02|3000000", "expense|2026-10-05|950000"),
            summary(after.records)
        )
    }
}
