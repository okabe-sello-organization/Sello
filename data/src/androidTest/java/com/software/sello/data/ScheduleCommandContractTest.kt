package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.RecurrenceError
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteSchedule
import com.software.sello.domain.port.PauseSchedule
import com.software.sello.domain.port.ResumeSchedule
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommandOutcome.Committed
import com.software.sello.domain.port.ScheduleCommandOutcome.Rejected
import com.software.sello.domain.port.ScheduleRejection
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schedule commands on the real database: what each writes, refuses and answers on a
 * replay, by the recurrence examples (docs/testing/recurrence-examples.md). The
 * category Hogar is record 100; the first schedule is record 101.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleCommandContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val hogar by lazy { ledger.categoryId(100) }
    private val rent = uuid(101)
    private val recorded by lazy { ledger.auditMillis.toEpochMilli() }

    private fun d(text: String) = LocalDate.parse(text)

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()

    private fun payment(pesos: Long = 950_000, note: String? = "Arriendo") =
        ScheduledEntry.Payment(hogar, pesos(pesos), Note.of(note).valueOrFail())

    private fun monthly(day: Int) = RepeatRule.Monthly.of(day).valueOrFail()

    private fun scheduleId(id: String = rent) = ScheduleId.of(id).valueOrFail()

    private suspend fun start(today: String = "2026-10-09") {
        ledger.goTo(today)
        ledger.categories.submit(ledger.createCategory("Hogar"))
    }

    private fun create(
        start: String,
        rule: RepeatRule = monthly(5),
        entry: ScheduledEntry = payment(),
        end: String? = null,
        operation: OperationId = ledger.operation(),
        generation: Long = 1
    ) = CreateSchedule(operation, generation, entry, rule, d(start), end?.let(::d))

    private fun change(
        version: Long,
        start: String,
        rule: RepeatRule = monthly(5),
        entry: ScheduledEntry = payment(),
        end: String? = null
    ) = ChangeSchedule(
        ledger.operation(),
        1,
        scheduleId(),
        version,
        entry,
        rule,
        d(start),
        end?.let(::d)
    )

    /** What catch-up would have stored after recording [date]. */
    private fun handledThrough(date: String) = ledger.database.openHelper.writableDatabase.execSQL(
        "UPDATE schedule SET handled_date = '$date', handled_slot = 1 WHERE id = '$rent'"
    )

    private fun schedules() = ledger.rows(
        "schedule",
        "id",
        "kind",
        "category_id",
        "amount_minor",
        "note",
        "rule",
        "rule_first",
        "start_date",
        "end_date",
        "sequence",
        "handled_date",
        "version"
    )

    private fun pauses() = ledger.rows("schedule_pause", "schedule_id", "from_date", "until_date")

    private fun receipts() = ledger.rows("operation_receipt", "revision", "kind", "subject_id")

    private suspend fun submit(command: ScheduleCommand) = ledger.schedules.submit(command)

    private fun committed(outcome: ScheduleCommandOutcome) {
        assertTrue("$outcome", outcome is Committed && !outcome.replayed)
    }

    // ---- Creating ----

    @Test
    fun aNewScheduleIsOneRowOneRevisionAndOneReceiptAndNothingIsRecorded() = runBlocking {
        start()
        val revision = ledger.revision()

        val outcome = submit(create("2026-10-22"))

        assertEquals(scheduleId(), (outcome as Committed).schedule.scheduleId)
        assertEquals(
            listOf(
                "$rent|payment|${hogar.value}|950000|Arriendo|monthly|5|2026-10-22|null|0|null|1"
            ),
            schedules()
        )
        assertEquals(revision + 1, ledger.revision())
        assertEquals(
            "${revision + 1}|schedule.create|$rent",
            receipts().last()
        )
        assertEquals(0L, ledger.long("SELECT COUNT(*) FROM expense"))
        assertEquals(OperationKind.CreateSchedule, outcome.schedule.receipt.kind)
    }

    @Test
    fun anIncomeScheduleKeepsItsSourceAndASecondScheduleComesAfterTheFirst() = runBlocking {
        start()
        committed(submit(create("2026-10-22")))

        committed(
            submit(
                create(
                    "2026-10-15",
                    RepeatRule.TwiceMonthly.of(15, 30).valueOrFail(),
                    ScheduledEntry.Income(IncomeSource.Salary, pesos(800_000), null)
                )
            )
        )

        assertEquals(
            listOf("income|null|salary|null|twice_monthly|15|30|1"),
            ledger.rows(
                "schedule",
                "kind",
                "category_id",
                "source_key",
                "source_name",
                "rule",
                "rule_first",
                "rule_second",
                "sequence"
            ).filter { it.startsWith("income") }
        )
    }

    @Test
    fun theSameOperationAgainIsTheOriginalAndOtherInputIsAConflict() = runBlocking {
        start()
        val operation = ledger.operation()
        committed(submit(create("2026-10-22", operation = operation)))
        val before = schedules() to ledger.revision()

        val again = submit(create("2026-10-22", operation = operation))
        val different = submit(create("2026-10-23", operation = operation))

        assertEquals(true, (again as Committed).replayed)
        assertEquals(Rejected(ScheduleRejection.OperationConflict), different)
        assertEquals(before, schedules() to ledger.revision())
    }

    @Test
    fun aNewScheduleIsRefusedBeforeTodayAgainstAnotherHistoryOrAMissingOrArchivedCategory() =
        runBlocking {
            start()
            val before = ledger.revision()

            assertEquals(
                Rejected(ScheduleRejection.StartTooEarly(d("2026-10-09"))),
                submit(create("2026-10-08"))
            )
            assertEquals(
                Rejected(ScheduleRejection.InvalidRecurrence(RecurrenceError.EndBeforeStart)),
                submit(create("2026-10-22", end = "2026-10-21"))
            )
            assertEquals(
                Rejected(ScheduleRejection.StaleGeneration(1)),
                submit(create("2026-10-22", generation = 2))
            )
            assertEquals(
                Rejected(ScheduleRejection.CategoryMissing),
                submit(
                    create(
                        "2026-10-22",
                        entry = ScheduledEntry.Payment(ledger.categoryId(999), pesos(1), null)
                    )
                )
            )
            ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, hogar, 1))
            assertEquals(
                Rejected(ScheduleRejection.CategoryArchived),
                submit(create("2026-10-22"))
            )
            assertEquals(emptyList<String>(), schedules())
            assertEquals(before + 1, ledger.revision())
        }

    // ---- Changing ----

    /** RE17: on 10 October the rent becomes 1.000.000; 5 October stays as it was. */
    @Test
    fun re17aNewAmountKeepsTheRuleAndItsStart() = runBlocking {
        start("2026-10-01")
        committed(submit(create("2026-10-01")))
        handledThrough("2026-10-05")
        ledger.goTo("2026-10-10")

        committed(submit(change(1, "2026-10-01", entry = payment(1_000_000))))

        assertEquals(
            listOf(
                "$rent|payment|${hogar.value}|1000000|Arriendo|monthly|5|2026-10-01|null|0|2026-10-05|2"
            ),
            schedules()
        )
    }

    /** RE18: a new day is a new rule, which starts after today. */
    @Test
    fun re18aNewRuleStartsAfterToday() = runBlocking {
        start("2026-10-01")
        committed(submit(create("2026-10-01")))
        handledThrough("2026-10-05")
        ledger.goTo("2026-10-10")

        assertEquals(
            Rejected(ScheduleRejection.StartTooEarly(d("2026-10-11"))),
            submit(change(1, "2026-10-10", monthly(25)))
        )
        committed(submit(change(1, "2026-10-11", monthly(25))))

        assertEquals(
            "monthly|25|2026-10-11|2",
            ledger.rows("schedule", "rule", "rule_first", "start_date", "version").single()
        )
    }

    @Test
    fun aChangeWaitsForCatchUpAndRefusesAnotherKindNothingNewOrAnOldVersion() = runBlocking {
        start("2026-10-01")
        committed(submit(create("2026-10-01")))
        ledger.goTo("2026-10-10")

        // 5 October is due and not recorded yet.
        assertEquals(
            Rejected(ScheduleRejection.CatchUpPending),
            submit(change(1, "2026-10-01", entry = payment(1_000_000)))
        )
        handledThrough("2026-10-05")
        assertEquals(
            Rejected(ScheduleRejection.KindChanged),
            submit(
                change(
                    1,
                    "2026-10-01",
                    entry = ScheduledEntry.Income(IncomeSource.Salary, pesos(950_000), null)
                )
            )
        )
        assertEquals(Rejected(ScheduleRejection.Unchanged), submit(change(1, "2026-10-01")))
        assertEquals(
            Rejected(ScheduleRejection.EndTooEarly(d("2026-10-10"))),
            submit(change(1, "2026-10-01", end = "2026-10-09"))
        )
        committed(submit(change(1, "2026-10-01", end = "2026-12-05")))
        assertEquals(
            Rejected(ScheduleRejection.VersionConflict(2)),
            submit(change(1, "2026-10-01", entry = payment(1)))
        )
    }

    // ---- Pausing ----

    /** RE21: paused on 3 October, resumed on 5 December, which counts. */
    @Test
    fun re21aPauseRunsFromItsDayToTheDayBeforeItsResumption() = runBlocking {
        start("2026-10-03")
        committed(submit(create("2026-10-03")))

        committed(submit(PauseSchedule(ledger.operation(), 1, scheduleId(), 1)))
        assertEquals(
            Rejected(ScheduleRejection.AlreadyPaused),
            submit(PauseSchedule(ledger.operation(), 1, scheduleId(), 2))
        )
        ledger.goTo("2026-12-05")
        committed(submit(ResumeSchedule(ledger.operation(), 1, scheduleId(), 2)))

        assertEquals(listOf("$rent|2026-10-03|2026-12-05"), pauses())
        assertEquals(
            Rejected(ScheduleRejection.NotPaused),
            submit(ResumeSchedule(ledger.operation(), 1, scheduleId(), 3))
        )
        assertEquals("3", ledger.rows("schedule", "version").single())
    }

    @Test
    fun aPauseResumedTheSameDayLeavesNothingAndAPauseWaitsForCatchUp() = runBlocking {
        start("2026-10-01")
        committed(submit(create("2026-10-01")))
        handledThrough("2026-10-05")
        ledger.goTo("2026-10-20")

        committed(submit(PauseSchedule(ledger.operation(), 1, scheduleId(), 1)))
        committed(submit(ResumeSchedule(ledger.operation(), 1, scheduleId(), 2)))

        assertEquals(emptyList<String>(), pauses())
        ledger.goTo("2026-11-06")
        assertEquals(
            Rejected(ScheduleRejection.CatchUpPending),
            submit(PauseSchedule(ledger.operation(), 1, scheduleId(), 3))
        )
    }

    // ---- Deleting ----

    @Test
    fun deletingRemovesTheScheduleAndItsPausesButNotWhatItRecorded() = runBlocking {
        start("2026-10-01")
        committed(submit(create("2026-10-01")))
        // RE24: the rent of 5 October, recorded by catch-up.
        ledger.database.insertRaw(
            "expense",
            "id" to uuid(300),
            "category_id" to hogar.value,
            "effective_date" to "2026-10-05",
            "sequence" to 1L,
            "amount_minor" to 950_000L,
            "currency" to "COP",
            "note" to "Arriendo",
            "version" to 1L,
            "created_at" to recorded,
            "updated_at" to recorded,
            "origin_schedule_id" to rent,
            "origin_date" to "2026-10-05",
            "origin_slot" to 1
        )
        ledger.goTo("2026-10-10")
        assertEquals(
            Rejected(ScheduleRejection.CatchUpPending),
            submit(DeleteSchedule(ledger.operation(), 1, scheduleId(), 1))
        )
        handledThrough("2026-10-05")
        committed(submit(PauseSchedule(ledger.operation(), 1, scheduleId(), 1)))

        committed(submit(DeleteSchedule(ledger.operation(), 1, scheduleId(), 2)))

        assertEquals(emptyList<String>(), schedules())
        assertEquals(emptyList<String>(), pauses())
        assertEquals(
            listOf("$rent|2026-10-05|1|950000"),
            ledger.rows(
                "expense",
                "origin_schedule_id",
                "origin_date",
                "origin_slot",
                "amount_minor"
            )
        )
        assertEquals(emptyList<Any>(), ledger.scheduleReads.book().valueOrFail().schedules)
    }

    // ---- Archiving the category ----

    /** RE23: archiving Gimnasio on 12 October pauses its schedule from that day. */
    @Test
    fun re23archivingPausesTheCategorysSchedulesFromThatDay() = runBlocking {
        start("2026-10-09")
        committed(submit(create("2026-10-09", monthly(15))))
        ledger.goTo("2026-10-12")

        val archived = ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, hogar, 1))

        assertTrue("$archived", archived is CategoryCommandOutcome.Committed)
        assertEquals(listOf("$rent|2026-10-12|null"), pauses())
        assertEquals("2", ledger.rows("schedule", "version").single())
    }

    @Test
    fun archivingWaitsWhileACategorysScheduleHasAnUnrecordedOccurrence() = runBlocking {
        start("2026-10-09")
        committed(submit(create("2026-10-09", monthly(10))))
        ledger.goTo("2026-10-12")

        val archived = ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, hogar, 1))

        assertEquals(CategoryCommandOutcome.Rejected(CategoryRejection.SchedulesPending), archived)
        assertEquals(emptyList<String>(), pauses())
        assertEquals("0", ledger.rows("category", "archived").single())
    }

    @Test
    fun findAnswersWhatAnOperationCommitted() = runBlocking {
        start()
        val operation = ledger.operation()
        val outcome = submit(create("2026-10-22", operation = operation))

        val found = ledger.schedules.find(operation).valueOrFail()

        assertEquals((outcome as Committed).schedule, found)
        assertEquals(null, ledger.schedules.find(ledger.operation()).valueOrFail())
    }
}
