package com.software.sello.feature.reset

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.ResetPreview
import com.software.sello.showSello
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen alone, given state by hand: what each step shows and what each control reports.
 * On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
class ResetScreenTest {
    @get:Rule
    val rule = createComposeRule()

    /** The field is found by its label, which is also what a screen reader calls it. */
    private val word get() = rule.onNodeWithContentDescription("Escribe BORRAR para confirmar")
    private val actions = mutableListOf<ResetAction>()
    private var closes = 0
    private val preview = ResetPreview(BackupSummary(3, 42, 1, 0, 0), generation = 1, revision = 9)
    private val operation =
        (OperationId.of("00000000-0000-4000-8000-000000007000") as Outcome.Success).value

    private fun show(state: ResetState) {
        rule.showSello(Look()) {
            ResetScreen(state, { actions += it }, PaddingValues(), { closes++ })
        }
    }

    @Test
    fun theFirstConfirmationCountsWhatGoesAndSaysBackupFilesStay() {
        show(ResetState(ResetStep.Ready(preview)))

        rule.onNodeWithText("¿Borrar todos tus datos?").assertIsDisplayed()
        rule.onNodeWithText(
            "Se borran 3 categorías, 42 gastos y 1 ingreso, con sus límites y notas.",
            substring = true
        ).assertIsDisplayed()
        rule.onNodeWithText(
            "Los respaldos que guardaste fuera de Sello no se borran",
            substring = true
        )
            .assertIsDisplayed()
        rule.onNodeWithTag(RESET_CONTINUE_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(RESET_CLOSE_TAG).performScrollTo().performClick()

        assertEquals(listOf<ResetAction>(ResetAction.Continue), actions)
        assertEquals(1, closes)
    }

    @Test
    fun largeCountsGroupTheirThousands() {
        show(
            ResetState(
                ResetStep.Ready(preview.copy(current = BackupSummary(20, 10_000, 2_000, 0, 0)))
            )
        )

        rule.onNodeWithText(
            "Se borran 20 categorías, 10.000 gastos y 2.000 ingresos, con sus límites y notas.",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun goalsAreCountedOnlyWhenThereAreAny() {
        show(ResetState(ResetStep.Ready(preview.copy(current = BackupSummary(2, 10, 1, 3, 0)))))

        rule.onNodeWithText(
            "Se borran 2 categorías, 10 gastos, 1 ingreso y 3 metas, con sus límites y notas.",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun oneRecurringPaymentOrIncomeIsCountedWhenThereIsOne() {
        show(ResetState(ResetStep.Ready(preview.copy(current = BackupSummary(2, 10, 1, 0, 1)))))

        rule.onNodeWithText(
            "Se borran 2 categorías, 10 gastos, 1 ingreso y 1 pago o ingreso fijo, con sus límites y notas.",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun recurringPaymentsAndIncomesComeAfterTheGoals() {
        show(ResetState(ResetStep.Ready(preview.copy(current = BackupSummary(2, 10, 1, 3, 2)))))

        rule.onNodeWithText(
            "Se borran 2 categorías, 10 gastos, 1 ingreso, 3 metas y 2 pagos e ingresos fijos, con sus límites y notas.",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun investmentsAreCountedLastAndOnlyWhenThereAreAny() {
        val current = BackupSummary(2, 10, 1, 3, 2, investments = 1)
        show(ResetState(ResetStep.Ready(preview.copy(current = current))))

        rule.onNodeWithText(
            "Se borran 2 categorías, 10 gastos, 1 ingreso, 3 metas, 2 pagos e ingresos fijos y 1 inversión, con sus límites y notas.",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun carryChainsAreCountedAfterInvestmentsAndOnlyWhenThereAreAny() {
        val current = BackupSummary(2, 10, 1, 3, 2, investments = 1, carryChains = 2)
        show(ResetState(ResetStep.Ready(preview.copy(current = current))))

        rule.onNodeWithText(
            "Se borran 2 categorías, 10 gastos, 1 ingreso, 3 metas, 2 pagos e ingresos fijos, " +
                "1 inversión y 2 arrastres entre meses, con sus límites y notas.",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun newCountsSayTheDataChanged() {
        show(ResetState(ResetStep.Ready(preview, refreshed = true)))

        rule.onNodeWithText("Tus datos cambiaron mientras confirmabas", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun theWordIsReportedExactlyAndErasesNothingUntilItIsRight() {
        show(ResetState(ResetStep.Ready(preview), confirming = true))

        rule.onNodeWithText("Escribe BORRAR").assertIsDisplayed()
        rule.onNodeWithTag(RESET_CONFIRM_TAG).performScrollTo().assertIsNotEnabled().performClick()
        word.performScrollTo().performTextInput("borrar")
        rule.onNodeWithTag(RESET_CANCEL_TAG).performScrollTo().performClick()

        assertEquals(listOf(ResetAction.Typed("borrar"), ResetAction.Cancel), actions)
    }

    @Test
    fun withTheWordTheOnlyEraseButtonWorks() {
        show(ResetState(ResetStep.Ready(preview), confirming = true, typed = "BORRAR"))

        rule.onNodeWithTag(RESET_CONFIRM_TAG).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf<ResetAction>(ResetAction.Confirm), actions)
    }

    @Test
    fun whileErasingNothingCanBePressed() {
        show(ResetState(ResetStep.Erasing(preview, operation)))

        rule.onNodeWithText("Borrando…").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(RESET_CONFIRM_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(RESET_CANCEL_TAG).assertIsNotEnabled()
    }

    @Test
    fun aFailureSaysNothingChangedAndAnUnknownOutcomeIsChecked() {
        show(ResetState(ResetStep.Failed))
        rule.onNodeWithText("Tus datos no cambiaron.").assertIsDisplayed()
        rule.onNodeWithText("Volver").performScrollTo().performClick()

        assertEquals(listOf<ResetAction>(ResetAction.Retry), actions)
    }

    @Test
    fun anUnknownOutcomeOffersToCheck() {
        show(ResetState(ResetStep.Unknown(preview, operation)))

        rule.onNodeWithText("No sabemos si se borró").assertIsDisplayed()
        rule.onNodeWithText("Comprobar").performScrollTo().performClick()

        assertEquals(listOf<ResetAction>(ResetAction.Check), actions)
    }

    @Test
    fun anEraseIsSaidAndGoingOnStartsAgain() {
        show(ResetState(ResetStep.Erased(preferencesPending = false)))

        rule.onNodeWithTag(RESET_DONE_TAG).assertIsDisplayed()
        rule.onNodeWithText("Tus respaldos fuera de Sello no se tocaron", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithTag(RESET_LEAVE_TAG).performScrollTo().performClick()

        assertEquals(listOf<ResetAction>(ResetAction.Leave), actions)
    }

    @Test
    fun preferencesNotResetAreNamedWithTheirFirstRunValuesAndOnlyTheyAreRetried() {
        show(ResetState(ResetStep.Erased(preferencesPending = true)))

        rule.onNodeWithText("Tus datos se borraron").assertIsDisplayed()
        rule.onNodeWithText("Apariencia: Sistema, Cobalto").assertIsDisplayed()
        rule.onNodeWithText("Orden de categorías: Más usadas, de mayor a menor").assertIsDisplayed()
        rule.onNodeWithText("No se borrará nada más", substring = true).assertExists()
        rule.onNodeWithTag(RESET_APPLY_TAG).performScrollTo().performClick()

        assertEquals(listOf<ResetAction>(ResetAction.ApplyPreferences), actions)
    }
}
