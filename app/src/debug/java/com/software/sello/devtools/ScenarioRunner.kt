package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.AdvanceTo
import com.software.sello.devtools.ScenarioStep.Aporte
import com.software.sello.devtools.ScenarioStep.Archive
import com.software.sello.devtools.ScenarioStep.CatchUp
import com.software.sello.devtools.ScenarioStep.Category
import com.software.sello.devtools.ScenarioStep.ChooseForRecibo
import com.software.sello.devtools.ScenarioStep.DeleteExpense
import com.software.sello.devtools.ScenarioStep.EditExpense
import com.software.sello.devtools.ScenarioStep.Expense
import com.software.sello.devtools.ScenarioStep.Goal
import com.software.sello.devtools.ScenarioStep.GoalTarget
import com.software.sello.devtools.ScenarioStep.Income
import com.software.sello.devtools.ScenarioStep.Investment
import com.software.sello.devtools.ScenarioStep.MarkLograda
import com.software.sello.devtools.ScenarioStep.Movement
import com.software.sello.devtools.ScenarioStep.PauseSchedule
import com.software.sello.devtools.ScenarioStep.ResumeSchedule
import com.software.sello.devtools.ScenarioStep.Retiro
import com.software.sello.devtools.ScenarioStep.Schedule
import com.software.sello.devtools.ScenarioStep.SetDefaultLimit
import com.software.sello.devtools.ScenarioStep.SetMonthLimit
import com.software.sello.devtools.ScenarioStep.SetRate
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.UsdRate
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
import com.software.sello.domain.port.CreateInvestment
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DatedUsdRate
import com.software.sello.domain.port.DeleteExpense as DeleteExpenseCommand
import com.software.sello.domain.port.EditExpense as EditExpenseCommand
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.PauseSchedule as PauseScheduleCommand
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.RecordInvestmentMovement
import com.software.sello.domain.port.ResumeSchedule as ResumeScheduleCommand
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.SetDefaultLimit as SetDefaultLimitCommand
import com.software.sello.domain.port.SetMonthLimit as SetMonthLimitCommand
import com.software.sello.domain.port.SetUsdRate
import com.software.sello.domain.usecase.CatchUpNow
import com.software.sello.domain.usecase.CatchUpStop
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ScenarioState(
    val scenario: Scenario?,
    /** The first step not yet confirmed; it may have committed already. */
    val next: Int,
    /** This process is loading the history now. */
    val loading: Boolean = false,
    /** Why the load stopped short, until it is tried again. */
    val problem: String? = null
) {
    val total: Int get() = scenario?.steps?.size ?: 0
    val finished: Boolean get() = scenario != null && next >= total
}

/** The schedule [commands] and the [catchUp] Sello runs when it opens and when the day changes. */
class ScenarioSchedules(val commands: ScheduleCommands, val catchUp: CatchUpNow)

/**
 * Loads the [scenario] through the app's own category, expense, income, goal, schedule
 * and investment commands and its catch-up, which the sandbox fences like any other
 * write. Every step uses the operation [Scenario.operation] gives it, so running a step
 * again after the process ended, even one that had committed, returns its original
 * receipt instead of writing a second record.
 *
 * Progress is kept in [store]: [ScenarioState.next] moves on only once a step's result
 * is known, and [SandboxSession.loading] stays set until the last step is confirmed. A
 * load cut short by the end of the process therefore carries on in the next one
 * ([resume]) with the same operations; one stopped by a refusal or an unknown outcome
 * says why and is tried again on [load].
 */
