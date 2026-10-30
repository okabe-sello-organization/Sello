package com.software.sello.data

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.repository.CommandRun
import com.software.sello.data.repository.CounterExhausted
import com.software.sello.data.repository.nextCounter
import com.software.sello.data.repository.runCommand
import com.software.sello.domain.model.StorageFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What a command's transaction turns into, for the endings that are not a write. */
@RunWith(AndroidJUnit4::class)
class CommandRunTest {
    @get:Rule
    val files = DatabaseFiles()

    private val exhausted = StorageFailure.Integrity("profile", null, "revision")

    @Test
    fun theValueAfterACounterIsOneMoreUpToTheLargest() {
        assertEquals(1L, nextCounter(0, "profile", null, "revision"))
        assertEquals(43L, nextCounter(42, "expense", null, "sequence"))
        assertEquals(Long.MAX_VALUE, nextCounter(Long.MAX_VALUE - 1, "category", "a", "version"))
    }

    @Test
    fun nothingFollowsTheLargestValueAndTheFailureNamesTheCounter() {
        val failure = assertThrows(CounterExhausted::class.java) {
            nextCounter(Long.MAX_VALUE, "category", "a", "version")
        }

        assertEquals(StorageFailure.Integrity("category", "a", "version"), failure.failure)
    }

    @Test
    fun anExhaustedCounterIsACertainRollbackWithWhatWasWrittenBeforeItUndone() = runBlocking {
        val database = files.open()

        val run = database.runCommand {
            database.categoryDao().insert(category(1, "Mercado").toEntity())
            throw CounterExhausted(exhausted)
        }

        assertEquals(CommandRun.RolledBack(exhausted), run)
        assertEquals(0, database.count("category"))
    }

    @Test
    fun aRefusedCommitIsUnknownAndTheNextCommandOnTheSameHandleIsSaved() = runBlocking {
        val database = files.open()

        val refused = database.runCommand {
            // A deferred check refuses the transaction only when it commits.
            database.openHelper.writableDatabase.execSQL("PRAGMA defer_foreign_keys = ON")
            database.expenseDao().insert(expense(100, 1, "2026-10-09", 1, 5_000).toEntity())
        }
        val next = database.runCommand {
            database.categoryDao().insert(category(1, "Mercado").toEntity())
        }

        assertEquals(CommandRun.Unknown, refused)
        assertEquals(CommandRun.Finished(Unit), next)
        assertEquals(1, database.count("category"))
        assertEquals(0, database.count("expense"))
    }

    @Test
    fun aCommandInsideAnotherTransactionFailsAtOnceAndNothingIsSaved() = runBlocking {
        val database = files.open()
        var decided = false

        val failure = runCatching {
            database.withTransaction {
                database.categoryDao().insert(category(1, "Mercado").toEntity())
                database.runCommand { decided = true }
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(decided)
        assertEquals(0, database.count("category"))
    }

    @Test
    fun anyOtherArithmeticFaultIsNotDressedUpAsAStorageFailure() {
        val database = files.open()

        // A sum that overflows is a fault in the command, not a full counter.
        assertThrows(ArithmeticException::class.java) {
            runBlocking { database.runCommand { Math.addExact(Long.MAX_VALUE, 1) } }
        }
        assertEquals(0, database.count("category"))
    }
}
