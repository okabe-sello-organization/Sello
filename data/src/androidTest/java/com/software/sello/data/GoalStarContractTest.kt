package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.ReplaceStar
import com.software.sello.domain.port.StarGoal
import com.software.sello.domain.port.UndoGoalCompletion
import com.software.sello.domain.port.UnstarGoal
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Up to five starred goals for Recibo (SELLO-143), against a real database file through
 * the real commands and reads. Goals are created with no movement, so goal n is record
 * 100 + 2n (its target is the record after it); operations are numbered from 1000.
 */
@RunWith(AndroidJUnit4::class)
class GoalStarContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }

    private fun goal(n: Int): GoalId = GoalId.of(uuid(100 + 2 * n)).valueOrFail()

    private suspend fun submit(command: GoalCommand): GoalCommandOutcome =
        ledger.goals.submit(command)

    /** [count] goals of 1.000.000, each created a second after the one before. */
    private suspend fun goals(count: Int) = repeat(count) { n ->
        ledger.audit = ledger.audit.plusSeconds(1)
        val created = submit(
            CreateGoal(
                ledger.operation(),
                1,
                GoalName.of("Meta $n").valueOrFail(),
                IconKey.of("flight").valueOrFail(),
                transactionPesos(1_000_000),
                null
            )
        )
        assertTrue("$created", created is Committed)
    }

    private suspend fun star(n: Int, operation: OperationId = ledger.operation()) =
        submit(StarGoal(operation, 1, goal(n)))

    private suspend fun version(n: Int) =
        (ledger.goalReads.goal(goal(n)) as Outcome.Success).value!!.history.goal.version

    private suspend fun complete(n: Int, operation: OperationId = ledger.operation()) =
        submit(CompleteGoal(operation, 1, goal(n), version(n), 10.seconds))

    private fun stars() = ledger.rows("starred_goal", "goal_id").sorted()

    private fun starsOf(vararg n: Int) = n.map { uuid(100 + 2 * it) }.sorted()

    private fun receipts() = ledger.rows("operation_receipt", "revision", "kind", "subject_id")

    private suspend fun board(): GoalBoard = ledger.goalReads.board().valueOrFail()

    /** Everything a star command could change, to show that a refusal changed none of it. */
    private fun snapshot() = listOf(
        stars(),
        receipts(),
        listOf(ledger.revision().toString()),
        ledger.rows("goal", "id", "completed_on", "version")
    )

    @Test
    fun upToFiveGoalsAreStarredAndASixthIsRefusedNamingTheLimitWithNothingWritten() = runBlocking {
        goals(6)
        for (n in 0..4) assertTrue(star(n) is Committed)
        val before = snapshot()
        val sixth = ledger.operation()

        val outcome = star(5, sixth)

        assertEquals(Rejected(Specific(GoalRejection.StarLimit(5))), outcome)
        assertEquals(before, snapshot())
        assertEquals(Outcome.Success(null), ledger.goals.find(sixth))
        assertEquals(starsOf(0, 1, 2, 3, 4), stars())
        assertEquals((0..4).map(::goal).toSet(), board().starred)
    }

    @Test
    fun twoStarsSentTogetherAtFourGiveOneStarAndOneRefusalNamingTheLimit() = runBlocking {
        goals(6)
        for (n in 0..3) star(n)
        val second = async(Dispatchers.Default, start = CoroutineStart.LAZY) { star(5) }
        // The second star starts while the first is inside its transaction, past its count.
        ledger.whenAWriteIsStamped = {
            ledger.whenAWriteIsStamped = {}
            second.start()
        }

        val first = async(Dispatchers.Default) { star(4) }.await()
        val other = second.await()

        assertTrue("$first", first is Committed)
        assertEquals(Rejected(Specific(GoalRejection.StarLimit(5))), other)
        assertEquals(starsOf(0, 1, 2, 3, 4), stars())
    }

    @Test
    fun aStarIsOneRevisionAndOneReceiptAndASecondSendIsItsReplay() = runBlocking {
        goals(1)
        val operation = ledger.operation()

        val first = star(0, operation)
        val again = star(0, operation)
        val other = submit(StarGoal(operation, 1, GoalId.of(uuid(999)).valueOrFail()))
        val stale = submit(StarGoal(ledger.operation(), 2, goal(0)))

        val saved = (first as Committed).value
        assertEquals(Committed(saved, replayed = true), again)
        assertEquals(Rejected(CommandRejection.OperationConflict), other)
        assertEquals(Rejected(CommandRejection.StaleGeneration(current = 1)), stale)
        assertEquals(goal(0), saved.goalId)
        // One goal created at revision 1, starred at revision 2.
        assertEquals("2|goal.feature|${uuid(100)}", receipts().last())
        assertEquals(2L, ledger.revision())
        assertEquals(Outcome.Success(saved.copy(movementIds = null)), ledger.goals.find(operation))
    }

    @Test
    fun starringAStarredCompletedOrMissingGoalIsRefusedWithNothingWritten() = runBlocking {
        goals(2)
        star(0)
        complete(1)
        val before = snapshot()

        assertEquals(Rejected(Specific(GoalRejection.Unchanged)), star(0))
        assertEquals(Rejected(Specific(GoalRejection.Completed)), star(1))
        assertEquals(
            Rejected(Specific(GoalRejection.GoalMissing)),
            submit(StarGoal(ledger.operation(), 1, GoalId.of(uuid(999)).valueOrFail()))
        )
        assertEquals(before, snapshot())
    }

    @Test
    fun unstarringRemovesOneStarAndAGoalNotStarredIsUnchanged() = runBlocking {
        goals(2)
        star(0)
        star(1)
        val operation = ledger.operation()

        val outcome = submit(UnstarGoal(operation, 1, goal(0)))

        assertTrue("$outcome", outcome is Committed)
        assertEquals(starsOf(1), stars())
        // Two goals, two stars, then this: revision 5.
        assertEquals("5|goal.unstar|${uuid(100)}", receipts().last())
        assertEquals(
            Committed((outcome as Committed).value, replayed = true),
            submit(UnstarGoal(operation, 1, goal(0)))
        )
        val before = snapshot()
        assertEquals(
            Rejected(Specific(GoalRejection.Unchanged)),
            submit(UnstarGoal(ledger.operation(), 1, goal(0)))
        )
        assertEquals(before, snapshot())
    }

    @Test
    fun replacingMovesOneStarToAnotherGoalInOneRevisionAndOneReceipt() = runBlocking {
        goals(6)
        for (n in 0..4) star(n)
        val operation = ledger.operation()
        val command = ReplaceStar(operation, 1, goal(2), goal(5))

        val outcome = submit(command)

        val saved = (outcome as Committed).value
        assertEquals(goal(5), saved.goalId)
        assertEquals(starsOf(0, 1, 3, 4, 5), stars())
        // Six goals and five stars are revisions 1 to 11; the replacement is 12, alone.
        assertEquals(12L, ledger.revision())
        assertEquals("12|goal.replace_star|${uuid(110)}", receipts().last())
        assertEquals(Committed(saved, replayed = true), submit(command))
        assertEquals(
            Rejected(CommandRejection.OperationConflict),
            submit(ReplaceStar(operation, 1, goal(3), goal(5)))
        )
        assertEquals(starsOf(0, 1, 3, 4, 5), stars())
        assertEquals(Outcome.Success(saved.copy(movementIds = null)), ledger.goals.find(operation))
        assertEquals(listOf(0, 1, 3, 4, 5).map(::goal).toSet(), board().starred)
    }

    @Test
    fun aReplacementIsRefusedWithNothingWrittenWhenEitherGoalNoLongerFits() = runBlocking {
        goals(5)
        star(0)
        star(1)
        complete(2)
        submit(DeleteGoal(ledger.operation(), 1, goal(3), version(3)))
        val before = snapshot()

        fun replace(unstar: Int, star: Int) = runBlocking {
            submit(ReplaceStar(ledger.operation(), 1, goal(unstar), goal(star)))
        }

        // The goal to unstar is not starred, or no longer exists.
        assertEquals(Rejected(Specific(GoalRejection.NotStarred)), replace(2, 4))
        assertEquals(Rejected(Specific(GoalRejection.NotStarred)), replace(3, 4))
        // The goal to star is already starred, completed or deleted.
        assertEquals(Rejected(Specific(GoalRejection.Unchanged)), replace(0, 1))
        assertEquals(Rejected(Specific(GoalRejection.Unchanged)), replace(0, 0))
        assertEquals(Rejected(Specific(GoalRejection.Completed)), replace(0, 2))
        assertEquals(Rejected(Specific(GoalRejection.GoalMissing)), replace(0, 3))
        assertEquals(before, snapshot())
    }

    @Test
    fun aFailureAfterTheOldStarWasRemovedPutsItBackAndWritesNothing() = runBlocking {
        goals(2)
        star(0)
        // The database itself refuses the new star, after the old one was deleted.
        ledger.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_new_star BEFORE INSERT ON starred_goal " +
                "WHEN NEW.goal_id = '${uuid(102)}' BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val before = snapshot()
        val command = ReplaceStar(ledger.operation(), 1, goal(0), goal(1))

        val outcome = submit(command)

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Unavailable("SQLiteConstraintException")
                )
            ),
            outcome
        )
        assertEquals(before, snapshot())
        assertEquals(starsOf(0), stars())
        assertEquals(Outcome.Success(null), ledger.goals.find(command.operationId))
        ledger.database.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_new_star")
    }

    @Test
    fun aCallerCancelledWhileAReplacementIsSavingIsToldSoAndCanRecoverIt() = runBlocking {
        goals(2)
        star(0)
        val command = ReplaceStar(ledger.operation(), 1, goal(0), goal(1))
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
        // The write went on to its end: both stars moved, once.
        assertEquals(starsOf(1), stars())
        val found = (ledger.goals.find(command.operationId) as Outcome.Success).value
        assertEquals(goal(1), found?.goalId)
        assertEquals(
            Committed(found!!.copy(movementIds = emptyList()), replayed = true),
            submit(command)
        )
    }

    @Test
    fun starsAndTheirReceiptsOutliveARestart() = runBlocking {
        goals(3)
        star(0)
        val operation = ledger.operation()
        val replaced = submit(ReplaceStar(operation, 1, goal(0), goal(2)))

        ledger.reopen()

        assertEquals(starsOf(2), stars())
        assertEquals(setOf(goal(2)), board().starred)
        assertEquals(
            Committed((replaced as Committed).value, replayed = true),
            submit(ReplaceStar(operation, 1, goal(0), goal(2)))
        )
    }

    // ---- Completion and Deshacer ----

    @Test
    fun completingAStarredGoalTakesItsStarAndDeshacerGivesItBack() = runBlocking {
        goals(2)
        star(0)
        star(1)
        val completion = ledger.operation()

        assertTrue(complete(0, completion) is Committed)
        assertEquals(starsOf(1), stars())
        assertEquals(setOf(goal(1)), board().starred)
        assertEquals(listOf(goal(1)), board().reciboGoals)

        val undone = submit(UndoGoalCompletion(ledger.operation(), 1, completion))

        assertTrue("$undone", undone is Committed)
        assertEquals(starsOf(0, 1), stars())
        assertEquals(setOf(goal(0), goal(1)), board().starred)
    }

    /** Makes the database refuse the receipt of [kind], the last write of its command. */
    private fun refuseReceipt(kind: String) = ledger.database.openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER refuse_receipt BEFORE INSERT ON operation_receipt " +
            "WHEN NEW.kind = '$kind' BEGIN SELECT RAISE(ABORT, 'refused'); END"
    )

    private val refused = Rejected(
        CommandRejection.StorageFailed(StorageFailure.Unavailable("SQLiteConstraintException"))
    )

    @Test
    fun aCompletionThatFailsAfterTakingTheStarLeavesTheGoalActiveAndStarred() = runBlocking {
        goals(1)
        star(0)
        refuseReceipt("goal.complete")
        val before = snapshot()
        val completion = ledger.operation()

        assertEquals(refused, complete(0, completion))
        assertEquals(before, snapshot())
        assertEquals(starsOf(0), stars())
        assertNull(ledger.goals.completionUndoOffer())
        assertEquals(Outcome.Success(null), ledger.goals.find(completion))
    }

    @Test
    fun aDeshacerThatFailsAfterGivingTheStarBackLeavesTheGoalCompletedAndUnstarred() = runBlocking {
        goals(1)
        star(0)
        val completion = ledger.operation()
        complete(0, completion)
        refuseReceipt("goal.undo_completion")
        val before = snapshot()
        val undo = ledger.operation()

        assertEquals(refused, submit(UndoGoalCompletion(undo, 1, completion)))
        assertEquals(before, snapshot())
        assertEquals(emptyList<String>(), stars())
        assertEquals(Outcome.Success(null), ledger.goals.find(undo))
    }

    @Test
    fun deshacerWithFiveStarredMeanwhileBringsTheGoalBackUnstarred() = runBlocking {
        goals(6)
        for (n in 0..4) star(n)
        val completion = ledger.operation()
        complete(0, completion)
        assertTrue(star(5) is Committed)

        val undone = submit(UndoGoalCompletion(ledger.operation(), 1, completion))

        assertTrue("$undone", undone is Committed)
        assertNull(board().goals.single { it.history.goal.id == goal(0) }.history.goal.completedOn)
        assertEquals(starsOf(1, 2, 3, 4, 5), stars())
    }

    @Test
    fun deshacerOfAGoalThatWasNotStarredStarsNothing() = runBlocking {
        goals(2)
        star(1)
        val completion = ledger.operation()
        complete(0, completion)

        submit(UndoGoalCompletion(ledger.operation(), 1, completion))

        assertEquals(starsOf(1), stars())
    }

    @Test
    fun deletingAStarredGoalTakesItsStarWithIt() = runBlocking {
        goals(2)
        star(0)
        star(1)

        val outcome = submit(DeleteGoal(ledger.operation(), 1, goal(0), version(0)))

        assertTrue("$outcome", outcome is Committed)
        assertEquals(starsOf(1), stars())
        assertEquals(listOf(goal(1)), board().reciboGoals)
    }

    // ---- Reading ----

    @Test
    fun recibosListPutsStarsFirstAndTopsUpToFive() = runBlocking {
        goals(7)
        // Completion 10 % to 70 %: goal n holds (n + 1) × 100.000 of 1.000.000.
        for (n in 0..6) {
            val aporte = submit(
                RecordGoalMovement(
                    ledger.operation(),
                    1,
                    goal(n),
                    version(n),
                    GoalMovementKind.Contribution,
                    transactionPesos((n + 1) * 100_000L),
                    LocalDate.of(2026, 10, 9),
                    null
                )
            )
            assertTrue("$aporte", aporte is Committed)
        }
        star(0)
        star(1)

        // Starred 20 % and 10 %, then the best of the rest: 70 %, 60 %, 50 %.
        assertEquals(listOf(1, 0, 6, 5, 4).map(::goal), board().reciboGoals)
        assertEquals(setOf(goal(0), goal(1)), board().starred)
    }

    @Test
    fun aStoreDamagedPastTheStarRulesFailsTheReadInsteadOfTrimmingIt() = runBlocking {
        goals(6)
        complete(5)
        val raw = ledger.database.openHelper.writableDatabase

        raw.execSQL("INSERT INTO starred_goal VALUES ('${uuid(110)}')")
        assertEquals(
            Outcome.Failure(StorageFailure.Integrity("starred_goal", uuid(110), "goal_id")),
            ledger.goalReads.board()
        )

        raw.execSQL("DELETE FROM starred_goal")
        for (n in 0..4) raw.execSQL("INSERT INTO starred_goal VALUES ('${uuid(100 + 2 * n)}')")
        assertTrue(ledger.goalReads.board() is Outcome.Success)
        raw.execSQL("UPDATE goal SET completed_on = NULL WHERE id = '${uuid(110)}'")
        raw.execSQL("INSERT INTO starred_goal VALUES ('${uuid(110)}')")
        assertEquals(
            Outcome.Failure(StorageFailure.Integrity("starred_goal", null, "goal_id")),
            ledger.goalReads.board()
        )
    }
}
