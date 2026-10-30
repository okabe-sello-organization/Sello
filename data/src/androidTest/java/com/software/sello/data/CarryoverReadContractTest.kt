package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomScheduleCatchUp
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.CarryBreakdown
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.DropPreview
import com.software.sello.domain.model.LimitedBudget
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CarryHistory
import com.software.sello.domain.port.CarryReadFailure
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DisableCarry
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.RecordedOccurrence
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Carry read back from a real database file (ADR 0020 CP7, CP8, CP18): derived on every read
 * from rows of one revision, the same through every read, and after reopening the file.
 * Expected amounts are copied from docs/testing/signed-carryover-examples.md.
 */
@RunWith(AndroidJUnit4::class)
class CarryoverReadContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val histories by lazy { CarryHistories(ledger) }

    private fun cop(pesos: Long) = Money.cop(pesos)

    private fun month(text: String) = YearMonth.parse(text)

    private suspend fun history(category: Int, through: String) =
        ledger.carry.history(ledger.categoryId(category), month(through))

    private suspend fun snapshot(of: String) = ledger.snapshots.read(month(of))

    private val sc01 = listOf(
        "2027-01|K 0|C 100000|S 20000|R 80000|WithinLimit",
        "2027-02|K 80000|C 180000|S 70000|R 110000|WithinLimit",
        "2027-03|K 110000|C 210000|S 250000|R -40000|Over",
        "2027-04|K -40000|C 60000|S 0|R 60000|OnTrack"
    )

    /** SC01: the replay, Recibo's month and the overall budget agree, before and after reopening. */
    @Test
    fun sc01ReadsTheSameFromEveryReadAndAfterReopening() = runBlocking {
        histories.sc01()

        val readings = List(2) { reopened ->
            if (reopened == 1) ledger.reopen()
            val history = history(100, "2027-04").valueOrFail()
            val march = snapshot("2027-03").valueOrFail()
            val april = snapshot("2027-04").valueOrFail()
            listOf(
                history,
                march.categories.single().budget,
                april.categories.single().budget,
                april.overall
            )
        }

        val history = readings.first()[0] as CarryHistory
        assertEquals(sc01, history.replay.months.map { it.text() })
        assertEquals(5L, history.revision)
        assertEquals(LocalDate.parse("2027-04-05"), history.asOf)
        assertEquals(1L, history.generation)
        assertEquals(
            listOf(CarryChain.of(ledger.categoryId(100), month("2027-01")).valueOrFail()),
            history.chains
        )
        val march = readings.first()[1] as LimitedBudget
        assertEquals(
            listOf(cop(210_000), cop(250_000), cop(-40_000)),
            listOf(march.limit, march.spent, march.remaining)
        )
        assertEquals(BudgetStanding.Past(PastVerdict.Over), march.standing)
        assertEquals(
            CarryBreakdown(cop(100_000), cop(110_000), cop(210_000), month("2027-01")),
            march.carry
        )
        val april = readings.first()[2] as LimitedBudget
        assertEquals(listOf(cop(60_000), cop(60_000)), listOf(april.limit, april.remaining))
        assertEquals(
            CarryBreakdown(cop(100_000), cop(-40_000), cop(60_000), month("2027-01")),
            april.carry
        )
        val overall = (readings.first()[3] as OverallBudget.Limited).budget
        assertEquals(CarryBreakdown(cop(100_000), cop(-40_000), cop(60_000), null), overall.carry)
        assertEquals(readings.first(), readings.last())
    }

    /** A correction committing while carry is read: each read is all before or all after. */
    @Test
    fun readsDuringAConfirmedCorrectionSeeOneRevisionOrTheOther() = runBlocking {
        histories.sc01()
        val go = CompletableDeferred<Unit>()
        val operation = ledger.operation()
        val shown = ledger.expenses.delete(DeleteExpense(operation, 1, expenseIdOf(101), 1))
        val delete = DeleteExpense(
            operation,
            1,
            expenseIdOf(101),
            1,
            shown.refusedCorrection().confirmation
        )

        val reads = List(24) {
            async(Dispatchers.Default) {
                go.await()
                val history = history(100, "2027-04").valueOrFail()
                val april = snapshot("2027-04").valueOrFail()
                val figures = history.replay.months.drop(1).joinToString("|") {
                    "${it.capacity.minorUnits}"
                }
                "${history.revision}|$figures" to
                    "${april.revision}|${april.categories.single().budget?.limit?.minorUnits}"
            }
        }
        val saved = async(Dispatchers.Default) {
            go.await()
            ledger.expenses.delete(delete)
        }
        go.complete(Unit)
        val seen = reads.awaitAll()

        assertTrue("${saved.await()}", saved.await() is CommandOutcome.Committed)
        // SC25's figures before the deletion, or after it; never some of each.
        assertTrue(
            "$seen",
            seen.all { it.first in setOf("5|180000|210000|60000", "6|200000|230000|80000") }
        )
        assertTrue("$seen", seen.all { it.second in setOf("5|60000", "6|80000") })
    }

    /**
     * SC47: a payment of 300.000 due 5 feb is reserved in mar until catch-up records it in
     * feb; the headline does not move and the notice names feb and mar. Deleting it again
     * is a correction listed first, and it leaves the notice.
     */
    @Test
    fun catchUpRecordingInAnEarlierChainMonthKeepsTheHeadlineAndNamesWhatChanged() = runBlocking {
        val hogar = histories.category("Hogar", 1_000_000, "2027-01-05")
        histories.enable(hogar, version = 1)
        histories.spend(hogar, "2027-01-10", 1_000_000)
        ledger.goTo("2027-02-01")
        ledger.schedules.submit(
            CreateSchedule(
                ledger.operation(),
                1,
                ScheduledEntry.Payment(
                    ledger.categoryId(hogar),
                    TransactionAmount.of(cop(300_000)).valueOrFail(),
                    Note.of(null).valueOrFail()
                ),
                RepeatRule.Monthly.of(5).valueOrFail(),
                LocalDate.parse("2027-02-05"),
                LocalDate.parse("2027-02-28")
            )
        )
        histories.spend(hogar, "2027-02-10", 800_000)
        histories.spend(hogar, "2027-03-10", 500_000)
        val before = snapshot("2027-03").valueOrFail()

        val batch = RoomScheduleCatchUp(ledger.database, { ledger.audit }, {
            uuid(ledger.nextRecord++)
        }).recordDue(LocalDate.parse("2027-03-10"), 50) as CatchUpBatch.Recorded
        val recorded = (batch.occurrences.single() as RecordedOccurrence.Payment).expenseId
        val after = snapshot("2027-03").valueOrFail()
        val notice = ledger.carry.catchUpNotice(listOf(recorded)).valueOrFail()

        val reservedBefore = before.overall as OverallBudget.Limited
        assertEquals(
            listOf(cop(1_200_000), cop(500_000), cop(700_000)),
            reservedBefore.budget.let { listOf(it.limit, it.spent, it.remaining) }
        )
        assertEquals(cop(300_000), reservedBefore.reservation?.total)
        assertEquals(cop(400_000), reservedBefore.reservation?.margin)
        val recordedAfter = after.overall as OverallBudget.Limited
        assertEquals(
            listOf(cop(900_000), cop(500_000), cop(400_000)),
            recordedAfter.budget.let { listOf(it.limit, it.spent, it.remaining) }
        )
        assertEquals(null, recordedAfter.reservation)
        assertEquals(after.revision, notice.revision)
        assertEquals(LocalDate.parse("2027-03-10"), notice.asOf)
        val change = notice.categories.single()
        assertEquals(
            listOf("100|2027-03|C 1200000→900000|R 700000→400000|OnTrack→OnTrack"),
            change.months.map { it.text() }
        )
        assertEquals(
            listOf("100|2027-02|C 1000000→1000000|R 200000→-100000|WithinLimit→Over"),
            change.edited.map { it.text() }
        )

        val asked = ledger.expenses.delete(DeleteExpense(ledger.operation(), 1, recorded, 1))
        val listed = asked.listedCorrection()
        assertEquals(
            listOf("100|2027-03|C 900000→1200000|R 400000→700000|OnTrack→OnTrack"),
            listed.months.map { it.text() }
        )
        assertEquals(
            listOf("100|2027-02|C 1000000→1000000|R -100000→200000|Over→WithinLimit"),
            listed.edited.map { it.text() }
        )
        val deleted =
            ledger.expenses.delete(
                DeleteExpense(
                    ledger.operation(),
                    1,
                    recorded,
                    1,
                    asked.refusedCorrection().confirmation
                )
            )
        assertTrue("$deleted", deleted is CommandOutcome.Committed)
        assertEquals(
            emptyList<Any>(),
            ledger.carry.catchUpNotice(listOf(recorded)).valueOrFail().categories
        )
    }

    @Test
    fun aMonthNotYetStartedOrACategoryThatDoesNotExistHasNoHistory() = runBlocking {
        histories.sc01()

        assertEquals(
            Outcome.Failure(
                CarryReadFailure.Carryover(CarryoverFailure.FutureMonth(month("2027-04")))
            ),
            history(100, "2027-05")
        )
        assertEquals(
            Outcome.Failure(CarryReadFailure.CategoryMissing),
            history(999, "2027-04")
        )
    }

    /** CP8: a chain month without a finite base, or a damaged chain, fails; never base alone or 0. */
    @Test
    fun whatCannotBeCalculatedFailsEveryFigureThatNeedsIt() = runBlocking {
        histories.sc01()
        // Damage no command can make (CP13): feb, inside the chain, unlimited.
        ledger.database.insertRaw(
            "month_limit",
            "category_id" to uuid(100),
            "month" to "2027-02",
            "kind" to "unlimited",
            "limit_minor" to null,
            "currency" to "COP"
        )

        val noBase = SnapshotFailure.Carryover(
            CarryoverFailure.NoFiniteBase(month("2027-02")),
            ledger.categoryId(100)
        )
        assertEquals(Outcome.Failure(noBase), snapshot("2027-04"))
        assertEquals(
            Outcome.Failure(
                CarryReadFailure.Carryover(CarryoverFailure.NoFiniteBase(month("2027-02")))
            ),
            history(100, "2027-04")
        )

        ledger.database.openHelper.writableDatabase.execSQL("DELETE FROM month_limit")
        ledger.database.insertRaw(
            "carry_chain",
            "category_id" to uuid(100),
            "start_month" to "2026-11",
            "end_month" to "2026-10"
        )
        val damaged = StorageFailure.Integrity("carry_chain", "${uuid(100)}/2026-11", "end_month")
        assertEquals(
            Outcome.Failure(SnapshotFailure.Storage(damaged)),
            snapshot("2027-04")
        )
        assertEquals(
            Outcome.Failure(CarryReadFailure.Storage(damaged)),
            history(100, "2027-04")
        )
    }

    /**
     * SC19's shape: Comida carried ene–feb, was turned off with −20.000 and on again from abr.
     * A month's carry-in and its drop read only the chain covering it (SELLO-038 consumer
     * boundary): with ene's rows damaged, may's snapshot and the drop in may still work,
     * while the replay of every chain, which must read ene, fails.
     */
    @Test
    fun aMonthOfTheLatestChainNeedsNoHistoryOfAnEarlierChain() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        histories.enable(comida, version = 1)
        histories.spend(comida, "2027-01-10", 120_000)
        ledger.goTo("2027-02-10")
        ledger.categories.submit(
            DisableCarry(ledger.operation(), 1, ledger.categoryId(comida), 2, cop(-20_000))
        )
        ledger.goTo("2027-04-03")
        histories.enable(comida, version = 3)
        histories.spend(comida, "2027-04-05", 30_000)
        ledger.goTo("2027-05-10")
        // Damage only in ene, the earlier chain: an expense of 0 and an unlimited own limit.
        ledger.database.insertRaw(
            "expense",
            "id" to uuid(900),
            "category_id" to uuid(comida),
            "effective_date" to "2027-01-15",
            "sequence" to 900L,
            "amount_minor" to 0L,
            "currency" to "COP",
            "note" to null,
            "version" to 1L,
            "created_at" to 0L,
            "updated_at" to 0L,
            "origin_schedule_id" to null,
            "origin_date" to null,
            "origin_slot" to null
        )
        ledger.database.insertRaw(
            "month_limit",
            "category_id" to uuid(comida),
            "month" to "2027-01",
            "kind" to "unlimited",
            "limit_minor" to null,
            "currency" to "COP"
        )

        val may = snapshot("2027-05").valueOrFail().categories.single().budget!!
        val asked = ledger.categories.submit(
            DisableCarry(ledger.operation(), 1, ledger.categoryId(comida), 4)
        )
        val dropped = ledger.categories.submit(
            DisableCarry(ledger.operation(), 1, ledger.categoryId(comida), 4, cop(70_000))
        )
        val everyChain = history(comida, "2027-05")

        // abr: K 0, C 100.000, spent 30.000, R 70.000; may: K 70.000, C 170.000.
        assertEquals(
            CarryBreakdown(cop(100_000), cop(70_000), cop(170_000), month("2027-04")),
            may.carry
        )
        assertEquals(
            CommandOutcome.Rejected(
                Specific(
                    CategoryRejection.DropUnconfirmed(
                        DropPreview(
                            month("2027-05"),
                            carryIn = cop(70_000),
                            base = cop(100_000),
                            capacityBefore = cop(170_000),
                            capacityAfter = cop(100_000),
                            remainingBefore = cop(170_000),
                            remainingAfter = cop(100_000)
                        )
                    )
                )
            ),
            asked
        )
        assertTrue("$dropped", dropped is CommandOutcome.Committed)
        assertEquals(
            listOf("${uuid(comida)}|2027-01|2027-02", "${uuid(comida)}|2027-04|2027-05"),
            ledger.chainRows()
        )
        assertTrue(
            "$everyChain",
            (everyChain as? Outcome.Failure)?.error is CarryReadFailure.Storage
        )
    }
}
