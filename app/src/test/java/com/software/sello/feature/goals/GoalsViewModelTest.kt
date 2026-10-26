package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
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
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.GoalUndoOffer
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })
    private val clock = HandFinancialClock("2026-10-23")
    private val reads = HandGoalReads()
    private val commands = HandGoalCommands()
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }

    private val viajeId = "00000000-0000-4000-9000-000000000001"
    private val celularId = "00000000-0000-4000-9000-000000000002"

    /** The test's virtual time, which the notices' clock follows so they end when they should. */
    private val scheduler = TestCoroutineScheduler()
    private lateinit var corrections: ExpenseCorrections

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        corrections = ExpenseCorrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            commands,
            HandInvestmentCommands(),
            { 5.hours + scheduler.currentTime.milliseconds },
            ids,
            CoroutineScope(Dispatchers.Main)
        )
    }

    @After
    fun reset() = Dispatchers.resetMain()

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun summary(id: String, saved: Long, target: Long, completedOn: LocalDate? = null) =
        Goal(
            GoalId.of(id).value(),
            GoalName.of("Meta $id".takeLast(20)).value(),
            IconKey.of("flight").value(),
            null,
            completedOn,
            4,
            Instant.EPOCH,
            Instant.EPOCH
        ).let { goal ->
            val day = LocalDate.of(2026, 10, 1)
            val events = listOf(
                GoalTargetChange(TransactionAmount.of(Money.cop(target)).value(), 0, day),
                GoalMovement(
                    GoalMovementId.of(id.dropLast(1) + "9").value(),
                    GoalMovementKind.Contribution,
                    TransactionAmount.of(Money.cop(saved)).value(),
                    day,
                    1,
                    day,
                    null,
                    1,
                    Instant.EPOCH,
                    Instant.EPOCH
                )
            )
            val history = GoalHistory(goal, events)
            GoalSummary(history, GoalPolicy.progress(history).value())
        }

    private fun board(vararg goals: GoalSummary, featured: String? = null) = GoalBoardState.Ready(
        GoalBoard(3, 9, goals.toList(), featured?.let { GoalId.of(it).value() })
    )

    private fun detail(saved: SavedStateHandle = SavedStateHandle(mapOf("goalId" to viajeId))) =
        GoalDetailViewModel(saved, reads, commands, clock, money, ids, corrections) { false }

    private fun TestScope.watch(model: GoalDetailViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }

    private fun TestScope.watch(model: MetasViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }

    private fun ready(model: GoalDetailViewModel) = model.state.value as GoalDetailState.Ready

    @Test
    fun metasFollowsTheBoardAndRetryObservesItAgain() = runTest {
        val model =
            MetasViewModel(SavedStateHandle(), reads, commands, clock, money, ids, corrections) {
                false
            }
        watch(model)
        reads.board.value = GoalBoardState.Failed(StorageFailure.Unavailable("x"), null)
        assertEquals(MetasState.Failed, model.state.value)

        model.onAction(MetasAction.Retry)
        reads.board.value = board(summary(viajeId, 1_710_000, 3_000_000))

        assertEquals(2, reads.started)
        assertEquals(
            listOf(viajeId),
            (model.state.value as MetasState.Ready).goals.map { it.id }
        )
    }

    @Test
    fun choosingTheGoalForReciboSendsOneCommandWithWhatWasRead() = runTest {
        reads.board.value = board(
            summary(viajeId, 1_710_000, 3_000_000),
            summary(celularId, 450_000, 1_200_000),
            featured = celularId
        )
        commands.pause = true
        val model = detail()
        watch(model)

        model.onAction(GoalDetailAction.Feature)
        model.onAction(GoalDetailAction.Feature)
        assertTrue(ready(model).busy)
        commands.release(committed())

        val sent = commands.sent.single() as FeatureGoal
        assertEquals(GoalId.of(viajeId).value(), sent.goalId)
        assertEquals(3L, sent.generation)
        assertEquals(false, ready(model).busy)
        assertNull(ready(model).error)
    }

    @Test
    fun theGoalAlreadyOnReciboAndACompletedGoalSendNothing() = runTest {
        reads.board.value = board(summary(viajeId, 1_710_000, 3_000_000), featured = viajeId)
        val featured = detail()
        watch(featured)
        featured.onAction(GoalDetailAction.Feature)

        reads.board.value = board(
            summary(viajeId, 400_000, 400_000, completedOn = LocalDate.of(2026, 9, 30))
        )
        featured.onAction(GoalDetailAction.Feature)

        assertEquals(emptyList<GoalCommand>(), commands.sent)
    }

    @Test
    fun anUnknownOutcomeIsAskedAboutWithTheSameOperationBeforeAnythingElseIsSent() = runTest {
        reads.board.value = board(summary(viajeId, 1_710_000, 3_000_000), featured = celularId)
        val model = detail()
        watch(model)
        commands.next = GoalCommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))

        model.onAction(GoalDetailAction.Feature)
        val operation = commands.sent.single().operationId
        assertEquals(GoalDetailError.Unknown, ready(model).error)
        assertEquals(listOf(operation), commands.asked)

        // Nothing new is sent until that outcome is known.
        model.onAction(GoalDetailAction.Feature)
        assertEquals(1, commands.sent.size)
        commands.found = Outcome.Success(null)
        model.onAction(GoalDetailAction.Retry)

        assertEquals(listOf(operation, operation), commands.asked)
        assertEquals(GoalDetailError.NotSaved, ready(model).error)
        assertEquals(1, commands.sent.size)
    }

    @Test
    fun aRejectedChoiceSaysItWasNotSaved() = runTest {
        reads.board.value = board(summary(viajeId, 1_710_000, 3_000_000), featured = celularId)
        val model = detail()
        watch(model)
        commands.next = GoalCommandOutcome.Rejected(GoalRejection.StaleGeneration(4))

        model.onAction(GoalDetailAction.Feature)

        assertEquals(GoalDetailError.NotSaved, ready(model).error)
        assertEquals(false, ready(model).busy)
    }

    @Test
    fun aCompletedGoalIsDeletedAfterTheConfirmSlipAtTheVersionRead() = runTest {
        reads.board.value = board(
            summary(viajeId, 400_000, 400_000, completedOn = LocalDate.of(2026, 9, 30))
        )
        val model = detail()
        watch(model)

        model.onAction(GoalDetailAction.AskToDelete)
        assertTrue(ready(model).confirmingDelete)
        model.onAction(GoalDetailAction.Delete)

        val sent = commands.sent.single() as DeleteGoal
        assertEquals(GoalId.of(viajeId).value() to 4L, sent.goalId to sent.expectedVersion)
        assertEquals(3L, sent.generation)
        assertTrue(model.deleted.value)
    }

    @Test
    fun anActiveGoalCannotBeDeletedFromItsDetail() = runTest {
        reads.board.value = board(summary(viajeId, 1_710_000, 3_000_000))
        val model = detail()
        watch(model)

        model.onAction(GoalDetailAction.AskToDelete)
        model.onAction(GoalDetailAction.Delete)

        assertEquals(false, ready(model).confirmingDelete)
        assertEquals(emptyList<GoalCommand>(), commands.sent)
    }

    @Test
    fun afterProcessDeathADeletionInFlightIsFoundNotSentAgain() = runTest {
        reads.board.value = board(
            summary(viajeId, 400_000, 400_000, completedOn = LocalDate.of(2026, 9, 30))
        )
        val saved = SavedStateHandle(mapOf("goalId" to viajeId))
        commands.next = GoalCommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        val first = detail(saved)
        watch(first)
        first.onAction(GoalDetailAction.AskToDelete)
        first.onAction(GoalDetailAction.Delete)
        val operation = commands.sent.single().operationId

        commands.found = Outcome.Success(committed().goal)
        val restored = detail(saved)
        watch(restored)

        assertEquals(operation, commands.asked.last())
        assertEquals(1, commands.sent.size)
        assertTrue(restored.deleted.value)
    }

    @Test
    fun theConfirmSlipAndTheWholeListSurviveRecreation() = runTest {
        reads.board.value = board(
            summary(viajeId, 400_000, 400_000, completedOn = LocalDate.of(2026, 9, 30))
        )
        val saved = SavedStateHandle(mapOf("goalId" to viajeId))
        val first = detail(saved)
        watch(first)
        first.onAction(GoalDetailAction.AskToDelete)
        first.onAction(GoalDetailAction.ShowAllMovements)

        val again = detail(saved)
        watch(again)

        assertTrue(ready(again).confirmingDelete)
        assertTrue(ready(again).allMovements)
    }

    @Test
    fun mt23AtOneHundredPercentMarcarComoLogradaCompletesAndShowsTheMoment() = runTest {
        // MT05: 2.950.000 + 110.000 = 3.060.000 of 3.000.000, the Meta waiting.
        reads.board.value = board(summary(viajeId, 3_060_000, 3_000_000))
        val model = detail()
        watch(model)
        assertEquals(true, ready(model).goal.next?.reached)

        model.onAction(GoalDetailAction.MarkComplete)
        model.onAction(GoalDetailAction.MarkComplete)

        val sent = commands.sent.single() as CompleteGoal
        assertEquals(GoalId.of(viajeId).value() to 4L, sent.goalId to sent.expectedVersion)
        val moment = ready(model).completion as CompletionState.Celebrating
        assertEquals("3.060.000" to 1, moment.plate.saved.digits to moment.plate.contributions)
        model.onAction(GoalDetailAction.CelebrationDone)
        assertEquals(CompletionState.Idle, ready(model).completion)
    }

    @Test
    fun belowItsTargetAGoalIsNotMarkedFromItsPage() = runTest {
        reads.board.value = board(summary(viajeId, 1_710_000, 3_000_000))
        val model = detail()
        watch(model)

        model.onAction(GoalDetailAction.MarkComplete)

        assertEquals(emptyList<GoalCommand>(), commands.sent)
    }

    private fun committed() = GoalCommandOutcome.Committed(
        CommittedGoal(
            OperationReceipt(
                OperationId.of(ids.next()).value(),
                OperationKind.FeatureGoal,
                3,
                10,
                Instant.EPOCH
            ),
            GoalId.of(viajeId).value(),
            null
        ),
        replayed = false
    )
}

