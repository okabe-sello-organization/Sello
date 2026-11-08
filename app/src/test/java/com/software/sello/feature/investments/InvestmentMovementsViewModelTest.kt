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
import com.software.sello.testing.HandGuidance
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
    private val guidance = HandGuidance()
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
    ) = InvestmentMovementsViewModel(
        saved,
        reads,
        commands,
        clock,
        ids,
        testMoney(),
        corrections,
        guidance
    )

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

    /** Aportado while the receipt is in one currency. */
    private val InvestmentMovementsState.aportado
        get() = (contributed as ContributedTotal.One).amount

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

        assertEquals("Ecopetrol", model.now.lines[0].investmentName)
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
        assertEquals("500.000", model.now.aportado.digits)

        model.act(InvestmentMovementsAction.Submit)

        val command = sent.single()
        assertEquals(mapOf(InvestmentId.of(ecopetrol).value() to 6L), command.expectedVersions)
        assertEquals(
            listOf(InvestmentId.of(ecopetrol).value()),
            command.lines.map { it.investmentId }.distinct()
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

    // ---------- The investments nudge closes once the receipt is saved (SELLO-213) ----------

    @Test
    fun aSavedReceiptClosesTheInvestmentsNudgeForGood() = runTest {
        val model = model()
        model.threeLines()

        model.act(InvestmentMovementsAction.Submit)

        assertTrue(model.now.closed)
        assertEquals(listOf("seen Investments"), guidance.writes)
    }

    @Test
    fun aRefusedReceiptLeavesItOpen() = runTest {
        val model = model()
        model.threeLines()
        hand.answers += CommandOutcome.Rejected(CommandRejection.StaleGeneration(4))

        model.act(InvestmentMovementsAction.Submit)

        assertFalse(model.now.closed)
        assertEquals(emptyList<String>(), guidance.writes)
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
        assertEquals("460.000", model.now.aportado.digits)
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
        assertEquals("USD" to "550,50", model.now.aportado.let { it.symbol to it.digits })

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
        assertEquals("30.000", model.now.aportado.digits)

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

        assertEquals(
            listOf(6L, 7L),
            sent.map { it.expectedVersions.getValue(InvestmentId.of(ecopetrol).value()) }
        )
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

        // An investment deleted meanwhile: its typed line asks for another, never saved.
        val gone = model(fondo)
        gone.act(InvestmentMovementsAction.Kind(Contribution))
        gone.keys("1", "000")
        reads.board.value = board(3, ecopetrolAcciones)
        assertTrue(gone.now.current.lost)
        assertEquals(1, gone.now.refused)
        assertFalse(gone.now.canSubmit)
        assertEquals("1.000", gone.now.current.amount?.digits)
        gone.act(InvestmentMovementsAction.InvestmentPicked(ecopetrol))
        assertTrue(gone.now.canSubmit)
    }

    @Test
    fun aFailedReadKeepsTheLastGoodInvestmentAndOnlyFailsWhenNothingWasRead() = runTest {
        val model = model()
        val good = reads.board.value as ReadState.Ready
        reads.board.value = ReadState.Failed(StorageFailure.Unavailable("hand"), good.value)
        assertFalse(model.now.loadFailed)
        assertTrue(model.now.stale)
        assertEquals("Ecopetrol", model.now.lines[0].investmentName)
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
        // The receipt stays editable, so a line can choose another investment.
        assertTrue(model.now.editable)
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
        assertEquals("29.999.999.999.970", model.now.aportado.digits)
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
            corrections,
            guidance
        )
        assertTrue(model.now.loadFailed)

        model.act(InvestmentMovementsAction.Retry)

        assertEquals(2, once.reads)
        assertFalse(model.now.loadFailed)
        assertEquals("Ecopetrol", model.now.lines[0].investmentName)
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

    @Test
    fun theDiscardQuestionCountsOnlyLinesWithAnAmountOrANote() = runTest {
        val model = model()
        model.act(InvestmentMovementsAction.Kind(Contribution))
        model.keys("5", "0", "0", "000")
        // Otro renglón copies the type: nothing typed on it, so it is not counted.
        model.act(InvestmentMovementsAction.AddLine, InvestmentMovementsAction.Back)
        assertTrue(model.now.confirmDiscard)
        assertEquals(2, model.now.lines.size)
        assertEquals(1, model.now.discarding)

        // A note alone is something typed.
        model.act(
            InvestmentMovementsAction.KeepEditing,
            InvestmentMovementsAction.OpenComposer,
            InvestmentMovementsAction.ComposerChanged("Aporte de octubre"),
            InvestmentMovementsAction.ComposerDone,
            InvestmentMovementsAction.Back
        )
        assertEquals(2, model.now.discarding)
    }

    @Test
    fun onlyATypeChosenStillAsksAndCountsThatLine() = runTest {
        val model = model()
        model.act(InvestmentMovementsAction.Kind(Dividend), InvestmentMovementsAction.Back)

        assertTrue(model.now.confirmDiscard)
        assertEquals(1, model.now.discarding)
    }

    // Across investments (SELLO-142)

    private fun fromList() = model(saved = SavedStateHandle())

    private val aportes get() = hand.sent.filterIsInstance<RecordInvestmentMovements>()

    private fun id(text: String) = InvestmentId.of(text).value()

    /** Ecopetrol: an aporte of 1.000.000; Acciones: an aporte of USD 250,50, a retiro of 50. */
    private fun InvestmentMovementsViewModel.mixed() {
        act(
            InvestmentMovementsAction.InvestmentPicked(ecopetrol),
            InvestmentMovementsAction.Kind(Contribution)
        )
        keys("1", "000", "000")
        act(
            InvestmentMovementsAction.AddLine,
            InvestmentMovementsAction.InvestmentPicked(acciones)
        )
        keys("2", "5", "0", ",", "5")
        act(InvestmentMovementsAction.AddLine, InvestmentMovementsAction.Kind(Withdrawal))
        keys("5", "0")
    }

    @Test
    fun fromTheListNoInvestmentIsChosenAndALineWithoutOneBlocks() = runTest {
        val model = fromList()

        assertNull(model.now.current.investmentId)
        assertEquals(
            listOf(ecopetrol, acciones, fondo).sorted(),
            model.now.investments.map { it.id }.sorted()
        )
        model.act(InvestmentMovementsAction.Kind(Contribution))
        model.keys("5", "000")
        assertTrue(model.now.current.missingInvestment)
        assertEquals(1, model.now.missingInvestments)
        assertFalse(model.now.canSubmit)
        // With no investment the 5.000 has no currency: Aportado leaves it out.
        assertEquals("0", model.now.aportado.digits)

        model.act(InvestmentMovementsAction.InvestmentPicked(fondo))
        assertEquals("Fondo", model.now.current.investmentName)
        assertEquals("5.000", model.now.aportado.digits)
        assertEquals(0, model.now.missingInvestments)
        assertTrue(model.now.canSubmit)

        // Nothing typed: leaving just closes, with nothing to ask about.
        val untouched = fromList()
        untouched.act(InvestmentMovementsAction.Back)
        assertTrue(untouched.now.closed)
        // An investment that is not on the board is never taken.
        val other = fromList()
        other.act(
            InvestmentMovementsAction.InvestmentPicked("00000000-0000-4000-9000-000000000099")
        )
        assertNull(other.now.current.investmentId)
    }

    @Test
    fun linesOfSeveralInvestmentsAreOneCommandWithEachVersion() = runTest {
        val model = fromList()
        model.act(
            InvestmentMovementsAction.InvestmentPicked(ecopetrol),
            InvestmentMovementsAction.Kind(Contribution)
        )
        model.keys("5", "0", "0", "000")
        model.act(InvestmentMovementsAction.DatePicked(day("2026-10-03")))
        model.act(InvestmentMovementsAction.AddLine)

        // A new line copies the investment, type and date above it.
        val copied = model.now.current
        assertEquals(
            listOf(ecopetrol, Contribution, day("2026-10-03")),
            listOf(copied.investmentId, copied.kind, copied.date)
        )
        model.act(
            InvestmentMovementsAction.InvestmentPicked(fondo),
            InvestmentMovementsAction.Kind(Withdrawal)
        )
        model.keys("2", "0", "0", "000")
        // 500.000 − 200.000 = 300.000, both in pesos.
        assertEquals("300.000", model.now.aportado.digits)

        model.act(InvestmentMovementsAction.Submit)

        val command = aportes.single()
        assertEquals(mapOf(id(ecopetrol) to 6L, id(fondo) to 1L), command.expectedVersions)
        assertEquals(
            listOf(
                Triple(id(ecopetrol), Contribution, Money.cop(500_000)),
                Triple(id(fondo), Withdrawal, Money.cop(200_000))
            ),
            command.lines.map { Triple(it.investmentId, it.kind, it.amount.money) }
        )
        assertTrue(model.now.closed)
        // Two investments: the confirmation names none of them.
        val notice = corrections.notice.value as CorrectionNotice.InvestmentMovementsRecorded
        assertEquals(2 to null, notice.count to notice.investmentName)
    }

    @Test
    fun anotherCurrencyClearsTheAmountAndTheSameOneKeepsIt() = runTest {
        val model = model()
        model.act(InvestmentMovementsAction.Kind(Contribution))
        model.keys("4", "000")
        assertFalse(model.now.usd)

        // Ecopetrol to Fondo: both in pesos, the 4.000 stays.
        model.act(InvestmentMovementsAction.InvestmentPicked(fondo))
        assertEquals("4.000", model.now.current.amount?.digits)

        // To dollars: the pesos meant other money, so they go; the comma key now types cents.
        model.act(InvestmentMovementsAction.InvestmentPicked(acciones))
        assertNull(model.now.current.amount)
        assertTrue(model.now.usd)
        assertTrue(model.now.current.usd)
        model.keys("1", "2", ",", "5")
        assertEquals("USD" to "12,5", model.now.current.amount?.let { it.symbol to it.digits })

        // Back to pesos clears the dollars too.
        model.act(InvestmentMovementsAction.InvestmentPicked(ecopetrol))
        assertNull(model.now.current.amount)
        assertFalse(model.now.usd)
        model.keys("7", "000")
        assertEquals("7.000", model.now.current.amount?.digits)
    }

    @Test
    fun aMixedReceiptIsInPesosAtTodaysRateAndSendsEachLineInItsOwnCurrency() = runTest {
        reads.board.value = ReadState.Ready(
            investmentsBoard(
                listOf(ecopetrolAcciones, accionesUsd, fondoConRetiro),
                rateEntry(390_000, "2026-10-01", sequence = 1),
                rateEntry(400_000, "2026-10-20", sequence = 2),
                generation = 3
            )
        )
        val model = fromList()
        model.mixed()

        // USD 250,50 − 50,00 = 200,50 × 4.000,00 (the rate of 20 oct, the latest) = 802.000;
        // + 1.000.000 = 1.802.000.
        val total = model.now.contributed as ContributedTotal.InPesos
        assertEquals("1.802.000", total.amount.digits)
        assertEquals("4.000,00", total.rate)
        assertEquals(day("2026-10-20"), total.rateDate)

        model.act(InvestmentMovementsAction.Submit)

        val command = aportes.single()
        assertEquals(mapOf(id(ecopetrol) to 6L, id(acciones) to 2L), command.expectedVersions)
        assertEquals(
            listOf(
                id(ecopetrol) to Money.cop(1_000_000),
                id(acciones) to Money(25_050, Currency.USD),
                id(acciones) to Money(5_000, Currency.USD)
            ),
            command.lines.map { it.investmentId to it.amount.money }
        )
    }

    @Test
    fun withNoRateAMixedReceiptShowsEachCurrencysPart() = runTest {
        val model = fromList()
        model.mixed()

        // No rate on the board: 1.000.000 and USD 200,50, never a made-up total.
        val parts = model.now.contributed as ContributedTotal.Parts
        assertEquals("1.000.000", parts.pesos.digits)
        assertEquals("USD" to "200,50", parts.dollars.symbol to parts.dollars.digits)
        assertTrue(model.now.canSubmit)
    }

    @Test
    fun aPesoValueBesideADollarAporteIsAReceiptInTwoCurrencies() = runTest {
        reads.board.value = ReadState.Ready(
            investmentsBoard(
                listOf(ecopetrolAcciones, accionesUsd, fondoConRetiro),
                rateEntry(400_000, "2026-10-20"),
                generation = 3
            )
        )
        val model = fromList()
        model.act(
            InvestmentMovementsAction.InvestmentPicked(ecopetrol),
            InvestmentMovementsAction.Kind(Valuation)
        )
        model.keys("3", "6", "0", "0", "000")
        model.act(
            InvestmentMovementsAction.AddLine,
            InvestmentMovementsAction.InvestmentPicked(acciones),
            InvestmentMovementsAction.Kind(Contribution)
        )
        model.keys("1", "0", "0")

        // Every typed line counts for the currency: the peso value makes it a mixed receipt.
        // Only the aporte adds: USD 100,00 × 4.000,00 = 400.000 pesos.
        val total = model.now.contributed as ContributedTotal.InPesos
        assertEquals("400.000", total.amount.digits)

        // With no rate, the parts: 0 pesos and USD 100,00.
        reads.board.value = board(3, ecopetrolAcciones, accionesUsd, fondoConRetiro)
        val parts = model.now.contributed as ContributedTotal.Parts
        assertEquals("0" to "100,00", parts.pesos.digits to parts.dollars.digits)
    }

    @Test
    fun aRefusedLineOfTheSecondInvestmentIsMarkedWithItsReason() = runTest {
        val model = fromList()
        model.act(
            InvestmentMovementsAction.InvestmentPicked(ecopetrol),
            InvestmentMovementsAction.Kind(Contribution)
        )
        model.keys("5", "0", "0", "000")
        model.act(
            InvestmentMovementsAction.AddLine,
            InvestmentMovementsAction.InvestmentPicked(acciones),
            InvestmentMovementsAction.Kind(Withdrawal)
        )
        model.keys("2", "0", "0", "0")
        val own = InvestmentMovementId.of("00000000-0000-4000-8000-0000000000fe").value()
        hand.answers += CommandOutcome.Rejected(
            CommandRejection.Specific(
                InvestmentRejection.LinesRefused(
                    listOf(
                        RefusedInvestmentLine(
                            1,
                            InvestmentRejection.AboveValue(own, Money(105_050, Currency.USD))
                        )
                    )
                )
            )
        )

        model.act(InvestmentMovementsAction.Submit)

        val refused = model.now.lines[1]
        // Named in the investment's own currency: USD 1.050,50 is what Acciones is worth.
        val available = (refused.refusal as MovementLineRefusal.OverValue).available
        assertEquals("USD" to "1.050,50", available.symbol to available.digits)
        assertNull(model.now.lines[0].refusal)
        assertEquals(1, model.now.highlighted)
        assertEquals(InvestmentMovementsError.NotSaved, model.now.error)
        assertFalse(model.now.canSubmit)
    }

    @Test
    fun aChangedInvestmentOfSeveralIsSentAgainOnlyWhenAskedAtTheVersionsReadAgain() = runTest {
        val model = fromList()
        model.mixed()
        hand.answers += CommandOutcome.Rejected(CommandRejection.VersionConflict(3))

        model.act(InvestmentMovementsAction.Submit)

        assertEquals(InvestmentMovementsError.Changed, model.now.error)
        assertEquals(1, aportes.size)
        reads.board.value = board(
            4,
            ecopetrolAcciones,
            handInvestment(
                acciones,
                "Acciones EE. UU.",
                null,
                "trending_up",
                listOf(Triple(Contribution, 100_000L, "2026-08-01")),
                currency = Currency.USD,
                version = 3
            )
        )
        model.act(InvestmentMovementsAction.Submit)

        assertEquals(
            listOf(
                mapOf(id(ecopetrol) to 6L, id(acciones) to 2L),
                mapOf(id(ecopetrol) to 6L, id(acciones) to 3L)
            ),
            aportes.map { it.expectedVersions }
        )
        assertEquals(listOf(3L, 4L), aportes.map { it.generation })
        assertTrue(model.now.closed)
    }

    @Test
    fun aRestoredReceiptKeepsEachLinesInvestment() = runTest {
        val saved = SavedStateHandle()
        val first = model(saved = saved)
        first.mixed()
        first.act(InvestmentMovementsAction.Select(0))

        val restored = model(saved = saved)

        assertEquals(first.now.lines, restored.now.lines)
        assertEquals(
            listOf(ecopetrol, acciones, acciones),
            restored.now.lines.map { it.investmentId }
        )
        assertEquals(listOf(false, true, true), restored.now.lines.map { it.usd })
        assertEquals(0, restored.now.highlighted)
        assertTrue(hand.sent.isEmpty())
    }

    @Test
    fun aDeletedInvestmentKeepsItsLineInItsCurrencyUntilAnotherIsChosen() = runTest {
        val model = fromList()
        model.mixed()
        reads.board.value = board(3, ecopetrolAcciones, fondoConRetiro)

        // Acciones is gone: its two lines still show their dollars, and ask for another.
        val lost = model.now.lines.filter { it.lost }
        // The highlighted one as the pad writes it.
        assertEquals(listOf("250,50", "50"), lost.map { it.amount?.digits })
        assertTrue(lost.all { it.usd && it.investmentName == null })
        assertEquals(2, model.now.refused)
        assertFalse(model.now.canSubmit)
    }

    @Test
    fun aRestoredLineWhoseInvestmentLeftTheBoardKeepsItsDollarsAndAsksForAnother() = runTest {
        val saved = SavedStateHandle()
        val first = model(saved = saved)
        first.mixed()
        reads.board.value = board(3, ecopetrolAcciones, fondoConRetiro)

        val restored = model(saved = saved)

        assertEquals(listOf(false, true, true), restored.now.lines.map { it.lost })
        assertEquals(listOf(false, true, true), restored.now.lines.map { it.usd })
        assertEquals(
            "USD" to "250,50",
            restored.now.lines[1].amount?.let {
                it.symbol to it.digits
            }
        )
        assertFalse(restored.now.canSubmit)
    }

    @Test
    fun aReceiptSavedBeforeSello142RestoresIntoTheInvestmentItWasOpenedFrom() = runTest {
        // As SELLO-134 kept it: an aporte of USD 250,5 on 12 oct, no investment per line.
        val before = arrayListOf<String?>(
            "1",
            "0", "+", "contribution", null, "250", "true", "5", "2026-10-12", "",
            "0",
            "1"
        )
        val saved = SavedStateHandle(
            mapOf(
                InvestmentMovementsViewModel.KEY_INVESTMENT to acciones,
                "investment.movements.draft" to before
            )
        )

        val model = model(acciones, saved)

        val line = model.now.current
        assertEquals(acciones to Contribution, line.investmentId to line.kind)
        assertEquals("USD" to "250,5", line.amount?.let { it.symbol to it.digits })
        assertTrue(line.usd)
        assertEquals(day("2026-10-12"), line.date)
        model.act(InvestmentMovementsAction.Submit)
        assertEquals(
            listOf(Money(25_050, Currency.USD)),
            sent.single().lines.map {
                it.amount.money
            }
        )
    }

    @Test
    fun aGoneInvestmentOfSeveralMarksOnlyItsLinesAndAChangeAnswersIt() = runTest {
        val model = fromList()
        model.mixed()
        hand.answers += CommandOutcome.Rejected(
            CommandRejection.Specific(InvestmentRejection.InvestmentMissing)
        )

        model.act(InvestmentMovementsAction.Submit)

        assertEquals(InvestmentMovementsError.Gone, model.now.error)
        assertTrue(model.now.editable)
        reads.board.value = board(3, ecopetrolAcciones, fondoConRetiro)
        // Only Acciones' two lines are marked; Ecopetrol's stays as it was.
        assertEquals(listOf(false, true, true), model.now.lines.map { it.lost })
        assertFalse(model.now.canSubmit)

        // Moving them to Fondo (pesos) clears their dollars and answers the message.
        model.act(
            InvestmentMovementsAction.Select(1),
            InvestmentMovementsAction.InvestmentPicked(fondo)
        )
        assertNull(model.now.error)
        assertNull(model.now.current.amount)
        model.act(
            InvestmentMovementsAction.Select(2),
            InvestmentMovementsAction.InvestmentPicked(fondo)
        )
        assertEquals(0, model.now.refused)
        assertTrue(model.now.canSubmit)
        assertEquals(1, sent.size)
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

    // From Anotar movimiento

    private fun fromSheet(investment: String, vararg hints: Pair<String, Any?>) =
        SavedStateHandle(mapOf(InvestmentMovementsViewModel.KEY_INVESTMENT to investment, *hints))

    @Test
    fun whatAnotarMovimientoHadTypedIsLineOneAndLineTwoWaitsWithItsTypeAndDate() = runTest {
        val model = model(
            saved = fromSheet(
                ecopetrol,
                "kind" to "dividend",
                "amount" to "89000",
                "note" to "Dividendo de junio",
                "date" to "2026-09-29"
            )
        )

        assertEquals(2, model.now.lines.size)
        assertEquals(1, model.now.highlighted)
        val first = model.now.lines[0]
        assertEquals(
            listOf(Dividend, "89.000", "Dividendo de junio", day("2026-09-29")),
            listOf(first.kind, first.amount?.digits, first.note, first.date)
        )
        assertTrue(first.valid)
        val second = model.now.current
        assertEquals(
            listOf(Dividend, null, "", day("2026-09-29")),
            listOf(second.kind, second.amount, second.note, second.date)
        )
        assertTrue(hand.sent.isEmpty())

        model.act(InvestmentMovementsAction.Submit)
        assertEquals(listOf(Money.cop(89_000)), sent.single().lines.map { it.amount.money })
    }

    @Test
    fun aUsdAmountKeepsItsCents() = runTest {
        val model = model(
            acciones,
            fromSheet(acciones, "kind" to "contribution", "amount" to "25050", "usd" to true)
        )

        assertEquals("USD" to "250,50", model.now.lines[0].amount?.let { it.symbol to it.digits })
        // 250,50 aportado.
        assertEquals("250,50", model.now.aportado.digits)
        model.act(InvestmentMovementsAction.Submit)
        assertEquals(
            listOf(Money(25_050, Currency.USD)),
            sent.single().lines.map { it.amount.money }
        )
    }

    @Test
    fun aValueOfZeroIsKeptButAnAporteOfZeroIsDropped() = runTest {
        val value = model(saved = fromSheet(ecopetrol, "kind" to "valuation", "amount" to "0"))
        assertEquals(2, value.now.lines.size)
        assertEquals("0", value.now.lines[0].amount?.digits)
        assertTrue(value.now.lines[0].valid)

        val aporte = model(saved = fromSheet(ecopetrol, "kind" to "contribution", "amount" to "0"))
        assertEquals(1, aporte.now.lines.size)
        assertEquals(Contribution to null, aporte.now.current.kind to aporte.now.current.amount)
    }

    @Test
    fun hintsThatAreNotValidAreDroppedNotShownAsErrors() = runTest {
        val model = model(
            saved = fromSheet(
                ecopetrol,
                "kind" to "bono",
                "amount" to "-5000",
                "note" to "x".repeat(200),
                "date" to "2026-10-23"
            )
        )

        assertEquals(1, model.now.lines.size)
        val line = model.now.current
        assertNull(line.kind)
        assertNull(line.amount)
        assertEquals("", line.note)
        // Tomorrow is not a date a movement can have: today instead.
        assertEquals(day("2026-10-22"), line.date)

        val garbled = model(saved = fromSheet(ecopetrol, "amount" to "12a", "date" to "ayer"))
        assertNull(garbled.now.current.amount)
        assertEquals(day("2026-10-22"), garbled.now.current.date)

        // USD 9.999.999.999,99 is the most; a cent more, or 20 digits, is dropped.
        val most = model(
            acciones,
            fromSheet(acciones, "kind" to "contribution", "amount" to "999999999999", "usd" to true)
        )
        assertEquals("9.999.999.999,99", most.now.lines[0].amount?.digits)
        for (tooLarge in listOf("1000000000000", "99999999999999999999")) {
            val dropped = model(
                acciones,
                fromSheet(acciones, "kind" to "contribution", "amount" to tooLarge, "usd" to true)
            )
            assertEquals(1, dropped.now.lines.size)
            assertNull(dropped.now.current.amount)
        }
    }

    @Test
    fun aRestoredReceiptKeepsWhatWasTypedOnItAndNeverTakesTheHintsAgain() = runTest {
        val saved = fromSheet(ecopetrol, "kind" to "contribution", "amount" to "500000")
        val first = model(saved = saved)
        first.keys("1", "000")
        first.act(InvestmentMovementsAction.Select(0), InvestmentMovementsAction.ClearAmount)

        val restored = model(saved = saved)

        assertEquals(listOf(null, "1.000"), restored.now.lines.map { it.amount?.digits })
        assertEquals(0, restored.now.highlighted)
        assertTrue(hand.sent.isEmpty())
    }
}
