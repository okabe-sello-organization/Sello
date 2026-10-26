package com.software.sello.domain.port

import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import java.time.Instant
import java.time.ZoneId

/** A document outside Sello that the person chose to read, such as one from the system picker. */
data class DocumentSource(val id: String)

sealed interface DocumentRead {
    data class Read(val bytes: ByteArray) : DocumentRead

    /** Longer than [maxBytes]; reading stopped one byte past it. */
    data class TooLarge(val maxBytes: Int) : DocumentRead

    /** The provider would not open or finish reading the document. */
    data object Failed : DocumentRead
}

/** Reads a document outside the app, never more than [maxBytes] plus one. */
fun interface DocumentReader {
    suspend fun read(source: DocumentSource, maxBytes: Int): DocumentRead
}

/**
 * A backup file read, checked and kept privately, ready to replace the history. It is
 * tied to the history it was previewed against: [generation] and [revision] are what
 * the current counts were read at, and the replacement is refused if they moved.
 * [operationId] is chosen when the file is staged and kept with it, so a restart
 * looks up the same replacement instead of starting another.
 */
data class StagedRestore(
    val operationId: OperationId,
    val backupAt: Instant,
    val current: BackupSummary,
    val replacement: BackupSummary,
    val generation: Long,
    val revision: Long,
    /** Present when the file was a FinanceTracker backup, translated first (ADR 0019). */
    val legacy: LegacyImportReport? = null
)

/**
 * What translating a FinanceTracker backup changed and left out, as counts by kind of
 * record and reason. What was imported is the staged replacement's own summary.
 */
data class LegacyImportReport(val lines: List<LegacyImportLine>) {
    val changed: List<LegacyImportLine> get() = lines.filter { it.reason.changed }
    val leftOut: List<LegacyImportLine> get() = lines.filterNot { it.reason.changed }
}

data class LegacyImportLine(val record: LegacyRecord, val reason: LegacyReason, val count: Int)

enum class LegacyRecord {
    Category,
    Limit,
    Expense,
    Income,
    RepeatingEntry,
    Goal,
    Investment,
    InvestmentEntry,
    ExchangeRate,
    MerchantRule
}

/** Why a record was changed ([changed]) or left out; docs/data/legacy-export-format.md. */
enum class LegacyReason(val changed: Boolean = false) {
    Shortened(changed = true),
    Renamed(changed = true),
    Unreadable,
    DuplicateId,
    AmountOutOfRange,
    InvalidDate,
    DateAfterExport,
    InvalidText,
    CategoryMissing,
    CategoryLeftOut,
    UnknownRepeat,
    NotImportedYet
}

/**
 * A backup read and checked whole without opening any storage, as when the data could
 * not be opened at start: when it was made, the financial zone it was made in, and
 * what it would restore.
 */
data class CheckedBackup(val backupAt: Instant, val zone: ZoneId, val contents: BackupSummary)

/** Why a chosen file cannot be restored. Nothing was changed. */
enum class RestoreRejection {
    /** The provider could not give the file to Sello. */
    Unreadable,

    /** More than a backup may hold. */
    TooLarge,

    /** JSON, but not a Sello backup; or not JSON at all. */
    NotABackup,

    /** A Sello backup of a version this app cannot read. */
    NewerVersion,

    /** Cut short or structurally broken. */
    Damaged,

    /** Well formed, but a value, amount, currency or reference breaks Sello's rules. */
    InvalidContent
}

sealed interface StageFailure {
    data class Rejected(val reason: RestoreRejection) : StageFailure

    /** The current data or the private staging area could not be read or written. */
    data object Storage : StageFailure
}

/** The backup's preferences that still have to be applied; null when that group is done. */
data class PendingPreferences(
    val appearance: AppearancePreference?,
    val ordering: CategoryOrderingPreference?
) {
    val done: Boolean get() = appearance == null && ordering == null
}

sealed interface RestoreOutcome {
    /** The history was replaced. [preferences] are those that could not be applied yet. */
    data class Restored(val summary: BackupSummary, val preferences: PendingPreferences) :
        RestoreOutcome

    /** The history changed after the preview; nothing was written. Preview again. */
    data object Stale : RestoreOutcome

    /** Nothing was written. */
    data object Failed : RestoreOutcome

    /** The replacement may or may not have committed; look it up with the same staging. */
    data class OutcomeUnknown(val operationId: OperationId) : RestoreOutcome
}

/**
 * Replacing the whole financial history with a backup. Staging, preview and
 * replacement never merge; preferences are applied after the replacement and kept
 * pending, with the staged file, until each one has been applied.
 */
interface Restores {
    /** Checks [bytes] completely and keeps them privately; nothing financial changes. */
    suspend fun stage(bytes: ByteArray): Outcome<StagedRestore, StageFailure>

    /** A file staged earlier, still unconfirmed and still valid for the current history. */
    suspend fun staged(): StagedRestore?

    /** Forgets an unconfirmed staged file. A replaced history keeps its pending file. */
    suspend fun discard()

    /** Replaces the history with [staged], then tries its preferences once. */
    suspend fun replace(staged: StagedRestore): RestoreOutcome

    /** A replacement that committed with preferences still to apply, if any. */
    suspend fun pending(): RestoreOutcome.Restored?

    /** Applies the pending preferences again; the history is never replaced twice. */
    suspend fun applyPending(): RestoreOutcome.Restored?
}

/** Starts the app again from Recibo in a new process, as after a restore. */
fun interface AppRestart {
    fun restart()
}
