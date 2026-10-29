package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.repository.CounterExhausted
import com.software.sello.data.repository.inputDigest
import com.software.sello.data.repository.proceedOr
import com.software.sello.data.repository.runOutcome
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** How the shared outcome is told for each way a command's start and transaction can end. */
@RunWith(AndroidJUnit4::class)
class CommandOutcomesTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger = TestLedger(files)
    private val database get() = ledger.database

    @Before
    fun setUp() {
        ledger.start()
    }

    // ---- runOutcome ----

    @Test
    fun aFinishedTransactionIsWhatItDecided() = runBlocking {
        val operation = ledger.operation()

        val outcome = database.runOutcome<String, Nothing>(operation) {
            Committed("decided", replayed = false)
        }

        assertEquals(Committed("decided", replayed = false), outcome)
    }

    @Test
    fun aRollbackIsACertainStorageFailureWithNothingWritten() = runBlocking {
        val exhausted = StorageFailure.Integrity("profile", null, "revision")

        val outcome = database.runOutcome<String, Nothing>(ledger.operation()) {
            database.categoryDao().insert(category(1, "Mercado").toEntity())
            throw CounterExhausted(exhausted)
        }

        assertEquals(Rejected(CommandRejection.StorageFailed(exhausted)), outcome)
        assertEquals(0, database.count("category"))
    }

    @Test
    fun aFailureAtTheCommitItselfIsUnknownForTheSameOperation() = runBlocking {
        val operation = ledger.operation()
        database.categoryDao().insert(category(1, "Mercado").toEntity())

        val outcome = database.runOutcome<String, Nothing>(operation) {
            database.expenseDao().insert(expense(100, 1, "2026-10-09", 1, 5_000).toEntity())
            // Deferred checks refuse the transaction only when it commits: by then every
            // statement has run without error.
            database.openHelper.writableDatabase.apply {
                execSQL("PRAGMA defer_foreign_keys = ON")
                execSQL("DELETE FROM category")
            }
            Committed("decided", replayed = false)
        }

        assertEquals(CommandOutcome.OutcomeUnknown(operation), outcome)
        assertEquals(0, database.count("expense"))
        assertEquals(1, database.count("category"))
    }

    @Test
    fun aCallerCancelledBeforeTheStartIsToldSoAndNothingRuns() {
        var decided = false

        assertThrows(CancellationException::class.java) {
            runBlocking {
                coroutineScope {
                    cancel()
                    database.runOutcome<String, Nothing>(ledger.operation()) {
                        decided = true
                        Committed("decided", replayed = false)
                    }
                }
            }
        }

        assertFalse(decided)
    }

    @Test
    fun aCallerCancelledDuringTheTransactionIsToldSoWhileTheWriteStands() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                coroutineScope {
                    val caller = coroutineContext.job
                    database.runOutcome<String, Nothing>(ledger.operation()) {
                        caller.cancel()
                        database.categoryDao().insert(category(1, "Mercado").toEntity())
                        Committed("decided", replayed = false)
                    }
                }
            }
        }

        // The cancellation says nothing about the write: it ran to its end.
        assertEquals(1, database.count("category"))
    }

    // ---- proceedOr ----

    @Test
    fun aNewOperationProceedsWithTheCurrentProfile() = runBlocking {
        val profile = database.proceedOr(
            ledger.operation(),
            1,
            OperationKind.CreateCategory,
            "digest",
            { it.receipt }
        ) { error("settled: $it") }

        assertEquals(1L, profile.generation)
        assertEquals(0L, profile.revision)
    }

    @Test
    fun aReplacedHistoryIsAStaleGenerationNamingTheCurrentOne() = runBlocking {
        val settled = settledBy(OperationKind.CreateCategory, "digest", generation = 2)

        assertEquals(Rejected(CommandRejection.StaleGeneration(1)), settled)
    }

    @Test
    fun theSameOperationAndInputAgainIsTheOriginalReceiptReplayed() = runBlocking {
        val (command, receipt) = committedCategory()

        val settled =
            settledBy(OperationKind.CreateCategory, inputDigest(command), command.operationId)

        assertEquals(Committed(receipt, replayed = true), settled)
    }

    @Test
    fun theSameOperationWithOtherInputOrKindIsAConflict() = runBlocking {
        val (command, _) = committedCategory()
        val digest = inputDigest(command)

        val otherInput = settledBy(OperationKind.CreateCategory, "other", command.operationId)
        val otherKind = settledBy(OperationKind.CreateExpense, digest, command.operationId)

        assertEquals(Rejected(CommandRejection.OperationConflict), otherInput)
        assertEquals(Rejected(CommandRejection.OperationConflict), otherKind)
    }

    @Test
    fun aDamagedReceiptIsAStorageFailureNotANewOperation() = runBlocking {
        val (command, _) = committedCategory()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE operation_receipt SET input_digest = 'not a digest'"
        )

        val settled =
            settledBy(OperationKind.CreateCategory, inputDigest(command), command.operationId)

        val reason = (settled as Rejected).reason
        assertTrue(
            "$reason",
            (reason as CommandRejection.StorageFailed).failure is StorageFailure.Integrity
        )
    }

    private suspend fun committedCategory() = ledger.createCategory("Mercado").let { command ->
        val outcome = ledger.categories.submit(command)
        command to (outcome as Committed).value.receipt
    }

    /** What [proceedOr] settles with instead of proceeding. */
    private suspend fun settledBy(
        kind: OperationKind,
        digest: String,
        operation: OperationId = ledger.operation(),
        generation: Long = 1
    ): CommandOutcome<Any, Nothing> {
        database.proceedOr(operation, generation, kind, digest, { it.receipt }) { return it }
        throw AssertionError("proceeded")
    }
}
