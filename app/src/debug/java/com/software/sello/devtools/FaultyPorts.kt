package com.software.sello.devtools

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryInsightsSnapshots
import com.software.sello.domain.port.CategoryInsightsState
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreIncome
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.SnapshotState
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** The storage failure a simulated read reports; its reason names it as simulated. */
val SimulatedStorageFailure = StorageFailure.Unavailable("SimulatedFault")

/** [SimulatedStorageFailure] as a month snapshot reports it. */
val SimulatedReadFailure = SnapshotFailure.Storage(SimulatedStorageFailure)

/** Thrown into a caller by [Fault.InterruptAfterSave], after the write committed. */
class SimulatedInterruption :
    CancellationException("simulated interruption after a committed write")

/**
 * Applies the command fault due, if any, around [write] and records what the caller
 * was told. [unknown] is the answer for an outcome the caller cannot know.
 */
private suspend fun <T> faulted(
    faults: DebugFaults,
    log: OperationLog,
    operation: OperationId,
    kind: String,
    unknown: T,
    describe: (T) -> String,
    write: suspend () -> T
): T {
    val fault = faults.takeCommandFault()
    if (fault == Fault.FailBeforeStorage) {
        log.record(LoggedOperation(operation, kind, describe(unknown), fault))
        return unknown
    }
    val outcome = write()
    return when (fault) {
        Fault.LostAnswer -> {
            log.record(LoggedOperation(operation, kind, describe(unknown), fault))
            unknown
        }

        Fault.InterruptAfterSave -> {
            log.record(LoggedOperation(operation, kind, "Interrumpido", fault))
            throw SimulatedInterruption()
        }

        else -> outcome.also { log.record(LoggedOperation(operation, kind, describe(it), null)) }
    }
}

private fun saved(replayed: Boolean) = if (replayed) "Ya estaba guardado" else "Guardado"

private fun told(outcome: Any): String = when (outcome) {
    is ExpenseCommandOutcome.Committed -> saved(outcome.replayed)
    is IncomeCommandOutcome.Committed -> saved(outcome.replayed)
    is CategoryCommandOutcome.Committed -> saved(outcome.replayed)
    is GoalCommandOutcome.Committed -> saved(outcome.replayed)
    is ScheduleCommandOutcome.Committed -> saved(outcome.replayed)
    is InvestmentCommandOutcome.Committed -> saved(outcome.replayed)
    is ExpenseCommandOutcome.Rejected -> "Rechazado: ${outcome.reason::class.simpleName}"
    is IncomeCommandOutcome.Rejected -> "Rechazado: ${outcome.reason::class.simpleName}"
    is CategoryCommandOutcome.Rejected -> "Rechazado: ${outcome.reason::class.simpleName}"
    is GoalCommandOutcome.Rejected -> "Rechazado: ${outcome.reason::class.simpleName}"
    is ScheduleCommandOutcome.Rejected -> "Rechazado: ${outcome.reason::class.simpleName}"
    is InvestmentCommandOutcome.Rejected -> "Rechazado: ${outcome.reason::class.simpleName}"
    else -> "Desconocido"
}

class FaultyExpenseCommands(
    private val commands: ExpenseCommands,
    private val faults: DebugFaults,
    private val log: OperationLog
) : ExpenseCommands by commands {
    private suspend fun run(
        operation: OperationId,
        kind: String,
        write: suspend () -> ExpenseCommandOutcome
    ) = faulted(
        faults,
        log,
        operation,
        kind,
        ExpenseCommandOutcome.OutcomeUnknown(operation),
        ::told,
        write
    )

    override suspend fun create(command: CreateExpense) =
        run(command.operationId, "Gasto: anotar") { commands.create(command) }

    override suspend fun edit(command: EditExpense) =
        run(command.operationId, "Gasto: corregir") { commands.edit(command) }

    override suspend fun delete(command: DeleteExpense) =
        run(command.operationId, "Gasto: borrar") { commands.delete(command) }

    override suspend fun restore(command: RestoreExpense) =
        run(command.operationId, "Gasto: deshacer") { commands.restore(command) }
}

class FaultyIncomeCommands(
    private val commands: IncomeCommands,
    private val faults: DebugFaults,
    private val log: OperationLog
) : IncomeCommands by commands {
    private suspend fun run(
        operation: OperationId,
        kind: String,
        write: suspend () -> IncomeCommandOutcome
    ) = faulted(
        faults,
        log,
        operation,
        kind,
        IncomeCommandOutcome.OutcomeUnknown(operation),
        ::told,
        write
    )

    override suspend fun create(command: CreateIncome) =
        run(command.operationId, "Ingreso: anotar") { commands.create(command) }

    override suspend fun edit(command: EditIncome) =
        run(command.operationId, "Ingreso: corregir") { commands.edit(command) }

    override suspend fun delete(command: DeleteIncome) =
        run(command.operationId, "Ingreso: borrar") { commands.delete(command) }

    override suspend fun restore(command: RestoreIncome) =
        run(command.operationId, "Ingreso: deshacer") { commands.restore(command) }
}

class FaultyCategoryCommands(
    private val commands: CategoryCommands,
    private val faults: DebugFaults,
    private val log: OperationLog
) : CategoryCommands by commands {
    override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome = faulted(
        faults,
        log,
        command.operationId,
        "Categoría: ${command::class.simpleName}",
        CategoryCommandOutcome.OutcomeUnknown(command.operationId),
        ::told
    ) { commands.submit(command) }
}

