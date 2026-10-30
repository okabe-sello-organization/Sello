package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PlaceState
import com.software.sello.domain.port.ChangeGoalTarget
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.DeleteGoalMovement
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditGoal
import com.software.sello.domain.port.EditGoalMovement
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.RestoreGoalMovement
import com.software.sello.domain.port.UndoGoalCompletion
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Goals against a real database file, through the real commands and reads. Figures are
 * the approved examples of docs/testing/goal-examples.md by ID. Records are numbered
 * from 100 (the first goal is 100, its target 101) and operations from 1000; today is
 * 9 October 2026.
 */
@RunWith(AndroidJUnit4::class)
class GoalCommandContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val today = LocalDate.of(2026, 10, 9)
    private val viaje = GoalId.of(uuid(100)).valueOrFail()

    private fun movement(number: Int) = GoalMovementId.of(uuid(number)).valueOrFail()

    private fun events() =
        ledger.rows("goal_event", "sequence", "id", "kind", "amount_minor", "version")

    private fun receipts() = ledger.rows("operation_receipt", "revision", "kind", "subject_id")

    private suspend fun submit(command: com.software.sello.domain.port.GoalCommand) =
        ledger.goals.submit(command)

    private fun create(
        target: Long = 3_000_000,
        name: String = "Viaje a la costa",
        operation: OperationId = ledger.operation(),
        generation: Long = 1
    ) = CreateGoal(
        operation,
        generation,
        GoalName.of(name).valueOrFail(),
        IconKey.of("flight").valueOrFail(),
        transactionPesos(target),
        YearMonth.of(2026, 12)
    )

    private suspend fun version(goal: GoalId = viaje) = summary(goal).history.goal.version

    private suspend fun summary(goal: GoalId = viaje): GoalSummary =
        (ledger.goalReads.goal(goal) as Outcome.Success).value!!

    private suspend fun record(
        pesos: Long,
        kind: GoalMovementKind = GoalMovementKind.Contribution,
        date: LocalDate = today,
        goal: GoalId = viaje,
        operation: OperationId = ledger.operation()
    ) = submit(
        RecordGoalMovement(
            operation,
            1,
            goal,
            version(goal),
            kind,
            transactionPesos(pesos),
            date,
            null
        )
    )

    private suspend fun aporte(pesos: Long, goal: GoalId = viaje) = record(pesos, goal = goal)

    private suspend fun retiro(pesos: Long, goal: GoalId = viaje) =
        record(pesos, GoalMovementKind.Withdrawal, goal = goal)

    private suspend fun delete(number: Int, operation: OperationId = ledger.operation()) =
        submit(DeleteGoalMovement(operation, 1, viaje, version(), movement(number), 1))

    private fun committed(outcome: GoalCommandOutcome) = (outcome as Committed).value

    private suspend fun started(target: Long = 3_000_000) {
        assertTrue(submit(create(target)) is Committed)
    }

    // ---- Creating ----

    @Test
    fun aNewGoalIsItsRowAndItsTargetWithOneRevisionAndOneReceipt() = runBlocking {
        val operation = ledger.operation()

        val outcome = submit(create(operation = operation))

        val saved = committed(outcome)
        assertEquals(OperationKind.CreateGoal to viaje, saved.receipt.kind to saved.goalId)
        assertEquals(
            listOf("${uuid(100)}|Viaje a la costa|2026-12|null|1|1"),
            ledger.rows(
                "goal",
                "id",
                "name",
                "target_month",
                "completed_on",
                "next_sequence",
                "version"
            )
        )
        assertEquals(listOf("0|${uuid(101)}|target|3000000|1"), events())
        assertEquals(1L, ledger.revision())
        assertEquals(listOf("1|goal.create|${uuid(100)}"), receipts())
        assertEquals(emptyList<GoalMovementId>(), saved.movementIds)
        // A found receipt does not keep which movements it wrote, so it names none.
        assertEquals(Outcome.Success(saved.copy(movementIds = null)), ledger.goals.find(operation))
        val board = (ledger.goalReads.board() as Outcome.Success).value
        assertEquals(listOf(0L), board.goals.map { it.progress.percentage })
        assertEquals(viaje, board.featured)
    }

    @Test
    fun theSameGoalSentTwiceIsOneGoalAndADifferentOneUnderItsNameIsAConflict() = runBlocking {
        val operation = ledger.operation()
        val first = submit(create(operation = operation))

        val again = submit(create(operation = operation))
        val other = submit(create(target = 1, operation = operation))
        val stale = submit(create(generation = 2))

        assertEquals(Committed(committed(first).copy(change = null), replayed = true), again)
        assertEquals(Rejected(CommandRejection.OperationConflict), other)
        assertEquals(Rejected(CommandRejection.StaleGeneration(current = 1)), stale)
        assertEquals(1, ledger.rows("goal", "id").size)
        assertEquals(1L, ledger.revision())
    }

    // ---- Contributions and withdrawals ----

    @Test
    fun mt01AnAporteReachingTheQuarterEarnsItsStampOnce() = runBlocking {
        started()

        val outcome = aporte(750_000)

        assertEquals(
            GoalChange(listOf(GoalPlace.Quarter), emptyList(), false),
            committed(outcome).change
        )
        val progress = summary().progress
        assertEquals(Money.cop(750_000), progress.saved)
        assertEquals(25L, progress.percentage)
        assertEquals(
            listOf("0|${uuid(101)}|target|3000000|1", "1|${uuid(102)}|contribution|750000|1"),
            events()
        )
        assertEquals(2L, version())
    }

    @Test
    fun mt08AWithdrawalAboveWhatIsSavedIsRefusedAndWritesNothing() = runBlocking {
        started()
        aporte(1_710_000)
        val before = events()

        val outcome = retiro(1_710_001)

        assertEquals(
            Rejected(Specific(GoalRejection.AboveSaved(movement(103), Money.cop(1_710_000)))),
            outcome
        )
        assertEquals(before, events())
        assertEquals(2L, ledger.revision())
    }

    @Test
    fun mt07AWithdrawalKeepsTheStampItFallsBelow() = runBlocking {
        started()
        aporte(1_710_000)

        retiro(400_000)

        val progress = summary().progress
        assertEquals(Money.cop(1_310_000), progress.saved)
        assertEquals(43L, progress.percentage)
        assertEquals(PlaceState.Kept(today), progress.state(GoalPlace.Half))
    }

    @Test
    fun mt21AFutureDateIsRefused() = runBlocking {
        started()

        val outcome = record(1, date = LocalDate.of(2026, 10, 10))

        assertEquals(Rejected(Specific(GoalRejection.FutureDate(today))), outcome)
    }

    @Test
    fun aMovementForAnOldVersionOfTheGoalIsRefused() = runBlocking {
        started()
        aporte(100)

        val outcome = submit(
            RecordGoalMovement(
                ledger.operation(),
                1,
                viaje,
                1,
                GoalMovementKind.Contribution,
                transactionPesos(1),
                today,
                null
            )
        )

        assertEquals(Rejected(CommandRejection.VersionConflict(current = 2)), outcome)
    }

    // ---- Corrections and undo ----

    @Test
    fun mt13ACorrectionInPlaceUnearnsWhatOnlyTheMistakeReachedAndCelebratesNothing() = runBlocking {
        started()
        aporte(700_000)
        aporte(9_000_000)

        val outcome = submit(
            EditGoalMovement(
                ledger.operation(),
                1,
                viaje,
                version(),
                movement(103),
                1,
                transactionPesos(900_000),
                today,
                Note.of("Prima").valueOrFail()
            )
        )

        assertNull(committed(outcome).change)
        val progress = summary().progress
        assertEquals(Money.cop(1_600_000), progress.saved)
        assertEquals(PlaceState.Empty, progress.state(GoalPlace.ThreeQuarters))
        assertEquals(PlaceState.Empty, progress.state(GoalPlace.Meta))
        assertEquals("2|${uuid(103)}|contribution|900000|2", events().last())
    }

    @Test
    fun mt15DeletingTheAporteAWithdrawalUsesIsRefused() = runBlocking {
        started()
        aporte(500_000)
        retiro(300_000)
        val before = events()

        val outcome = delete(102)

        assertEquals(
            Rejected(Specific(GoalRejection.AboveSaved(movement(103), Money.cop(0)))),
            outcome
        )
        assertEquals(before, events())
    }

    @Test
    fun mt14ADeletedAporteComesBackWithinSixSecondsAndNotAfter() = runBlocking {
        started()
        aporte(700_000)
        aporte(900_000)
        val withIt = summary().progress
        ledger.elapsed = 7.hours
        val deletion = ledger.operation()

        delete(103, deletion)
        assertEquals(Money.cop(700_000), summary().progress.saved)
        assertEquals(deletion, ledger.goals.movementUndoOffer()?.operationId)
        assertEquals(1, ledger.rows("deleted_goal_event", "deletion_id").size)

        ledger.elapsed = 7.hours + 5999.milliseconds
        val restored = submit(RestoreGoalMovement(ledger.operation(), 1, deletion))

        assertTrue("$restored", restored is Committed)
        assertEquals(withIt.saved, summary().progress.saved)
        assertEquals(withIt.places, summary().progress.places)
        assertEquals(0, ledger.rows("deleted_goal_event", "deletion_id").size)

        val again = ledger.operation()
        delete(103, again)
        ledger.elapsed += 6.seconds
        assertEquals(
            Rejected(Specific(GoalRejection.UndoExpired)),
            submit(RestoreGoalMovement(ledger.operation(), 1, again))
        )
    }

    @Test
    fun deletingAMovementMakesAnEarlierIncomeDeletionFinal() = runBlocking {
        started()
        aporte(500_000)
        ledger.incomes.create(
            CreateIncome(
                ledger.operation(),
                1,
                transactionPesos(150_000),
                IncomeSource.of("salary", null).valueOrFail(),
                today,
                null
            )
        )
        val income = com.software.sello.domain.model.IncomeId.of(uuid(103)).valueOrFail()
        ledger.incomes.delete(DeleteIncome(ledger.operation(), 1, income, 1))
        assertTrue(ledger.incomes.undoOffer() != null)

        delete(102)

        assertNull(ledger.incomes.undoOffer())
        assertEquals(0, ledger.rows("deleted_income", "deletion_id").size)
        assertTrue(ledger.goals.movementUndoOffer() != null)
    }

    // ---- Targets ----

    @Test
    fun mt18LoweringTheTargetEarnsTheNewlyReachedStamp() = runBlocking {
        started()
        aporte(1_710_000)

        val outcome =
            submit(
                ChangeGoalTarget(
                    ledger.operation(),
                    1,
                    viaje,
                    version(),
                    transactionPesos(2_000_000)
                )
            )

        assertEquals(listOf(GoalPlace.ThreeQuarters), committed(outcome).change?.earned)
        assertEquals(85L, summary().progress.percentage)
        assertEquals("2|${uuid(103)}|target|2000000|1", events().last())
    }

    @Test
    fun mt17RaisingTheTargetKeepsTheOldStampFaded() = runBlocking {
        started()
        aporte(1_710_000)

        submit(
            ChangeGoalTarget(ledger.operation(), 1, viaje, version(), transactionPesos(4_000_000))
        )

        val progress = summary().progress
        assertEquals(42L, progress.percentage)
        assertEquals(PlaceState.Kept(today), progress.state(GoalPlace.Half))
    }

    @Test
    fun editingNameIconAndMonthChangesNoFigureAndTheSameValuesAreUnchanged() = runBlocking {
        started()
        aporte(750_000)
        val before = summary().progress

        val renamed = submit(
            EditGoal(
                ledger.operation(),
                1,
                viaje,
                version(),
                GoalName.of("Costa").valueOrFail(),
                IconKey.of("beach").valueOrFail(),
                null
            )
        )
        val same = submit(
            EditGoal(
                ledger.operation(),
                1,
                viaje,
                version(),
                GoalName.of("Costa").valueOrFail(),
                IconKey.of("beach").valueOrFail(),
                null
            )
        )

        assertTrue(renamed is Committed)
        assertEquals(Rejected(Specific(GoalRejection.Unchanged)), same)
        assertEquals(before, summary().progress)
        assertEquals("Costa", summary().history.goal.name.value)
    }

    // ---- Completion ----

    @Test
    fun mt23CompletingEarnsMetaAndMakesTheGoalReadOnly() = runBlocking {
        started()
        aporte(3_060_000)
        ledger.elapsed = 7.hours
        val completion = ledger.operation()

        val outcome = submit(CompleteGoal(completion, 1, viaje, version(), 10.seconds))

        assertEquals(listOf(GoalPlace.Meta), committed(outcome).change?.earned)
        assertTrue(summary().progress.completed)
        assertEquals(today.toString(), ledger.rows("goal", "completed_on").single())
        assertEquals(Rejected(Specific(GoalRejection.Completed)), aporte(1))
        assertEquals(completion, ledger.goals.completionUndoOffer()?.operationId)
    }

    @Test
    fun mt24DeshacerWithinItsTimeReturnsTheGoalToWaitingAndNotAfter() = runBlocking {
        started()
        aporte(3_060_000)
        ledger.elapsed = 7.hours
        val completion = ledger.operation()
        submit(CompleteGoal(completion, 1, viaje, version(), 10.seconds))

        ledger.elapsed = 7.hours + 9999.milliseconds
        val undone = submit(UndoGoalCompletion(ledger.operation(), 1, completion))

        assertTrue("$undone", undone is Committed)
        assertEquals(PlaceState.Ready, summary().progress.state(GoalPlace.Meta))
        assertNull(ledger.goals.completionUndoOffer())

        val second = ledger.operation()
        submit(CompleteGoal(second, 1, viaje, version(), 10.seconds))
        ledger.elapsed += 10.seconds
        assertEquals(
            Rejected(Specific(GoalRejection.UndoExpired)),
            submit(UndoGoalCompletion(ledger.operation(), 1, second))
        )
        assertTrue(summary().progress.completed)
    }

    @Test
    fun mt25ACompletionIsFinalInANewProcess() = runBlocking {
        started()
        aporte(3_060_000)
        val completion = ledger.operation()
        submit(CompleteGoal(completion, 1, viaje, version(), 10.seconds))

        ledger.reopen()

        assertNull(ledger.goals.completionUndoOffer())
        assertEquals(
            Rejected(Specific(GoalRejection.UndoExpired)),
            submit(UndoGoalCompletion(ledger.operation(), 1, completion))
        )
    }

    @Test
    fun mt26MarkingAchievedBelowAHundredLightsAllFourStamps() = runBlocking {
        started()
        aporte(1_710_000)

        submit(CompleteGoal(ledger.operation(), 1, viaje, version(), 10.seconds))

        assertTrue(summary().progress.places.all { it.state is PlaceState.Earned })
    }

    // ---- Deleting and Recibo's goal ----

    @Test
    fun mt27DeletingAGoalRemovesItsMovementsAndItsChoiceForRecibo() = runBlocking {
        started()
        aporte(500_000)
        aporte(500_000)
        submit(FeatureGoal(ledger.operation(), 1, viaje))

        val outcome = submit(DeleteGoal(ledger.operation(), 1, viaje, version()))

        assertTrue("$outcome", outcome is Committed)
        assertEquals(emptyList<String>(), ledger.rows("goal", "id"))
        assertEquals(emptyList<String>(), events())
        assertEquals(emptyList<String>(), ledger.rows("featured_goal", "goal_id"))
        val board = (ledger.goalReads.board() as Outcome.Success).value
        assertEquals(emptyList<GoalSummary>(), board.goals)
        assertNull(board.featured)
    }

    @Test
    fun mt28To30TheChoiceWinsUntilThatGoalIsCompleted() = runBlocking {
        started()
        aporte(1_710_000)
        ledger.audit = ledger.audit.plusSeconds(1)
        assertTrue(submit(create(1_200_000, "Celular nuevo")) is Committed)
        val celular = GoalId.of(uuid(103)).valueOrFail()
        aporte(450_000, celular)
        fun board() = runBlocking { (ledger.goalReads.board() as Outcome.Success).value }

        assertEquals(viaje, board().featured)
        submit(FeatureGoal(ledger.operation(), 1, celular))
        assertEquals(celular, board().featured)
        submit(CompleteGoal(ledger.operation(), 1, celular, version(celular), 10.seconds))
        assertEquals(viaje, board().featured)
    }

    @Test
    fun aCallerCancelledWhileAnAporteIsSavingIsToldSoAndCanRecoverIt() = runBlocking {
        started()
        val command = RecordGoalMovement(
            ledger.operation(),
            1,
            viaje,
            version(),
            GoalMovementKind.Contribution,
            transactionPesos(750_000),
            today,
            null
        )
        lateinit var caller: Job
        var returned: GoalCommandOutcome? = null
        var cancellation: CancellationException? = null
        ledger.whenAWriteIsStamped = { caller.cancel() }

        caller = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                returned = ledger.goals.submit(command)
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        ledger.whenAWriteIsStamped = {}

        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(Money.cop(750_000), summary().progress.saved)
        val found = (ledger.goals.find(command.operationId) as Outcome.Success).value!!
        assertNull(found.movementIds)
        // The replay of a single movement is not a batch, so it names no lines.
        assertEquals(
            Committed(found.copy(movementIds = emptyList()), replayed = true),
            ledger.goals.submit(command)
        )
        assertEquals(Money.cop(750_000), summary().progress.saved)
    }
}