/** The board a test sets by hand; [started] counts observations. */
class HandGoalReads : GoalReads {
    val board = MutableStateFlow<GoalBoardState>(GoalBoardState.Loading)
    var started = 0

    /** What a one-off read returns; [reads] counts them. */
    var read: Outcome<GoalBoard, StorageFailure> =
        Outcome.Failure(StorageFailure.Unavailable("not used"))
    var reads = 0

    override suspend fun board(): Outcome<GoalBoard, StorageFailure> {
        reads++
        return read
    }

    override fun observeBoard(): Flow<GoalBoardState> = board.onStart { started++ }

    override suspend fun goal(id: GoalId): Outcome<GoalSummary?, StorageFailure> =
        Outcome.Failure(StorageFailure.Unavailable("not used"))
}

/**
 * Commands a test answers by hand: [next] once, or committed. With [pause] a command
 * waits for [release]. [found] answers [find], and [asked] records what was asked.
 */
class HandGoalCommands : GoalCommands {
    val sent = mutableListOf<GoalCommand>()
    val asked = mutableListOf<OperationId>()
    var next: GoalCommandOutcome? = null

    /** Answers in order, before [next]. */
    val queue = ArrayDeque<GoalCommandOutcome>()
    var found: Outcome<CommittedGoal?, StorageFailure> = Outcome.Success(null)
    var pause = false
    private var waiting: CompletableDeferred<GoalCommandOutcome>? = null

