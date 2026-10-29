package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.DeletedExpenseEntity
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedExpense
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.UndoOffer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Records, changes, removes and puts back expenses by the steps every ledger shares,
 * [LedgerCommands]: check the generation, answer a replay, validate, then write the
 * expense, the revision and the receipt together.
 *
 * What is the expense's own: it belongs to a category that must exist and not be
 * archived, and a change that reaches a closed month of a carry chain is a correction
 * (ADR 0020 CP21). [carryRejection] lets it through only when the list of later months it
 * changes was seen for this very change at the current revision, and never when a month
 * could no longer be calculated afterwards.
 */
class RoomExpenseCommands internal constructor(
    private val database: SelloDatabase,
    audit: AuditClock,
    private val clock: FinancialClock,
    ids: RecordIdSource,
    undo: UndoWindow
) : ExpenseCommands {
    private val ledger = LedgerCommands(database, audit, clock, ids, undo, ExpenseRecords(database))

    override fun undoOffer(): UndoOffer? = ledger.offered()?.let { offer ->
        (ExpenseId.of(offer.subjectId) as? Outcome.Success)
            ?.let { UndoOffer(offer.deletionId, it.value, offer.until) }
    }

    /** Every rejection returns before the first write. */
    override suspend fun create(command: CreateExpense): ExpenseCommandOutcome = ledger.create(
        command.operationId,
        command.generation,
        OperationKind.CreateExpense,
        inputDigest(command),
        validate = {
            categoryRejection(command.categoryId) ?: ledger.dateRejection(command.date)
        },
        build = { rawId, sequence, now ->
            Expense(
                id = issued(ExpenseId.of(rawId)),
                categoryId = command.categoryId,
                date = command.date,
                sequence = sequence,
                amount = command.amount,
                note = command.note,
                version = 1,
                createdAt = now,
                updatedAt = now
            ).toEntity()
        },
        confirm = { profile, expense ->
            carryRejection(
                profile,
                null,
                expense,
                correctionIdentity(command),
                command.confirmation
            )
        }
    )

    /**
     * It may stay in a category that was archived since, but it cannot be moved into an
     * archived one. Likewise it may keep a date that is after today, but cannot be moved
     * to one.
     */
    override suspend fun edit(command: EditExpense): ExpenseCommandOutcome = ledger.edit(
        command.operationId,
        command.generation,
        OperationKind.EditExpense,
        inputDigest(command),
        command.expenseId.value,
        command.expectedVersion,
        validate = validate@{ profile, row, current ->
            val same = current.categoryId == command.categoryId &&
                current.amount == command.amount &&
                current.date == command.date &&
                current.note == command.note
            if (same) return@validate Rejected(Specific(ExpenseRejection.Unchanged))
            if (command.categoryId != current.categoryId) {
                categoryRejection(command.categoryId)?.let { return@validate it }
            }
            // As with the category, only where it is moved to is checked: an expense
            // already dated after today can still have its other fields corrected.
            if (command.date != current.date) {
                ledger.dateRejection(command.date)?.let { return@validate it }
            }
            val edited = current.copy(
                categoryId = command.categoryId,
                amount = command.amount,
                date = command.date,
                note = command.note
            ).toEntity()
            carryRejection(profile, row, edited, correctionIdentity(command), command.confirmation)
        },
        update = { row, newVersion, now ->
            database.expenseDao().update(
                id = row.id,
                expectedVersion = row.version,
                categoryId = command.categoryId.value,
                effectiveDate = EffectiveDates.storedText(command.date),
                amountMinor = command.amount.money.minorUnits,
                currency = command.amount.money.currency.code,
                note = command.note?.value,
                newVersion = newVersion,
                updatedAt = now.toEpochMilli()
            )
        }
    )

    override suspend fun delete(command: DeleteExpense): ExpenseCommandOutcome = ledger.delete(
        command.operationId,
        command.generation,
        OperationKind.DeleteExpense,
        inputDigest(command),
        command.expenseId.value,
        command.expectedVersion,
        confirm = { profile, row ->
            carryRejection(profile, row, null, correctionIdentity(command), command.confirmation)
        }
    )

    /** The category is not asked: the expense goes back where it was. */
    override suspend fun restore(command: RestoreExpense): ExpenseCommandOutcome = ledger.restore(
        command.operationId,
        command.generation,
        OperationKind.RestoreExpense,
        inputDigest(command),
        command.deletionId,
        // Undoing a deletion in a closed chain month is a correction too (CP21).
        confirm = { profile, restored ->
            carryRejection(
                profile,
                null,
                restored,
                correctionIdentity(command),
                command.confirmation
            )
        }
    )

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedExpense?, StorageFailure> = ledger.find(operationId)

    /** Why an expense cannot be put into [categoryId], or null when it can. */
    private suspend fun categoryRejection(categoryId: CategoryId): ExpenseCommandOutcome? {
        val category = database.categoryDao().byId(categoryId.value)
            ?: return Rejected(Specific(ExpenseRejection.CategoryMissing))
        return when (val stored = category.toDomain()) {
            is Outcome.Failure -> storageFailed(stored.error)

            is Outcome.Success ->
                if (stored.value.archived) {
                    Rejected(
                        Specific(ExpenseRejection.CategoryArchived)
                    )
                } else {
                    null
                }
        }
    }

    /**
     * Why replacing [removed] with [added] cannot commit for carry, or null when it can
     * (CP20–CP23). Asked before the first write, inside the transaction.
     */
    private suspend fun carryRejection(
        profile: FinancialProfile,
        removed: ExpenseEntity?,
        added: ExpenseEntity?,
        change: String,
        confirmation: CorrectionConfirmation?
    ): ExpenseCommandOutcome? {
        val today = clock.today.value.date
        return database.expenseCarry(
            removed,
            added,
            YearMonth.from(today),
            today,
            profile.revision,
            change,
            confirmation
        )?.let { Rejected(it) }
    }
}

