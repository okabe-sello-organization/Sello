package com.software.sello.data.reset

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.data.local.entity.ProfileEntity
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toStored
import com.software.sello.data.repository.CommandRun
import com.software.sello.data.repository.nextCounter
import com.software.sello.data.repository.reading
import com.software.sello.data.repository.runCommand
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.ResetPreview
import com.software.sello.domain.port.Resets
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Erases one storage profile's history (the sandbox has its own), through a private
 * folder that holds `pending.properties` while an erase is under way: the operation,
 * written before the transaction, and whether the preferences are back to first-run.
 *
 * The receipt decides what happened. A file whose operation has no receipt is an erase
 * that never committed and is forgotten; one whose receipt exists is finished: the
 * restore folder and [setAside] copy are deleted, the preferences are reset once, then
 * the file goes. Once a restore has replaced the history the erase made, the restore
 * folder is the restore's and stays; the copy set aside still goes, and the preferences
 * are not reset again: the restore put them back to first-run before it committed
 * ([EraseMarker.preferencesFirst]) and may have applied its own since. Either way the database file is compacted so no
 * erased row survives in its free pages; `compact.pending` keeps that owed, apart from
 * the erase, until a compaction is seen through. Nothing about the erased history is
 * written to either file.
 *
 * [recovery] is shared with the restores of the same storage: finishing an erase and
 * committing a restore take turns, so an erase never acts on a history a restore is
 * replacing at that moment.
 */
