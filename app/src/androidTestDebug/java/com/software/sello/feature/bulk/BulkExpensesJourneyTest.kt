package com.software.sello.feature.bulk

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestData
import com.software.sello.awaitDescription
import com.software.sello.awaitTag
import com.software.sello.domain.port.FinancialClock
import com.software.sello.feature.category.EDITOR_SUBMIT_TAG
import com.software.sello.feature.category.EDITOR_UNLIMITED_TAG
import com.software.sello.feature.expense.ENTRY_CATEGORIES_TAG
import com.software.sello.feature.expense.ENTRY_KEYPAD_TAG
import com.software.sello.feature.expense.ENTRY_SEVERAL_TAG
import com.software.sello.feature.recibo.RECIBO_CATEGORIES_TAG
import com.software.sello.feature.recibo.RECIBO_FIRST_RUN_TAG
import com.software.sello.hideKeyboard
import com.software.sello.navigation.CORRECTION_NOTICE_TAG
import com.software.sello.press
import com.software.sello.revealed
import com.software.sello.systemKeyboardRequested
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The installed app with its real database: Anotar, then Varios, three expenses on one
 * receipt, saved together and shown on Recibo, using only the screens a person uses.
 */
@RunWith(AndroidJUnit4::class)
class BulkExpensesJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val today get() = GlobalContext.get().get<FinancialClock>().today.value.date.toString()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun newInstallation() = TestData.reset()

    @After
    fun leaveNothingBehind() {
        scenario?.takeIf { it.state != Lifecycle.State.DESTROYED }?.hideKeyboard()
        scenario?.close()
        TestData.reset()
    }

    private fun press(pad: String, vararg keys: String) = keys.forEach { key ->
        rule.onNode(hasText(key) and hasAnyAncestor(hasTestTag(pad))).performClick()
    }

    @Test
    fun threeExpensesOnOneReceiptAreSavedTogetherAndReciboSaysSo() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitTag(RECIBO_FIRST_RUN_TAG)
        rule.onNodeWithText("Crear categoría").performClick()
        rule.awaitDescription("Nombre")
        rule.onNodeWithContentDescription("Nombre").performTextInput("Mercado")
        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).revealed().press()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).press()
        rule.awaitTag(RECIBO_CATEGORIES_TAG)
        // The editor is gone and Recibo has no field: nothing may still ask for the
        // keyboard, which would hide the dock (#292).
        rule.waitForIdle()
        assertFalse(systemKeyboardRequested())

        // Anotar, with one expense typed, then Varios: it becomes line one.
        rule.awaitDescription("Anotar un gasto")
        rule.onNodeWithContentDescription("Anotar un gasto").performClick()
        rule.awaitTag(ENTRY_KEYPAD_TAG)
        rule.onNode(hasText("Mercado") and hasAnyAncestor(hasTestTag(ENTRY_CATEGORIES_TAG)))
            .performClick()
        press(ENTRY_KEYPAD_TAG, "1", "8", "4", "0", "0")
        rule.onNodeWithTag(ENTRY_SEVERAL_TAG).press()

        // Line two waits in Mercado, highlighted.
        rule.awaitTag(BULK_CATEGORIES_TAG)
        rule.onNode(hasText("Mercado") and hasAnyAncestor(hasTestTag(BULK_CATEGORIES_TAG)))
            .assertIsSelected()
        press(BULK_KEYPAD_TAG, "7", "5", "1", "0", "0")
        rule.onNodeWithText("Otro renglón").performClick()
        press(BULK_KEYPAD_TAG, "3", "2", "5", "0", "0")
        rule.onNodeWithText("Anotar 3 gastos").assertExists()
        rule.onNodeWithTag(BULK_SUBMIT_TAG).press()

        // 18.400 + 75.100 + 32.500 = 126.000, said once on Recibo.
        rule.awaitTag(CORRECTION_NOTICE_TAG)
        rule.onNodeWithText("3 gastos anotados: 126.000").assertExists()
        rule.onNodeWithTag(BULK_KEYPAD_TAG).assertDoesNotExist()
        rule.awaitTag(RECIBO_CATEGORIES_TAG)
        assertEquals(
            listOf("18400|$today", "75100|$today", "32500|$today"),
            TestData.rows("SELECT amount_minor, effective_date FROM expense ORDER BY sequence")
        )
        // One receipt for the whole of it, none for single expenses.
        assertEquals(
            1,
            TestData.long(
                "SELECT COUNT(*) FROM operation_receipt WHERE kind = 'expense.create_batch'"
            )
        )
        assertEquals(
            0,
            TestData.long("SELECT COUNT(*) FROM operation_receipt WHERE kind = 'expense.create'")
        )
    }
}
