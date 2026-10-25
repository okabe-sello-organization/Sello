package com.software.sello.composition

import androidx.lifecycle.ViewModelProvider
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CheckedBackup
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedRestore
import com.software.sello.feature.startup.StartupRestore
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Starting the app against storage a test scripts: each open and each move is counted,
 * and a restore from the failure screen is logged step by step.
 */
class StartupTest {
    private val corrupt = StorageFailure.Unavailable("SQLiteDatabaseCorruptException")
    private val full = StorageFailure.Unavailable("SQLiteFullException")
    private val factory = object : ViewModelProvider.Factory {}

    /** What happened, in order: each open, move, read, stage and replacement. */
    private val events = mutableListOf<String>()
    private val opens = mutableListOf<Outcome<ViewModelProvider.Factory, StorageFailure>>()
    private var opened = 0
    private var moves = 0
    private var move: Outcome<Unit, StorageFailure> = Outcome.Success(Unit)
    private val restores = HandRestores(events)
    private val bytes = byteArrayOf(1, 2, 3)
    private val checked = CheckedBackup(
        Instant.parse("2026-10-22T14:30:00Z"),
        ZoneId.of("America/Lima"),
        BackupSummary(categories = 2, expenses = 3, incomes = 1, 0, 0)
    )
    private var read: Outcome<ChosenBackup, RestoreRejection> =
        Outcome.Success(ChosenBackup(bytes, checked))
    private val restored = RestoreOutcome.Restored(
        BackupSummary(2, 3, 1, 0, 0),
        PendingPreferences(null, null)
    )

    /** Answers each open with the next scripted result; a test never opens more than it scripted. */
    private fun startup(
        vararg results: Outcome<ViewModelProvider.Factory, StorageFailure>
    ): Startup {
        opens += results
        return Startup(
            start = {
                events += "open"
                when (val result = opens[opened++]) {
                    is Outcome.Success -> Outcome.Success(Started(result.value, restores))
                    is Outcome.Failure -> result
                }
            },
            moveUnreadable = {
                events += "move"
                moves++
                move
            },
            readBackup = {
                events += "read ${it.id}"
                read
            }
        )
    }

    /** Restores that answer as told and log what they were asked. */
    private class HandRestores(private val events: MutableList<String>) : Restores {
        var stageAnswer: Outcome<StagedRestore, StageFailure>? = null
        var replaceAnswer: RestoreOutcome? = null
        var applyAnswer: RestoreOutcome.Restored? = null

        override suspend fun stage(bytes: ByteArray): Outcome<StagedRestore, StageFailure> {
            events += "stage ${bytes.toList()}"
            return stageAnswer!!
        }

        override suspend fun staged(): StagedRestore? = null

        override suspend fun discard() = Unit

        override suspend fun replace(staged: StagedRestore): RestoreOutcome {
            events += "replace ${staged.operationId.value}"
            return replaceAnswer!!
        }

        override suspend fun pending(): RestoreOutcome.Restored? = null

        override suspend fun applyPending(): RestoreOutcome.Restored? {
            events += "apply"
            return applyAnswer
        }
    }

    private fun staged() = StagedRestore(
        operationId = (OperationId.of("00000000-0000-4000-8000-000000005000") as Outcome.Success)
            .value,
        backupAt = checked.backupAt,
        current = BackupSummary(0, 0, 0, 0, 0),
        replacement = checked.contents,
        generation = 1,
        revision = 0
    )

    private fun willRestore(outcome: RestoreOutcome = restored) {
        restores.stageAnswer = Outcome.Success(staged())
        restores.replaceAnswer = outcome
    }

    private val source = DocumentSource("content://provider/document/7")

    @Test
    fun dataThatCannotBeOpenedIsAFailedStartNotACrash() {
        val startup = startup(Outcome.Failure(corrupt))

        assertEquals(StartupState.Failed(corrupt), startup.state.value)
        assertEquals(1, opened)
    }

    @Test
    fun dataThatOpensIsReadyWithTheViewModelsBuiltForIt() {
        val startup = startup(Outcome.Success(factory))

        assertSame(factory, (startup.state.value as StartupState.Ready).viewModels)
    }

    @Test
    fun tryingAgainOpensOnceMoreAndBecomesReadyWhenItWorks() {
        val startup = startup(Outcome.Failure(corrupt), Outcome.Success(factory))

        assertTrue(startup.retry())

        assertSame(factory, (startup.state.value as StartupState.Ready).viewModels)
        assertEquals(2, opened)
    }

    @Test
    fun tryingAgainThatFailsStaysFailedWithTheLatestReason() {
        val startup = startup(Outcome.Failure(full), Outcome.Failure(corrupt))

        assertFalse(startup.retry())

        assertEquals(StartupState.Failed(corrupt), startup.state.value)
        assertEquals(2, opened)
    }

    @Test
    fun onceReadyTryingAgainNeverOpensASecondStore() {
        val startup = startup(Outcome.Failure(corrupt), Outcome.Success(factory))
        startup.retry()

        assertTrue(startup.retry())
        assertTrue(startup.retry())

        assertEquals(2, opened)
        assertSame(factory, (startup.state.value as StartupState.Ready).viewModels)
    }

