package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.Aporte
import com.software.sello.devtools.ScenarioStep.ChooseForRecibo
import com.software.sello.devtools.ScenarioStep.Goal
import com.software.sello.devtools.ScenarioStep.GoalTarget
import com.software.sello.devtools.ScenarioStep.MarkLograda
import com.software.sello.devtools.ScenarioStep.Repeat
import com.software.sello.devtools.ScenarioStep.Retiro
import com.software.sello.devtools.ScenarioStep.UndoLograda
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.Money
import com.software.sello.domain.port.ChangeGoalTarget
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.UndoGoalCompletion
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goal steps send the app's own goal commands: to the goal their creation step made, at
 * the version the step was written for, on the sandbox's day; Deshacer names the
 * completion it undoes, and a repeated step sends the same operation again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalScenarioRunnerTest {
    private val start = LocalDate.of(2026, 10, 23)
    private val scenario = Scenario(
        id = "metas-prueba",
        version = 1,
        seed = 1,
        zone = ZoneId.of("America/Bogota"),
        start = start,
        title = "Metas",
        summary = "",
        steps = listOf(
            Goal("viaje", "Viaje a la costa", "flight", 3_000_000),
            Aporte("viaje", 749_999, 1),
            Repeat(1),
            GoalTarget("viaje", 2_000_000, 2),
            MarkLograda("viaje", 3),
            UndoLograda(4),
            ChooseForRecibo("viaje"),
            Retiro("viaje", 100_000, 5),
            MarkLograda("viaje", 6, undoFor = Duration.ZERO)
        )
    )
    private val ledger = Ledger()
    private val goals = LedgerGoals(ledger)
    private val store = InMemorySessionStore(
        SandboxSession(active = true, date = start, scenario = scenario.key)
    )

    private val clock = ControlledFinancialClock(FinancialDay(start, scenario.zone))

    private fun TestScope.runner() = ScenarioRunner(
        scenario,
        7,
        SandboxTime(clock, store, backgroundScope),
        LedgerCategories(ledger),
        LedgerExpenses(ledger),
        LedgerIncomes(ledger),
        goals,
        NoSnapshots,
        NoSnapshots,
        NoGoalReads,
        noSchedules(clock),
        noInvestments(),
        store,
        backgroundScope
    )

    @Test
    fun goalStepsSendTheirOwnCommandsToTheGoalTheScenarioMade() = runTest {
        val runner = runner()
        while (runner.step()) Unit

        assertTrue(runner.state.value.problem, runner.state.value.finished)
        val sent = goals.sent
        val create = sent[0] as CreateGoal
        assertEquals(
            listOf("Viaje a la costa", "flight", 3_000_000L, null),
            listOf(
                create.name.value,
                create.icon.value,
                create.target.money.minorUnits,
                create.targetMonth
            )
        )
        val made = ledger.receipts.getValue(scenario.operation(0)).second
        val aporte = sent[1] as RecordGoalMovement
        assertEquals(
            listOf(made, 1L, GoalMovementKind.Contribution, Money.cop(749_999), start, 7L),
            listOf(
                aporte.goalId.value,
                aporte.expectedVersion,
                aporte.kind,
                aporte.amount.money,
                aporte.date,
                aporte.generation
            )
        )
        // The repeat is the same operation and input: storage answers with its receipt.
        assertEquals(aporte, sent[2])
        val target = sent[3] as ChangeGoalTarget
        assertEquals(made to 2L, target.goalId.value to target.expectedVersion)
        assertEquals(Money.cop(2_000_000), target.target.money)
        val complete = sent[4] as CompleteGoal
        assertEquals(made to 3L, complete.goalId.value to complete.expectedVersion)
        assertEquals(
            scenario.operation(4),
            (sent[5] as UndoGoalCompletion).completionId.value
        )
        assertEquals(made, (sent[6] as FeatureGoal).goalId.value)
        val retiro = sent[7] as RecordGoalMovement
        assertEquals(
            listOf(GoalMovementKind.Withdrawal, 5L, Money.cop(100_000)),
            listOf(retiro.kind, retiro.expectedVersion, retiro.amount.money)
        )
        // The longest window the app offers by default; a step may ask for none at all.
        assertEquals(34.seconds, complete.undoFor)
        assertEquals(Duration.ZERO, (sent[8] as CompleteGoal).undoFor)
        assertEquals(9, sent.size)
        assertEquals(8, ledger.writes.size)
    }
}
