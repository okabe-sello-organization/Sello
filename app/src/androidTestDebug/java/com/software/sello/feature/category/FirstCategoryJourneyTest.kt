package com.software.sello.feature.category

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
import com.software.sello.feature.recibo.RECIBO_ADD_CATEGORY_TAG
import com.software.sello.feature.recibo.RECIBO_CATEGORIES_TAG
import com.software.sello.feature.recibo.RECIBO_FIRST_RUN_TAG
import com.software.sello.hideKeyboard
import com.software.sello.press
import com.software.sello.revealed
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The installed app with its real database, starting from a new installation: no test
 * tools create the category, only the screens a person uses.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FirstCategoryJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val month get() =
        YearMonth.from(GlobalContext.get().get<FinancialClock>().today.value.date).toString()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun newInstallation() = TestData.reset()

    @After
    fun leaveNothingBehind() {
        scenario?.takeIf { it.state != Lifecycle.State.DESTROYED }?.hideKeyboard()
        scenario?.close()
        TestData.reset()
    }

    /** Starts the app and waits until Recibo has read the month. */
    private fun launch() =
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
            .also {
                scenario = it
                rule.awaitTag(RECIBO_FIRST_RUN_TAG)
            }

    /** Opens the form from the empty Recibo and waits until it is ready to type in. */
    private fun openFirstForm() {
        rule.onNodeWithText("Crear categoría").performClick()
        rule.awaitDescription("Nombre")
    }

    private fun ComposeTestRule.nameField() = onNodeWithContentDescription("Nombre")

    private fun submit() = rule.onNodeWithTag(EDITOR_SUBMIT_TAG).press()

    /** From Recibo, through the form, back to Recibo with the category listed. */
    private fun create(name: String, limit: String? = null) {
        val open = if (TestData.count("category") == 0L) {
            rule.onNodeWithText("Crear categoría")
        } else {
            rule.onNodeWithTag(RECIBO_ADD_CATEGORY_TAG).performScrollTo()
        }
        open.performClick()
        rule.awaitDescription("Nombre")
        rule.nameField().performTextInput(name)
        if (limit != null) {
            rule.typeLimit(limit)
        } else {
            // No limit is a choice the person makes; it is not where the form starts.
            rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).revealed().performClick()
        }
        submit()
        rule.waitUntilExactlyOneExists(hasTestTag(RECIBO_CATEGORIES_TAG), 10_000)
    }

    @Test
    fun aNewInstallationCreatesItsFirstCategoryAndStillHasItAfterTheScreenIsRebuilt() {
        val app = launch()
        rule.onNodeWithTag(RECIBO_FIRST_RUN_TAG).assertIsDisplayed()
        openFirstForm()
        rule.onNodeWithText("Nueva categoría").assertIsDisplayed()
        // Nothing can be created without a name.
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).assertIsNotEnabled()

        rule.nameField().performTextInput(" Mercado de plaza ")
        rule.onNodeWithTag(EDITOR_ICON_TAG).press()
        rule.awaitTag(EDITOR_ICONS_TAG)
        rule.onNodeWithContentDescription("Mercado").performClick()
        rule.typeLimit("300.000")
        submit()

        rule.waitUntilExactlyOneExists(hasTestTag(RECIBO_CATEGORIES_TAG), 10_000)
        rule.onNodeWithContentDescription(
            "Mercado de plaza. Te quedan 300.000 pesos de 300.000 pesos. Al día."
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(
            "Restante del presupuesto: 300.000 pesos. Al día."
        ).assertExists()
        assertEquals(
            listOf("Mercado de plaza|mercado de plaza|shopping_cart|0|1"),
            TestData.rows("SELECT name, name_key, icon, archived, version FROM category")
        )
        assertEquals(
            listOf("$month|finite|300000|COP"),
            TestData.rows("SELECT effective_month, kind, limit_minor, currency FROM default_limit")
        )
        assertEquals(
            listOf("category.create|1|1"),
            TestData.rows("SELECT kind, generation, revision FROM operation_receipt")
        )
        assertEquals(1, TestData.long("SELECT revision FROM profile"))
        // The receipt's subject is the category that is shown.
        assertEquals(
            TestData.rows("SELECT id FROM category"),
            TestData.rows("SELECT subject_id FROM operation_receipt")
        )

        app.recreate()

        rule.awaitDescription(
            "Mercado de plaza. Te quedan 300.000 pesos de 300.000 pesos. Al día."
        )
        rule.onNodeWithContentDescription(
            "Restante del presupuesto: 300.000 pesos. Al día."
        ).assertExists()
    }

    @Test
    fun aCategoryWithoutALimitIsUnlimitedAndASecondOneCanBeAdded() {
        launch()

        create("Café")
        create("Transporte", limit = "0")

        rule.onNodeWithContentDescription("Café. Has gastado 0 pesos. Sin límite.")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Transporte. Te quedan 0 pesos de 0 pesos. Al día.")
            .performScrollTo().assertIsDisplayed()
        assertEquals(
            listOf("Café|unlimited|null", "Transporte|finite|0"),
            TestData.rows(
                "SELECT c.name, d.kind, d.limit_minor FROM category c " +
                    "JOIN default_limit d ON d.category_id = c.id ORDER BY c.name"
            )
        )
        assertEquals(2, TestData.long("SELECT revision FROM profile"))
    }
}
