package com.software.sello.feature.backup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.BackupPreparationFailure
import com.software.sello.domain.port.BackupRecord
import com.software.sello.domain.port.DocumentDestination
import com.software.sello.domain.port.DocumentWrite
import com.software.sello.domain.port.LastBackup
import com.software.sello.navigation.HandFinancialClock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val exports = HandBackupExports()
    private val writer = HandDocumentWriter()
    private val records = HandBackupRecords()
    private val clock = HandFinancialClock("2026-10-22")
    private val savedAt = Instant.parse("2026-10-22T19:30:00Z")
    private val document = "content://provider/document/1"
    private val earlier = LastBackup(
        Instant.parse("2026-10-02T14:05:00Z"),
        "sello-respaldo-2026-10-02-0905.json",
        3_000,
        g01Summary
    )

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun session(scope: CoroutineScope) =
        BackupSession(exports, writer, records, AuditClock { savedAt }, scope)

    private fun TestScope.model(
        session: BackupSession = session(backgroundScope),
        handle: SavedStateHandle = SavedStateHandle()
    ) = BackupViewModel(handle, session, records, clock).also { model ->
        // The screen collects the state while it is shown.
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
    }

    /** Taps the button and lets the screen hand the request to the picker. */
    private fun TestScope.openPicker(model: BackupViewModel): String {
        model.onAction(BackupAction.Create)
        runCurrent()
        val name = requireNotNull(model.state.value.pickerRequest) { "no picker request" }
        model.onAction(BackupAction.PickerOpened)
        runCurrent()
        return name
    }

    @Test
    fun aFirstVisitHasNoBackupAndNothingUnfinished() = runTest {
        val state = model().state.value

        assertFalse(state.loading)
        assertNull(state.last)
        assertFalse(state.unknownOutcome)
        assertFalse(state.busy)
    }

    @Test
    fun theStateKnowsTheFinancialDaySoTheBackupsAgeFollowsRollover() = runTest {
        val model = model()
        assertEquals(LocalDate.parse("2026-10-22"), model.state.value.today)

        clock.goTo("2026-10-23")
        runCurrent()

        assertEquals(LocalDate.parse("2026-10-23"), model.state.value.today)
    }

    @Test
    fun anUnreadableRecordIsSaidSoAndABackupCanStillBeMade() = runTest {
        val model = model()
        records.failReads()
        runCurrent()

        assertTrue(model.state.value.recordFailed)
        assertNull(model.state.value.last)
        assertFalse(model.state.value.busy)
        // The picker still opens with a name to suggest.
        assertTrue(openPicker(model).isNotEmpty())
    }

    @Test
    fun creatingAsksThePickerOnceWithTheSuggestedName() = runTest {
        val model = model()

        model.onAction(BackupAction.Create)
        model.onAction(BackupAction.Create)
        runCurrent()

        assertEquals("sello-respaldo-2026-10-22-1430.json", model.state.value.pickerRequest)
        model.onAction(BackupAction.PickerOpened)
        runCurrent()
        assertNull(model.state.value.pickerRequest)
        assertTrue(model.state.value.busy)
        assertEquals(0, exports.prepared)
    }

    @Test
    fun dismissingThePickerChangesNothingAndSaysNothing() = runTest {
        records.backupCompleted(earlier)
        val model = model()
        openPicker(model)

        model.onAction(BackupAction.DestinationChosen(null))
        runCurrent()

        val state = model.state.value
        assertEquals(earlier, state.last)
        assertFalse(state.justSaved)
        assertNull(state.failure)
        assertFalse(state.busy)
        assertEquals(0, exports.prepared)
        assertTrue(writer.written.isEmpty())
        assertEquals(BackupRecord(earlier, unfinished = false), records.stored.value)
    }

    @Test
    fun guardadoComesOnlyAfterTheDocumentIsClosed() = runTest {
        val model = model()
        openPicker(model)
        writer.gate = CompletableDeferred()

        model.onAction(BackupAction.DestinationChosen(document))
        runCurrent()

        assertTrue(model.state.value.saving)
        assertFalse(model.state.value.justSaved)
        assertNull(model.state.value.last)
        // The start is on record while the bytes are going out, and is not "unknown".
        assertTrue(records.stored.value.unfinished)
        assertFalse(model.state.value.unknownOutcome)

        writer.gate!!.complete(Unit)
        runCurrent()

        val saved = LastBackup(savedAt, "Mi respaldo.json", 4_096, g01Summary)
        assertTrue(model.state.value.justSaved)
        assertFalse(model.state.value.saving)
        assertEquals(saved, model.state.value.last)
        assertEquals(BackupRecord(saved, unfinished = false), records.stored.value)
        assertEquals(listOf(DocumentDestination(document) to 4_096), writer.written)
    }

    @Test
    fun aDocumentWithoutAProviderNameKeepsTheSuggestedOne() = runTest {
        writer.answer = DocumentWrite.Completed(null, 4_096)
        val model = model()
        openPicker(model)

        model.onAction(BackupAction.DestinationChosen(document))
        runCurrent()

        assertEquals("sello-respaldo-2026-10-22-1430.json", records.stored.value.last?.name)
    }

    @Test
    fun aFailedWriteOrCloseKeepsTheLastGoodBackupAndClearsTheStart() = runTest {
        records.backupCompleted(earlier)
        writer.answer = DocumentWrite.Failed
        val model = model()
        openPicker(model)

        model.onAction(BackupAction.DestinationChosen(document))
        runCurrent()

        val state = model.state.value
        assertEquals(BackupFailure.Write, state.failure)
        assertFalse(state.justSaved)
        assertEquals(earlier, state.last)
        assertFalse(state.unknownOutcome)
        assertEquals(BackupRecord(earlier, unfinished = false), records.stored.value)
    }

    @Test
    fun unreadableDataFailsBeforeAnythingIsWritten() = runTest {
        exports.next = Outcome.Failure(BackupPreparationFailure.StorageUnavailable)
        val model = model()
        openPicker(model)

        model.onAction(BackupAction.DestinationChosen(document))
        runCurrent()

        assertEquals(BackupFailure.Storage, model.state.value.failure)
        assertTrue(writer.written.isEmpty())
        assertFalse(records.stored.value.unfinished)
    }

    @Test
    fun tooMuchDataIsItsOwnFailure() = runTest {
        exports.next = Outcome.Failure(BackupPreparationFailure.TooLarge)
        val model = model()
        openPicker(model)

        model.onAction(BackupAction.DestinationChosen(document))
        runCurrent()

        assertEquals(BackupFailure.TooLarge, model.state.value.failure)
        assertTrue(writer.written.isEmpty())
    }

    @Test
    fun aFileThatFailsItsOwnCheckForAnotherReasonIsNotCalledTooLarge() = runTest {
        exports.next = Outcome.Failure(BackupPreparationFailure.Unrestorable)
        val model = model()
        openPicker(model)

        model.onAction(BackupAction.DestinationChosen(document))
        runCurrent()

        assertEquals(BackupFailure.SelfCheck, model.state.value.failure)
        assertTrue(writer.written.isEmpty())
        assertFalse(records.stored.value.unfinished)
    }

    @Test
    fun aSecondAnswerWhileSavingDoesNotStartASecondWrite() = runTest {
        val session = session(backgroundScope)
        val model = model(session)
        openPicker(model)
        writer.gate = CompletableDeferred()

        model.onAction(BackupAction.DestinationChosen(document))
        session.save(DocumentDestination("content://provider/document/2"), "otro.json")
        model.onAction(BackupAction.Create)
        runCurrent()
        writer.gate!!.complete(Unit)
        runCurrent()

        assertEquals(1, exports.prepared)
        assertEquals(listOf(DocumentDestination(document) to 4_096), writer.written)
        assertNull(model.state.value.pickerRequest)
    }

    @Test
    fun aStartLeftByAProcessThatDiedIsAnUnknownOutcomeNotASave() = runTest {
        // The previous process recorded a start and never an outcome.
        records.backupStarted()

        val model = model()

        assertTrue(model.state.value.unknownOutcome)
        assertFalse(model.state.value.justSaved)
        assertNull(model.state.value.last)

        openPicker(model)
        model.onAction(BackupAction.DestinationChosen(document))
        runCurrent()

        assertFalse(model.state.value.unknownOutcome)
        assertTrue(model.state.value.justSaved)
    }

    @Test
    fun thePickersAnswerReachesTheViewModelOfARecreatedProcess() = runTest {
        val handle = SavedStateHandle()
        openPicker(model(handle = handle))

        // The process died while the picker was open; a new one restores the handle.
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val recreated = model(session(backgroundScope), restored)
        recreated.onAction(BackupAction.DestinationChosen(document))
        runCurrent()

        assertTrue(recreated.state.value.justSaved)
        assertEquals(listOf(DocumentDestination(document) to 4_096), writer.written)
    }

    @Test
    fun leavingTheScreenDoesNotStopASaveThatHasStarted() = runTest {
        val session = session(backgroundScope)
        val store = ViewModelStore()
        val model = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer { BackupViewModel(SavedStateHandle(), session, records, clock) }
            }
        )[BackupViewModel::class]
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
        openPicker(model)
        writer.gate = CompletableDeferred()
        model.onAction(BackupAction.DestinationChosen(document))
        runCurrent()

        // The screen and its view model go away; the session does not.
        store.clear()
        writer.gate!!.complete(Unit)
        runCurrent()

        assertEquals(
            BackupProgress.Saved(LastBackup(savedAt, "Mi respaldo.json", 4_096, g01Summary)),
            session.progress.value
        )
        assertFalse(records.stored.value.unfinished)
    }
}
