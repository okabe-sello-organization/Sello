package com.software.sello.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.SCAFFOLD_SECONDARY_TAG
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
 * "Mes actual" in the month picker: one press goes to the month the financial day is
 * in, from whatever year is being browsed, the same way picking its cell would.
 * On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: the layout below depends on how wide the words are.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthPickerShortcutTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<ShellAction>()

    private fun browsing(current: String, selected: String, year: Int) = ShellState(
        currentMonth = YearMonth.parse(current),
        selectedMonth = YearMonth.parse(selected),
        monthPickerOpen = true,
        pickerYear = year,
        pendingEntry = null
    )

    private var state by mutableStateOf(browsing("2026-10", "2023-05", 2021))

    /** The picker's content alone, one dp drawn as one pixel, in a sheet of this width. */
    private fun showPicker(width: Int = 360, fontScale: Float = 1f) = rule.showSello(
        Look(fontScale = fontScale, window = DpSize(width.dp, 700.dp), density = 1f)
    ) {
        MonthPicker(state, monthNames(), { actions += it })
    }

    private val shortcut get() = rule.onNodeWithTag(MONTH_PICKER_CURRENT_TAG)

    private fun spoken(): String? = shortcut.fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.ContentDescription)?.single()

    @Test
    fun fromAnyYearBeingBrowsedOnePressPicksTheCurrentMonth() {
        // Looking at May 2023, with the picker turned back to 2021.
        showPicker()

        shortcut.assertHasClickAction().assertIsEnabled().performClick()

        assertEquals(listOf<ShellAction>(ShellAction.PickMonth(YearMonth.of(2026, 10))), actions)
    }

    @Test
    fun itReadsMesActualAndSaysWhereItGoes() {
        showPicker()

        rule.onNodeWithText("Mes actual").assertExists()
        assertEquals("Ir al mes actual: Octubre 2026", spoken())
    }

    @Test
    fun itIsThereAndWorksWhenTheCurrentMonthIsAlreadyTheOneShown() {
        state = browsing("2026-10", "2026-10", 2026)
        showPicker()

        shortcut.assertIsEnabled().performClick()

        assertEquals(listOf<ShellAction>(ShellAction.PickMonth(YearMonth.of(2026, 10))), actions)
    }

    @Test
    fun whenTheMonthTurnsWhileThePickerIsOpenItGoesToTheNewMonth() {
        showPicker()
        val before = spoken()

        state = browsing("2026-11", "2023-05", 2021)
        rule.waitForIdle()
        shortcut.performClick()

        assertEquals("Ir al mes actual: Octubre 2026", before)
        assertEquals("Ir al mes actual: Noviembre 2026", spoken())
        assertEquals(listOf<ShellAction>(ShellAction.PickMonth(YearMonth.of(2026, 11))), actions)
    }

    @Test
    fun itSitsAtTheRightOfTheTitleOnTheSameLine() {
        showPicker()
        val title = rule.onNodeWithText("Elige un mes").fetchSemanticsNode().boundsInRoot
        val button = shortcut.fetchSemanticsNode().boundsInRoot

        assertTrue("$title $button", button.left >= title.right)
        assertTrue("$title $button", button.top < title.bottom && button.bottom > title.top)
        // What a finger can hit is at least the smallest size it can be asked to.
        val touch = shortcut.fetchSemanticsNode().touchBoundsInRoot
        assertTrue("$touch", touch.height >= 48f && touch.width >= 48f)
    }

    @Test
    fun atTwiceTheFontSizeItDropsBelowTheTitleWithItsTextWhole() {
        showPicker(width = 320, fontScale = 2f)
        val title = rule.onNodeWithText("Elige un mes").fetchSemanticsNode().boundsInRoot
        val button = shortcut.fetchSemanticsNode().boundsInRoot
        val label = rule.onNodeWithText("Mes actual", useUnmergedTree = true).fetchSemanticsNode()

        assertTrue("$title $button", button.top >= title.bottom)
        assertTrue("$button", button.left >= 0f && button.right <= 320f)
        // One line of text, not cut: as tall as one line and no taller.
        val lines = label.config.getOrNull(SemanticsProperties.Text)!!.single().text.lines().size
        assertEquals(1, lines)
        assertTrue("$label", label.size.width <= 320)
        shortcut.performClick()
        assertEquals(listOf<ShellAction>(ShellAction.PickMonth(YearMonth.of(2026, 10))), actions)
    }

    @Test
    fun theSidePanelOfAWideWindowHasItToo() {
        rule.showSello(Look(window = DpSize(1000.dp, 700.dp), density = 1f)) {
            ShellScreen(state, monthNames(), { actions += it }) {}
        }

        rule.onNodeWithTag(SCAFFOLD_SECONDARY_TAG).assertExists()
        shortcut.performClick()

        assertEquals(listOf<ShellAction>(ShellAction.PickMonth(YearMonth.of(2026, 10))), actions)
    }
}