    fun release(outcome: GoalCommandOutcome) {
        waiting?.complete(outcome)
    }

    override suspend fun submit(command: GoalCommand): GoalCommandOutcome {
        sent += command
        if (pause) {
            val deferred = CompletableDeferred<GoalCommandOutcome>()
            waiting = deferred
            return deferred.await()
        }
        queue.removeFirstOrNull()?.let { return it }
        return next?.also { next = null } ?: GoalCommandOutcome.Committed(
            CommittedGoal(
                OperationReceipt(
                    command.operationId,
                    OperationKind.DeleteGoal,
                    command.generation,
                    10,
                    Instant.EPOCH
                ),
                (GoalId.of("00000000-0000-4000-9000-000000000001") as Outcome.Success).value,
                null
            ),
            replayed = false
        )
    }

    /** What [movementUndoOffer] says is on offer. */
    var undoOffer: () -> GoalUndoOffer? = { null }

    override fun movementUndoOffer(): GoalUndoOffer? = undoOffer()

    /** What [completionUndoOffer] says is on offer. */
    var completionOffer: () -> GoalUndoOffer? = { null }

    override fun completionUndoOffer(): GoalUndoOffer? = completionOffer()

    override suspend fun find(operationId: OperationId): Outcome<CommittedGoal?, StorageFailure> {
        asked += operationId
        return found
    }
}
