package com.software.sello.devtools

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CommittedExpense
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.CommittedIncome
import com.software.sello.domain.port.CommittedSchedule
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreIncome
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.ScheduleRejection
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A ledger that keeps each operation once, as the real commands do: the same operation
 * again answers with the first result and writes nothing. [answer] can make the next
 * write be refused, or commit but report an unknown outcome.
 */
class Ledger {
    enum class Next { Commit, Refuse, CommitButUnknown }

    var answer = Next.Commit
    var revision = 0L
    val writes = mutableListOf<String>()
    val receipts = mutableMapOf<String, Pair<OperationReceipt, String>>()
    val dates = mutableMapOf<String, LocalDate>()

    private fun id() = UUID.randomUUID().toString()

    /** Null when refused; else the receipt, the record's id and whether it was a replay. */
    fun write(
        operation: OperationId,
        kind: OperationKind,
        date: LocalDate?
    ): Triple<OperationReceipt, String, Boolean>? {
        receipts[operation.value]?.let { return Triple(it.first, it.second, true) }
        val now = answer
        answer = Next.Commit
        if (now == Next.Refuse) return null
        val receipt = OperationReceipt(operation, kind, 1, ++revision, Instant.EPOCH)
        receipts[operation.value] = receipt to id()
        date?.let { dates[operation.value] = it }
        writes += operation.value
        return if (now == Next.CommitButUnknown) {
            Triple(receipt, "", false).also { throw Unknown }
        } else {
            Triple(receipt, receipts.getValue(operation.value).second, false)
        }
    }

    object Unknown : RuntimeException() {
        private fun readResolve(): Any = Unknown
    }
}

private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

class LedgerCategories(private val ledger: Ledger) : CategoryCommands {
    override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome = try {
        ledger.write(
            command.operationId,
            OperationKind.CreateCategory,
            null
        )?.let { (receipt, id, replayed) ->
            CategoryCommandOutcome.Committed(
                CommittedCategoryChange(receipt, CategoryId.of(id).value()),
                replayed
            )
        }
            ?: CategoryCommandOutcome.Rejected(
                com.software.sello.domain.port.CategoryRejection.NameTaken
            )
    } catch (_: Ledger.Unknown) {
        CategoryCommandOutcome.OutcomeUnknown(command.operationId)
    }

    override suspend fun find(operationId: OperationId) = Outcome.Success(
        ledger.receipts[operationId.value]?.let { (receipt, id) ->
            CommittedCategoryChange(receipt, CategoryId.of(id).value())
        }
    )
}

class LedgerExpenses(private val ledger: Ledger) : ExpenseCommands {
    override suspend fun create(command: CreateExpense): ExpenseCommandOutcome = try {
        ledger.write(
            command.operationId,
            OperationKind.CreateExpense,
            command.date
        )?.let { (receipt, id, replayed) ->
            ExpenseCommandOutcome.Committed(
                CommittedExpense(receipt, ExpenseId.of(id).value()),
                replayed
            )
        } ?: ExpenseCommandOutcome.Rejected(ExpenseRejection.CategoryMissing)
    } catch (_: Ledger.Unknown) {
        ExpenseCommandOutcome.OutcomeUnknown(command.operationId)
    }

    override suspend fun find(operationId: OperationId) = Outcome.Success(
        ledger.receipts[operationId.value]?.let { (receipt, id) ->
            CommittedExpense(receipt, ExpenseId.of(id).value())
        }
    )

    override suspend fun edit(command: EditExpense) = error("unused")

    override suspend fun delete(command: DeleteExpense) = error("unused")

    override suspend fun restore(command: RestoreExpense) = error("unused")

    override fun undoOffer() = null
}

class LedgerIncomes(private val ledger: Ledger) : IncomeCommands {
    override suspend fun create(command: CreateIncome): IncomeCommandOutcome = try {
        ledger.write(
            command.operationId,
            OperationKind.CreateIncome,
            command.date
        )?.let { (receipt, id, replayed) ->
            IncomeCommandOutcome.Committed(
                CommittedIncome(receipt, IncomeId.of(id).value()),
                replayed
            )
        } ?: error("unused")
    } catch (_: Ledger.Unknown) {
        IncomeCommandOutcome.OutcomeUnknown(command.operationId)
    }

