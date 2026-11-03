package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
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
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.ReplaceStar
import com.software.sello.domain.port.StarGoal
import com.software.sello.domain.port.UnstarGoal
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val money = testMoney()
    private val clock = HandFinancialClock("2026-10-23")
    private val reads = HandGoalReads()
    private val commands = HandGoalCommands()
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }

    private val viajeId = "00000000-0000-4000-9000-000000000001"
    private val celularId = "00000000-0000-4000-9000-000000000002"

    /** The test's virtual time, which the notices' clock follows so they end when they should. */
    private val scheduler = TestCoroutineScheduler()
    private lateinit var corrections: Corrections

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        corrections = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            commands,
            HandInvestmentCommands(),
            { 5.hours + scheduler.currentTime.milliseconds },
            ids,
            CoroutineScope(Dispatchers.Main),
            HandCategoryReads(),
            testMoney()
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

    /** [recibo] is Recibo's list, [featured] alone heading it; [starred] are the goals starred. */
    private fun board(
        vararg goals: GoalSummary,
        featured: String? = null,
        recibo: List<String> = listOfNotNull(featured),
        starred: Set<String> = emptySet()
    ) = ReadState.Ready(
        GoalBoard(
            3,
            9,
            goals.toList(),
            starred.map { GoalId.of(it).value() }.toSet(),
            recibo.map { GoalId.of(it).value() }
        )
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
        reads.board.value = ReadState.Failed(StorageFailure.Unavailable("x"), null)
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
    fun starringSendsOneCommandWithWhatWasRead() = runTest {
        // Shown first on Recibo only to fill a place: not starred, so the pill offers to star.
        reads.board.value = board(
            summary(viajeId, 1_710_000, 3_000_000),
            summary(celularId, 450_000, 1_200_000),
            featured = viajeId
        )
        commands.pause = true
        val model = detail()
        watch(model)
        assertEquals(false, ready(model).goal.starred)

        model.onAction(GoalDetailAction.ToggleStar)
        model.onAction(GoalDetailAction.ToggleStar)
        assertTrue(ready(model).busy)
        commands.release(committed())

        val sent = commands.sent.single() as StarGoal
        assertEquals(GoalId.of(viajeId).value(), sent.goalId)
        assertEquals(3L, sent.generation)
        assertEquals(false, ready(model).busy)
        assertNull(ready(model).error)
    }

    @Test
    fun withSeveralStarsStarringAddsOneAndAFilledStarUnstars() = runTest {
        reads.board.value = board(
            summary(viajeId, 1_710_000, 3_000_000),
            summary(celularId, 450_000, 1_200_000),
            summary(motoId, 100_000, 9_000_000),
            recibo = listOf(celularId, motoId, viajeId),
            starred = setOf(celularId, motoId)
        )
        val viaje = detail()
        watch(viaje)
        val moto = detail(SavedStateHandle(mapOf("goalId" to motoId)))
        watch(moto)
        assertEquals(true, ready(moto).goal.starred)

        moto.onAction(GoalDetailAction.ToggleStar)
        viaje.onAction(GoalDetailAction.ToggleStar)

        assertEquals(
            listOf(
                UnstarGoal(commands.sent[0].operationId, 3, GoalId.of(motoId).value()),
                StarGoal(commands.sent[1].operationId, 3, GoalId.of(viajeId).value())
            ),
            commands.sent
        )
    }

    @Test
    fun aCompletedGoalSendsNothing() = runTest {
        reads.board.value = board(
            summary(viajeId, 400_000, 400_000, completedOn = LocalDate.of(2026, 9, 30))
        )
        val model = detail()
        watch(model)

        model.onAction(GoalDetailAction.ToggleStar)

        assertEquals(emptyList<GoalCommand>(), commands.sent)
    }

    @Test
    fun anUnknownOutcomeIsAskedAboutWithTheSameOperationBeforeAnythingElseIsSent() = runTest {
        reads.board.value = board(summary(viajeId, 1_710_000, 3_000_000), featured = celularId)
        val model = detail()
        watch(model)
        commands.next = CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))

        model.onAction(GoalDetailAction.ToggleStar)
        val operation = commands.sent.single().operationId
        assertEquals(GoalDetailError.Unknown, ready(model).error)
        assertEquals(listOf(operation), commands.asked)

        // Nothing new is sent until that outcome is known.
        model.onAction(GoalDetailAction.ToggleStar)
        assertEquals(1, commands.sent.size)
        commands.found = Outcome.Success(null)
        model.onAction(GoalDetailAction.Retry)

        assertEquals(listOf(operation, operation), commands.asked)
        assertEquals(GoalDetailError.NotSaved, ready(model).error)
        assertEquals(1, commands.sent.size)
    }

    @Test
    fun aRejectedStarSaysItWasNotSaved() = runTest {
        reads.board.value = board(summary(viajeId, 1_710_000, 3_000_000), featured = celularId)
        val model = detail()
        watch(model)
        commands.next = CommandOutcome.Rejected(CommandRejection.StaleGeneration(4))

        model.onAction(GoalDetailAction.ToggleStar)

        assertEquals(GoalDetailError.NotSaved, ready(model).error)
        assertEquals(false, ready(model).busy)
        assertNull(ready(model).replacing)
    }

    // ---------- The sixth star (SELLO-144) ----------

    private val motoId = "00000000-0000-4000-9000-000000000003"
    private val biciId = "00000000-0000-4000-9000-000000000004"
    private val fondoId = "00000000-0000-4000-9000-000000000005"
    private val cursoId = "00000000-0000-4000-9000-000000000006"

    /**
     * Viaje (57 %) unstarred while five are: Celular 450.000 of 1.200.000 (37 %), Moto
     * 100.000 of 1.000.000 (10 %), Bici 900.000 of 1.000.000 (90 %), Fondo 500.000 of
     * 1.000.000 (50 %) and Curso 10.000 of 1.000.000 (1 %). Recibo's order, by completion:
     * Bici, Fondo, Celular, Moto, Curso.
     */
    private fun fiveStarred() = board(
        summary(viajeId, 1_710_000, 3_000_000),
        summary(celularId, 450_000, 1_200_000),
        summary(motoId, 100_000, 1_000_000),
        summary(biciId, 900_000, 1_000_000),
        summary(fondoId, 500_000, 1_000_000),
        summary(cursoId, 10_000, 1_000_000),
        recibo = listOf(biciId, fondoId, celularId, motoId, cursoId),
        starred = setOf(celularId, motoId, biciId, fondoId, cursoId)
    )

    @Test
    fun withFiveStarredTheStarOpensTheSheetInRecibosOrderAndSendsNothing() = runTest {
        reads.board.value = fiveStarred()
        val model = detail()
        watch(model)

        model.onAction(GoalDetailAction.ToggleStar)

        assertEquals(emptyList<GoalCommand>(), commands.sent)
        val sheet = ready(model).replacing!!
        assertEquals(
            listOf(biciId, fondoId, celularId, motoId, cursoId),
            sheet.choices.map {
                it.id
            }
        )
        assertEquals(listOf(90L, 50L, 37L, 10L, 1L), sheet.choices.map { it.percentage })
        assertNull(sheet.picked)
    }

    @Test
    fun pickingReplacesThatStarInOneCommandOnceAndClosesTheSheet() = runTest {
        reads.board.value = fiveStarred()
        commands.pause = true
        val model = detail()
        watch(model)
        model.onAction(GoalDetailAction.ToggleStar)

        model.onAction(GoalDetailAction.Replace(motoId))
        // Double submit: a second pick while the first is on its way sends nothing.
        model.onAction(GoalDetailAction.Replace(cursoId))
        model.onAction(GoalDetailAction.CancelReplace)
        assertTrue(ready(model).busy)
        assertEquals(motoId, ready(model).replacing?.picked)
        commands.release(committed())

        assertEquals(
            ReplaceStar(
                commands.sent.single().operationId,
                3,
                GoalId.of(motoId).value(),
                GoalId.of(viajeId).value()
            ),
            commands.sent.single()
        )
        assertNull(ready(model).replacing)
        assertNull(ready(model).error)
    }

    @Test
    fun aRefusedReplaceSaysSoKeepsThePickAndTriesAgainAsANewOperation() = runTest {
        reads.board.value = fiveStarred()
        val model = detail()
        watch(model)
        model.onAction(GoalDetailAction.ToggleStar)
        commands.next = CommandOutcome.Rejected(
            CommandRejection.Specific(GoalRejection.NotStarred)
        )

        model.onAction(GoalDetailAction.Replace(motoId))

        assertEquals(GoalDetailError.NotSaved, ready(model).error)
        assertEquals(motoId, ready(model).replacing?.picked)
        assertEquals(false, ready(model).busy)

        model.onAction(GoalDetailAction.Replace(motoId))
        assertEquals(2, commands.sent.size)
        assertTrue(commands.sent[0].operationId != commands.sent[1].operationId)
        assertNull(ready(model).replacing)
    }

    @Test
    fun anUnknownReplaceIsCheckedBeforeAnyOtherPickAndClosesOnceFoundCommitted() = runTest {
        reads.board.value = fiveStarred()
        val model = detail()
        watch(model)
        model.onAction(GoalDetailAction.ToggleStar)
        commands.next = CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))

        model.onAction(GoalDetailAction.Replace(motoId))
        val operation = commands.sent.single().operationId
        assertEquals(GoalDetailError.Unknown, ready(model).error)
        assertEquals(motoId, ready(model).replacing?.picked)

        model.onAction(GoalDetailAction.Replace(cursoId))
        assertEquals(1, commands.sent.size)

        commands.found = Outcome.Success(committed().value)
        model.onAction(GoalDetailAction.Retry)

        assertEquals(listOf(operation, operation), commands.asked)
        assertEquals(1, commands.sent.size)
        assertNull(ready(model).replacing)
        assertNull(ready(model).error)
    }

    @Test
    fun backingOutChangesNothing() = runTest {
        reads.board.value = fiveStarred()
        val model = detail()
        watch(model)
        model.onAction(GoalDetailAction.ToggleStar)

        model.onAction(GoalDetailAction.CancelReplace)
        // A pick after the sheet closed is not a choice.
        model.onAction(GoalDetailAction.Replace(motoId))

        assertNull(ready(model).replacing)
        assertEquals(emptyList<GoalCommand>(), commands.sent)
    }

    @Test
    fun aGoalNotStarredIsNoChoice() = runTest {
        reads.board.value = fiveStarred()
        val model = detail()
        watch(model)
        model.onAction(GoalDetailAction.ToggleStar)

        model.onAction(GoalDetailAction.Replace(viajeId))

        assertEquals(emptyList<GoalCommand>(), commands.sent)
    }

    @Test
    fun theSheetAndARefusedPickComeBackAfterProcessDeath() = runTest {
        reads.board.value = fiveStarred()
        val saved = SavedStateHandle(mapOf("goalId" to viajeId))
        val first = detail(saved)
        watch(first)
        first.onAction(GoalDetailAction.ToggleStar)
        commands.next = CommandOutcome.Rejected(CommandRejection.StaleGeneration(4))
        first.onAction(GoalDetailAction.Replace(fondoId))

        val restored = detail(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        watch(restored)

        assertEquals(fondoId, ready(restored).replacing?.picked)
        assertEquals(5, ready(restored).replacing?.choices?.size)
    }

    @Test
    fun aStarRefusedForTheLimitOpensTheSheet() = runTest {
        // Read with four stars; a fifth was starred elsewhere before this one landed.
        reads.board.value = board(
            summary(viajeId, 1_710_000, 3_000_000),
            summary(celularId, 450_000, 1_200_000),
            recibo = listOf(celularId, viajeId),
            starred = setOf(celularId)
        )
        val model = detail()
        watch(model)
        commands.next = CommandOutcome.Rejected(
            CommandRejection.Specific(GoalRejection.StarLimit(5))
        )

        model.onAction(GoalDetailAction.ToggleStar)

        assertNull(ready(model).error)
        assertEquals(listOf(celularId), ready(model).replacing?.choices?.map { it.id })
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
        commands.next = CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        val first = detail(saved)
        watch(first)
        first.onAction(GoalDetailAction.AskToDelete)
        first.onAction(GoalDetailAction.Delete)
        val operation = commands.sent.single().operationId

        commands.found = Outcome.Success(committed().value)
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

    private fun committed() = CommandOutcome.Committed(
        CommittedGoal(
            OperationReceipt(
                OperationId.of(ids.next()).value(),
                OperationKind.StarGoal,
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
