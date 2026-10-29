package com.software.sello.domain.port

import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.CarryCorrection
import java.time.LocalDate
import kotlin.time.Duration

/**
 * A request to record one new expense. [operationId] names this attempt and
 * [generation] is the history the caller was looking at when it was prepared.
 * Everything except [operationId] is the input a replay is compared against.
 *
 * [confirmation], on this and the other expense commands, is only for a correction that
 * changes later months of a carry chain (ADR 0020 CP20–CP22): what came with the list of
 * changes the person saw and confirmed for this very change.
 */
data class CreateExpense(
    val operationId: OperationId,
    val generation: Long,
    val categoryId: CategoryId,
    val amount: TransactionAmount,
    val date: LocalDate,
    val note: Note?,
    val confirmation: CorrectionConfirmation? = null
)

/**
 * A request to change one recorded expense. [expectedVersion] is the version the
 * caller read: the change is made only to that version, so it can never overwrite a
 * change the caller has not seen. The four fields are the expense as it should be
 * afterwards; when it was first recorded and its place in the order do not change.
 */
data class EditExpense(
    val operationId: OperationId,
    val generation: Long,
    val expenseId: ExpenseId,
    val expectedVersion: Long,
    val categoryId: CategoryId,
    val amount: TransactionAmount,
    val date: LocalDate,
    val note: Note?,
    val confirmation: CorrectionConfirmation? = null
)

/** A request to remove one recorded expense, as the caller read it at [expectedVersion]. */
data class DeleteExpense(
    val operationId: OperationId,
    val generation: Long,
    val expenseId: ExpenseId,
    val expectedVersion: Long,
    val confirmation: CorrectionConfirmation? = null
)

/**
 * A request to put back the expense that the deletion [deletionId] removed. It is a new
 * operation of its own: [operationId] names this attempt to undo, not the deletion.
 */
data class RestoreExpense(
    val operationId: OperationId,
    val generation: Long,
    val deletionId: OperationId,
    val confirmation: CorrectionConfirmation? = null
)

/**
 * The one deletion that can still be undone. [until] is the reading of the monotonic
 * clock at which it stops being so: [UndoPolicy.WINDOW] after the deletion committed,
 * in real elapsed time. It exists only in the process that made the deletion.
 */
data class UndoOffer(val deletionId: OperationId, val expenseId: ExpenseId, val until: Duration)

/** Why an expense command wrote nothing, beyond the reasons every [CommandRejection] has. */
sealed interface ExpenseRejection {
    data object CategoryMissing : ExpenseRejection

    data object CategoryArchived : ExpenseRejection

    /** The date is after the financial current date. */
    data class FutureDate(val today: LocalDate) : ExpenseRejection

    /** The date cannot be stored and read back; see `EffectiveDateError.OutOfRange`. */
    data object InvalidDate : ExpenseRejection

    /** There is no expense with that identifier: never recorded, or already deleted. */
    data object ExpenseMissing : ExpenseRejection

    /** The edit asks for exactly what is already recorded, so there is nothing to save. */
    data object Unchanged : ExpenseRejection

    /**
     * The deletion can no longer be undone: its time ran out, another deletion took its
     * place, or it was made before the app was last started.
     */
    data object UndoExpired : ExpenseRejection

    /**
     * Putting the expense back would overwrite or contradict what is recorded now: the
     * history was replaced, or its place has been taken. Nothing was restored.
     */
    data object UndoConflict : ExpenseRejection

    /**
     * The change reaches a closed month of a carry chain and changes later months, and it
     * was not confirmed for this very change at the current revision (ADR 0020 CP20–CP22).
     * [correction] is the list to show; confirming sends the same change with [confirmation].
     */
    data class CorrectionUnconfirmed(
        val confirmation: CorrectionConfirmation,
        val correction: CarryCorrection
    ) : ExpenseRejection

    /** The category's carry could not be calculated before or after the change (CP8, CP23). */
    data class CarryUnavailable(val failure: CarryoverFailure) : ExpenseRejection
}

/**
 * A committed change to an expense and the receipt that proves it: recorded, edited,
 * deleted or restored, as [OperationReceipt.kind] says. [expenseId] is the expense it
 * was about, which after a deletion no longer exists.
 */
data class CommittedExpense(val receipt: OperationReceipt, val expenseId: ExpenseId)

typealias ExpenseCommandOutcome = CommandOutcome<CommittedExpense, ExpenseRejection>

interface ExpenseCommands {
    /**
     * Records the expense once. Validation, the expense, the revision and the receipt
     * are one transaction: all of it happens or none of it. Submitting the same
     * operation and input again returns the original receipt.
     *
     * A cancelled caller gets the cancellation, which says nothing about whether the
     * expense was saved; [find] does.
     */
    suspend fun create(command: CreateExpense): ExpenseCommandOutcome

    /**
     * Changes the expense once, if it is still at the version the caller read. The
     * expense, the revision and the receipt are one transaction. A saved edit is not a
     * new expense: when it was first recorded and its order are kept.
     */
    suspend fun edit(command: EditExpense): ExpenseCommandOutcome

    /**
     * Removes the expense once, if it is still at the version the caller read, and keeps
     * what it was so that [restore] can put it back while [undoOffer] lasts. A deletion
     * that takes the place of an earlier one makes the earlier one final.
     */
    suspend fun delete(command: DeleteExpense): ExpenseCommandOutcome

    /**
     * Puts back exactly the expense a deletion removed, if that deletion is the one
     * [undoOffer] names, its time has not run out, the history is the same one and
     * nothing has taken the expense's place. Asked again with the same operation, it
     * answers with the original result whatever the time is by then.
     */
    suspend fun restore(command: RestoreExpense): ExpenseCommandOutcome

    /**
     * The deletion that can be undone right now, or null. Null once its time has run
     * out, and always null in a process that did not make the deletion.
     */
    fun undoOffer(): UndoOffer?

    /** The committed result of [operationId], or null when it did not commit. */
    suspend fun find(operationId: OperationId): Outcome<CommittedExpense?, StorageFailure>
}
