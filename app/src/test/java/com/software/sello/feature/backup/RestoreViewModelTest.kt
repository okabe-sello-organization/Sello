package com.software.sello.feature.backup

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.DocumentRead
import com.software.sello.domain.port.DocumentReader
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedRestore
import com.software.sello.navigation.HandFinancialClock
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreViewModelTest {
    private val operation = OperationId.of("00000000-0000-4000-8000-000000005000")
        .let { (it as Outcome.Success).value }
    private val staged = StagedRestore(
        operationId = operation,
        backupAt = Instant.parse("2026-10-22T14:30:00Z"),
        current = BackupSummary(1, 2, 0, 0, 0),
        replacement = BackupSummary(2, 3, 1, 0, 0),
        generation = 1,
        revision = 4
    )
    private val done = RestoreOutcome.Restored(
        BackupSummary(2, 3, 1, 0, 0),
        PendingPreferences(null, null)
    )

    /** Restores that answer as told and count what they were asked. */
    private class HandRestores : Restores {
        var stageAnswer: Outcome<StagedRestore, StageFailure> =
            Outcome.Failure(StageFailure.Storage)
        var replaceAnswer: RestoreOutcome = RestoreOutcome.Failed
        var stagedAnswer: StagedRestore? = null
        var pendingAnswer: RestoreOutcome.Restored? = null
        var applyAnswer: RestoreOutcome.Restored? = null
        var replaceGate: CompletableDeferred<Unit>? = null
        val staged = mutableListOf<Int>()
        var replaced = 0
        var discarded = 0
        var applied = 0

        override suspend fun stage(bytes: ByteArray): Outcome<StagedRestore, StageFailure> {
            staged += bytes.size
            return stageAnswer
        }

        override suspend fun staged(): StagedRestore? = stagedAnswer

        override suspend fun discard() {
            discarded++
        }

        override suspend fun replace(staged: StagedRestore): RestoreOutcome {
            replaceGate?.await()
            replaced++
            return replaceAnswer
        }

        override suspend fun pending(): RestoreOutcome.Restored? = pendingAnswer

        override suspend fun applyPending(): RestoreOutcome.Restored? {
            applied++
            return applyAnswer
        }
    }

    private val restores = HandRestores()
    private var document: DocumentRead = DocumentRead.Read(ByteArray(2_297))
    private var readGate: CompletableDeferred<Unit>? = null
    private val read = mutableListOf<String>()
    private val reader = DocumentReader { source, _ ->
        read += source.id
        readGate?.await()
        document
    }
    private var restarts = 0

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.model(
        handle: SavedStateHandle = SavedStateHandle(),
        scope: CoroutineScope = backgroundScope
    ): Pair<RestoreViewModel, RestoreSession> {
        val session = RestoreSession(restores, reader, scope, 10 * 1024 * 1024)
        val model = RestoreViewModel(
            handle,
            session,
            { restarts++ },
            HandFinancialClock("2026-10-22")
        )
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
        return model to session
    }

    private fun TestScope.choose(model: RestoreViewModel) {
        model.onAction(RestoreAction.Choose)
        runCurrent()
        assertTrue(model.state.value.pickerRequest)
        model.onAction(RestoreAction.PickerOpened)
        model.onAction(RestoreAction.Chosen("content://provider/document/7"))
        runCurrent()
    }

    @Test
    fun aDismissedPickerReadsNothing() = runTest {
        val (model) = model()

        model.onAction(RestoreAction.Choose)
        model.onAction(RestoreAction.PickerOpened)
        model.onAction(RestoreAction.Chosen(null))
        runCurrent()

        assertEquals(RestoreStep.Idle, model.state.value.step)
        assertTrue(restores.staged.isEmpty())
    }

    @Test
    fun aValidFileIsPreviewedAndNothingIsReplacedBeforeConfirmation() = runTest {
        restores.stageAnswer = Outcome.Success(staged)
        val (model) = model()

        choose(model)

        assertEquals(RestoreStep.Previewing(staged), model.state.value.step)
        assertEquals(listOf(2_297), restores.staged)
        assertEquals(0, restores.replaced)
    }

    @Test
    fun eachKindOfBadFileIsNamedAndChangesNothing() = runTest {
        for (reason in RestoreRejection.entries.filter { it != RestoreRejection.Unreadable }) {
            restores.stageAnswer = Outcome.Failure(StageFailure.Rejected(reason))
            val (model) = model()

            choose(model)

            assertEquals(RestoreStep.Rejected(reason), model.state.value.step)
        }
        assertEquals(0, restores.replaced)
    }

    @Test
    fun aDocumentThatCannotBeReadOrIsTooLargeIsRejectedBeforeStaging() = runTest {
        document = DocumentRead.Failed
        val (unreadable) = model()
        choose(unreadable)
        document = DocumentRead.TooLarge(10)
        val (tooLarge) = model()
        choose(tooLarge)

        assertEquals(RestoreStep.Rejected(RestoreRejection.Unreadable), unreadable.state.value.step)
        assertEquals(RestoreStep.Rejected(RestoreRejection.TooLarge), tooLarge.state.value.step)
        assertTrue(restores.staged.isEmpty())
    }

    @Test
    fun cancellingThePreviewForgetsTheFile() = runTest {
        restores.stageAnswer = Outcome.Success(staged)
        val (model) = model()
        choose(model)

        model.onAction(RestoreAction.Cancel)
        runCurrent()

        assertEquals(RestoreStep.Idle, model.state.value.step)
        assertEquals(1, restores.discarded)
        assertEquals(0, restores.replaced)
    }

    @Test
    fun confirmingReplacesOnceEvenWhenPressedAgain() = runTest {
        restores.stageAnswer = Outcome.Success(staged)
        restores.replaceAnswer = done
        restores.replaceGate = CompletableDeferred()
        val (model) = model()
        choose(model)

        model.onAction(RestoreAction.Confirm)
        model.onAction(RestoreAction.Confirm)
        runCurrent()
        assertEquals(RestoreStep.Replacing(staged), model.state.value.step)
        assertTrue(model.state.value.busy)
        restores.replaceGate!!.complete(Unit)
        runCurrent()

        assertEquals(1, restores.replaced)
        assertEquals(RestoreStep.Restored(done), model.state.value.step)
        assertTrue(model.state.value.replaced)
    }

    @Test
    fun aHistoryThatChangedAfterThePreviewIsPreviewedAgain() = runTest {
        val fresh = staged.copy(current = BackupSummary(1, 3, 0, 0, 0), revision = 5)
        restores.stageAnswer = Outcome.Success(staged)
        restores.replaceAnswer = RestoreOutcome.Stale
        restores.stagedAnswer = fresh
        val (model) = model()
        choose(model)

        model.onAction(RestoreAction.Confirm)
        runCurrent()

        assertEquals(RestoreStep.Previewing(fresh, refreshed = true), model.state.value.step)
        assertFalse(model.state.value.replaced)
    }

    @Test
    fun anUnknownOutcomeIsCheckedWithTheSameStagingNotANewOne() = runTest {
        restores.stageAnswer = Outcome.Success(staged)
        restores.replaceAnswer = RestoreOutcome.OutcomeUnknown(operation)
        val (model) = model()
        choose(model)
        model.onAction(RestoreAction.Confirm)
        runCurrent()

        assertEquals(RestoreStep.Unknown(staged), model.state.value.step)
        assertTrue(model.state.value.replaced)

        restores.replaceAnswer = done
        model.onAction(RestoreAction.Confirm)
        runCurrent()

        assertEquals(RestoreStep.Restored(done), model.state.value.step)
        assertEquals(1, restores.staged.size)
        assertEquals(2, restores.replaced)
    }

    @Test
    fun pendingPreferencesAreRetriedWithoutReplacingAgain() = runTest {
        val pending = done.copy(
            preferences = PendingPreferences(null, CategoryOrderingPreference())
        )
        restores.stageAnswer = Outcome.Success(staged)
        restores.replaceAnswer = pending
        restores.applyAnswer = done
        val (model) = model()
        choose(model)
        model.onAction(RestoreAction.Confirm)
        runCurrent()
        assertEquals(RestoreStep.Restored(pending), model.state.value.step)

        model.onAction(RestoreAction.ApplyPreferences)
        runCurrent()

        assertEquals(RestoreStep.Restored(done), model.state.value.step)
        assertEquals(1, restores.replaced)
        // Once at start-up, once on request.
        assertEquals(2, restores.applied)
    }

    @Test
    fun leavingAfterTheReplacementStartsTheAppAgain() = runTest {
        restores.stageAnswer = Outcome.Success(staged)
        restores.replaceAnswer = done
        val (model) = model()
        choose(model)
        model.onAction(RestoreAction.Confirm)
        runCurrent()

        model.onAction(RestoreAction.Leave)

        assertEquals(1, restarts)
    }

    @Test
    fun aFileStagedBeforeARestartIsPreviewedAgain() = runTest {
        restores.stagedAnswer = staged

        val (model) = model()

        assertEquals(RestoreStep.Previewing(staged), model.state.value.step)
    }

    /**
     * The process dies: the session and its scope go with it and only what was saved
     * for the screen comes back, through a new handle as the system would build it.
     */
    private fun TestScope.afterProcessDeath(
        handle: SavedStateHandle,
        process: CoroutineScope
    ): RestoreViewModel {
        process.cancel()
        runCurrent()
        val saved = handle.keys().associateWith { handle.get<Any?>(it) }
        return model(SavedStateHandle(saved)).first
    }

    @Test
    fun aFileStillBeingCheckedWhenTheProcessDiedIsCheckedAgainAndPreviewed() = runTest {
        val handle = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        readGate = CompletableDeferred()
        val (model) = model(handle, process)
        choose(model)
        assertEquals(RestoreStep.Checking, model.state.value.step)

        readGate = null
        restores.stageAnswer = Outcome.Success(staged)
        val restored = afterProcessDeath(handle, process)

        assertEquals(RestoreStep.Previewing(staged), restored.state.value.step)
        assertEquals(List(2) { "content://provider/document/7" }, read)
        assertEquals(0, restores.replaced)
    }

    @Test
    fun aFileThatCannotBeReadAfterTheRestartIsNamedAndNothingIsStaged() = runTest {
        val handle = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        readGate = CompletableDeferred()
        val (model) = model(handle, process)
        choose(model)

        readGate = null
        document = DocumentRead.Failed
        val restored = afterProcessDeath(handle, process)

        assertEquals(
            RestoreStep.Rejected(RestoreRejection.Unreadable),
            restored.state.value.step
        )
        assertTrue(restores.staged.isEmpty())
        assertEquals(0, restores.replaced)
    }

    @Test
    fun aPreviewKeptBeforeTheRestartComesBackWithoutReadingTheFileAgain() = runTest {
        val handle = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        restores.stageAnswer = Outcome.Success(staged)
        val (model) = model(handle, process)
        choose(model)
        assertEquals(RestoreStep.Previewing(staged), model.state.value.step)

        restores.stagedAnswer = staged
        val restored = afterProcessDeath(handle, process)

        assertEquals(RestoreStep.Previewing(staged), restored.state.value.step)
        assertEquals(1, read.size)
        assertEquals(0, restores.replaced)
    }

    @Test
    fun aPreviewBroughtBackIsReplacedOnlyOnceAndOnlyOnConfirmation() = runTest {
        val handle = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        readGate = CompletableDeferred()
        val (model) = model(handle, process)
        choose(model)

        readGate = null
        restores.stageAnswer = Outcome.Success(staged)
        restores.replaceAnswer = done
        restores.replaceGate = CompletableDeferred()
        val restored = afterProcessDeath(handle, process)
        assertEquals(0, restores.replaced)

        restored.onAction(RestoreAction.Confirm)
        restored.onAction(RestoreAction.Confirm)
        restores.replaceGate!!.complete(Unit)
        runCurrent()

        assertEquals(1, restores.replaced)
        assertEquals(RestoreStep.Restored(done), restored.state.value.step)
    }

    @Test
    fun aConfirmedReplacementIsNeverOfferedAgainFromTheFileAfterTheRestart() = runTest {
        val handle = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        restores.stageAnswer = Outcome.Success(staged)
        restores.replaceGate = CompletableDeferred()
        val (model) = model(handle, process)
        choose(model)
        model.onAction(RestoreAction.Confirm)
        runCurrent()
        assertEquals(RestoreStep.Replacing(staged), model.state.value.step)

        // Its outcome is unknown, so the staging is not offered either (RoomRestores).
        restores.stagedAnswer = null
        val restored = afterProcessDeath(handle, process)

        assertEquals(RestoreStep.Idle, restored.state.value.step)
        assertEquals(1, read.size)
    }

    @Test
    fun aCancelledPreviewOrADismissedRejectionStaysClosedAfterTheRestart() = runTest {
        val handle = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        restores.stageAnswer = Outcome.Success(staged)
        val (model) = model(handle, process)
        choose(model)
        model.onAction(RestoreAction.Cancel)
        runCurrent()
        restores.stageAnswer = Outcome.Failure(StageFailure.Rejected(RestoreRejection.Unreadable))
        choose(model)
        assertTrue(model.state.value.step is RestoreStep.Rejected)
        model.onAction(RestoreAction.Dismiss)

        val restored = afterProcessDeath(handle, process)

        assertEquals(RestoreStep.Idle, restored.state.value.step)
        assertEquals(2, read.size)
        assertEquals(0, restores.replaced)
    }

    @Test
    fun preferencesStillPendingAtStartAreShownAndCompletedOnesAreNot() = runTest {
        val pending = done.copy(preferences = PendingPreferences(AppearancePreference(), null))
        restores.applyAnswer = pending
        restores.pendingAnswer = pending
        val (stillPending) = model()
        assertEquals(RestoreStep.Restored(pending), stillPending.state.value.step)

        restores.applyAnswer = done
        restores.pendingAnswer = null
        val (finished) = model()
        assertEquals(RestoreStep.Idle, finished.state.value.step)
    }
}
