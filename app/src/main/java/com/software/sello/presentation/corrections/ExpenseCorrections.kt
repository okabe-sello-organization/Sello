package com.software.sello.presentation.corrections

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteGoalMovement
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.DeleteInvestmentMovement
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.IncomeRejection
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreGoalMovement
import com.software.sello.domain.port.RestoreIncome
import com.software.sello.domain.port.RestoreInvestmentMovement
import com.software.sello.domain.port.UndoGoalCompletion
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What is said, for a moment, about the last correction of an expense. */
sealed interface CorrectionNotice {
    /**
     * An expense was deleted. With [canUndo] it can still be put back; [undoing] while
     * that has been asked, and [undoFailed] when the last attempt did not work and
     * another can be made.
     */
    data class Deleted(
        val canUndo: Boolean,
        val undoing: Boolean = false,
        val undoFailed: Boolean = false
    ) : CorrectionNotice

    /** The deleted expense is back. */
    data object Restored : CorrectionNotice

    /** The time to undo was over when undo was asked. The deletion stands. */
    data object UndoTooLate : CorrectionNotice

    /** Putting the expense back would contradict what is recorded now. The deletion stands. */
    data object UndoBlocked : CorrectionNotice

    /** An edit was saved. */
    data object Updated : CorrectionNotice

    /**
     * A category's changes were saved. [limitOf] is the finished month whose limit was
     * changed, when it was one: that month's figures now follow the new limit.
     */
    data class CategorySaved(val limitOf: YearMonth?) : CorrectionNotice

    data object CategoryArchived : CorrectionNotice

    data object CategoryUnarchived : CorrectionNotice

    /** The same as [Deleted], [Restored], [UndoTooLate], [UndoBlocked] and [Updated], for an income. */
    data class IncomeDeleted(
        val canUndo: Boolean,
        val undoing: Boolean = false,
        val undoFailed: Boolean = false
    ) : CorrectionNotice

    data object IncomeRestored : CorrectionNotice

    data object IncomeUndoTooLate : CorrectionNotice

    data object IncomeUndoBlocked : CorrectionNotice

    data object IncomeUpdated : CorrectionNotice

    /**
     * A save that storage confirmed, said with the confirmation look: a check before
     * [message]. Whoever saved writes the words from the record read back, so nothing is
     * said before the save is confirmed. [spoken] can carry the full amount.
     */
    data class Confirmed(val message: String, val spoken: String = message) : CorrectionNotice

    /**
     * A new expense storage confirmed, as it was read back: "Gasto anotado: 48.700 en
     * Comida". [amount] is null when it is saved but could not be read back, and
     * [categoryName] when its category could not be named; it is saved all the same.
     */
    data class ExpenseRecorded(val amount: MoneyTextValue?, val categoryName: String?) :
        CorrectionNotice

    /** The same for a new income: "Ingreso anotado: 2.000.000". */
    data class IncomeRecorded(val amount: MoneyTextValue?) : CorrectionNotice

    /**
     * A new schedule storage confirmed, as it was read back: "Pago fijo guardado: Arriendo,
     * el 5 de cada mes". [name] is its note, else its category or the name of an Otro
     * source; an income without either is named by [sourceKey]. [rule] and [start] are
     * null when it is saved but could not be read back.
     */
    data class ScheduleSaved(
        val name: String?,
        val rule: RepeatRule?,
        val start: LocalDate?,
        val income: Boolean,
        val sourceKey: String? = null
    ) : CorrectionNotice

    /** A schedule changed, paused, resumed or deleted, as its receipt confirmed. */
    data class ScheduleChanged(val change: ScheduleChange, val income: Boolean) : CorrectionNotice

    /** The same as [Deleted], [Restored], [UndoTooLate], [UndoBlocked] and [Updated], for a goal's movement. */
    data class GoalMovementDeleted(
        val canUndo: Boolean,
        val undoing: Boolean = false,
        val undoFailed: Boolean = false
    ) : CorrectionNotice

