package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.DeletedExpenseEntity
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDateError
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.CommittedExpense
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommandOutcome.Committed
import com.software.sello.domain.port.ExpenseCommandOutcome.OutcomeUnknown
import com.software.sello.domain.port.ExpenseCommandOutcome.Rejected
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.UndoOffer
import java.time.LocalDate

/**
 * Records, changes, removes and puts back expenses in one short transaction each:
 * check the generation, answer a replay, validate, then write the expense, the
 * revision and the receipt together.
 *
 * [audit] stamps the rows and the receipt with real time. [clock] is only asked which
 * day it is, to refuse a future date; simulated financial time never reaches a timestamp.
 *
 * The offer to undo is [undo]'s, shared with every other workflow that can delete.
 */
class RoomExpenseCommands internal constructor(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val undo: UndoWindow
) : ExpenseCommands {
    override fun undoOffer(): UndoOffer? = undo.offered(UndoWindow.Kind.Expense)?.let { offer ->
        (ExpenseId.of(offer.subjectId) as? Outcome.Success)
            ?.let { UndoOffer(offer.deletionId, it.value, offer.until) }
    }

    override suspend fun create(command: CreateExpense): ExpenseCommandOutcome =
        run(command.operationId) { decide(command) }

    override suspend fun edit(command: EditExpense): ExpenseCommandOutcome =
        run(command.operationId) { decide(command) }

    override suspend fun delete(command: DeleteExpense): ExpenseCommandOutcome = undo.changing {
        val outcome = run(command.operationId) { decide(command) }
        // A replay is the original deletion, so it never starts the time again.
        if (outcome is Committed && !outcome.replayed) {
            undo.opened(UndoWindow.Kind.Expense, command.operationId, command.expenseId.value)
        }
        outcome
    }

    override suspend fun restore(command: RestoreExpense): ExpenseCommandOutcome = undo.changing {
        val outcome = run(command.operationId) { decide(command) }
        if (outcome is Committed && !outcome.replayed) undo.closed()
        outcome
    }

    private suspend fun run(
        operationId: OperationId,
        decide: suspend () -> ExpenseCommandOutcome
    ): ExpenseCommandOutcome = when (val run = database.runCommand(decide)) {
        is CommandRun.Finished -> run.value
        is CommandRun.RolledBack -> failed(run.failure)
        CommandRun.Unknown -> OutcomeUnknown(operationId)
    }

    private fun failed(failure: StorageFailure) = Rejected(ExpenseRejection.StorageFailed(failure))

    private fun StoredReceipt.committedExpense() =
        CommittedExpense(receipt, checkNotNull(expenseId) { "Not an expense receipt" })

    /** Steps 1 and 2 of every command. [proceed] runs only for an operation not seen before. */
    private suspend inline fun starting(
        operationId: OperationId,
        generation: Long,
        kind: OperationKind,
        digest: String,
        proceed: (FinancialProfile) -> ExpenseCommandOutcome
    ): ExpenseCommandOutcome =
        when (val start = database.startCommand(operationId, generation, kind, digest)) {
            is CommandStart.Proceed -> proceed(start.profile)
            is CommandStart.Stale -> Rejected(ExpenseRejection.StaleGeneration(start.current))
            is CommandStart.Replay -> Committed(start.original.committedExpense(), true)
            CommandStart.Conflict -> Rejected(ExpenseRejection.OperationConflict)
            is CommandStart.Damaged -> failed(start.failure)
        }

    /** Why [date] cannot be an expense's date today, or null when it can. */
    private fun dateRejection(date: LocalDate): ExpenseRejection? {
        val today = clock.today.value.date
        return when (val checked = EffectiveDates.forManualEntry(date, today)) {
            is Outcome.Success -> null

            is Outcome.Failure -> when (checked.error) {
                is EffectiveDateError.InFuture -> ExpenseRejection.FutureDate(today)

                // Refused here, before the write: once stored it could not be read back.
                EffectiveDateError.OutOfRange,
                is EffectiveDateError.Malformed -> ExpenseRejection.InvalidDate
            }
        }
    }

    /** Why an expense cannot be put into [categoryId], or null when it can. */
    private suspend fun categoryRejection(categoryId: CategoryId): ExpenseCommandOutcome? {
        val category = database.categoryDao().byId(categoryId.value)
            ?: return Rejected(ExpenseRejection.CategoryMissing)
        return when (val stored = category.toDomain()) {
            is Outcome.Failure -> failed(stored.error)

            is Outcome.Success ->
                if (stored.value.archived) Rejected(ExpenseRejection.CategoryArchived) else null
        }
    }

    /** Runs inside the transaction. Every rejection returns before the first write. */
    private suspend fun decide(command: CreateExpense): ExpenseCommandOutcome {
        val kind = OperationKind.CreateExpense
        val digest = inputDigest(command)
        return starting(command.operationId, command.generation, kind, digest) { profile ->
            categoryRejection(command.categoryId)?.let { return it }
            dateRejection(command.date)?.let { return Rejected(it) }

            val now = audit.now()
            val expenseId = when (val id = ExpenseId.of(ids.next())) {
                is Outcome.Success -> id.value
                is Outcome.Failure -> error("The identifier source returned a malformed identifier")
            }
            val receipt = database.commitCommand(
                profile,
                command.operationId,
                kind,
                digest,
                expenseId.value,
                now
            )
            // A deleted expense that can still come back keeps its place in the order.
            val highest = maxOf(
                database.expenseDao().highestSequence() ?: 0,
                database.deletedExpenseDao().highestSequence() ?: 0
            )
            val expense = Expense(
                id = expenseId,
                categoryId = command.categoryId,
                date = command.date,
                sequence = nextCounter(highest, ExpenseEntity.TABLE, null, "sequence"),
                amount = command.amount,
                note = command.note,
                version = 1,
                createdAt = now,
                updatedAt = now
            )
            database.expenseDao().insert(expense.toEntity())
            Committed(receipt.committedExpense(), replayed = false)
        }
    }

    /**
     * Runs inside the transaction. The expense keeps its identity, its place in the
     * order and when it was first recorded; its version goes up by one. It may stay in
     * a category that was archived since, but it cannot be moved into an archived one.
     * Likewise it may keep a date that is after today, but cannot be moved to one.
     */
    private suspend fun decide(command: EditExpense): ExpenseCommandOutcome {
        val kind = OperationKind.EditExpense
        val digest = inputDigest(command)
        return starting(command.operationId, command.generation, kind, digest) { profile ->
            val row = database.expenseDao().byId(command.expenseId.value)
                ?: return Rejected(ExpenseRejection.ExpenseMissing)
            val current = when (val stored = row.toDomain()) {
                is Outcome.Success -> stored.value
                is Outcome.Failure -> return failed(stored.error)
            }
            if (current.version != command.expectedVersion) {
                return Rejected(ExpenseRejection.VersionConflict(current.version))
            }
            val same = current.categoryId == command.categoryId &&
                current.amount == command.amount &&
                current.date == command.date &&
                current.note == command.note
            if (same) return Rejected(ExpenseRejection.Unchanged)
            if (command.categoryId != current.categoryId) {
                categoryRejection(command.categoryId)?.let { return it }
            }
            // As with the category, only where it is moved to is checked: an expense
            // already dated after today can still have its other fields corrected.
            if (command.date != current.date) {
                dateRejection(command.date)?.let { return Rejected(it) }
            }

            val now = audit.now()
            val changed = database.expenseDao().update(
                id = row.id,
                expectedVersion = row.version,
                categoryId = command.categoryId.value,
                effectiveDate = EffectiveDates.storedText(command.date),
                amountMinor = command.amount.money.minorUnits,
                currency = command.amount.money.currency.code,
                note = command.note?.value,
                newVersion = nextCounter(row.version, ExpenseEntity.TABLE, row.id, "version"),
                updatedAt = now.toEpochMilli()
            )
            check(changed == 1) { "The expense changed inside the transaction that read it" }
            val receipt =
                database.commitCommand(profile, command.operationId, kind, digest, row.id, now)
            Committed(receipt.committedExpense(), replayed = false)
        }
    }

    /**
     * Runs inside the transaction. The row is moved, column for column, into the copy
     * kept for undo; any copy an earlier deletion left is removed, which makes that
     * deletion final. The row is not interpreted: undo puts back exactly what was here.
     */
    private suspend fun decide(command: DeleteExpense): ExpenseCommandOutcome {
        val kind = OperationKind.DeleteExpense
        val digest = inputDigest(command)
        return starting(command.operationId, command.generation, kind, digest) { profile ->
            val row = database.expenseDao().byId(command.expenseId.value)
                ?: return Rejected(ExpenseRejection.ExpenseMissing)
            if (row.version != command.expectedVersion) {
                return Rejected(ExpenseRejection.VersionConflict(row.version))
            }

            val now = audit.now()
            database.discardKeptCopies()
            database.deletedExpenseDao().insert(
                DeletedExpenseEntity(
                    deletionId = command.operationId.value,
                    generation = profile.generation,
                    deletedAt = now.toEpochMilli(),
                    expenseId = row.id,
                    categoryId = row.categoryId,
                    effectiveDate = row.effectiveDate,
                    sequence = row.sequence,
                    amountMinor = row.amountMinor,
                    currency = row.currency,
                    note = row.note,
                    version = row.version,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    originScheduleId = row.originScheduleId,
                    originDate = row.originDate,
                    originSlot = row.originSlot
                )
            )
            val removed = database.expenseDao().delete(row.id, row.version)
            check(removed == 1) { "The expense changed inside the transaction that read it" }
            val receipt =
                database.commitCommand(profile, command.operationId, kind, digest, row.id, now)
            Committed(receipt.committedExpense(), replayed = false)
        }
    }

    /**
     * Runs inside the transaction. A replay is answered first, whatever the time is.
     * Otherwise the deletion must be the one on offer and still in time, its copy must
     * belong to this history, and neither the expense's identifier nor its place in the
     * order may be in use. The category is not asked: the expense goes back where it was.
     */
    private suspend fun decide(command: RestoreExpense): ExpenseCommandOutcome {
        val kind = OperationKind.RestoreExpense
        val digest = inputDigest(command)
        return starting(command.operationId, command.generation, kind, digest) { profile ->
            val inTime =
                undo.offered(UndoWindow.Kind.Expense)?.deletionId == command.deletionId
            if (!inTime) return Rejected(ExpenseRejection.UndoExpired)
            val kept = database.deletedExpenseDao().find(command.deletionId.value)
                ?: return Rejected(ExpenseRejection.UndoExpired)
            val taken = kept.generation != profile.generation ||
                database.expenseDao().byId(kept.expenseId) != null ||
                database.expenseDao().countAtSequence(kept.sequence) != 0 ||
                kept.originTaken()
            if (taken) return Rejected(ExpenseRejection.UndoConflict)

            database.expenseDao().insert(
                ExpenseEntity(
                    id = kept.expenseId,
                    categoryId = kept.categoryId,
                    effectiveDate = kept.effectiveDate,
                    sequence = kept.sequence,
                    amountMinor = kept.amountMinor,
                    currency = kept.currency,
                    note = kept.note,
                    version = kept.version,
                    createdAt = kept.createdAt,
                    updatedAt = kept.updatedAt,
                    originScheduleId = kept.originScheduleId,
                    originDate = kept.originDate,
                    originSlot = kept.originSlot
                )
            )
            val removed = database.deletedExpenseDao().remove(kept.deletionId)
            check(removed == 1) { "The kept copy changed inside the transaction that read it" }
            val receipt = database.commitCommand(
                profile,
                command.operationId,
                kind,
                digest,
                kept.expenseId,
                audit.now()
            )
            Committed(receipt.committedExpense(), replayed = false)
        }
    }

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedExpense?, StorageFailure> = reading {
        when (val receipt = database.operationReceiptDao().find(operationId.value)?.toStored()) {
            null -> Outcome.Success(null)

            is Outcome.Failure -> receipt

            is Outcome.Success ->
                Outcome.Success(receipt.value.expenseId?.let { receipt.value.committedExpense() })
        }
    }

    /** A kept occurrence cannot come back while another expense records the same origin. */
    private suspend fun DeletedExpenseEntity.originTaken(): Boolean {
        val schedule = originScheduleId ?: return false
        val date = originDate ?: return false
        val slot = originSlot ?: return false
        return database.expenseDao().countAtOrigin(schedule, date, slot) != 0
    }
}
