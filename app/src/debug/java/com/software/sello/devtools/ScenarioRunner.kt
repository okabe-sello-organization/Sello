package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.AdvanceTo
import com.software.sello.devtools.ScenarioStep.Aporte
import com.software.sello.devtools.ScenarioStep.Archive
import com.software.sello.devtools.ScenarioStep.Category
import com.software.sello.devtools.ScenarioStep.ChooseForRecibo
import com.software.sello.devtools.ScenarioStep.DeleteExpense
import com.software.sello.devtools.ScenarioStep.EditExpense
import com.software.sello.devtools.ScenarioStep.Expense
import com.software.sello.devtools.ScenarioStep.Goal
import com.software.sello.devtools.ScenarioStep.GoalTarget
import com.software.sello.devtools.ScenarioStep.Income
import com.software.sello.devtools.ScenarioStep.MarkLograda
import com.software.sello.devtools.ScenarioStep.Repeat
import com.software.sello.devtools.ScenarioStep.Retiro
import com.software.sello.devtools.ScenarioStep.SetDefaultLimit
import com.software.sello.devtools.ScenarioStep.SetMonthLimit
import com.software.sello.devtools.ScenarioStep.UndoDelete
import com.software.sello.devtools.ScenarioStep.UndoLograda
import com.software.sello.devtools.ScenarioStep.Verify
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PlaceState
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.ChangeGoalTarget
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CreateCategory
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteExpense as DeleteExpenseCommand
import com.software.sello.domain.port.EditExpense as EditExpenseCommand
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.SetDefaultLimit as SetDefaultLimitCommand
import com.software.sello.domain.port.SetMonthLimit as SetMonthLimitCommand
import com.software.sello.domain.port.UndoGoalCompletion
import java.time.YearMonth
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What one step did, as the tools list it. [operation] and [revision] are absent for a date step. */
data class StepRecord(
    val index: Int,
    val action: String,
    val operation: String?,
    val revision: Long?,
    val result: String
)

data class ScenarioState(
    val scenario: Scenario?,
    /** The first step not yet confirmed; it may have committed already. */
    val next: Int,
    val log: List<StepRecord> = emptyList(),
    val playing: Boolean = false,
    val running: Boolean = false,
    /** Why the run stopped short, until the next step is tried. */
    val problem: String? = null
) {
    val total: Int get() = scenario?.steps?.size ?: 0
    val finished: Boolean get() = scenario != null && next >= total
}

/**
 * Replays the loaded [scenario] through the app's own category, expense and income
 * commands, which the sandbox fences and audits like any other write. Every step uses
 * the operation [Scenario.operation] gives it, so running a step again after the
 * process ended, even one that had committed, returns its original receipt instead
 * of writing a second record.
 *
 * Progress is kept in [store]: [ScenarioState.next] moves on only once a step's result
 * is known. A rejection or an unknown outcome stops the run and says why; the next
 * try of that step uses the same operation. Playing takes one step every [stepMillis]
 * real milliseconds and ends with the scenario, at a stop, or on [pause].
 */
