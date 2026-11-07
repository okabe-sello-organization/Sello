package com.software.sello.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.showSello
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Gastos hormiga's form on the JVM (ADR 0022), given state by hand. */
@RunWith(AndroidJUnit4::class)
class SmallExpenseLineScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<SmallExpenseLineAction>()

    private val typed = SmallExpenseLineState(
        loading = false,
        loadFailed = false,
        amount = "15.000",
        problem = null,
        current = "20.000"
    )

    private fun show(state: SmallExpenseLineState) =
        rule.showSello { SmallExpenseLineScreen(state) { actions += it } }

    @Test
    fun theFormSaysTheLineInUseAndSavesWithGuardar() {
        show(typed)

        rule.onNodeWithText("Gastos hormiga").assertIsDisplayed()
        rule.onNodeWithText("Hasta, en pesos").assertIsDisplayed()
        rule.onNodeWithText(
            "Hoy es 20.000. El nuevo vale para todos los meses, también los pasados. " +
                "Entre 1.000 y 100.000."
        ).assertIsDisplayed()
        rule.onNodeWithTag(SMALL_LINE_SUBMIT_TAG).assertIsEnabled().performClick()

        assertEquals(listOf<SmallExpenseLineAction>(SmallExpenseLineAction.Submit), actions)
    }

    @Test
    fun aRefusedLineSaysWhyAndGuardarIsOff() {
        show(typed.copy(amount = "999", problem = LineProblem.BelowMinimum))
        rule.onNodeWithText("La línea es de 1.000 en adelante").assertIsDisplayed()
        rule.onNodeWithTag(SMALL_LINE_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun aLineOverTheTopSaysWhyAndGuardarIsOff() {
        show(typed.copy(amount = "150.000", problem = LineProblem.AboveMaximum))
        rule.onNodeWithText("La línea llega hasta 100.000").assertIsDisplayed()
        rule.onNodeWithTag(SMALL_LINE_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun theSaveInFlightSaysGuardandoAndAFailedOneSaysNothingChanged() {
        show(typed.copy(notSaved = true))
        rule.onNodeWithText("No se guardó. Tu línea sigue igual; puedes intentarlo de nuevo.")
            .assertIsDisplayed()
    }

    @Test
    fun thePadSendsDigitsTheThousandsKeyAndBackspace() {
        show(typed)

        rule.onNodeWithText("7").performClick()
        rule.onNodeWithText("000").performClick()

        assertTrue(actions.contains(SmallExpenseLineAction.Digit(7)))
        assertTrue(actions.contains(SmallExpenseLineAction.TripleZero))
    }

    @Test
    fun aLineThatCannotBeReadOffersReintentar() {
        show(typed.copy(loadFailed = true, current = null))

        rule.onNodeWithText("No pudimos leer tu línea").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<SmallExpenseLineAction>(SmallExpenseLineAction.Retry), actions)
    }
}
