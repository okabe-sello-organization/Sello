package com.software.sello.feature.investments

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.designsystem.component.SCAFFOLD_DOCK_TAG
import com.software.sello.hideKeyboard
import com.software.sello.press
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inversiones in the installed app over its real database (investment examples): a CDT
 * is added from the empty tab, valued, and the tab answers with its value and return
 * (IN03); a dollar investment asks for the rate first and saves both together (IN33).
 */
@RunWith(AndroidJUnit4::class)
class InvestmentJourneyTest {
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

    private fun openInversiones() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitText("Inversiones")
        rule.onNodeWithText("Inversiones").performClick()
        rule.awaitTag(INVERSIONES_LIST_TAG)
    }

    private fun name(text: String) {
        rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(INVESTMENT_EDITOR_NAME_TAG)))
            .performTextInput(text)
        scenario?.hideKeyboard()
    }

    /** Gives the always-present pad to [field]'s slip and types [keys] on it. */
    private fun editorKeys(field: String, vararg keys: String) {
        rule.onNodeWithTag(field).performScrollTo().press()
        keys.forEach { key ->
            val node = if (key == ",") hasContentDescription("Coma decimal") else hasText(key)
            rule.onNode(node and hasAnyAncestor(hasTestTag(INVESTMENT_EDITOR_PAD_TAG))).press()
        }
    }

    /** Chooses an icon from the circle's sheet. */
    private fun icon(label: String) {
        rule.onNodeWithTag(INVESTMENT_EDITOR_ICON_TAG).performScrollTo().press()
        rule.awaitTag(INVESTMENT_EDITOR_ICONS_TAG)
        rule.onNodeWithContentDescription(label).performClick()
    }

    private fun movementKeys(vararg keys: String) = keys.forEach { key ->
        rule.onNode(hasText(key) and hasAnyAncestor(hasTestTag(INVESTMENT_MOVEMENT_KEYPAD_TAG)))
            .press()
    }

    @Test
    fun in03ACdtAddedFromTheEmptyTabIsValuedAndTheTabAnswersWithItsReturn() {
        openInversiones()
        rule.onNodeWithText("Anota lo que tienes invertido", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Agregar inversión")).performClick()
        rule.awaitTag(INVESTMENT_EDITOR_TAG)

        name("CDT Banco")
        icon("Banco")
        editorKeys(INVESTMENT_EDITOR_FIRST_TAG, "1", "0", "000", "000")
        rule.onNodeWithTag(INVESTMENT_EDITOR_SUBMIT_TAG).press()

        // The new investment opens with its first aporte and the same value.
        rule.awaitTag(INVESTMENT_DETAIL_TAG)
        assertEquals(
            listOf("CDT Banco|account_balance|COP"),
            TestData.rows("SELECT name, icon_key, currency FROM investment")
        )
        assertEquals(
            listOf("contribution|10000000", "valuation|10000000"),
            TestData.rows("SELECT kind, amount_minor FROM investment_movement ORDER BY sequence")
        )

        rule.onNodeWithTag(INVESTMENT_RECORD_VALUE_TAG).performScrollTo().press()
        rule.awaitTag(INVESTMENT_MOVEMENT_KEYPAD_TAG)
        movementKeys("1", "0", "7", "4", "6", "000")
        rule.onNodeWithTag(INVESTMENT_MOVEMENT_SUBMIT_TAG).press()

        // IN03: 10.746.000 on 10.000.000 is +7,46 %, shown +7,5 %.
        rule.awaitTag(INVESTMENT_DETAIL_TAG)
        rule.awaitText("+7,5 %")
        assertEquals(
            listOf("contribution|10000000", "valuation|10000000", "valuation|10746000"),
            TestData.rows("SELECT kind, amount_minor FROM investment_movement ORDER BY sequence")
        )
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.awaitTag("inversiones.total")
        rule.onNodeWithContentDescription(
            "Tus inversiones valen, 10.746.000 pesos, Gana",
            useUnmergedTree = true
        ).assertExists()
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Anotar movimiento"))
    }

    @Test
    fun in33ADollarInvestmentAsksForTheRateFirstAndSavesBothTogether() {
        openInversiones()
        // With no investments yet, the dock adds the first.
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Agregar inversión")).performClick()
        rule.awaitTag(INVESTMENT_EDITOR_TAG)

        name("Acciones EE. UU.")
        icon("Acciones")
        rule.onNodeWithText("Dólares").performScrollTo().performClick()
        rule.awaitTag(INVESTMENT_EDITOR_RATE_SLIP_TAG)
        editorKeys(INVESTMENT_EDITOR_FIRST_TAG, "1", "0", "0", "0", ",", "0", "0")
        rule.onNodeWithTag(INVESTMENT_EDITOR_SUBMIT_TAG).assert(
            androidx.compose.ui.test.isNotEnabled()
        )
        editorKeys(INVESTMENT_EDITOR_RATE_SLIP_TAG, "4", "1", "5", "0", ",", "2", "5")
        rule.onNodeWithTag(INVESTMENT_EDITOR_SUBMIT_TAG).press()

        rule.awaitTag(INVESTMENT_DETAIL_TAG)
        assertEquals(
            listOf("Acciones EE. UU.|USD"),
            TestData.rows("SELECT name, currency FROM investment")
        )
        assertEquals(
            listOf("contribution|100000|USD", "valuation|100000|USD"),
            TestData.rows(
                "SELECT kind, amount_minor, currency FROM investment_movement ORDER BY sequence"
            )
        )
        assertEquals(listOf("415025"), TestData.rows("SELECT rate_hundredths FROM usd_rate"))
    }
}
