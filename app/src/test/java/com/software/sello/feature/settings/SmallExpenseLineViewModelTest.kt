package com.software.sello.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.testing.testMoney
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Gastos hormiga's form (SELLO-194): whole pesos from 1.000 to 100.000, nothing else saved. */
@OptIn(ExperimentalCoroutinesApi::class)
class SmallExpenseLineViewModelTest {
    private val preferences = HandSmallExpenses()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun form(saved: SavedStateHandle = SavedStateHandle()) =
        SmallExpenseLineViewModel(saved, preferences, testMoney())

    private fun SmallExpenseLineViewModel.type(vararg keys: String) = keys.forEach { key ->
        onAction(
            if (key == "000") {
                SmallExpenseLineAction.TripleZero
            } else {
                SmallExpenseLineAction.Digit(key.toInt())
            }
        )
    }

    @Test
    fun theFormStartsEmptyWithTheLineInUseUnderIt() = runTest {
        val form = form()

        val state = form.state.value
        assertNull(state.amount)
        assertEquals("20.000", state.current)
        assertFalse(state.canSubmit)
    }

    @Test
    fun theFormWaitsForTheLineInUseBeforeItCanSave() = runTest {
        preferences.read.value = null
        val form = form()
        form.type("15", "000")

        assertTrue(form.state.value.loading)
        assertFalse(form.state.value.canSubmit)
        form.onAction(SmallExpenseLineAction.Submit)
        assertTrue(preferences.written.isEmpty())
    }

    @Test
    fun aLineInTheRangeIsSavedAndTheFormCloses() = runTest {
        val form = form()
        // A leading zero is ignored, then 1, 5 and the 000 key: 15.000.
        form.type("0", "1", "5", "000")

        assertEquals("15.000", form.state.value.amount)
        assertNull(form.state.value.problem)
        form.onAction(SmallExpenseLineAction.Submit)

        assertEquals(listOf(SmallExpenseLine.of(15_000)), preferences.written)
        assertTrue(form.state.value.closed)
    }

    @Test
    fun theEdgesOfTheRangeAreSavedAsTheyAre() = runTest {
        val lowest = form()
        lowest.type("1", "000")
        lowest.onAction(SmallExpenseLineAction.Submit)
        val highest = form()
        highest.type("1", "0", "0", "000")
        highest.onAction(SmallExpenseLineAction.Submit)

        assertEquals(
            listOf(SmallExpenseLine.of(1_000), SmallExpenseLine.of(100_000)),
            preferences.written
        )
    }

    @Test
    fun aLineUnderOneThousandIsRefusedWithAMessageAndNothingIsSaved() = runTest {
        val form = form()
        form.type("9", "9", "9")

        assertEquals("999", form.state.value.amount)
        assertEquals(LineProblem.BelowMinimum, form.state.value.problem)
        assertFalse(form.state.value.canSubmit)
        form.onAction(SmallExpenseLineAction.Submit)

        assertTrue(preferences.written.isEmpty())
        assertFalse(form.state.value.closed)
    }

    @Test
    fun aLineOverOneHundredThousandIsRefusedWithAMessageAndNothingIsSaved() = runTest {
        val form = form()
        form.type("1", "0", "0", "0", "0", "1")

        assertEquals("100.001", form.state.value.amount)
        assertEquals(LineProblem.AboveMaximum, form.state.value.problem)
        form.onAction(SmallExpenseLineAction.Submit)

        assertTrue(preferences.written.isEmpty())
        // Taking the extra digit away makes it 10.000, which may be saved.
        form.onAction(SmallExpenseLineAction.Backspace)
        assertEquals("10.000", form.state.value.amount)
        assertNull(form.state.value.problem)
    }

    @Test
    fun aDoubleTapSavesOnce() = runTest {
        val hold = CompletableDeferred<Unit>()
        preferences.hold = hold
        val form = form()
        form.type("5", "000")

        form.onAction(SmallExpenseLineAction.Submit)
        assertTrue(form.state.value.busy)
        form.onAction(SmallExpenseLineAction.Submit)
        // Back and typing wait while it is being saved.
        form.onAction(SmallExpenseLineAction.Back)
        form.type("1")
        assertFalse(form.state.value.closed)
        assertEquals("5.000", form.state.value.amount)
        hold.complete(Unit)

        assertEquals(listOf(SmallExpenseLine.of(5_000)), preferences.written)
        assertTrue(form.state.value.closed)
    }

    @Test
    fun aSaveThatFailsChangesNothingAndSaysSoThenCanBeTriedAgain() = runTest {
        preferences.fail = true
        val form = form()
        form.type("8", "000")

        form.onAction(SmallExpenseLineAction.Submit)

        assertTrue(form.state.value.notSaved)
        assertFalse(form.state.value.busy)
        assertFalse(form.state.value.closed)
        assertEquals("20.000", form.state.value.current)
        assertTrue(preferences.written.isEmpty())

        preferences.fail = false
        form.onAction(SmallExpenseLineAction.Submit)
        assertEquals(listOf(SmallExpenseLine.of(8_000)), preferences.written)
        assertTrue(form.state.value.closed)
    }

    @Test
    fun theTypedLineOutlivesProcessDeath() = runTest {
        val saved = SavedStateHandle()
        form(saved).type("1", "2", "000")

        // A new view model on the same saved state, as after process death.
        val again = form(saved)

        assertEquals("12.000", again.state.value.amount)
        again.onAction(SmallExpenseLineAction.Submit)
        assertEquals(listOf(SmallExpenseLine.of(12_000)), preferences.written)
    }

    @Test
    fun aDraftThisFormNeverWroteIsNoDraft() = runTest {
        for (stored in listOf("abc", "0150", "-5", "")) {
            val form = form(SavedStateHandle(mapOf("small.line.draft" to stored)))

            assertNull(stored, form.state.value.amount)
        }
    }

    @Test
    fun aLineThatCannotBeReadIsSaidAndReintentarReadsItAgain() = runTest {
        preferences.read.value = PreferenceRead.Failed(PreferenceFailure.Unavailable, null)
        val form = form()

        assertTrue(form.state.value.loadFailed)
        assertFalse(form.state.value.canSubmit)
        val asked = preferences.observations
        form.onAction(SmallExpenseLineAction.Retry)

        assertEquals(asked + 1, preferences.observations)
        preferences.read.value = PreferenceRead.Ready(SmallExpenseLine.of(30_000)!!)
        assertFalse(form.state.value.loadFailed)
        assertEquals("30.000", form.state.value.current)
    }

    @Test
    fun aLineThatCannotBeReadAgainKeepsTheLastOneRead() = runTest {
        preferences.read.value =
            PreferenceRead.Failed(PreferenceFailure.Unavailable, SmallExpenseLine.of(30_000))
        val form = form()

        assertFalse(form.state.value.loadFailed)
        assertEquals("30.000", form.state.value.current)
    }

    @Test
    fun clearEmptiesTheDraftAndBackCloses() = runTest {
        val form = form()
        form.type("4", "000")

        form.onAction(SmallExpenseLineAction.Clear)
        assertNull(form.state.value.amount)
        form.onAction(SmallExpenseLineAction.Back)

        assertTrue(form.state.value.closed)
        assertTrue(preferences.written.isEmpty())
    }
}
