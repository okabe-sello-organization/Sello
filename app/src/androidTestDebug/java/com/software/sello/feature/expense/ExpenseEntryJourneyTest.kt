package com.software.sello.feature.expense

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.TestData
import com.software.sello.awaitDescription
import com.software.sello.awaitTag
import com.software.sello.domain.port.FinancialClock
import com.software.sello.feature.category.EDITOR_PREREQUISITE_TAG
import com.software.sello.feature.category.EDITOR_SUBMIT_TAG
import com.software.sello.feature.category.EDITOR_UNLIMITED_TAG
import com.software.sello.feature.recibo.RECIBO_CATEGORIES_TAG
import com.software.sello.feature.recibo.RECIBO_FIRST_RUN_TAG
import com.software.sello.hideKeyboard
import com.software.sello.navigation.CORRECTION_NOTICE_TAG
import com.software.sello.press
import com.software.sello.revealed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The installed app with its real database, from a new installation: category, then
 * Anotar, then the receipt, using only the screens a person uses.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseEntryJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
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

    private fun launch(link: String? = null): ActivityScenario<MainActivity> {
        val intent = Intent(context, MainActivity::class.java)
        if (link != null) {
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(link)
        }
        return ActivityScenario.launch<MainActivity>(intent).also { scenario = it }
    }

    private fun createCategory(name: String) {
        rule.awaitDescription("Nombre")
        rule.onNodeWithContentDescription("Nombre").performTextInput(name)
        // These journeys are about expenses: the category is given no limit, on purpose.
        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).revealed().press()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).press()
    }

    /** A new installation with one category, on Recibo. */
    private fun startWithCategory(name: String = "Mercado"): ActivityScenario<MainActivity> {
        val app = launch()
        rule.awaitTag(RECIBO_FIRST_RUN_TAG)
        rule.onNodeWithText("Crear categoría").performClick()
        createCategory(name)
        rule.awaitTag(RECIBO_CATEGORIES_TAG)
        return app
    }

    /** Opens Anotar from the dock; nothing is chosen for the person, so [category] is picked. */
    private fun openAnotar(category: String = "Mercado") {
        // The dock is not drawn while the system keyboard is up, and the keyboard used
        // to type the category's name may still be closing when Recibo comes back.
        rule.awaitDescription("Anotar un gasto")
        rule.onNodeWithContentDescription("Anotar un gasto").performClick()
        rule.awaitTag(ENTRY_KEYPAD_TAG)
        val chip = rule.onNode(
            hasText(category) and hasAnyAncestor(hasTestTag(ENTRY_CATEGORIES_TAG))
        )
        chip.assertIsNotSelected()
        chip.performClick()
        chip.assertIsSelected()
    }

    /** Writes [text] in the note's composer and finishes it. */
    private fun writeNote(text: String) {
        rule.onNodeWithTag(ENTRY_NOTE_TAG).performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextInput(text)
        rule.onNodeWithContentDescription("Listo").performClick()
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    private fun press(vararg keys: String) = keys.forEach { key ->
        rule.onNode(hasText(key) and hasAnyAncestor(hasTestTag(ENTRY_KEYPAD_TAG)))
            .performClick()
    }

    /** The amount on the slip; the preview lines below it may show the same figure. */
    private fun amountShows(spoken: String) = rule.onAllNodesWithContentDescription(
        spoken
    ).onFirst().assertIsDisplayed()

    /** A confirmed save closes the form; Recibo shows it and says what was saved. */
    private fun savedAndBackOnRecibo(said: String) {
        rule.awaitTag(CORRECTION_NOTICE_TAG)
        rule.onNodeWithText(said).assertIsDisplayed()
        rule.onNodeWithTag(ENTRY_KEYPAD_TAG).assertDoesNotExist()
        rule.awaitTag(RECIBO_CATEGORIES_TAG)
    }

    private fun expenses() = TestData.rows(
        "SELECT e.amount_minor, e.currency, c.name, e.effective_date, e.note, e.sequence, " +
            "e.version FROM expense e JOIN category c ON c.id = e.category_id ORDER BY e.sequence"
    )

    @Test
    fun aNewInstallationRecordsItsFirstExpenseAndReturnsToReciboSayingWhatWasStored() {
        startWithCategory()
        openAnotar()
        // Nothing can be recorded without an amount.
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).assertIsNotEnabled()

        press("1", "0", "000")
        writeNote("Pan")
        // Two presses with nothing in between, faster than a finger could manage.
        val save = rule.onNodeWithContentDescription("Anotar gasto de 10.000 pesos en Mercado")
            .fetchSemanticsNode()
        rule.runOnUiThread { repeat(2) { save.config[SemanticsActions.OnClick].action!!.invoke() } }

        // No receipt and no Listo: straight back, with the stored figures already on Recibo.
        savedAndBackOnRecibo("Gasto anotado: 10.000 en Mercado")
        rule.onNodeWithContentDescription("Gasto anotado: 10.000 pesos en Mercado")
            .assertExists()
        rule.onNodeWithText("Listo").assertDoesNotExist()
        assertEquals(listOf("10000|COP|Mercado|$today|Pan|1|1"), expenses())
        // One receipt, for exactly that expense.
        assertEquals(
            1,
            TestData.long("SELECT COUNT(*) FROM operation_receipt WHERE kind = 'expense.create'")
        )
        assertEquals(
            TestData.rows("SELECT id FROM expense"),
            TestData.rows("SELECT subject_id FROM operation_receipt WHERE kind = 'expense.create'")
        )
        assertEquals(2, TestData.long("SELECT revision FROM profile"))
        rule.onNodeWithContentDescription("Has gastado este mes: 10.000 pesos. Sin límite.")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("Mercado. Has gastado 10.000 pesos. Sin límite.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rebuildingTheScreenOnADraftOrAfterTheSaveNeverRecordsAgain() {
        val app = startWithCategory()
        openAnotar()
        press("7", "5", "0", "0")

        app.recreate()
        rule.awaitTag(ENTRY_KEYPAD_TAG)
        amountShows("7.500 pesos")
        assertEquals(emptyList<String>(), expenses())

        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).press()
        savedAndBackOnRecibo("Gasto anotado: 7.500 en Mercado")
        app.recreate()

        rule.awaitTag(RECIBO_CATEGORIES_TAG)
        rule.onNodeWithTag(ENTRY_KEYPAD_TAG).assertDoesNotExist()
        assertEquals(listOf("7500|COP|Mercado|$today|null|1|1"), expenses())
    }

    @Test
    fun withNoCategoryAnotarAsksForOneFirstThenGoesOnToTheExpenseWithItChosen() {
        launch()
        rule.awaitTag(RECIBO_FIRST_RUN_TAG)

        rule.onNodeWithContentDescription("Anotar un gasto").performClick()
        rule.awaitTag(EDITOR_PREREQUISITE_TAG)
        createCategory("Mercado")

        rule.awaitTag(ENTRY_KEYPAD_TAG)
        rule.onNode(hasText("Mercado") and hasAnyAncestor(hasTestTag(ENTRY_CATEGORIES_TAG)))
            .assertIsSelected()
        press("9", "000")
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).press()
        savedAndBackOnRecibo("Gasto anotado: 9.000 en Mercado")

        assertEquals(listOf("9000|COP|Mercado|$today|null|1|1"), expenses())
    }

    @Test
    fun aLinkPrefillsTheDraftAndRecordsNothingUntilThePersonConfirms() {
        startWithCategory("Café")
        val category = TestData.rows("SELECT id FROM category").single()
        scenario?.close()

        launch("sello://anotar?categoria=$category&monto=48700")
        rule.awaitTag(ENTRY_KEYPAD_TAG)

        amountShows("48.700 pesos")
        rule.onNode(hasText("Café") and hasAnyAncestor(hasTestTag(ENTRY_CATEGORIES_TAG)))
            .assertIsSelected()
        assertEquals(emptyList<String>(), expenses())

        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).press()
        savedAndBackOnRecibo("Gasto anotado: 48.700 en Café")

        assertEquals(listOf("48700|COP|Café|$today|null|1|1"), expenses())
    }

    @Test
    fun nuevaCreatesACategoryAndComesBackWithItChosenAndTheDraftKept() {
        startWithCategory()
        openAnotar()
        press("4", "8", "7", "0", "0")

        rule.onNodeWithTag(ENTRY_NEW_CATEGORY_TAG).performScrollTo().performClick()
        createCategory("Café")

        rule.awaitTag(ENTRY_KEYPAD_TAG)
        rule.onNode(hasText("Café") and hasAnyAncestor(hasTestTag(ENTRY_CATEGORIES_TAG)))
            .assertIsSelected()
        amountShows("48.700 pesos")
        assertEquals(emptyList<String>(), expenses())
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).press()
        savedAndBackOnRecibo("Gasto anotado: 48.700 en Café")

        assertEquals(listOf("48700|COP|Café|$today|null|1|1"), expenses())
    }
}
