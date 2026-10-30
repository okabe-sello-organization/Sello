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
        commands.completionOffer = {
            commands.sent.lastOrNull()?.let {
                GoalUndoOffer(
                    it.operationId,
                    (GoalId.of(goal) as Outcome.Success).value,
                    60.seconds
                )
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
    fun deshacerLastsAboutTenSecondsAfterTheMomentAndLongerWithAScreenReader() = runTest {
        completer().complete(viaje)
        reading = true
        completer().complete(viaje)

        val (plain, read) = commands.sent.map { (it as CompleteGoal).undoFor }
        assertEquals(14.seconds, plain)
        assertEquals(34.seconds, read)
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
