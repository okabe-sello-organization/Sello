package com.software.sello.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.SCAFFOLD_BAR_TAG
import com.software.sello.designsystem.component.SCAFFOLD_RAIL_TAG
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The frame alone, given state by hand, in windows of chosen sizes and font scales.
 * On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: the layout below depends on how wide the words are.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private val october = YearMonth.of(2026, 10)
    private val actions = mutableListOf<ShellAction>()

    private fun state(pickerOpen: Boolean) = ShellState(
        currentMonth = october,
        selectedMonth = YearMonth.of(2026, 8),
        monthPickerOpen = pickerOpen,
        pickerYear = 2026,
        pendingEntry = null
    )

    private fun stateWith(pickerOpen: Boolean) = state(pickerOpen)

    /** The frame in the test device's own window, which on a phone is a compact one. */
    private fun showOnThisScreen(pickerOpen: Boolean, fontScale: Float = 1f) {
        rule.showSello(Look(fontScale = fontScale)) { Frame(pickerOpen) }
    }

    /**
     * The frame in a window of [width] × [height] dp, one dp drawn as one pixel. Such a
     * window can be larger than the device's screen, so these cases check what exists
     * and where it is laid out, not whether it is visible on the screen.
     */
    private fun showInWindow(width: Int, height: Int, pickerOpen: Boolean) {
        rule.showSello(Look(window = DpSize(width.dp, height.dp), density = 1f)) {
            Frame(pickerOpen)
        }
    }

    /** Set by a test that gives the frame a detail, and that opens it. */
    private var withDetail = false
    private var opened: String? = null

    @Composable
    private fun Frame(pickerOpen: Boolean) {
        val detail = ShellDetail("Alimentación") { padding ->
            Text("detalle", Modifier.padding(padding).testTag("detail"))
        }
        ShellScreen(
            stateWith(pickerOpen).copy(detailCategoryId = opened),
            monthNames(),
            { actions += it },
            detail = detail.takeIf { withDetail }
        ) { padding ->
            Text("contenido", Modifier.padding(padding).testTag("content"))
        }
    }

    @Test
    fun onAPhoneAnOpenedDetailTakesTheScreenUnderItsOwnTitleWithAWayBack() {
        withDetail = true
        opened = "a"
        showOnThisScreen(pickerOpen = false)

        rule.onNodeWithTag("detail").assertIsDisplayed()
        rule.onNodeWithTag("content").assertDoesNotExist()
        rule.onNodeWithText("Alimentación").assertIsDisplayed()
        rule.onNodeWithText("Agosto 2026").assertIsDisplayed()
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription("Anotar un gasto").performClick()
        rule.onNodeWithContentDescription("Volver al recibo").performClick()

        assertEquals(
            listOf(ShellAction.StartEntryIn("a"), ShellAction.CloseCategory),
            actions
        )
    }

    @Test
    fun onAPhoneADetailThatWasNotOpenedIsNotShown() {
        withDetail = true
        showOnThisScreen(pickerOpen = false)

        rule.onNodeWithTag("content").assertIsDisplayed()
        rule.onNodeWithTag("detail").assertDoesNotExist()
        rule.onNodeWithTag(DETAIL_BACK_TAG).assertDoesNotExist()
    }

    @Test
    fun withRoomForTwoColumnsTheDetailIsBesideTheContentOpenedOrNot() {
        withDetail = true
        showInWindow(900, 700, pickerOpen = false)

        val content = rule.onNodeWithTag("content").assertExists().getUnclippedBoundsInRoot()
        val detail = rule.onNodeWithTag("detail").assertExists().getUnclippedBoundsInRoot()

        assertTrue("content $content, detail $detail", content.right <= detail.left)
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertExists()
        rule.onNodeWithTag(DETAIL_BACK_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription("Anotar un gasto")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf<ShellAction>(ShellAction.StartEntry), actions)
    }

    @Test
    fun inAShortLandscapeWindowAnOpenedDetailIsBesideTheContentToo() {
        withDetail = true
        opened = "a"
        showInWindow(760, 400, pickerOpen = false)

        val content = rule.onNodeWithTag("content").assertExists().getUnclippedBoundsInRoot()
        val detail = rule.onNodeWithTag("detail").assertExists().getUnclippedBoundsInRoot()

        assertTrue("content $content, detail $detail", content.right <= detail.left)
        rule.onNodeWithTag(DETAIL_BACK_TAG).assertDoesNotExist()
    }

    @Test
    fun inAnExpandedWindowTheMonthPanelTakesTheDetailsPlaceWhileItIsOpen() {
        withDetail = true
        showInWindow(900, 700, pickerOpen = true)

        rule.onNodeWithTag(MONTH_PICKER_TAG).assertExists()
        rule.onNodeWithTag("content").assertExists()
        rule.onNodeWithTag("detail").assertDoesNotExist()
    }

    @Test
    fun theTitleIsTheSelectedMonthAndOpensThePicker() {
        showOnThisScreen(pickerOpen = false)

        // The v2 month bar: one button saying the month, not a one-line "Agosto 2026" title.
        rule.onNodeWithText("Agosto 2026").assertDoesNotExist()
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertIsDisplayed()
            .assert(hasContentDescription("Agosto 2026", substring = true))
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).performClick()

        assertEquals(listOf<ShellAction>(ShellAction.OpenMonthPicker), actions)
        rule.onNodeWithTag(MONTH_PICKER_TAG).assertDoesNotExist()
    }

    @Test
    fun withOneTabThereIsNoTabBarOrRailAndTheDockAsksToRecordAnExpense() {
        showInWindow(900, 700, pickerOpen = false)

        rule.onNodeWithTag(SCAFFOLD_BAR_TAG).assertDoesNotExist()
        rule.onNodeWithTag(SCAFFOLD_RAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag("content").assertExists()
        rule.onNodeWithContentDescription("Anotar un gasto")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf<ShellAction>(ShellAction.StartEntry), actions)
    }

    @Test
    fun onAPhoneThePickerIsASheetOverTheContent() {
        showOnThisScreen(pickerOpen = true)

        rule.onNodeWithTag(MONTH_PICKER_TAG).assertIsDisplayed()
        // A month is one control, announced by its full name.
        rule.onNodeWithContentDescription("Agosto 2026").performScrollTo().performClick()

        assertEquals(listOf<ShellAction>(ShellAction.PickMonth(YearMonth.of(2026, 8))), actions)
    }

    @Test
    fun inAnExpandedWindowThePickerIsAPanelBesideTheContentNotOverIt() {
        showInWindow(900, 700, pickerOpen = true)

        val content = rule.onNodeWithTag("content").assertExists().getUnclippedBoundsInRoot()
        val picker = rule.onNodeWithTag(MONTH_PICKER_TAG).assertExists().getUnclippedBoundsInRoot()

        assertTrue("content $content, picker $picker", content.right <= picker.left)
    }

    @Test
    fun inAShortLandscapeWindowEveryMonthOfThePickerCanBeReached() {
        showInWindow(760, 400, pickerOpen = true)

        rule.onNodeWithTag(MONTH_PICKER_TAG).assertExists()
        rule.onNodeWithContentDescription("Diciembre 2026").performScrollTo().assertExists()
    }

    @Test
    fun atTwiceTheFontSizeTheTitleAndPickerAreStillThere() {
        showOnThisScreen(pickerOpen = true, fontScale = 2f)

        rule.onNodeWithTag(MONTH_PICKER_TAG).assertIsDisplayed()
        // Every month can be reached, scrolling if it has to.
        rule.onNodeWithContentDescription("Diciembre 2026").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Enero 2026").performScrollTo().assertIsDisplayed()
    }
}
