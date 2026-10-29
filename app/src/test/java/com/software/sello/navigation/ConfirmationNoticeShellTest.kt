package com.software.sello.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.SCAFFOLD_BAR_TAG
import com.software.sello.designsystem.component.SCAFFOLD_DOCK_TAG
import com.software.sello.designsystem.component.SCAFFOLD_RAIL_TAG
import com.software.sello.designsystem.component.SELLO_NOTICE_CHECK_TAG
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The frame showing a confirmed save the way the app draws it, on each tab, in both
 * themes, at twice the font size and beside a rail. The words are a sample: the screen
 * that saved writes them. On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: the layout below depends on how wide the words are.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConfirmationNoticeShellTest {
    @get:Rule
    val rule = createComposeRule()

    private val message = "Gasto anotado: 48.700 en Comida"
    private val spoken = "Gasto anotado: 48.700 pesos en Comida"
    private val saved = CorrectionNotice.Confirmed(message, spoken)

    private val state = ShellState(
        currentMonth = YearMonth.of(2026, 10),
        selectedMonth = YearMonth.of(2026, 10),
        monthPickerOpen = false,
        pickerYear = 2026,
        pendingEntry = null
    )

    @Composable
    private fun Frame(tab: String) {
        ShellScreen(
            state,
            monthNames(),
            {},
            notice = { CorrectionNoticeBar(saved) {} },
            navigation = SelloNavigation(
                listOf(
                    SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                    SelloDestination("resumen", "Resumen", SelloIcon.Insights)
                ),
                tab
            ) {}
        ) { padding ->
            Text("contenido", Modifier.padding(padding).testTag("content"))
        }
    }

    private fun showOnThisScreen(tab: String, dark: Boolean = false, fontScale: Float = 1f) {
        rule.showSello(Look(dark = dark, fontScale = fontScale)) { Frame(tab) }
    }

    private fun assertDrawnAboveTheDockAndTabs() {
        val notice = rule.onNodeWithTag(CORRECTION_NOTICE_TAG).assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val dock = rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).getUnclippedBoundsInRoot()
        val bar = rule.onNodeWithTag(SCAFFOLD_BAR_TAG).getUnclippedBoundsInRoot()
        assertTrue("$notice must end above the dock $dock", notice.bottom <= dock.top)
        assertTrue("$notice must end above the tab bar $bar", notice.bottom <= bar.top)
    }

    /**
     * [onScreen] is false for a window larger than the device's screen, where the check
     * can be laid out beyond what the screen shows: it must then exist, not be visible.
     */
    private fun assertTheCheckIsDrawnAndTheMessageIsSaidPolitely(onScreen: Boolean = true) {
        val check = rule.onNodeWithTag(SELLO_NOTICE_CHECK_TAG, useUnmergedTree = true)
            .let { if (onScreen) it.assertIsDisplayed() else it.assertExists() }
        assertNull(
            check.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        )
        rule.onNodeWithContentDescription(spoken, useUnmergedTree = true)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
    }

    @Test
    fun onReciboAConfirmedSaveShowsItsCheckAboveTheDockAndIsSaidPolitely() {
        showOnThisScreen("recibo")

        assertDrawnAboveTheDockAndTabs()
        assertTheCheckIsDrawnAndTheMessageIsSaidPolitely()
    }

    @Test
    fun onResumenAConfirmedSaveShowsItsCheckAboveTheDock() {
        showOnThisScreen("resumen")

        assertDrawnAboveTheDockAndTabs()
        assertTheCheckIsDrawnAndTheMessageIsSaidPolitely()
    }

    @Test
    fun inTheDarkThemeAConfirmedSaveLooksTheSameWay() {
        showOnThisScreen("recibo", dark = true)

        assertDrawnAboveTheDockAndTabs()
        assertTheCheckIsDrawnAndTheMessageIsSaidPolitely()
    }

    @Test
    fun atTwiceTheFontSizeAConfirmedSaveStillLeavesTheDockAndTabsUncovered() {
        showOnThisScreen("recibo", fontScale = 2f)

        assertDrawnAboveTheDockAndTabs()
        assertTheCheckIsDrawnAndTheMessageIsSaidPolitely()
    }

    @Test
    fun besideARailAConfirmedSaveSitsOverTheContentNotTheRail() {
        rule.showSello(Look(window = DpSize(1000.dp, 700.dp), density = 1f)) { Frame("recibo") }

        val notice = rule.onNodeWithTag(CORRECTION_NOTICE_TAG).getUnclippedBoundsInRoot()
        val rail = rule.onNodeWithTag(SCAFFOLD_RAIL_TAG).getUnclippedBoundsInRoot()
        assertTrue("$notice must be beside the rail $rail", notice.left >= rail.right)
        assertTheCheckIsDrawnAndTheMessageIsSaidPolitely(onScreen = false)
    }

    private fun says(notice: CorrectionNotice, shown: String, spoken: String) {
        rule.onNodeWithText(shown).assertExists()
        rule.onNodeWithContentDescription(spoken, useUnmergedTree = true)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
        assertTrue(
            notice is CorrectionNotice.ExpenseRecorded || notice is CorrectionNotice.IncomeRecorded
        )
    }

    private fun showNotice(notice: CorrectionNotice) = rule.showSello {
        CorrectionNoticeBar(notice) {}
    }

    private val amount = MoneyTextValue(MoneySign.None, "", "48.700", "48.700 pesos")

    @Test
    fun aNewExpenseIsSaidWithItsStoredAmountAndCategory() {
        val notice = CorrectionNotice.ExpenseRecorded(amount, "Comida")
        showNotice(notice)

        says(notice, "Gasto anotado: 48.700 en Comida", "Gasto anotado: 48.700 pesos en Comida")
        rule.onNodeWithTag(SELLO_NOTICE_CHECK_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun aNewExpenseWhoseCategoryCouldNotBeNamedIsSaidWithItsAmountOnly() {
        val notice = CorrectionNotice.ExpenseRecorded(amount, categoryName = null)
        showNotice(notice)

        says(notice, "Gasto anotado: 48.700", "Gasto anotado: 48.700 pesos")
    }

    @Test
    fun aNewExpenseThatCouldNotBeReadBackIsStillSaidToBeSaved() {
        val notice = CorrectionNotice.ExpenseRecorded(amount = null, categoryName = null)
        showNotice(notice)

        says(notice, "Gasto anotado", "Gasto anotado")
    }

    @Test
    fun aNewIncomeIsSaidWithItsStoredAmountOrPlainlyWhenItCouldNotBeRead() {
        var notice: CorrectionNotice by mutableStateOf(CorrectionNotice.IncomeRecorded(amount))
        rule.showSello { CorrectionNoticeBar(notice) {} }
        says(notice, "Ingreso anotado: 48.700", "Ingreso anotado: 48.700 pesos")

        notice = CorrectionNotice.IncomeRecorded(amount = null)
        rule.waitForIdle()

        says(notice, "Ingreso anotado", "Ingreso anotado")
    }
}
