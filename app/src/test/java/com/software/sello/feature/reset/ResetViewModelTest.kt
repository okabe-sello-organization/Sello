package com.software.sello.feature.reset

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.ResetPreview
import com.software.sello.domain.port.Resets
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResetViewModelTest {
    private val preview = ResetPreview(BackupSummary(3, 42, 2, 0, 0), generation = 1, revision = 9)

    /** Resets that answer as told and record what they were asked. */
    private class HandResets : Resets {
        var previewAnswer: Outcome<ResetPreview, StorageFailure> =
            Outcome.Failure(StorageFailure.Unavailable("test"))
        var resetAnswer: ResetOutcome = ResetOutcome.Failed
        var finishAnswer: ResetOutcome.Erased? = null
        var resetGate: CompletableDeferred<Unit>? = null
        var previews = 0
        val resets = mutableListOf<Pair<ResetPreview, OperationId>>()
        var finishes = 0

        override suspend fun preview(): Outcome<ResetPreview, StorageFailure> {
            previews++
            return previewAnswer
        }

        override suspend fun reset(preview: ResetPreview, operationId: OperationId): ResetOutcome {
            resetGate?.await()
            resets += preview to operationId
            return resetAnswer
        }

        override suspend fun pending(): Boolean = finishAnswer != null

        override suspend fun finishPending(): ResetOutcome.Erased? {
            finishes++
            return finishAnswer
        }
    }

    private val resets = HandResets().apply { previewAnswer = Outcome.Success(preview) }
    private val saved = SavedStateHandle()
    private var restarts = 0
    private var nextId = 0

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.model(handle: SavedStateHandle = saved): ResetViewModel {
        val session = ResetSession(
            resets,
            { "00000000-0000-4000-8000-%012d".format(7_000 + nextId++) },
            backgroundScope
        )
        val model = ResetViewModel(handle, session) { restarts++ }
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
        return model
    }

    private fun TestScope.confirmWith(model: ResetViewModel, text: String) {
        model.onAction(ResetAction.Continue)
        model.onAction(ResetAction.Typed(text))
        model.onAction(ResetAction.Confirm)
        runCurrent()
    }

    @Test
    fun openingCountsWhatWouldBeErasedAndErasesNothing() = runTest {
        val model = model()

        assertEquals(ResetStep.Ready(preview), model.state.value.step)
        assertFalse(model.state.value.confirming)
        assertTrue(resets.resets.isEmpty())
    }

    @Test
    fun countsThatCannotBeReadAreSaidAndCanBeReadAgain() = runTest {
        resets.previewAnswer = Outcome.Failure(StorageFailure.Unavailable("test"))
        val model = model()
        assertEquals(ResetStep.Unreadable, model.state.value.step)

        resets.previewAnswer = Outcome.Success(preview)
        model.onAction(ResetAction.Retry)
        runCurrent()

        assertEquals(ResetStep.Ready(preview), model.state.value.step)
    }

    @Test
    fun anythingButTheExactWordErasesNothing() = runTest {
        val model = model()

        for (text in listOf("", "borrar", "BORRA", "BORRAR ya", "B O R R A R")) {
            confirmWith(model, text)
        }

        assertTrue(resets.resets.isEmpty())
        assertTrue(model.state.value.confirming)
    }

    @Test
    fun cancellingTheWordGoesBackToTheCountsAndForgetsWhatWasTyped() = runTest {
        val model = model()
        model.onAction(ResetAction.Continue)
        model.onAction(ResetAction.Typed("BORRAR"))

        model.onAction(ResetAction.Cancel)

        assertFalse(model.state.value.confirming)
        assertEquals("", model.state.value.typed)
        assertTrue(resets.resets.isEmpty())
    }

    @Test
    fun theOpenConfirmationAndTheWordSurviveRecreation() = runTest {
        val first = model()
        first.onAction(ResetAction.Continue)
        first.onAction(ResetAction.Typed("BORR"))

        val second = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))

        assertTrue(second.state.value.confirming)
        assertEquals("BORR", second.state.value.typed)
    }

    @Test
    fun theWordErasesOnceEvenWhenPressedAgain() = runTest {
        resets.resetAnswer = ResetOutcome.Erased(preferencesPending = false)
        resets.resetGate = CompletableDeferred()
        val model = model()

        confirmWith(model, " BORRAR ")
        model.onAction(ResetAction.Confirm)
        assertTrue(model.state.value.step is ResetStep.Erasing)
        assertTrue(model.state.value.busy)
        resets.resetGate!!.complete(Unit)
        runCurrent()

        assertEquals(1, resets.resets.size)
        assertEquals(preview, resets.resets.single().first)
        assertEquals(ResetStep.Erased(preferencesPending = false), model.state.value.step)
        assertTrue(model.state.value.erased)
    }

    @Test
    fun countsThatChangedAreShownAgainAndNeedBothConfirmations() = runTest {
        val fresh = preview.copy(current = BackupSummary(3, 43, 2, 0, 0), revision = 10)
        resets.resetAnswer = ResetOutcome.Stale
        val model = model()
        resets.previewAnswer = Outcome.Success(fresh)

        confirmWith(model, "BORRAR")

        assertEquals(ResetStep.Ready(fresh, refreshed = true), model.state.value.step)
        assertFalse(model.state.value.confirming)
        assertEquals("", model.state.value.typed)
        assertFalse(model.state.value.erased)
    }

    @Test
    fun anUnknownOutcomeIsCheckedWithTheSameOperation() = runTest {
        val model = model()
        resets.resetAnswer = ResetOutcome.OutcomeUnknown(
            (OperationId.of("00000000-0000-4000-8000-000000007000") as Outcome.Success).value
        )
        confirmWith(model, "BORRAR")
        assertTrue(model.state.value.step is ResetStep.Unknown)
        assertTrue(model.state.value.erased)

        resets.resetAnswer = ResetOutcome.Erased(preferencesPending = false)
        model.onAction(ResetAction.Check)
        runCurrent()

        assertEquals(ResetStep.Erased(preferencesPending = false), model.state.value.step)
        assertEquals(2, resets.resets.size)
        assertEquals(1, resets.resets.map { it.second }.distinct().size)
    }

    @Test
    fun aFailedEraseSaysNothingChangedAndCountsAgain() = runTest {
        resets.resetAnswer = ResetOutcome.Failed
        val model = model()
        confirmWith(model, "BORRAR")
        assertEquals(ResetStep.Failed, model.state.value.step)
        assertFalse(model.state.value.erased)

        model.onAction(ResetAction.Retry)
        runCurrent()

        assertEquals(ResetStep.Ready(preview), model.state.value.step)
    }

    @Test
    fun pendingPreferencesAreRetriedWithoutErasingAgain() = runTest {
        resets.resetAnswer = ResetOutcome.Erased(preferencesPending = true)
        val model = model()
        confirmWith(model, "BORRAR")
        assertEquals(ResetStep.Erased(preferencesPending = true), model.state.value.step)

        resets.finishAnswer = ResetOutcome.Erased(preferencesPending = false)
        model.onAction(ResetAction.ApplyPreferences)
        runCurrent()

        assertEquals(ResetStep.Erased(preferencesPending = false), model.state.value.step)
        assertEquals(1, resets.resets.size)
    }

    @Test
    fun leavingStartsTheAppAgainOnlyOnceTheHistoryIsOrMayBeErased() = runTest {
        val model = model()
        model.onAction(ResetAction.Leave)
        assertEquals(0, restarts)

        resets.resetAnswer = ResetOutcome.Erased(preferencesPending = false)
        confirmWith(model, "BORRAR")
        model.onAction(ResetAction.Leave)

        assertEquals(1, restarts)
    }

    @Test
    fun anEraseLeftUnfinishedIsFinishedAtStartAndShownOnlyIfPreferencesStillFail() = runTest {
        resets.finishAnswer = ResetOutcome.Erased(preferencesPending = true)
        val stillPending = model()
        assertEquals(ResetStep.Erased(preferencesPending = true), stillPending.state.value.step)

        resets.finishAnswer = ResetOutcome.Erased(preferencesPending = false)
        val finished = model()
        assertEquals(ResetStep.Ready(preview), finished.state.value.step)
    }
}
