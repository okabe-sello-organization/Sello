package com.software.sello.feature.income

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestData
import com.software.sello.await
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.hideKeyboard
import com.software.sello.press
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomeJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun emptyHistory() = TestData.reset()

    @After
    fun close() {
        scenario?.hideKeyboard()
        scenario?.close()
        TestData.reset()
    }

    private fun openForm() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitText("Resumen")
        rule.onNodeWithText("Resumen").performClick()
        rule.awaitTag("resumen.income")
        rule.onNodeWithTag("resumen.openIncome").performScrollTo().performClick()
        rule.awaitTag("income.history")
        rule.onNodeWithContentDescription("Anotar un ingreso").performClick()
        rule.awaitTag("income.keypad")
    }

    private fun keys(vararg labels: String) {
        labels.forEach { label ->
            rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag("income.keypad")))
                .performClick()
        }
    }

    @Test
    fun createsIncomeWithoutCategoriesThenEditsDeletesAndUndoesTheSameRow() {
        openForm()
        keys("1", "5", "0", "000")
        rule.onNodeWithTag("income.source.freelance").performScrollTo().performClick()
        scenario?.recreate()
        rule.awaitTag("income.keypad")
        rule.onNodeWithTag("income.submit").press()
        // No receipt: back to the income history, saying what was stored.
        rule.awaitTag("income.history")
        rule.awaitText("Ingreso anotado: 150.000")
        rule.onNodeWithText("Listo").assertDoesNotExist()
        // The v2 history: what came in, and the income itself in the list.
        rule.awaitTag("income.hero")
        rule.onNodeWithTag("income.list").assertExists()
        assertEquals(
            listOf("150000|COP|freelance|null|1"),
            TestData.rows(
                "SELECT amount_minor, currency, source_key, source_name, version FROM income"
            )
        )
        assertEquals(
            TestData.rows("SELECT id FROM income"),
            TestData.rows(
                "SELECT subject_id FROM operation_receipt WHERE kind = 'income.create'"
            )
        )
        assertEquals(0L, TestData.count("expense"))
        assertEquals(0L, TestData.count("month_limit"))
        val id = TestData.rows("SELECT id FROM income").single()
        rule.awaitTag("income.line.$id")
        rule.onNodeWithTag("income.line.$id").performScrollTo().performClick()
        rule.awaitText("Editar ingreso")
        rule.onNodeWithTag("income.source.salary").performScrollTo().performClick()
        rule.onNodeWithTag("income.submit").press()
        rule.awaitText("Ingreso actualizado")
        assertEquals(
            listOf("150000|salary|2"),
            TestData.rows(
                "SELECT amount_minor, source_key, version FROM income"
            )
        )
        // Back on the history, the list is read again: the row from before the edit can
        // still be shown and then leave while the list reloads, so wait for the edited row.
        rule.await(hasTestTag("income.line.$id") and hasAnyDescendant(hasText("Salario")))
        rule.onNodeWithTag("income.line.$id").performScrollTo().performClick()
        rule.awaitText("Editar ingreso")
        rule.onNodeWithTag("income.delete").press()
        rule.awaitText("Deshacer")
        assertEquals(0L, TestData.count("income"))
        rule.onNodeWithText("Deshacer").press()
        rule.awaitText("Ingreso restaurado")
        assertEquals(
            listOf("150000|salary|2"),
            TestData.rows(
                "SELECT amount_minor, source_key, version FROM income"
            )
        )
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitText("Resumen")
        rule.onNodeWithText("Resumen").performClick()
        rule.awaitTag("resumen.income")
        assertEquals(1L, TestData.count("income"))
    }

    @Test
    fun aDraftSurvivesSwitchingKindsAndActivityRecreationWithoutSaving() {
        openForm()
        keys("5", "000")
        rule.onNodeWithText("Gasto").press()
        rule.awaitTag("entry.kind")
        rule.onNodeWithText("Ingreso").press()
        scenario?.recreate()
        rule.awaitTag("income.amount")
        rule.onNodeWithTag("income.submit").press()
        rule.awaitText("Ingreso anotado: 5.000")
        assertEquals(listOf("5000"), TestData.rows("SELECT amount_minor FROM income"))
        assertEquals(0L, TestData.count("expense"))
    }
}
