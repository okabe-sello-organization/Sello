package com.software.sello.data.repository

import android.database.SQLException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.data.local.entity.ProfileEntity
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/*
 * The steps every command shares. A workflow supplies its own typed command, checks
 * and writes; these keep the parts that must never differ between them the same.
 */

/** How a command's transaction ended, before it is turned into a typed outcome. */
internal sealed interface CommandRun<out T> {
    /** The transaction committed, or decided to write nothing; [value] says which. */
    data class Finished<T>(val value: T) : CommandRun<T>

    /** A statement failed, so the transaction rolled back. Nothing was written. */
    data class RolledBack(val failure: StorageFailure) : CommandRun<Nothing>

    /** Every statement ran and the commit itself failed. Whether it took effect is not known. */
    data object Unknown : CommandRun<Nothing>
}

/**
 * A stored counter is already at the largest value it can hold, so the change that
 * needs the one after it cannot be written. [failure] names the counter. Raised inside
 * a command's transaction, which it rolls back.
 */
internal class CounterExhausted(val failure: StorageFailure.Integrity) :
    RuntimeException("No value follows the largest one: $failure")

/**
 * The value after [current], which is stored in [field] of [record]'s row [key]. It
 * never wraps, saturates or starts again: at the largest value it raises
 * [CounterExhausted], and the command is refused with nothing written.
 */
internal fun nextCounter(current: Long, record: String, key: String?, field: String): Long {
    if (current == Long.MAX_VALUE) {
        throw CounterExhausted(StorageFailure.Integrity(record, key, field))
    }
    return current + 1
}

/**
 * Runs [decide] in one transaction. The caller is checked for cancellation before it
 * starts and after it ends; in between the transaction always runs to its end, so a
 * cancellation can never stop it halfway. A cancelled caller is told so instead of
 * being handed a result it may never see; the receipt is already durable by then.
 * Never call it from inside another transaction: it commits its own.
 */
internal suspend fun <T> SelloDatabase.runCommand(decide: suspend () -> T): CommandRun<T> {
    currentCoroutineContext().ensureActive()
    var everyStatementRan = false
    val run = try {
        withContext(NonCancellable) {
            CommandRun.Finished(
                withTransaction {
                    decide().also {
                        everyStatementRan = true
                        commitWhileHeld()
                    }
                }
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exhausted: CounterExhausted) {
        // Raised before anything is committed, so this is certain, never Unknown. Only
        // this is caught: any other arithmetic fault is a bug and stays one.
        CommandRun.RolledBack(exhausted.failure)
    } catch (failure: SQLiteException) {
        if (everyStatementRan) CommandRun.Unknown else rolledBack(failure)
    } catch (failure: IllegalStateException) {
        if (everyStatementRan) CommandRun.Unknown else rolledBack(failure)
    }
    currentCoroutineContext().ensureActive()
    return run
}

/**
 * Commits the transaction while it still holds its connection, then opens an empty one
 * for Room to end. Android sends Room's own COMMIT and hands the connection back even
 * when the COMMIT fails, and SQLite can keep a refused transaction open: every later
 * write on that connection would then fail, and a read on it could see the refused rows.
 * Committed here instead, a refusal throws inside the transaction, so Android's ROLLBACK
 * ends it on the same connection before handing that back. If SQLite had already ended
 * it, that ROLLBACK fails too; either way nothing stays open, and the outcome stays unknown.
 *
 * Android runs BEGIN, COMMIT and ROLLBACK itself instead of passing them to SQLite; the
 * leading ";" is an empty statement to SQLite and makes Android pass the rest through.
 */
private fun SelloDatabase.commitWhileHeld() {
    val sqlite = openHelper.writableDatabase
    sqlite.execSQL(";COMMIT")
    sqlite.execSQL(";BEGIN")
}

private fun rolledBack(failure: Exception) =
    CommandRun.RolledBack(StorageFailure.Unavailable(failure.javaClass.simpleName))

/** A read that is not a command: a database error becomes a typed failure. */
internal suspend fun <T> reading(
    read: suspend () -> Outcome<T, StorageFailure>
): Outcome<T, StorageFailure> = try {
    read()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: SQLException) {
    Outcome.Failure(StorageFailure.Unavailable(failure.javaClass.simpleName))
} catch (failure: IllegalStateException) {
    Outcome.Failure(StorageFailure.Unavailable(failure.javaClass.simpleName))
}

/** What the first two steps of a command found, before any workflow-specific check. */
internal sealed interface CommandStart {
    /** Nothing committed under this operation yet; continue against [profile]. */
    data class Proceed(val profile: FinancialProfile) : CommandStart

    /** The history was replaced since the command was prepared. */
    data class Stale(val current: Long) : CommandStart

    /** This operation already committed with this very input. */
    data class Replay(val original: StoredReceipt) : CommandStart

    /** This operation already committed something else. */
    data object Conflict : CommandStart

    data class Damaged(val failure: StorageFailure.Integrity) : CommandStart
}

/**
 * Step 1, the generation: a command prepared against a replaced history is refused
 * even if it once committed. Step 2, replay: the same kind and input is the original
 * result; anything else under that identifier is a conflict.
 */
internal suspend fun SelloDatabase.startCommand(
    operationId: OperationId,
    generation: Long,
    kind: OperationKind,
    digest: String
): CommandStart {
    val profile = when (val stored = profileFrom(profileDao().rows())) {
        is Outcome.Success -> stored.value
        is Outcome.Failure -> return CommandStart.Damaged(stored.error)
    }
    if (profile.generation != generation) return CommandStart.Stale(profile.generation)
    val receipt = operationReceiptDao().find(operationId.value)
        ?: return CommandStart.Proceed(profile)
    return when (val original = receipt.toStored()) {
        is Outcome.Failure -> CommandStart.Damaged(original.error)

        is Outcome.Success -> {
            val same = original.value.receipt.kind == kind && original.value.inputDigest == digest
            if (same) CommandStart.Replay(original.value) else CommandStart.Conflict
        }
    }
}

/**
 * The last step: advance the revision by one and store the receipt. Returns the receipt.
 * [subjectId] is absent only for a change with no single subject, such as catch-up.
 */
internal suspend fun SelloDatabase.commitCommand(
    profile: FinancialProfile,
    operationId: OperationId,
    kind: OperationKind,
    digest: String,
    subjectId: String?,
    at: Instant
): StoredReceipt {
    val revision = nextCounter(profile.revision, ProfileEntity.TABLE, null, "revision")
    val advanced = profileDao().advanceRevision(profile.generation, profile.revision, revision)
    check(advanced == 1) { "The profile changed inside the transaction that read it" }
    val receipt = OperationReceiptEntity(
        operationId = operationId.value,
        kind = kind.key,
        inputDigest = digest,
        generation = profile.generation,
        revision = revision,
        subjectId = subjectId,
        committedAt = at.toEpochMilli()
    )
    operationReceiptDao().insert(receipt)
    return when (val stored = receipt.toStored()) {
        is Outcome.Success -> stored.value
        is Outcome.Failure -> error("A receipt written by this command did not read back")
    }
}
