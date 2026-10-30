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
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
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
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Several movements of one investment saved as one operation (SELLO-129), against a real
 * database file through the real commands and reads. Every expected figure is worked out
 * by hand in the comment beside it. Unless a test says otherwise the investment is
 * "Fondo": an aporte of 1.000.000 on 1 oct and its value the same (records 100–102,
 * revision 1, version 1). Operations are numbered from 1000; today is 25 October 2026.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentBatchCommandTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val today = LocalDate.of(2026, 10, 25)
    private val first = investmentId(100)

    @Before
    fun startToday() {
        ledger.goTo("2026-10-25")
    }

    private suspend fun fondo() {
        ledger.created(ledger.createInvestment("Fondo", pesos(1_000_000), "2026-10-01"))
    }

    private fun line(
        kind: InvestmentMovementKind,
        amount: InvestmentAmount,
        date: String,
        note: String? = null
    ) = InvestmentMovementLine(kind, amount, LocalDate.parse(date), Note.of(note).valueOrFail())

    private suspend fun batch(
        vararg lines: InvestmentMovementLine,
        operation: OperationId = ledger.operation(),
        investment: InvestmentId = first,
        version: Long? = null,
        generation: Long = 1
    ) = RecordInvestmentMovements(
        operation,
        generation,
        investment,
        version ?: ledger.investmentVersion(first),
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
        "sequence",
        "id",
        "kind",
        "amount_minor",
        "currency",
        "effective_date",
        "note"
    )

    private fun investmentRow() = ledger.rows("investment", "next_sequence", "version")

    private fun receipts() = ledger.rows("operation_receipt", "revision", "kind", "subject_id")

    /** Everything a batch may write: movements, the investment's row, revision, receipts. */
    private fun state() = listOf(
        movements(),
        investmentRow(),
        listOf(ledger.revision().toString()),
        receipts()
    )

    private suspend fun figures() = ledger.summary(first).figures

    private fun dollarValue(cents: Long) =
        InvestmentAmount.ofValue(Money(cents, Currency.USD)).valueOrFail()

    // ---- Saving ----

    @Test
    fun threeLinesAreOneReceiptOneRevisionAndOneVersionWithTheirIdsInLineOrder() = runBlocking {
        fondo()
        val command = batch(
            line(Valuation, pesosValue(1_500_000), "2026-10-05", note = "Extracto"),
            line(Withdrawal, pesos(1_200_000), "2026-10-10"),
            line(Contribution, pesos(200_000), "2026-10-12")
        )

        val saved = committed(submit(command))

        assertEquals(OperationKind.RecordInvestmentMovements, saved.receipt.kind)
        assertEquals(first, saved.investmentId)
        val ids = saved.movementIds.map { it.value }
        assertEquals(3, ids.toSet().size)
        // The next three recording positions, in line order.
        assertEquals(
            listOf(
                "0|${uuid(101)}|contribution|1000000|COP|2026-10-01|null",
                "1|${uuid(102)}|valuation|1000000|COP|2026-10-01|null",
                "2|${ids[0]}|valuation|1500000|COP|2026-10-05|Extracto",
                "3|${ids[1]}|withdrawal|1200000|COP|2026-10-10|null",
                "4|${ids[2]}|contribution|200000|COP|2026-10-12|null"
            ),
            movements()
        )
        assertEquals(listOf("5|2"), investmentRow())
        assertEquals(2L, ledger.revision())
        assertEquals(
            listOf(
                "1|investment.create|${uuid(100)}",
                "2|investment.record_movements|${uuid(100)}"
            ),
            receipts()
        )
        val figures = figures()
        // 1.500.000 − 1.200.000 + 200.000 = 500.000; aportes 1.000.000 + 200.000.
        assertEquals(Money.cop(500_000), figures.value!!.amount)
        assertEquals(Money.cop(1_200_000), figures.contributed)
        assertEquals(Money.cop(1_200_000), figures.withdrawn)
        // 500.000 + 1.200.000 − 1.200.000 = 500.000.
        assertEquals(Money.cop(500_000), figures.gain)
    }

    @Test
    fun aRetiroThatNeedsTheValueOfALaterLineIsCheckedAfterItByDate() = runBlocking {
        fondo()
        val operation = ledger.operation()
        val before = state()

        // Alone, the retiro of 1.200.000 is above the 1.000.000 recorded by 10 oct.
        val alone = submit(
            batch(line(Withdrawal, pesos(1_200_000), "2026-10-10"), operation = operation)
        )
        assertEquals(before, state())
        val withValue = submit(
            batch(
                line(Withdrawal, pesos(1_200_000), "2026-10-10"),
                line(Valuation, pesosValue(1_500_000), "2026-10-05"),
                operation = operation
            )
        )

        val named = refused(alone).single()
        assertEquals(0, named.position)
        val reason = named.reason as InvestmentRejection.AboveValue
        assertEquals(Money.cop(1_000_000), reason.available)
        // The retiro the refusal named is the line itself, never written then.
        assertEquals(committed(withValue).movementIds[0], reason.withdrawal)
        // The value of 5 oct replays first: 1.500.000 − 1.200.000 = 300.000.
        assertEquals(Money.cop(300_000), figures().value!!.amount)
    }

    @Test
    fun onTheSameDateTheLinesReplayInLineOrderAfterWhatIsRecorded() = runBlocking {
        fondo()
        ledger.recorded(first, Valuation, pesosValue(4_000_000), "2026-10-20")

        committed(
            submit(
                batch(
                    line(Contribution, pesos(500_000), "2026-10-20"),
                    line(Valuation, pesosValue(3_000_000), "2026-10-21"),
                    line(Contribution, pesos(250_000), "2026-10-21")
                )
            )
        )

        // 20 oct: the recorded 4.000.000, then the aporte. 21 oct: the value of 3.000.000,
        // then the aporte of 250.000 = 3.250.000.
        assertEquals(Money.cop(3_250_000), figures().value!!.amount)
    }

    // ---- Refusing ----

    @Test
    fun everyLineWhoseOwnAmountOrDateIsRefusedIsNamedAndNothingIsWritten() = runBlocking {
        fondo()
        val command = batch(
            line(Contribution, pesos(100_000), "2026-10-02"),
            line(Contribution, pesosValue(0), "2026-10-03"),
            line(Valuation, pesosValue(1_100_000), "2026-10-26"),
            line(Contribution, cents(100), "2026-10-04"),
            line(Valuation, pesosValue(1_000_000), LocalDate.of(-1, 1, 1).toString())
        )
        val before = state()

        val outcome = submit(command)

        assertEquals(
            Rejected(
                Specific(
                    LinesRefused(
                        listOf(
                            RefusedInvestmentLine(1, InvestmentRejection.InvalidAmount),
                            RefusedInvestmentLine(2, InvestmentRejection.FutureDate(today)),
                            RefusedInvestmentLine(3, InvestmentRejection.CurrencyMismatch),
                            RefusedInvestmentLine(4, InvestmentRejection.InvalidDate)
                        )
                    )
                )
            ),
            outcome
        )
        assertEquals(before, state())
        assertEquals(Outcome.Success(null), ledger.investments.find(command.operationId))
    }

    @Test
    fun aLineBeforeTheFirstAporteIsNamedAndNothingIsWritten() = runBlocking {
        fondo()
        val before = state()

        val outcome = submit(
            batch(
                line(Contribution, pesos(50_000), "2026-10-02"),
                line(Dividend, pesos(10_000), "2026-09-30")
            )
        )

        assertEquals(
            Rejected(
                Specific(
                    LinesRefused(
                        listOf(
                            RefusedInvestmentLine(
                                1,
                                InvestmentRejection.BeforeFirstContribution(day("2026-10-01"))
                            )
                        )
                    )
                )
            ),
            outcome
        )
        assertEquals(before, state())
    }

    @Test
    fun aRetiroLineWithNoValueYetIsNamedAndNothingIsWritten() = runBlocking {
        fondo()
        val before = state()

        // The earlier aporte moves the first aporte to 1 sep; no value is recorded until 1 oct.
        val outcome = submit(
            batch(
                line(Contribution, pesos(500_000), "2026-09-01"),
                line(Withdrawal, pesos(100_000), "2026-09-15")
            )
        )

        val named = refused(outcome).single()
        assertEquals(1, named.position)
        assertTrue(named.reason is InvestmentRejection.NoValue)
        assertEquals(before, state())
    }

    @Test
    fun aRecordedRetiroALineLeavesShortIsNamedWithTheValueLineAtFault() = runBlocking {
        fondo()
        ledger.recorded(first, Withdrawal, pesos(800_000), "2026-10-15") // 103
        val before = state()

        val outcome = submit(
            batch(
                line(Contribution, pesos(200_000), "2026-10-12"),
                line(Valuation, pesosValue(500_000), "2026-10-05")
            )
        )

        // 5 oct value 500.000; 12 oct + 200.000 = 700.000; the retiro of 15 oct takes 800.000.
        assertEquals(
            Rejected(
                Specific(
                    LinesRefused(
                        listOf(
                            RefusedInvestmentLine(
                                1,
                                InvestmentRejection.AboveValue(movementId(103), Money.cop(700_000))
                            )
                        )
                    )
                )
            ),
            outcome
        )
        assertEquals(before, state())
    }

    @Test
    fun noLineOrMoreThanThirtyIsRefusedAndThirtyAtTheLargestAmountAddUpExactly() = runBlocking {
        fondo()
        val before = state()
        val largest = line(Contribution, pesos(999_999_999_999), "2026-10-02")

        val none = submit(batch())
        val tooMany = submit(batch(*Array(31) { largest }))

        assertEquals(Rejected(Specific(InvestmentRejection.LineCount(0))), none)
        assertEquals(Rejected(Specific(InvestmentRejection.LineCount(31))), tooMany)
        assertEquals(before, state())

        val saved = committed(submit(batch(*Array(30) { largest })))

        assertEquals(30, saved.movementIds.toSet().size)
        assertEquals(listOf("32|2"), investmentRow())
        assertEquals(32L, ledger.database.count("investment_movement"))
        // 1.000.000 + 30 × 999.999.999.999 = 1.000.000 + 29.999.999.999.970.
        assertEquals(Money.cop(30_000_000_999_970), figures().contributed)
        assertEquals(Money.cop(30_000_000_999_970), figures().value!!.amount)
    }

    @Test
    fun anInvestmentReadAtAnOldVersionOrGoneIsRefusedAndNothingIsWritten() = runBlocking {
        fondo()
        val before = state()

        val stale = submit(batch(line(Contribution, pesos(1), "2026-10-02"), version = 0))
        val gone = submit(
            batch(line(Contribution, pesos(1), "2026-10-02"), investment = investmentId(999))
        )

        assertEquals(Rejected(CommandRejection.VersionConflict(current = 1)), stale)
        assertEquals(Rejected(Specific(InvestmentRejection.InvestmentMissing)), gone)
        assertEquals(before, state())
    }

    @Test
    fun aUsdInvestmentsLinesAreStoredInCentsOfDollarsAndNeverConverted() = runBlocking {
        ledger.created(
            ledger.createInvestment(
                "Acciones EE. UU.",
                cents(100_000),
                "2026-10-20",
                newRate = DatedUsdRate(rate(415_025), day("2026-10-20"))
            )
        )

        val saved = committed(
            submit(
                batch(
                    line(Contribution, cents(25_050), "2026-10-21"),
                    line(Dividend, cents(199), "2026-10-22"),
                    line(Valuation, dollarValue(130_000), "2026-10-23")
                )
            )
        )

        val ids = saved.movementIds.map { it.value }
        assertEquals(
            listOf(
                "2|${ids[0]}|contribution|25050|USD|2026-10-21|null",
                "3|${ids[1]}|dividend|199|USD|2026-10-22|null",
                "4|${ids[2]}|valuation|130000|USD|2026-10-23|null"
            ),
            movements().drop(2)
        )
        assertEquals(1, ledger.rows("usd_rate", "id").size)
        val figures = figures()
        // USD 1.000,00 + 250,50 = 1.250,50 in; gain 1.300,00 + 1,99 − 1.250,50 = USD 51,49.
        assertEquals(Money(125_050, Currency.USD), figures.contributed)
        assertEquals(Money(5_149, Currency.USD), figures.gain)
    }

    // ---- Replay and recovery ----

    @Test
    fun theSameReceiptAgainIsTheOriginalWithItsIdsEvenAfterARestart() = runBlocking {
        fondo()
        val operation = ledger.operation()
        val command = batch(
            line(Contribution, pesos(300_000), "2026-10-02"),
            line(Dividend, pesos(20_000), "2026-10-03"),
            operation = operation
        )
        val original = committed(submit(command))
        val after = state()

        val again = submit(command)
        val other = submit(command.copy(lines = command.lines.reversed()))
        val stale = submit(batch(line(Contribution, pesos(1), "2026-10-02"), generation = 2))
        ledger.reopen()
        val afterRestart = submit(command)

        assertEquals(Committed(original, replayed = true), again)
        assertEquals(Rejected(CommandRejection.OperationConflict), other)
        assertEquals(Rejected(CommandRejection.StaleGeneration(current = 1)), stale)
        assertEquals(Committed(original, replayed = true), afterRestart)
        assertEquals(after, state())
        // The receipt alone names no movements: submitting again gives them.
        assertEquals(
            Outcome.Success(CommittedInvestment(original.receipt, first)),
            ledger.investments.find(operation)
        )
    }

    @Test
    fun aFailureAtTheThirdLineLeavesNoLineVersionRevisionOrReceipt() = runBlocking {
        fondo()
        ledger.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_third_line BEFORE INSERT ON investment_movement " +
                "WHEN NEW.sequence = 4 BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val command = batch(
            line(Contribution, pesos(100_000), "2026-10-02"),
            line(Contribution, pesos(200_000), "2026-10-03"),
            line(Contribution, pesos(300_000), "2026-10-04")
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

        ledger.database.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_third_line")
        committed(submit(command))
        // 1.000.000 + 100.000 + 200.000 + 300.000.
        assertEquals(Money.cop(1_600_000), figures().contributed)
    }

    @Test
    fun aFailureAtTheCommitItselfIsUnknownAndFoundWithTheSameOperation() = runBlocking {
        fondo()
        val command = batch(
            line(Contribution, pesos(100_000), "2026-10-02"),
            line(Contribution, pesos(200_000), "2026-10-03")
        )
        val before = state()
        // A deferred check makes the database refuse the transaction only when it commits:
        // by then every statement of the command has run without error.
        ledger.whenAWriteIsStamped = {
            ledger.whenAWriteIsStamped = {}
            ledger.database.openHelper.writableDatabase.apply {
                execSQL("PRAGMA defer_foreign_keys = ON")
                execSQL(
                    "INSERT INTO investment_movement (id, investment_id, sequence, kind, " +
                        "amount_minor, currency, effective_date, note, version, created_at, " +
                        "updated_at) VALUES ('${uuid(990)}', '${uuid(999)}', 0, 'contribution', " +
                        "1, 'COP', '2026-10-01', NULL, 1, 0, 0)"
                )
            }
        }

        val outcome = submit(command)

        assertEquals(CommandOutcome.OutcomeUnknown(command.operationId), outcome)
        // The refused commit leaves this handle's transaction open; a restart clears it.
        ledger.reopen()
        assertEquals(Outcome.Success(null), ledger.investments.find(command.operationId))
        assertEquals(before, state())
        val saved = committed(submit(command))
        assertFalse(saved.movementIds.isEmpty())
        // 1.000.000 + 100.000 + 200.000.
        assertEquals(Money.cop(1_300_000), figures().contributed)
    }

    @Test
    fun aCallerCancelledWhileTheReceiptIsSavingIsToldSoAndCanRecoverIt() = runBlocking {
        fondo()
        val command = batch(
            line(Contribution, pesos(750_000), "2026-10-02"),
            line(Dividend, pesos(5_000), "2026-10-03")
        )
        lateinit var caller: Job
        var returned: InvestmentCommandOutcome? = null
        var cancellation: CancellationException? = null
        ledger.whenAWriteIsStamped = { caller.cancel() }

        caller = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                returned = submit(command)
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        ledger.whenAWriteIsStamped = {}

        assertNull(returned)
        assertTrue(cancellation != null)
        // The transaction ran to its end: 1.000.000 + 750.000.
        assertEquals(Money.cop(1_750_000), figures().contributed)
        val found = ledger.investments.find(command.operationId).valueOrFail()!!
        val replay = committed(submit(command))
        assertEquals(found.receipt, replay.receipt)
        assertEquals(
            replay.movementIds.map { it.value },
            movements().drop(2).map { it.split("|")[1] }
        )
        assertEquals(Money.cop(1_750_000), figures().contributed)
    }

    @Test
    fun aReceiptThatWouldPassTheLastRecordingPositionIsRefusedWithNothingWritten() = runBlocking {
        fondo()
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE investment SET next_sequence = ${Long.MAX_VALUE - 1}"
        )
        val before = state()

        // Positions MAX − 1 and MAX are free, but none follows MAX for the next movement.
        val outcome = submit(
            batch(
                line(Contribution, pesos(1), "2026-10-02"),
                line(Contribution, pesos(2), "2026-10-03")
            )
        )

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Integrity("investment", uuid(100), "next_sequence")
                )
            ),
            outcome
        )
        assertEquals(before, state())
    }

    @Test
    fun aDamagedHistoryFailsTheReadAndNothingIsWritten() = runBlocking {
        fondo()
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE investment_movement SET kind = 'gift' WHERE id = '${uuid(102)}'"
        )
        val before = state()

        val outcome = submit(batch(line(Contribution, pesos(1), "2026-10-02"), version = 1))

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Integrity("investment_movement", uuid(102), "kind")
                )
            ),
            outcome
        )
        assertEquals(before, state())
    }
}
