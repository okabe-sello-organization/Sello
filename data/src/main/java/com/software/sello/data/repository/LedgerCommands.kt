package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDateError
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.RecordIdSource
import java.time.Instant
import java.time.LocalDate

/** What every ledger row holds that [LedgerCommands] reads, whatever else it records. */
internal data class LedgerRow(
    val id: String,
    val version: Long,
    val sequence: Long,
    val originScheduleId: String?,
    val originDate: String?,
    val originSlot: Int?
)

/**
 * What one ledger, expenses or income, has of its own for [LedgerCommands]: its rows
 * and the copies kept for undo, its stored record [D], what a receipt proves [C], and
 * its own reasons to refuse [F]. The steps around them are the same for both.
 */
internal interface LedgerRecords<Row : Any, Kept : Any, D : Any, C : Any, F : Any> {
    val undoKind: UndoWindow.Kind

    /** The table, to name a counter that can go no further. */
    val table: String

    /** What one record is called in a fault message. */
    val noun: String

    fun futureDate(today: LocalDate): F

    val invalidDate: F

    /** There is no record with that identifier: never recorded, or already deleted. */
    val missing: F

    val undoExpired: F

    val undoConflict: F

    /** What [stored] proves was committed, or null when it is not about this ledger. */
    fun committed(stored: StoredReceipt): C?

    fun facts(row: Row): LedgerRow

    fun decode(row: Row): Outcome<D, StorageFailure>

    suspend fun byId(id: String): Row?

    /** The highest sequence held by a row or by a kept copy, which keeps its place. */
    suspend fun highestSequence(): Long

    suspend fun countAtSequence(sequence: Long): Int

    suspend fun countAtOrigin(scheduleId: String, date: String, slot: Int): Int

    suspend fun insert(row: Row)

    /** Removes [row] only while it is still at its version. Returns the rows removed. */
    suspend fun delete(row: Row): Int

    /** Keeps [row], column for column, as the copy the deletion [deletionId] can put back. */
    suspend fun keep(row: Row, deletionId: OperationId, generation: Long, deletedAt: Instant)

    suspend fun kept(deletionId: OperationId): Kept?

    fun generation(kept: Kept): Long

    /** The row exactly as it was before [kept]'s deletion. */
    fun restored(kept: Kept): Row

    /** Removes the kept copy. Returns how many rows were removed. */
    suspend fun forget(kept: Kept): Int
}

/** A check that may refuse a change before anything is written; null lets it through. */
internal typealias LedgerCheck<Row, C, F> =
    suspend (profile: FinancialProfile, row: Row) -> CommandOutcome<C, F>?

/** [LedgerCheck] for an edit, which also sees the record as stored. */
internal typealias LedgerEditCheck<Row, D, C, F> =
    suspend (profile: FinancialProfile, row: Row, current: D) -> CommandOutcome<C, F>?

/**
 * Records, changes, removes and puts back one ledger's records in one short transaction
 * each: check the generation, answer a replay, validate, then write the record, the
 * revision and the receipt together. The ledger's own rules come in as checks, run
 * inside the transaction before the first write.
 *
 * [audit] stamps the rows and the receipt with real time. [clock] is only asked which
 * day it is, to refuse a future date. The offer to undo is [undo]'s, shared with every
 * other workflow that can delete.
 */
