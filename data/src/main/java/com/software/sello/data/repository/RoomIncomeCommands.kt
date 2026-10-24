package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.DeletedIncomeEntity
import com.software.sello.data.local.entity.IncomeEntity
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDateError
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.CommittedIncome
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommandOutcome.Committed
import com.software.sello.domain.port.IncomeCommandOutcome.OutcomeUnknown
import com.software.sello.domain.port.IncomeCommandOutcome.Rejected
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.IncomeRejection
import com.software.sello.domain.port.IncomeUndoOffer
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreIncome
import java.time.LocalDate

/**
 * Records, changes, removes and puts back income, by the same steps as
 * [RoomExpenseCommands]: check the generation, answer a replay, validate, then write
 * the income, the revision and the receipt together. It has its own typed commands,
 * its own table and its own ordering sequence, and reads or writes nothing of an
 * expense, a category or a limit.
 *
 * [audit] stamps the rows and the receipt with real time. [clock] is only asked which
 * day it is, to refuse a future date. The offer to undo is [undo]'s, the one shared
 * with the expense workflow: a deletion here makes an earlier one of either kind final.
 */
class RoomIncomeCommands internal constructor(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val undo: UndoWindow
) : IncomeCommands {
    override fun undoOffer(): IncomeUndoOffer? =
        undo.offered(UndoWindow.Kind.Income)?.let { offer ->
            (IncomeId.of(offer.subjectId) as? Outcome.Success)
                ?.let { IncomeUndoOffer(offer.deletionId, it.value, offer.until) }
        }

    override suspend fun create(command: CreateIncome): IncomeCommandOutcome =
        run(command.operationId) { decide(command) }

    override suspend fun edit(command: EditIncome): IncomeCommandOutcome =
        run(command.operationId) { decide(command) }

    override suspend fun delete(command: DeleteIncome): IncomeCommandOutcome = undo.changing {
        val outcome = run(command.operationId) { decide(command) }
        // A replay is the original deletion, so it never starts the time again.
        if (outcome is Committed && !outcome.replayed) {
            undo.opened(UndoWindow.Kind.Income, command.operationId, command.incomeId.value)
        }
        outcome
    }

    override suspend fun restore(command: RestoreIncome): IncomeCommandOutcome = undo.changing {
        val outcome = run(command.operationId) { decide(command) }
        if (outcome is Committed && !outcome.replayed) undo.closed()
        outcome
    }

    private suspend fun run(
        operationId: OperationId,
        decide: suspend () -> IncomeCommandOutcome
    ): IncomeCommandOutcome = when (val run = database.runCommand(decide)) {
        is CommandRun.Finished -> run.value
        is CommandRun.RolledBack -> failed(run.failure)
        CommandRun.Unknown -> OutcomeUnknown(operationId)
    }

    private fun failed(failure: StorageFailure) = Rejected(IncomeRejection.StorageFailed(failure))

    private fun StoredReceipt.committedIncome() =
        CommittedIncome(receipt, checkNotNull(incomeId) { "Not an income receipt" })

    /** Steps 1 and 2 of every command. [proceed] runs only for an operation not seen before. */
    private suspend inline fun starting(
        operationId: OperationId,
        generation: Long,
        kind: OperationKind,
        digest: String,
        proceed: (FinancialProfile) -> IncomeCommandOutcome
    ): IncomeCommandOutcome =
        when (val start = database.startCommand(operationId, generation, kind, digest)) {
            is CommandStart.Proceed -> proceed(start.profile)
            is CommandStart.Stale -> Rejected(IncomeRejection.StaleGeneration(start.current))
            is CommandStart.Replay -> Committed(start.original.committedIncome(), true)
            CommandStart.Conflict -> Rejected(IncomeRejection.OperationConflict)
            is CommandStart.Damaged -> failed(start.failure)
        }

    /** Why [date] cannot be an income's date today, or null when it can. */
    private fun dateRejection(date: LocalDate): IncomeRejection? {
        val today = clock.today.value.date
        return when (val checked = EffectiveDates.forManualEntry(date, today)) {
            is Outcome.Success -> null

            is Outcome.Failure -> when (checked.error) {
                is EffectiveDateError.InFuture -> IncomeRejection.FutureDate(today)

                // Refused here, before the write: once stored it could not be read back.
                EffectiveDateError.OutOfRange,
                is EffectiveDateError.Malformed -> IncomeRejection.InvalidDate
            }
        }
    }

    /** Runs inside the transaction. Every rejection returns before the first write. */
    private suspend fun decide(command: CreateIncome): IncomeCommandOutcome {
        val kind = OperationKind.CreateIncome
        val digest = inputDigest(command)
        return starting(command.operationId, command.generation, kind, digest) { profile ->
            dateRejection(command.date)?.let { return Rejected(it) }

            val now = audit.now()
            val incomeId = when (val id = IncomeId.of(ids.next())) {
                is Outcome.Success -> id.value
                is Outcome.Failure -> error("The identifier source returned a malformed identifier")
            }
            val receipt = database.commitCommand(
                profile,
                command.operationId,
                kind,
                digest,
                incomeId.value,
                now
            )
            // A deleted income that can still come back keeps its place in the order.
            val highest = maxOf(
                database.incomeDao().highestSequence() ?: 0,
                database.deletedIncomeDao().highestSequence() ?: 0
            )
            val income = Income(
                id = incomeId,
                date = command.date,
                sequence = nextCounter(highest, IncomeEntity.TABLE, null, "sequence"),
                amount = command.amount,
                source = command.source,
                note = command.note,
                version = 1,
                createdAt = now,
                updatedAt = now
            )
            database.incomeDao().insert(income.toEntity())
            Committed(receipt.committedIncome(), replayed = false)
        }
    }

    /**
     * Runs inside the transaction. The income keeps its identity, its place in the
     * order and when it was first recorded; its version goes up by one. The date is
     * checked only when it changes: an income already dated after today can still have
     * its other fields corrected.
     */
    private suspend fun decide(command: EditIncome): IncomeCommandOutcome {
        val kind = OperationKind.EditIncome
        val digest = inputDigest(command)
        return starting(command.operationId, command.generation, kind, digest) { profile ->
            val row = database.incomeDao().byId(command.incomeId.value)
                ?: return Rejected(IncomeRejection.IncomeMissing)
            val current = when (val stored = row.toDomain()) {
                is Outcome.Success -> stored.value
                is Outcome.Failure -> return failed(stored.error)
            }
            if (current.version != command.expectedVersion) {
                return Rejected(IncomeRejection.VersionConflict(current.version))
            }
            val same = current.amount == command.amount &&
                current.source == command.source &&
                current.date == command.date &&
                current.note == command.note
            if (same) return Rejected(IncomeRejection.Unchanged)
            if (command.date != current.date) {
                dateRejection(command.date)?.let { return Rejected(it) }
            }

            val now = audit.now()
            val changed = database.incomeDao().update(
                id = row.id,
                expectedVersion = row.version,
                effectiveDate = EffectiveDates.storedText(command.date),
                amountMinor = command.amount.money.minorUnits,
                currency = command.amount.money.currency.code,
                sourceKey = command.source.key,
                sourceName = (command.source as? IncomeSource.Other)?.name?.value,
                note = command.note?.value,
                newVersion = nextCounter(row.version, IncomeEntity.TABLE, row.id, "version"),
                updatedAt = now.toEpochMilli()
            )
            check(changed == 1) { "The income changed inside the transaction that read it" }
            val receipt =
                database.commitCommand(profile, command.operationId, kind, digest, row.id, now)
            Committed(receipt.committedIncome(), replayed = false)
        }
    }

    /**
     * Runs inside the transaction. The row is moved, column for column, into the copy
     * kept for undo; any copy an earlier deletion left, of an income or an expense, is
     * removed, which makes that deletion final. The row is not interpreted.
     */
    private suspend fun decide(command: DeleteIncome): IncomeCommandOutcome {
        val kind = OperationKind.DeleteIncome
        val digest = inputDigest(command)
        return starting(command.operationId, command.generation, kind, digest) { profile ->
            val row = database.incomeDao().byId(command.incomeId.value)
                ?: return Rejected(IncomeRejection.IncomeMissing)
            if (row.version != command.expectedVersion) {
                return Rejected(IncomeRejection.VersionConflict(row.version))
            }

            val now = audit.now()
            database.discardKeptCopies()
            database.deletedIncomeDao().insert(
                DeletedIncomeEntity(
                    deletionId = command.operationId.value,
                    generation = profile.generation,
                    deletedAt = now.toEpochMilli(),
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
            val removed = database.incomeDao().delete(row.id, row.version)
            check(removed == 1) { "The income changed inside the transaction that read it" }
            val receipt =
                database.commitCommand(profile, command.operationId, kind, digest, row.id, now)
            Committed(receipt.committedIncome(), replayed = false)
        }
    }

    /**
     * Runs inside the transaction. A replay is answered first, whatever the time is.
     * Otherwise the deletion must be the one on offer and still in time, its copy must
     * belong to this history, and neither the income's identifier nor its place in the
     * order may be in use.
     */
    private suspend fun decide(command: RestoreIncome): IncomeCommandOutcome {
        val kind = OperationKind.RestoreIncome
        val digest = inputDigest(command)
        return starting(command.operationId, command.generation, kind, digest) { profile ->
            val inTime = undo.offered(UndoWindow.Kind.Income)?.deletionId == command.deletionId
            if (!inTime) return Rejected(IncomeRejection.UndoExpired)
            val kept = database.deletedIncomeDao().find(command.deletionId.value)
                ?: return Rejected(IncomeRejection.UndoExpired)
            val taken = kept.generation != profile.generation ||
                database.incomeDao().byId(kept.incomeId) != null ||
                database.incomeDao().countAtSequence(kept.sequence) != 0 ||
                kept.originTaken()
            if (taken) return Rejected(IncomeRejection.UndoConflict)

            database.incomeDao().insert(
                IncomeEntity(
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
            )
            val removed = database.deletedIncomeDao().remove(kept.deletionId)
            check(removed == 1) { "The kept copy changed inside the transaction that read it" }
            val receipt = database.commitCommand(
                profile,
                command.operationId,
                kind,
                digest,
                kept.incomeId,
                audit.now()
            )
            Committed(receipt.committedIncome(), replayed = false)
        }
    }

    override suspend fun find(operationId: OperationId): Outcome<CommittedIncome?, StorageFailure> =
        reading {
            when (
                val receipt = database.operationReceiptDao().find(
                    operationId.value
                )?.toStored()
            ) {
                null -> Outcome.Success(null)

                is Outcome.Failure -> receipt

                is Outcome.Success ->
                    Outcome.Success(receipt.value.incomeId?.let { receipt.value.committedIncome() })
            }
        }

    /** A kept occurrence cannot come back while another income records the same origin. */
    private suspend fun DeletedIncomeEntity.originTaken(): Boolean {
        val schedule = originScheduleId ?: return false
        val date = originDate ?: return false
        val slot = originSlot ?: return false
        return database.incomeDao().countAtOrigin(schedule, date, slot) != 0
    }
}
