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
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedLookup
import com.software.sello.domain.port.StagedRestore
import com.software.sello.navigation.HandFinancialClock
import java.time.Instant
import kotlinx.coroutines.CancellationException
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
        scope: CoroutineScope = backgroundScope,
        session: RestoreSession = RestoreSession(restores, reader, scope, 10 * 1024 * 1024)
    ): Pair<RestoreViewModel, RestoreSession> {
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
        restores.stagedAnswer = StagedLookup.Waiting(fresh)
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

        assertEquals(RestoreStep.Unknown(operation), model.state.value.step)
        assertTrue(model.state.value.replaced)

        // Confirm does nothing here; Comprobar reads the receipt and never replaces.
        model.onAction(RestoreAction.Confirm)
        restores.stagedAnswer = StagedLookup.Replaced(operation)
        restores.applyAnswer = done
        model.onAction(RestoreAction.Check)
        runCurrent()

        assertEquals(RestoreStep.Restored(done), model.state.value.step)
        assertEquals(1, restores.staged.size)
        assertEquals(1, restores.replaced)
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
        restores.stagedAnswer = StagedLookup.Waiting(staged)

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
        return model(saved(handle)).first
    }

    private fun saved(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    /**
     * Tus datos and Respaldo each have their own model and handle over the one session:
     * a file is chosen on Tus datos and finished on Respaldo. After [finish] the process
     * dies with both screens on the back stack, and both come back.
     */
    private fun TestScope.chosenOnTusDatosAfterProcessDeath(
        finish: (RestoreViewModel) -> Unit
    ): List<RestoreViewModel> {
        val tusDatos = SavedStateHandle()
        val respaldo = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        val (picker, session) = model(tusDatos, process)
        val (screen) = model(respaldo, process, session)
        choose(picker)
        assertEquals(RestoreStep.Previewing(staged), screen.state.value.step)
        finish(screen)
        runCurrent()

        process.cancel()
        runCurrent()
        val next = CoroutineScope(backgroundScope.coroutineContext + Job())
        val (restoredScreen, restoredSession) = model(saved(respaldo), next)
        val (restoredPicker) = model(saved(tusDatos), next, restoredSession)
        return listOf(restoredPicker, restoredScreen)
    }

    @Test
    fun aFileChosenOnTusDatosAndCancelledOnRespaldoStaysClosedAfterTheRestart() = runTest {
        restores.stageAnswer = Outcome.Success(staged)

        val restored = chosenOnTusDatosAfterProcessDeath { it.onAction(RestoreAction.Cancel) }

        assertEquals(1, restores.discarded)
        restored.forEach { assertEquals(RestoreStep.Idle, it.state.value.step) }
        assertEquals(1, read.size)
        assertEquals(listOf(2_297), restores.staged)
    }

    @Test
    fun aFileChosenOnTusDatosAndConfirmedOnRespaldoIsNotStagedAgainAfterTheRestart() = runTest {
        restores.stageAnswer = Outcome.Success(staged)
        restores.replaceAnswer = done

        val restored = chosenOnTusDatosAfterProcessDeath { it.onAction(RestoreAction.Confirm) }

        assertEquals(1, restores.replaced)
        restored.forEach { assertEquals(RestoreStep.Idle, it.state.value.step) }
        assertEquals(1, read.size)
        assertEquals(listOf(2_297), restores.staged)
    }

    @Test
    fun aPreviewWhoseStagingIsGoneIsNotReadAgainFromTheFileAfterTheRestart() = runTest {
        val handle = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        restores.stageAnswer = Outcome.Success(staged)
        val (model) = model(handle, process)
        choose(model)
        assertEquals(RestoreStep.Previewing(staged), model.state.value.step)

        // The staging expired or the history moved on: nothing waits any more.
        restores.stagedAnswer = StagedLookup.NotReplaced
        val restored = afterProcessDeath(handle, process)

        assertEquals(RestoreStep.Idle, restored.state.value.step)
        assertEquals(1, read.size)
        assertEquals(listOf(2_297), restores.staged)
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

        restores.stagedAnswer = StagedLookup.Waiting(staged)
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

    /** The process dies while replacing; after the restart the receipt cannot be read. */
    private fun TestScope.unknownAfterTheRestart(): RestoreViewModel {
        val handle = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        restores.stageAnswer = Outcome.Success(staged)
        restores.replaceGate = CompletableDeferred()
        val (model) = model(handle, process)
        choose(model)
        model.onAction(RestoreAction.Confirm)
        runCurrent()
        assertEquals(RestoreStep.Replacing(staged), model.state.value.step)

        restores.stagedAnswer = StagedLookup.Unknown(operation)
        return afterProcessDeath(handle, process)
    }

    @Test
    fun aReplacementWhoseOutcomeIsUnknownAfterTheRestartIsNamedNotHidden() = runTest {
        val restored = unknownAfterTheRestart()

        assertEquals(RestoreStep.Unknown(operation), restored.state.value.step)
        assertTrue(restored.state.value.replaced)
        // Neither offered again from the file nor replaced again.
        assertEquals(1, read.size)
        assertEquals(0, restores.replaced)
    }

    @Test
    fun checkingAgainFindsItRestoredAndAppliesItsPreferences() = runTest {
        val restored = unknownAfterTheRestart()

        restores.stagedAnswer = StagedLookup.Replaced(operation)
        restores.applyAnswer = done
        restored.onAction(RestoreAction.Check)
        runCurrent()

        assertEquals(RestoreStep.Restored(done), restored.state.value.step)
        assertEquals(0, restores.replaced)
    }

    @Test
    fun checkingAgainFindsItNotReplacedAndOffersTheSameFileToConfirmAgain() = runTest {
        val restored = unknownAfterTheRestart()

        restores.stagedAnswer = StagedLookup.Waiting(staged)
        restored.onAction(RestoreAction.Check)
        runCurrent()

        assertEquals(
            RestoreStep.Previewing(staged, notReplaced = true),
            restored.state.value.step
        )
        assertFalse(restored.state.value.replaced)
        assertEquals(0, restores.replaced)

        restores.replaceGate = null
        restores.replaceAnswer = done
        restored.onAction(RestoreAction.Confirm)
        runCurrent()

        assertEquals(RestoreStep.Restored(done), restored.state.value.step)
        assertEquals(1, restores.replaced)
        assertEquals(1, read.size)
    }

    @Test
    fun checkingAgainFindsItNotReplacedAndNoLongerOfferedAndSaysNothingChanged() = runTest {
        val restored = unknownAfterTheRestart()

        restores.stagedAnswer = StagedLookup.NotReplaced
        restored.onAction(RestoreAction.Check)
        runCurrent()

        assertEquals(RestoreStep.Failed, restored.state.value.step)
        assertEquals(0, restores.replaced)
        assertEquals(0, restores.discarded)
    }

    @Test
    fun anOutcomeStillUnknownIsNamedAgainAndCanBeCheckedAgain() = runTest {
        val restored = unknownAfterTheRestart()
        val before = restores.looked

        restored.onAction(RestoreAction.Check)
        runCurrent()
        assertEquals(RestoreStep.Unknown(operation), restored.state.value.step)

        // Nothing left to read is not taken as nothing attempted.
        restores.stagedAnswer = StagedLookup.None
        restored.onAction(RestoreAction.Check)
        runCurrent()

        assertEquals(RestoreStep.Unknown(operation), restored.state.value.step)
        assertEquals(before + 2, restores.looked)
        assertEquals(0, restores.replaced)
        assertEquals(0, restores.discarded)
    }

    @Test
    fun checkingTwiceInARowReadsOnce() = runTest {
        val restored = unknownAfterTheRestart()
        val before = restores.looked
        restores.stagedGate = CompletableDeferred()

        restored.onAction(RestoreAction.Check)
        restored.onAction(RestoreAction.Check)
        runCurrent()
        assertEquals(RestoreStep.Unknown(operation, checking = true), restored.state.value.step)
        assertTrue(restored.state.value.busy)
        restores.stagedAnswer = StagedLookup.Replaced(operation)
        restores.applyAnswer = done
        restores.stagedGate!!.complete(Unit)
        runCurrent()

        assertEquals(before + 1, restores.looked)
        assertEquals(RestoreStep.Restored(done), restored.state.value.step)
    }

    @Test
    fun aCancelledCheckStaysUnknownAndCanBeCheckedAgain() = runTest {
        val restored = unknownAfterTheRestart()
        val gate = CompletableDeferred<Unit>()
        restores.stagedGate = gate

        restored.onAction(RestoreAction.Check)
        runCurrent()
        gate.completeExceptionally(CancellationException("cancelled"))
        runCurrent()

        assertEquals(RestoreStep.Unknown(operation), restored.state.value.step)
        assertFalse(restored.state.value.busy)
        assertEquals(0, restores.replaced)
    }

    @Test
    fun aReplacementThatCommittedBeforeTheProcessDiedIsShownRestored() = runTest {
        val handle = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        restores.stageAnswer = Outcome.Success(staged)
        restores.replaceGate = CompletableDeferred()
        val (model) = model(handle, process)
        choose(model)
        model.onAction(RestoreAction.Confirm)
        runCurrent()

        restores.stagedAnswer = StagedLookup.Replaced(operation)
        restores.applyAnswer = done
        val restored = afterProcessDeath(handle, process)

        assertEquals(RestoreStep.Restored(done), restored.state.value.step)
        assertEquals(0, restores.replaced)
    }

    @Test
    fun theProcessDyingWhileCheckingNamesTheUnknownOutcomeAgain() = runTest {
        val handle = SavedStateHandle()
        val process = CoroutineScope(backgroundScope.coroutineContext + Job())
        restores.stagedAnswer = StagedLookup.Unknown(operation)
        val (model) = model(handle, process)
        restores.stagedGate = CompletableDeferred()
        model.onAction(RestoreAction.Check)
        runCurrent()
        assertEquals(RestoreStep.Unknown(operation, checking = true), model.state.value.step)

        restores.stagedGate = null
        val restored = afterProcessDeath(handle, process)

        assertEquals(RestoreStep.Unknown(operation), restored.state.value.step)
        assertEquals(0, restores.replaced)
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