class ScenarioRunner(
    private val scenario: Scenario?,
    private val generation: Long,
    private val time: SandboxTime,
    private val categories: CategoryCommands,
    private val expenses: ExpenseCommands,
    private val incomes: IncomeCommands,
    private val goals: GoalCommands,
    private val schedules: ScenarioSchedules,
    private val investments: InvestmentCommands,
    private val store: SandboxSessionStore,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow(ScenarioState(scenario, store.read().nextStep))
    val state: StateFlow<ScenarioState> = mutable.asStateFlow()

    private val stepping = Mutex()
    private var loader: Job? = null
    private val categoryIds = mutableMapOf<String, CategoryId>()
    private val goalIds = mutableMapOf<String, GoalId>()
    private val scheduleIds = mutableMapOf<String, ScheduleId>()
    private val investmentIds = mutableMapOf<String, InvestmentId>()

    /** Carries on with a load an earlier process asked for and did not finish. */
    fun resume() {
        if (store.read().loading) load()
    }

    /**
     * Runs every step not yet confirmed, up to the end or the first that stops. A load
     * already under way is carried on only while the history it wrote is still there:
     * after the app's own reset erased it, the rest would land in an empty history.
     */
    fun load(): Job {
        loader?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            mutable.update { it.copy(loading = true, problem = null) }
            try {
                if (erased()) {
                    mutable.update { it.copy(problem = ERASED) }
                } else {
                    while (step()) Unit
                }
            } finally {
                mutable.update { it.copy(loading = false) }
            }
        }.also { loader = it }
    }

    /** Whether the first step this history saved has no receipt any more. */
    private suspend fun erased(): Boolean {
        val loaded = scenario ?: return false
        if (mutable.value.next == 0) return false
        val operation = OperationId.of(loaded.operation(0)).value()
        val found = when (loaded.steps.first()) {
            is Category -> categories.find(operation)
            is Investment -> investments.find(operation)
            is Goal -> goals.find(operation)
            else -> return false
        }
        return found is Outcome.Success && found.value == null
    }

    /** Runs the next step once. Returns whether the history moved on. */
    suspend fun step(): Boolean = stepping.withLock {
        val loaded = scenario ?: return@withLock false
        val index = mutable.value.next
        if (index >= loaded.steps.size) return@withLock false
        val step = loaded.steps[index]
        val problem = try {
            run(loaded, index, step)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            "Falló: ${failure::class.simpleName}"
        }
        if (problem == null) {
            val next = index + 1
            val done = next >= loaded.steps.size
            store.write(store.read().copy(nextStep = next, loading = store.read().loading && !done))
            mutable.update { it.copy(next = next, problem = null) }
            true
        } else {
            val text = "Paso ${index + 1} de ${loaded.steps.size} " +
                "(${step::class.simpleName}): $problem"
            mutable.update { it.copy(problem = text) }
            false
        }
    }

    /** Null once the step's effect is in storage, now or by an earlier try; else why not. */
    private suspend fun run(loaded: Scenario, index: Int, step: ScenarioStep): String? {
        val operation = OperationId.of(loaded.operation(index)).value()
        val today = time.clock.today.value.date
        return when (step) {
            is AdvanceTo -> when {
                step.date == today -> null

                step.date < today -> "la fecha va hacia atrás"

                else -> when (time.jumpTo(step.date)) {
                    DateChange.Moved -> null
                    DateChange.RefusedBeforeSavedHistory -> "la fecha no puede volver atrás"
                }
            }

            is Category -> category(
                categories.submit(
                    CreateCategory(
                        operation,
                        generation,
                        CategoryName.of(step.name).value(),
                        IconKey.of(step.icon).value(),
                        limit(step.limit)
                    )
                )
            ) { categoryIds[step.key] = it }

            is Expense -> {
                val category = categoryId(loaded, step.category)
                    ?: return "no existe la categoría «${step.category}»"
                expense(
                    expenses.create(
                        CreateExpense(
                            operation,
                            generation,
                            category,
                            pesos(step.pesos),
                            step.on ?: today,
                            Note.of(step.note).value()
                        )
                    )
                )
            }

            is EditExpense -> {
                val original = loaded.steps[step.of] as Expense
                val recorded = recordedExpense(loaded, step.of)
                    ?: return "el paso ${step.of + 1} no registró un gasto"
                expense(
                    expenses.edit(
                        EditExpenseCommand(
                            operation,
                            generation,
                            recorded,
                            step.version,
                            checkNotNull(categoryId(loaded, original.category)),
                            pesos(step.pesos),
                            original.on ?: dayAt(loaded, step.of),
                            Note.of(original.note).value()
                        )
                    )
                )
            }

            is DeleteExpense -> {
                val recorded = recordedExpense(loaded, step.of)
                    ?: return "el paso ${step.of + 1} no registró un gasto"
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

            is Income -> when (
                val outcome = incomes.create(
                    CreateIncome(
                        operation,
                        generation,
                        pesos(step.pesos),
                        IncomeSource.of(step.source, null).value(),
                        step.on ?: today,
                        null
                    )
                )
            ) {
                is IncomeCommandOutcome.Committed -> null
                is IncomeCommandOutcome.Rejected -> rejected(outcome.reason)
                is IncomeCommandOutcome.OutcomeUnknown -> UNKNOWN
            }

            is Goal -> goal(
                goals.submit(
                    CreateGoal(
                        operation,
                        generation,
                        GoalName.of(step.name).value(),
                        IconKey.of(step.icon).value(),
                        pesos(step.target),
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
                    pesos(step.pesos),
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
                    pesos(step.pesos),
                    today,
                    null
                )
            }

            is GoalTarget -> goalCommand(loaded, step.goal) { id ->
                ChangeGoalTarget(operation, generation, id, step.version, pesos(step.pesos))
            }

            is MarkLograda -> goalCommand(loaded, step.goal) { id ->
                CompleteGoal(operation, generation, id, step.version, UNDO_FOR)
            }

            is ChooseForRecibo -> goalCommand(loaded, step.goal) { id ->
                FeatureGoal(operation, generation, id)
            }

            is Schedule -> {
                val entry = scheduledEntry(loaded, step.entry)
                    ?: return "no existe la categoría de «${step.entry.note}»"
                schedule(
                    schedules.commands.submit(
                        CreateSchedule(
                            operation,
                            generation,
                            entry,
                            rule(step.every),
                            step.start ?: today,
                            step.end
                        )
                    )
                ) { scheduleIds[step.key] = it }
            }

            is PauseSchedule -> scheduleCommand(loaded, step.schedule) { id ->
                PauseScheduleCommand(operation, generation, id, step.version)
            }

            is ResumeSchedule -> scheduleCommand(loaded, step.schedule) { id ->
                ResumeScheduleCommand(operation, generation, id, step.version)
            }

            is Investment -> investment(
                investments.submit(
                    CreateInvestment(
                        operation,
                        generation,
                        InvestmentName.of(step.name).value(),
                        null,
                        IconKey.of(step.icon).value(),
                        step.currency,
                        investmentAmount(
                            InvestmentMovementKind.Contribution,
                            step.contribution,
                            step.currency
                        ),
                        investmentAmount(
                            InvestmentMovementKind.Valuation,
                            step.value ?: step.contribution,
                            step.currency
                        ),
                        step.on ?: today,
                        step.rate?.let { rate(it, today) }
                    )
                )
            ) { investmentIds[step.key] = it }

            is Movement -> {
                val currency = loaded.steps.filterIsInstance<Investment>()
                    .firstOrNull { it.key == step.investment }?.currency
                    ?: return "no existe la inversión «${step.investment}»"
                val kind = kind(step.kind)
                investmentCommand(loaded, step.investment) { id ->
                    RecordInvestmentMovement(
                        operation,
                        generation,
                        id,
                        step.version,
                        kind,
                        investmentAmount(kind, step.minor, currency),
                        step.on ?: today,
                        null
                    )
                }
            }

            is SetRate -> {
                val rate = rate(step.rate, today)
                investment(
                    investments.submit(SetUsdRate(operation, generation, rate.rate, rate.date))
                )
            }

            CatchUp -> when (val stop = schedules.catchUp.run().stopped) {
                null -> null
                is CatchUpStop.Failed -> "falló la puesta al día: ${name(stop.failure)}"
                CatchUpStop.Unknown -> "no se sabe si el último lote de la puesta al día se guardó"
            }
        }
    }

    private fun pesos(amount: Long): TransactionAmount =
        TransactionAmount.of(Money.cop(amount)).value()

    private fun limit(pesos: Long?): BudgetLimit =
        pesos?.let { BudgetLimit.finite(Money.cop(it)).value() } ?: BudgetLimit.Unlimited

    private inline fun category(
        outcome: CategoryCommandOutcome,
        made: (CategoryId) -> Unit = {}
    ): String? = when (outcome) {
        is CategoryCommandOutcome.Committed -> null.also { made(outcome.change.categoryId) }
        is CategoryCommandOutcome.Rejected -> rejected(outcome.reason)
        is CategoryCommandOutcome.OutcomeUnknown -> UNKNOWN
    }

    private suspend fun categoryChange(
        loaded: Scenario,
        key: String,
        command: (CategoryId) -> CategoryCommand
    ): String? {
        val id = categoryId(loaded, key) ?: return "no existe la categoría «$key»"
        return category(categories.submit(command(id)))
    }

    private fun expense(outcome: ExpenseCommandOutcome): String? = when (outcome) {
        is ExpenseCommandOutcome.Committed -> null
        is ExpenseCommandOutcome.Rejected -> rejected(outcome.reason)
        is ExpenseCommandOutcome.OutcomeUnknown -> UNKNOWN
    }

    private fun goal(outcome: GoalCommandOutcome): String? = when (outcome) {
        is GoalCommandOutcome.Committed -> null
        is GoalCommandOutcome.Rejected -> rejected(outcome.reason)
        is GoalCommandOutcome.OutcomeUnknown -> UNKNOWN
    }

    private suspend fun goalCommand(
        loaded: Scenario,
        key: String,
        command: (GoalId) -> GoalCommand
    ): String? {
        val id = goalId(loaded, key) ?: return "no existe la meta «$key»"
        return goal(goals.submit(command(id)))
    }

    private inline fun schedule(
        outcome: ScheduleCommandOutcome,
        made: (ScheduleId) -> Unit = {}
    ): String? = when (outcome) {
        is ScheduleCommandOutcome.Committed -> null.also { made(outcome.schedule.scheduleId) }
        is ScheduleCommandOutcome.Rejected -> rejected(outcome.reason)
        is ScheduleCommandOutcome.OutcomeUnknown -> UNKNOWN
    }

    private suspend fun scheduleCommand(
        loaded: Scenario,
        key: String,
        command: (ScheduleId) -> ScheduleCommand
    ): String? {
        val id = scheduleId(loaded, key) ?: return "no existe el programado «$key»"
        return schedule(schedules.commands.submit(command(id)))
    }

    private suspend fun scheduledEntry(loaded: Scenario, entry: Scheduled): ScheduledEntry? {
        val note = Note.of(entry.note).value()
        return when (entry) {
            is Scheduled.Payment -> categoryId(loaded, entry.category)?.let {
                ScheduledEntry.Payment(it, pesos(entry.pesos), note)
            }

            is Scheduled.Income -> ScheduledEntry.Income(
                IncomeSource.of(entry.source, null).value(),
                pesos(entry.pesos),
                note
            )
        }
    }

    private fun rule(every: Every): RepeatRule = when (every) {
        is Every.Month -> RepeatRule.Monthly.of(every.day).value()
        is Every.TwiceAMonth -> RepeatRule.TwiceMonthly.of(every.first, every.second).value()
        is Every.Days -> RepeatRule.EveryDays.of(every.days).value()
        is Every.Weeks -> RepeatRule.EveryWeeks.of(every.weeks).value()
    }

    private inline fun investment(
        outcome: InvestmentCommandOutcome,
        made: (InvestmentId) -> Unit = {}
    ): String? = when (outcome) {
        is InvestmentCommandOutcome.Committed ->
            null.also { outcome.change.investmentId?.let(made) }

        is InvestmentCommandOutcome.Rejected -> rejected(outcome.reason)

        is InvestmentCommandOutcome.OutcomeUnknown -> UNKNOWN
    }

    private suspend fun investmentCommand(
        loaded: Scenario,
        key: String,
        command: (InvestmentId) -> InvestmentCommand
    ): String? {
        val id = investmentId(loaded, key) ?: return "no existe la inversión «$key»"
        return investment(investments.submit(command(id)))
    }

    private fun kind(move: Move): InvestmentMovementKind = when (move) {
        Move.Aporte -> InvestmentMovementKind.Contribution
        Move.Retiro -> InvestmentMovementKind.Withdrawal
        Move.Dividendo -> InvestmentMovementKind.Dividend
        Move.Valor -> InvestmentMovementKind.Valuation
    }

    private fun investmentAmount(
        kind: InvestmentMovementKind,
        minor: Long,
        currency: Currency
    ): InvestmentAmount {
        val money = Money(minor, currency)
        return if (kind == InvestmentMovementKind.Valuation) {
            InvestmentAmount.ofValue(money).value()
        } else {
            InvestmentAmount.of(money).value()
        }
    }

    private fun rate(rate: Rate, today: LocalDate) = DatedUsdRate(
        UsdRate.ofHundredths(rate.hundredths).value(),
        rate.on ?: today
    )

    private fun rejected(reason: Any) = "rechazado: ${name(reason)}"

    private fun name(reason: Any): String = reason::class.simpleName ?: reason.toString()

    /** The sandbox's day when step [index] runs, as the history's date steps set it. */
    private fun dayAt(loaded: Scenario, index: Int): LocalDate =
        loaded.steps.subList(0, index).filterIsInstance<AdvanceTo>().lastOrNull()?.date
            ?: loaded.start

    /** The expense the expense step [of] recorded, read back from its receipt. */
    private suspend fun recordedExpense(loaded: Scenario, of: Int): ExpenseId? {
        val found = expenses.find(OperationId.of(loaded.operation(of)).value())
        return (found as? Outcome.Success)?.value?.expenseId
    }

    /**
     * The identity the step making [key] received: kept from this process, else read back
     * from that step's receipt, as after a restart.
     */
    private suspend fun <T> identity(
        loaded: Scenario,
        key: String,
        known: MutableMap<String, T>,
        makes: (ScenarioStep) -> Boolean,
        find: suspend (OperationId) -> T?
    ): T? {
        known[key]?.let { return it }
        val index = loaded.steps.indexOfFirst(makes)
        if (index < 0) return null
        return find(OperationId.of(loaded.operation(index)).value())?.also { known[key] = it }
    }

    private suspend fun categoryId(loaded: Scenario, key: String) =
        identity(loaded, key, categoryIds, { it is Category && it.key == key }) {
            (categories.find(it) as? Outcome.Success)?.value?.categoryId
        }

    private suspend fun goalId(loaded: Scenario, key: String) =
        identity(loaded, key, goalIds, { it is Goal && it.key == key }) {
            (goals.find(it) as? Outcome.Success)?.value?.goalId
        }

    private suspend fun scheduleId(loaded: Scenario, key: String) =
        identity(loaded, key, scheduleIds, { it is Schedule && it.key == key }) {
            (schedules.commands.find(it) as? Outcome.Success)?.value?.scheduleId
        }

    private suspend fun investmentId(loaded: Scenario, key: String) =
        identity(loaded, key, investmentIds, { it is Investment && it.key == key }) {
            (investments.find(it) as? Outcome.Success)?.value?.investmentId
        }

    private fun <T> Outcome<T, *>.value(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> error("invalid history input: $error")
    }

    private companion object {
        const val ERASED = "La historia que se estaba cargando se borró; cárgala de nuevo."

        const val UNKNOWN = "no se sabe si se guardó; al intentarlo de nuevo usa la misma operación"

        /** The longest Deshacer the app offers, with a screen reader; real elapsed time. */
        val UNDO_FOR = 34.seconds
    }
}
