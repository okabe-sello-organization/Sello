package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.DeleteGoalMovement
import com.software.sello.domain.port.EditGoalMovement
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.GoalUndoOffer
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
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
 * Aportar and Retirar through the real goal rules. Figures are the approved examples
 * (docs/testing/goal-examples.md) by ID; today is 23 October 2026.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalMovementViewModelTest {
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })
    private val clock = HandFinancialClock("2026-10-23")
    private val today = LocalDate.of(2026, 10, 23)
    private val reads = HandGoalReads()
    private val commands = HandGoalCommands()
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }
    private lateinit var corrections: Corrections

    private val viaje = "00000000-0000-4000-9000-000000000001"
    private val celular = "00000000-0000-4000-9000-000000000002"
    private val vacunas = "00000000-0000-4000-9000-000000000003"
    private val fondo = "00000000-0000-4000-9000-000000000004"

    /** The test's virtual time, which the notices' clock follows so they end when they should. */
    private val scheduler = TestCoroutineScheduler()

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        corrections = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            commands,
            HandInvestmentCommands(),
            { scheduler.currentTime.milliseconds },
            ids,
            CoroutineScope(Dispatchers.Main)
        )
        reads.board.value = GoalBoardState.Ready(
            GoalBoard(
                3,
                9,
                listOf(
                    goal(celular, "Celular nuevo", 1_200_000, aporte(21, 450_000)),
                    goal(
                        viaje,
                        "Viaje a la costa",
                        3_000_000,
                        aporte(11, 750_000),
                        aporte(12, 960_000)
                    ),
                    goal(vacunas, "Vacunas de Luna", 400_000, aporte(31, 400_000), done = true),
                    goal(
                        fondo,
                        "Fondo",
                        1_000_000,
                        aporte(41, 500_000),
                        movement(
                            42,
                            GoalMovementKind.Withdrawal,
                            300_000,
                            LocalDate.of(2026, 10, 20)
                        )
                    )
                ),
                null
            )
        )
    }

    @After
    fun reset() = Dispatchers.resetMain()

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).value()

    private fun movementId(number: Int) =
        GoalMovementId.of("00000000-0000-4000-8000-%012d".format(number)).value()

    private fun movement(
        number: Int,
        kind: GoalMovementKind,
        amount: Long,
        date: LocalDate = LocalDate.of(2026, 10, 1)
    ) = GoalMovement(
        movementId(number),
        kind,
        pesos(amount),
        date,
        (number % 10).toLong(),
        date,
        null,
        2,
        Instant.EPOCH,
        Instant.EPOCH
    )

    private fun aporte(number: Int, amount: Long) =
        movement(number, GoalMovementKind.Contribution, amount)

    private fun goal(
        id: String,
        name: String,
        target: Long,
        vararg movements: GoalMovement,
        done: Boolean = false
    ): GoalSummary {
        val history = GoalHistory(
            Goal(
                GoalId.of(id).value(),
                GoalName.of(name).value(),
                IconKey.of("flight").value(),
                null,
                LocalDate.of(2026, 10, 2).takeIf { done },
                5,
                Instant.ofEpochSecond(id.takeLast(1).toLong()),
                Instant.EPOCH
            ),
            listOf<GoalEvent>(GoalTargetChange(pesos(target), 0, LocalDate.of(2026, 10, 1))) +
                movements
        )
        return GoalSummary(history, GoalPolicy.progress(history).value())
    }

    private fun model(
        goal: String? = null,
        withdrawal: Boolean = false,
        movement: String? = null,
        saved: SavedStateHandle = SavedStateHandle(
            listOfNotNull(
                goal?.let { "goalId" to it },
                "withdrawal" to withdrawal,
                movement?.let { "movementId" to it }
            ).toMap()
        )
    ) = GoalMovementViewModel(saved, reads, commands, clock, ids, money, corrections)

    private fun GoalMovementViewModel.keys(vararg keys: String) = keys.forEach {
        onAction(
            when (it) {
                "000" -> GoalMovementAction.TripleZero
                "<" -> GoalMovementAction.Backspace
                else -> GoalMovementAction.Digit(it.toInt())
            }
        )
    }

    @Test
    fun fromAGoalAnAporteIsOneRecordAtTheVersionReadAndSaysWhatWasSaved() = runTest {
        val model = model(goal = viaje)
        assertEquals(viaje, model.state.value.goalId)
        assertEquals("1.710.000", model.state.value.goal!!.saved.digits)
        model.keys("3", "5", "0", "000")

        model.onAction(GoalMovementAction.Submit)
        model.onAction(GoalMovementAction.Submit)

        val sent = commands.sent.single() as RecordGoalMovement
        assertEquals(GoalMovementKind.Contribution, sent.kind)
        assertEquals(pesos(350_000), sent.amount)
        assertEquals(today, sent.date)
        assertEquals(GoalId.of(viaje).value() to 5L, sent.goalId to sent.expectedVersion)
        assertEquals(3L, sent.generation)
        assertNull(sent.note)
        assertTrue(model.state.value.closed)
        val notice = corrections.notice.value as CorrectionNotice.GoalMovementRecorded
        assertEquals(false to "350.000", notice.withdrawal to notice.amount!!.digits)
    }

    @Test
    fun fromTheTabNoGoalIsChosenAndActiveGoalsAreOfferedBestFirstWithTheirPercentage() = runTest {
        val model = model()
        model.keys("5", "000")
        assertNull(model.state.value.goalId)
        assertFalse(model.state.value.canSubmit)

        // Completed goals are not offered; Fondo is 500.000 − 300.000 of 1.000.000.
        assertEquals(
            listOf("Viaje a la costa" to 57L, "Celular nuevo" to 37L, "Fondo" to 20L),
            model.state.value.goals.map { it.name to it.percentage }
        )
        model.onAction(GoalMovementAction.GoalPicked(vacunas))
        assertNull(model.state.value.goalId)
        model.onAction(GoalMovementAction.GoalPicked(celular))
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun mt08ARetiroAboveWhatIsSavedSaysWhatIsAvailableAndWaits() = runTest {
        val model = model(goal = viaje, withdrawal = true)
        model.keys("1", "7", "1", "0", "0", "0", "1")

        assertEquals("1.710.000", model.state.value.overSaved!!.digits)
        assertFalse(model.state.value.canSubmit)
        model.onAction(GoalMovementAction.Submit)
        assertEquals(emptyList<GoalCommand>(), commands.sent)

        model.keys("<")
        model.keys("0")
        assertNull(model.state.value.overSaved)
        model.onAction(GoalMovementAction.Submit)
        assertEquals(
            GoalMovementKind.Withdrawal,
            (commands.sent.single() as RecordGoalMovement).kind
        )
        val notice = corrections.notice.value as CorrectionNotice.GoalMovementRecorded
        assertTrue(notice.withdrawal)
    }

    @Test
    fun mt20AndMt21APastDateIsKeptAndAFutureOneIsNot() = runTest {
        val model = model(goal = viaje)
        model.onAction(GoalMovementAction.DatePicked(LocalDate.of(2026, 10, 24)))
        assertEquals(today, model.state.value.date)

        model.onAction(GoalMovementAction.DatePicked(LocalDate.of(2026, 9, 15)))
        model.keys("7", "5", "0", "000")
        model.onAction(GoalMovementAction.Submit)

        assertEquals(LocalDate.of(2026, 9, 15), (commands.sent.single() as RecordGoalMovement).date)
    }

    @Test
    fun anUnknownOutcomeIsAskedAboutAndNothingElseIsSentUntilItIsKnown() = runTest {
        val model = model(goal = viaje)
        model.keys("5", "000")
        commands.next = GoalCommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))

        model.onAction(GoalMovementAction.Submit)
        val operation = commands.sent.single().operationId
        assertEquals(GoalMovementError.Unknown, model.state.value.error)
        model.keys("0")
        model.onAction(GoalMovementAction.Submit)
        assertEquals(1, commands.sent.size)
        assertEquals("5.000", model.state.value.amount!!.digits)

        commands.found = Outcome.Success(
            CommittedGoal(
                OperationReceipt(operation, OperationKind.RecordGoalMovement, 3, 10, Instant.EPOCH),
                GoalId.of(viaje).value(),
                null
            )
        )
        model.onAction(GoalMovementAction.Retry)

        assertEquals(listOf(operation, operation), commands.asked)
        assertTrue(model.state.value.closed)
    }

    @Test
    fun editingAnAporteSendsTheCorrectionAtBothVersionsOnlyOnceSomethingChanged() = runTest {
        val model = model(goal = viaje, movement = movementId(12).value)
        assertTrue(model.state.value.editing)
        assertEquals("960.000", model.state.value.amount!!.digits)
        assertFalse(model.state.value.canSubmit)

        model.onAction(GoalMovementAction.ClearAmount)
        model.keys("9", "0", "0", "000")
        model.onAction(GoalMovementAction.Kind(withdrawal = true))
        assertFalse(model.state.value.withdrawal)
        model.onAction(GoalMovementAction.Submit)

        val sent = commands.sent.single() as EditGoalMovement
        assertEquals(movementId(12) to 2L, sent.movementId to sent.movementVersion)
        assertEquals(5L, sent.expectedVersion)
        assertEquals(pesos(900_000), sent.amount)
        assertEquals(CorrectionNotice.GoalMovementUpdated, corrections.notice.value)
        assertTrue(model.state.value.closed)
    }

    @Test
    fun mt15LoweringAnAporteALaterRetiroUsesSaysWhichRetiroAndWaits() = runTest {
        val model = model(goal = fondo, movement = movementId(41).value)
        model.onAction(GoalMovementAction.ClearAmount)
        model.keys("2", "9", "9", "9", "9", "9")

        val blocked = model.state.value.blockedBy!!
        assertEquals("300.000" to LocalDate.of(2026, 10, 20), blocked.amount.digits to blocked.date)
        assertFalse(model.state.value.canSubmit)

        model.keys("<", "<", "<", "<", "<", "<", "3", "000", "0", "0")
        assertNull(model.state.value.blockedBy)
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun mt15AnAporteALaterRetiroUsesCannotBeDeletedAndTheFormSaysWhy() = runTest {
        val model = model(goal = fondo, movement = movementId(41).value)
        commands.queue += GoalCommandOutcome.Rejected(
            GoalRejection.AboveSaved(movementId(42), Money.cop(0))
        )

        model.onAction(GoalMovementAction.Delete)

        assertEquals("300.000", model.state.value.blockedBy!!.amount.digits)
        assertFalse(model.state.value.closed)
        assertNull(corrections.notice.value)
    }

    @Test
    fun deletingAMovementClosesTheFormAndOffersUndoInTheMovementsWords() = runTest {
        val model = model(goal = viaje, movement = movementId(12).value)
        commands.undoOffer = {
            commands.sent.lastOrNull()?.let {
                GoalUndoOffer(it.operationId, GoalId.of(viaje).value(), 6.seconds)
            }
        }

        model.onAction(GoalMovementAction.Delete)

        val sent = commands.sent.single() as DeleteGoalMovement
        assertEquals(movementId(12) to 2L, sent.movementId to sent.movementVersion)
        assertTrue(model.state.value.closed)
        assertEquals(CorrectionNotice.GoalMovementDeleted(canUndo = true), corrections.notice.value)
    }

    @Test
    fun afterProcessDeathTheDraftAndTheOpenNoteAreBack() = runTest {
        val saved = SavedStateHandle(mapOf("goalId" to viaje, "withdrawal" to false))
        val first = model(saved = saved)
        first.keys("4", "5", "000")
        first.onAction(GoalMovementAction.DatePicked(LocalDate.of(2026, 10, 2)))
        first.onAction(GoalMovementAction.OpenComposer)
        first.onAction(GoalMovementAction.ComposerChanged("Prima"))

        val restored = model(saved = saved)

        assertEquals("45.000", restored.state.value.amount!!.digits)
        assertEquals(LocalDate.of(2026, 10, 2), restored.state.value.date)
        assertTrue(restored.state.value.composerOpen)
        assertEquals("Prima", restored.state.value.composerDraft)
    }

    @Test
    fun aNoteOverSixtyCharactersKeepsTheComposerOpenWithTheText() = runTest {
        val model = model(goal = viaje)
        model.onAction(GoalMovementAction.OpenComposer)
        model.onAction(GoalMovementAction.ComposerChanged("a".repeat(61)))
        model.onAction(GoalMovementAction.ComposerDone)

        assertTrue(model.state.value.composerOpen)
        assertEquals(GoalNoteProblem.TooLong, model.state.value.composerProblem)

        model.onAction(GoalMovementAction.ComposerChanged("Arreglo del carro"))
        model.onAction(GoalMovementAction.ComposerDone)
        assertFalse(model.state.value.composerOpen)
        assertEquals("Arreglo del carro", model.state.value.note)
    }

    @Test
    fun leavingAsksOnlyWhenSomethingWasTyped() = runTest {
        val clean = model(goal = viaje)
        clean.onAction(GoalMovementAction.Back)
        assertTrue(clean.state.value.closed)

        val typed = model(goal = viaje)
        typed.keys("5")
        typed.onAction(GoalMovementAction.Back)
        assertTrue(typed.state.value.confirmDiscard)
        typed.onAction(GoalMovementAction.DiscardConfirmed)
        assertTrue(typed.state.value.closed)
        assertEquals(emptyList<GoalCommand>(), commands.sent)
    }

    private fun committedWith(change: GoalChange?, replayed: Boolean = false) =
        GoalCommandOutcome.Committed(
            CommittedGoal(
                OperationReceipt(
                    OperationId.of(ids.next()).value(),
                    OperationKind.RecordGoalMovement,
                    3,
                    10,
                    Instant.EPOCH
                ),
                GoalId.of(celular).value(),
                change
            ),
            replayed
        )

    @Test
    fun anAporteThatEarnsAStampCarriesItToBeCelebratedWithWhatIsLeft() = runTest {
        val model = model(goal = celular)
        // 450.000 + 300.000 = 750.000 of 1.200.000: past 50 % (600.000), 450.000 left.
        model.keys("3", "0", "0", "000")
        commands.queue += committedWith(GoalChange(listOf(GoalPlace.Half), emptyList(), false))

        model.onAction(GoalMovementAction.Submit)

        val notice = corrections.notice.value as CorrectionNotice.GoalMovementRecorded
        assertEquals(
            GoalCelebration(celular, "Celular nuevo", listOf(GoalPlace.Half), emptyList(), false),
            notice.celebration?.copy(missing = null)
        )
        assertEquals("450.000", notice.celebration!!.missing!!.digits)
    }

    @Test
    fun aReplayOrAnAporteWithoutAStampCelebratesNothing() = runTest {
        val replay = model(goal = celular)
        replay.keys("3", "0", "0", "000")
        commands.queue += committedWith(change = null, replayed = true)
        replay.onAction(GoalMovementAction.Submit)
        assertNull((corrections.notice.value as CorrectionNotice.GoalMovementRecorded).celebration)

        val plain = model(goal = celular)
        plain.keys("1", "000")
        commands.queue += committedWith(GoalChange(emptyList(), emptyList(), false))
        plain.onAction(GoalMovementAction.Submit)
        assertNull((corrections.notice.value as CorrectionNotice.GoalMovementRecorded).celebration)
    }

    @Test
    fun aSaveFoundAfterAnUnknownOutcomeIsNotCelebrated() = runTest {
        val model = model(goal = celular)
        model.keys("3", "0", "0", "000")
        commands.next = GoalCommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Success(
            committedWith(GoalChange(listOf(GoalPlace.Half), emptyList(), false)).goal
        )

        model.onAction(GoalMovementAction.Submit)

        assertTrue(model.state.value.closed)
        assertNull((corrections.notice.value as CorrectionNotice.GoalMovementRecorded).celebration)
    }
}
