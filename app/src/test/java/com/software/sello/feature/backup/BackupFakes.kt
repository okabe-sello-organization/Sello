package com.software.sello.feature.backup

import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.BackupExports
import com.software.sello.domain.port.BackupPreparationFailure
import com.software.sello.domain.port.BackupRecord
import com.software.sello.domain.port.BackupRecords
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.DocumentDestination
import com.software.sello.domain.port.DocumentWrite
import com.software.sello.domain.port.DocumentWriter
import com.software.sello.domain.port.LastBackup
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.PreparedBackup
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedLookup
import com.software.sello.domain.port.StagedRestore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** G01's counts, as a prepared file would carry them. */
val g01Summary = BackupSummary(categories = 2, expenses = 3, incomes = 1, 0, 0)

class HandBackupExports : BackupExports {
    var name = "sello-respaldo-2026-10-22-1430.json"
    var next: Outcome<PreparedBackup, BackupPreparationFailure> =
        Outcome.Success(PreparedBackup(ByteArray(4_096) { 32 }, g01Summary))
    var prepared = 0

    override suspend fun suggestedName(): String = name

    override suspend fun prepare(): Outcome<PreparedBackup, BackupPreparationFailure> {
        prepared++
        return next
    }
}

/** Records every write; [gate], when set, holds the write until the test completes it. */
class HandDocumentWriter : DocumentWriter {
    val written = mutableListOf<Pair<DocumentDestination, Int>>()
    var answer: DocumentWrite = DocumentWrite.Completed("Mi respaldo.json", 4_096)
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun write(destination: DocumentDestination, bytes: ByteArray): DocumentWrite {
        gate?.await()
        written += destination to bytes.size
        return answer
    }
}

/** The DataStore record in memory, with an optional failure to read it. */
class HandBackupRecords(initial: BackupRecord = BackupRecord()) : BackupRecords {
    val stored = MutableStateFlow(initial)
    var unreadable = false
    val reads = MutableStateFlow<PreferenceRead<BackupRecord>>(PreferenceRead.Ready(initial))

    override fun observeBackups(): Flow<PreferenceRead<BackupRecord>> = reads

    override suspend fun backupStarted(): PreferenceWrite =
        set(stored.value.copy(unfinished = true))

    override suspend fun backupCompleted(last: LastBackup): PreferenceWrite =
        set(BackupRecord(last = last, unfinished = false))

    override suspend fun backupAbandoned(): PreferenceWrite =
        set(stored.value.copy(unfinished = false))

    fun failReads() {
        unreadable = true
        reads.value = PreferenceRead.Failed(PreferenceFailure.Unavailable, null)
    }

    private fun set(record: BackupRecord): PreferenceWrite {
        stored.value = record
        if (!unreadable) reads.value = PreferenceRead.Ready(record)
        return PreferenceWrite.Saved
    }
}

/**
 * Restores that answer as told and count what they were asked; given [events], they also log
 * each call there, in order.
 */
class HandRestores(private val events: MutableList<String>? = null) : Restores {
    var stageAnswer: Outcome<StagedRestore, StageFailure> =
        Outcome.Failure(StageFailure.Storage)

    /** Answered in turn before [stageAnswer]. */
    val stageAnswers = ArrayDeque<Outcome<StagedRestore, StageFailure>>()
    var replaceAnswer: RestoreOutcome = RestoreOutcome.Failed
    var stagedAnswer: StagedLookup = StagedLookup.None
    var pendingAnswer: RestoreOutcome.Restored? = null
    var applyAnswer: RestoreOutcome.Restored? = null
    var replaceGate: CompletableDeferred<Unit>? = null
    var stagedGate: CompletableDeferred<Unit>? = null
    val staged = mutableListOf<Int>()
    var replaced = 0

    /** The operation of each replacement that got past [replaceGate], in order. */
    val replacedOperations = mutableListOf<OperationId>()
    var discarded = 0
    var applied = 0
    var looked = 0

    override suspend fun stage(bytes: ByteArray): Outcome<StagedRestore, StageFailure> {
        events?.add("stage ${bytes.toList()}")
        staged += bytes.size
        return stageAnswers.removeFirstOrNull() ?: stageAnswer
    }

    override suspend fun staged(): StagedLookup {
        looked++
        stagedGate?.await()
        return stagedAnswer
    }

    override suspend fun discard() {
        discarded++
    }

    override suspend fun replace(staged: StagedRestore): RestoreOutcome {
        events?.add("replace ${staged.operationId.value}")
        replaceGate?.await()
        replaced++
        replacedOperations += staged.operationId
        return replaceAnswer
    }

    override suspend fun pending(): RestoreOutcome.Restored? = pendingAnswer

    override suspend fun applyPending(): RestoreOutcome.Restored? {
        events?.add("apply")
        applied++
        return applyAnswer
    }
}

/**
 * The store as a new process finds it when a replacement committed and the process died
 * before its staged file was renamed, behaving as the real one does: looking at the
 * staged file renames it, a look before the preferences are applied cannot rename it
 * again, and applying them writes them and, with none left, forgets the replacement,
 * so a later look or apply finds nothing. The nth [staged] waits for the nth of
 * [stagedGates]; the nth [applyPending], for the nth of [readGates] before it reads and
 * the nth of [writeGates] before it writes. A missing or null gate does not wait.
 */
class CommittedBeforeRename(
    private val operation: OperationId,
    private val outcome: RestoreOutcome.Restored
) : Restores {
    val stagedGates = ArrayDeque<CompletableDeferred<Unit>?>()
    val readGates = ArrayDeque<CompletableDeferred<Unit>?>()
    val writeGates = ArrayDeque<CompletableDeferred<Unit>?>()
    private var renamed = false
    private var forgotten = false

    /** How many times the preferences were written. */
    var written = 0

    /** Refused until a look finds the replacement; staging is never offered after that. */
    override suspend fun stage(bytes: ByteArray): Outcome<StagedRestore, StageFailure> =
        Outcome.Failure(if (renamed) StageFailure.Storage else StageFailure.Unsettled)

    override suspend fun staged(): StagedLookup {
        stagedGates.removeFirstOrNull()?.await()
        return when {
            forgotten -> StagedLookup.None
            renamed -> StagedLookup.Unknown(operation)
            else -> StagedLookup.Replaced(operation).also { renamed = true }
        }
    }

    override suspend fun discard() = Unit

    override suspend fun replace(staged: StagedRestore): RestoreOutcome = RestoreOutcome.Failed

    override suspend fun pending(): RestoreOutcome.Restored? =
        outcome.takeIf { renamed && !forgotten }

    override suspend fun applyPending(): RestoreOutcome.Restored? {
        val write = writeGates.removeFirstOrNull()
        readGates.removeFirstOrNull()?.await()
        if (pending() == null) return null
        write?.await()
        written++
        forgotten = true
        return outcome.copy(preferences = PendingPreferences(null, null))
    }
}
