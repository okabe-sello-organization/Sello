package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalUndoOffer
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.testing.TestMoneyLabels
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Marcar como lograda: one completion, the big moment only after a confirmed save, and
 * Deshacer for about ten seconds after it (longer with a screen reader). Figures are the
 * approved example MT23; today is 23 October 2026.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalCompleterTest {
    private val scheduler = TestCoroutineScheduler()
    private val commands = HandGoalCommands()
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }
    private var reading = false
    private lateinit var corrections: Corrections
    private val goal = "00000000-0000-4000-9000-000000000001"

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        corrections = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            commands,
            HandInvestmentCommands(),
            { 5.seconds + scheduler.currentTime.milliseconds },
            ids,
            CoroutineScope(Dispatchers.Main),
            HandCategoryReads(),
            testMoney()
        )
        // Storage's offer as RoomGoalCommands keeps it: the time asked, from the save at 0.
        commands.completionOffer = {
            (commands.sent.lastOrNull() as? CompleteGoal)?.let {
                GoalUndoOffer(it.operationId, it.goalId, 5.seconds + it.undoFor)
            }
        }
    }

    @After
    fun reset() = Dispatchers.resetMain()

    private fun completer(saved: SavedStateHandle = SavedStateHandle()) = GoalCompleter(
        saved,
        commands,
        corrections,
        ids,
        HandFinancialClock("2026-10-23"),
        { reading },
        CoroutineScope(Dispatchers.Main)
    )

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    /** MT23: 3.060.000 saved in six aportes. */
    private val viaje =
        CompletionTarget(goal, 9, 3, "Viaje a la costa", "flight", plain("3.060.000"), 6)

    /** What storage keeps for a completion: its receipt names the completion's own operation. */
    private fun receipt(
        operation: OperationId = (OperationId.of(ids.next()) as Outcome.Success).value
    ) = CommittedGoal(
        OperationReceipt(operation, OperationKind.CompleteGoal, 3, 11, Instant.EPOCH),
        (GoalId.of(goal) as Outcome.Success).value,
        null
    )

    @Test
    fun mt23MarkingLogradaCelebratesWithWhatWasSavedThenOffersDeshacer() = runTest {
        val completer = completer()

        completer.complete(viaje)

        val sent = commands.sent.single() as CompleteGoal
        assertEquals(
            (GoalId.of(goal) as Outcome.Success).value to 9L,
            sent.goalId to sent.expectedVersion
        )
        assertEquals(3L, sent.generation)
        assertEquals(
            CompletionState.Celebrating(
                LogradaPlate(
                    "Viaje a la costa",
                    "flight",
                    plain("3.060.000"),
                    LocalDate.of(2026, 10, 23),
                    6,
                    goal
                )
            ),
            completer.state.value
        )
        assertEquals(
            CorrectionNotice.GoalCompleted("Viaje a la costa", canUndo = true),
            corrections.notice.value
        )
        completer.done()
        assertEquals(CompletionState.Idle, completer.state.value)
    }

    @Test
    fun storageKeepsDeshacerPossibleWhileTheMomentShowsAndOffersAboutTenSeconds() = runTest {
        completer().complete(viaje)
        assertEquals(5.minutes, (commands.sent.single() as CompleteGoal).undoFor)

        // A moment that never closes here: about ten seconds from the save.
        scheduler.advanceTimeBy(9999.milliseconds)
        scheduler.runCurrent()
        assertEquals(
            CorrectionNotice.GoalCompleted("Viaje a la costa", canUndo = true),
            corrections.notice.value
        )
        scheduler.advanceTimeBy(1.milliseconds)
        scheduler.runCurrent()
        assertEquals(null, corrections.notice.value)
    }

    @Test
    fun listoNineSecondsAfterTheSaveOffersDeshacerUntilNineteenSeconds() = runTest {
        val completer = completer()
        completer.complete(viaje)

        scheduler.advanceTimeBy(9.seconds)
        scheduler.runCurrent()
        completer.done()

        scheduler.advanceTimeBy(9999.milliseconds)
        scheduler.runCurrent()
        assertEquals(
            CorrectionNotice.GoalCompleted("Viaje a la costa", canUndo = true),
            corrections.notice.value
        )
        scheduler.advanceTimeBy(1.milliseconds)
        scheduler.runCurrent()
        assertEquals(null, corrections.notice.value)
    }

    @Test
    fun withAScreenReaderListoAfterFortySecondsOffersDeshacerThirtySecondsMore() = runTest {
        reading = true
        val completer = completer()
        completer.complete(viaje)

        // The time the save asked ran out behind the moment.
        scheduler.advanceTimeBy(40.seconds)
        scheduler.runCurrent()
        assertEquals(null, corrections.notice.value)
        completer.done()

        scheduler.advanceTimeBy(29999.milliseconds)
        scheduler.runCurrent()
        assertEquals(
            CorrectionNotice.GoalCompleted("Viaje a la costa", canUndo = true),
            corrections.notice.value
        )
        scheduler.advanceTimeBy(1.milliseconds)
        scheduler.runCurrent()
        assertEquals(null, corrections.notice.value)
    }

    @Test
    fun closingTheMomentAgainNeverGrowsTheOffer() = runTest {
        val completer = completer()
        completer.complete(viaje)
        scheduler.advanceTimeBy(2.seconds)
        scheduler.runCurrent()

        // Listo, then Listo again from a recreated screen on the same ViewModel.
        completer.done()
        scheduler.advanceTimeBy(5.seconds)
        scheduler.runCurrent()
        completer.done()

        scheduler.advanceTimeBy(4999.milliseconds)
        scheduler.runCurrent()
        assertTrue(corrections.notice.value is CorrectionNotice.GoalCompleted)
        scheduler.advanceTimeBy(1.milliseconds)
        scheduler.runCurrent()
        assertEquals(null, corrections.notice.value)
    }

    @Test
    fun aCompletionFoundAgainOffersAboutTenSecondsNotStoragesWholeTime() = runTest {
        val completer = completer()
        commands.next =
            CommandOutcome.OutcomeUnknown((OperationId.of(ids.next()) as Outcome.Success).value)
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        completer.complete(viaje)
        commands.found = Outcome.Success(receipt(commands.sent.single().operationId))
        completer.retry()

        scheduler.advanceTimeBy(9999.milliseconds)
        scheduler.runCurrent()
        assertTrue(corrections.notice.value is CorrectionNotice.GoalCompleted)
        scheduler.advanceTimeBy(1.milliseconds)
        scheduler.runCurrent()
        assertEquals(null, corrections.notice.value)
    }

    @Test
    fun aCompletionFoundSixtySecondsAfterItsSaveIsOnlySaid() = runTest {
        val completer = completer()
        commands.next =
            CommandOutcome.OutcomeUnknown((OperationId.of(ids.next()) as Outcome.Success).value)
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        completer.complete(viaje)

        scheduler.advanceTimeBy(60.seconds)
        scheduler.runCurrent()
        commands.found = Outcome.Success(receipt(commands.sent.single().operationId))
        completer.retry()

        assertEquals(
            CorrectionNotice.GoalCompleted("Viaje a la costa", canUndo = false),
            corrections.notice.value
        )
    }

    @Test
    fun aRecreationThatFindsTheCompletionDoesNotOpenANewWindow() = runTest {
        val saved = SavedStateHandle()
        commands.next =
            CommandOutcome.OutcomeUnknown((OperationId.of(ids.next()) as Outcome.Success).value)
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        completer(saved).complete(viaje)

        // Recreated 8 seconds after the save: Deshacer has the 2 seconds left, not 10.
        scheduler.advanceTimeBy(8.seconds)
        scheduler.runCurrent()
        commands.found = Outcome.Success(receipt(commands.sent.single().operationId))
        completer(saved)

        scheduler.advanceTimeBy(1999.milliseconds)
        scheduler.runCurrent()
        assertEquals(
            CorrectionNotice.GoalCompleted("Viaje a la costa", canUndo = true),
            corrections.notice.value
        )
        scheduler.advanceTimeBy(1.milliseconds)
        scheduler.runCurrent()
        assertEquals(null, corrections.notice.value)
    }

    @Test
    fun listoAfterStoragesFiveMinutesSaysLogradaWithoutDeshacer() = runTest {
        val completer = completer()
        completer.complete(viaje)

        scheduler.advanceTimeBy(5.minutes)
        scheduler.runCurrent()
        completer.done()

        assertEquals(
            CorrectionNotice.GoalCompleted("Viaje a la costa", canUndo = false),
            corrections.notice.value
        )
    }

    @Test
    fun listoNeverOffersDeshacerAgainOnceItsUndoWasBlocked() = runTest {
        val completer = completer()
        completer.complete(viaje)
        // Blocked: storage still keeps the completion, in time, for Deshacer.
        val completion = commands.sent.single() as CompleteGoal
        commands.completionOffer =
            { GoalUndoOffer(completion.operationId, completion.goalId, 5.minutes) }
        commands.next =
            CommandOutcome.Rejected(CommandRejection.Specific(GoalRejection.UndoConflict))
        corrections.undo()
        assertEquals(CorrectionNotice.GoalCompletionUndoTooLate, corrections.notice.value)

        completer.done()

        assertEquals(CorrectionNotice.GoalCompletionUndoTooLate, corrections.notice.value)
    }

    @Test
    fun listoAfterAnUndoThatRestoredTheGoalKeepsSayingItIsBack() = runTest {
        val completer = completer()
        completer.complete(viaje)
        // The undo commits: storage keeps nothing more for Deshacer.
        corrections.undo()
        commands.completionOffer = { null }
        assertEquals(
            CorrectionNotice.GoalCompletionUndone("Viaje a la costa"),
            corrections.notice.value
        )

        completer.done()

        assertEquals(
            CorrectionNotice.GoalCompletionUndone("Viaje a la costa"),
            corrections.notice.value
        )
    }

    @Test
    fun aCompletionFoundTooLateLeavesAnotherDeletionOnOfferInPlace() = runTest {
        val completer = completer()
        commands.next =
            CommandOutcome.OutcomeUnknown((OperationId.of(ids.next()) as Outcome.Success).value)
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        completer.complete(viaje)
        scheduler.advanceTimeBy(60.seconds)
        scheduler.runCurrent()
        val movement = (OperationId.of(ids.next()) as Outcome.Success).value
        commands.undoOffer = {
            GoalUndoOffer(movement, (GoalId.of(goal) as Outcome.Success).value, 120.seconds)
        }
        corrections.goalMovementDeleted(
            OperationReceipt(movement, OperationKind.DeleteGoalMovement, 3, 12, Instant.EPOCH)
        )

        commands.found = Outcome.Success(receipt(commands.sent.single().operationId))
        completer.retry()

        assertEquals(
            CorrectionNotice.GoalMovementDeleted(canUndo = true),
            corrections.notice.value
        )
    }

    @Test
    fun listoLeavesAnotherDeletionOnOfferInPlace() = runTest {
        val completer = completer()
        completer.complete(viaje)
        val movement = (OperationId.of(ids.next()) as Outcome.Success).value
        commands.undoOffer = {
            GoalUndoOffer(movement, (GoalId.of(goal) as Outcome.Success).value, 60.seconds)
        }
        corrections.goalMovementDeleted(
            OperationReceipt(movement, OperationKind.DeleteGoalMovement, 3, 12, Instant.EPOCH)
        )

        completer.done()

        assertEquals(
            CorrectionNotice.GoalMovementDeleted(canUndo = true),
            corrections.notice.value
        )
    }

    @Test
    fun listoNeverOffersDeshacerPastWhatStorageKeeps() = runTest {
        val completer = completer()
        completer.complete(viaje)

        // Five minutes of storage's time; Listo with four minutes and 55 seconds gone.
        scheduler.advanceTimeBy(295.seconds)
        scheduler.runCurrent()
        completer.done()

        scheduler.advanceTimeBy(4999.milliseconds)
        scheduler.runCurrent()
        assertTrue(corrections.notice.value is CorrectionNotice.GoalCompleted)
        scheduler.advanceTimeBy(1.milliseconds)
        scheduler.runCurrent()
        assertEquals(null, corrections.notice.value)
    }

    @Test
    fun whenStorageNoLongerKeepsTheCompletionClosingTheMomentOnlySaysIt() = runTest {
        val completer = completer()
        commands.completionOffer = { null }
        completer.complete(viaje)
        scheduler.advanceTimeBy(5.seconds)
        scheduler.runCurrent()

        completer.done()

        assertEquals(
            CorrectionNotice.GoalCompleted("Viaje a la costa", canUndo = false),
            corrections.notice.value
        )
    }

    @Test
    fun pressedTwiceItIsSentOnce() = runTest {
        commands.pause = true
        val completer = completer()

        completer.complete(viaje)
        completer.complete(viaje)
        assertEquals(CompletionState.Busy, completer.state.value)
        commands.release(CommandOutcome.Committed(receipt(), replayed = false))

        assertEquals(1, commands.sent.size)
        assertTrue(completer.state.value is CompletionState.Celebrating)
    }

    @Test
    fun anUnknownOutcomeIsAskedAboutAndAFoundCompletionIsNotCelebratedAgain() = runTest {
        val completer = completer()
        commands.next =
            CommandOutcome.OutcomeUnknown((OperationId.of(ids.next()) as Outcome.Success).value)
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))

        completer.complete(viaje)
        val operation = commands.sent.single().operationId
        assertEquals(CompletionState.Failed(GoalDetailError.Unknown), completer.state.value)
        completer.complete(viaje)
        assertEquals(1, commands.sent.size)

        commands.found = Outcome.Success(receipt(operation))
        completer.retry()

        assertEquals(listOf(operation, operation), commands.asked)
        assertEquals(CompletionState.Idle, completer.state.value)
        assertEquals(
            CorrectionNotice.GoalCompleted("Viaje a la costa", canUndo = true),
            corrections.notice.value
        )
    }

    @Test
    fun aRefusedCompletionSaysItWasNotSaved() = runTest {
        val completer = completer()
        commands.queue += CommandOutcome.Rejected(CommandRejection.VersionConflict(10))

        completer.complete(viaje)

        assertEquals(CompletionState.Failed(GoalDetailError.NotSaved), completer.state.value)
    }

    @Test
    fun afterProcessDeathACompletionInFlightIsFoundNotSentAgain() = runTest {
        val saved = SavedStateHandle()
        commands.next =
            CommandOutcome.OutcomeUnknown((OperationId.of(ids.next()) as Outcome.Success).value)
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        completer(saved).complete(viaje)

        commands.found = Outcome.Success(receipt(commands.sent.single().operationId))
        val restored = completer(saved)

        assertEquals(1, commands.sent.size)
        assertEquals(CompletionState.Idle, restored.state.value)
        assertEquals(
            CorrectionNotice.GoalCompleted("Viaje a la costa", canUndo = true),
            corrections.notice.value
        )
    }
}
