package com.software.sello.feature.recibo

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.await
import com.software.sello.awaitDescription
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.feature.category.EDITOR_SUBMIT_TAG
import com.software.sello.feature.category.typeLimit
import com.software.sello.feature.expense.ENTRY_CATEGORIES_TAG
import com.software.sello.feature.expense.ENTRY_KEYPAD_TAG
import com.software.sello.feature.expense.ENTRY_NOTE_TAG
import com.software.sello.feature.expense.ENTRY_SUBMIT_TAG
import com.software.sello.hideKeyboard
import com.software.sello.navigation.CORRECTION_NOTICE_TAG
import com.software.sello.navigation.DETAIL_BACK_TAG
import com.software.sello.navigation.MONTH_PICKER_TAG
import com.software.sello.navigation.MONTH_SWITCHER_TAG
import com.software.sello.press
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole first slice in the installed app with its real database: a category, an
 * expense, Recibo, the category's detail and its list, another month, and starting
 * again. Each figure on screen is compared with a value worked out by hand from what
 * was entered, and with what the database holds.
 */
@RunWith(AndroidJUnit4::class)
class WalkingSliceJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun newInstallation() {
        TestData.reset()
        TestCommands.showCurrentMonth()
    }

    @After
    fun leaveNothingBehind() {
        scenario?.takeIf { it.state != Lifecycle.State.DESTROYED }?.hideKeyboard()
        scenario?.close()
        TestCommands.showCurrentMonth()
        TestData.reset()
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
            .also { scenario = it }

    /** A new installation with "Mercado" limited to 300.000 a month, on Recibo. */
    private fun startWithMercado(): ActivityScenario<MainActivity> {
        val app = launch()
        rule.awaitTag(RECIBO_FIRST_RUN_TAG)
        rule.onNodeWithText("Crear categoría").performClick()
        rule.awaitDescription("Nombre")
        rule.onNodeWithContentDescription("Nombre").performTextInput("Mercado")
        rule.typeLimit("300.000")
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).press()
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        return app
    }

    private fun saying(text: String) = hasContentDescription(text, substring = true)

    /** Records an expense with the keypad from whichever screen shows the dock. */
    private fun record(vararg keys: String, note: String? = null) {
        // The dock is not drawn while the system keyboard is up, and a keyboard used
        // just before may still be closing.
        rule.awaitDescription("Anotar un gasto")
        rule.onNodeWithContentDescription("Anotar un gasto").performClick()
        rule.awaitTag(ENTRY_KEYPAD_TAG)
        // Nothing is chosen for the person: Mercado is picked.
        rule.onNode(hasText("Mercado") and hasAnyAncestor(hasTestTag(ENTRY_CATEGORIES_TAG)))
            .performClick()
            .assertIsSelected()
        keys.forEach { key ->
            rule.onNode(hasText(key) and hasAnyAncestor(hasTestTag(ENTRY_KEYPAD_TAG)))
                .performClick()
        }
        if (note != null) {
            rule.onNodeWithTag(ENTRY_NOTE_TAG).performScrollTo().performClick()
            rule.onNode(hasSetTextAction()).performTextInput(note)
            rule.onNodeWithContentDescription("Listo").performClick()
        }
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).press()
        // A confirmed save closes the form and says so over the screen it was opened from.
        rule.awaitTag(CORRECTION_NOTICE_TAG)
        rule.onNode(saying("Gasto anotado:")).assertExists()
    }

    private fun openMercado() {
        rule.onNodeWithTag(reciboCategoryTag(TestCommands.categoryId("Mercado")))
            .performScrollTo().performClick()
        rule.awaitTag(CATEGORY_DETAIL_TAG)
    }

    private fun inDetail(tag: String, spoken: String) = rule.onNode(
        hasContentDescription(spoken) and hasAnyAncestor(hasTestTag(tag)),
        useUnmergedTree = true
    )

    private val listedExpense = SemanticsMatcher("is a listed expense") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("detail:expense:") == true
    }

    private fun listed() = rule.onAllNodes(listedExpense).fetchSemanticsNodes().size

    private fun expenseIds() = TestData.rows("SELECT id FROM expense ORDER BY sequence")

    private val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    private fun pick(month: YearMonth) {
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).performClick()
        rule.awaitTag(MONTH_PICKER_TAG)
        if (month.year < YearMonth.from(TestCommands.today).year) {
            rule.onNodeWithContentDescription("Año anterior").performClick()
        }
        rule.onNodeWithContentDescription("${monthNames[month.monthValue - 1]} ${month.year}")
            .performScrollTo().performClick()
    }

    @Test
    fun aSavedExpenseReachesReciboItsCategoryAndItsListAndIsThereAfterStartingAgain() {
        val app = startWithMercado()
        rule.onNodeWithContentDescription(
            "Restante del presupuesto: 300.000 pesos. Al día."
        ).assertIsDisplayed()

        record("4", "8", "7", "0", "0", note = "Pan")

        // 300.000 less 48.700. The verdict depends on the day this runs, so it is not
        // asserted here; the sentence and both amounts are.
        rule.await(saying("Restante del presupuesto: 251.300 pesos."))
        rule.onNode(saying("Mercado. Te quedan 251.300 pesos de 300.000 pesos."))
            .performScrollTo().assertIsDisplayed()

        openMercado()
        rule.onNodeWithText("Mercado").assertIsDisplayed()
        rule.await(saying("Te quedan aquí 251.300 pesos."))
        inDetail(CATEGORY_DETAIL_SPENT_TAG, "48.700 pesos").assertExists()
        inDetail(CATEGORY_DETAIL_LIMIT_TAG, "300.000 pesos").assertExists()
        val first = categoryExpenseTag(expenseIds().single())
        rule.awaitTag(first)
        rule.onNode(hasText("Pan") and hasAnyAncestor(hasTestTag(first)), useUnmergedTree = true)
            .assertExists()
        inDetail(first, "48.700 pesos").assertExists()
        rule.onNodeWithText("Ver el gasto").assertExists()

        // From the category's own screen the entry starts in that category.
        record("1", "3", "0", "0")

        // Back on the same category: 48.700 + 1.300 = 50.000 spent, 250.000 left.
        rule.awaitTag(CATEGORY_DETAIL_HEADER_TAG)
        rule.await(saying("Te quedan aquí 250.000 pesos."))
        inDetail(CATEGORY_DETAIL_SPENT_TAG, "50.000 pesos").assertExists()
        rule.awaitText("Ver los 2")
        val second = categoryExpenseTag(expenseIds().last())
        rule.awaitTag(second)
        inDetail(second, "1.300 pesos").assertExists()
        inDetail(first, "48.700 pesos").assertExists()
        assertEquals(2, listed())
        // The one added last is listed first.
        assertTrue(
            rule.onNodeWithTag(second).getUnclippedBoundsInRoot().top <
                rule.onNodeWithTag(first).getUnclippedBoundsInRoot().top
        )
        // What the screens say is what is stored.
        assertEquals(
            listOf("48700|Pan", "1300|null"),
            TestData.rows("SELECT amount_minor, note FROM expense ORDER BY sequence")
        )
        assertEquals(50_000, TestData.long("SELECT SUM(amount_minor) FROM expense"))

        // Rebuilt, the same category is still open with the same figures.
        app.recreate()
        rule.awaitTag(CATEGORY_DETAIL_HEADER_TAG)
        rule.await(saying("Te quedan aquí 250.000 pesos."))
        rule.awaitTag(second)
        assertEquals(2, listed())

        // Back leaves the category for Recibo and does not leave the app.
        Espresso.pressBack()
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.onNodeWithTag(CATEGORY_DETAIL_TAG).assertDoesNotExist()
        rule.await(saying("Restante del presupuesto: 250.000 pesos."))
        assertEquals(Lifecycle.State.RESUMED, app.state)

        // Started again, it is all read back from storage.
        app.close()
        launch()
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.await(saying("Restante del presupuesto: 250.000 pesos."))
        rule.onNode(saying("Mercado. Te quedan 250.000 pesos de 300.000 pesos.")).assertExists()
        openMercado()
        rule.awaitTag(second)
        inDetail(CATEGORY_DETAIL_SPENT_TAG, "50.000 pesos").assertExists()
        assertEquals(2, listed())
    }

    @Test
    fun aFinishedMonthUsesItsOwnBudgetShowsNoDailyAmountAndListsOnlyItsOwnExpenses() {
        startWithMercado()
        val lastMonth = YearMonth.from(TestCommands.today).minusMonths(1)
        TestCommands.setMonthLimit("Mercado", lastMonth, 200_000)
        TestCommands.createExpense("Mercado", lastMonth.atDay(10), 150_000, "Del mes pasado")
        TestCommands.createExpense("Mercado", TestCommands.today, 10_000, "De hoy")

        // This month: its own limit, with a daily amount.
        rule.await(saying("Restante del presupuesto: 290.000 pesos."))
        rule.onNodeWithTag(RECIBO_PER_DAY_TAG).assertExists()

        pick(lastMonth)

        // Last month: 200.000 less 150.000, judged on what was spent, nothing per day.
        rule.awaitDescription("Restante del presupuesto: 50.000 pesos. Cumplido.")
        rule.onNodeWithTag(RECIBO_PER_DAY_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription(
            "Mercado. Te sobraron 50.000 pesos de 200.000 pesos. Cumplido."
        ).performScrollTo().assertIsDisplayed()

        openMercado()
        rule.awaitDescription("Te sobraron aquí 50.000 pesos. Cumplido.")
        inDetail(CATEGORY_DETAIL_SPENT_TAG, "150.000 pesos").assertExists()
        inDetail(CATEGORY_DETAIL_LIMIT_TAG, "200.000 pesos").assertExists()
        rule.awaitText("Del mes pasado")
        rule.onNodeWithText("De hoy").assertDoesNotExist()
        assertEquals(1, listed())

        // A change made while the screen is open arrives by itself: 210.000 of 200.000.
        TestCommands.createExpense("Mercado", lastMonth.atDay(11), 60_000, "Otro")

        rule.awaitDescription("Te pasaste aquí por 10.000 pesos. Excedido.")
        rule.awaitText("Otro")
        assertEquals(2, listed())
        Espresso.pressBack()
        rule.awaitDescription("Restante del presupuesto: menos 10.000 pesos. Excedido.")
        rule.onNodeWithTag(RECIBO_PER_DAY_TAG).assertDoesNotExist()
    }

    private fun awaitBrowserCount(text: String) {
        rule.await(
            hasTestTag("category.expenses") and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)
        )
    }

    private fun scrollBrowserTo(tag: String) {
        rule.onNodeWithTag("category.expenses").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun aLongListIsShownInPartsWhileTheFiguresCountAllOfIt() {
        startWithMercado()
        repeat(25) { TestCommands.createExpense("Mercado", TestCommands.today, 1_000) }
        rule.await(saying("Restante del presupuesto: 275.000 pesos."))
        openMercado()
        rule.awaitText("Ver los 25")
        inDetail(CATEGORY_DETAIL_SPENT_TAG, "25.000 pesos").assertExists()
        assertEquals(4, listed())
        rule.onNodeWithTag(CATEGORY_DETAIL_MORE_TAG).performScrollTo().performClick()
        awaitBrowserCount("Se muestran 20 de 25")
        scrollBrowserTo("category.expenses.more")
        rule.onNodeWithTag("category.expenses.more").performClick()
        awaitBrowserCount("25 gastos")
        scrollBrowserTo(categoryExpenseTag(expenseIds().first()))
        rule.onNodeWithTag(categoryExpenseTag(expenseIds().first())).assertIsDisplayed()
        rule.onNodeWithTag("category.expenses.more").assertDoesNotExist()
        Espresso.pressBack()
        rule.awaitTag(CATEGORY_DETAIL_SPENT_TAG)
        inDetail(CATEGORY_DETAIL_SPENT_TAG, "25.000 pesos").assertExists()
    }

    @Test
    fun anExpenseSavedWhileALongListIsOpenLeavesTheListAsLongAndWhereItWas() {
        startWithMercado()
        repeat(45) { TestCommands.createExpense("Mercado", TestCommands.today, 1_000) }
        rule.await(saying("Restante del presupuesto: 255.000 pesos."))
        openMercado()
        rule.awaitText("Ver los 45")
        rule.onNodeWithTag(CATEGORY_DETAIL_MORE_TAG).performScrollTo().performClick()
        awaitBrowserCount("Se muestran 20 de 45")
        scrollBrowserTo("category.expenses.more")
        rule.onNodeWithTag("category.expenses.more").performClick()
        awaitBrowserCount("Se muestran 40 de 45")
        scrollBrowserTo("category.expenses.more")
        rule.onNodeWithTag("category.expenses.more").assertIsDisplayed()

        // 45 × 1.000 + 2.500 = 47.500; the open browser keeps its 40 loaded rows.
        TestCommands.createExpense("Mercado", TestCommands.today, 2_500, "Nuevo")
        awaitBrowserCount("Se muestran 40 de 46")
        rule.onNodeWithTag("category.expenses.more").assertIsDisplayed()
        scrollBrowserTo(categoryExpenseTag(expenseIds().last()))
        rule.onNodeWithText("Nuevo").assertIsDisplayed()
        Espresso.pressBack()
        rule.await(saying("Te quedan aquí 252.500 pesos."))
        inDetail(CATEGORY_DETAIL_SPENT_TAG, "47.500 pesos").assertExists()
        rule.onNodeWithText("Ver los 46").assertExists()
    }

    @Test
    fun aReadThatFailsIsShownAsAFailureWithOlderFiguresMarkedAndNeverAsAnEmptyMonth() {
        startWithMercado()
        TestCommands.createExpense("Mercado", TestCommands.today, 40_000)
        rule.await(saying("Restante del presupuesto: 260.000 pesos."))

        // A stored amount that breaks the rules cannot be read. The next change makes
        // the app read the month again.
        TestData.execute("UPDATE expense SET amount_minor = 0")
        TestCommands.createExpense("Mercado", TestCommands.today, 5_000)

        // What was read before stays, marked as older; it is not replaced by a guess.
        rule.awaitTag(RECIBO_STALE_TAG)
        rule.onNode(saying("Restante del presupuesto: 260.000 pesos.")).assertExists()

        // The category was never read: it shows a failure, not "no expenses".
        openMercado()
        rule.awaitTag(CATEGORY_DETAIL_FAILED_TAG)
        rule.onNodeWithTag(CATEGORY_DETAIL_EMPTY_TAG).assertDoesNotExist()
        rule.onNodeWithTag(CATEGORY_DETAIL_HEADER_TAG).assertDoesNotExist()
        rule.onNodeWithTag(DETAIL_BACK_TAG).performClick()
        rule.awaitTag(RECIBO_STALE_TAG)

        // Started again there is nothing older to show: a failure, with no figure and
        // not the first-run screen.
        scenario?.close()
        launch()
        rule.awaitTag(RECIBO_FAILED_TAG)
        rule.onNodeWithTag(RECIBO_SUMMARY_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_FIRST_RUN_TAG).assertDoesNotExist()

        // Once the row can be read, trying again shows the month: 45.000 of 300.000.
        TestData.execute("UPDATE expense SET amount_minor = 40000 WHERE amount_minor = 0")
        rule.onNodeWithText("Reintentar").performClick()

        rule.await(saying("Restante del presupuesto: 255.000 pesos."))
        rule.onNodeWithTag(RECIBO_STALE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_FAILED_TAG).assertDoesNotExist()
        openMercado()
        rule.awaitText("Ver los 2")
        inDetail(CATEGORY_DETAIL_SPENT_TAG, "45.000 pesos").assertExists()
    }
}
