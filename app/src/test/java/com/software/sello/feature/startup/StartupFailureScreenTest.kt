package com.software.sello.feature.startup

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.RESTORE_LEAVE_TAG
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CheckedBackup
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.platform.PhoneColor
import com.software.sello.platform.SystemColor
import com.software.sello.presentation.restore.RESTORE_DONE_TAG
import com.software.sello.showSello
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen alone, given state by hand: what each state shows and what each control reports.
 * On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
class StartupFailureScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<StartupFailureAction>()

    private fun show(state: StartupFailureState) {
        rule.showSello(Look()) {
            StartupFailureScreen(state, { actions += it })
        }
    }

    /** The field is found by its label, which is also what a screen reader calls it. */
    private val word get() = rule.onNodeWithContentDescription("Escribe BORRAR para confirmar")

    @Test
    fun itSaysTheDataCouldNotBeOpenedAndThatNothingWasDeleted() {
        show(StartupFailureState())

        rule.onNodeWithText("No pudimos abrir tus datos").assertIsDisplayed()
        rule.onNodeWithText("Sello no pudo leer lo que tienes guardado. No se borró nada.")
            .assertIsDisplayed()
        rule.onNodeWithTag(STARTUP_STILL_FAILING_TAG).assertDoesNotExist()
        rule.onNodeWithTag(STARTUP_NOT_SET_ASIDE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(STARTUP_WARNING_TAG).assertDoesNotExist()
    }

    @Test
    fun tryingAgainAndStartingOverAreSeparateChoicesAndNeitherConfirmsAnything() {
        show(StartupFailureState())

        rule.onNodeWithTag(STARTUP_RETRY_TAG).performClick()
        rule.onNodeWithTag(STARTUP_START_OVER_TAG).performClick()

        assertEquals(
            listOf(StartupFailureAction.Retry, StartupFailureAction.StartOver),
            actions
        )
    }

    @Test
    fun aRetryThatDidNotWorkIsSaid() {
        show(StartupFailureState(stillFailing = true))

        rule.onNodeWithTag(STARTUP_STILL_FAILING_TAG).assertIsDisplayed()
        rule.onNodeWithText("Todavía no se pueden abrir tus datos.").assertIsDisplayed()
    }

    @Test
    fun theWarningSaysWhatStartingOverDoesAndCannotBeConfirmedWithoutTheWord() {
        show(StartupFailureState(confirming = true, typed = "BORRA"))

        rule.onNodeWithTag(STARTUP_WARNING_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("¿Empezar de cero?").assertIsDisplayed()
        rule.onNodeWithText("quedan guardados aparte", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(STARTUP_CONFIRM_TAG).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(STARTUP_CONFIRM_TAG).performClick()

        assertEquals(emptyList<StartupFailureAction>(), actions)
    }

    @Test
    fun whatIsTypedIsReportedExactly() {
        show(StartupFailureState(confirming = true))

        word.performScrollTo().performTextInput("borrar")

        assertEquals(listOf<StartupFailureAction>(StartupFailureAction.Typed("borrar")), actions)
    }

    @Test
    fun withTheWordTypedTheConfirmationAndTheWayOutBothWork() {
        show(StartupFailureState(confirming = true, typed = "BORRAR"))

        rule.onNodeWithTag(STARTUP_CONFIRM_TAG).performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag(STARTUP_CANCEL_TAG).performScrollTo().performClick()

        assertEquals(
            listOf(StartupFailureAction.Confirm, StartupFailureAction.Cancel),
            actions
        )
    }

    @Test
    fun dataThatCouldNotBeMovedIsSaidAndNothingIsClaimed() {
        show(StartupFailureState(notSetAside = true))

        rule.onNodeWithTag(STARTUP_NOT_SET_ASIDE_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("No pudimos apartar tus datos. No se cambió nada.").assertIsDisplayed()
    }

    private val backup = CheckedBackup(
        Instant.parse("2026-10-22T19:30:00Z"),
        ZoneId.of("America/Lima"),
        BackupSummary(categories = 2, expenses = 3, incomes = 1, 0, 0)
    )

    @Test
    fun restoringABackupIsOfferedBetweenTryingAgainAndStartingOver() {
        show(StartupFailureState())

        rule.onNodeWithText("Restaurar un respaldo").assertIsDisplayed()
        val top = { tag: String -> rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(top(STARTUP_RETRY_TAG) < top(STARTUP_RESTORE_TAG))
        assertTrue(top(STARTUP_RESTORE_TAG) < top(STARTUP_START_OVER_TAG))
        rule.onNodeWithTag(STARTUP_RESTORE_TAG).performClick()

        assertEquals(listOf<StartupFailureAction>(StartupFailureAction.RestoreBackup), actions)
    }

    @Test
    fun thePreviewSaysWhatTheBackupHoldsAndThatTheDataIsKeptAside() {
        show(StartupFailureState(restore = StartupRestoreStep.Previewing(backup)))

        rule.onNodeWithTag(STARTUP_RESTORE_PREVIEW_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("¿Restaurar este respaldo?").assertIsDisplayed()
        // 19:30 UTC is 2:30 p. m. in Lima, the backup's own zone.
        rule.onNodeWithText("Respaldo del 22 oct 2026, 2:30", substring = true).assertExists()
        rule.onNodeWithText("Tiene 2 categorías, 3 gastos y 1 ingreso.").assertExists()
        rule.onNodeWithText("queda guardado aparte", substring = true).assertExists()
        rule.onNodeWithTag(STARTUP_RESTORE_CONFIRM_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(STARTUP_RESTORE_CANCEL_TAG).performScrollTo().performClick()

        assertEquals(
            listOf(StartupFailureAction.ConfirmRestore, StartupFailureAction.CancelRestore),
            actions
        )
    }

    @Test
    fun whileRestoringNothingElseCanBePressed() {
        show(StartupFailureState(restore = StartupRestoreStep.Restoring(backup)))

        rule.onNodeWithText("Restaurando…").performScrollTo().assertIsDisplayed()
        val choices = listOf(
            STARTUP_RESTORE_CONFIRM_TAG,
            STARTUP_RESTORE_CANCEL_TAG,
            STARTUP_RETRY_TAG,
            STARTUP_RESTORE_TAG,
            STARTUP_START_OVER_TAG
        )
        for (tag in choices) {
            rule.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
        }
    }

    @Test
    fun aFileThatCannotBeRestoredSaysWhyAndOffersAnother() {
        show(
            StartupFailureState(
                restore = StartupRestoreStep.Rejected(RestoreRejection.NewerVersion)
            )
        )

        rule.onNodeWithTag(STARTUP_RESTORE_REJECTED_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Lo hizo una versión más nueva de Sello", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithText("Elegir otro archivo").performScrollTo().performClick()

        assertEquals(listOf<StartupFailureAction>(StartupFailureAction.RestoreBackup), actions)
    }

    @Test
    fun aFinanceTrackerFileSaysWhereItIsImported() {
        show(
            StartupFailureState(
                restore = StartupRestoreStep.Rejected(RestoreRejection.FinanceTrackerFile)
            )
        )

        rule.onNodeWithTag(STARTUP_RESTORE_REJECTED_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "Es un respaldo de FinanceTracker. Se importa desde Respaldo, después de empezar " +
                "de cero.",
            substring = true
        ).assertIsDisplayed()
        rule.onNodeWithText("No es un respaldo", substring = true).assertDoesNotExist()
    }

    @Test
    fun anInterruptedRestoreSaysSoAndContinuingIsTheWayOn() {
        show(StartupFailureState(restore = StartupRestoreStep.Interrupted))

        rule.onNodeWithTag(STARTUP_RESTORE_INTERRUPTED_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("La restauración no terminó").assertIsDisplayed()
        rule.onNodeWithText("Abrir Sello de nuevo").performScrollTo().performClick()

        assertEquals(listOf<StartupFailureAction>(StartupFailureAction.Leave), actions)
    }

    @Test
    fun aRestoredBackupIsRestauradoAndGoingOnLeaves() {
        val done = RestoreOutcome.Restored(
            BackupSummary(2, 3, 1, 0, 0),
            PendingPreferences(null, null)
        )
        show(StartupFailureState(restore = StartupRestoreStep.Restored(done)))

        rule.onNodeWithTag(RESTORE_DONE_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Se restauró el respaldo: 2 categorías, 3 gastos y 1 ingreso.")
            .assertExists()
        rule.onNodeWithTag(RESTORE_LEAVE_TAG).performScrollTo().performClick()

        assertEquals(listOf<StartupFailureAction>(StartupFailureAction.Leave), actions)
    }

    @Test
    fun aLargeRestoredBackupGroupsItsThousands() {
        val done = RestoreOutcome.Restored(
            BackupSummary(20, 10_000, 2_000, 0, 0),
            PendingPreferences(null, null)
        )
        show(StartupFailureState(restore = StartupRestoreStep.Restored(done)))

        rule.onNodeWithText(
            "Se restauró el respaldo: 20 categorías, 10.000 gastos y 2.000 ingresos."
        ).assertExists()
    }

    @Test
    fun aRestoredBackupWithGoalsCountsThem() {
        val done = RestoreOutcome.Restored(
            BackupSummary(2, 3, 1, 1, 0),
            PendingPreferences(null, null)
        )
        show(StartupFailureState(restore = StartupRestoreStep.Restored(done)))

        rule.onNodeWithText("Se restauró el respaldo: 2 categorías, 3 gastos, 1 ingreso y 1 meta.")
            .assertExists()
    }

    @Test
    fun preferencesNotAppliedAreNamedAndOnlyTheyAreRetried() {
        val pending = RestoreOutcome.Restored(
            BackupSummary(2, 3, 1, 0, 0),
            PendingPreferences(
                null,
                CategoryOrderingPreference(
                    CategoryOrder.Alphabetical,
                    CategoryOrderDirection.Ascending
                )
            )
        )
        show(StartupFailureState(restore = StartupRestoreStep.Restored(pending)))

        rule.onNodeWithText("Historial financiero restaurado").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Orden de categorías: Alfabético, de menor a mayor").assertExists()
        rule.onNodeWithText("Apariencia", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Aplicar orden").performScrollTo().performClick()

        assertEquals(listOf<StartupFailureAction>(StartupFailureAction.ApplyPreferences), actions)
    }

    /** The button tagged [tag] keeps [label] and tells a screen reader it is under way. */
    private fun assertWorking(tag: String, label: String) {
        rule.onNodeWithTag(tag).performScrollTo().assert(hasText(label))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "En curso"))
        assertTrue(spinning(tag))
    }

    /** Whether the button tagged [tag] shows a spinner. */
    private fun spinning(tag: String) = rule.onAllNodes(
        hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate) and
            hasAnyAncestor(hasTestTag(tag)),
        useUnmergedTree = true
    ).fetchSemanticsNodes().isNotEmpty()

    /** Each tagged control is shown disabled, and pressing it reports nothing. */
    private fun assertNothingPressable(vararg tags: String) {
        for (tag in tags) {
            rule.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled().performClick()
        }
        assertEquals(emptyList<StartupFailureAction>(), actions)
    }

    @Test
    fun whileTryingAgainItsButtonShowsItIsWorkingAndNoChoiceCanBePressed() {
        show(StartupFailureState(working = StartupWork.Retrying, stillFailing = true))

        assertWorking(STARTUP_RETRY_TAG, "Intentar de nuevo")
        assertFalse(spinning(STARTUP_RESTORE_TAG))
        assertFalse(spinning(STARTUP_START_OVER_TAG))
        assertNothingPressable(STARTUP_RETRY_TAG, STARTUP_RESTORE_TAG, STARTUP_START_OVER_TAG)
    }

    @Test
    fun whileTryingAgainAnOpenWarningWaitsAndKeepsItsWord() {
        show(
            StartupFailureState(
                working = StartupWork.Retrying,
                confirming = true,
                typed = "BORRAR"
            )
        )

        word.performScrollTo().assertIsNotEnabled().assert(hasText("BORRAR"))
        assertFalse(spinning(STARTUP_CONFIRM_TAG))
        assertWorking(STARTUP_RETRY_TAG, "Intentar de nuevo")
        assertNothingPressable(
            STARTUP_CONFIRM_TAG,
            STARTUP_CANCEL_TAG,
            STARTUP_RETRY_TAG,
            STARTUP_RESTORE_TAG,
            STARTUP_START_OVER_TAG
        )
    }

    @Test
    fun whileTryingAgainAPreviewWaits() {
        show(
            StartupFailureState(
                working = StartupWork.Retrying,
                restore = StartupRestoreStep.Previewing(backup)
            )
        )

        assertNothingPressable(STARTUP_RESTORE_CONFIRM_TAG, STARTUP_RESTORE_CANCEL_TAG)
        assertFalse(spinning(STARTUP_RESTORE_CONFIRM_TAG))
    }

    @Test
    fun whileTryingAgainAnotherFileCannotBeChosen() {
        show(
            StartupFailureState(
                working = StartupWork.Retrying,
                restore = StartupRestoreStep.Rejected(RestoreRejection.NotABackup)
            )
        )

        rule.onNodeWithText("Elegir otro archivo").performScrollTo().assertIsNotEnabled()
            .performClick()
        assertEquals(emptyList<StartupFailureAction>(), actions)
    }

    @Test
    fun whileSettingAsideTheConfirmationShowsItIsWorkingAndNothingElseCanBePressed() {
        show(
            StartupFailureState(
                working = StartupWork.SettingAside,
                confirming = true,
                typed = "BORRAR"
            )
        )

        assertWorking(STARTUP_CONFIRM_TAG, "Empezar de cero")
        assertFalse(spinning(STARTUP_RETRY_TAG))
        assertFalse(spinning(STARTUP_CANCEL_TAG))
        word.performScrollTo().assertIsNotEnabled()
        assertNothingPressable(
            STARTUP_CONFIRM_TAG,
            STARTUP_CANCEL_TAG,
            STARTUP_RETRY_TAG,
            STARTUP_RESTORE_TAG,
            STARTUP_START_OVER_TAG
        )
    }

    /** A recovery whose open and move wait for [disk], as a slow disk makes them. */
    private class SlowDisk : StartupRecovery {
        val disk = CompletableDeferred<Unit>()
        var retries = 0
        var setAsides = 0

        override suspend fun retry(): Boolean {
            retries++
            disk.await()
            return false
        }

        override suspend fun setAside(): Boolean {
            setAsides++
            disk.await()
            return false
        }

        override suspend fun check(source: DocumentSource) =
            Outcome.Failure(RestoreRejection.NotABackup)

        override fun forget() = Unit

        override suspend fun restore(): StartupRestore = StartupRestore.NotSetAside

        override suspend fun applyPending(): RestoreOutcome.Restored? = null
    }

    private fun showWorking(recovery: StartupRecovery) {
        val phone = SystemColor { PhoneColor.None }
        val viewModel = StartupFailureViewModel(SavedStateHandle(), recovery, {}, phone)
        rule.showSello(Look()) {
            val state by viewModel.state.collectAsState()
            StartupFailureScreen(state, viewModel::onAction)
        }
    }

    @Test
    fun aSecondPressWhileTryingAgainOpensNothingMore() {
        val recovery = SlowDisk()
        showWorking(recovery)

        rule.onNodeWithTag(STARTUP_RETRY_TAG).performClick()
        rule.onNodeWithTag(STARTUP_RETRY_TAG).assertIsNotEnabled().performClick()
        rule.onNodeWithTag(STARTUP_START_OVER_TAG).assertIsNotEnabled().performClick()
        assertEquals(1, recovery.retries)
        rule.onNodeWithTag(STARTUP_WARNING_TAG).assertDoesNotExist()

        recovery.disk.complete(Unit)
        rule.onNodeWithTag(STARTUP_STILL_FAILING_TAG).assertIsDisplayed()
        rule.onNodeWithTag(STARTUP_RETRY_TAG).assertIsEnabled()
        assertEquals(1, recovery.retries)
    }

    @Test
    fun aSecondPressWhileSettingAsideMovesNothingMore() {
        val recovery = SlowDisk()
        showWorking(recovery)

        rule.onNodeWithTag(STARTUP_START_OVER_TAG).performClick()
        word.performScrollTo().performTextInput("BORRAR")
        rule.onNodeWithTag(STARTUP_CONFIRM_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(STARTUP_CONFIRM_TAG).assertIsNotEnabled().performClick()
        rule.onNodeWithTag(STARTUP_RETRY_TAG).performScrollTo().assertIsNotEnabled().performClick()
        assertEquals(1, recovery.setAsides)
        assertEquals(0, recovery.retries)

        // Nothing was moved: the warning closes and says so, and it can be asked again.
        recovery.disk.complete(Unit)
        rule.onNodeWithTag(STARTUP_NOT_SET_ASIDE_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(STARTUP_START_OVER_TAG).performScrollTo().assertIsEnabled()
        assertEquals(1, recovery.setAsides)
    }
}