internal class LedgerCommands<Row : Any, Kept : Any, D : Any, C : Any, F : Any>(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val undo: UndoWindow,
    private val records: LedgerRecords<Row, Kept, D, C, F>
) {
    /** The deletion of this ledger that can be undone right now, or null. */
    fun offered(): UndoWindow.Pending? = undo.offered(records.undoKind)

    /**
     * Runs inside the transaction. [validate] may refuse before anything is made;
     * [build] makes the row from a fresh identifier, the next place in the order and the
     * time; [confirm] may still refuse the row before it is written.
     */
    suspend fun create(
        operationId: OperationId,
        generation: Long,
        kind: OperationKind,
        digest: String,
        validate: suspend (FinancialProfile) -> CommandOutcome<C, F>?,
        build: (rawId: String, sequence: Long, now: Instant) -> Row,
        confirm: LedgerCheck<Row, C, F>
    ): CommandOutcome<C, F> = database.runOutcome(operationId) {
        starting(operationId, generation, kind, digest) { profile ->
            validate(profile)?.let { return@starting it }

            val now = audit.now()
            val raw = ids.next()
            // A deleted record that can still come back keeps its place in the order.
            val sequence = nextCounter(records.highestSequence(), records.table, null, "sequence")
            val row = build(raw, sequence, now)
            confirm(profile, row)?.let { return@starting it }
            val receipt = database.commitCommand(
                profile,
                operationId,
                kind,
                digest,
                records.facts(row).id,
                now
            )
            records.insert(row)
            freshlyCommitted(receipt)
        }
    }

    /**
     * Runs inside the transaction. The record keeps its identity, its place in the order
     * and when it was first recorded; its version goes up by one. [validate] sees the
     * record as stored and may refuse; [update] writes the change at [Row]'s version.
     */
    suspend fun edit(
        operationId: OperationId,
        generation: Long,
        kind: OperationKind,
        digest: String,
        id: String,
        expectedVersion: Long,
        validate: LedgerEditCheck<Row, D, C, F>,
        update: suspend (row: Row, newVersion: Long, now: Instant) -> Int
    ): CommandOutcome<C, F> = database.runOutcome(operationId) {
        starting(operationId, generation, kind, digest) { profile ->
            val row = records.byId(id) ?: return@starting Rejected(Specific(records.missing))
            val current = when (val stored = records.decode(row)) {
                is Outcome.Success -> stored.value
                is Outcome.Failure -> return@starting storageFailed(stored.error)
            }
            val facts = records.facts(row)
            if (facts.version != expectedVersion) {
                return@starting Rejected(CommandRejection.VersionConflict(facts.version))
            }
            validate(profile, row, current)?.let { return@starting it }

            val now = audit.now()
            val newVersion = nextCounter(facts.version, records.table, facts.id, "version")
            val changed = update(row, newVersion, now)
            check(changed == 1) {
                "The ${records.noun} changed inside the transaction that read it"
            }
            freshlyCommitted(
                database.commitCommand(profile, operationId, kind, digest, facts.id, now)
            )
        }
    }

    /**
     * Runs inside the transaction. The row is moved, column for column, into the copy
     * kept for undo; any copy an earlier deletion left, of any kind, is removed, which
     * makes that deletion final. The row is not interpreted: undo puts back exactly what
     * was here. A replay is the original deletion, so it never starts the time again.
     */
    suspend fun delete(
        operationId: OperationId,
        generation: Long,
        kind: OperationKind,
        digest: String,
        id: String,
        expectedVersion: Long,
        confirm: LedgerCheck<Row, C, F>
    ): CommandOutcome<C, F> = undo.changing {
        val outcome = database.runOutcome(operationId) {
            starting(operationId, generation, kind, digest) { profile ->
                val row = records.byId(id) ?: return@starting Rejected(Specific(records.missing))
                val facts = records.facts(row)
                if (facts.version != expectedVersion) {
                    return@starting Rejected(CommandRejection.VersionConflict(facts.version))
                }
                confirm(profile, row)?.let { return@starting it }

                val now = audit.now()
                database.discardKeptCopies()
                records.keep(row, operationId, profile.generation, now)
                val removed = records.delete(row)
                check(removed == 1) {
                    "The ${records.noun} changed inside the transaction that read it"
                }
                freshlyCommitted(
                    database.commitCommand(profile, operationId, kind, digest, facts.id, now)
                )
            }
        }
        if (outcome is Committed && !outcome.replayed) {
            undo.opened(records.undoKind, operationId, id)
        }
        outcome
    }

    /**
     * Runs inside the transaction. A replay is answered first, whatever the time is.
     * Otherwise the deletion must be the one on offer and still in time, its copy must
     * belong to this history, and neither the record's identifier, its place in the
     * order nor the schedule occurrence it records may be in use. [confirm] may still
     * refuse the row before it is put back.
     */
    suspend fun restore(
        operationId: OperationId,
        generation: Long,
        kind: OperationKind,
        digest: String,
        deletionId: OperationId,
        confirm: LedgerCheck<Row, C, F>
    ): CommandOutcome<C, F> = undo.changing {
        val outcome = database.runOutcome(operationId) {
            starting(operationId, generation, kind, digest) { profile ->
                val inTime = offered()?.deletionId == deletionId
                if (!inTime) return@starting Rejected(Specific(records.undoExpired))
                val kept = records.kept(deletionId)
                    ?: return@starting Rejected(Specific(records.undoExpired))
                val restored = records.restored(kept)
                val facts = records.facts(restored)
                val taken = records.generation(kept) != profile.generation ||
                    records.byId(facts.id) != null ||
                    records.countAtSequence(facts.sequence) != 0 ||
                    originTaken(facts)
                if (taken) return@starting Rejected(Specific(records.undoConflict))

                confirm(profile, restored)?.let { return@starting it }
                records.insert(restored)
                val removed = records.forget(kept)
                check(removed == 1) { "The kept copy changed inside the transaction that read it" }
                val receipt = database.commitCommand(
                    profile,
                    operationId,
                    kind,
                    digest,
                    facts.id,
                    audit.now()
                )
                freshlyCommitted(receipt)
            }
        }
        if (outcome is Committed && !outcome.replayed) undo.closed()
        outcome
    }

    /** The committed result of [operationId], or null when it changed no record of this ledger. */
    suspend fun find(operationId: OperationId): Outcome<C?, StorageFailure> = reading {
        when (val receipt = database.operationReceiptDao().find(operationId.value)?.toStored()) {
            null -> Outcome.Success(null)
            is Outcome.Failure -> receipt
            is Outcome.Success -> Outcome.Success(records.committed(receipt.value))
        }
    }

    /** Why [date] cannot be a record's date today, or null when it can. */
    fun dateRejection(date: LocalDate): CommandOutcome<C, F>? {
        val today = clock.today.value.date
        return when (val checked = EffectiveDates.forManualEntry(date, today)) {
            is Outcome.Success -> null

            is Outcome.Failure -> when (checked.error) {
                is EffectiveDateError.InFuture -> Rejected(Specific(records.futureDate(today)))

                // Refused here, before the write: once stored it could not be read back.
                EffectiveDateError.OutOfRange,
                is EffectiveDateError.Malformed -> Rejected(Specific(records.invalidDate))
            }
        }
    }

    private fun freshlyCommitted(stored: StoredReceipt): CommandOutcome<C, F> =
        Committed(records.receiptOf(stored), replayed = false)

    private fun LedgerRecords<Row, Kept, D, C, F>.receiptOf(stored: StoredReceipt): C =
        checkNotNull(this.committed(stored)) { "Not an $noun receipt" }

    /** Steps 1 and 2 of every command. [proceed] runs only for an operation not seen before. */
    private suspend inline fun starting(
        operationId: OperationId,
        generation: Long,
        kind: OperationKind,
        digest: String,
        proceed: (FinancialProfile) -> CommandOutcome<C, F>
    ): CommandOutcome<C, F> = proceed(
        database.proceedOr(operationId, generation, kind, digest, { records.receiptOf(it) }) {
            return it
        }
    )

    /** A kept occurrence cannot come back while another record holds the same origin. */
    private suspend fun originTaken(facts: LedgerRow): Boolean {
        val schedule = facts.originScheduleId ?: return false
        val date = facts.originDate ?: return false
        val slot = facts.originSlot ?: return false
        return records.countAtOrigin(schedule, date, slot) != 0
    }
}

/** A fresh identifier from the source; a malformed one is a fault, never stored. */
internal fun <T> issued(id: Outcome<T, *>): T = when (id) {
    is Outcome.Success -> id.value
    is Outcome.Failure -> error("The identifier source returned a malformed identifier")
}
