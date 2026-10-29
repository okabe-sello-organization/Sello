package com.software.sello.domain.port

import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import java.time.LocalDate
import kotlin.time.Duration

/**
 * A request to record one income: money that came in, declared by hand. [operationId]
 * names the attempt and [generation] the history it was prepared against, as for every
 * command. An income belongs to no category and changes no budget.
 */
data class CreateIncome(
    val operationId: OperationId,
    val generation: Long,
    val amount: TransactionAmount,
    val source: IncomeSource,
    val date: LocalDate,
    val note: Note?
)

/**
 * A request to change one recorded income, as the caller read it at [expectedVersion].
 * Its identity, its place in the order and when it was first recorded never change.
 */
data class EditIncome(
    val operationId: OperationId,
    val generation: Long,
    val incomeId: IncomeId,
    val expectedVersion: Long,
    val amount: TransactionAmount,
    val source: IncomeSource,
    val date: LocalDate,
    val note: Note?
)

/** A request to remove one recorded income, as the caller read it at [expectedVersion]. */
data class DeleteIncome(
    val operationId: OperationId,
    val generation: Long,
    val incomeId: IncomeId,
    val expectedVersion: Long
)

/**
 * A request to put back the income that the deletion [deletionId] removed. It is a new
 * operation of its own: [operationId] names this attempt to undo, not the deletion.
 */
data class RestoreIncome(
    val operationId: OperationId,
    val generation: Long,
    val deletionId: OperationId
)

/**
 * The one deleted income that can still be put back, as [UndoOffer] is for an expense.
 * There is one offer for both kinds: while this exists no expense deletion can be
 * undone, and a later deletion of either kind takes its place.
 */
data class IncomeUndoOffer(val deletionId: OperationId, val incomeId: IncomeId, val until: Duration)

/** Why an income command wrote nothing, beyond the reasons every [CommandRejection] has. */
sealed interface IncomeRejection {
    /** The date is after the financial current date. */
    data class FutureDate(val today: LocalDate) : IncomeRejection

    /** The date cannot be stored and read back. */
    data object InvalidDate : IncomeRejection

    /** There is no income with that identifier: never recorded, or deleted. */
    data object IncomeMissing : IncomeRejection

    /** The edit asks for exactly what is recorded, so there is nothing to save. */
    data object Unchanged : IncomeRejection

    /** The time to undo is over, or this deletion is not the one on offer. */
    data object UndoExpired : IncomeRejection

    /**
     * Putting the income back would overwrite or contradict what is recorded now: the
     * history was replaced, or its place has been taken. Nothing was restored.
     */
    data object UndoConflict : IncomeRejection
}

/**
 * A committed change to an income and the receipt that proves it: recorded, edited,
 * deleted or restored, as [OperationReceipt.kind] says. [incomeId] is the income it
 * was about, which after a deletion no longer exists.
 */
data class CommittedIncome(val receipt: OperationReceipt, val incomeId: IncomeId)

typealias IncomeCommandOutcome = CommandOutcome<CommittedIncome, IncomeRejection>

/**
 * Changes to recorded income, with the same guarantees as [ExpenseCommands]: one
 * operation writes once, with its revision and receipt, in one transaction, and the
 * same operation and input again returns the original result. No income command
 * reads or writes an expense, a category or a limit.
 */
interface IncomeCommands {
    suspend fun create(command: CreateIncome): IncomeCommandOutcome

    /** Changes the income once, if it is still at the version the caller read. */
    suspend fun edit(command: EditIncome): IncomeCommandOutcome

    /**
     * Removes the income once, if it is still at the version the caller read, and keeps
     * what it was so that [restore] can put it back while [undoOffer] lasts. It makes
     * any earlier deletion, of an income or an expense, final.
     */
    suspend fun delete(command: DeleteIncome): IncomeCommandOutcome

    /**
     * Puts back exactly the income a deletion removed, if that deletion is the one on
     * offer, its time has not run out, the history is the same one and nothing has
     * taken the income's place. Asked again with the same operation, it answers with
     * the original result whatever the time is by then.
     */
    suspend fun restore(command: RestoreIncome): IncomeCommandOutcome

    /**
     * The deleted income that can be put back right now, or null. Null once its time
     * has run out, when the deletion on offer is an expense's, and always in a process
     * that did not make the deletion.
     */
    fun undoOffer(): IncomeUndoOffer?

    /** The committed result of [operationId], or null when it committed no income change. */
    suspend fun find(operationId: OperationId): Outcome<CommittedIncome?, StorageFailure>
}