    override suspend fun find(operationId: OperationId) = Outcome.Success(
        ledger.receipts[operationId.value]?.let { (receipt, id) ->
            CommittedIncome(receipt, IncomeId.of(id).value())
        }
    )

    override suspend fun edit(command: EditIncome) = error("unused")

    override suspend fun delete(command: DeleteIncome) = error("unused")

    override suspend fun restore(command: RestoreIncome) = error("unused")

    override fun undoOffer() = null
}

/** Goal commands on the [ledger]; every goal command's record is the goal it names or makes. */
class LedgerGoals(private val ledger: Ledger) : GoalCommands {
    val sent = mutableListOf<GoalCommand>()

    override suspend fun submit(command: GoalCommand): GoalCommandOutcome = try {
        sent += command
        ledger.write(command.operationId, OperationKind.CreateGoal, null)
            ?.let { (receipt, id, replayed) ->
                GoalCommandOutcome.Committed(
                    CommittedGoal(receipt, GoalId.of(id).value(), null),
                    replayed
                )
            } ?: GoalCommandOutcome.Rejected(GoalRejection.VersionConflict(0))
    } catch (_: Ledger.Unknown) {
        GoalCommandOutcome.OutcomeUnknown(command.operationId)
    }

    override suspend fun find(operationId: OperationId) = Outcome.Success(
        ledger.receipts[operationId.value]?.let { (receipt, id) ->
            CommittedGoal(receipt, GoalId.of(id).value(), null)
        }
    )

    override fun movementUndoOffer() = null

    override fun completionUndoOffer() = null
}

/** Schedule commands on the [ledger]; each record is the schedule the command names or makes. */
class LedgerSchedules(private val ledger: Ledger) : ScheduleCommands {
    val sent = mutableListOf<ScheduleCommand>()

    override suspend fun submit(command: ScheduleCommand): ScheduleCommandOutcome = try {
        sent += command
        ledger.write(command.operationId, OperationKind.PauseSchedule, null)
            ?.let { (receipt, id, replayed) ->
                ScheduleCommandOutcome.Committed(
                    CommittedSchedule(receipt, ScheduleId.of(id).value()),
                    replayed
                )
            } ?: ScheduleCommandOutcome.Rejected(ScheduleRejection.Unchanged)
    } catch (_: Ledger.Unknown) {
        ScheduleCommandOutcome.OutcomeUnknown(command.operationId)
    }

