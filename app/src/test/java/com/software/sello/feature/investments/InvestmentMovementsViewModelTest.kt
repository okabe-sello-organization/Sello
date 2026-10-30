package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.InvestmentMovementKind.Dividend
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.model.InvestmentMovementKind.Withdrawal
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RecordInvestmentMovements
import com.software.sello.domain.port.RefusedInvestmentLine
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.testing.testMoney
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Varios movimientos (SELLO-134) through the real investment amounts. Synthetic histories;
 * every expected figure is worked by hand. Today is Thursday 22 October 2026.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentMovementsViewModelTest {
    private val clock = HandFinancialClock("2026-10-22")
    private val reads = HandInvestmentReads()
    private val hand = HandInvestmentCommands()
    private val commands = GatedCommands(hand)
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }
    private val scheduler = TestCoroutineScheduler()
    private lateinit var corrections: Corrections

    private val ecopetrol = "00000000-0000-4000-9000-000000000002"
    private val acciones = "00000000-0000-4000-9000-000000000005"
    private val fondo = "00000000-0000-4000-9000-000000000001"

    /** 3.000.000 put in on 2 mar, two values and a dividend; worth 3.050.000 on 9 oct. */
    private val ecopetrolAcciones = handInvestment(
        ecopetrol,
        "Ecopetrol",
        null,
        "trending_up",
        listOf(
            Triple(Contribution, 3_000_000L, "2026-03-02"),
            Triple(Valuation, 3_000_000L, "2026-03-02"),
            Triple(Dividend, 89_000L, "2026-06-15"),
            Triple(Valuation, 3_050_000L, "2026-10-09")
        ),
        version = 6
    )

    /** USD 1.000,00 put in, worth USD 1.050,50. */
    private val accionesUsd = handInvestment(
        acciones,
        "Acciones EE. UU.",
        null,
        "trending_up",
        listOf(
            Triple(Contribution, 100_000L, "2026-08-01"),
            Triple(Valuation, 105_050L, "2026-10-16")
        ),
        currency = Currency.USD,
        version = 2
    )

    /** 4.000.000 on 1 sep, worth 5.000.000 on 1 oct, a retiro of 2.000.000 on 12 oct. */
    private val fondoConRetiro = handInvestment(
        fondo,
        "Fondo",
        null,
        "insights",
        listOf(
            Triple(Contribution, 4_000_000L, "2026-09-01"),
            Triple(Valuation, 4_000_000L, "2026-09-01"),
            Triple(Valuation, 5_000_000L, "2026-10-01"),
            Triple(Withdrawal, 2_000_000L, "2026-10-12")
        )
    )

    private fun board(generation: Long = 3, vararg investments: InvestmentSummary) =
        ReadState.Ready(investmentsBoard(investments.toList(), generation = generation))

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        corrections = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            HandGoalCommands(),
            hand,
            { scheduler.currentTime.milliseconds },
            ids,
            CoroutineScope(Dispatchers.Main),
            HandCategoryReads(),
            testMoney()
        )
        reads.board.value = board(3, ecopetrolAcciones, accionesUsd, fondoConRetiro)
    }

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model(
        investment: String = ecopetrol,
        saved: SavedStateHandle = SavedStateHandle(
            mapOf(InvestmentMovementsViewModel.KEY_INVESTMENT to investment)
        )
    ) = InvestmentMovementsViewModel(saved, reads, commands, clock, ids, testMoney(), corrections)

    private fun InvestmentMovementsViewModel.keys(vararg keys: String) = keys.forEach {
        onAction(
            when (it) {
                "000" -> InvestmentMovementsAction.TripleZero
                "," -> InvestmentMovementsAction.Comma
                "<" -> InvestmentMovementsAction.Backspace
                else -> InvestmentMovementsAction.Digit(it.toInt())
            }
        )
    }

    private fun InvestmentMovementsViewModel.act(vararg actions: InvestmentMovementsAction) =
        actions.forEach(::onAction)

    private val InvestmentMovementsViewModel.now get() = state.value

    private fun day(text: String) = LocalDate.parse(text)

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    /** The mock's receipt: an aporte of 500.000 on 3 oct, a dividend of 89.000, today's value. */
    private fun InvestmentMovementsViewModel.threeLines() {
        act(InvestmentMovementsAction.Kind(Contribution))
        keys("5", "0", "0", "000")
        act(
            InvestmentMovementsAction.DatePicked(day("2026-10-03")),
            InvestmentMovementsAction.AddLine
        )
        act(InvestmentMovementsAction.Kind(Dividend))
        keys("8", "9", "000")
        act(
            InvestmentMovementsAction.DatePicked(day("2026-09-29")),
            InvestmentMovementsAction.AddLine
        )
        act(
            InvestmentMovementsAction.Kind(Valuation),
            InvestmentMovementsAction.DatePicked(day("2026-10-22"))
        )
        keys("3", "6", "0", "0", "000")
    }

    private val sent get() = hand.sent.filterIsInstance<RecordInvestmentMovements>()

    @Test
    fun itOpensWithOneEmptyLineOfNoTypeDatedTodayAndTheButtonWaits() = runTest {
        val model = model()

        assertEquals("Ecopetrol", model.now.name)
        assertEquals(1, model.now.lines.size)
        val line = model.now.current
        assertNull(line.kind)
        assertNull(line.amount)
        assertEquals(day("2026-10-22"), line.date)
        assertFalse(model.now.canSubmit)
        assertFalse(model.now.canAdd)

        // An amount without a type blocks the receipt and says so.
        model.keys("4", "000")
        assertTrue(model.now.current.missingKind)
        assertEquals(1, model.now.missingKinds)
        assertFalse(model.now.canSubmit)
        model.act(InvestmentMovementsAction.Kind(Contribution))
        assertTrue(model.now.canSubmit)
    }

    @Test
    fun everyTypeIsOneLineAndANewLineCopiesTheTypeAndDateAbove() = runTest {
        val model = model()
        model.act(InvestmentMovementsAction.Kind(Withdrawal))
        model.keys("1", "000")
        model.act(
            InvestmentMovementsAction.DatePicked(day("2026-10-12")),
            InvestmentMovementsAction.AddLine
        )

        val copied = model.now.current
        assertEquals(Withdrawal to day("2026-10-12"), copied.kind to copied.date)
        assertNull(copied.amount)
        assertEquals(1, model.now.highlighted)
        // Otro renglón waits until the new line has an amount.
        assertFalse(model.now.canAdd)

        model.act(InvestmentMovementsAction.Kind(Valuation))
        model.keys("0")
        // A recorded value may be 0 (rule 2); an aporte may not.
        assertTrue(model.now.current.valid)
        model.act(InvestmentMovementsAction.Kind(Contribution))
        assertTrue(model.now.current.invalidAmount)
        assertFalse(model.now.canSubmit)
        model.act(InvestmentMovementsAction.Kind(Dividend))
        assertTrue(model.now.current.invalidAmount)
    }

    @Test
    fun threeLinesAreOneCommandAndAportadoCountsOnlyAportesLessRetiros() = runTest {
        val model = model()
        model.threeLines()

        assertEquals(3, model.now.counted)
        // 500.000 aportado; the dividend of 89.000 and the value of 3.600.000 stay out.
        assertEquals("500.000", model.now.contributed?.digits)

        model.act(InvestmentMovementsAction.Submit)

        val command = sent.single()
        assertEquals(
            InvestmentId.of(ecopetrol).value() to 6L,
            command.investmentId to command.expectedVersion
        )
        assertEquals(3L, command.generation)
        assertEquals(
            listOf(
                Triple(Contribution, Money.cop(500_000), day("2026-10-03")),
                Triple(Dividend, Money.cop(89_000), day("2026-09-29")),
                Triple(Valuation, Money.cop(3_600_000), day("2026-10-22"))
            ),
            command.lines.map { Triple(it.kind, it.amount.money, it.date) }
        )
        assertTrue(model.now.closed)
        val notice = corrections.notice.value as CorrectionNotice.InvestmentMovementsRecorded
        assertEquals(3 to "Ecopetrol", notice.count to notice.investmentName)
    }

    @Test
    fun aRetiroTakesFromAportadoAndLinesWithoutAnAmountAreLeftOut() = runTest {
        val model = model()
        model.act(InvestmentMovementsAction.Kind(Contribution))
        model.keys("5", "0", "0", "000")
        model.act(InvestmentMovementsAction.AddLine, InvestmentMovementsAction.Kind(Withdrawal))
        model.keys("4", "0", "000")
        model.act(InvestmentMovementsAction.AddLine)

        // 500.000 − 40.000 = 460.000; the third line has no amount.
        assertEquals("460.000", model.now.contributed?.digits)
        assertEquals(3, model.now.lines.size)
        assertEquals(2, model.now.counted)
        model.act(InvestmentMovementsAction.Submit)

        assertEquals(
            listOf(Contribution to Money.cop(500_000), Withdrawal to Money.cop(40_000)),
            sent.single().lines.map { it.kind to it.amount.money }
        )
    }

    @Test
    fun aRetiroAboveTheValueIsMarkedOnItsLineAndTheDraftStays() = runTest {
        val model = model()
        model.threeLines()
        // An empty fourth line, then the retiro fifth: it is the fourth line sent, position 3.
        model.act(InvestmentMovementsAction.AddLine, InvestmentMovementsAction.Select(2))
        model.act(InvestmentMovementsAction.AddLine, InvestmentMovementsAction.Kind(Withdrawal))
        model.keys("4", "000", "000")
        val own = InvestmentMovementId.of("00000000-0000-4000-8000-0000000000ff").value()
        hand.answers += CommandOutcome.Rejected(
            CommandRejection.Specific(
                InvestmentRejection.LinesRefused(
                    listOf(
                        RefusedInvestmentLine(
                            3,
                            InvestmentRejection.AboveValue(own, Money.cop(3_600_000))
                        )
                    )
                )
            )
        )

        model.act(InvestmentMovementsAction.Submit)

        val refused = model.now.lines.single { it.refusal != null }
        assertEquals(model.now.lines[4], refused)
        assertEquals(Withdrawal to "4.000.000", refused.kind to refused.amount?.digits)
        assertEquals(
            "3.600.000",
            (refused.refusal as MovementLineRefusal.OverValue).available.digits
        )
        assertEquals(refused, model.now.current)
        assertEquals(InvestmentMovementsError.NotSaved, model.now.error)
        assertEquals(5, model.now.lines.size)
        assertFalse(model.now.canSubmit)
        assertFalse(model.now.closed)

        // Lowering the retiro answers the refusal: the receipt can be sent again.
        model.keys("<")
        assertNull(model.now.current.refusal)
        assertNull(model.now.error)
        assertTrue(model.now.canSubmit)
    }

    @Test
    fun aLineThatLeavesARecordedRetiroShortNamesThatRetiro() = runTest {
        val model = model(fondo)
        model.act(
            InvestmentMovementsAction.Kind(Valuation),
            InvestmentMovementsAction.DatePicked(day("2026-10-05"))
        )
        model.keys("1", "000", "000")
        // The recorded retiro of 2.000.000 on 12 oct is the fourth movement: id 503.
        val recorded = InvestmentMovementId.of("00000000-0000-4000-8000-000000000503").value()
        hand.answers += CommandOutcome.Rejected(
            CommandRejection.Specific(
                InvestmentRejection.LinesRefused(
                    listOf(
                        RefusedInvestmentLine(
                            0,
                            InvestmentRejection.AboveValue(recorded, Money.cop(1_000_000))
                        )
                    )
                )
            )
        )

        model.act(InvestmentMovementsAction.Submit)

        val short = model.now.current.refusal as MovementLineRefusal.LeavesRetiroShort
        assertEquals("2.000.000" to day("2026-10-12"), short.amount.digits to short.date)
    }

    @Test
    fun aUsdReceiptIsInDollarsWithCentsAndItsTotalToo() = runTest {
        val model = model(acciones)
        assertTrue(model.now.usd)
        model.act(InvestmentMovementsAction.Kind(Contribution))
        model.keys("2", "5", "0", ",", "5")
        // As the pad wrote it while highlighted.
        assertEquals("USD" to "250,5", model.now.current.amount?.let { it.symbol to it.digits })
        model.act(InvestmentMovementsAction.AddLine)
        assertEquals("250,50", model.now.lines[0].amount?.digits)
        model.keys("3", "0", "0")
        model.act(InvestmentMovementsAction.AddLine, InvestmentMovementsAction.Kind(Dividend))
        model.keys("1", "2", ",", "4")
        // 250,50 + 300,00 = 550,50 aportado; the dividend of 12,40 stays out.
        assertEquals("USD" to "550,50", model.now.contributed?.let { it.symbol to it.digits })

        // The 000 key is not a dollar key.
        model.act(InvestmentMovementsAction.TripleZero)
        assertEquals("12,4", model.now.current.amount?.digits)

        model.act(InvestmentMovementsAction.Submit)
        assertEquals(
            listOf(
                Money(25_050, Currency.USD),
                Money(30_000, Currency.USD),
                Money(1_240, Currency.USD)
            ),
            sent.single().lines.map { it.amount.money }
        )
    }

    @Test
    fun theReceiptHoldsOneToThirtyLinesWithAValidHighlight() = runTest {
        val model = model()
        model.act(InvestmentMovementsAction.Kind(Contribution))
        repeat(29) {
            model.keys("1", "000")
            model.act(InvestmentMovementsAction.AddLine)
        }
        model.keys("1", "000")
        assertEquals(30, model.now.lines.size)
        assertTrue(model.now.full)
        assertFalse(model.now.canAdd)
        model.act(InvestmentMovementsAction.AddLine)
        assertEquals(30, model.now.lines.size)
        assertEquals(29, model.now.highlighted)
        // 30 aportes of 1.000 = 30.000.
        assertEquals("30.000", model.now.contributed?.digits)

        // Removing the highlighted last line keeps a highlight on the receipt.
        model.act(InvestmentMovementsAction.Remove(29))
        assertEquals(29 to 28, model.now.lines.size to model.now.highlighted)
        // Removing a line above the highlight keeps the same line highlighted.
        val highlighted = model.now.current.key
        model.act(InvestmentMovementsAction.Remove(0))
        assertEquals(highlighted, model.now.current.key)
        assertEquals(27, model.now.highlighted)
        // A line outside the receipt is never selected or removed.
        model.act(InvestmentMovementsAction.Select(28), InvestmentMovementsAction.Remove(28))
        assertEquals(28 to 27, model.now.lines.size to model.now.highlighted)
    }

    @Test
    fun theLastLineIsNeverRemovedAndARemovedLineCanBePutBack() = runTest {
        val model = model()
        model.act(InvestmentMovementsAction.Remove(0))
        assertEquals(1, model.now.lines.size)
        assertNull(model.now.removed)

        model.threeLines()
        val dividend = model.now.lines[1]
        model.act(InvestmentMovementsAction.Remove(1))
        assertEquals(listOf(Contribution, Valuation), model.now.lines.map { it.kind })
        assertEquals(dividend.key, model.now.removed?.key)
        assertEquals(1, model.now.highlighted)

        model.act(InvestmentMovementsAction.UndoRemove)
        assertEquals(listOf(Contribution, Dividend, Valuation), model.now.lines.map { it.kind })
        assertEquals(1, model.now.highlighted)
        assertNull(model.now.removed)

        model.act(InvestmentMovementsAction.Remove(2), InvestmentMovementsAction.RemovalShown)
        model.act(InvestmentMovementsAction.UndoRemove)
        assertEquals(2, model.now.lines.size)
    }

    @Test
    fun aFutureDateIsNeverTaken() = runTest {
        val model = model()
        model.act(InvestmentMovementsAction.DatePicked(day("2026-10-23")))
        assertEquals(day("2026-10-22"), model.now.current.date)
    }

    @Test
    fun aDoubleSubmitSendsOnce() = runTest {
        val model = model()
        model.threeLines()
        val gate = CompletableDeferred<Unit>()
        commands.gate = gate

        model.act(InvestmentMovementsAction.Submit, InvestmentMovementsAction.Submit)
        assertTrue(model.now.busy)
        assertFalse(model.now.editable)
        // Locked while saving: nothing changes the receipt.
        model.keys("9")
        model.act(InvestmentMovementsAction.Back)
        assertFalse(model.now.confirmDiscard)

        gate.complete(Unit)
        assertEquals(1, sent.size)
        assertTrue(model.now.closed)
    }

    @Test
    fun anUnknownOutcomeIsAskedAboutWithTheSameOperationAndNeverSentAgain() = runTest {
        val model = model()
        model.threeLines()
        hand.answers += CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        hand.found = Outcome.Failure(StorageFailure.Unavailable("hand"))

        model.act(InvestmentMovementsAction.Submit)

        assertEquals(InvestmentMovementsError.Unknown, model.now.error)
        assertFalse(model.now.editable)
        model.act(InvestmentMovementsAction.Submit)
        model.act(InvestmentMovementsAction.Back)
        assertFalse(model.now.closed)
        assertEquals(1, sent.size)

        val operation = sent.single().operationId
        hand.found = Outcome.Success(
            committedInvestment(operation, OperationKind.RecordInvestmentMovements, ecopetrol)
                .value.copy(movementIds = null)
        )
        model.act(InvestmentMovementsAction.Retry)

        assertEquals(listOf(operation, operation), hand.asked)
        assertEquals(1, sent.size)
        assertTrue(model.now.closed)
        val notice = corrections.notice.value as CorrectionNotice.InvestmentMovementsRecorded
        assertEquals(3, notice.count)
    }

    @Test
    fun anUnknownOutcomeFoundNotSavedLeavesTheDraftToSendAsANewOperation() = runTest {
        val model = model()
        model.threeLines()
        hand.answers += CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        hand.found = Outcome.Success(null)

        model.act(InvestmentMovementsAction.Submit)

        assertEquals(InvestmentMovementsError.NotSaved, model.now.error)
        assertTrue(model.now.canSubmit)
        model.act(InvestmentMovementsAction.Submit)
        assertEquals(2, sent.size)
        assertTrue(sent[0].operationId != sent[1].operationId)
        assertTrue(model.now.closed)
    }

    @Test
    fun anInvestmentThatChangedAsksToSendAgainAtTheVersionReadAgain() = runTest {
        val model = model()
        model.threeLines()
        hand.answers += CommandOutcome.Rejected(CommandRejection.VersionConflict(7))

        model.act(InvestmentMovementsAction.Submit)

        assertEquals(InvestmentMovementsError.Changed, model.now.error)
        assertFalse(model.now.closed)
        assertEquals(3, model.now.counted)
        // Nothing is sent until asked; the board read again carries the new version.
        assertEquals(1, sent.size)
        reads.board.value = board(
            3,
            handInvestment(
                ecopetrol,
                "Ecopetrol",
                null,
                "trending_up",
                listOf(Triple(Contribution, 3_000_000L, "2026-03-02")),
                version = 7
            )
        )
        model.act(InvestmentMovementsAction.Submit)

        assertEquals(listOf(6L, 7L), sent.map { it.expectedVersion })
        assertTrue(model.now.closed)
    }

    @Test
    fun dataReplacedMeanwhileOrAGoneInvestmentHoldsTheReceipt() = runTest {
        val model = model()
        model.threeLines()
        hand.answers += CommandOutcome.Rejected(CommandRejection.StaleGeneration(4))

        model.act(InvestmentMovementsAction.Submit)

        assertEquals(InvestmentMovementsError.DataChanged, model.now.error)
        assertFalse(model.now.editable)
        assertFalse(model.now.canSubmit)

        val gone = model(fondo)
        reads.board.value = board(3, ecopetrolAcciones)
        assertEquals(InvestmentMovementsError.Gone, gone.now.error)
        assertFalse(gone.now.canSubmit)
    }

    @Test
    fun aFailedReadKeepsTheLastGoodInvestmentAndOnlyFailsWhenNothingWasRead() = runTest {
        val model = model()
        val good = reads.board.value as ReadState.Ready
        reads.board.value = ReadState.Failed(StorageFailure.Unavailable("hand"), good.value)
        assertFalse(model.now.loadFailed)
        assertTrue(model.now.stale)
        assertEquals("Ecopetrol", model.now.name)
        model.threeLines()
        assertTrue(model.now.canSubmit)
        reads.board.value = good
        assertFalse(model.now.stale)

        reads.board.value = ReadState.Failed(StorageFailure.Unavailable("hand"), null)
        val never = model()
        assertTrue(never.now.loadFailed)
        assertFalse(never.now.editable)
    }

    @Test
    fun theDraftAndTheHighlightSurviveProcessDeath() = runTest {
        val saved = SavedStateHandle(mapOf(InvestmentMovementsViewModel.KEY_INVESTMENT to acciones))
        val first = model(acciones, saved)
        first.act(InvestmentMovementsAction.Kind(Contribution))
        first.keys("2", "5", "0", ",", "5")
        first.act(
            InvestmentMovementsAction.DatePicked(day("2026-10-12")),
            InvestmentMovementsAction.AddLine
        )
        first.act(InvestmentMovementsAction.Kind(Dividend))
        first.keys("1", "2", ",", "4", "0")
        first.act(InvestmentMovementsAction.OpenComposer)
        first.act(InvestmentMovementsAction.ComposerChanged("Dividendo de octubre"))
        first.act(InvestmentMovementsAction.ComposerDone, InvestmentMovementsAction.Select(0))

        val restored = model(acciones, saved)

        assertEquals(first.now.lines, restored.now.lines)
        assertEquals(0, restored.now.highlighted)
        assertEquals("Dividendo de octubre", restored.now.lines[1].note)
        // The comma typed is kept: the next digit is a cent.
        restored.keys("1")
        assertEquals("250,51", restored.now.current.amount?.digits)
        assertTrue(hand.sent.isEmpty())
    }

    @Test
    fun aRestoredReceiptWithAnOperationInFlightOnlyAsksAboutIt() = runTest {
        val saved =
            SavedStateHandle(mapOf(InvestmentMovementsViewModel.KEY_INVESTMENT to ecopetrol))
        val first = model(saved = saved)
        first.threeLines()
        hand.answers += CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        hand.found = Outcome.Failure(StorageFailure.Unavailable("hand"))
        first.act(InvestmentMovementsAction.Submit)
        val operation = sent.single().operationId

        hand.found = Outcome.Success(
            committedInvestment(operation, OperationKind.RecordInvestmentMovements, ecopetrol)
                .value.copy(movementIds = null)
        )
        val restored = model(saved = saved)

        assertEquals(1, sent.size)
        assertEquals(operation, hand.asked.last())
        assertTrue(restored.now.closed)
    }

    @Test
    fun aRestoredReceiptStillUnknownStaysLockedUntilAnswered() = runTest {
        val saved =
            SavedStateHandle(mapOf(InvestmentMovementsViewModel.KEY_INVESTMENT to ecopetrol))
        val first = model(saved = saved)
        first.threeLines()
        hand.answers += CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        hand.found = Outcome.Failure(StorageFailure.Unavailable("hand"))
        first.act(InvestmentMovementsAction.Submit)

        val restored = model(saved = saved)

        assertEquals(InvestmentMovementsError.Unknown, restored.now.error)
        assertFalse(restored.now.editable)
        assertEquals(3, restored.now.counted)
        assertEquals(1, sent.size)
    }

    @Test
    fun aRestoredUsdReceiptFoundSavedBeforeTheBoardIsReadCountsItsLines() = runTest {
        val saved = SavedStateHandle(mapOf(InvestmentMovementsViewModel.KEY_INVESTMENT to acciones))
        val first = model(acciones, saved)
        first.act(InvestmentMovementsAction.Kind(Contribution))
        first.keys("2", "5", "0", ",", "5")
        first.act(InvestmentMovementsAction.AddLine, InvestmentMovementsAction.Kind(Dividend))
        first.keys("1", "2", ",", "4")
        hand.answers += CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        hand.found = Outcome.Failure(StorageFailure.Unavailable("hand"))
        first.act(InvestmentMovementsAction.Submit)
        val operation = sent.single().operationId

        // After process death the board is still being read when the answer comes.
        reads.board.value = ReadState.Loading
        hand.found = Outcome.Success(
            committedInvestment(operation, OperationKind.RecordInvestmentMovements, acciones)
                .value.copy(movementIds = null)
        )
        val restored = model(acciones, saved)

        assertTrue(restored.now.closed)
        assertEquals(1, sent.size)
        val notice = corrections.notice.value as CorrectionNotice.InvestmentMovementsRecorded
        assertEquals(2, notice.count)
    }

    @Test
    fun aSendCancelledMidwayLeavesTheOperationToAskAboutAfterTheRestart() = runTest {
        val saved =
            SavedStateHandle(mapOf(InvestmentMovementsViewModel.KEY_INVESTMENT to ecopetrol))
        val first = model(saved = saved)
        first.threeLines()
        val gate = CompletableDeferred<Unit>()
        commands.gate = gate
        first.act(InvestmentMovementsAction.Submit)
        gate.completeExceptionally(CancellationException("process ending"))

        // Cancelled is not an answer: the first screen stays busy, with no error.
        assertTrue(first.now.busy)
        assertNull(first.now.error)
        val operation = saved.get<String>("investment.movements.operation")
        assertNotNull(operation)

        hand.found = Outcome.Success(null)
        val restored = model(saved = saved)

        // It is only asked about, with its own operation, and never sent again by itself.
        assertTrue(sent.isEmpty())
        assertEquals(listOf(operation), hand.asked.map { it.value })
        assertEquals(InvestmentMovementsError.NotSaved, restored.now.error)
        assertEquals(3, restored.now.counted)
        assertTrue(restored.now.canSubmit)
    }

    @Test
    fun aGenericRefusalIsNotSavedAndAMissingInvestmentIsGone() = runTest {
        val model = model()
        model.threeLines()
        hand.answers += CommandOutcome.Rejected(
            CommandRejection.StorageFailed(StorageFailure.Unavailable("hand"))
        )

        model.act(InvestmentMovementsAction.Submit)

        assertEquals(InvestmentMovementsError.NotSaved, model.now.error)
        assertEquals(3, model.now.counted)
        assertTrue(model.now.canSubmit)

        hand.answers += CommandOutcome.Rejected(
            CommandRejection.Specific(InvestmentRejection.InvestmentMissing)
        )
        model.act(InvestmentMovementsAction.Submit)

        assertEquals(InvestmentMovementsError.Gone, model.now.error)
        assertFalse(model.now.editable)
        assertFalse(model.now.closed)
        assertEquals(2, sent.size)
    }

    @Test
    fun thirtyOfTheLargestAportesAreAportadoInFull() = runTest {
        val model = model()
        model.act(InvestmentMovementsAction.Kind(Contribution))
        repeat(30) { line ->
            if (line > 0) model.act(InvestmentMovementsAction.AddLine)
            repeat(12) { model.keys("9") }
            // A thirteenth digit is refused, never wrapped.
            model.keys("9")
        }

        assertEquals(30, model.now.counted)
        assertEquals("999.999.999.999", model.now.current.amount?.digits)
        // 30 × 999.999.999.999 = 29.999.999.999.970, written whole, not clipped.
        assertEquals("29.999.999.999.970", model.now.contributed?.digits)
    }

    @Test
    fun editingAfterTheInvestmentChangedClearsTheMessage() = runTest {
        val model = model()
        model.threeLines()
        hand.answers += CommandOutcome.Rejected(CommandRejection.VersionConflict(7))
        model.act(InvestmentMovementsAction.Submit)
        assertEquals(InvestmentMovementsError.Changed, model.now.error)

        model.keys("<")

        assertNull(model.now.error)
        assertTrue(model.now.canSubmit)
    }

    @Test
    fun reintentarAfterAFailedFirstReadReadsAgain() = runTest {
        val failed = ReadState.Failed(StorageFailure.Unavailable("hand"), null)
        val answers = ArrayDeque(listOf(failed, board(3, ecopetrolAcciones)))
        val once = object : InvestmentReads by reads {
            var reads = 0

            override fun observeBoard(): Flow<InvestmentBoardState> {
                reads++
                return flowOf(answers.removeFirst())
            }
        }
        val model = InvestmentMovementsViewModel(
            SavedStateHandle(mapOf(InvestmentMovementsViewModel.KEY_INVESTMENT to ecopetrol)),
            once,
            commands,
            clock,
            ids,
            testMoney(),
            corrections
        )
        assertTrue(model.now.loadFailed)

        model.act(InvestmentMovementsAction.Retry)

        assertEquals(2, once.reads)
        assertFalse(model.now.loadFailed)
        assertEquals("Ecopetrol", model.now.name)
    }

    @Test
    fun leavingWithLinesAsksFirstAndAnEmptyReceiptJustCloses() = runTest {
        val empty = model()
        empty.act(InvestmentMovementsAction.Back)
        assertTrue(empty.now.closed)

        val model = model()
        model.threeLines()
        model.act(InvestmentMovementsAction.Back)
        assertTrue(model.now.confirmDiscard)
        model.act(InvestmentMovementsAction.KeepEditing)
        assertFalse(model.now.confirmDiscard)
        assertFalse(model.now.closed)
        model.act(InvestmentMovementsAction.Back, InvestmentMovementsAction.DiscardConfirmed)
        assertTrue(model.now.closed)
        assertTrue(hand.sent.isEmpty())
    }

    /** [delegate], held at [gate] while it is not completed. */
    private class GatedCommands(private val delegate: HandInvestmentCommands) :
        InvestmentCommands by delegate {
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun submit(command: InvestmentCommand): InvestmentCommandOutcome {
            gate?.await()
            return delegate.submit(command)
        }
    }
}
