package com.software.sello.feature.backup

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
    var replaceAnswer: RestoreOutcome = RestoreOutcome.Failed
    var stagedAnswer: StagedLookup = StagedLookup.None
    var pendingAnswer: RestoreOutcome.Restored? = null
    var applyAnswer: RestoreOutcome.Restored? = null
    var replaceGate: CompletableDeferred<Unit>? = null
    var stagedGate: CompletableDeferred<Unit>? = null
    val staged = mutableListOf<Int>()
    var replaced = 0
    var discarded = 0
    var applied = 0
    var looked = 0

    override suspend fun stage(bytes: ByteArray): Outcome<StagedRestore, StageFailure> {
        events?.add("stage ${bytes.toList()}")
        staged += bytes.size
        return stageAnswer
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
        return replaceAnswer
    }

    override suspend fun pending(): RestoreOutcome.Restored? = pendingAnswer

    override suspend fun applyPending(): RestoreOutcome.Restored? {
        events?.add("apply")
        applied++
        return applyAnswer
    }
}
