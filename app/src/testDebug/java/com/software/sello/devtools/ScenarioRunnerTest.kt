package com.software.sello.devtools

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CommittedExpense
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.CommittedIncome
import com.software.sello.domain.port.CommittedInvestment
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
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentEdit
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreIncome
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.ScheduleRejection
import com.software.sello.domain.port.SetUsdRate
import com.software.sello.domain.usecase.CatchUpNow
import com.software.sello.domain.usecase.CatchUpResult
import com.software.sello.domain.usecase.CatchUpStop
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
            CommandOutcome.Committed(
                CommittedCategoryChange(receipt, CategoryId.of(id).value()),
                replayed
            )
        }
            ?: CommandOutcome.Rejected(
                Specific(com.software.sello.domain.port.CategoryRejection.NameTaken)
            )
    } catch (_: Ledger.Unknown) {
        CommandOutcome.OutcomeUnknown(command.operationId)
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
            CommandOutcome.Committed(
                CommittedExpense(receipt, ExpenseId.of(id).value()),
                replayed
            )
        } ?: CommandOutcome.Rejected(Specific(ExpenseRejection.CategoryMissing))
    } catch (_: Ledger.Unknown) {
        CommandOutcome.OutcomeUnknown(command.operationId)
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
            CommandOutcome.Committed(
                CommittedIncome(receipt, IncomeId.of(id).value()),
                replayed
            )
        } ?: error("unused")
    } catch (_: Ledger.Unknown) {
        CommandOutcome.OutcomeUnknown(command.operationId)
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
                CommandOutcome.Committed(
                    CommittedGoal(receipt, GoalId.of(id).value(), null),
                    replayed
                )
            } ?: CommandOutcome.Rejected(CommandRejection.VersionConflict(0))
    } catch (_: Ledger.Unknown) {
        CommandOutcome.OutcomeUnknown(command.operationId)
    }

    override suspend fun find(operationId: OperationId) = Outcome.Success(
        ledger.receipts[operationId.value]?.let { (receipt, id) ->
            CommittedGoal(receipt, GoalId.of(id).value(), null)
        }
    )

    override fun movementUndoOffer() = null

    override fun completionUndoOffer() = null
}

/**
 * Investment commands on the [ledger]; each record is the investment the command names
 * or makes, and a rate's record is none, as the real receipt has it.
 */
class LedgerInvestments(private val ledger: Ledger) : InvestmentCommands {
    val sent = mutableListOf<InvestmentCommand>()

    /** The answer to the next command instead of the ledger's, once. */
    var refuseNext: InvestmentRejection? = null

    override suspend fun submit(command: InvestmentCommand): InvestmentCommandOutcome = try {
        sent += command
        refuseNext?.let {
            refuseNext = null
            return CommandOutcome.Rejected(Specific(it))
        }
        val subject = (command as? InvestmentEdit)?.investmentId
        try {
            ledger.write(command.operationId, OperationKind.CreateInvestment, null)
        } finally {
            // Even an unknown outcome committed: its receipt names what it changed.
            ledger.receipts[command.operationId.value]?.let { (_, id) ->
                subjects.putIfAbsent(
                    command.operationId.value,
                    if (command is SetUsdRate) null else subject ?: InvestmentId.of(id).value()
                )
            }
        }?.let { (receipt, _, replayed) ->
            CommandOutcome.Committed(
                CommittedInvestment(receipt, subjects[command.operationId.value]),
                replayed
            )
        } ?: CommandOutcome.Rejected(CommandRejection.VersionConflict(0))
    } catch (_: Ledger.Unknown) {
        CommandOutcome.OutcomeUnknown(command.operationId)
    }

    /** What each committed operation changed: the investment it made or named; none for a rate. */
    private val subjects = mutableMapOf<String, InvestmentId?>()

    override suspend fun find(operationId: OperationId) = Outcome.Success(
        ledger.receipts[operationId.value]?.let { (receipt, _) ->
            CommittedInvestment(receipt, subjects[operationId.value])
        }
    )

    override fun movementUndoOffer() = null
}

/** Schedule commands on the [ledger]; each record is the schedule the command names or makes. */
class LedgerSchedules(private val ledger: Ledger) : ScheduleCommands {
    val sent = mutableListOf<ScheduleCommand>()

    override suspend fun submit(command: ScheduleCommand): ScheduleCommandOutcome = try {
        sent += command
        ledger.write(command.operationId, OperationKind.PauseSchedule, null)
            ?.let { (receipt, id, replayed) ->
                CommandOutcome.Committed(
                    CommittedSchedule(receipt, ScheduleId.of(id).value()),
                    replayed
                )
            } ?: CommandOutcome.Rejected(Specific(ScheduleRejection.Unchanged))
    } catch (_: Ledger.Unknown) {
        CommandOutcome.OutcomeUnknown(command.operationId)
    }

    override suspend fun find(operationId: OperationId) = Outcome.Success(
        ledger.receipts[operationId.value]?.let { (receipt, id) ->
            CommittedSchedule(receipt, ScheduleId.of(id).value())
        }
    )
}

