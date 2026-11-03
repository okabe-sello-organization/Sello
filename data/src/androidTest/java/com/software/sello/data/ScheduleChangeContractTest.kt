package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomScheduleCatchUp
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.ScheduleListPolicy
import com.software.sello.domain.policy.ScheduleStatus
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.PauseSchedule
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleCommandOutcome
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Changing a schedule's repeat, end or start on the real database (SELLO-145; rules 7 and
 * 8 of docs/testing/recurrence-examples.md): the schedule reads back, Recibo's month and
 * catch-up keep working, nothing dated on the day of the change or earlier is recorded,
 * and a pause the person set stays. Hogar is record 100, limited to 1.000.000; the
 * schedule is record 101.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleChangeContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val hogar by lazy { ledger.categoryId(100) }
    private val schedule by lazy { ScheduleId.of(uuid(101)).valueOrFail() }

    private fun d(text: String) = LocalDate.parse(text)

    private fun payment(pesos: Long, note: String) =
        ScheduledEntry.Payment(hogar, transactionPesos(pesos), Note.of(note).valueOrFail())

    private fun monthly(day: Int) = RepeatRule.Monthly.of(day).valueOrFail()

    private fun twice(first: Int, second: Int) =
        RepeatRule.TwiceMonthly.of(first, second).valueOrFail()

    private suspend fun start(today: String) {
        ledger.goTo(today)
        ledger.categories.submit(
            ledger.createCategory("Hogar", BudgetLimit.finite(Money.cop(1_000_000)).valueOrFail())
        )
    }

    private suspend fun create(
        start: String,
        rule: RepeatRule,
        entry: ScheduledEntry,
        end: String? = null
    ) = committed(
        submit(CreateSchedule(ledger.operation(), 1, entry, rule, d(start), end?.let(::d)))
    )

    private fun change(
        version: Long,
        start: String,
        rule: RepeatRule,
        entry: ScheduledEntry,
        end: String? = null,
        operation: OperationId = ledger.operation()
    ) = ChangeSchedule(operation, 1, schedule, version, entry, rule, d(start), end?.let(::d))

    private suspend fun submit(command: ScheduleCommand) = ledger.schedules.submit(command)

    private fun committed(outcome: ScheduleCommandOutcome): ScheduleCommandOutcome {
        assertTrue("$outcome", outcome is Committed && !outcome.replayed)
        return outcome
    }

    /** Everything due up to [today], as catch-up records it, by date. */
    private suspend fun catchUp(today: String): List<String> {
        val store =
            RoomScheduleCatchUp(ledger.database, { ledger.audit }, { uuid(ledger.nextRecord++) })
        val recorded = mutableListOf<String>()
        while (true) {
            val batch = store.recordDue(d(today), 50)
            assertTrue("$batch", batch is CatchUpBatch.Recorded)
            val occurrences = (batch as CatchUpBatch.Recorded).occurrences
            recorded += occurrences.map { it.origin.occurrence.date.toString() }
            if (occurrences.size < 50) return recorded
        }
    }

    private fun expenses() = ledger.rows("expense", "effective_date", "amount_minor", "origin_slot")

    private fun checkpoint() = ledger.rows("schedule", "handled_date", "handled_slot").single()

    private fun pauses() = ledger.rows("schedule_pause", "from_date", "until_date")

    private fun sql(statement: String) =
        ledger.database.openHelper.writableDatabase.execSQL(statement)

    /** The schedule's row and pauses, the revision and the receipts, as one value. */
    private fun everything() = listOf(
        ledger.rows(
            "schedule",
            "rule",
            "rule_first",
            "rule_second",
            "start_date",
            "end_date",
            "handled_date",
            "handled_slot",
            "version",
            "updated_at"
        ),
        pauses(),
        listOf(ledger.revision().toString()),
        ledger.rows("operation_receipt", "revision", "kind", "subject_id")
    )

    private val faulted = Rejected(
        CommandRejection.StorageFailed(StorageFailure.Unavailable("SQLiteConstraintException"))
    )

    private suspend fun version() =
        ledger.scheduleReads.book().valueOrFail().schedules.single().version

    /** What Recibo reserves this [month], or null when nothing is reserved. */
    private suspend fun reserved(month: YearMonth): Money? =
        (ledger.snapshots.read(month).valueOrFail().overall as OverallBudget.Limited)
            .reservation?.total

    private suspend fun line(today: String) = ScheduleListPolicy.lines(
        ledger.scheduleReads.book().valueOrFail().schedules,
        d(today),
        YearMonth.from(d(today))
    ).single()

    // ---- Twice a month becomes another rule ----

    /**
     * Twice a month on 15 and 30, 300.000, recorded up to 30 Oct (its second slot). On
     * 2 Nov it becomes [rule] from 3 Nov: [first] are the dates catch-up records up to
     * 15 Nov, and [reservedInNovember] what Recibo reserves for November on 2 Nov.
     */
    private fun twiceAMonthBecomes(
        rule: RepeatRule,
        first: List<String>,
        reservedInNovember: Long
    ) = runBlocking {
        start("2026-10-01")
        val rent = payment(300_000, "Arriendo")
        create("2026-10-01", twice(15, 30), rent)
        ledger.goTo("2026-10-30")
        assertEquals(listOf("2026-10-15", "2026-10-30"), catchUp("2026-10-30"))
        assertEquals("2026-10-30|2", checkpoint())

        ledger.goTo("2026-11-02")
        val operation = ledger.operation()
        committed(submit(change(1, "2026-11-03", rule, rent, operation = operation)))

        // The schedule reads back, on this handle and after a restart.
        assertEquals(
            rule,
            ledger.scheduleReads.book().valueOrFail().schedules.single().schedule.recurrence.rule
        )
        ledger.reopen()
        assertEquals(2L, version())
        // The same change again is the original, and writes nothing more.
        val again = submit(change(1, "2026-11-03", rule, rent, operation = operation))
        assertTrue("$again", again is Committed && again.replayed)
        assertEquals("2026-11-02|1", checkpoint())
        assertEquals(Money.cop(reservedInNovember), reserved(YearMonth.of(2026, 11)))
        assertEquals(ScheduleStatus.Active, line("2026-11-02").status)
        assertEquals(emptyList<String>(), catchUp("2026-11-02"))

        ledger.goTo("2026-11-15")
        assertEquals(first, catchUp("2026-11-15"))
        // 30 Oct stays, at its own slot.
        assertEquals(
            listOf("2026-10-15|300000|1", "2026-10-30|300000|2") +
                first.map { "$it|300000|1" },
            expenses()
        )
    }

    @Test
    fun twiceAMonthBecomingMonthlyOn15ReadsBackAndNextRecords15November() =
        twiceAMonthBecomes(monthly(15), listOf("2026-11-15"), 300_000)

    /** Every 10 days from mar 3 nov: 3, 13 and 23 Nov in November, 900.000. */
    @Test
    fun twiceAMonthBecomingEvery10DaysReadsBackAndRecordsFrom3November() = twiceAMonthBecomes(
        RepeatRule.EveryDays.of(10).valueOrFail(),
        listOf("2026-11-03", "2026-11-13"),
        900_000
    )

    /** Every 2 weeks from mar 3 nov: 3 and 17 Nov in November, 600.000. */
    @Test
    fun twiceAMonthBecomingEvery2WeeksReadsBackAndRecordsFrom3November() = twiceAMonthBecomes(
        RepeatRule.EveryWeeks.of(2).valueOrFail(),
        listOf("2026-11-03"),
        600_000
    )

    /**
     * Twice a month on 15 and 30 becomes monthly on 30 November, the day its second slot
     * is recorded: the checkpoint covers the whole day for a rule with one slot a day.
     */
    @Test
    fun twiceAMonthBecomingMonthlyOnTheDayOfItsSecondSlotReadsBack() = runBlocking {
        start("2026-11-01")
        val rent = payment(300_000, "Arriendo")
        create("2026-11-01", twice(15, 30), rent)
        ledger.goTo("2026-11-30")
        assertEquals(listOf("2026-11-15", "2026-11-30"), catchUp("2026-11-30"))

        committed(submit(change(1, "2026-12-01", monthly(15), rent)))

        assertEquals("2026-11-30|1", checkpoint())
        assertEquals(2L, version())
        assertEquals(emptyList<String>(), catchUp("2026-11-30"))
        ledger.goTo("2026-12-15")
        assertEquals(listOf("2026-12-15"), catchUp("2026-12-15"))
    }

    // ---- An ended schedule given a later end, or none ----

    /**
     * Rent monthly on 5, 950.000, ended 30 Sep after its 5 Sep payment. On 20 Oct its end
     * becomes [end]: 5 Oct is not recorded, nor reserved, and 5 Nov is.
     */
    private fun anEndedRentGets(end: String?) = runBlocking {
        start("2026-09-01")
        val rent = payment(950_000, "Arriendo")
        create("2026-09-01", monthly(5), rent, end = "2026-09-30")
        ledger.goTo("2026-09-05")
        assertEquals(listOf("2026-09-05"), catchUp("2026-09-05"))

        ledger.goTo("2026-10-20")
        committed(submit(change(1, "2026-09-01", monthly(5), rent, end = end)))

        assertEquals("2026-10-20|1", checkpoint())
        assertNull(reserved(YearMonth.of(2026, 10)))
        assertEquals(ScheduleStatus.Active, line("2026-10-20").status)
        assertEquals(d("2026-11-05"), line("2026-10-20").next)
        assertEquals(emptyList<String>(), catchUp("2026-10-20"))
        ledger.goTo("2026-11-05")
        assertEquals(listOf("2026-11-05"), catchUp("2026-11-05"))
        assertEquals(listOf("2026-09-05|950000|1", "2026-11-05|950000|1"), expenses())
    }

    @Test
    fun anEndedRentWhoseEndIsRemovedRecordsNothingFor5OctoberAndNext5November() =
        anEndedRentGets(end = null)

    @Test
    fun anEndedRentWhoseEndMovesTo31DecemberRecordsNothingFor5OctoberAndNext5November() =
        anEndedRentGets(end = "2026-12-31")

    /** Café every day, 5.000, ended 10 Oct and recorded through it; on 20 Oct it ends 31 Dec. */
    @Test
    fun anEndedDailyScheduleGivenALaterEndRecordsNothingBeforeTomorrow() = runBlocking {
        start("2026-10-01")
        val cafe = payment(5_000, "Café")
        val daily = RepeatRule.EveryDays.of(1).valueOrFail()
        create("2026-10-01", daily, cafe, end = "2026-10-10")
        ledger.goTo("2026-10-10")
        assertEquals(10, catchUp("2026-10-10").size)

        ledger.goTo("2026-10-20")
        committed(submit(change(1, "2026-10-01", daily, cafe, end = "2026-12-31")))

        assertEquals(emptyList<String>(), catchUp("2026-10-20"))
        ledger.goTo("2026-10-21")
        assertEquals(listOf("2026-10-21"), catchUp("2026-10-21"))
        assertEquals(11, expenses().size)
    }

    // ---- A pause set before the start ----

    /**
     * Monthly on 25 from 1 Dec, paused on 10 Oct: the pause starts 1 Dec and the schedule
     * is listed under En pausa. Its start moved to 20 Nov takes the pause with it.
     */
    @Test
    fun aPauseSetBeforeTheStartListsTheScheduleAsPausedAndMovesWithTheStart() = runBlocking {
        start("2026-10-10")
        val gym = payment(99_900, "Gimnasio")
        create("2026-12-01", monthly(25), gym)
        committed(submit(PauseSchedule(ledger.operation(), 1, schedule, 1)))
        assertEquals(listOf("2026-12-01|null"), pauses())
        assertEquals(ScheduleStatus.Paused, line("2026-10-10").status)
        assertEquals(d("2026-12-01"), line("2026-10-10").pausedFrom)

        committed(submit(change(2, "2026-11-20", monthly(25), gym)))

        assertEquals(listOf("2026-11-20|null"), pauses())
        assertEquals(ScheduleStatus.Paused, line("2026-10-10").status)
        assertEquals(d("2026-11-20"), line("2026-10-10").pausedFrom)
        ledger.goTo("2026-11-25")
        assertEquals(emptyList<String>(), catchUp("2026-11-25"))
        assertNull(reserved(YearMonth.of(2026, 11)))
        assertEquals(ScheduleStatus.Paused, line("2026-11-25").status)
        assertEquals(emptyList<String>(), expenses())
    }

    // ---- A failure after the schedule's row is written ----

    /**
     * The checkpoint's write fails after the row took the new rule and version: the whole
     * change rolls back, and the same operation commits once the fault is gone.
     */
    @Test
    fun aFailedCheckpointWriteRollsBackTheRuleTheVersionAndTheReceipt() = runBlocking {
        start("2026-10-01")
        val rent = payment(300_000, "Arriendo")
        create("2026-10-01", twice(15, 30), rent)
        ledger.goTo("2026-10-30")
        catchUp("2026-10-30")
        ledger.goTo("2026-11-02")
        sql(
            "CREATE TRIGGER refuse_checkpoint BEFORE UPDATE OF handled_date ON schedule " +
                "BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val before = everything()
        val command = change(1, "2026-11-03", monthly(15), rent)

        assertEquals(faulted, submit(command))

        assertEquals(before, everything())
        assertEquals("2026-10-30|2", checkpoint())
        assertEquals(null, ledger.schedules.find(command.operationId).valueOrFail())
        sql("DROP TRIGGER refuse_checkpoint")
        committed(submit(command))
        assertEquals("2026-11-02|1", checkpoint())
    }

    /**
     * Rewriting the pauses fails after the old ones were removed: the start, the version
     * and the pause set before the start all stay as they were.
     */
    @Test
    fun aFailedPauseRewriteRollsBackTheStartTheVersionAndTheOldPause() = runBlocking {
        start("2026-10-10")
        val gym = payment(99_900, "Gimnasio")
        create("2026-12-01", monthly(25), gym)
        committed(submit(PauseSchedule(ledger.operation(), 1, schedule, 1)))
        sql(
            "CREATE TRIGGER refuse_pause BEFORE INSERT ON schedule_pause " +
                "BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val before = everything()
        val command = change(2, "2026-11-20", monthly(25), gym)

        assertEquals(faulted, submit(command))

        assertEquals(before, everything())
        assertEquals(listOf("2026-12-01|null"), pauses())
        sql("DROP TRIGGER refuse_pause")
        committed(submit(command))
        assertEquals(listOf("2026-11-20|null"), pauses())
    }
}