class RoomResets(
    private val database: SelloDatabase,
    private val folder: File,
    private val restoreFolder: File,
    private val setAside: List<File>,
    private val preferences: PreferenceReset,
    private val deviceZone: () -> ZoneId,
    private val audit: AuditClock,
    private val dispatchers: DispatcherProvider,
    private val recovery: Mutex
) : Resets {
    private val files = EraseMarker(database, folder, dispatchers)

    override suspend fun preview(): Outcome<ResetPreview, StorageFailure> = reading {
        database.withTransaction {
            when (val profile = profileFrom(database.profileDao().rows())) {
                is Outcome.Failure -> profile

                is Outcome.Success -> Outcome.Success(
                    ResetPreview(
                        BackupSummary(
                            categories = database.categoryDao().count(),
                            expenses = database.expenseDao().count(),
                            incomes = database.incomeDao().count(),
                            goals = database.goalDao().count(),
                            schedules = database.scheduleDao().count(),
                            investments = database.investmentDao().count(),
                            carryChains = database.carryChainDao().count()
                        ),
                        profile.value.generation,
                        profile.value.revision
                    )
                )
            }
        }
    }

    override suspend fun reset(preview: ResetPreview, operationId: OperationId): ResetOutcome {
        // An erase still finishing is finished first; a second one is never started over it.
        if (pending()) finishPending()
        val existing = files.read()
        if (existing != null && existing.operation != operationId) {
            return ResetOutcome.Failed
        }
        if (existing == null &&
            !files.write(EraseMarker.Marker(operationId, preferencesDone = false))
        ) {
            return ResetOutcome.Failed
        }
        val digest = digest(preview)
        return when (val run = database.runCommand { write(preview, operationId, digest) }) {
            is CommandRun.Finished -> when (run.value) {
                Erase.Done, Erase.AlreadyDone ->
                    finishPending() ?: ResetOutcome.Erased(preferencesPending = true)

                Erase.Moved -> {
                    files.forget()
                    ResetOutcome.Stale
                }

                Erase.Conflict -> {
                    files.forget()
                    ResetOutcome.Failed
                }
            }

            is CommandRun.RolledBack -> {
                files.forget()
                ResetOutcome.Failed
            }

            CommandRun.Unknown -> ResetOutcome.OutcomeUnknown(operationId)
        }
    }

    override suspend fun pending(): Boolean {
        val marker = files.read() ?: return files.compactionOwed()
        return when (files.place(marker.operation)) {
            EraseMarker.Place.Current, EraseMarker.Place.Replaced -> true

            // Never committed: the erase did not happen and is forgotten.
            EraseMarker.Place.NeverCommitted -> {
                files.forget()
                files.compactionOwed()
            }

            // Unknown: neither finish it nor forget it.
            EraseMarker.Place.Unknown -> false
        }
    }

    override suspend fun finishPending(): ResetOutcome.Erased? {
        val finished = recovery.withLock { finishMarker() }
        // Outside the turn: a compaction can take a while and changes no row. An erase
        // handled just now is compacted even if what it owes could not be written down.
        if ((finished != null || files.compactionOwed()) && compact()) files.compacted()
        return finished
    }

    /**
     * The steps after a committed erase, in turn with restores. Compaction is recorded as
     * owed first, so the erase's own file can go before it is done. A restore that has
     * replaced the erased history since owns the restore folder; the copy set aside and
     * the erased history's preferences still go.
     */
    private suspend fun finishMarker(): ResetOutcome.Erased? {
        val marker = files.read() ?: return null
        val place = files.place(marker.operation)
        if (place != EraseMarker.Place.Current && place != EraseMarker.Place.Replaced) return null
        val owed = files.oweCompaction()
        val filesGone = io {
            if (place == EraseMarker.Place.Current) restoreFolder.deleteRecursively()
            setAside.forEach { it.delete() }
            (place == EraseMarker.Place.Replaced || !restoreFolder.exists()) &&
                setAside.none { it.exists() }
        }
        // A restore resets them before it commits, so after one they are first-run already
        // and the restore's own may be applied over them: they are only marked done.
        var preferencesDone = marker.preferencesDone
        if (!preferencesDone) {
            val reset = place == EraseMarker.Place.Replaced ||
                preferences.resetAll() == PreferenceWrite.Saved
            if (reset) preferencesDone = files.write(marker.copy(preferencesDone = true))
        }
        if (owed && filesGone && preferencesDone) files.forget()
        return ResetOutcome.Erased(preferencesPending = !preferencesDone)
    }

    private sealed interface Erase {
        data object Done : Erase
        data object AlreadyDone : Erase
        data object Moved : Erase
        data object Conflict : Erase
    }

    /**
     * Inside one transaction: a receipt for this operation means it already happened;
     * otherwise the history must still be where it was counted. Then every financial
     * row, kept undo copy and receipt goes, and the profile takes the device's zone and
     * a new generation, with one receipt for this erase.
     */
    private suspend fun write(
        preview: ResetPreview,
        operation: OperationId,
        digest: String
    ): Erase {
        database.operationReceiptDao().find(operation.value)?.let { existing ->
            val original = (existing.toStored() as? Outcome.Success)?.value
                ?: return Erase.Conflict
            val same = original.receipt.kind == OperationKind.ResetHistory &&
                original.inputDigest == digest
            return if (same) Erase.AlreadyDone else Erase.Conflict
        }
        val profile = (profileFrom(database.profileDao().rows()) as? Outcome.Success)?.value
            ?: return Erase.Moved
        if (profile.generation != preview.generation || profile.revision != preview.revision) {
            return Erase.Moved
        }
        with(database) {
            deletedExpenseDao().clear()
            deletedIncomeDao().clear()
            deletedGoalEventDao().clear()
            deletedInvestmentMovementDao().clear()
            investmentDao().deleteAllMovements()
            investmentDao().deleteAll()
            investmentDao().deleteAllRates()
            goalDao().deleteAllStars()
            goalDao().deleteAllEvents()
            goalDao().deleteAll()
            scheduleDao().deleteAllPauses()
            scheduleDao().deleteAll()
            expenseDao().deleteAll()
            incomeDao().deleteAll()
            limitDao().deleteAllMonthLimits()
            limitDao().deleteAllDefaults()
            carryChainDao().deleteAll()
            categoryDao().deleteAll()
            operationReceiptDao().deleteAll()
        }
        val generation = nextCounter(profile.generation, ProfileEntity.TABLE, null, "generation")
        val revision = nextCounter(profile.revision, ProfileEntity.TABLE, null, "revision")
        val moved = database.profileDao().replaceHistory(
            zone = deviceZone().id,
            generation = generation,
            revision = revision,
            expectedGeneration = profile.generation,
            expectedRevision = profile.revision
        )
        check(moved == 1) { "The profile changed inside the transaction that read it" }
        database.operationReceiptDao().insert(
            OperationReceiptEntity(
                operationId = operation.value,
                kind = OperationKind.ResetHistory.key,
                inputDigest = digest,
                generation = generation,
                revision = revision,
                subjectId = null,
                committedAt = audit.now().toEpochMilli()
            )
        )
        return Erase.Done
    }

    /**
     * SQLite keeps deleted rows in free pages and in its write-ahead log; rebuilding the
     * file and truncating the log leaves no erased bytes behind. False if it could not,
     * including a checkpoint that reports it was kept busy: the log may still hold them.
     */
    private suspend fun compact(): Boolean = io {
        try {
            val sqlite = database.openHelper.writableDatabase
            sqlite.execSQL("VACUUM")
            sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { result ->
                result.moveToFirst() && result.getInt(0) == 0
            }
        } catch (_: SQLiteException) {
            false
        }
    }

    /** File work off the main thread. */
    private suspend fun <T> io(block: () -> T): T = withContext(dispatchers.io) { block() }

    private companion object {
        /** What the erase is: the history position it was counted at, nothing else. */
        fun digest(preview: ResetPreview): String {
            val sha256 = MessageDigest.getInstance("SHA-256")
            listOf(
                OperationKind.ResetHistory.key,
                preview.generation.toString(),
                preview.revision.toString()
            ).forEach { field ->
                val encoded = field.toByteArray(Charsets.UTF_8)
                sha256.update("${encoded.size}:".toByteArray(Charsets.UTF_8))
                sha256.update(encoded)
            }
            return sha256.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
