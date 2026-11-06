package com.software.sello.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ajustar presupuesto and Pagos previstos show their own month, not the shared one, and
 * offer no month picker: picking a month there would change the title but not the figures
 * (#387). On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
class OwnMonthTitleTest {
    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf(showing(current = "2026-10", selected = "2026-09"))

    private fun showing(current: String, selected: String) = ShellState(
        currentMonth = YearMonth.parse(current),
        selectedMonth = YearMonth.parse(selected),
        monthPickerOpen = false,
        pickerYear = YearMonth.parse(selected).year,
        pendingEntry = null
    )

    private fun noSwitcher() = rule.onAllNodesWithTag(MONTH_SWITCHER_TAG).assertCountEquals(0)

    @Test
    fun ajustarShowsTheMonthItWasOpenedOnWithoutAPicker() {
        rule.showSello {
            AdjustBudgetFrame(state, YearMonth.parse("2026-08"), {}, {}, null) {}
        }

        rule.onNodeWithText("Ajustar presupuesto").assertExists()
        rule.onNodeWithText("Agosto 2026").assertExists()
        rule.onAllNodesWithText("Septiembre 2026", substring = true).assertCountEquals(0)
        noSwitcher()
    }

    @Test
    fun ajustarKeepsItsMonthWhenTheSharedMonthChanges() {
        rule.showSello {
            AdjustBudgetFrame(state, YearMonth.parse("2026-08"), {}, {}, null) {}
        }

        state = showing(current = "2026-10", selected = "2026-10")
        rule.waitForIdle()

        rule.onNodeWithText("Agosto 2026").assertExists()
        rule.onAllNodesWithText("Octubre 2026", substring = true).assertCountEquals(0)
    }

    @Test
    fun pagosPrevistosShowsTheCurrentMonthWithoutAPicker() {
        rule.showSello { UpcomingPaymentsFrame(state, {}, {}, null) {} }

        rule.onNodeWithText("Pagos previstos").assertExists()
        rule.onNodeWithText("Octubre 2026").assertExists()
        rule.onAllNodesWithText("Septiembre 2026", substring = true).assertCountEquals(0)
        noSwitcher()
    }

    @Test
    fun pagosPrevistosFollowsTodayIntoTheNextMonth() {
        rule.showSello { UpcomingPaymentsFrame(state, {}, {}, null) {} }

        // Midnight on 31 October.
        state = showing(current = "2026-11", selected = "2026-09")
        rule.waitForIdle()

        rule.onNodeWithText("Noviembre 2026").assertExists()
        rule.onAllNodesWithText("Octubre 2026", substring = true).assertCountEquals(0)
    }
}