    data object GoalMovementRestored : CorrectionNotice

    data object GoalMovementUndoTooLate : CorrectionNotice

    data object GoalMovementUndoBlocked : CorrectionNotice

    data object GoalMovementUpdated : CorrectionNotice

    /** A goal marked lograda; with [canUndo] Deshacer returns it to 100 %, waiting. */
    data class GoalCompleted(
        val goalName: String,
        val canUndo: Boolean,
        val undoing: Boolean = false,
        val undoFailed: Boolean = false
    ) : CorrectionNotice

    data class GoalCompletionUndone(val goalName: String) : CorrectionNotice

    data object GoalCompletionUndoTooLate : CorrectionNotice

    /**
     * A new aporte, or with [withdrawal] a retiro, storage confirmed: "Aporte anotado:
     * 350.000". [amount] is null when it is saved but could not be read back.
     */
    data class GoalMovementRecorded(
        val withdrawal: Boolean,
        val amount: MoneyTextValue?,
        val celebration: GoalCelebration? = null
    ) : CorrectionNotice

    /** A new target, storage confirmed, that earned or relit a stamp (rule 7, MT18). */
    data class GoalTargetChanged(val celebration: GoalCelebration) : CorrectionNotice

    /** The same as [GoalMovementDeleted] and the notices after it, for an investment's movement. */
    data class InvestmentMovementDeleted(
        val canUndo: Boolean,
        val undoing: Boolean = false,
        val undoFailed: Boolean = false
    ) : InvestmentMovementNotice

    data object InvestmentMovementRestored : InvestmentMovementNotice

    data object InvestmentMovementUndoTooLate : InvestmentMovementNotice

    data object InvestmentMovementUndoBlocked : InvestmentMovementNotice

    data object InvestmentMovementUpdated : InvestmentMovementNotice

    /**
     * A new movement of [kind] storage confirmed: "Aporte anotado: 500.000". [amount] is
     * null when it is saved but could not be read back.
     */
    data class InvestmentMovementRecorded(
        val kind: InvestmentMovementKind,
        val amount: MoneyTextValue?
    ) : InvestmentMovementNotice
}

/** What is said about an investment's movement (SELLO-110), drawn in its own words. */
sealed interface InvestmentMovementNotice : CorrectionNotice

/** The stamps a confirmed save earned, if this notice says so. */
val CorrectionNotice.celebration: GoalCelebration?
    get() = when (this) {
        is CorrectionNotice.GoalMovementRecorded -> celebration
        is CorrectionNotice.GoalTargetChanged -> celebration
        else -> null
    }

/**
 * What one confirmed save did to a goal's stamps, to be celebrated once: places
 * [earned] for the first time, places [relit] after a withdrawal or a target change, and
 * whether the Meta was [reachedMeta], waiting to be marked. [missing] is what is still
 * left for the target. Never made for a replay or a save found after an unknown outcome.
 */
data class GoalCelebration(
    val goalId: String,
    val goalName: String,
    val earned: List<GoalPlace>,
    val relit: List<GoalPlace>,
    val reachedMeta: Boolean,
    val missing: MoneyTextValue? = null
)

/** How a deletion ended. Only [Deleted] means the expense is gone. */
enum class DeleteResult { Deleted, Gone, Changed, DataChanged, Failed }

/**
 * Deleting an expense, an income, a goal's movement or an investment's movement, undoing that, and what is said
 * about the last correction. One instance serves the whole process, so the offer to undo is the same
 * whatever screen is showing and however often it is redrawn, and a deletion is
 * finished and offered even if the screen that asked for it is gone. There is one
 * offer for every kind, as in storage: a later deletion takes its place. (It is named
 * for expenses because it started with them.)
 *
 * The time to undo belongs to the commands; this only stops showing the offer when
 * that time is over, by the same [monotonic] clock. [scope] must outlive screens and
 * run everything on the one thread these functions are called from.
 */
