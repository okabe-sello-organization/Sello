package com.software.sello.feature.income

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the month's income looks like with the suggestion to assign what is unassigned, kept as
 * committed images (ADR 0022): Cobalto light, Cobalto dark at twice the font size, and Violeta
 * light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IncomeHistoryScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)

    private val state = IncomeHistoryState.Ready(
        YearMonth.of(2026, 10),
        pesos("150.000"),
        listOf(IncomeLine("i1", pesos("150.000"), "salary", null, today, "Quincena")),
        false
    )

    private val split = IncomeSplit(
        income = pesos("150.000"),
        allocated = pesos("100.000"),
        unallocated = pesos("50.000"),
        over = false,
        allocatedShare = 2f / 3f
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            IncomeHistoryScreen(
                state,
                split,
                IncomePlanState.None,
                AssignSuggestion(pesos("50.000"), "50000"),
                today,
                {},
                {},
                {},
                {},
                PaddingValues(12.dp)
            )
        }
        rule.captureStill("src/test/screenshots/income-history-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
