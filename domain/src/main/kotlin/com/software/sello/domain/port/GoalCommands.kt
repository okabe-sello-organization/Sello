package com.software.sello.domain.port

import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalMovementLine
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalProgress
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/**
 * A change to goals (docs/testing/goal-examples.md). Every command names its attempt
 * with [operationId] and the history it was prepared against with [generation], as for
 * expenses. No goal command reads or writes an expense, an income, a category or a limit.
 */
sealed interface GoalCommand {
    val operationId: OperationId
    val generation: Long
}

/** A command about one existing goal, as the caller read it at [expectedVersion]. */
sealed interface GoalEdit : GoalCommand {
    val goalId: GoalId
    val expectedVersion: Long
}

data class CreateGoal(
    override val operationId: OperationId,
    override val generation: Long,
    val name: GoalName,
    val icon: IconKey,
    val target: TransactionAmount,
    val targetMonth: YearMonth?
) : GoalCommand

/** Name, icon and target month. The target itself changes with [ChangeGoalTarget]. */
data class EditGoal(
    override val operationId: OperationId,
    override val generation: Long,
    override val goalId: GoalId,
    override val expectedVersion: Long,
    val name: GoalName,
    val icon: IconKey,
    val targetMonth: YearMonth?
) : GoalEdit

/** A new target from this point of the history on (rule 7). */
data class ChangeGoalTarget(
    override val operationId: OperationId,
    override val generation: Long,
    override val goalId: GoalId,
    override val expectedVersion: Long,
    val target: TransactionAmount
) : GoalEdit

/** An aporte or a retiro, dated [date] (rule 9). */
data class RecordGoalMovement(
    override val operationId: OperationId,
    override val generation: Long,
    override val goalId: GoalId,
    override val expectedVersion: Long,
    val kind: GoalMovementKind,
    val amount: TransactionAmount,
    val date: LocalDate,
    val note: Note?
) : GoalEdit

/**
 * Up to [com.software.sello.domain.policy.GoalPolicy.MAX_RECEIPT_LINES] aportes and
 * retiros on one receipt, all recorded or none. The lines are recorded after the history
 * by date, a tie in line order, and checked in that order.
 */
data class RecordGoalMovements(
    override val operationId: OperationId,
    override val generation: Long,
    override val goalId: GoalId,
    override val expectedVersion: Long,
    val lines: List<GoalMovementLine>
) : GoalEdit

/** A correction in place (rule 6): the movement keeps its kind and recording position. */
data class EditGoalMovement(
    override val operationId: OperationId,
    override val generation: Long,
    override val goalId: GoalId,
    override val expectedVersion: Long,
    val movementId: GoalMovementId,
    val movementVersion: Long,
    val amount: TransactionAmount,
    val date: LocalDate,
    val note: Note?
) : GoalEdit

/** Removes a movement, with the shared six-second undo. */
data class DeleteGoalMovement(
    override val operationId: OperationId,
    override val generation: Long,
    override val goalId: GoalId,
    override val expectedVersion: Long,
    val movementId: GoalMovementId,
    val movementVersion: Long
) : GoalEdit

/** Puts back the movement the deletion [deletionId] removed; a new operation of its own. */
data class RestoreGoalMovement(
    override val operationId: OperationId,
    override val generation: Long,
    val deletionId: OperationId
) : GoalCommand

/**
 * Marcar como lograda (rule 8). [undoFor] is how long Deshacer is offered: about ten
 * seconds, longer while a screen reader is on. It is not part of the input's identity.
 */
data class CompleteGoal(
    override val operationId: OperationId,
    override val generation: Long,
    override val goalId: GoalId,
    override val expectedVersion: Long,
    val undoFor: Duration
) : GoalEdit

/** Deshacer after [CompleteGoal] [completionId], while it is on offer. */
data class UndoGoalCompletion(
    override val operationId: OperationId,
    override val generation: Long,
    val completionId: OperationId
) : GoalCommand

/** Removes the goal and every movement, with no undo (rule 14). */
data class DeleteGoal(
    override val operationId: OperationId,
    override val generation: Long,
    override val goalId: GoalId,
    override val expectedVersion: Long
) : GoalEdit

/** Chooses the goal Recibo shows (rule 12). */
data class FeatureGoal(
    override val operationId: OperationId,
    override val generation: Long,
    val goalId: GoalId
) : GoalCommand

