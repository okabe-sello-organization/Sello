package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection

/*
 * The command protocol's steps told as the shared [CommandOutcome], so that every money
 * workflow answers a rollback, an unknown commit, a replaced history, a replay and a
 * conflict in exactly the same words.
 */

/** Storage failed and the write was rolled back: certain, nothing was written. */
internal fun storageFailed(failure: StorageFailure) =
    CommandOutcome.Rejected(CommandRejection.StorageFailed(failure))

/**
 * [runCommand] for the command [operationId]: a rollback is [storageFailed], a commit
 * whose result is not known is [CommandOutcome.OutcomeUnknown], and a cancelled caller
 * still gets the cancellation.
 */
internal suspend fun <C, F> SelloDatabase.runOutcome(
    operationId: OperationId,
    decide: suspend () -> CommandOutcome<C, F>
): CommandOutcome<C, F> = when (val run = runCommand(decide)) {
    is CommandRun.Finished -> run.value
    is CommandRun.RolledBack -> storageFailed(run.failure)
    CommandRun.Unknown -> CommandOutcome.OutcomeUnknown(operationId)
}

/**
 * Steps 1 and 2 of every command, [startCommand]: the profile to go on with when this
 * operation is new. Otherwise [settled] is handed the answer and must leave: the original
 * result, made by [committed] from its receipt, for a replay; a rejection for a replaced
 * history, a conflict or a damaged record.
 */
internal suspend inline fun <C> SelloDatabase.proceedOr(
    operationId: OperationId,
    generation: Long,
    kind: OperationKind,
    digest: String,
    committed: (StoredReceipt) -> C,
    settled: (CommandOutcome<C, Nothing>) -> Nothing
): FinancialProfile = when (val start = startCommand(operationId, generation, kind, digest)) {
    is CommandStart.Proceed -> start.profile

    is CommandStart.Stale ->
        settled(CommandOutcome.Rejected(CommandRejection.StaleGeneration(start.current)))

    is CommandStart.Replay ->
        settled(CommandOutcome.Committed(committed(start.original), replayed = true))

    CommandStart.Conflict -> settled(CommandOutcome.Rejected(CommandRejection.OperationConflict))

    is CommandStart.Damaged -> settled(storageFailed(start.failure))
}