private typealias ExpenseLedger =
    LedgerRecords<ExpenseEntity, DeletedExpenseEntity, Expense, CommittedExpense, ExpenseRejection>

/** The expense table, its kept copy and its own words, for [LedgerCommands]. */
private class ExpenseRecords(private val database: SelloDatabase) : ExpenseLedger {
    override val undoKind = UndoWindow.Kind.Expense
    override val table = ExpenseEntity.TABLE
    override val noun = "expense"

    override fun futureDate(today: LocalDate) = ExpenseRejection.FutureDate(today)

    override val invalidDate = ExpenseRejection.InvalidDate
    override val missing = ExpenseRejection.ExpenseMissing
    override val undoExpired = ExpenseRejection.UndoExpired
    override val undoConflict = ExpenseRejection.UndoConflict

    override fun committed(stored: StoredReceipt) =
        stored.expenseId?.let { CommittedExpense(stored.receipt, it) }

    override fun facts(row: ExpenseEntity) = LedgerRow(
        row.id,
        row.version,
        row.sequence,
        row.originScheduleId,
        row.originDate,
        row.originSlot
    )

    override fun decode(row: ExpenseEntity) = row.toDomain()

    override suspend fun byId(id: String) = database.expenseDao().byId(id)

    override suspend fun highestSequence() = maxOf(
        database.expenseDao().highestSequence() ?: 0,
        database.deletedExpenseDao().highestSequence() ?: 0
    )

    override suspend fun countAtSequence(sequence: Long) =
        database.expenseDao().countAtSequence(sequence)

    override suspend fun countAtOrigin(scheduleId: String, date: String, slot: Int) =
        database.expenseDao().countAtOrigin(scheduleId, date, slot)

    override suspend fun insert(row: ExpenseEntity) = database.expenseDao().insert(row)

    override suspend fun delete(row: ExpenseEntity) =
        database.expenseDao().delete(row.id, row.version)

    override suspend fun keep(
        row: ExpenseEntity,
        deletionId: OperationId,
        generation: Long,
        deletedAt: Instant
    ) = database.deletedExpenseDao().insert(
        DeletedExpenseEntity(
            deletionId = deletionId.value,
            generation = generation,
            deletedAt = deletedAt.toEpochMilli(),
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

    override suspend fun kept(deletionId: OperationId) =
        database.deletedExpenseDao().find(deletionId.value)

    override fun generation(kept: DeletedExpenseEntity) = kept.generation

    override fun restored(kept: DeletedExpenseEntity) = ExpenseEntity(
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

    override suspend fun forget(kept: DeletedExpenseEntity) =
        database.deletedExpenseDao().remove(kept.deletionId)
}