class ExpenseCorrections(
    private val commands: ExpenseCommands,
    private val incomes: IncomeCommands,
    private val goals: GoalCommands,
    private val investments: InvestmentCommands,
    private val monotonic: MonotonicClock,
    private val ids: RecordIdSource,
    private val scope: CoroutineScope
) {
    /**
     * The deletion on offer. [undoOperation] names every attempt to undo it, so an
     * attempt whose outcome was not known is asked again and never done twice.
     */
    private class Pending(
        val kind: Kind,
        val deletionId: OperationId,
        val generation: Long,
        val until: Duration,
        val undoOperation: OperationId,
        val goalName: String = ""
    )

    /** What the deletion on offer removed. */
    private enum class Kind { Expense, Income, GoalMovement, GoalCompletion, InvestmentMovement }

    /** How an attempt to undo ended. [Retry]: nothing was restored and nothing forbids it. */
    private enum class Undone { Restored, TooLate, Blocked, Retry }

    private val shown = MutableStateFlow<CorrectionNotice?>(null)
    val notice: StateFlow<CorrectionNotice?> = shown

    private var pending: Pending? = null
    private var ending: Job? = null
    private var landed: GoalCelebration? = null

    /** The offer in the words of what was deleted. */
    private fun offer(
        kind: Kind,
        canUndo: Boolean,
        undoing: Boolean = false,
        failed: Boolean = false,
        goalName: String = ""
    ) = when (kind) {
        Kind.Expense -> CorrectionNotice.Deleted(canUndo, undoing, failed)

        Kind.Income -> CorrectionNotice.IncomeDeleted(canUndo, undoing, failed)

        Kind.GoalMovement -> CorrectionNotice.GoalMovementDeleted(canUndo, undoing, failed)

        Kind.GoalCompletion -> CorrectionNotice.GoalCompleted(goalName, canUndo, undoing, failed)

        Kind.InvestmentMovement ->
            CorrectionNotice.InvestmentMovementDeleted(canUndo, undoing, failed)
    }

    /** Whether an offer is on screen, and if so whether it can be taken and is being taken. */
    private fun offerShown(): Pair<Boolean, Boolean>? = when (val now = shown.value) {
        is CorrectionNotice.Deleted -> now.canUndo to now.undoing
        is CorrectionNotice.IncomeDeleted -> now.canUndo to now.undoing
        is CorrectionNotice.GoalMovementDeleted -> now.canUndo to now.undoing
        is CorrectionNotice.GoalCompleted -> now.canUndo to now.undoing
        is CorrectionNotice.InvestmentMovementDeleted -> now.canUndo to now.undoing
        else -> null
    }

    /**
     * Deletes the expense as the caller read it. It runs to its end even if the caller
     * is cancelled meanwhile; the caller then gets the cancellation, not a result.
     */
    suspend fun delete(
        expenseId: ExpenseId,
        expectedVersion: Long,
        generation: Long
    ): DeleteResult = scope.async {
        val operation = operation()
        val command = DeleteExpense(operation, generation, expenseId, expectedVersion)
        val committed = when (val outcome = commands.delete(command)) {
            is ExpenseCommandOutcome.Committed -> true

            // Asked, never sent again: only a receipt says it was deleted.
            is ExpenseCommandOutcome.OutcomeUnknown ->
                (commands.find(operation) as? Outcome.Success)?.value != null

            is ExpenseCommandOutcome.Rejected -> return@async when (outcome.reason) {
                ExpenseRejection.ExpenseMissing -> DeleteResult.Gone
                is ExpenseRejection.VersionConflict -> DeleteResult.Changed
                is ExpenseRejection.StaleGeneration -> DeleteResult.DataChanged
                else -> DeleteResult.Failed
            }
        }
        if (!committed) return@async DeleteResult.Failed
        val until = commands.undoOffer()?.takeIf { it.deletionId == operation }?.until
        deleted(Kind.Expense, operation, generation, until)
    }.await()

    /** The same for an income. */
    suspend fun deleteIncome(command: DeleteIncome): IncomeCommandOutcome = scope.async {
        val outcome = incomes.delete(command)
        if (outcome is IncomeCommandOutcome.Committed) incomeDeleted(outcome.income.receipt)
        outcome
    }.await()

    fun incomeDeleted(receipt: OperationReceipt) {
        val until = incomes.undoOffer()?.takeIf { it.deletionId == receipt.operationId }?.until
        deleted(Kind.Income, receipt.operationId, receipt.generation, until)
    }

    suspend fun deleteIncome(
        incomeId: IncomeId,
        expectedVersion: Long,
        generation: Long
    ): DeleteResult = scope.async {
        val operation = operation()
        val command = DeleteIncome(operation, generation, incomeId, expectedVersion)
        val committed = when (val outcome = incomes.delete(command)) {
            is IncomeCommandOutcome.Committed -> true

            is IncomeCommandOutcome.OutcomeUnknown ->
                (incomes.find(operation) as? Outcome.Success)?.value != null

            is IncomeCommandOutcome.Rejected -> return@async when (outcome.reason) {
                IncomeRejection.IncomeMissing -> DeleteResult.Gone
                is IncomeRejection.VersionConflict -> DeleteResult.Changed
                is IncomeRejection.StaleGeneration -> DeleteResult.DataChanged
                else -> DeleteResult.Failed
            }
        }
        if (!committed) return@async DeleteResult.Failed
        val until = incomes.undoOffer()?.takeIf { it.deletionId == operation }?.until
        deleted(Kind.Income, operation, generation, until)
    }.await()

    /** A deletion committed. With [until] it can be undone until then; without, it is only said. */
    private fun deleted(
        kind: Kind,
        operation: OperationId,
        generation: Long,
        until: Duration?,
        goalName: String = ""
    ): DeleteResult {
        if (until == null) {
            pending = null
            show(offer(kind, canUndo = false, goalName = goalName), monotonic.elapsed() + NOTICE)
        } else {
            pending = Pending(kind, operation, generation, until, operation(), goalName)
            show(offer(kind, canUndo = true, goalName = goalName), until)
        }
        return DeleteResult.Deleted
    }

    /** Puts back the deletion on offer. Asked again while that is being done, it does nothing. */
    fun undo() {
        val offered = pending ?: return
        val (canUndo, undoing) = offerShown() ?: return
        if (!canUndo || undoing) return
        // Once asked, the answer decides what is said next, not the time running out.
        ending?.cancel()
        shown.value =
            offer(offered.kind, canUndo = true, undoing = true, goalName = offered.goalName)
        scope.launch {
            settle(
                offered,
                when (offered.kind) {
                    Kind.Expense -> restore(offered)
                    Kind.Income -> restoreIncome(offered)
                    Kind.GoalMovement -> restoreGoalMovement(offered)
                    Kind.GoalCompletion -> undoCompletion(offered)
                    Kind.InvestmentMovement -> restoreInvestmentMovement(offered)
                }
            )
        }
    }

    /** An outcome that is not known is asked about with the same operation, never sent as a new one. */
    private suspend fun restore(offered: Pending): Undone {
        val command = RestoreExpense(offered.undoOperation, offered.generation, offered.deletionId)
        return when (val outcome = commands.restore(command)) {
            is ExpenseCommandOutcome.Committed -> Undone.Restored

            is ExpenseCommandOutcome.OutcomeUnknown -> {
                val found = (commands.find(offered.undoOperation) as? Outcome.Success)?.value
                if (found != null) Undone.Restored else Undone.Retry
            }

            is ExpenseCommandOutcome.Rejected -> when (outcome.reason) {
                ExpenseRejection.UndoExpired -> Undone.TooLate
                ExpenseRejection.UndoConflict, is ExpenseRejection.StaleGeneration -> Undone.Blocked
                else -> Undone.Retry
            }
        }
    }

    private suspend fun restoreIncome(offered: Pending): Undone {
        val command = RestoreIncome(offered.undoOperation, offered.generation, offered.deletionId)
        return when (val outcome = incomes.restore(command)) {
            is IncomeCommandOutcome.Committed -> Undone.Restored

            is IncomeCommandOutcome.OutcomeUnknown -> {
                val found = (incomes.find(offered.undoOperation) as? Outcome.Success)?.value
                if (found != null) Undone.Restored else Undone.Retry
            }

            is IncomeCommandOutcome.Rejected -> when (outcome.reason) {
                IncomeRejection.UndoExpired -> Undone.TooLate
                IncomeRejection.UndoConflict, is IncomeRejection.StaleGeneration -> Undone.Blocked
                else -> Undone.Retry
            }
        }
    }

    private fun settle(offered: Pending, outcome: Undone) {
        // A later deletion took this one's place while it was being undone.
        if (pending !== offered) return
        val kind = offered.kind
        when {
            outcome == Undone.Restored -> said(
                when (kind) {
                    Kind.Expense -> CorrectionNotice.Restored
                    Kind.Income -> CorrectionNotice.IncomeRestored
                    Kind.GoalMovement -> CorrectionNotice.GoalMovementRestored
                    Kind.GoalCompletion -> CorrectionNotice.GoalCompletionUndone(offered.goalName)
                    Kind.InvestmentMovement -> CorrectionNotice.InvestmentMovementRestored
                }
            )

            outcome == Undone.Blocked -> said(
                when (kind) {
                    Kind.Expense -> CorrectionNotice.UndoBlocked
                    Kind.Income -> CorrectionNotice.IncomeUndoBlocked
                    Kind.GoalMovement -> CorrectionNotice.GoalMovementUndoBlocked
                    Kind.GoalCompletion -> CorrectionNotice.GoalCompletionUndoTooLate
                    Kind.InvestmentMovement -> CorrectionNotice.InvestmentMovementUndoBlocked
                }
            )

            // Nothing was restored and nothing forbids it: it can be asked again while
            // the time lasts.
            outcome == Undone.Retry && monotonic.elapsed() < offered.until ->
                show(
                    offer(kind, canUndo = true, failed = true, goalName = offered.goalName),
                    offered.until
                )

            else -> said(
                when (kind) {
                    Kind.Expense -> CorrectionNotice.UndoTooLate
                    Kind.Income -> CorrectionNotice.IncomeUndoTooLate
                    Kind.GoalMovement -> CorrectionNotice.GoalMovementUndoTooLate
                    Kind.GoalCompletion -> CorrectionNotice.GoalCompletionUndoTooLate
                    Kind.InvestmentMovement -> CorrectionNotice.InvestmentMovementUndoTooLate
                }
            )
        }
    }

    /**
     * Deletes a goal's movement and offers to undo it, in the movement's own words. It runs
     * to its end even if the caller is cancelled meanwhile.
     */
    suspend fun deleteGoalMovement(command: DeleteGoalMovement): GoalCommandOutcome = scope.async {
        val outcome = goals.submit(command)
        if (outcome is GoalCommandOutcome.Committed) goalMovementDeleted(outcome.goal.receipt)
        outcome
    }.await()

    /** A movement's deletion committed, as found again after its outcome was not known. */
    fun goalMovementDeleted(receipt: OperationReceipt) {
        val until = goals.movementUndoOffer()
            ?.takeIf { it.operationId == receipt.operationId }?.until
        deleted(Kind.GoalMovement, receipt.operationId, receipt.generation, until)
    }

    private suspend fun restoreGoalMovement(offered: Pending): Undone {
        val command =
            RestoreGoalMovement(offered.undoOperation, offered.generation, offered.deletionId)
        return when (val outcome = goals.submit(command)) {
            is GoalCommandOutcome.Committed -> Undone.Restored

            is GoalCommandOutcome.OutcomeUnknown -> {
                val found = (goals.find(offered.undoOperation) as? Outcome.Success)?.value
                if (found != null) Undone.Restored else Undone.Retry
            }

            is GoalCommandOutcome.Rejected -> when (outcome.reason) {
                GoalRejection.UndoExpired -> Undone.TooLate
                GoalRejection.UndoConflict, is GoalRejection.StaleGeneration -> Undone.Blocked
                else -> Undone.Retry
            }
        }
    }

    /**
     * Deletes an investment's movement and offers to undo it, as for a goal's movement. It
     * runs to its end even if the caller is cancelled meanwhile.
     */
    suspend fun deleteInvestmentMovement(
        command: DeleteInvestmentMovement
    ): InvestmentCommandOutcome = scope.async {
        val outcome = investments.submit(command)
        if (outcome is InvestmentCommandOutcome.Committed) {
            investmentMovementDeleted(outcome.change.receipt)
        }
        outcome
    }.await()

    /** An investment movement's deletion committed, as found again after its outcome was not known. */
    fun investmentMovementDeleted(receipt: OperationReceipt) {
        val until = investments.movementUndoOffer()
            ?.takeIf { it.operationId == receipt.operationId }?.until
        deleted(Kind.InvestmentMovement, receipt.operationId, receipt.generation, until)
    }

    private suspend fun restoreInvestmentMovement(offered: Pending): Undone {
        val command =
            RestoreInvestmentMovement(offered.undoOperation, offered.generation, offered.deletionId)
        return when (val outcome = investments.submit(command)) {
            is InvestmentCommandOutcome.Committed -> Undone.Restored

            is InvestmentCommandOutcome.OutcomeUnknown -> {
                val found = (investments.find(offered.undoOperation) as? Outcome.Success)?.value
                if (found != null) Undone.Restored else Undone.Retry
            }

            is InvestmentCommandOutcome.Rejected -> when (outcome.reason) {
                InvestmentRejection.UndoExpired -> Undone.TooLate

                InvestmentRejection.UndoConflict, is InvestmentRejection.StaleGeneration ->
                    Undone.Blocked

                else -> Undone.Retry
            }
        }
    }

    /** A screen reports that a correction of an investment's movement was saved. */
    fun investmentMovementUpdated() = said(CorrectionNotice.InvestmentMovementUpdated)

    /** A screen reports a new investment movement its receipt confirmed. */
    fun investmentMovementRecorded(notice: CorrectionNotice.InvestmentMovementRecorded) =
        said(notice)

    /**
     * Marks a goal lograda and offers Deshacer, in its name, for the time [command] asked.
     * It runs to its end even if the caller is cancelled meanwhile.
     */
    suspend fun completeGoal(command: CompleteGoal, goalName: String): GoalCommandOutcome =
        scope.async {
            val outcome = goals.submit(command)
            if (outcome is GoalCommandOutcome.Committed) {
                goalCompleted(outcome.goal.receipt, goalName)
            }
            outcome
        }.await()

    /** A completion committed, as found again after its outcome was not known. */
    fun goalCompleted(receipt: OperationReceipt, goalName: String) {
        val until = goals.completionUndoOffer()
            ?.takeIf { it.operationId == receipt.operationId }?.until
        deleted(Kind.GoalCompletion, receipt.operationId, receipt.generation, until, goalName)
    }

    private suspend fun undoCompletion(offered: Pending): Undone {
        val command =
            UndoGoalCompletion(offered.undoOperation, offered.generation, offered.deletionId)
        return when (val outcome = goals.submit(command)) {
            is GoalCommandOutcome.Committed -> Undone.Restored

            is GoalCommandOutcome.OutcomeUnknown -> {
                val found = (goals.find(offered.undoOperation) as? Outcome.Success)?.value
                if (found != null) Undone.Restored else Undone.Retry
            }

            is GoalCommandOutcome.Rejected -> when (outcome.reason) {
                GoalRejection.UndoExpired -> Undone.TooLate
                GoalRejection.UndoConflict, is GoalRejection.StaleGeneration -> Undone.Blocked
                else -> Undone.Retry
            }
        }
    }

    /** A screen reports that a correction of a goal's movement was saved. */
    fun goalMovementUpdated() = said(CorrectionNotice.GoalMovementUpdated)

    /** A screen reports a new aporte or retiro its receipt confirmed, read back from storage. */
    fun goalMovementRecorded(notice: CorrectionNotice.GoalMovementRecorded) = said(notice)

    /**
     * Whether [celebration]'s stamps come down now: only the first time a screen asks, so
     * reopening the goal or redrawing it while the notice is said does not land them again.
     * Each save makes its own celebration, so a later save lands again.
     */
    fun lands(celebration: GoalCelebration): Boolean {
        if (landed === celebration) return false
        landed = celebration
        return true
    }

    /** A screen reports a new target whose receipt earned or relit a stamp. */
    fun goalTargetChanged(celebration: GoalCelebration) =
        said(CorrectionNotice.GoalTargetChanged(celebration))

    /** A screen reports that an edit of an expense was saved. */
    fun updated() = said(CorrectionNotice.Updated)

    /** A screen reports that an edit of an income was saved. */
    fun incomeUpdated() = said(CorrectionNotice.IncomeUpdated)

    /**
     * A screen reports a save that storage confirmed. Like every statement it takes the
     * place of whatever is shown, an offer to undo included.
     */
    fun confirmed(notice: CorrectionNotice.Confirmed) = said(notice)

    /** A screen reports a new expense its receipt confirmed, read back from storage. */
    fun recorded(notice: CorrectionNotice.ExpenseRecorded) = said(notice)

    /** A screen reports a new income its receipt confirmed, read back from storage. */
    fun incomeRecorded(notice: CorrectionNotice.IncomeRecorded) = said(notice)

    /** A screen reports a new schedule its receipt confirmed, read back from storage. */
    fun scheduleSaved(notice: CorrectionNotice.ScheduleSaved) = said(notice)

    /** A screen reports a schedule change its receipt confirmed. */
    fun scheduleChanged(notice: CorrectionNotice.ScheduleChanged) = said(notice)

    /** A screen reports a category change its receipt confirmed. */
    fun categoryChanged(notice: CorrectionNotice) {
        require(
            notice !is CorrectionNotice.Deleted && notice !is CorrectionNotice.IncomeDeleted &&
                notice !is CorrectionNotice.GoalMovementDeleted &&
                notice !is CorrectionNotice.InvestmentMovementDeleted
        ) {
            "An offer to undo is not a statement"
        }
        said(notice)
    }

    fun dismiss() {
        if (offerShown()?.second == true) return
        ending?.cancel()
        pending = null
        shown.value = null
    }

    /**
     * Call when the app is back in view. Waiting does not advance while a device
     * sleeps but the time to undo does, so the offer is checked against the clock again.
     */
    fun refresh() {
        val offered = pending ?: return
        val (canUndo, undoing) = offerShown() ?: return
        if (canUndo && !undoing) shown.value?.let { show(it, offered.until) }
    }

    /** Something that is over: said for a moment, with nothing left to undo. */
    private fun said(notice: CorrectionNotice) {
        pending = null
        show(notice, monotonic.elapsed() + NOTICE)
    }

    /** Shows [notice] until the clock reads [until]. */
    private fun show(notice: CorrectionNotice, until: Duration) {
        ending?.cancel()
        if (monotonic.elapsed() >= until) {
            pending = null
            shown.value = null
            return
        }
        shown.value = notice
        ending = scope.launch {
            var left = until - monotonic.elapsed()
            while (left > Duration.ZERO) {
                delay(left)
                left = until - monotonic.elapsed()
            }
            pending = null
            shown.value = null
        }
    }

    private fun operation(): OperationId = when (val id = OperationId.of(ids.next())) {
        is Outcome.Success -> id.value
        is Outcome.Failure -> error("The identifier source returned a malformed identifier")
    }

    private companion object {
        /** How long something that needs no answer stays on screen. */
        val NOTICE = 4.seconds
    }
}

/** What a confirmed command did to a schedule. */
enum class ScheduleChange { Changed, Paused, Resumed, Deleted }
