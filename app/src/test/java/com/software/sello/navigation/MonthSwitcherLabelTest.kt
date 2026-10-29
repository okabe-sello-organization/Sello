package com.software.sello.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a screen reader is told the month switcher is showing. "Mes actual" is said
 * only of the month the financial day is in; any other month is the selected one.
 * On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
class MonthSwitcherLabelTest {
    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf(showing(current = "2026-10", selected = "2026-10"))

    private fun showing(current: String, selected: String) = ShellState(
        currentMonth = YearMonth.parse(current),
        selectedMonth = YearMonth.parse(selected),
        monthPickerOpen = false,
        pickerYear = YearMonth.parse(selected).year,
        pendingEntry = null
    )

    private fun show() = rule.showSello { ShellScreen(state, monthNames(), {}) {} }

    private fun spoken(): List<String>? = rule.onNodeWithTag(MONTH_SWITCHER_TAG)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)

    @Test
    fun theMonthTheDayIsInIsCalledTheCurrentMonth() {
        show()

        assertEquals(listOf("Cambiar de mes. Mes actual: Octubre 2026"), spoken())
    }

    @Test
    fun anEarlierMonthIsCalledTheSelectedMonthNeverTheCurrentOne() {
        state = showing(current = "2026-10", selected = "2026-09")
        show()

        assertEquals(listOf("Cambiar de mes. Mes seleccionado: Septiembre 2026"), spoken())
    }

    @Test
    fun itHoldsAcrossAYearBoundaryAndForAMonthYearsBack() {
        state = showing(current = "2027-01", selected = "2026-12")
        show()
        val lastDecember = spoken()

        state = showing(current = "2027-01", selected = "2024-02")
        rule.waitForIdle()

        assertEquals(listOf("Cambiar de mes. Mes seleccionado: Diciembre 2026"), lastDecember)
        assertEquals(listOf("Cambiar de mes. Mes seleccionado: Febrero 2024"), spoken())
    }

    @Test
    fun whenTheMonthTurnsTheMonthStillSelectedStopsBeingCalledCurrent() {
        show()
        val before = spoken()

        // Midnight on 31 October with October still selected.
        state = showing(current = "2026-11", selected = "2026-10")
        rule.waitForIdle()

        assertEquals(listOf("Cambiar de mes. Mes actual: Octubre 2026"), before)
        assertEquals(listOf("Cambiar de mes. Mes seleccionado: Octubre 2026"), spoken())
    }

    @Test
    fun whatIsSpokenAndWhatIsShownNameTheSameMonth() {
        state = showing(current = "2026-10", selected = "2026-09")
        var label: MonthBarLabel? = null
        rule.showSello {
            label = monthBarLabel(state, monthNames())
            ShellScreen(state, monthNames(), {}) {}
        }

        // The bar draws [month] large and [year] small; both come from the same month.
        assertEquals(MonthBarLabel("Septiembre", "2026", spoken()!!.single()), label)
        assertEquals(true, label!!.spoken.endsWith("${label!!.month} ${label!!.year}"))
    }
}
