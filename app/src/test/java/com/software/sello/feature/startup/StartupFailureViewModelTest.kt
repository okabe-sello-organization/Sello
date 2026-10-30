package com.software.sello.feature.startup

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CheckedBackup
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupFailureViewModelTest {
    /** Recovery a test scripts: what each request answers, and how often it was asked. */
    private class HandRecovery : StartupRecovery {
        var opens = false
        var moves = true
        var retries = 0
        var setAsides = 0

        /** While set, a retry or a move waits for it, as a slow disk makes it. */
        var diskGate: CompletableDeferred<Unit>? = null

        override suspend fun retry(): Boolean {
            retries++
            diskGate?.await()
            return opens
        }

        override suspend fun setAside(): Boolean {
            setAsides++
            diskGate?.await()
            return moves
        }

        var checkAnswer: Outcome<CheckedBackup, RestoreRejection> =
            Outcome.Failure(RestoreRejection.NotABackup)
        var restoreAnswer: StartupRestore = StartupRestore.NotSetAside
        var restoreGate: CompletableDeferred<Unit>? = null
        var applyAnswer: RestoreOutcome.Restored? = null
        val checked = mutableListOf<String>()
        var forgotten = 0
        var restores = 0
        var applied = 0

        override suspend fun check(
            source: DocumentSource
        ): Outcome<CheckedBackup, RestoreRejection> {
            checked += source.id
            return checkAnswer
        }

        override fun forget() {
            forgotten++
        }

        override suspend fun restore(): StartupRestore {
            restores++
            restoreGate?.await()
            return restoreAnswer
        }

        override suspend fun applyPending(): RestoreOutcome.Restored? {
            applied++
            return applyAnswer
        }
    }

    private val recovery = HandRecovery()
    private val saved = SavedStateHandle()
    private var restarts = 0

    private val backup = CheckedBackup(
        Instant.parse("2026-10-22T14:30:00Z"),
        ZoneId.of("America/Lima"),
        BackupSummary(categories = 2, expenses = 3, incomes = 1, 0, 0)
    )
    private val restored = RestoreOutcome.Restored(
        BackupSummary(2, 3, 1, 0, 0),
        PendingPreferences(null, null)
    )

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = saved) =
        StartupFailureViewModel(handle, recovery) { restarts++ }

    /** Restaurar un respaldo, then the open dialog answers with [source]. */
    private fun StartupFailureViewModel.choose(source: String? = "content://provider/document/7") {
        onAction(StartupFailureAction.RestoreBackup)
        assertTrue(state.value.pickerRequest)
        onAction(StartupFailureAction.PickerOpened)
        onAction(StartupFailureAction.Chosen(source))
    }

    private fun previewing(): StartupFailureViewModel {
        recovery.checkAnswer = Outcome.Success(backup)
        return viewModel().also { it.choose() }
    }

    private fun StartupFailureViewModel.confirmWith(text: String) {
        onAction(StartupFailureAction.StartOver)
        onAction(StartupFailureAction.Typed(text))
        onAction(StartupFailureAction.Confirm)
    }

    @Test
    fun itStartsWithNothingAskedAndNothingTouched() {
        val viewModel = viewModel()

        assertEquals(StartupFailureState(), viewModel.state.value)
        assertEquals(0, recovery.retries)
        assertEquals(0, recovery.setAsides)
    }

    @Test
    fun tryingAgainThatStillFailsSaysSo() {
        val viewModel = viewModel()

        viewModel.onAction(StartupFailureAction.Retry)

        assertEquals(1, recovery.retries)
        assertTrue(viewModel.state.value.stillFailing)
    }

    @Test
    fun tryingAgainThatWorksReportsNoFailure() {
        recovery.opens = true
        val viewModel = viewModel()

        viewModel.onAction(StartupFailureAction.Retry)

        assertEquals(1, recovery.retries)
        assertFalse(viewModel.state.value.stillFailing)
    }

    @Test
    fun whileTryingAgainItIsBusyAndAnotherPressOpensNothingMore() {
        recovery.diskGate = CompletableDeferred()
        recovery.opens = true
        val viewModel = viewModel()

        viewModel.onAction(StartupFailureAction.Retry)
        viewModel.onAction(StartupFailureAction.Retry)
        viewModel.onAction(StartupFailureAction.StartOver)

        assertTrue(viewModel.state.value.busy)
        assertFalse(viewModel.state.value.confirming)
        assertEquals(1, recovery.retries)

        recovery.diskGate!!.complete(Unit)
        assertFalse(viewModel.state.value.busy)
        assertFalse(viewModel.state.value.stillFailing)
    }

    @Test
    fun whileTheDataIsSetAsideAnotherConfirmMovesNothingMore() {
        recovery.diskGate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.confirmWith("BORRAR")
        viewModel.onAction(StartupFailureAction.Confirm)
        viewModel.onAction(StartupFailureAction.Retry)

        assertTrue(viewModel.state.value.busy)
        assertFalse(viewModel.state.value.startedOver)
        assertEquals(1, recovery.setAsides)
        assertEquals(0, recovery.retries)

        recovery.diskGate!!.complete(Unit)
        assertFalse(viewModel.state.value.busy)
        assertTrue(viewModel.state.value.startedOver)
    }

    @Test
    fun askingToStartOverOnlyOpensTheWarning() {
        val viewModel = viewModel()

        viewModel.onAction(StartupFailureAction.StartOver)

        assertTrue(viewModel.state.value.confirming)
        assertFalse(viewModel.state.value.canConfirm)
        assertEquals(0, recovery.setAsides)
    }

    @Test
    fun confirmingWithoutTheWordMovesNothing() {
        val viewModel = viewModel()

        for (text in listOf("", "borrar", "BORRA", "BORRAR ya", "B O R R A R")) {
            viewModel.confirmWith(text)
        }

        assertEquals(0, recovery.setAsides)
        assertFalse(viewModel.state.value.startedOver)
        assertTrue(viewModel.state.value.confirming)
    }

    @Test
    fun confirmingWithTheWordMovesTheDataOnceAndStartsOver() {
        val viewModel = viewModel()

        viewModel.confirmWith("BORRAR")
        viewModel.onAction(StartupFailureAction.Confirm)

        assertEquals(1, recovery.setAsides)
        assertTrue(viewModel.state.value.startedOver)
        assertEquals(0, recovery.retries)
    }

    @Test
    fun theWordIsAcceptedWithSpacesAroundItAsAKeyboardLeavesThem() {
        val viewModel = viewModel()

        viewModel.confirmWith(" BORRAR ")

        assertEquals(1, recovery.setAsides)
    }

    @Test
    fun dataThatCouldNotBeMovedIsReportedAndCanBeConfirmedAgain() {
        recovery.moves = false
        val viewModel = viewModel()

        viewModel.confirmWith("BORRAR")

        assertTrue(viewModel.state.value.notSetAside)
        assertFalse(viewModel.state.value.startedOver)
        assertFalse(viewModel.state.value.confirming)

        recovery.moves = true
        viewModel.confirmWith("BORRAR")

        assertEquals(2, recovery.setAsides)
        assertTrue(viewModel.state.value.startedOver)
        assertFalse(viewModel.state.value.notSetAside)
    }

    @Test
    fun cancellingClosesTheWarningAndForgetsWhatWasTyped() {
        val viewModel = viewModel()
        viewModel.onAction(StartupFailureAction.StartOver)
        viewModel.onAction(StartupFailureAction.Typed("BORRAR"))

        viewModel.onAction(StartupFailureAction.Cancel)
        viewModel.onAction(StartupFailureAction.Confirm)

        assertEquals(StartupFailureState(), viewModel.state.value)
        assertEquals(0, recovery.setAsides)
    }

    @Test
    fun anOpenWarningAndWhatWasTypedSurviveRecreationWithoutConfirmingAnything() {
        val first = viewModel()
        first.onAction(StartupFailureAction.StartOver)
        first.onAction(StartupFailureAction.Typed("BORR"))

        val restored = viewModel(saved)

        assertTrue(restored.state.value.confirming)
        assertEquals("BORR", restored.state.value.typed)
        assertEquals(0, recovery.setAsides)
    }

    @Test
    fun aDismissedOpenDialogChecksNothing() {
        val viewModel = viewModel()

        viewModel.choose(source = null)

        assertEquals(StartupRestoreStep.Idle, viewModel.state.value.restore)
        assertFalse(viewModel.state.value.pickerRequest)
        assertTrue(recovery.checked.isEmpty())
    }

    @Test
    fun aValidBackupIsPreviewedAndNothingIsMovedBeforeConfirmation() {
        val viewModel = previewing()

        assertEquals(StartupRestoreStep.Previewing(backup), viewModel.state.value.restore)
        assertEquals(listOf("content://provider/document/7"), recovery.checked)
        assertEquals(0, recovery.restores)
        assertEquals(0, recovery.setAsides)
    }

    @Test
    fun eachKindOfBadFileIsNamedAndAnotherCanBeChosen() {
        for (reason in RestoreRejection.entries) {
            recovery.checkAnswer = Outcome.Failure(reason)
            val viewModel = viewModel()

            viewModel.choose()

            assertEquals(StartupRestoreStep.Rejected(reason), viewModel.state.value.restore)
            viewModel.onAction(StartupFailureAction.RestoreBackup)
            assertTrue(viewModel.state.value.pickerRequest)
        }
        assertEquals(0, recovery.restores)
    }

    @Test
    fun cancellingThePreviewForgetsTheFileAndMovesNothing() {
        val viewModel = previewing()

        viewModel.onAction(StartupFailureAction.CancelRestore)

        assertEquals(StartupRestoreStep.Idle, viewModel.state.value.restore)
        assertEquals(1, recovery.forgotten)
        assertEquals(0, recovery.restores)
    }

    @Test
    fun startingOverAndRestoringCloseEachOther() {
        val viewModel = previewing()

        viewModel.onAction(StartupFailureAction.StartOver)
        assertTrue(viewModel.state.value.confirming)
        assertEquals(StartupRestoreStep.Idle, viewModel.state.value.restore)

        viewModel.onAction(StartupFailureAction.RestoreBackup)
        assertFalse(viewModel.state.value.confirming)
        assertTrue(viewModel.state.value.pickerRequest)
    }

    @Test
    fun confirmingRestoresOnceEvenWhenPressedAgainAndLocksTheOtherChoices() = runTest {
        recovery.restoreAnswer = StartupRestore.Restored(restored)
        recovery.restoreGate = CompletableDeferred()
        val viewModel = previewing()

        viewModel.onAction(StartupFailureAction.ConfirmRestore)
        viewModel.onAction(StartupFailureAction.ConfirmRestore)
        assertEquals(StartupRestoreStep.Restoring(backup), viewModel.state.value.restore)
        // Neither another way out nor leaving cuts a restore short.
        viewModel.onAction(StartupFailureAction.Retry)
        viewModel.onAction(StartupFailureAction.StartOver)
        viewModel.onAction(StartupFailureAction.RestoreBackup)
        viewModel.onAction(StartupFailureAction.Leave)
        recovery.restoreGate!!.complete(Unit)

        assertEquals(1, recovery.restores)
        assertEquals(StartupRestoreStep.Restored(restored), viewModel.state.value.restore)
        assertEquals(0, recovery.retries)
        assertFalse(viewModel.state.value.confirming)
        assertFalse(viewModel.state.value.pickerRequest)
        assertEquals(0, restarts)
    }

    @Test
    fun leavingARestoredBackupStartsTheAppAgain() {
        recovery.restoreAnswer = StartupRestore.Restored(restored)
        val viewModel = previewing()
        viewModel.onAction(StartupFailureAction.ConfirmRestore)

        viewModel.onAction(StartupFailureAction.Leave)

        assertEquals(1, restarts)
    }

    @Test
    fun dataThatCouldNotBeSetAsideIsSaidAndNothingElseChanged() {
        recovery.restoreAnswer = StartupRestore.NotSetAside
        val viewModel = previewing()

        viewModel.onAction(StartupFailureAction.ConfirmRestore)

        assertTrue(viewModel.state.value.notSetAside)
        assertEquals(StartupRestoreStep.Idle, viewModel.state.value.restore)
        assertFalse(viewModel.state.value.restoreBegun)
        assertEquals(0, restarts)
    }

    @Test
    fun anInterruptedRestoreIsSaidAndContinuingStartsTheAppAgain() {
        recovery.restoreAnswer = StartupRestore.Interrupted
        val viewModel = previewing()
        viewModel.onAction(StartupFailureAction.ConfirmRestore)

        assertEquals(StartupRestoreStep.Interrupted, viewModel.state.value.restore)
        viewModel.onAction(StartupFailureAction.Retry)
        assertEquals(0, recovery.retries)

        viewModel.onAction(StartupFailureAction.Leave)
        assertEquals(1, restarts)
    }

    @Test
    fun pendingPreferencesAreRetriedWithoutRestoringAgain() {
        val pending = restored.copy(
            preferences = PendingPreferences(null, CategoryOrderingPreference())
        )
        recovery.restoreAnswer = StartupRestore.Restored(pending)
        recovery.applyAnswer = restored
        val viewModel = previewing()
        viewModel.onAction(StartupFailureAction.ConfirmRestore)
        assertEquals(StartupRestoreStep.Restored(pending), viewModel.state.value.restore)

        viewModel.onAction(StartupFailureAction.ApplyPreferences)

        assertEquals(StartupRestoreStep.Restored(restored), viewModel.state.value.restore)
        assertEquals(1, recovery.restores)
        assertEquals(1, recovery.applied)
    }
}
