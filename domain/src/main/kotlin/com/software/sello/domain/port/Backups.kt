package com.software.sello.domain.port

import com.software.sello.domain.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** What a backup holds, as a person would count it. [goals] counts goals of any state. */
data class BackupSummary(
    val categories: Int,
    val expenses: Int,
    val incomes: Int,
    val goals: Int,
    /** Recurring payments and incomes, which version 3 backups carry. */
    val schedules: Int,
    /**
     * Investments, which version 4 backups carry, each with its movements; 0 where they
     * are not counted, such as a backup remembered before investments existed.
     */
    val investments: Int = 0
)

/** A backup file ready to be written to a place the person chose. Nothing has been written yet. */
class PreparedBackup(val bytes: ByteArray, val summary: BackupSummary)

sealed interface BackupPreparationFailure {
    /** The financial data could not be read. */
    data object StorageUnavailable : BackupPreparationFailure

    /** The history is beyond what the backup format holds: too many bytes, records or limits. */
    data object TooLarge : BackupPreparationFailure

    /** The file made would fail the app's own reading check for another reason: a fault in Sello. */
    data object Unrestorable : BackupPreparationFailure
}

/** Makes backup files. Making one never changes financial data. */
interface BackupExports {
    /** The name to suggest before the person chooses where to save. */
    suspend fun suggestedName(): String

    /** Reads the current data and encodes it. */
    suspend fun prepare(): Outcome<PreparedBackup, BackupPreparationFailure>
}

/**
 * A place outside Sello that the person chose to save a file to, such as a document
 * returned by the system picker. [id] is opaque: only the adapter that made it reads it.
 */
data class DocumentDestination(val id: String)

sealed interface DocumentWrite {
    /** Every byte was written, flushed and the document closed. [name] is what the provider calls it. */
    data class Completed(val name: String?, val bytes: Long) : DocumentWrite

    /**
     * The document could not be opened, written or closed. Some bytes may have reached
     * it: the provider owns that document and Sello never deletes it.
     */
    data object Failed : DocumentWrite
}

/** Writes bytes to a document outside the app. */
fun interface DocumentWriter {
    suspend fun write(destination: DocumentDestination, bytes: ByteArray): DocumentWrite
}

/** The last backup that was written and closed successfully. */
data class LastBackup(
    val at: Instant,
    val name: String,
    val bytes: Long,
    val summary: BackupSummary
)

/**
 * What the app remembers about backups on this device. [unfinished] means a save was
 * started and its outcome never recorded: the app stopped while writing, so whether
 * that file is complete is unknown.
 */
data class BackupRecord(val last: LastBackup? = null, val unfinished: Boolean = false)

/** Nonfinancial memory of backups. It is never part of a backup itself. */
interface BackupRecords {
    fun observeBackups(): Flow<PreferenceRead<BackupRecord>>

    /** Recorded before any byte is written. */
    suspend fun backupStarted(): PreferenceWrite

    /** The save completed: [last] replaces the previous one and the start is cleared. */
    suspend fun backupCompleted(last: LastBackup): PreferenceWrite

    /** The save failed or its outcome was acknowledged as unknown: only the start is cleared. */
    suspend fun backupAbandoned(): PreferenceWrite
}
