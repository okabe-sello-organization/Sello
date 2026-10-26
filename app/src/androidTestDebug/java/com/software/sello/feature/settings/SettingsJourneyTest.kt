package com.software.sello.feature.settings

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.feature.recibo.reciboCategoryTag
import com.software.sello.navigation.MONTH_SWITCHER_TAG
import com.software.sello.navigation.SETTINGS_ACTION_TAG
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** Real settings navigation and persistence against isolated synthetic financial history. */
@RunWith(AndroidJUnit4::class)
class SettingsJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val koin get() = GlobalContext.get()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun syntheticHistory() {
        TestData.reset()
        TestCommands.showCurrentMonth()
        defaults()
        TestCommands.createCategory("Alfa", 100_000)
        TestCommands.createCategory("Zeta", 100_000)
        TestCommands.createCategory("Libre", null)
        TestCommands.createExpense("Alfa", TestCommands.today, 1_000)
        TestCommands.createExpense("Zeta", TestCommands.today, 1_000)
        TestCommands.createExpense("Zeta", TestCommands.today, 1_000)
    }

    @After
    fun leaveNoFinancialOrPreferenceFixture() {
        scenario?.close()
        TestData.reset()
        TestCommands.showCurrentMonth()
        defaults()
    }

    private fun defaults() = runBlocking {
        koin.get<AppearancePreferences>().setTheme(ThemePreference.System)
        koin.get<AppearancePreferences>().setInk(InkPreference.Cobalto)
        koin.get<CategoryOrderingPreferences>().setCategoryOrder(CategoryOrder.MostUsed)
        koin.get<CategoryOrderingPreferences>().setCategoryOrderDirection(
            CategoryOrderDirection.Descending
        )
    }

    private fun launch() =
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
            .also {
                scenario = it
                rule.awaitTag(RECIBO_SUMMARY_TAG)
            }

    private fun openSettings() {
        rule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        rule.awaitTag(SETTINGS_TAG)
    }

    private fun inSettings(tag: String, text: String) = rule.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(tag))
    )

    /** Opens the option slip behind [row] and chooses [option] in the slip tagged [slip]. */
    private fun choose(row: String, slip: String, option: String) {
        rule.onNodeWithTag(row).performScrollTo().performClick()
        rule.awaitTag(slip)
        inSettings(slip, option).performClick()
        rule.waitForIdle()
    }

    /** [option] is the one marked in the slip behind [row]; the slip is closed again. */
    private fun assertChosen(row: String, slip: String, option: String) {
        rule.onNodeWithTag(row).performScrollTo().performClick()
        rule.awaitTag(slip)
        inSettings(slip, option).assertIsSelected()
        Espresso.pressBack()
        rule.waitForIdle()
    }

    @Test
    fun tusDatosComesFirstThenLoQueSeRepiteAndThereIsNoAvisos() {
        launch()
        openSettings()

        val tops = listOf(
            SETTINGS_DATA_TAG,
            SETTINGS_RECURRING_TAG,
            SETTINGS_APPEARANCE_TAG,
            SETTINGS_ORDERING_TAG,
            SETTINGS_INVESTMENTS_TAG
        ).map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        assertEquals(0, rule.onAllNodes(hasText("Avisos")).fetchSemanticsNodes().size)
    }

    @Test
    fun settingsPreserveTheSelectedMonthFinancialRowsAndTheReciboBackStack() {
        val selected = YearMonth.from(TestCommands.today).minusMonths(1)
        TestCommands.setMonthLimit("Alfa", selected, 100_000)
        TestCommands.createExpense("Alfa", selected.atDay(1), 500)
        koin.get<com.software.sello.navigation.MonthSession>().select(selected)
        launch()
        val expenseRows = TestData.count("expense")
        val spent = TestData.long("SELECT SUM(amount_minor) FROM expense")

        openSettings()
        choose(SETTINGS_ORDER_ROW_TAG, SETTINGS_ORDER_TAG, "Alfabético")

        Espresso.pressBack()
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertExists()
        monthShown(selected)
        assertEquals(expenseRows, TestData.count("expense"))
        assertEquals(spent, TestData.long("SELECT SUM(amount_minor) FROM expense"))
    }

    @Test
    fun appearanceAndOrderingPersistAcrossAColdActivityRestart() {
        launch()
        rule.awaitTag(reciboCategoryTag(TestCommands.categoryId("Alfa")))
        rule.awaitTag(reciboCategoryTag(TestCommands.categoryId("Zeta")))
        val alfa = rule.onNodeWithTag(reciboCategoryTag(TestCommands.categoryId("Alfa")))
        val zeta = rule.onNodeWithTag(reciboCategoryTag(TestCommands.categoryId("Zeta")))
        assertTrue(zeta.getUnclippedBoundsInRoot().left < alfa.getUnclippedBoundsInRoot().left)

        openSettings()
        inSettings(SETTINGS_THEME_TAG, "Oscuro").performScrollTo().performClick()
        inSettings(SETTINGS_INK_TAG, "Violeta").performScrollTo().performClick()
        choose(SETTINGS_ORDER_ROW_TAG, SETTINGS_ORDER_TAG, "Alfabético")
        choose(SETTINGS_DIRECTION_ROW_TAG, SETTINGS_DIRECTION_TAG, "Menor a mayor")
        // The rows say what was chosen.
        rule.onNodeWithTag(SETTINGS_ORDER_ROW_TAG).assert(hasText("Alfabético"))
        rule.onNodeWithTag(SETTINGS_DIRECTION_ROW_TAG).assert(hasText("Menor a mayor"))
        scenario!!.close()

        launch()
        val alphabeticAlfa = rule.onNodeWithTag(reciboCategoryTag(TestCommands.categoryId("Alfa")))
        val alphabeticZeta = rule.onNodeWithTag(reciboCategoryTag(TestCommands.categoryId("Zeta")))
        assertTrue(
            alphabeticAlfa.getUnclippedBoundsInRoot().left <
                alphabeticZeta.getUnclippedBoundsInRoot().left
        )
        assertEquals(3_000L, TestData.long("SELECT SUM(amount_minor) FROM expense"))

        openSettings()
        inSettings(SETTINGS_THEME_TAG, "Oscuro").assertIsSelected()
        inSettings(SETTINGS_INK_TAG, "Violeta").assertIsSelected()
        assertChosen(SETTINGS_ORDER_ROW_TAG, SETTINGS_ORDER_TAG, "Alfabético")
        assertChosen(SETTINGS_DIRECTION_ROW_TAG, SETTINGS_DIRECTION_TAG, "Menor a mayor")
        assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
    }

    /** The month bar names [month]; its words are one button, so it is read as a label. */
    private fun monthShown(month: YearMonth) {
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertIsDisplayed()
            .assert(hasContentDescription(monthTitle(month), substring = true))
    }

    private fun monthTitle(month: YearMonth): String = context.getString(
        com.software.sello.R.string.month_title,
        context.resources.getStringArray(com.software.sello.R.array.month_names)[
            month.monthValue -
                1
        ],
        month.year
    )
}
