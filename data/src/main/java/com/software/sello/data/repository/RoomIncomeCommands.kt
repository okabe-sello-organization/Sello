package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.DeletedIncomeEntity
import com.software.sello.data.local.entity.IncomeEntity
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedIncome
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.IncomeRejection
import com.software.sello.domain.port.IncomeUndoOffer
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreIncome
import java.time.Instant
import java.time.LocalDate

/**
 * Records, changes, removes and puts back income by the steps every ledger shares,
 * [LedgerCommands], as [RoomExpenseCommands] does for expenses. It has its own typed
 * commands, its own table and its own ordering sequence, and reads or writes nothing of
 * an expense, a category or a limit. The offer to undo is the one shared with the
 * expense workflow: a deletion here makes an earlier one of either kind final.
 */
class RoomIncomeCommands internal constructor(
    private val database: SelloDatabase,
    audit: AuditClock,
    clock: FinancialClock,
    ids: RecordIdSource,
    undo: UndoWindow
) : IncomeCommands {
    private val ledger = LedgerCommands(database, audit, clock, ids, undo, IncomeRecords(database))

    override fun undoOffer(): IncomeUndoOffer? = ledger.offered()?.let { offer ->
        (IncomeId.of(offer.subjectId) as? Outcome.Success)
            ?.let { IncomeUndoOffer(offer.deletionId, it.value, offer.until) }
    }

    override suspend fun create(command: CreateIncome): IncomeCommandOutcome = ledger.create(
        command.operationId,
        command.generation,
        OperationKind.CreateIncome,
        inputDigest(command),
        validate = { ledger.dateRejection(command.date) },
        build = { rawId, sequence, now ->
            Income(
                id = issued(IncomeId.of(rawId)),
                date = command.date,
                sequence = sequence,
                amount = command.amount,
                source = command.source,
                note = command.note,
                version = 1,
                createdAt = now,
                updatedAt = now
            ).toEntity()
        },
        confirm = { _, _ -> null }
    )

    /**
     * The date is checked only when it changes: an income already dated after today can
     * still have its other fields corrected.
     */
    override suspend fun edit(command: EditIncome): IncomeCommandOutcome = ledger.edit(
        command.operationId,
        command.generation,
        OperationKind.EditIncome,
        inputDigest(command),
        command.incomeId.value,
        command.expectedVersion,
        validate = validate@{ _, _, current ->
            val same = current.amount == command.amount &&
                current.source == command.source &&
                current.date == command.date &&
                current.note == command.note
            if (same) return@validate Rejected(Specific(IncomeRejection.Unchanged))
            if (command.date != current.date) ledger.dateRejection(command.date) else null
        },
        update = { row, newVersion, now ->
            database.incomeDao().update(
                id = row.id,
                expectedVersion = row.version,
                effectiveDate = EffectiveDates.storedText(command.date),
                amountMinor = command.amount.money.minorUnits,
                currency = command.amount.money.currency.code,
                sourceKey = command.source.key,
                sourceName = (command.source as? IncomeSource.Other)?.name?.value,
                note = command.note?.value,
                newVersion = newVersion,
                updatedAt = now.toEpochMilli()
            )
        }
    )

    override suspend fun delete(command: DeleteIncome): IncomeCommandOutcome = ledger.delete(
        command.operationId,
        command.generation,
        OperationKind.DeleteIncome,
        inputDigest(command),
        command.incomeId.value,
        command.expectedVersion,
        confirm = { _, _ -> null }
    )

    override suspend fun restore(command: RestoreIncome): IncomeCommandOutcome = ledger.restore(
        command.operationId,
        command.generation,
        OperationKind.RestoreIncome,
        inputDigest(command),
        command.deletionId,
        confirm = { _, _ -> null }
    )

    override suspend fun find(operationId: OperationId): Outcome<CommittedIncome?, StorageFailure> =
        ledger.find(operationId)
}

/** The income table, its kept copy and its own words, for [LedgerCommands]. */
private class IncomeRecords(private val database: SelloDatabase) :
    LedgerRecords<IncomeEntity, DeletedIncomeEntity, Income, CommittedIncome, IncomeRejection> {
    override val undoKind = UndoWindow.Kind.Income
    override val table = IncomeEntity.TABLE
    override val noun = "income"

    override fun futureDate(today: LocalDate) = IncomeRejection.FutureDate(today)

    override val invalidDate = IncomeRejection.InvalidDate
    override val missing = IncomeRejection.IncomeMissing
    override val undoExpired = IncomeRejection.UndoExpired
    override val undoConflict = IncomeRejection.UndoConflict

    override fun committed(stored: StoredReceipt) =
        stored.incomeId?.let { CommittedIncome(stored.receipt, it) }

    override fun facts(row: IncomeEntity) = LedgerRow(
        row.id,
        row.version,
        row.sequence,
        row.originScheduleId,
        row.originDate,
        row.originSlot
    )

    override fun decode(row: IncomeEntity) = row.toDomain()

    override suspend fun byId(id: String) = database.incomeDao().byId(id)

    override suspend fun highestSequence() = maxOf(
        database.incomeDao().highestSequence() ?: 0,
        database.deletedIncomeDao().highestSequence() ?: 0
    )

    override suspend fun countAtSequence(sequence: Long) =
        database.incomeDao().countAtSequence(sequence)

    override suspend fun countAtOrigin(scheduleId: String, date: String, slot: Int) =
        database.incomeDao().countAtOrigin(scheduleId, date, slot)

    override suspend fun insert(row: IncomeEntity) = database.incomeDao().insert(row)

    override suspend fun delete(row: IncomeEntity) =
        database.incomeDao().delete(row.id, row.version)

    override suspend fun keep(
        row: IncomeEntity,
        deletionId: OperationId,
        generation: Long,
        deletedAt: Instant
    ) = database.deletedIncomeDao().insert(
        DeletedIncomeEntity(
            deletionId = deletionId.value,
            generation = generation,
            deletedAt = deletedAt.toEpochMilli(),
            incomeId = row.id,
            effectiveDate = row.effectiveDate,
            sequence = row.sequence,
            amountMinor = row.amountMinor,
            currency = row.currency,
            sourceKey = row.sourceKey,
            sourceName = row.sourceName,
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
        database.deletedIncomeDao().find(deletionId.value)

    override fun generation(kept: DeletedIncomeEntity) = kept.generation

    override fun restored(kept: DeletedIncomeEntity) = IncomeEntity(
        id = kept.incomeId,
        effectiveDate = kept.effectiveDate,
        sequence = kept.sequence,
        amountMinor = kept.amountMinor,
        currency = kept.currency,
        sourceKey = kept.sourceKey,
        sourceName = kept.sourceName,
        note = kept.note,
        version = kept.version,
        createdAt = kept.createdAt,
        updatedAt = kept.updatedAt,
        originScheduleId = kept.originScheduleId,
        originDate = kept.originDate,
        originSlot = kept.originSlot
    )

    override suspend fun forget(kept: DeletedIncomeEntity) =
        database.deletedIncomeDao().remove(kept.deletionId)
}
