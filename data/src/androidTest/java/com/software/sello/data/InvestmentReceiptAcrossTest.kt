package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.InvestmentMovementKind.Dividend
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.model.InvestmentMovementKind.Withdrawal
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.DatedUsdRate
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentMovementLine
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.InvestmentRejection.LinesRefused
import com.software.sello.domain.port.RecordInvestmentMovements
import com.software.sello.domain.port.RefusedInvestmentLine
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Movements of several investments on one receipt (SELLO-140), against a real database
 * file through the real commands and reads. Every expected figure is worked out by hand
 * in the comment beside it. "Fondo" is an aporte of 1.000.000 COP on 1 oct with its value
 * the same (records 100–102). The second investment is records 103–105: "CDT", 2.000.000
 * COP on 1 sep, or "Acciones", USD 1.000,00 on 20 oct with a rate of 4.150,25 (rate 106).
 * Operations are numbered from 1000; today is 25 October 2026.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentReceiptAcrossTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val today = LocalDate.of(2026, 10, 25)
    private val fondo = investmentId(100)
    private val second = investmentId(103)

    @Before
    fun startToday() {
        ledger.goTo("2026-10-25")
    }

    private suspend fun fondo() {
        ledger.created(ledger.createInvestment("Fondo", pesos(1_000_000), "2026-10-01"))
    }

    private suspend fun cdt() {
        ledger.created(ledger.createInvestment("CDT", pesos(2_000_000), "2026-09-01"))
    }

    private suspend fun acciones() {
        ledger.created(
            ledger.createInvestment(
                "Acciones",
                cents(100_000),
                "2026-10-20",
                newRate = DatedUsdRate(rate(415_025), day("2026-10-20"))
            )
        )
    }

    private fun line(
        investment: InvestmentId,
        kind: InvestmentMovementKind,
        amount: InvestmentAmount,
        date: String
    ) = InvestmentMovementLine(investment, kind, amount, LocalDate.parse(date), null)

    /** The lines, with the version read now of each investment they name. */
    private suspend fun batch(
        vararg lines: InvestmentMovementLine,
        operation: OperationId = ledger.operation()
    ) = RecordInvestmentMovements(
        operation,
        1,
        lines.map { it.investmentId }.distinct()
            .associateWith { ledger.investmentVersion(it) },
        lines.toList()
    )

    private suspend fun submit(command: RecordInvestmentMovements) =
        ledger.investments.submit(command)

    private fun committed(outcome: InvestmentCommandOutcome): CommittedInvestment =
        (outcome as? Committed)?.value ?: throw AssertionError("not committed: $outcome")

    private fun refused(outcome: InvestmentCommandOutcome): List<RefusedInvestmentLine> =
        (((outcome as? Rejected)?.reason as? Specific)?.reason as? LinesRefused)?.lines
            ?: throw AssertionError("no line refused: $outcome")

    private fun movements() = ledger.rows(
        "investment_movement",
        "investment_id",
        "sequence",
        "id",
        "kind",
        "amount_minor",
        "currency",
        "effective_date"
    )

    private fun investments() = ledger.rows("investment", "id", "next_sequence", "version")

    private fun receipts() = ledger.rows("operation_receipt", "revision", "kind", "subject_id")

    /** Everything a receipt may write: movements, investment rows, revision, receipts, rates. */
    private fun state() = listOf(
        movements(),
        investments(),
        listOf(ledger.revision().toString()),
        receipts(),
        ledger.rows("usd_rate", "id")
    )

    // ---- Saving ----

    @Test
    fun aCopAndAUsdInvestmentShareOneReceiptEachLineInItsOwnCurrency() = runBlocking {
        fondo()
        acciones()

        val saved = committed(
            submit(
                batch(
                    line(fondo, Contribution, pesos(200_000), "2026-10-05"),
                    line(second, Contribution, cents(25_050), "2026-10-21"),
                    line(fondo, Valuation, pesosValue(1_300_000), "2026-10-22"),
                    line(second, Dividend, cents(199), "2026-10-22")
                )
            )
        )

        assertEquals(OperationKind.RecordInvestmentMovements, saved.receipt.kind)
        assertNull(saved.investmentId)
        assertEquals(listOf(fondo, second), saved.investmentIds)
        val ids = saved.movementIds!!.map { it.value }
        assertEquals(4, ids.toSet().size)
        // Each investment's next positions, in line order; pesos and cents as typed.
        assertEquals(
            listOf(
                "${uuid(100)}|0|${uuid(101)}|contribution|1000000|COP|2026-10-01",
                "${uuid(100)}|1|${uuid(102)}|valuation|1000000|COP|2026-10-01",
                "${uuid(100)}|2|${ids[0]}|contribution|200000|COP|2026-10-05",
                "${uuid(100)}|3|${ids[2]}|valuation|1300000|COP|2026-10-22",
                "${uuid(103)}|0|${uuid(104)}|contribution|100000|USD|2026-10-20",
                "${uuid(103)}|1|${uuid(105)}|valuation|100000|USD|2026-10-20",
                "${uuid(103)}|2|${ids[1]}|contribution|25050|USD|2026-10-21",
                "${uuid(103)}|3|${ids[3]}|dividend|199|USD|2026-10-22"
            ),
            movements()
        )
        // One version raise each, one revision and one receipt naming both.
        assertEquals(listOf("${uuid(100)}|4|2", "${uuid(103)}|4|2"), investments())
        assertEquals(3L, ledger.revision())
        assertEquals(
            "3|investment.record_movements|${uuid(100)},${uuid(103)}",
            receipts().last()
        )
        // Nothing converted: the one rate is still the one entered with Acciones.
        assertEquals(listOf(uuid(106)), ledger.rows("usd_rate", "id"))
        val fondoFigures = ledger.summary(fondo).figures
        // 1.000.000 + 200.000 in; the value of 22 oct is 1.300.000.
        assertEquals(Money.cop(1_200_000), fondoFigures.contributed)
        assertEquals(Money.cop(1_300_000), fondoFigures.value!!.amount)
        val dollars = ledger.summary(second).figures
        // USD 1.000,00 + 250,50 = 1.250,50 in, and the value moves with the aporte.
        assertEquals(Money(125_050, Currency.USD), dollars.contributed)
        assertEquals(
            Money(125_050, Currency.USD),
            dollars.value!!.amount
        )
        // 1.250,50 + 1,99 dividend − 1.250,50 = USD 1,99.
        assertEquals(Money(199, Currency.USD), dollars.gain)
    }

    // ---- Each investment against its own history ----

    @Test
    fun aRefusedLineOfOneInvestmentLeavesTheOtherUntouched() = runBlocking {
        fondo()
        cdt()
        val before = state()

        val outcome = submit(
            batch(
                line(fondo, Contribution, pesos(100_000), "2026-10-02"),
                line(second, Withdrawal, pesos(2_500_000), "2026-10-10")
            )
        )

        // CDT holds 2.000.000 by 10 oct; Fondo's line was fine and is not written either.
        val named = refused(outcome).single()
        assertEquals(1, named.position)
        assertEquals(
            Money.cop(2_000_000),
            (named.reason as InvestmentRejection.AboveValue).available
        )
        assertEquals(before, state())
    }

    @Test
    fun aLineChecksOnlyAgainstItsOwnInvestmentAndEveryInvestmentAtFaultIsNamed() = runBlocking {
        fondo()
        cdt()
        val before = state()

        // CDT's value of 5.000.000 does not cover Fondo's retiro.
        val crossed = submit(
            batch(
                line(second, Valuation, pesosValue(5_000_000), "2026-10-05"),
                line(fondo, Withdrawal, pesos(1_500_000), "2026-10-10")
            )
        )
        // CDT's aporte of 1 sep does not let a Fondo line go before Fondo's own of 1 oct.
        val early = submit(
            batch(
                line(second, Contribution, pesos(100_000), "2026-10-02"),
                line(fondo, Dividend, pesos(5_000), "2026-09-15")
            )
        )
        // Both investments at fault: each named, in line order.
        val both = submit(
            batch(
                line(second, Contribution, pesos(100_000), "2026-10-02"),
                line(fondo, Withdrawal, pesos(1_500_000), "2026-10-10"),
                line(second, Dividend, pesos(5_000), "2026-08-15")
            )
        )

        // Fondo has 1.000.000 by 10 oct, not CDT's 5.000.000.
        val over = refused(crossed).single()
        assertEquals(1, over.position)
        assertEquals(
            Money.cop(1_000_000),
            (over.reason as InvestmentRejection.AboveValue).available
        )
        assertEquals(
            listOf(
                RefusedInvestmentLine(
                    1,
                    InvestmentRejection.BeforeFirstContribution(day("2026-10-01"))
                )
            ),
            refused(early)
        )
        val named = refused(both)
        assertEquals(listOf(1, 2), named.map { it.position })
        // Fondo has 1.000.000 by 10 oct, not CDT's 2.100.000.
        assertEquals(
            Money.cop(1_000_000),
            (named[0].reason as InvestmentRejection.AboveValue).available
        )
        assertEquals(
            InvestmentRejection.BeforeFirstContribution(day("2026-09-01")),
            named[1].reason
        )
        assertEquals(before, state())

        // With Fondo's own value first, the same retiro saves: 2.000.000 − 1.500.000.
        committed(
            submit(
                batch(
                    line(second, Valuation, pesosValue(5_000_000), "2026-10-05"),
                    line(fondo, Withdrawal, pesos(1_500_000), "2026-10-10"),
                    line(fondo, Valuation, pesosValue(2_000_000), "2026-10-06")
                )
            )
        )
        assertEquals(Money.cop(500_000), ledger.summary(fondo).figures.value!!.amount)
        assertEquals(Money.cop(5_000_000), ledger.summary(second).figures.value!!.amount)
    }

    @Test
    fun aLineInAnotherInvestmentsCurrencyIsRefusedAndNothingIsWritten() = runBlocking {
        fondo()
        acciones()
        val before = state()

        val outcome = submit(
            batch(
                line(fondo, Contribution, cents(10_000), "2026-10-21"),
                line(second, Contribution, pesos(100_000), "2026-10-21"),
                line(fondo, Contribution, pesos(100_000), "2026-10-21"),
                line(second, Contribution, cents(10_000), "2026-10-21")
            )
        )

        assertEquals(
            Rejected(
                Specific(
                    LinesRefused(
                        listOf(
                            RefusedInvestmentLine(0, InvestmentRejection.CurrencyMismatch),
                            RefusedInvestmentLine(1, InvestmentRejection.CurrencyMismatch)
                        )
                    )
                )
            ),
            outcome
        )
        assertEquals(before, state())
    }

    @Test
    fun aChangedMissingOrUnreadInvestmentRefusesTheWholeReceipt() = runBlocking {
        fondo()
        cdt()
        val read = batch(
            line(fondo, Contribution, pesos(100_000), "2026-10-02"),
            line(second, Contribution, pesos(100_000), "2026-10-02")
        )
        // CDT changes after the receipt read it at version 1: it is version 2 now.
        ledger.recorded(second, Dividend, pesos(1_000), "2026-10-03")
        val before = state()

        val changed = submit(read)
        val missing = submit(
            read.copy(
                operationId = ledger.operation(),
                expectedVersions = mapOf(fondo to 1L, investmentId(999) to 1L),
                lines = listOf(
                    read.lines[0],
                    read.lines[1].copy(investmentId = investmentId(999))
                )
            )
        )
        val unread = submit(
            read.copy(operationId = ledger.operation(), expectedVersions = mapOf(fondo to 1L))
        )
        val extra = submit(
            read.copy(
                operationId = ledger.operation(),
                expectedVersions = mapOf(fondo to 1L, second to 2L, investmentId(999) to 1L)
            )
        )

        assertEquals(Rejected(CommandRejection.VersionConflict(current = 2)), changed)
        assertEquals(Rejected(Specific(InvestmentRejection.InvestmentMissing)), missing)
        assertEquals(Rejected(Specific(InvestmentRejection.VersionsUnmatched)), unread)
        assertEquals(Rejected(Specific(InvestmentRejection.VersionsUnmatched)), extra)
        assertEquals(before, state())
    }

    // ---- Replay and recovery ----

    @Test
    fun theSameReceiptAgainIsTheOriginalAndAnyChangeToItConflicts() = runBlocking {
        fondo()
        cdt()
        val operation = ledger.operation()
        val command = batch(
            line(fondo, Contribution, pesos(300_000), "2026-10-02"),
            line(second, Dividend, pesos(20_000), "2026-10-03"),
            operation = operation
        )
        val original = committed(submit(command))
        val after = state()

        // The same versions given in another order are the same receipt.
        val reordered = submit(
            command.copy(expectedVersions = mapOf(second to 1L, fondo to 1L))
        )
        val moved = submit(
            command.copy(
                lines = listOf(command.lines[0].copy(investmentId = second), command.lines[1]),
                expectedVersions = mapOf(second to 1L)
            )
        )
        val otherVersion = submit(command.copy(expectedVersions = mapOf(fondo to 1L, second to 2L)))
        ledger.reopen()
        val afterRestart = submit(command)

        assertEquals(Committed(original, replayed = true), reordered)
        assertEquals(Rejected(CommandRejection.OperationConflict), moved)
        assertEquals(Rejected(CommandRejection.OperationConflict), otherVersion)
        assertEquals(Committed(original, replayed = true), afterRestart)
        assertEquals(after, state())
        assertEquals(
            Outcome.Success(
                CommittedInvestment(
                    original.receipt,
                    null,
                    movementIds = null,
                    investmentIds = listOf(fondo, second)
                )
            ),
            ledger.investments.find(operation)
        )
    }

    @Test
    fun aFailureAtTheSecondInvestmentsLineLeavesTheFirstUntouched() = runBlocking {
        fondo()
        cdt()
        ledger.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_cdt_line BEFORE INSERT ON investment_movement " +
                "WHEN NEW.investment_id = '${uuid(103)}' BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val command = batch(
            line(fondo, Contribution, pesos(100_000), "2026-10-02"),
            line(second, Contribution, pesos(200_000), "2026-10-03")
        )
        val before = state()

        val outcome = submit(command)

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Unavailable("SQLiteConstraintException")
                )
            ),
            outcome
        )
        assertEquals(before, state())
        assertEquals(Outcome.Success(null), ledger.investments.find(command.operationId))

        ledger.database.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_cdt_line")
        committed(submit(command))
        // 1.000.000 + 100.000 and 2.000.000 + 200.000.
        assertEquals(Money.cop(1_100_000), ledger.summary(fondo).figures.contributed)
        assertEquals(Money.cop(2_200_000), ledger.summary(second).figures.contributed)
    }

    @Test
    fun aFailureAtTheCommitAcrossInvestmentsIsUnknownAndFoundWithTheSameOperation() = runBlocking {
        fondo()
        cdt()
        val command = batch(
            line(fondo, Contribution, pesos(100_000), "2026-10-02"),
            line(second, Contribution, pesos(200_000), "2026-10-03")
        )
        val before = state()
        // A deferred check refuses the transaction only when it commits.
        ledger.whenAWriteIsStamped = {
            ledger.whenAWriteIsStamped = {}
            ledger.database.openHelper.writableDatabase.apply {
                execSQL("PRAGMA defer_foreign_keys = ON")
                execSQL(
                    "INSERT INTO investment_movement (id, investment_id, sequence, kind, " +
                        "amount_minor, currency, effective_date, note, version, " +
                        "created_at, updated_at) VALUES ('${uuid(990)}', '${uuid(999)}', " +
                        "0, 'contribution', 1, 'COP', '2026-10-01', NULL, 1, 0, 0)"
                )
            }
        }

        val outcome = submit(command)

        assertEquals(CommandOutcome.OutcomeUnknown(command.operationId), outcome)
        assertEquals(Outcome.Success(null), ledger.investments.find(command.operationId))
        assertEquals(before, state())
        val saved = committed(submit(command))
        assertEquals(listOf(fondo, second), saved.investmentIds)
        // 1.000.000 + 100.000 and 2.000.000 + 200.000.
        assertEquals(Money.cop(1_100_000), ledger.summary(fondo).figures.contributed)
        assertEquals(Money.cop(2_200_000), ledger.summary(second).figures.contributed)
    }

    @Test
    fun aReceiptStoredBySello129sCommandIsFoundButNeverReplayedByTheNewCommand() = runBlocking {
        fondo()
        // As SELLO-129 stored it: its one investment as the subject, and the format-1
        // identity its code printed (at 0369b338) for these two lines on Fondo, version 1.
        val operation = ledger.operation(900)
        ledger.database.insertRaw(
            "operation_receipt",
            "operation_id" to operation.value,
            "kind" to "investment.record_movements",
            "input_digest" to "20c8cf2e18aa254bb30382c1be1c6a2e0bb0306fc22ceb1ce1493da3c2aaa93f",
            "generation" to 1L,
            "revision" to 2L,
            "subject_id" to uuid(100),
            "committed_at" to recordedAt.toEpochMilli()
        )
        val before = state()

        // The same lines with the same operation: format 2 never equals format 1.
        val again = submit(
            batch(
                line(fondo, Contribution, pesos(300_000), "2026-10-02"),
                line(fondo, Dividend, pesos(20_000), "2026-10-03"),
                operation = operation
            )
        )

        assertEquals(Rejected(CommandRejection.OperationConflict), again)
        assertEquals(before, state())
        assertEquals(
            Outcome.Success(
                CommittedInvestment(
                    OperationReceipt(
                        operation,
                        OperationKind.RecordInvestmentMovements,
                        1,
                        2,
                        Instant.ofEpochMilli(recordedAt.toEpochMilli())
                    ),
                    null,
                    movementIds = null,
                    investmentIds = listOf(fondo)
                )
            ),
            ledger.investments.find(operation)
        )
    }

    @Test
    fun aSecondInvestmentWithNoRecordingPositionLeftUndoesTheFirstsWrites() = runBlocking {
        fondo()
        cdt()
        // CDT's one line takes the last position, and none follows it for its next movement;
        // Fondo's line and version raise are written before CDT's counter is reached.
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE investment SET next_sequence = ${Long.MAX_VALUE} WHERE id = '${uuid(103)}'"
        )
        val before = state()

        val outcome = submit(
            batch(
                line(fondo, Contribution, pesos(100_000), "2026-10-02"),
                line(second, Contribution, pesos(200_000), "2026-10-03")
            )
        )

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Integrity("investment", uuid(103), "next_sequence")
                )
            ),
            outcome
        )
        assertEquals(before, state())
    }
}
