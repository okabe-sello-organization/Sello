package com.software.sello.feature.investments

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.designsystem.component.SCAFFOLD_DOCK_TAG
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.feature.settings.SETTINGS_INVESTMENT_CURRENCY_ROW_TAG
import com.software.sello.feature.settings.SETTINGS_INVESTMENT_CURRENCY_TAG
import com.software.sello.feature.settings.SETTINGS_INVESTMENT_ORDER_ROW_TAG
import com.software.sello.feature.settings.SETTINGS_INVESTMENT_ORDER_TAG
import com.software.sello.feature.settings.SETTINGS_TAG
import com.software.sello.hideKeyboard
import com.software.sello.navigation.SETTINGS_ACTION_TAG
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Configuración › Inversiones in the installed app (SELLO-120): the list follows the order
 * chosen there, and Nueva inversión starts in the currency chosen there.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentSettingsJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val settings get() = GlobalContext.get().get<InvestmentPreferences>()

    @Before
    fun defaults() {
        TestData.reset()
        restoreDefaults()
    }

    @After
    fun close() {
        scenario?.hideKeyboard()
        scenario?.close()
        TestData.reset()
        restoreDefaults()
    }

    private fun restoreDefaults() = runBlocking {
        val defaults = InvestmentPreference()
        settings.setNewInvestmentCurrency(defaults.newCurrency)
        settings.setInvestmentOrder(defaults.order)
    }

    private fun openInversiones() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitText("Inversiones")
        rule.onNode(hasText("Inversiones") and !hasAnyAncestor(hasTestTag(INVERSIONES_LIST_TAG)))
            .performClick()
        rule.awaitTag(INVERSIONES_LIST_TAG)
    }

    /**
     * In Configuración, opened from Recibo (the only tab with the gear), opens the slip
     * behind [row] and chooses [option] in [slip], then comes back to Inversiones.
     */
    private fun choose(row: String, slip: String, option: String) {
        rule.onNode(hasText("Recibo") and hasClickAction()).performClick()
        rule.awaitTag(SETTINGS_ACTION_TAG)
        rule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        rule.awaitTag(SETTINGS_TAG)
        rule.onNodeWithTag(row).performScrollTo().performClick()
        rule.awaitTag(slip)
        rule.onNode(hasText(option) and hasAnyAncestor(hasTestTag(slip))).assertExists()
            .performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(row).performScrollTo().performClick()
        rule.awaitTag(slip)
        rule.onNode(hasText(option) and hasAnyAncestor(hasTestTag(slip))).assertIsSelected()
        Espresso.pressBack()
        Espresso.pressBack()
        rule.onNode(hasText("Inversiones") and hasClickAction()).performClick()
        rule.awaitTag(INVERSIONES_LIST_TAG)
    }

    private fun top(name: String) = rule.onNode(
        hasText(name) and hasAnyAncestor(hasTestTag("inversiones.rows"))
    ).getUnclippedBoundsInRoot().top

    @Test
    fun theListFollowsTheOrderChosenInConfiguracion() {
        TestCommands.createInvestment("Zeta", 5_000_000)
        TestCommands.createInvestment("Alfa", 1_000_000)
        openInversiones()
        rule.awaitText("Zeta")
        // Mayor valor, the default.
        assertTrue(top("Zeta") < top("Alfa"))

        choose(SETTINGS_INVESTMENT_ORDER_ROW_TAG, SETTINGS_INVESTMENT_ORDER_TAG, "Alfabético")

        rule.awaitText("Zeta")
        assertTrue(top("Alfa") < top("Zeta"))
        // Saved through the real preferences, not only shown.
        assertEquals(
            PreferenceRead.Ready(InvestmentPreference(order = InvestmentOrder.Alphabetical)),
            runBlocking { settings.observeInvestments().first() }
        )
    }

    @Test
    fun nuevaInversionStartsInDollarsWhenThatIsChosen() {
        openInversiones()
        choose(
            SETTINGS_INVESTMENT_CURRENCY_ROW_TAG,
            SETTINGS_INVESTMENT_CURRENCY_TAG,
            "Dólares (USD)"
        )

        // With no investments yet, the dock adds the first.
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Agregar inversión")).performClick()
        rule.awaitTag(INVESTMENT_EDITOR_TAG)

        rule.onNode(
            hasText("Dólares") and hasAnyAncestor(hasTestTag(INVESTMENT_EDITOR_CURRENCY_TAG))
        ).assertIsSelected()
        // No rate exists yet: the editor asks for it, as when dollars are picked there.
        rule.awaitTag(INVESTMENT_EDITOR_RATE_SLIP_TAG)
    }
}