class ScenarioRunner(
    private val scenario: Scenario?,
    private val generation: Long,
    private val time: SandboxTime,
    private val categories: CategoryCommands,
    private val expenses: ExpenseCommands,
    private val incomes: IncomeCommands,
    private val goals: GoalCommands,
    private val months: MonthlySnapshots,
    private val summaries: ResumenSnapshots,
    private val goalReads: GoalReads,
    private val store: SandboxSessionStore,
    private val scope: CoroutineScope,
    private val stepMillis: Long = SandboxTime.STEP_MILLIS
) {
    /** The steps confirmed by earlier processes; this process's own steps start here. */
    private val confirmed = store.read().nextStep
    private val mutable = MutableStateFlow(ScenarioState(scenario, confirmed))
    val state: StateFlow<ScenarioState> = mutable.asStateFlow()

    private val stepping = Mutex()
    private var player: Job? = null
    private val categoryIds = mutableMapOf<String, CategoryId>()
    private val goalIds = mutableMapOf<String, GoalId>()

    /**
     * Lists the steps confirmed in earlier processes from their stored receipts, ahead of
     * any this process has already taken, whose own results it keeps.
     */
    fun recover(): Job = scope.launch {
        val loaded = scenario ?: return@launch
        stepping.withLock {
            val records = (0 until confirmed).map { index ->
                val step = loaded.steps[index]
                val receipt = receiptOf(loaded, index, step)
                StepRecord(
                    index,
                    describe(step),
                    loaded.operation(index).takeIf { step.writes },
                    receipt?.revision,
                    when {
                        step is AdvanceTo -> DATE_SET
                        step is Verify -> "Comprobado antes"
                        receipt != null -> "Guardado"
                        else -> "Sin escritura, como se esperaba"
                    }
                )
            }
            mutable.update { state ->
                state.copy(log = records + state.log.filter { it.index >= confirmed })
            }
        }
    }

    /** Runs the next step once. Returns whether the scenario moved on. */
    suspend fun step(): Boolean = stepping.withLock {
        val loaded = scenario ?: return@withLock false
        val index = mutable.value.next
        if (index >= loaded.steps.size) return@withLock false
        mutable.update { it.copy(running = true, problem = null) }
        val step = loaded.steps[index]
        val done = try {
            run(loaded, index, step)
        } catch (interrupted: SimulatedInterruption) {
            Result(
                null,
                "Interrumpido tras guardar (simulado); el siguiente paso lo intenta con la misma operación",
                moved = false
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result(null, "Falló: ${failure::class.simpleName}", moved = false)
        } finally {
            mutable.update { it.copy(running = false) }
        }
        val record = StepRecord(
            index,
            describe(step),
            loaded.operation(index).takeIf { step.writes },
            done.revision,
            done.text
        )
        if (done.moved) {
            store.write(store.read().copy(nextStep = index + 1))
            mutable.update { it.copy(next = index + 1, log = it.log + record) }
        } else {
            mutable.update { it.copy(log = it.log + record, problem = done.text) }
        }
        done.moved
    }

    fun play() {
        if (scenario == null || mutable.value.finished) return
        player?.cancel()
        mutable.update { it.copy(playing = true) }
        player = scope.launch {
            try {
                while (!mutable.value.finished) {
                    delay(stepMillis)
                    if (!step()) break
                }
            } finally {
                mutable.update { it.copy(playing = false) }
            }
        }
    }

    fun pause() {
        player?.cancel()
        player = null
        mutable.update { it.copy(playing = false) }
    }

    private class Result(val revision: Long?, val text: String, val moved: Boolean)

    /** What a step met, before it is judged against what it expects. */
    private sealed interface Observed {
        data class Committed(val receipt: OperationReceipt, val replayed: Boolean) : Observed

        data class Rejected(val reason: String) : Observed

        data object Unknown : Observed

        data class Invalid(val reason: String) : Observed

        data object DateSet : Observed

        data class Stop(val why: String) : Observed

        /** Each check's line, and whether all of them matched. */
        data class Checked(val lines: List<String>, val matched: Boolean) : Observed
    }

    private fun judge(expect: Expect, seen: Observed): Result = when (seen) {
        is Observed.Committed -> when {
            expect == Expect.Saved || (expect == Expect.AlreadySaved && seen.replayed) ->
                Result(
                    seen.receipt.revision,
                    if (seen.replayed) "Ya estaba guardado" else "Guardado",
                    true
                )

            else -> Result(
                seen.receipt.revision,
                "Se esperaba ${expected(expect)} y se guardó",
                false
            )
        }

        is Observed.Rejected ->
            if (expect is Expect.Rejected && expect.reason == seen.reason) {
                Result(null, "Rechazado, como se esperaba: ${seen.reason}", true)
            } else {
                Result(null, "Rechazado: ${seen.reason}", false)
            }

        is Observed.Invalid ->
            if (expect == Expect.InvalidInput) {
                Result(null, "No se envió, como se esperaba: ${seen.reason}", true)
            } else {
                Result(null, "Dato no válido: ${seen.reason}", false)
            }

        Observed.Unknown -> Result(
            null,
            "No se sabe si se guardó; el siguiente paso lo intenta con la misma operación",
            false
        )

        Observed.DateSet -> Result(null, DATE_SET, true)

        is Observed.Checked -> Result(
            null,
            (
                listOf(
                    if (seen.matched) {
                        "Comprobación: ${seen.lines.size} de ${seen.lines.size} cifras coinciden"
                    } else {
                        "Comprobación: no coincide"
                    }
                ) + seen.lines
                ).joinToString("\n"),
            seen.matched
        )

        is Observed.Stop -> Result(null, seen.why, false)
    }

    private fun expected(expect: Expect) = when (expect) {
        Expect.Saved -> "guardar"
        Expect.AlreadySaved -> "que ya estuviera guardado"
        is Expect.Rejected -> "rechazo ${expect.reason}"
        Expect.InvalidInput -> "un dato no válido"
    }

    private suspend fun run(loaded: Scenario, index: Int, step: ScenarioStep): Result =
        judge(step.expect, observe(loaded, index, step))

    private suspend fun observe(loaded: Scenario, index: Int, step: ScenarioStep): Observed {
        val operation = OperationId.of(loaded.operation(index)).value()
        val today = time.clock.today.value.date
        return when (step) {
            is AdvanceTo -> when {
                step.date < today -> Observed.Stop("La fecha del escenario va hacia atrás")

                step.date == today -> Observed.DateSet

                else -> when (time.jumpTo(step.date)) {
                    DateChange.Moved -> Observed.DateSet

                    DateChange.RefusedBeforeSavedHistory ->
                        Observed.Stop("La fecha no puede volver atrás")
                }
            }

            is Category -> category(operation, step)

            is Expense -> {
                val category = categoryId(loaded, step.category)
                    ?: return Observed.Stop("No existe la categoría «${step.category}»")
                val amount = when (val checked = TransactionAmount.of(Money.cop(step.pesos))) {
                    is Outcome.Success -> checked.value
                    is Outcome.Failure -> return Observed.Invalid(name(checked.error))
                }
                expense(
                    expenses.create(
                        CreateExpense(
                            operation,
                            generation,
                            category,
                            amount,
                            step.on ?: today,
                            Note.of(step.note).value()
                        )
                    )
                )
            }

            is Income -> when (
                val outcome = incomes.create(
                    CreateIncome(
                        operation,
                        generation,
                        TransactionAmount.of(Money.cop(step.pesos)).value(),
                        IncomeSource.of(step.source, null).value(),
                        today,
                        null
                    )
                )
            ) {
                is IncomeCommandOutcome.Committed ->
                    Observed.Committed(outcome.income.receipt, outcome.replayed)

                is IncomeCommandOutcome.Rejected -> Observed.Rejected(name(outcome.reason))

                is IncomeCommandOutcome.OutcomeUnknown -> Observed.Unknown
            }

            // The same operation and the same input as the step it names.
            is Repeat -> when (val target = loaded.steps[step.of]) {
                is Repeat, is AdvanceTo -> Observed.Stop("Solo se repite un paso que escribe")
                else -> observe(loaded, step.of, target)
            }

            is EditExpense -> {
                val recorded = recordedExpense(loaded, step.of)
                    ?: return Observed.Stop("El paso ${step.of + 1} no registró un gasto")
                val original = loaded.steps[step.of] as Expense
                expense(
                    expenses.edit(
                        EditExpenseCommand(
                            operation,
                            generation,
                            recorded,
                            step.version,
                            checkNotNull(categoryId(loaded, original.category)),
                            TransactionAmount.of(Money.cop(step.pesos)).value(),
                            today,
                            Note.of(original.note).value()
                        )
                    )
                )
            }

            is DeleteExpense -> {
                val recorded = recordedExpense(loaded, step.of)
                    ?: return Observed.Stop("El paso ${step.of + 1} no registró un gasto")
                expense(
                    expenses.delete(
                        DeleteExpenseCommand(operation, generation, recorded, step.version)
                    )
                )
            }

            is SetDefaultLimit -> categoryChange(loaded, step.category) { id ->
                SetDefaultLimitCommand(operation, generation, id, step.version, limit(step.pesos))
            }

            is SetMonthLimit -> categoryChange(loaded, step.category) { id ->
                SetMonthLimitCommand(
                    operation,
                    generation,
                    id,
                    step.version,
                    step.month,
                    limit(step.pesos)
                )
            }

            is Archive -> categoryChange(loaded, step.category) { id ->
                ArchiveCategory(operation, generation, id, step.version)
            }

            is Verify -> verify(loaded, step.checks, step.goals)

            is Goal -> goal(
                goals.submit(
                    CreateGoal(
                        operation,
                        generation,
                        GoalName.of(step.name).value(),
                        IconKey.of(step.icon).value(),
                        TransactionAmount.of(Money.cop(step.target)).value(),
                        null
                    )
                )
            )

            is Aporte -> goalCommand(loaded, step.goal) { id ->
                RecordGoalMovement(
                    operation,
                    generation,
                    id,
                    step.version,
                    GoalMovementKind.Contribution,
                    TransactionAmount.of(Money.cop(step.pesos)).value(),
                    step.on ?: today,
                    null
                )
            }

            is Retiro -> goalCommand(loaded, step.goal) { id ->
                RecordGoalMovement(
                    operation,
                    generation,
                    id,
                    step.version,
                    GoalMovementKind.Withdrawal,
                    TransactionAmount.of(Money.cop(step.pesos)).value(),
                    today,
                    null
                )
            }

            is GoalTarget -> goalCommand(loaded, step.goal) { id ->
                ChangeGoalTarget(
                    operation,
                    generation,
                    id,
                    step.version,
                    TransactionAmount.of(Money.cop(step.pesos)).value()
                )
            }

            is MarkLograda -> goalCommand(loaded, step.goal) { id ->
                CompleteGoal(operation, generation, id, step.version, step.undoFor ?: UNDO_FOR)
            }

            is UndoLograda -> goal(
                goals.submit(
                    UndoGoalCompletion(
                        operation,
                        generation,
                        OperationId.of(loaded.operation(step.of)).value()
                    )
                )
            )

            is ChooseForRecibo -> goalCommand(loaded, step.goal) { id ->
                FeatureGoal(operation, generation, id)
            }

            is UndoDelete -> expense(
                expenses.restore(
                    RestoreExpense(
                        operation,
                        generation,
                        OperationId.of(loaded.operation(step.of)).value()
                    )
                )
            )
        }
    }

    private suspend fun category(operation: OperationId, step: Category): Observed = when (
        val outcome = categories.submit(
            CreateCategory(
                operation,
                generation,
                CategoryName.of(step.name).value(),
                IconKey.of(step.icon).value(),
                step.limit?.let { BudgetLimit.finite(Money.cop(it)).value() }
                    ?: BudgetLimit.Unlimited
            )
        )
    ) {
        is CategoryCommandOutcome.Committed -> {
            categoryIds[step.key] = outcome.change.categoryId
            Observed.Committed(outcome.change.receipt, outcome.replayed)
        }

        is CategoryCommandOutcome.Rejected -> Observed.Rejected(name(outcome.reason))

        is CategoryCommandOutcome.OutcomeUnknown -> Observed.Unknown
    }

    private fun limit(pesos: Long?): BudgetLimit =
        pesos?.let { BudgetLimit.finite(Money.cop(it)).value() } ?: BudgetLimit.Unlimited

    private suspend fun categoryChange(
        loaded: Scenario,
        key: String,
        command: (CategoryId) -> CategoryCommand
    ): Observed {
        val id = categoryId(loaded, key) ?: return Observed.Stop("No existe la categoría «$key»")
        return when (val outcome = categories.submit(command(id))) {
            is CategoryCommandOutcome.Committed ->
                Observed.Committed(outcome.change.receipt, outcome.replayed)

            is CategoryCommandOutcome.Rejected -> Observed.Rejected(name(outcome.reason))

            is CategoryCommandOutcome.OutcomeUnknown -> Observed.Unknown
        }
    }

    /**
     * Reads each month's real snapshots and the goal board once and compares every check
     * against them.
     */
    private suspend fun verify(
        loaded: Scenario,
        checks: List<Check>,
        goalChecks: List<GoalCheck>
    ): Observed {
        val lines = checks.map { check ->
            val actual = actual(check.month, check.figure)
            val expected = if (check.figure == Figure.NoLimit) "sí" else pesos(check.pesos)
            val ok = actual == expected
            "${if (ok) "✓" else "✗"} ${check.figure.label} ${check.month}: esperado $expected, " +
                "real ${actual ?: "no se pudo leer"} (${check.source})"
        }
        val board = if (goalChecks.isEmpty()) {
            null
        } else {
            (goalReads.board() as? Outcome.Success)?.value
        }
        val goalLines = goalChecks.map { check ->
            // A history restored from a backup has no receipts: the goal is found by its
            // name, which is unique in a scenario.
            val name = loaded.steps.filterIsInstance<Goal>().firstOrNull {
                it.key == check.goal
            }?.name
            val summary = goalId(loaded, check.goal)
                ?.let { id -> board?.goals?.firstOrNull { it.history.goal.id == id } }
                ?: board?.goals?.firstOrNull { it.history.goal.name.value == name }
            val id = summary?.history?.goal?.id
            val expected = goalLine(check.saved, check.percentage, check.stamps, check.completed) +
                (check.onRecibo?.let { if (it) ", en Recibo" else ", no en Recibo" } ?: "")
            val actual = summary?.let { found ->
                val progress = found.progress
                goalLine(
                    progress.saved.minorUnits,
                    progress.percentage,
                    progress.places.map { stamp(it.state) },
                    found.history.goal.completedOn != null
                ) + (
                    check.onRecibo?.let {
                        if (board?.featured == id) ", en Recibo" else ", no en Recibo"
                    } ?: ""
                    )
            }
            val ok = actual == expected
            "${if (ok) "✓" else "✗"} meta ${check.goal}: esperado $expected, " +
                "real ${actual ?: "no se pudo leer"} (${check.source})"
        }
        val all = lines + goalLines
        return Observed.Checked(all, all.all { it.startsWith("✓") })
    }

    private fun goalLine(saved: Long, percentage: Long, stamps: List<Stamp>, completed: Boolean) =
        "${pesos(saved)}, $percentage %, sellos ${stamps.joinToString("/") { it.label }}" +
            if (completed) ", lograda" else ""

    private fun stamp(state: PlaceState): Stamp = when (state) {
        PlaceState.Empty -> Stamp.Empty
        is PlaceState.Earned -> Stamp.Earned
        is PlaceState.Kept -> Stamp.Kept
        PlaceState.Ready -> Stamp.Waiting
    }

    private fun goal(outcome: GoalCommandOutcome): Observed = when (outcome) {
        is GoalCommandOutcome.Committed -> Observed.Committed(
            outcome.goal.receipt,
            outcome.replayed
        )

        is GoalCommandOutcome.Rejected -> Observed.Rejected(name(outcome.reason))

        is GoalCommandOutcome.OutcomeUnknown -> Observed.Unknown
    }

    /** [command] for the goal the step named [key] made. */
    private suspend fun goalCommand(
        loaded: Scenario,
        key: String,
        command: (GoalId) -> GoalCommand
    ): Observed {
        val id = goalId(loaded, key) ?: return Observed.Stop("No existe la meta «$key»")
        return goal(goals.submit(command(id)))
    }

    /** The identity the goal step named [key] received, read back from its receipt. */
    private suspend fun goalId(loaded: Scenario, key: String): GoalId? {
        goalIds[key]?.let { return it }
        val index = loaded.steps.indexOfFirst { it is Goal && it.key == key }
        if (index < 0) return null
        val found = goals.find(OperationId.of(loaded.operation(index)).value()) as? Outcome.Success
            ?: return null
        return found.value?.goalId?.also { goalIds[key] = it }
    }

    private suspend fun actual(month: YearMonth, figure: Figure): String? = when (figure) {
        Figure.Spent, Figure.Limit, Figure.Remaining, Figure.NoLimit -> {
            val snapshot = (months.read(month) as? Outcome.Success)?.value
            val overall = snapshot?.overall
            when (figure) {
                Figure.Spent -> snapshot?.spent?.let(::pesos)

                Figure.NoLimit -> overall?.let { if (it is OverallBudget.NoLimit) "sí" else "no" }

                Figure.Limit -> (overall as? OverallBudget.Limited)?.budget?.limit?.let(::pesos)
                    ?: overall?.let { "sin límite" }

                else -> (overall as? OverallBudget.Limited)?.budget?.remaining?.let(::pesos)
                    ?: overall?.let { "sin límite" }
            }
        }

        Figure.Income, Figure.NetFlow, Figure.Unallocated -> {
            val summary = (summaries.read(month) as? Outcome.Success)?.value
            when (figure) {
                Figure.Income -> summary?.income
                Figure.NetFlow -> summary?.netFlow
                else -> summary?.unallocated
            }?.let(::pesos)
        }
    }

    private fun expense(outcome: ExpenseCommandOutcome): Observed = when (outcome) {
        is ExpenseCommandOutcome.Committed -> Observed.Committed(
            outcome.expense.receipt,
            outcome.replayed
        )

        is ExpenseCommandOutcome.Rejected -> Observed.Rejected(name(outcome.reason))

        is ExpenseCommandOutcome.OutcomeUnknown -> Observed.Unknown
    }

    private fun name(reason: Any): String = reason::class.simpleName ?: reason.toString()

    /** The expense the expense step [of] recorded, read back from its receipt. */
    private suspend fun recordedExpense(loaded: Scenario, of: Int): ExpenseId? {
        if (loaded.steps.getOrNull(of) !is Expense) return null
        val found = expenses.find(OperationId.of(loaded.operation(of)).value())
        return (found as? Outcome.Success)?.value?.expenseId
    }

    /** The identity the category step named [key] received, read back from its receipt. */
    private suspend fun categoryId(loaded: Scenario, key: String): CategoryId? {
        categoryIds[key]?.let { return it }
        val index = loaded.steps.indexOfFirst { it is Category && it.key == key }
        if (index < 0) return null
        val operation = OperationId.of(loaded.operation(index)).value()
        val found = categories.find(operation) as? Outcome.Success ?: return null
        return found.value?.categoryId?.also { categoryIds[key] = it }
    }

    private suspend fun receiptOf(
        loaded: Scenario,
        index: Int,
        step: ScenarioStep
    ): OperationReceipt? {
        val operation = OperationId.of(loaded.operation(index)).value()
        return when (step) {
            is AdvanceTo -> null

            is Category -> (categories.find(operation) as? Outcome.Success)?.value?.receipt

            is Expense -> (expenses.find(operation) as? Outcome.Success)?.value?.receipt

            is Income -> (incomes.find(operation) as? Outcome.Success)?.value?.receipt

            is Repeat -> receiptOf(loaded, step.of, loaded.steps[step.of])

            is EditExpense, is DeleteExpense, is UndoDelete ->
                (expenses.find(operation) as? Outcome.Success)?.value?.receipt

            is SetDefaultLimit, is SetMonthLimit, is Archive ->
                (categories.find(operation) as? Outcome.Success)?.value?.receipt

            is Verify -> null

            is Goal, is Aporte, is Retiro, is GoalTarget, is MarkLograda, is UndoLograda,
            is ChooseForRecibo -> (goals.find(operation) as? Outcome.Success)?.value?.receipt
        }
    }

    private fun <T> Outcome<T, *>.value(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> error("invalid scenario input: $error")
    }

    companion object {
        private const val DATE_SET = "Fecha fijada"

        /** The longest Deshacer the app offers, with a screen reader; real elapsed time. */
        private val UNDO_FOR = 34.seconds

        fun describe(step: ScenarioStep): String = when (step) {
            is AdvanceTo -> "Ir al ${spokenDate(step.date)}"

            is Category ->
                "Categoría ${step.name}" +
                    (step.limit?.let { ", límite ${pesos(it)}" } ?: ", sin límite")

            is Expense -> "Gasto ${pesos(step.pesos)} en ${step.category}" +
                (step.on?.let { " con fecha ${spokenDate(it)}" } ?: "")

            is Income -> "Ingreso ${pesos(step.pesos)} (${step.source})"

            is Repeat -> "Repetir el paso ${step.of + 1}"

            is EditExpense ->
                "Editar el gasto del paso ${step.of + 1} a ${pesos(
                    step.pesos
                )} (versión ${step.version})"

            is DeleteExpense -> "Borrar el gasto del paso ${step.of + 1} (versión ${step.version})"

            is UndoDelete -> "Deshacer el borrado del paso ${step.of + 1}"

            is SetDefaultLimit ->
                "Límite por defecto de ${step.category}: " + (step.pesos?.let(::pesos) ?: "ninguno")

            is SetMonthLimit ->
                "Límite de ${step.month} en ${step.category}: " +
                    (step.pesos?.let(::pesos) ?: "ninguno")

            is Archive -> "Archivar ${step.category}"

            is Verify -> "Comprobar ${step.checks.size + step.goals.size} cifras"

            is Goal -> "Meta ${step.name}, objetivo ${pesos(step.target)}"

            is Aporte -> "Aporte ${pesos(step.pesos)} a ${step.goal} (versión ${step.version})" +
                (step.on?.let { " con fecha ${spokenDate(it)}" } ?: "")

            is Retiro -> "Retiro ${pesos(step.pesos)} de ${step.goal} (versión ${step.version})"

            is GoalTarget ->
                "Objetivo de ${step.goal}: ${pesos(step.pesos)} (versión ${step.version})"

            is MarkLograda ->
                "Marcar ${step.goal} como lograda (versión ${step.version})" +
                    (step.undoFor?.let { ", Deshacer durante ${it.inWholeSeconds} s" } ?: "")

            is UndoLograda -> "Deshacer la meta lograda del paso ${step.of + 1}"

            is ChooseForRecibo -> "Mostrar ${step.goal} en Recibo"
        }

        private fun pesos(amount: Money): String = pesos(amount.minorUnits)

        private fun pesos(amount: Long): String =
            String.format(Locale.ROOT, "%,d", amount).replace(',', '.')
    }
}
