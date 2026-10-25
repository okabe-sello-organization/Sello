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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.ZoneId
import java.util.Properties
import kotlinx.coroutines.withContext

/**
 * Erases one storage profile's history (the sandbox has its own), through a private
 * folder that holds `pending.properties` while an erase is under way: the operation,
 * written before the transaction, and whether the preferences are back to first-run.
 *
 * The receipt decides what happened. A file whose operation has no receipt is an erase
 * that never committed and is forgotten; one whose receipt exists is finished: the
 * restore folder and [setAside] copy are deleted, the database file is compacted so
 * no erased row survives in its free pages, the preferences are reset once, then the
 * file goes. Nothing about the erased history is written to it.
 */
class RoomResets(
    private val database: SelloDatabase,
    private val folder: File,
    private val restoreFolder: File,
    private val setAside: List<File>,
    private val preferences: PreferenceReset,
    private val deviceZone: () -> ZoneId,
    private val audit: AuditClock,
    private val dispatchers: DispatcherProvider
) : Resets {
    private val marker = File(folder, "pending.properties")

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
                            schedules = database.scheduleDao().count()
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
        val existing = readMarker()
        if (existing != null && existing.operation != operationId) {
            return ResetOutcome.Failed
        }
        if (existing == null && !writeMarker(Marker(operationId, preferencesDone = false))) {
            return ResetOutcome.Failed
        }
        val digest = digest(preview)
        return when (val run = database.runCommand { write(preview, operationId, digest) }) {
            is CommandRun.Finished -> when (run.value) {
                Erase.Done, Erase.AlreadyDone ->
                    finishPending() ?: ResetOutcome.Erased(preferencesPending = true)

                Erase.Moved -> {
                    forget()
                    ResetOutcome.Stale
                }

                Erase.Conflict -> {
                    forget()
                    ResetOutcome.Failed
                }
            }

            is CommandRun.RolledBack -> {
                forget()
                ResetOutcome.Failed
            }

            CommandRun.Unknown -> ResetOutcome.OutcomeUnknown(operationId)
        }
    }

    override suspend fun pending(): Boolean {
        val marker = readMarker() ?: return false
        return when (committed(marker.operation)) {
            true -> true

            // Never committed: the erase did not happen and is forgotten.
            false -> {
                forget()
                false
            }

            // Unknown: neither finish it nor forget it.
            null -> false
        }
    }

    override suspend fun finishPending(): ResetOutcome.Erased? {
        val marker = readMarker() ?: return null
        if (committed(marker.operation) != true) return null
        val filesGone = io {
            restoreFolder.deleteRecursively()
            setAside.forEach { it.delete() }
            !restoreFolder.exists() && setAside.none { it.exists() }
        } && compact()
        var preferencesDone = marker.preferencesDone
        if (!preferencesDone && preferences.resetAll() == PreferenceWrite.Saved) {
            preferencesDone = writeMarker(marker.copy(preferencesDone = true))
        }
        if (filesGone && preferencesDone) io { this.marker.delete() }
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
            goalDao().deleteFeatured()
            goalDao().deleteAllEvents()
            goalDao().deleteAll()
            scheduleDao().deleteAllPauses()
            scheduleDao().deleteAll()
            expenseDao().deleteAll()
            incomeDao().deleteAll()
            limitDao().deleteAllMonthLimits()
            limitDao().deleteAllDefaults()
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
     * file and truncating the log leaves no erased bytes behind. False if it could not.
     */
    private suspend fun compact(): Boolean = io {
        try {
            val sqlite = database.openHelper.writableDatabase
            sqlite.execSQL("VACUUM")
            sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            true
        } catch (_: SQLiteException) {
            false
        }
    }

    /** Whether [operation] committed: true, false, or null when that cannot be read. */
    private suspend fun committed(operation: OperationId): Boolean? = when (
        val read = reading {
            Outcome.Success(database.operationReceiptDao().find(operation.value) != null)
        }
    ) {
        is Outcome.Success -> read.value
        is Outcome.Failure -> null
    }

    private data class Marker(val operation: OperationId, val preferencesDone: Boolean)

    private suspend fun readMarker(): Marker? = io {
        if (!marker.exists()) return@io null
        val properties = try {
            Properties().apply { marker.inputStream().use(::load) }
        } catch (_: IOException) {
            return@io null
        }
        val operation = OperationId.of(properties.getProperty(OPERATION).orEmpty())
            as? Outcome.Success ?: return@io null
        Marker(operation.value, properties.getProperty(PREFERENCES) == DONE)
    }

    private suspend fun writeMarker(value: Marker): Boolean = io {
        folder.mkdirs()
        val properties = Properties().apply {
            setProperty(OPERATION, value.operation.value)
            if (value.preferencesDone) setProperty(PREFERENCES, DONE)
        }
        val bytes = ByteArrayOutputStream().also { properties.store(it, null) }.toByteArray()
        try {
            val temporary = File(folder, "${marker.name}.tmp")
            FileOutputStream(temporary).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            temporary.renameTo(marker)
        } catch (_: IOException) {
            false
        }
    }

    private suspend fun forget() {
        io { marker.delete() }
    }

    /** File work off the main thread. */
    private suspend fun <T> io(block: () -> T): T = withContext(dispatchers.io) { block() }

    private companion object {
        const val OPERATION = "operation"
        const val PREFERENCES = "preferences"
        const val DONE = "done"

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