    override suspend fun find(operationId: OperationId) = Outcome.Success(
        ledger.receipts[operationId.value]?.let { (receipt, id) ->
            CommittedSchedule(receipt, ScheduleId.of(id).value())
        }
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioRunnerTest {
    // G01's writes without its final check, which reads real snapshots on device.
    private val scenario = Scenarios.G01.copy(steps = Scenarios.G01.steps.dropLast(1))
    private val ledger = Ledger()
    private val store = InMemorySessionStore(
        SandboxSession(active = true, date = scenario.start, scenario = scenario.key)
    )

    /** A process: a clock on the stored date and a runner on the stored progress. */
    private fun TestScope.process(): ScenarioRunner {
        val time = SandboxTime(
            ControlledFinancialClock(FinancialDay(store.session.date!!, scenario.zone)),
            store,
            backgroundScope
        )
        return ScenarioRunner(
            scenario,
            1,
            time,
            LedgerCategories(ledger),
            LedgerExpenses(ledger),
            LedgerIncomes(ledger),
            LedgerGoals(ledger),
            NoSnapshots,
            NoSnapshots,
            NoGoalReads,
            noSchedules(time.clock),
            store,
            backgroundScope
        )
    }

    @Test
    fun aFullRunWritesEachStepOnceOnItsDayAndRecordsEachReceipt() = runTest {
        val runner = process()
        while (runner.step()) Unit

        assertTrue(runner.state.value.finished)
        assertEquals(5, ledger.writes.size)
        assertEquals(
            listOf(1, 2, 3).map { LocalDate.of(2026, 10, it) },
            ledger.dates.filterKeys { it != scenario.operation(6) }.values.toList()
        )
        assertEquals(LocalDate.of(2026, 10, 3), ledger.dates[scenario.operation(6)])
        assertEquals(7, store.session.nextStep)
        assertEquals(
            listOf(1L, 2L, null, 3L, null, 4L, 5L),
            runner.state.value.log.map {
                it.revision
            }
        )
    }

    @Test
    fun aStepThatCommittedBeforeItsCheckpointIsNotWrittenAgainInTheNextProcess() = runTest {
        val first = process()
        repeat(3) { first.step() }
        // Step 4 commits, then the process ends before its checkpoint is written.
        ledger.answer = Ledger.Next.Commit
        first.step()
        store.write(store.session.copy(nextStep = 3))
        assertEquals(3, ledger.writes.size)

        val second = process()
        while (second.step()) Unit

        assertEquals(5, ledger.writes.size)
        assertEquals("Ya estaba guardado", second.state.value.log.first { it.index == 3 }.result)
        assertEquals(
            scenario.steps.indices.filter {
                it != 2 && it != 4
            }.map(scenario::operation),
            ledger.writes
        )
    }

    @Test
    fun anUnknownOutcomeStopsTheRunAndTheNextTryUsesTheSameOperation() = runTest {
        val runner = process()
        runner.step()
        ledger.answer = Ledger.Next.CommitButUnknown

        assertFalse(runner.step())
        assertEquals(1, runner.state.value.next)
        assertTrue(runner.state.value.problem!!.startsWith("No se sabe"))
        assertEquals(1, store.session.nextStep)

        assertTrue(runner.step())
        assertEquals(2, runner.state.value.next)
        assertNull(runner.state.value.problem)
        assertEquals("Ya estaba guardado", runner.state.value.log.last().result)
        assertEquals(2, ledger.writes.size)
    }

    @Test
    fun aRefusalStopsPlayingAndLeavesTheStepToTry() = runTest {
        val runner = process()
        runner.step()
        ledger.answer = Ledger.Next.Refuse

        runner.play()
        advanceTimeBy(60_000)

        assertFalse(runner.state.value.playing)
        assertEquals(1, runner.state.value.next)
        assertTrue(runner.state.value.problem!!.startsWith("Rechazado"))
    }

    @Test
    fun playingTakesOneStepEveryTwoSecondsAndPauseKeepsTheProgress() = runTest {
        val runner = process()

        runner.play()
        runCurrent()
        advanceTimeBy(4_001)
        assertEquals(2, runner.state.value.next)
        runner.pause()
        advanceTimeBy(60_000)

        assertEquals(2, runner.state.value.next)
        assertFalse(runner.state.value.playing)
        assertEquals(2, store.session.nextStep)
    }

    @Test
    fun aNewProcessListsTheStepsAlreadyConfirmedFromTheirReceipts() = runTest {
        val first = process()
        repeat(4) { first.step() }

        val second = process()
        second.recover()
        runCurrent()

        assertEquals(
            first.state.value.log.map {
                it.revision
            },
            second.state.value.log.map { it.revision }
        )
        assertEquals(4, second.state.value.next)
    }

    @Test
    fun aStepTakenBeforeTheNewProcessHasListedItsHistoryKeepsItsOwnResult() = runTest {
        val first = process()
        repeat(4) { first.step() }
        // Step 4 committed, but the process ended before its checkpoint.
        store.session = store.session.copy(nextStep = 3)

        val second = process()
        second.recover()
        second.step()
        runCurrent()

        assertEquals(listOf(0, 1, 2, 3), second.state.value.log.map { it.index })
        assertEquals("Ya estaba guardado", second.state.value.log.last().result)
    }
}

/** Snapshot ports the host runner tests never read. */
object NoSnapshots :
    com.software.sello.domain.port.MonthlySnapshots,
    com.software.sello.domain.port.ResumenSnapshots {
    override suspend fun read(month: java.time.YearMonth): Nothing = error("unused")

    override fun observe(month: java.time.YearMonth): Nothing = error("unused")
}

object NoGoalReads : com.software.sello.domain.port.GoalReads {
    override suspend fun board(): Nothing = error("unused")

    override fun observeBoard(): Nothing = error("unused")

    override suspend fun goal(id: GoalId): Nothing = error("unused")
}
