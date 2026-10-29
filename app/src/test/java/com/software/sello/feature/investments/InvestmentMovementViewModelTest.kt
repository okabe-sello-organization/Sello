package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.InvestmentMovementKind.Dividend
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.model.InvestmentMovementKind.Withdrawal
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.DeleteInvestmentMovement
import com.software.sello.domain.port.EditInvestmentMovement
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.InvestmentUndoOffer
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RecordInvestmentMovement
import com.software.sello.domain.port.RestoreInvestmentMovement
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Anotar movimiento through the real investment rules. Histories and expected figures
 * are the approved examples (docs/testing/investment-examples.md) by ID, copied by hand;
 * today is 25 October 2026 and the rate 4.150,25 of 20 oct.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentMovementViewModelTest {
    private val money = testMoney()
    private val clock = HandFinancialClock("2026-10-25")
    private val reads = HandInvestmentReads()
    private val commands = HandInvestmentCommands()
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }
    private val scheduler = TestCoroutineScheduler()
    private lateinit var corrections: Corrections

    private val fondo = "00000000-0000-4000-9000-000000000001"
    private val ecopetrol = "00000000-0000-4000-9000-000000000002"
    private val prueba = "00000000-0000-4000-9000-000000000003"
    private val cripto = "00000000-0000-4000-9000-000000000004"
    private val acciones = "00000000-0000-4000-9000-000000000005"

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun day(text: String) = LocalDate.parse(text)

    private fun movementId(number: Int) =
        InvestmentMovementId.of("00000000-0000-4000-8000-%012d".format(number)).value()

    private fun movement(
        number: Int,
        kind: InvestmentMovementKind,
        units: Long,
        date: String,
        currency: Currency = Currency.COP
    ) = InvestmentMovement(
        movementId(number),
        kind,
        Money(units, currency).let {
            if (kind == Valuation) InvestmentAmount.ofValue(it) else InvestmentAmount.of(it)
        }.value(),
        day(date),
        number.toLong(),
        null,
        2,
        Instant.EPOCH,
        Instant.EPOCH
    )

    private fun investment(
        id: String,
        name: String,
        icon: String,
        vararg movements: InvestmentMovement,
        currency: Currency = Currency.COP
    ): InvestmentSummary {
        val history = InvestmentHistory(
            Investment(
                InvestmentId.of(id).value(),
                InvestmentName.of(name).value(),
                null,
                IconKey.of(icon).value(),
                currency,
                6,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            movements.toList()
        )
        return InvestmentSummary(history, InvestmentPolicy.figures(history).value())
    }

    /** IN04: two aportes, a retiro and the 14 oct value. */
    private val fondoDeAcciones = investment(
        fondo,
        "Fondo de acciones",
        "insights",
        movement(11, Contribution, 3_000_000, "2025-05-03"),
        movement(12, Valuation, 3_000_000, "2025-05-03"),
        movement(13, Contribution, 3_000_000, "2025-11-03"),
        movement(14, Withdrawal, 500_000, "2026-08-03"),
        movement(15, Valuation, 5_242_000, "2026-10-14")
    )

    /** IN05: 3.000.000 put in, two dividends of 89.000, the 9 oct value. */
    private val ecopetrolAcciones = investment(
        ecopetrol,
        "Ecopetrol",
        "trending_up",
        movement(21, Contribution, 3_000_000, "2026-03-02"),
        movement(22, Valuation, 3_000_000, "2026-03-02"),
        movement(23, Dividend, 89_000, "2026-06-15"),
        movement(24, Dividend, 89_000, "2026-09-15"),
        movement(25, Valuation, 3_050_000, "2026-10-09")
    )

    /** IN08 and IN09: 4.000.000 on 1 sep, value 5.000.000 on 1 oct, 1.000.000 on 10 oct, retiro 2.000.000 on 12 oct. */
    private val in09 = arrayOf(
        movement(31, Contribution, 4_000_000, "2026-09-01"),
        movement(32, Valuation, 4_000_000, "2026-09-01"),
        movement(33, Valuation, 5_000_000, "2026-10-01"),
        movement(34, Contribution, 1_000_000, "2026-10-10"),
        movement(35, Withdrawal, 2_000_000, "2026-10-12")
    )

    /** IN17: Cripto, its only value deleted. */
    private val criptoSinValor =
        investment(
            cripto,
            "Cripto",
            "insights",
            movement(41, Contribution, 2_000_000, "2026-07-01")
        )

    /** IN27: USD 1.000,00 put in, worth USD 1.050,50. */
    private val accionesUsd = investment(
        acciones,
        "Acciones EE. UU.",
        "trending_up",
        movement(51, Contribution, 100_000, "2026-08-01", Currency.USD),
        movement(52, Valuation, 105_050, "2026-10-16", Currency.USD),
        currency = Currency.USD
    )

    private fun board(vararg investments: InvestmentSummary): InvestmentBoardState {
        val rate = rateEntry(415_025, "2026-10-20")
        return ReadState.Ready(
            InvestmentBoard(
                generation = 3,
                revision = 9,
                investments = investments.toList(),
                rates = ratesBoard(rate).rates,
                overview = InvestmentPolicy.overview(
                    investments.map { it.history to it.figures },
                    rate
                )
            )
        )
    }

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        corrections = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            HandGoalCommands(),
            commands,
            { scheduler.currentTime.milliseconds },
            ids,
            CoroutineScope(Dispatchers.Main)
        )
        reads.board.value = board(
            ecopetrolAcciones,
            criptoSinValor,
            investment(prueba, "Prueba", "home", *in09),
            fondoDeAcciones,
            accionesUsd
        )
    }

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model(
        investment: String? = null,
        kind: String? = null,
        movement: String? = null,
        saved: SavedStateHandle = SavedStateHandle(
            listOfNotNull(
                investment?.let { InvestmentMovementViewModel.KEY_INVESTMENT to it },
                kind?.let { InvestmentMovementViewModel.KEY_KIND to it },
                movement?.let { InvestmentMovementViewModel.KEY_MOVEMENT to it }
            ).toMap()
        )
    ) = InvestmentMovementViewModel(saved, reads, commands, clock, ids, money, corrections)

    private fun InvestmentMovementViewModel.keys(vararg keys: String) = keys.forEach {
        onAction(
            when (it) {
                "000" -> InvestmentMovementAction.TripleZero
                "," -> InvestmentMovementAction.Comma
                "<" -> InvestmentMovementAction.Backspace
                else -> InvestmentMovementAction.Digit(it.toInt())
            }
        )
    }

    private val InvestmentMovementViewModel.now get() = state.value

    @Test
    fun fromTheDockNothingIsChosenTheButtonWaitsAndInvestmentsAreLargestValueFirst() = runTest {
        val model = model()
        model.keys("5", "000")

        assertNull(model.now.investmentId)
        assertFalse(model.now.canSubmit)
        // Values in pesos: 5.242.000, USD 1.050,50 at 4.150,25 = 4.359.838 (IN28),
        // 4.000.000 (IN09), 3.050.000; Cripto has none and comes last.
        assertEquals(
            listOf("Fondo de acciones", "Acciones EE. UU.", "Prueba", "Ecopetrol", "Cripto"),
            model.now.investments.map { it.name }
        )
        assertEquals(InvestmentMovementKind.Contribution, model.now.kind)

        model.onAction(InvestmentMovementAction.InvestmentPicked(ecopetrol))
        assertTrue(model.now.canSubmit)
    }

    @Test
    fun anAporteFromAnInvestmentSaysWhatWasPutInAndIsOneRecordAtTheVersionRead() = runTest {
        val model = model(investment = ecopetrol)
        val fact = model.now.fact as InvestmentMovementFact.Contributed
        assertEquals("3.000.000" to "Ecopetrol", fact.amount.digits to fact.name)

        model.keys("5", "0", "0", "000")
        model.onAction(InvestmentMovementAction.Submit)
        model.onAction(InvestmentMovementAction.Submit)

        val sent = commands.sent.single() as RecordInvestmentMovement
        assertEquals(Contribution, sent.kind)
        assertEquals(Money.cop(500_000), sent.amount.money)
        assertEquals(day("2026-10-25"), sent.date)
        assertEquals(
            InvestmentId.of(ecopetrol).value() to 6L,
            sent.investmentId to sent.expectedVersion
        )
        assertEquals(3L, sent.generation)
        assertTrue(model.now.closed)
        val notice = corrections.notice.value as CorrectionNotice.InvestmentMovementRecorded
        assertEquals(Contribution to "500.000", notice.kind to notice.amount!!.digits)
    }

    @Test
    fun theFactFollowsTheTypeAsTheDesignWritesIt() = runTest {
        val model = model(investment = ecopetrol, kind = InvestmentMovementViewModel.KIND_VALUATION)
        val last = model.now.fact as InvestmentMovementFact.LastValue
        assertEquals("3.050.000" to day("2026-10-09"), last.amount.digits to last.valuedOn)

        model.onAction(InvestmentMovementAction.Kind(Dividend))
        val paid = model.now.fact as InvestmentMovementFact.Dividends
        assertEquals("178.000", paid.amount.digits)

        model.onAction(InvestmentMovementAction.InvestmentPicked(fondo))
        model.onAction(InvestmentMovementAction.Kind(Withdrawal))
        val worth = model.now.fact as InvestmentMovementFact.Worth
        assertEquals("5.242.000" to day("2026-10-14"), worth.amount.digits to worth.valuedOn)
    }

    @Test
    fun in10ARetiroAboveTheValueWaitsAndExactlyTheValueIsAccepted() = runTest {
        val model = model(investment = prueba, kind = InvestmentMovementViewModel.KIND_WITHDRAWAL)
        val worth = model.now.fact as InvestmentMovementFact.Worth
        assertEquals("4.000.000" to day("2026-10-01"), worth.amount.digits to worth.valuedOn)

        model.keys("4", "0", "0", "0", "0", "0", "1")
        assertEquals("4.000.000", model.now.overValue?.digits)
        assertFalse(model.now.canSubmit)
        assertTrue(model.now.offersValue)
        model.onAction(InvestmentMovementAction.Submit)
        assertEquals(emptyList<InvestmentCommand>(), commands.sent)

        model.keys("<", "0")
        assertNull(model.now.overValue)
        model.onAction(InvestmentMovementAction.Submit)
        val sent = commands.sent.single() as RecordInvestmentMovement
        assertEquals(Withdrawal to Money.cop(4_000_000), sent.kind to sent.amount.money)
    }

    @Test
    fun in18ARetiroWithNoValueWaitsAndOffersToAnotarTheValueFirst() = runTest {
        val model = model(investment = cripto, kind = InvestmentMovementViewModel.KIND_WITHDRAWAL)
        assertEquals(InvestmentMovementFact.NoValue, model.now.fact)
        model.keys("1", "000")

        assertTrue(model.now.noValue)
        assertFalse(model.now.canSubmit)
        assertTrue(model.now.offersValue)

        model.onAction(InvestmentMovementAction.RecordValueFirst)
        assertEquals(Valuation, model.now.kind)
        assertEquals(cripto, model.now.investmentId)
        assertEquals(InvestmentMovementFact.FirstValue, model.now.fact)
        assertFalse(model.now.noValue)
    }

    @Test
    fun in20EditingTheRetiroAboveWhatItIsWorthSaysTheValueOnItsDate() = runTest {
        val model = model(investment = prueba, movement = movementId(35).value)
        assertTrue(model.now.editing)
        assertEquals(Withdrawal, model.now.kind)
        assertEquals("2.000.000", model.now.amount!!.digits)
        assertFalse(model.now.canSubmit)

        model.onAction(InvestmentMovementAction.ClearAmount)
        model.keys("7", "000", "000")

        assertEquals("6.000.000", model.now.overValue?.digits)
        assertFalse(model.now.canSubmit)
        assertFalse(model.now.offersValue)
    }

    @Test
    fun in19ACorrectionALaterRetiroNeedsNamesItAndWaits() = runTest {
        // IN08, then a retiro of 6.000.000 on 12 oct.
        reads.board.value = board(
            investment(
                prueba,
                "Prueba",
                "home",
                *in09.take(4).toTypedArray(),
                movement(36, Withdrawal, 6_000_000, "2026-10-12")
            )
        )
        val model = model(investment = prueba, movement = movementId(34).value)
        model.onAction(InvestmentMovementAction.ClearAmount)
        model.keys("5", "0", "0", "000")

        val blocked = checkNotNull(model.now.blockedBy) { "no retiro named" }
        assertEquals(
            Triple("6.000.000", day("2026-10-12"), "5.500.000"),
            Triple(blocked.amount.digits, blocked.date, blocked.available!!.digits)
        )
        assertFalse(model.now.canSubmit)
    }

    @Test
    fun in19DeletingTheAporteALaterRetiroNeedsIsRefusedNamingThatRetiro() = runTest {
        reads.board.value = board(
            investment(
                prueba,
                "Prueba",
                "home",
                *in09.take(4).toTypedArray(),
                movement(36, Withdrawal, 6_000_000, "2026-10-12")
            )
        )
        val model = model(investment = prueba, movement = movementId(34).value)
        commands.answers += CommandOutcome.Rejected(
            Specific(InvestmentRejection.AboveValue(movementId(36), Money.cop(5_000_000)))
        )

        model.onAction(InvestmentMovementAction.Delete)

        val blocked = checkNotNull(model.now.blockedBy) { "no retiro named" }
        assertEquals(
            Triple("6.000.000", day("2026-10-12"), "5.000.000"),
            Triple(blocked.amount.digits, blocked.date, blocked.available!!.digits)
        )
        assertFalse(model.now.closed)
        assertNull(corrections.notice.value)
    }

    @Test
    fun in22TheOnlyAporteCannotBeDeletedAndTheFormSaysWhy() = runTest {
        val model = model(investment = cripto, movement = movementId(41).value)
        commands.answers += CommandOutcome.Rejected(Specific(InvestmentRejection.LastContribution))

        model.onAction(InvestmentMovementAction.Delete)

        assertEquals(InvestmentMovementError.LastContribution, model.now.error)
        assertFalse(model.now.closed)
    }

    @Test
    fun in25AValueBeforeTheFirstAporteWaitsWithItsDateAndAnAporteThenIsAccepted() = runTest {
        val model = model(investment = fondo, kind = InvestmentMovementViewModel.KIND_VALUATION)
        model.onAction(InvestmentMovementAction.DatePicked(day("2025-05-01")))
        model.keys("6", "000", "000")

        assertEquals(day("2025-05-03"), model.now.firstContribution)
        assertFalse(model.now.canSubmit)

        model.onAction(InvestmentMovementAction.Kind(Contribution))
        assertNull(model.now.firstContribution)
        assertTrue(model.now.canSubmit)
    }

    @Test
    fun in25TheCommandsRefusalBeforeTheFirstAporteIsShownWithItsDate() = runTest {
        val model = model(investment = fondo, kind = InvestmentMovementViewModel.KIND_DIVIDEND)
        model.keys("1", "000")
        commands.answers += CommandOutcome.Rejected(
            Specific(InvestmentRejection.BeforeFirstContribution(day("2025-05-03")))
        )

        model.onAction(InvestmentMovementAction.Submit)

        assertEquals(day("2025-05-03"), model.now.firstContribution)
        assertFalse(model.now.closed)
    }

    @Test
    fun rule2AValueOfZeroPesosCanBeRecordedButAnAporteOfZeroWaits() = runTest {
        val value = model(investment = ecopetrol, kind = InvestmentMovementViewModel.KIND_VALUATION)
        value.keys("0")
        assertTrue(value.now.canSubmit)
        value.onAction(InvestmentMovementAction.Submit)
        assertEquals(
            Money.cop(0),
            (commands.sent.single() as RecordInvestmentMovement).amount.money
        )

        val replaced =
            model(investment = ecopetrol, kind = InvestmentMovementViewModel.KIND_VALUATION)
        replaced.keys("0", "5", "000")
        replaced.onAction(InvestmentMovementAction.Submit)
        assertEquals(
            Money.cop(5_000),
            (commands.sent.last() as RecordInvestmentMovement).amount.money
        )

        val aporte = model(investment = ecopetrol)
        aporte.keys("0")
        assertFalse(aporte.now.canSubmit)
    }

    @Test
    fun in34DollarsAreTypedWithTheCommaAndSentAsExactCents() = runTest {
        val model = model(investment = acciones)
        assertTrue(model.now.usd)
        model.keys("2", "5", "0", "000", ",", "5")
        assertEquals("250,5", model.now.amount!!.digits)
        assertEquals("USD", model.now.amount!!.symbol)
        model.keys("0", "1")
        assertTrue(model.now.atLimit)

        model.onAction(InvestmentMovementAction.Submit)

        val sent = commands.sent.single() as RecordInvestmentMovement
        assertEquals(Money(25_050, Currency.USD), sent.amount.money)
        val fact = (corrections.notice.value as CorrectionNotice.InvestmentMovementRecorded)
        assertEquals("250,50", fact.amount!!.digits)
    }

    @Test
    fun in34TenWholeDigitsOfDollarsAreTheMostAnAmountTakes() = runTest {
        val model = model(investment = acciones)
        model.keys("9", "9", "9", "9", "9", "9", "9", "9", "9", "9")
        assertFalse(model.now.atLimit)
        model.keys("9")
        assertTrue(model.now.atLimit)
        model.keys(",", "9", "9")

        model.onAction(InvestmentMovementAction.Submit)

        val sent = commands.sent.single() as RecordInvestmentMovement
        assertEquals(Money(999_999_999_999, Currency.USD), sent.amount.money)
    }

    @Test
    fun anAmountTypedInPesosIsNotCarriedToADollarInvestment() = runTest {
        val model = model()
        model.keys("5", "000")
        model.onAction(InvestmentMovementAction.InvestmentPicked(acciones))

        assertNull(model.now.amount)
        assertTrue(model.now.usd)
    }

    @Test
    fun anUnknownOutcomeIsAskedAboutWithTheSameIdBeforeAnythingElseIsSent() = runTest {
        val model = model(investment = ecopetrol)
        model.keys("5", "000")
        commands.answers +=
            CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))

        model.onAction(InvestmentMovementAction.Submit)
        val operation = commands.sent.single().operationId
        assertEquals(InvestmentMovementError.Unknown, model.now.error)
        model.keys("0")
        model.onAction(InvestmentMovementAction.Submit)
        assertEquals(1, commands.sent.size)
        assertEquals("5.000", model.now.amount!!.digits)

        commands.found = Outcome.Success(
            CommittedInvestment(
                OperationReceipt(
                    operation,
                    OperationKind.RecordInvestmentMovement,
                    3,
                    10,
                    Instant.EPOCH
                ),
                InvestmentId.of(ecopetrol).value()
            )
        )
        model.onAction(InvestmentMovementAction.Retry)

        assertEquals(listOf(operation, operation), commands.asked)
        assertEquals(1, commands.sent.size)
        assertTrue(model.now.closed)
    }

    @Test
    fun afterProcessDeathAnUnknownSaveIsAskedAboutWithItsOwnId() = runTest {
        val saved = SavedStateHandle(mapOf(InvestmentMovementViewModel.KEY_INVESTMENT to ecopetrol))
        val first = model(saved = saved)
        first.keys("5", "000")
        commands.answers +=
            CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        first.onAction(InvestmentMovementAction.Submit)
        val operation = commands.sent.single().operationId

        commands.found = Outcome.Success(null)
        val restored = model(saved = saved)

        assertEquals(listOf(operation, operation), commands.asked)
        assertEquals(1, commands.sent.size)
        assertEquals(InvestmentMovementError.NotSaved, restored.now.error)
    }

    @Test
    fun afterProcessDeathTheDraftAndTheOpenNoteAreBack() = runTest {
        val saved = SavedStateHandle(mapOf(InvestmentMovementViewModel.KEY_INVESTMENT to acciones))
        val first = model(saved = saved)
        first.onAction(InvestmentMovementAction.Kind(Dividend))
        first.keys("1", "2", ",", "3")
        first.onAction(InvestmentMovementAction.DatePicked(day("2026-10-02")))
        first.onAction(InvestmentMovementAction.OpenComposer)
        first.onAction(InvestmentMovementAction.ComposerChanged("Trimestre"))

        val restored = model(saved = saved)

        assertEquals(Dividend, restored.now.kind)
        assertEquals("12,3", restored.now.amount!!.digits)
        assertEquals(day("2026-10-02"), restored.now.date)
        assertTrue(restored.now.composerOpen)
        assertEquals("Trimestre", restored.now.composerDraft)
        restored.onAction(InvestmentMovementAction.ComposerDone)
        restored.onAction(InvestmentMovementAction.Submit)
        val sent = commands.sent.single() as RecordInvestmentMovement
        assertEquals(Money(1_230, Currency.USD), sent.amount.money)
        assertEquals("Trimestre", sent.note!!.value)
    }

    @Test
    fun editingSendsTheCorrectionAtBothVersionsOnlyOnceSomethingChanged() = runTest {
        val model = model(investment = ecopetrol, movement = movementId(23).value)
        assertEquals("89.000", model.now.amount!!.digits)
        assertFalse(model.now.canSubmit)
        model.onAction(InvestmentMovementAction.Kind(Contribution))
        assertEquals(Dividend, model.now.kind)

        model.onAction(InvestmentMovementAction.ClearAmount)
        model.keys("9", "0", "000")
        model.onAction(InvestmentMovementAction.Submit)

        val sent = commands.sent.single() as EditInvestmentMovement
        assertEquals(movementId(23) to 2L, sent.movementId to sent.movementVersion)
        assertEquals(6L, sent.expectedVersion)
        assertEquals(Money.cop(90_000), sent.amount.money)
        assertEquals(CorrectionNotice.InvestmentMovementUpdated, corrections.notice.value)
        assertTrue(model.now.closed)
    }

    @Test
    fun in21DeletingAMovementGoesThroughTheSharedUndoAndDeshacerPutsItBack() = runTest {
        val model = model(investment = prueba, movement = movementId(35).value)
        commands.undoOffer = {
            commands.sent.filterIsInstance<DeleteInvestmentMovement>().lastOrNull()?.let {
                InvestmentUndoOffer(it.operationId, it.investmentId, 6.seconds)
            }
        }

        model.onAction(InvestmentMovementAction.Delete)

        val sent = commands.sent.single() as DeleteInvestmentMovement
        assertEquals(movementId(35) to 2L, sent.movementId to sent.movementVersion)
        assertTrue(model.now.closed)
        assertEquals(
            CorrectionNotice.InvestmentMovementDeleted(canUndo = true),
            corrections.notice.value
        )

        corrections.undo()

        val restore = commands.sent.last() as RestoreInvestmentMovement
        assertEquals(sent.operationId, restore.deletionId)
        assertEquals(CorrectionNotice.InvestmentMovementRestored, corrections.notice.value)
    }

    @Test
    fun aFailedReadIsNotNoInvestments() = runTest {
        reads.board.value = ReadState.Failed(StorageFailure.Unavailable("x"), null)
        val model = model()

        assertTrue(model.now.loadFailed)
        assertFalse(model.now.loading)
        assertFalse(model.now.canSubmit)
    }

    @Test
    fun leavingAsksOnlyWhenSomethingWasTyped() = runTest {
        val clean = model(investment = ecopetrol)
        clean.onAction(InvestmentMovementAction.Back)
        assertTrue(clean.now.closed)

        val typed = model(investment = ecopetrol)
        typed.keys("5")
        typed.onAction(InvestmentMovementAction.Back)
        assertFalse(typed.now.closed)
        assertTrue(typed.now.confirmDiscard)
    }
}
