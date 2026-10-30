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
 * What the month's income looks like, without and with the suggestion to assign what is
 * unassigned, and as last read after a refresh that failed, kept as committed images
 * (ADR 0022): Cobalto light, Cobalto dark at twice the font size, and Violeta light. See
 * ExpenseEntryScreenshotTest.
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

    /**
     * [name] is the baseline's name; [assign] is the suggestion drawn, if any; [stale] draws
     * the figures as last read, after a refresh that failed.
     */
    private fun capture(
        look: Look,
        name: String,
        assign: AssignSuggestion?,
        stale: Boolean = false
    ) {
        rule.showSello(look) {
            IncomeHistoryScreen(
                state.copy(stale = stale),
                split,
                IncomePlanState.None,
                assign,
                today,
                {},
                {},
                {},
                {},
                PaddingValues(12.dp)
            )
        }
        rule.captureStill("src/test/screenshots/$name-${look.label}.png")
    }

    private val suggestion = AssignSuggestion(pesos("50.000"), "50000")

    @Test
    fun cobaltoLight() = capture(Look(), "income-history", null)

    @Test
    fun cobaltoDark() = capture(Look(dark = true), "income-history", null)

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta), "income-history", null)

    @Test
    fun suggestionCobaltoLight() = capture(Look(), "income-history-suggestion", suggestion)

    @Test
    fun suggestionCobaltoDark() =
        capture(Look(dark = true), "income-history-suggestion", suggestion)

    @Test
    fun suggestionVioletaLight() =
        capture(Look(ink = SelloInk.Violeta), "income-history-suggestion", suggestion)

    @Test
    fun staleCobaltoLight() = capture(Look(), "income-history-stale", null, stale = true)

    @Test
    fun staleCobaltoDark() = capture(Look(dark = true), "income-history-stale", null, stale = true)

    @Test
    fun staleVioletaLight() =
        capture(Look(ink = SelloInk.Violeta), "income-history-stale", null, stale = true)
}