/** Why a goal command wrote nothing, beyond the reasons every [CommandRejection] has. */
sealed interface GoalRejection {
    data object GoalMissing : GoalRejection

    data object MovementMissing : GoalRejection

    /** The change asks for exactly what is recorded. */
    data object Unchanged : GoalRejection

    /** A completed goal is read-only; it can only be deleted. */
    data object Completed : GoalRejection

    data class FutureDate(val today: LocalDate) : GoalRejection

    data object InvalidDate : GoalRejection

    /**
     * In recording order, [withdrawal] would take more than the [available] saved at that
     * point: a new retiro above what is saved, or a correction that would leave less than
     * zero (rule 1, MT08, MT15).
     */
    data class AboveSaved(val withdrawal: GoalMovementId, val available: Money) : GoalRejection

    /** A receipt of several movements has no line or more than its limit; [count] were sent. */
    data class LineCount(val count: Int) : GoalRejection

    /** The receipt's line [line], by its 0-based position as sent, is dated after [today]. */
    data class LineFutureDate(val line: Int, val today: LocalDate) : GoalRejection

    /** The receipt's line [line] has a date that cannot be stored. */
    data class LineInvalidDate(val line: Int) : GoalRejection

    /**
     * The retiro on the receipt's line [line] would take more than the [available] saved
     * before it: the history, then the receipt's lines dated earlier or on an earlier line.
     */
    data class LineAboveSaved(val line: Int, val available: Money) : GoalRejection

    /** A total would not fit an exact amount. */
    data object Unrepresentable : GoalRejection

    /** The time to undo is over, or this is not the change on offer. */
    data object UndoExpired : GoalRejection

    /** Undoing would contradict what is recorded now. */
    data object UndoConflict : GoalRejection
}

/**
 * A committed goal change and its receipt. [change] says which stamps it earned or lit
 * again, for a celebration; it is null for a replay, which is never celebrated again,
 * and for changes that cannot earn one. For [RecordGoalMovements] it is one change for
 * the whole receipt. [movementIds] are the movements [RecordGoalMovements] recorded, in
 * line order, for that command and its replay; empty for every other change and when the
 * receipt is found by asking, since its lines are not kept with it.
 */
data class CommittedGoal(
    val receipt: OperationReceipt,
    val goalId: GoalId,
    val change: GoalChange?,
    val movementIds: List<GoalMovementId> = emptyList()
)

typealias GoalCommandOutcome = CommandOutcome<CommittedGoal, GoalRejection>

/** The deleted movement or the completion that can still be undone. */
data class GoalUndoOffer(val operationId: OperationId, val goalId: GoalId, val until: Duration)

/**
 * Changes to goals, with the guarantees of [ExpenseCommands]: one operation writes once,
 * with its revision and receipt, in one transaction, and the same operation and input
 * again returns the original result.
 */
interface GoalCommands {
    suspend fun submit(command: GoalCommand): GoalCommandOutcome

    /** The deleted movement that can be put back now, or null; shared with expenses and income. */
    fun movementUndoOffer(): GoalUndoOffer?

    /** The completion that can be undone now, or null. Gone with the process. */
    fun completionUndoOffer(): GoalUndoOffer?

    suspend fun find(operationId: OperationId): Outcome<CommittedGoal?, StorageFailure>
}

/** One goal as the screens show it. */
data class GoalSummary(val history: GoalHistory, val progress: GoalProgress)

/**
 * Every goal, the earliest created first, read at one [revision] of one [generation].
 * [featured] is Recibo's goal after the fallback rule, or null.
 */
data class GoalBoard(
    val generation: Long,
    val revision: Long,
    val goals: List<GoalSummary>,
    val featured: GoalId?
)

/** The board as [GoalReads.observeBoard] sees it. A failed read keeps the last good board. */
typealias GoalBoardState = ReadState<GoalBoard, StorageFailure>

interface GoalReads {
    /** One damaged goal fails the read: it is never left out. */
    suspend fun board(): Outcome<GoalBoard, StorageFailure>

    /**
     * [board], read again after every committed change: Loading first, then each read.
     * A failure is a state with the last good board, never an empty one.
     */
    fun observeBoard(): Flow<GoalBoardState>

    suspend fun goal(id: GoalId): Outcome<GoalSummary?, StorageFailure>
}
