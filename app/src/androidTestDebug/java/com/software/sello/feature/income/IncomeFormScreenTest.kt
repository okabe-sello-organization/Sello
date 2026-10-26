package com.software.sello.feature.income

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.recurrence.REPEAT_ROW_TAG
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IncomeFormScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aRecordedSaveDrawsNoReceipt() {
        val today = LocalDate.of(2026, 10, 13)
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                IncomeFormScreen(
                    IncomeFormState(
                        loading = false,
                        amount = MoneyTextValue(MoneySign.None, "", "5.000", "5.000 pesos"),
                        date = today,
                        today = today,
                        missing = null,
                        busy = true,
                        recorded = "synthetic"
                    ),
                    {}
                )
            }
        }

        rule.onNodeWithTag("income.receipt").assertDoesNotExist()
        rule.onNodeWithText("Listo").assertDoesNotExist()
        rule.onNodeWithText("Ingreso anotado", substring = true).assertDoesNotExist()
    }

    @Test
    fun largeFontKeepsExactAmountAndBusySaveCannotBeRepeated() {
        val today = LocalDate.of(2026, 10, 13)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                SelloTheme(reducedMotion = true) {
                    IncomeFormScreen(
                        IncomeFormState(
                            loading = false,
                            date = today,
                            today = today,
                            amount = MoneyTextValue(
                                MoneySign.None,
                                "",
                                "999.999.999.999",
                                "999.999.999.999 pesos"
                            ),
                            sourceKeys = listOf("salary", "other"),
                            sourceKey = "salary",
                            missing = null,
                            busy = true
                        ),
                        {}
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("999.999.999.999 pesos").assertIsDisplayed()
        rule.onNodeWithTag("income.submit").assertIsNotEnabled()
    }

    private val today = LocalDate.of(2026, 10, 13)

    private fun showForm(state: IncomeFormState, actions: MutableList<IncomeFormAction>) =
        rule.setContent {
            SelloTheme(reducedMotion = true) { IncomeFormScreen(state, { actions += it }) }
        }

    private val ready = IncomeFormState(
        loading = false,
        amount = MoneyTextValue(MoneySign.None, "", "5.000", "5.000 pesos"),
        sourceKeys = listOf("salary", "freelance", "other"),
        sourceKey = "salary",
        date = today,
        today = today,
        missing = null
    )

    @Test
    fun theFormIsGreenWithAPlusAndOffersSourcesAsWordsWithTheirOwnAction() {
        val actions = mutableListOf<IncomeFormAction>()
        showForm(ready, actions)

        rule.onNodeWithText("Ingreso en pesos").assertIsDisplayed()
        // The plus is drawn; the amount read aloud is the amount itself.
        rule.onNodeWithContentDescription("5.000 pesos").assertIsDisplayed()
        rule.onNodeWithTag("income.other").assertDoesNotExist()
        rule.onNodeWithTag("income.submit").assertTextContains("Anotar ingreso").performClick()

        assertEquals(listOf<IncomeFormAction>(IncomeFormAction.Submit), actions)
    }

    @Test
    fun anIncomeRecordedOnceOffersToRepeatItWithoutSayingItRepeats() {
        showForm(ready, mutableListOf())

        rule.onNodeWithTag(REPEAT_ROW_TAG).performScrollTo()
            .assertTextEquals("Repetir este ingreso")
            .assertContentDescriptionEquals("No se repite. Repetir este ingreso")
        rule.onNodeWithText("Se repite").assertDoesNotExist()
    }

    @Test
    fun otroHasANameRowThatOpensTheComposer() {
        val actions = mutableListOf<IncomeFormAction>()
        showForm(
            ready.copy(sourceKey = "other", otherName = "", missing = IncomeMissing.SourceName),
            actions
        )

        // Why the button is off is said by the button, whatever has scrolled.
        rule.onNodeWithText("Escribe el nombre de la fuente.").assertIsDisplayed()
        rule.onNodeWithTag("income.other").performScrollTo().performClick()

        assertEquals(
            listOf<IncomeFormAction>(IncomeFormAction.OpenComposer(IncomeComposer.SourceName)),
            actions
        )
    }

    @Test
    fun theComposerForOtrosNameCountsToTwentyFourAndKeepsLongerTextWithoutFinishing() {
        val actions = mutableListOf<IncomeFormAction>()
        val long = "a".repeat(25)
        showForm(
            ready.copy(
                sourceKey = "other",
                composer = IncomeComposer.SourceName,
                composerDraft = long,
                composerLength = 25,
                composerProblem = IncomeTextProblem.TooLong
            ),
            actions
        )

        rule.onNodeWithText(long).assertIsDisplayed()
        rule.onNodeWithText("25/24").assertIsDisplayed()
        rule.onNodeWithText("Máximo 24 caracteres. Tu texto sigue aquí.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Listo").assertIsNotEnabled().performClick()
        rule.onNodeWithText("Cancelar").performClick()

        assertEquals(listOf<IncomeFormAction>(IncomeFormAction.ComposerCancelled), actions)
    }

    @Test
    fun aThirteenthDigitSaysMaximoDoceDigitosInPlaceOfTheLabel() {
        showForm(ready.copy(atLimit = true, limitPulse = 1), mutableListOf())

        rule.onNodeWithText("Máximo 12 dígitos").assertIsDisplayed()
        rule.onNodeWithText("Ingreso en pesos").assertDoesNotExist()
    }
}