    @Test
    fun dataThatOpenedIsNeverSetAside() {
        val startup = startup(Outcome.Success(factory))

        assertFalse(startup.setAside())

        assertEquals(0, moves)
        assertSame(factory, (startup.state.value as StartupState.Ready).viewModels)
    }

    @Test
    fun settingAsideMovesTheDataAndOpensNothingByItself() {
        val startup = startup(Outcome.Failure(corrupt))

        assertTrue(startup.setAside())

        assertEquals(1, moves)
        assertEquals(1, opened)
        assertEquals(StartupState.Failed(corrupt), startup.state.value)
    }

    @Test
    fun dataThatCannotBeMovedIsReportedAndLeavesTheStartFailed() {
        move = Outcome.Failure(StorageFailure.Unavailable("AccessDeniedException"))
        val startup = startup(Outcome.Failure(corrupt))

        assertFalse(startup.setAside())

        assertEquals(1, moves)
        assertEquals(StartupState.Failed(corrupt), startup.state.value)
    }

    @Test
    fun checkingABackupReadsItAndMovesOrOpensNothing() = runTest {
        val startup = startup(Outcome.Failure(corrupt))

        val answer = startup.check(source)

        assertEquals(Outcome.Success(checked), answer)
        assertEquals(listOf("open", "read ${source.id}"), events)
        assertEquals(StartupState.Failed(corrupt), startup.state.value)
    }

    @Test
    fun aRestoreSetsTheDataAsideOpensANewStoreAndFillsItFromTheCheckedFile() = runTest {
        willRestore()
        val startup = startup(Outcome.Failure(corrupt), Outcome.Success(factory))
        startup.check(source)

        val ended = startup.restore()

        assertEquals(StartupRestore.Restored(restored), ended)
        assertEquals(
            listOf(
                "open",
                "read ${source.id}",
                "move",
                "open",
                "stage [1, 2, 3]",
                "replace 00000000-0000-4000-8000-000000005000"
            ),
            events
        )
        // The new store is never shown: the app starts again in a new process.
        assertEquals(StartupState.Failed(corrupt), startup.state.value)
    }

    @Test
    fun withoutACheckedFileOrAfterARejectedOneNothingIsMoved() = runTest {
        val startup = startup(Outcome.Failure(corrupt))
        assertEquals(StartupRestore.NotSetAside, startup.restore())

        startup.check(source)
        read = Outcome.Failure(RestoreRejection.NotABackup)
        startup.check(source)
        assertEquals(StartupRestore.NotSetAside, startup.restore())

        read = Outcome.Success(ChosenBackup(bytes, checked))
        startup.check(source)
        startup.forget()
        assertEquals(StartupRestore.NotSetAside, startup.restore())

        assertEquals(0, moves)
        assertEquals(1, opened)
    }

    @Test
    fun dataThatCannotBeSetAsideIsNotRestoredOverAndNothingOpens() = runTest {
        move = Outcome.Failure(StorageFailure.Unavailable("AccessDeniedException"))
        val startup = startup(Outcome.Failure(corrupt))
        startup.check(source)

        assertEquals(StartupRestore.NotSetAside, startup.restore())

        assertEquals(1, opened)
        assertEquals(StartupState.Failed(corrupt), startup.state.value)
    }

    @Test
    fun aNewStoreThatWillNotOpenIsAnInterruptedRestoreWithTheLatestReason() = runTest {
        val startup = startup(Outcome.Failure(corrupt), Outcome.Failure(full))
        startup.check(source)

        assertEquals(StartupRestore.Interrupted, startup.restore())

        assertEquals(StartupState.Failed(full), startup.state.value)
        assertTrue(events.none { it.startsWith("stage") })
    }

    @Test
    fun aReplacementThatFailedOrMayHaveFailedIsInterruptedNotRestored() = runTest {
        val outcomes = listOf(
            RestoreOutcome.Failed,
            RestoreOutcome.OutcomeUnknown(staged().operationId)
        )
        for (outcome in outcomes) {
            willRestore(outcome)
            opened = 0
            opens.clear()
            val startup = startup(Outcome.Failure(corrupt), Outcome.Success(factory))
            startup.check(source)

            assertEquals(StartupRestore.Interrupted, startup.restore())
        }
    }

    @Test
    fun onceARestoreOpenedTheNewStoreNothingOpensOrMovesAgain() = runTest {
        willRestore()
        val startup = startup(Outcome.Failure(corrupt), Outcome.Success(factory))
        startup.check(source)
        startup.restore()

        assertFalse(startup.retry())
        assertFalse(startup.setAside())
        startup.check(source)
        assertEquals(StartupRestore.NotSetAside, startup.restore())

        assertEquals(2, opened)
        assertEquals(1, moves)
    }

    @Test
    fun pendingPreferencesAreAppliedInTheStoreTheRestoreOpened() = runTest {
        willRestore()
        restores.applyAnswer = restored
        val startup = startup(Outcome.Failure(corrupt), Outcome.Success(factory))
        assertNull(startup.applyPending())
        startup.check(source)
        startup.restore()

        assertEquals(restored, startup.applyPending())
        assertEquals(1, events.count { it == "apply" })
    }
}
