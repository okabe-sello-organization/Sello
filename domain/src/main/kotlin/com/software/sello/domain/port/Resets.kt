package com.software.sello.domain.port

import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure

/**
 * What erasing would remove, counted with the history position it was read at. The
 * erase is refused if [generation] or [revision] moved, so the person never confirms
 * one set of counts and loses another.
 */
data class ResetPreview(val current: BackupSummary, val generation: Long, val revision: Long)

sealed interface ResetOutcome {
    /**
     * The history was erased. [preferencesPending]: the preferences could not be put
     * back to their first-run values yet; retrying does only that.
     */
    data class Erased(val preferencesPending: Boolean) : ResetOutcome

    /** The history changed after the preview; nothing was erased. Count again. */
    data object Stale : ResetOutcome

    /** Nothing was erased. */
    data object Failed : ResetOutcome

    /** The erase may or may not have committed; ask again with the same operation. */
    data class OutcomeUnknown(val operationId: OperationId) : ResetOutcome
}

/**
 * Erasing the whole history on request. One transaction removes every financial row,
 * kept undo copy and operation receipt, leaving one receipt for the erase itself with
 * no amount, name or note, and a new generation in the device's zone. Private files
 * that would keep old data (a staged restore, the copy set aside at start-up) go
 * afterwards, then the preferences return to first-run values; until all of that is
 * done the erase stays pending and is finished at start-up, never repeated. A restore
 * that replaced the erased history before then takes those files and preferences over:
 * the erase leaves them alone and only the database's compaction is still owed.
 */
interface Resets {
    /** Counts what would be erased. */
    suspend fun preview(): Outcome<ResetPreview, StorageFailure>

    /**
     * Erases the history counted in [preview] under [operationId]. Asking again with the
     * same operation after an unknown outcome or a crash finds the first erase.
     */
    suspend fun reset(preview: ResetPreview, operationId: OperationId): ResetOutcome

    /** True when an erase committed and what follows it is not finished. */
    suspend fun pending(): Boolean

    /** Finishes a committed erase's files and preferences; never erases the history again. */
    suspend fun finishPending(): ResetOutcome.Erased?
}

/** Puts every stored preference back to its first-run value, in one write. */
fun interface PreferenceReset {
    suspend fun resetAll(): PreferenceWrite
}