/** The app's catch-up as the runner sees it, answering each run with [answer]. */
class ScriptedCatchUpNow(var answer: CatchUpResult = CatchUpResult(emptyList(), null)) :
    CatchUpNow {
    var runs = 0

    override suspend fun run(): CatchUpResult = answer.also { runs++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioRunnerTest {
    private val bogota = ZoneId.of("America/Bogota")

    // A short history on ledgers: each write is kept once, as the real commands keep it.
    private val scenario = Scenario(
        id = "prueba",
        version = 1,
        seed = 1,
        zone = bogota,
        start = LocalDate.of(2026, 10, 1),
        today = LocalDate.of(2026, 10, 3),
        title = "Prueba",
        summary = "",
        steps = listOf(
            ScenarioStep.Category("comida", "Comida", "restaurant", 100_000),
            ScenarioStep.Expense("comida", 10_000),
            ScenarioStep.AdvanceTo(LocalDate.of(2026, 10, 2)),
            ScenarioStep.Expense("comida", 15_000),
            ScenarioStep.CatchUp,
            ScenarioStep.AdvanceTo(LocalDate.of(2026, 10, 3)),
            ScenarioStep.Income(200_000, "salary"),
            ScenarioStep.Expense("comida", 5_000, on = LocalDate.of(2026, 10, 1))
        )
    )
    private val ledger = Ledger()
    private val catchUp = ScriptedCatchUpNow()
    private val store = InMemorySessionStore(
        SandboxSession(
            active = true,
            date = scenario.start,
            scenario = scenario.key,
            loading = true
        )
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
            ScenarioSchedules(LedgerSchedules(ledger), catchUp),
            LedgerInvestments(ledger),
            store,
            backgroundScope
        )
    }

    /** The operations of the steps that write, in order. */
    private fun writes(vararg except: Int) =
        scenario.steps.indices.filter { scenario.steps[it].writes && it !in except }
            .map(scenario::operation)

    @Test
    fun loadingWritesEachStepOnceOnItsDayAndEndsOnTheHistorysLastDay() = runTest {
        val runner = process()
        runner.load().join()

        assertTrue(runner.state.value.finished)
        assertNull(runner.state.value.problem)
        assertEquals(writes(), ledger.writes)
        assertEquals(
            listOf(1, 2, 3, 1).map { LocalDate.of(2026, 10, it) },
            listOf(1, 3, 6, 7).map { ledger.dates[scenario.operation(it)] }
        )
        assertEquals(1, catchUp.runs)
        assertEquals(scenario.today, store.session.date)
        assertEquals(8, store.session.nextStep)
        assertFalse(store.session.loading)
    }

    @Test
    fun aStepThatCommittedBeforeItsCheckpointIsNotWrittenAgainInTheNextProcess() = runTest {
        val first = process()
        repeat(4) { first.step() }
        // Step 4 committed, then the process ended before its checkpoint was written.
        store.write(store.session.copy(nextStep = 3))
        assertEquals(3, ledger.writes.size)

        val second = process()
        second.resume()
        runCurrent()

        assertTrue(second.state.value.finished)
        assertEquals(writes(), ledger.writes)
        assertFalse(store.session.loading)
    }

    @Test
    fun anUnknownOutcomeStopsTheLoadAndTryingAgainUsesTheSameOperation() = runTest {
        val runner = process()
        ledger.answer = Ledger.Next.Refuse
        runner.step()
        ledger.answer = Ledger.Next.CommitButUnknown
        runner.load().join()

        assertEquals(0, runner.state.value.next)
        assertTrue(runner.state.value.problem!!.contains("no se sabe si se guardó"))
        assertEquals(0, store.session.nextStep)
        assertTrue(store.session.loading)

        runner.load().join()

        assertTrue(runner.state.value.finished)
        assertNull(runner.state.value.problem)
        assertEquals(writes(), ledger.writes)
    }

    @Test
    fun aRefusalStopsTheLoadNamingTheStepAndLeavesItIncomplete() = runTest {
        val runner = process()
        runner.step()
        ledger.answer = Ledger.Next.Refuse

        runner.load().join()

        assertEquals(1, runner.state.value.next)
        assertFalse(runner.state.value.finished)
        assertFalse(runner.state.value.loading)
        assertEquals(
            "Paso 2 de 8 (Expense): rechazado: CategoryMissing",
            runner.state.value.problem
        )
        assertTrue(store.session.loading)
    }

    @Test
    fun aFailedCatchUpStopsTheLoadAndTheNextTryRunsItAgain() = runTest {
        val runner = process()
        catchUp.answer = CatchUpResult(emptyList(), CatchUpStop.Unknown)

        runner.load().join()

        assertEquals(4, runner.state.value.next)
        assertTrue(runner.state.value.problem!!.contains("puesta al día"))
        catchUp.answer = CatchUpResult(emptyList(), null)
        runner.load().join()
        assertTrue(runner.state.value.finished)
        assertEquals(2, catchUp.runs)
    }

    @Test
    fun aNewProcessLoadsOnlyWhenALoadWasAskedForAndNotFinished() = runTest {
        store.session = store.session.copy(loading = false)
        val idle = process()
        idle.resume()
        runCurrent()
        assertEquals(0, idle.state.value.next)
        assertTrue(ledger.writes.isEmpty())

        store.session = store.session.copy(loading = true)
        val loading = process()
        loading.resume()
        runCurrent()
        assertTrue(loading.state.value.finished)
    }

    @Test
    fun aLoadIsNotCarriedOnIntoAHistoryTheAppsOwnResetErased() = runTest {
        val first = process()
        repeat(4) { first.step() }
        // The app's own reset erased everything, receipts included.
        ledger.receipts.clear()
        val written = ledger.writes.toList()

        val second = process()
        second.resume()
        runCurrent()

        assertEquals(4, second.state.value.next)
        assertTrue(second.state.value.problem!!.contains("se borró"))
        assertEquals(written, ledger.writes)
    }
}
