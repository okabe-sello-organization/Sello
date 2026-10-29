package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomScheduleCatchUp
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.PauseSchedule
import com.software.sello.domain.port.RecordedOccurrence
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreIncome
import com.software.sello.domain.port.ResumeSchedule
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.usecase.CatchUpSchedules
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Catch-up on the real database: what it records, where it stops and what it never
 * records twice, by the recurrence examples (docs/testing/recurrence-examples.md).
 * Hogar is record 100, Transporte 101; schedules come next, then entries.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleCatchUpContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }

    private fun d(text: String) = LocalDate.parse(text)

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()

    private fun store() = RoomScheduleCatchUp(ledger.database, {
        ledger.audit
    }, { uuid(ledger.nextRecord++) })

    private suspend fun batch(today: String, limit: Int = CatchUpSchedules.BATCH) =
        store().recordDue(d(today), limit)

    private suspend fun categories() {
        ledger.categories.submit(ledger.createCategory("Hogar"))
        ledger.categories.submit(ledger.createCategory("Transporte"))
    }

    private suspend fun schedule(
        start: String,
        rule: RepeatRule,
        entry: ScheduledEntry
    ): ScheduleId {
        ledger.goTo(start)
        val created = ledger.schedules.submit(
            CreateSchedule(ledger.operation(), 1, entry, rule, d(start), null)
        )
        return (created as CommandOutcome.Committed).value.scheduleId
    }

    private fun payment(category: Int, amount: Long, note: String? = null) = ScheduledEntry.Payment(
        ledger.categoryId(category),
        pesos(amount),
        Note.of(note).valueOrFail()
    )

    private fun expenses() = ledger.rows(
        "expense",
        "effective_date",
        "origin_slot",
        "amount_minor",
        "category_id",
        "note",
        "origin_schedule_id"
    )

    private fun checkpoints() = ledger.rows("schedule", "sequence", "handled_date", "handled_slot")

    /**
     * RE10: rent monthly on the 5th, 950.000 in Hogar; bus every week from 26 September,
     * 20.000 in Transporte, its 3 October already recorded. On 24 October: 5 October rent,
     * then the bus on 10, 17 and 24 October, 1.010.000, in one revision.
     */
    @Test
    fun re10theMissedOccurrencesAreRecordedOnTheirOwnDatesInOneRevision() = runBlocking {
        categories()
        val rent =
            schedule(
                "2026-09-01",
                RepeatRule.Monthly.of(5).valueOrFail(),
                payment(100, 950_000, "Arriendo")
            )
        val bus =
            schedule("2026-09-26", RepeatRule.EveryWeeks.of(1).valueOrFail(), payment(101, 20_000))
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE schedule SET handled_date = '2026-09-05', handled_slot = 1 WHERE id = '${rent.value}'"
        )
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE schedule SET handled_date = '2026-10-03', handled_slot = 1 WHERE id = '${bus.value}'"
        )
        val revision = ledger.revision()

        val recorded = batch("2026-10-24") as CatchUpBatch.Recorded

        assertEquals(
            listOf("2026-10-05", "2026-10-10", "2026-10-17", "2026-10-24"),
            recorded.occurrences.map { it.origin.occurrence.date.toString() }
        )
        assertEquals(
            listOf(
                "2026-10-05|1|950000|${uuid(100)}|Arriendo|${rent.value}",
                "2026-10-10|1|20000|${uuid(101)}|null|${bus.value}",
                "2026-10-17|1|20000|${uuid(101)}|null|${bus.value}",
                "2026-10-24|1|20000|${uuid(101)}|null|${bus.value}"
            ),
            expenses()
        )
        assertEquals(1_010_000L, ledger.long("SELECT SUM(amount_minor) FROM expense"))
        assertEquals(listOf("0|2026-10-05|1", "1|2026-10-24|1"), checkpoints())
        assertEquals(revision + 1, ledger.revision())
        assertEquals(
            "${revision + 1}|schedule.catch_up|null",
            ledger.rows("operation_receipt", "revision", "kind", "subject_id").last()
        )
    }

    /** RE11 and RE12: 85 daily cafés are 50 and 35, and a stop after the first resumes there. */
    @Test
    fun re11re12everyMissedOccurrenceInBatchesThatResumeAfterTheLastCommitted() = runBlocking {
        categories()
        val cafe =
            schedule("2026-07-31", RepeatRule.EveryDays.of(1).valueOrFail(), payment(100, 5_000))
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE schedule SET handled_date = '2026-07-31', handled_slot = 1 WHERE id = '${cafe.value}'"
        )

        val first = batch("2026-10-24") as CatchUpBatch.Recorded
        // Sello closes here; a new process opens the same file.
        ledger.reopen()
        val today = object : FinancialClock {
            override val today = MutableStateFlow(FinancialDay(d("2026-10-24"), ledger.zone))
        }
        val result = CatchUpSchedules(store(), today).run()

        // 1 August to 19 September, then 20 September to 24 October: 85, 425.000.
        assertEquals(50, first.occurrences.size)
        assertEquals(d("2026-08-01"), first.occurrences.first().origin.occurrence.date)
        assertEquals(d("2026-09-19"), first.occurrences.last().origin.occurrence.date)
        assertEquals(35, result.recorded.size)
        assertEquals(d("2026-09-20"), result.recorded.first().origin.occurrence.date)
        assertEquals(null, result.stopped)
        assertEquals(85L, ledger.long("SELECT COUNT(*) FROM expense"))
        assertEquals(425_000L, ledger.long("SELECT SUM(amount_minor) FROM expense"))
        assertEquals(85L, ledger.long("SELECT COUNT(DISTINCT effective_date) FROM expense"))
    }

    /**
     * Two daily schedules, 30 days each, are 60 occurrences: a batch is the 50 oldest
     * across both, by date and then by which schedule came first, and the next the 10 left.
     */
    @Test
    fun aBatchIsTheOldestFiftyAcrossSchedulesInCreationOrderOnEachDay() = runBlocking {
        categories()
        val cafe =
            schedule("2026-10-01", RepeatRule.EveryDays.of(1).valueOrFail(), payment(100, 5_000))
        val bus =
            schedule("2026-10-01", RepeatRule.EveryDays.of(1).valueOrFail(), payment(101, 3_000))

        val first = batch("2026-10-30") as CatchUpBatch.Recorded
        val second = batch("2026-10-30") as CatchUpBatch.Recorded

        assertEquals(50, first.occurrences.size)
        assertEquals(
            listOf(cafe, bus, cafe, bus),
            first.occurrences.take(4).map { it.origin.schedule }
        )
        // 25 days of both: 1 to 25 October.
        assertEquals(d("2026-10-25"), first.occurrences.last().origin.occurrence.date)
        assertEquals(bus, first.occurrences.last().origin.schedule)
        assertEquals(10, second.occurrences.size)
        assertEquals(listOf("0|2026-10-30|1", "1|2026-10-30|1"), checkpoints())
        assertEquals(60L, ledger.long("SELECT COUNT(*) FROM expense"))
    }

    /** RE13: running a batch again after it committed records nothing twice. */
    @Test
    fun re13aBatchAgainRecordsNothing() = runBlocking {
        categories()
        schedule("2026-10-01", RepeatRule.Monthly.of(5).valueOrFail(), payment(100, 950_000))
        batch("2026-10-24")
        val revision = ledger.revision()

        val again = batch("2026-10-24")

        assertEquals(CatchUpBatch.Recorded(emptyList()), again)
        assertEquals(revision, ledger.revision())
        assertEquals(1L, ledger.long("SELECT COUNT(*) FROM expense"))
    }

    /** RE15: a recorded occurrence deleted afterwards is never recorded again. */
    @Test
    fun re15aDeletedOccurrenceDoesNotComeBack() = runBlocking {
        categories()
        schedule("2026-10-01", RepeatRule.Monthly.of(5).valueOrFail(), payment(100, 950_000))
        val recorded = (batch("2026-10-05") as CatchUpBatch.Recorded).occurrences.single()
        ledger.goTo("2026-10-06")
        ledger.expenses.delete(
            DeleteExpense(
                ledger.operation(),
                1,
                (recorded as RecordedOccurrence.Payment).expenseId,
                1
            )
        )

        assertEquals(CatchUpBatch.Recorded(emptyList()), batch("2026-10-07"))
        assertEquals(0L, ledger.long("SELECT COUNT(*) FROM expense"))
    }

    /**
     * RE15's second half (rule 4): Deshacer brings the deleted rent back with its origin,
     * and catch-up still never records the 5 October rent again.
     */
    @Test
    fun re15anUndoneDeletionComesBackWithItsOriginAndIsNotRecordedTwice() = runBlocking {
        categories()
        val rent =
            schedule("2026-10-01", RepeatRule.Monthly.of(5).valueOrFail(), payment(100, 950_000))
        val recorded = (batch("2026-10-05") as CatchUpBatch.Recorded).occurrences.single()
        ledger.goTo("2026-10-06")
        val deletion = ledger.operation()
        ledger.expenses.delete(
            DeleteExpense(deletion, 1, (recorded as RecordedOccurrence.Payment).expenseId, 1)
        )

        val undone = ledger.expenses.restore(RestoreExpense(ledger.operation(), 1, deletion))

        assertTrue("$undone", undone is ExpenseCommandOutcome.Committed)
        assertEquals(listOf("2026-10-05|1|950000|${uuid(100)}|null|${rent.value}"), expenses())
        assertEquals(CatchUpBatch.Recorded(emptyList()), batch("2026-10-07"))
        assertEquals(listOf("0|2026-10-05|1"), checkpoints())
    }

    /** Rule 4 for an income: an undone deletion of the 15 October salary is not recorded again. */
    @Test
    fun anUndoneIncomeDeletionComesBackWithItsOriginAndIsNotRecordedTwice() = runBlocking {
        val salary = schedule(
            "2026-10-01",
            RepeatRule.Monthly.of(15).valueOrFail(),
            ScheduledEntry.Income(IncomeSource.Salary, pesos(1_000_000), null)
        )
        val recorded = (batch("2026-10-15") as CatchUpBatch.Recorded).occurrences.single()
        ledger.goTo("2026-10-16")
        val deletion = ledger.operation()
        ledger.incomes.delete(
            DeleteIncome(deletion, 1, (recorded as RecordedOccurrence.Income).incomeId, 1)
        )

        val undone = ledger.incomes.restore(RestoreIncome(ledger.operation(), 1, deletion))

        assertTrue("$undone", undone is CommandOutcome.Committed)
        assertEquals(
            listOf("2026-10-15|1|1000000|${salary.value}"),
            ledger.rows(
                "income",
                "effective_date",
                "origin_slot",
                "amount_minor",
                "origin_schedule_id"
            )
        )
        assertEquals(CatchUpBatch.Recorded(emptyList()), batch("2026-10-17"))
    }

    /** RE16: a manual entry is never taken for the scheduled one. */
    @Test
    fun re16aManualEntryOfTheSameRentIsNotMatched() = runBlocking {
        categories()
        schedule("2026-10-01", RepeatRule.Monthly.of(5).valueOrFail(), payment(100, 950_000))
        ledger.goTo("2026-10-05")
        ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 950_000))

        batch("2026-10-05")

        assertEquals(1_900_000L, ledger.long("SELECT SUM(amount_minor) FROM expense"))
        assertEquals(
            1L,
            ledger.long("SELECT COUNT(*) FROM expense WHERE origin_schedule_id IS NULL")
        )
    }

    /** RE21: what falls inside a pause is skipped, not caught up; the resume day counts. */
    @Test
    fun re21thePausedOccurrencesAreSkipped() = runBlocking {
        categories()
        val rent =
            schedule("2026-10-03", RepeatRule.Monthly.of(5).valueOrFail(), payment(100, 950_000))
        ledger.schedules.submit(PauseSchedule(ledger.operation(), 1, rent, 1))
        ledger.goTo("2026-12-05")
        ledger.schedules.submit(ResumeSchedule(ledger.operation(), 1, rent, 2))

        batch("2026-12-05")

        assertEquals(listOf("2026-12-05"), ledger.rows("expense", "effective_date"))
    }

    /** RE06: two days that fall on 28 February are two entries that day. */
    @Test
    fun re06twoSlotsOnOneDateAreTwoEntries() = runBlocking {
        categories()
        schedule(
            "2027-02-01",
            RepeatRule.TwiceMonthly.of(30, 31).valueOrFail(),
            payment(100, 10_000)
        )

        batch("2027-03-01")

        assertEquals(
            listOf("2027-02-28|1", "2027-02-28|2"),
            ledger.rows("expense", "effective_date", "origin_slot")
        )
    }

    /** RE36: a salary is recorded as income with its source, never as an expense. */
    @Test
    fun re36anIncomeScheduleRecordsIncome() = runBlocking {
        categories()
        schedule(
            "2026-10-30",
            RepeatRule.Monthly.of(30).valueOrFail(),
            ScheduledEntry.Income(IncomeSource.Salary, pesos(1_000_000), null)
        )

        val recorded = batch("2026-10-30") as CatchUpBatch.Recorded

        assertTrue(recorded.occurrences.single() is RecordedOccurrence.Income)
        assertEquals(
            listOf("2026-10-30|1000000|salary"),
            ledger.rows("income", "effective_date", "amount_minor", "source_key")
        )
        assertEquals(0L, ledger.long("SELECT COUNT(*) FROM expense"))
    }

    @Test
    fun nothingIsRecordedAheadOfToday() = runBlocking {
        categories()
        schedule("2026-10-09", RepeatRule.Monthly.of(28).valueOrFail(), payment(100, 950_000))
        val revision = ledger.revision()

        assertEquals(CatchUpBatch.Recorded(emptyList()), batch("2026-10-27"))
        assertEquals(revision, ledger.revision())
    }

    @Test
    fun aPaymentWhoseCategoryWasArchivedUnpausedIsDamageNotSkipped() = runBlocking {
        categories()
        val rent =
            schedule("2026-10-01", RepeatRule.Monthly.of(5).valueOrFail(), payment(100, 950_000))
        // Archived behind the commands' back, as only damage could leave it.
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE category SET archived = 1 WHERE id = '${uuid(100)}'"
        )

        assertEquals(
            CatchUpBatch.Failed(StorageFailure.Integrity("schedule", rent.value, "category_id")),
            batch("2026-10-05")
        )
        assertEquals(0L, ledger.long("SELECT COUNT(*) FROM expense"))
    }
}
