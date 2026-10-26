package com.software.sello.feature.resumen

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestData
import com.software.sello.awaitDescription
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.feature.category.EDITOR_SUBMIT_TAG
import com.software.sello.feature.expense.ENTRY_CATEGORIES_TAG
import com.software.sello.feature.expense.ENTRY_KEYPAD_TAG
import com.software.sello.feature.expense.ENTRY_SUBMIT_TAG
import com.software.sello.hideKeyboard
import com.software.sello.press
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResumenJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun reset() = TestData.reset()

    @After
    fun close() {
        scenario?.hideKeyboard()
        scenario?.close()
        TestData.reset()
    }

    @Test
    fun realEntrySummarySourceLinksAndRecreationReconcileWithStorage() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitText("Crear categoría")
        rule.onNodeWithText("Crear categoría").performClick()
        rule.awaitDescription("Nombre")
        rule.onNodeWithContentDescription("Nombre").performTextInput("Comida")
        rule.onNodeWithContentDescription(
            "Límite en pesos"
        ).performScrollTo().performTextInput("100000")
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).press()
        rule.awaitText("Resumen")
        rule.onNodeWithContentDescription("Anotar un gasto").performClick()
        rule.awaitTag(ENTRY_KEYPAD_TAG)
        rule.onNode(hasText("Comida") and hasAnyAncestor(hasTestTag(ENTRY_CATEGORIES_TAG)))
            .performClick()
        listOf("3", "0", "000").forEach { key ->
            rule.onNode(hasText(key) and hasAnyAncestor(hasTestTag(ENTRY_KEYPAD_TAG)))
                .performClick()
        }
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).press()
        rule.awaitText("Gasto anotado: 30.000 en Comida")
        rule.awaitText("Resumen")
        rule.onNodeWithText("Resumen").performClick()
        rule.awaitTag("resumen.flow")
        rule.onNodeWithTag("resumen.openIncome").performScrollTo().performClick()
        rule.awaitTag("income.history")
        rule.onNodeWithContentDescription("Anotar un ingreso").performClick()
        rule.awaitTag("income.keypad")
        listOf("1", "5", "0", "000").forEach { key ->
            rule.onNode(hasText(key) and hasAnyAncestor(hasTestTag("income.keypad")))
                .performClick()
        }
        rule.onNodeWithTag("income.submit").press()
        // Back where income entry was opened, saying what was saved.
        rule.awaitTag("income.history")
        rule.awaitText("Ingreso anotado: 150.000")
        Espresso.pressBack()
        rule.awaitTag("resumen.flow")
        rule.onNodeWithContentDescription("120.000 pesos").performScrollTo().assertIsDisplayed()
        // Where it went: Comida, inside the income slip, and the stub counts the one income.
        val comida = TestData.rows("SELECT id FROM category WHERE name = 'Comida'").single()
        rule.onNode(
            hasTestTag("resumen.category.$comida") and hasAnyAncestor(hasTestTag("resumen.flow"))
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Ingresos, 1 en", substring = true).performScrollTo()
            .assertIsDisplayed()
        assertEquals(listOf("150000"), TestData.rows("SELECT SUM(amount_minor) FROM income"))
        assertEquals(listOf("30000"), TestData.rows("SELECT SUM(amount_minor) FROM expense"))
        // The month leads: nothing recorded before it, so what it spent stands alone.
        rule.onNodeWithText("¿Cómo va este mes?").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("resumen.history.chart").performScrollTo()
        rule.onNode(
            hasContentDescription("30.000 pesos", substring = true) and
                hasAnyAncestor(hasTestTag("resumen.history.chart"))
        ).performSemanticsAction(SemanticsActions.OnClick)
        rule.awaitTag("resumen.history.selected")
        rule.onNodeWithTag("resumen.history.selected")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("30.000 pesos")
        val id = TestData.rows("SELECT id FROM category WHERE name = 'Comida'").single()
        rule.onNodeWithTag("resumen.category.$id").performScrollTo().performClick()
        rule.awaitText("Comida")
        scenario?.recreate()
        rule.awaitText("Comida")
        Espresso.pressBack()
        rule.awaitTag("resumen.flow")
        rule.onNodeWithContentDescription("120.000 pesos").performScrollTo().assertIsDisplayed()
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitText("Resumen")
        rule.onNodeWithText("Resumen").performClick()
        rule.awaitTag("resumen.flow")
        rule.onNodeWithContentDescription("120.000 pesos").performScrollTo().assertIsDisplayed()
        assertEquals(1L, TestData.count("income"))
        assertEquals(1L, TestData.count("expense"))
    }
}
