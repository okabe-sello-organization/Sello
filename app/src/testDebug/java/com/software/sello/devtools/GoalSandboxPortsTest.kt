package com.software.sello.devtools

import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goal writes in the debug build go through the same fence, faults and log as every
 * other financial write, and the sandbox's read failure reaches the goal board.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalSandboxPortsTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private val ledger = Ledger()
    private val fence = WriteFence()
    private val faults = DebugFaults()
    private val log = OperationLog()
    private var saved = 0

    private fun feature(n: Int) = FeatureGoal(
        OperationId.of("00000000-0000-4000-8000-00000000000$n").value(),
        1,
        GoalId.of("00000000-0000-4000-9000-000000000001").value()
    )

    @Test
    fun aNewGoalChangeIsReportedAndAReplayOrARejectionIsNot() = runTest {
        val commands = FencedGoalCommands(LedgerGoals(ledger), fence) { saved++ }

        val first = commands.submit(feature(1))
        val replay = commands.submit(feature(1))
        ledger.answer = Ledger.Next.Refuse
        commands.submit(feature(2))

        assertFalse((first as GoalCommandOutcome.Committed).replayed)
        assertTrue((replay as GoalCommandOutcome.Committed).replayed)
        assertEquals(1, saved)
    }

    @Test
    fun aGoalCommandAfterTheFenceClosedNeverReachesStorage() = runTest {
        val goals = LedgerGoals(ledger)
        val commands = FencedGoalCommands(goals, fence) { saved++ }
        fence.close()

        val late = launch { commands.submit(feature(3)) }
        runCurrent()

        assertTrue(goals.sent.isEmpty())
        assertFalse(late.isCompleted)
        late.cancel()
    }

    @Test
    fun aLostGoalAnswerCommitsOnceIsLoggedAndItsReceiptIsFound() = runTest {
        val goals = LedgerGoals(ledger)
        val commands = FaultyGoalCommands(goals, faults, log)
        val inspector = OperationInspector(
            LedgerExpenses(ledger),
            LedgerIncomes(ledger),
            LedgerCategories(ledger),
            goals,
            LedgerInvestments(ledger),
            LedgerSchedules(ledger)
        )
        faults.arm(Fault.LostAnswer)
        val command = feature(4)

        val told = commands.submit(command)

        assertEquals(GoalCommandOutcome.OutcomeUnknown(command.operationId), told)
        assertEquals(1, ledger.writes.size)
        val logged = log.state.value.single()
        assertEquals("Meta: FeatureGoal" to Fault.LostAnswer, logged.kind to logged.fault)
        assertEquals(StoredOutcome.Saved(1), inspector.stored(logged))
    }

    @Test
    fun aFailureBeforeStorageWritesNoGoalChange() = runTest {
        faults.arm(Fault.FailBeforeStorage)

        val told = FaultyGoalCommands(LedgerGoals(ledger), faults, log).submit(feature(5))

        assertTrue(told is GoalCommandOutcome.OutcomeUnknown)
        assertTrue(ledger.writes.isEmpty())
    }

    private val good = GoalBoard(1, 4, emptyList<GoalSummary>(), null)

    private class Upstream(val state: MutableStateFlow<GoalBoardState>) : GoalReads {
        override suspend fun board() = error("unused")

        override fun observeBoard() = state

        override suspend fun goal(id: GoalId) = error("unused")
    }

    @Test
    fun aGoalReadFailureKeepsTheLastGoodBoardAndARetryReadsAgain() = runTest {
        val reads = FaultyGoalReads(Upstream(MutableStateFlow(GoalBoardState.Ready(good))), faults)
        val seen = mutableListOf<GoalBoardState>()
        val reading = launch { reads.observeBoard().toList(seen) }
        runCurrent()

        faults.arm(Fault.ReadFailure)
        runCurrent()
        reading.cancel()

        val failed = seen.last() as GoalBoardState.Failed
        assertEquals(SimulatedStorageFailure, failed.failure)
        assertSame(good, failed.lastGood)
        assertEquals(GoalBoardState.Ready(good), reads.observeBoard().first())
    }
}
