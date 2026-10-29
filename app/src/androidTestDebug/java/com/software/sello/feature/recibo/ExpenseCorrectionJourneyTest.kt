package com.software.sello.feature.recibo

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.await
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.designsystem.component.ACTION_LINE_MENU_TAG
import com.software.sello.feature.expense.ENTRY_KEYPAD_TAG
import com.software.sello.feature.expense.ENTRY_SUBMIT_TAG
import com.software.sello.hideKeyboard
import com.software.sello.navigation.CORRECTION_NOTICE_TAG
import com.software.sello.press
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Correcting, deleting and bringing back an expense in the installed app with its real
 * database. "Mercado" has 300.000 this month and one expense of 10.000 noted "Pan".
 * Each figure is worked out by hand from that, and compared with what is stored.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseCorrectionJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun mercadoWithOneExpense() {
        TestData.reset()
        TestCommands.showCurrentMonth()
        TestCommands.createCategory("Mercado", 300_000)
        TestCommands.createExpense("Mercado", TestCommands.today, 10_000, "Pan")
    }

    @After
    fun leaveNothingBehind() {
        scenario?.takeIf { it.state != Lifecycle.State.DESTROYED }?.hideKeyboard()
        scenario?.close()
        TestCommands.showCurrentMonth()
        TestData.reset()
    }

    private fun saying(text: String) = hasContentDescription(text, substring = true)

    private val listedExpense = SemanticsMatcher("is a listed expense") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("detail:expense:") == true
    }

    private fun listed() = rule.onAllNodes(listedExpense).fetchSemanticsNodes().size

    private fun stored() =
        TestData.rows("SELECT id, sequence, amount_minor, note, version FROM expense")

    private fun receipts() = TestData.rows("SELECT kind FROM operation_receipt ORDER BY revision")

    private fun inRow(row: String, spoken: String) = rule.onNode(
        hasContentDescription(spoken) and hasAnyAncestor(hasTestTag(row)),
        useUnmergedTree = true
    )

    /** Opens the app on Mercado's detail and returns the tag of its one expense. */
    private fun openMercado(): String {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        rule.await(saying("Restante del presupuesto: 290.000 pesos."))
        rule.onNodeWithTag(reciboCategoryTag(TestCommands.categoryId("Mercado")))
            .performScrollTo().performClick()
        rule.awaitTag(CATEGORY_DETAIL_TAG)
        val row = categoryExpenseTag(TestData.rows("SELECT id FROM expense").single())
        rule.awaitTag(row)
        return row
    }

    private fun key(label: String) =
        rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(ENTRY_KEYPAD_TAG)))
            .performClick()

    private fun deleteFromMenu(row: String) {
        rule.onNodeWithTag(row).performScrollTo().performTouchInput { longClick() }
        rule.onNode(hasText("Borrar") and hasAnyAncestor(hasTestTag(ACTION_LINE_MENU_TAG)))
            .performClick()
    }

    @Test
    fun anAmountIsCorrectedThenTheExpenseIsDeletedAndBroughtBackExactlyAsItWas() {
        val row = openMercado()
        val id = TestData.rows("SELECT id FROM expense").single()

        // 10.000 becomes 12.000: the same record, 2.000 less left.
        rule.onNodeWithTag(row).performScrollTo().performClick()
        rule.awaitText("Editar gasto")
        rule.awaitTag(ENTRY_KEYPAD_TAG)
        rule.await(saying("10.000 pesos"))
        repeat(5) {
            rule.onNode(
                hasContentDescription("Borrar") and hasAnyAncestor(hasTestTag(ENTRY_KEYPAD_TAG))
            ).performClick()
        }
        listOf("1", "2", "000").forEach(::key)
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).press()

        rule.awaitText("Gasto actualizado")
        rule.await(saying("Te quedan aquí 288.000 pesos."))
        rule.awaitTag(row)
        inRow(row, "12.000 pesos").assertExists()
        assertEquals(1, listed())
        assertEquals(listOf("$id|1|12000|Pan|2"), stored())

        // Deleted: nothing spent, all 300.000 left, and the offer to undo.
        deleteFromMenu(row)
        rule.awaitText("Gasto borrado")
        // Rebuilt at once, as a rotation does: the same offer is still there.
        scenario!!.recreate()
        rule.awaitTag(CORRECTION_NOTICE_TAG)
        rule.onNodeWithText("Deshacer").performClick()

        // Back, exactly as it was: same record, place, amount, note and version.
        rule.awaitText("Gasto restaurado")
        rule.awaitTag(row)
        rule.await(saying("Te quedan aquí 288.000 pesos."))
        inRow(row, "12.000 pesos").assertExists()
        assertEquals(listOf("$id|1|12000|Pan|2"), stored())
        assertEquals(0L, TestData.count("deleted_expense"))
        assertEquals(
            listOf(
                "category.create",
                "expense.create",
                "expense.edit",
                "expense.delete",
                "expense.restore"
            ),
            receipts()
        )
    }
}