/** Every goal command, logged with what its caller was told. */
class FaultyGoalCommands(
    private val commands: GoalCommands,
    private val faults: DebugFaults,
    private val log: OperationLog
) : GoalCommands by commands {
    override suspend fun submit(command: GoalCommand): GoalCommandOutcome = faulted(
        faults,
        log,
        command.operationId,
        "Meta: ${command::class.simpleName}",
        GoalCommandOutcome.OutcomeUnknown(command.operationId),
        ::told
    ) { commands.submit(command) }
}

/** Every investment command, logged with what its caller was told. */
class FaultyInvestmentCommands(
    private val commands: InvestmentCommands,
    private val faults: DebugFaults,
    private val log: OperationLog
) : InvestmentCommands by commands {
    override suspend fun submit(command: InvestmentCommand): InvestmentCommandOutcome = faulted(
        faults,
        log,
        command.operationId,
        "Inversión: ${command::class.simpleName}",
        InvestmentCommandOutcome.OutcomeUnknown(command.operationId),
        ::told
    ) { commands.submit(command) }
}

/** Every schedule command, logged with what its caller was told. */
class FaultyScheduleCommands(
    private val commands: ScheduleCommands,
    private val faults: DebugFaults,
    private val log: OperationLog
) : ScheduleCommands by commands {
    override suspend fun submit(command: ScheduleCommand): ScheduleCommandOutcome = faulted(
        faults,
        log,
        command.operationId,
        "Programado: ${command::class.simpleName}",
        ScheduleCommandOutcome.OutcomeUnknown(command.operationId),
        ::told
    ) { commands.submit(command) }
}

/**
 * Passes Recibo's reads through, and for each read failure armed tells the open reads
 * that the latest read failed, with the last good snapshot kept, as a real failed read
 * does. A retry reads again and gets real data.
 */
class FaultyMonthlySnapshots(
    private val snapshots: MonthlySnapshots,
    private val faults: DebugFaults
) : MonthlySnapshots by snapshots {
    override fun observe(month: YearMonth): Flow<SnapshotState> = channelFlow {
        var lastGood: MonthlySnapshot? = null
        launch {
            faults.readFailures.collect {
                send(SnapshotState.Failed(SimulatedReadFailure, lastGood))
            }
        }
        snapshots.observe(month).collect { state ->
            if (state is SnapshotState.Ready) lastGood = state.snapshot
            send(state)
        }
    }
}

/** The same as [FaultyMonthlySnapshots] for Resumen. */
class FaultyResumenSnapshots(
    private val snapshots: ResumenSnapshots,
    private val faults: DebugFaults
) : ResumenSnapshots by snapshots {
    override fun observe(month: YearMonth): Flow<ResumenSnapshotState> = channelFlow {
        var lastGood: ResumenSnapshot? = null
        launch {
            faults.readFailures.collect {
                send(ResumenSnapshotState.Failed(SimulatedReadFailure, lastGood))
            }
        }
        snapshots.observe(month).collect { state ->
            if (state is ResumenSnapshotState.Ready) lastGood = state.snapshot
            send(state)
        }
    }
}

/** Category statistics share the sandbox's read-failure control. */
class FaultyCategoryInsightsSnapshots(
    private val snapshots: CategoryInsightsSnapshots,
    private val faults: DebugFaults
) : CategoryInsightsSnapshots by snapshots {
    override fun observe(categoryId: CategoryId, month: YearMonth): Flow<CategoryInsightsState> =
        channelFlow {
            var lastGood: CategoryInsights? = null
            launch {
                faults.readFailures.collect {
                    send(CategoryInsightsState.Failed(SimulatedReadFailure, lastGood))
                }
            }
            snapshots.observe(categoryId, month).collect { state ->
                if (state is CategoryInsightsState.Ready) lastGood = state.snapshot
                send(state)
            }
        }
}

/** Inversiones, an investment and Dólar share the sandbox's read-failure control. */
class FaultyInvestmentReads(private val reads: InvestmentReads, private val faults: DebugFaults) :
    InvestmentReads by reads {
    override fun observeBoard(): Flow<InvestmentBoardState> = channelFlow {
        var lastGood: InvestmentBoard? = null
        launch {
            faults.readFailures.collect {
                send(InvestmentBoardState.Failed(SimulatedStorageFailure, lastGood))
            }
        }
        reads.observeBoard().collect { state ->
            if (state is InvestmentBoardState.Ready) lastGood = state.board
            send(state)
        }
    }
}

/** Metas, the goal page and Recibo's goal share the sandbox's read-failure control. */
class FaultyGoalReads(private val reads: GoalReads, private val faults: DebugFaults) :
    GoalReads by reads {
    override fun observeBoard(): Flow<GoalBoardState> = channelFlow {
        var lastGood: GoalBoard? = null
        launch {
            faults.readFailures.collect {
                send(GoalBoardState.Failed(SimulatedStorageFailure, lastGood))
            }
        }
        reads.observeBoard().collect { state ->
            if (state is GoalBoardState.Ready) lastGood = state.board
            send(state)
        }
    }
}
