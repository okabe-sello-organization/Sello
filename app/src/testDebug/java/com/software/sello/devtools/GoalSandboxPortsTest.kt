package com.software.sello.devtools

import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.GoalCommandOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goal writes in the debug build go through the same fence as every other financial
 * write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalSandboxPortsTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private val ledger = Ledger()
    private val fence = WriteFence()
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

        assertFalse((first as CommandOutcome.Committed).replayed)
        assertTrue((replay as CommandOutcome.Committed).replayed)
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
}
