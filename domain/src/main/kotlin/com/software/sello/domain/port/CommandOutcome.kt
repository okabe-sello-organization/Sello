package com.software.sello.domain.port

import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.StorageFailure

/**
 * How a money command ended (ARCHITECTURE §5), the same for every feature. [C] is what
 * a committed change carries, with its receipt; [F] is the feature's own reasons to refuse.
 */
sealed interface CommandOutcome<out C, out F> {
    /**
     * The change is saved. [replayed] is true when this operation had already committed
     * with the same input and this call wrote nothing new.
     */
    data class Committed<out C>(val value: C, val replayed: Boolean) : CommandOutcome<C, Nothing>

    /** Nothing was written, for certain. */
    data class Rejected<out F>(val reason: CommandRejection<F>) : CommandOutcome<Nothing, F>

    /**
     * It is not known whether the change was saved. Ask the feature's `find` with the same
     * [operationId], or submit the same command again; never a new one.
     */
    data class OutcomeUnknown(val operationId: OperationId) : CommandOutcome<Nothing, Nothing>
}

/** Why nothing was written. Each of these is certain: nothing changed. */
sealed interface CommandRejection<out F> {
    /** The history was replaced (restore or reset) since the command was prepared. */
    data class StaleGeneration(val current: Long) : CommandRejection<Nothing>

    /** This operation already committed with different input. The original is unchanged. */
    data object OperationConflict : CommandRejection<Nothing>

    /** The record changed since the caller read it. [current] is its version now. */
    data class VersionConflict(val current: Long) : CommandRejection<Nothing>

    /** Storage failed and the write was rolled back. */
    data class StorageFailed(val failure: StorageFailure) : CommandRejection<Nothing>

    /** A reason only this feature has. */
    data class Specific<out F>(val reason: F) : CommandRejection<F>
}
