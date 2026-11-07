package com.software.sello.feature.resumen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.navigation.DayByDayBarAction
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellState
import com.software.sello.navigation.ShellTab
import com.software.sello.navigation.appTabs
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.carry.CarryFigures
import com.software.sello.presentation.month.monthNames
import com.software.sello.revealed
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What Resumen looks like, kept as committed images (ADR 0022). The host
 * gate compares each capture with its baseline in `src/test/screenshots/`; a visual change
 * re-records them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResumenScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val money = testMoney()
    private val state = resumenExample(money)

    private fun capture(look: Look) {
        rule.showSello(look) {
            ResumenScreen(state, {}, {}, {}, {}, PaddingValues(), {}, {})
        }
        rule.captureStill("src/test/screenshots/resumen-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    private fun shot(
        name: String,
        shown: ResumenState,
        look: Look,
        reveal: String? = null,
        shell: ShellState = octoberShell
    ) {
        rule.showSello(look) {
            // As ResumenDestinations.kt frames the tab: the month bar with its magnifier, no
            // Anotar.
            ShellScreen(
                shell,
                monthNames(),
                {},
                navigation = appTabs(ShellTab.Resumen),
                entryDock = false,
                actions = { DayByDayBarAction {} }
            ) { padding ->
                ResumenScreen(shown, {}, {}, {}, {}, padding, {}, {})
            }
        }
        if (reveal != null) rule.onNodeWithTag(reveal).revealed()
        rule.captureStill("src/test/screenshots/resumen-$name-${look.label}.png")
    }

    private val ready = state as ResumenState.Ready

    @Test fun loadingLight() = shot("loading", ResumenState.Loading, Look())

    @Test fun loadingDark() = shot("loading", ResumenState.Loading, Look(dark = true))

    @Test fun failedLight() = shot("failed", ResumenState.Failed, Look())

    @Test fun failedDark() = shot("failed", ResumenState.Failed, Look(dark = true))

    @Test fun staleLight() = shot("stale", ready.copy(stale = true), Look())

    @Test fun staleDark() = shot("stale", ready.copy(stale = true), Look(dark = true))

    @Test fun emptyLight() = shot("empty", ready.copy(empty = true), Look())

    @Test fun emptyDark() = shot("empty", ready.copy(empty = true), Look(dark = true))

    @Test fun firstMonthLight() = shot(
        "first-month",
        ready.copy(insufficientHistory = true),
        Look()
    )

    @Test fun firstMonthDark() =
        shot("first-month", ready.copy(insufficientHistory = true), Look(dark = true))

    @Test fun noIncomeLight() =
        shot("no-income", ready.copy(incomeSlip = ResumenIncome(0, 0f, false)), Look())

    @Test fun noIncomeDark() =
        shot("no-income", ready.copy(incomeSlip = ResumenIncome(0, 0f, false)), Look(dark = true))

    @Test fun overspentLight() =
        shot("overspent", ready.copy(incomeSlip = ResumenIncome(2, 1f, true)), Look())

    @Test fun overspentDark() =
        shot("overspent", ready.copy(incomeSlip = ResumenIncome(2, 1f, true)), Look(dark = true))

    // ---------- A closed month, a selected column, carry and the suggestion ----------

    private fun pesos(amount: Long) = money.format(Money.cop(amount))

    /** September seen from October: 35.000 spent against August's 40.000, whole months. */
    private val september = ready.copy(
        month = "2026-09",
        hero = ResumenHero(
            inProgress = false,
            monthName = "septiembre",
            spent = pesos(35_000),
            throughDay = 30,
            comparison = ResumenComparison(
                previousName = "agosto",
                difference = pesos(5_000),
                direction = SpendingDirection.Less,
                // 5.000 / 40.000 = 12,5 %.
                percent = "12,5",
                throughDay = null,
                previousThroughDay = null,
                bars = listOf(
                    ComparisonBar("Septiembre", pesos(35_000), 35f / 40f, true),
                    ComparisonBar("Agosto", pesos(40_000), 1f, false)
                )
            )
        ),
        asOf = "30 de septiembre"
    )

    private val septemberShell = octoberShell.copy(selectedMonth = YearMonth.of(2026, 9))

    @Test
    fun closedLight() = shot("closed", september, Look(), shell = septemberShell)

    @Test
    fun closedDark() = shot("closed", september, Look(dark = true), shell = septemberShell)

    private val historySelected = ready.copy(selectedHistory = "2026-09")

    @Test
    fun historySelectedLight() = shot("history-selected", historySelected, Look(), HISTORY_IN_VIEW)

    @Test
    fun historySelectedDark() =
        shot("history-selected", historySelected, Look(dark = true), HISTORY_IN_VIEW)

    private val weekdaySelected = ready.copy(selectedWeekday = "SATURDAY")

    @Test
    fun weekdaySelectedLight() = shot("weekday-selected", weekdaySelected, Look(), WEEKDAYS_IN_VIEW)

    @Test
    fun weekdaySelectedDark() =
        shot("weekday-selected", weekdaySelected, Look(dark = true), WEEKDAYS_IN_VIEW)

    /** October carries +20.000 from September: base 100.000, month limit 120.000. */
    private val carried = ready.copy(
        monthLimit = ResumenLimit(120_000, pesos(120_000), carried = true),
        carry = CarryFigures(
            base = pesos(100_000),
            carry = money.format(Money.cop(20_000), signed = true),
            carryMagnitude = pesos(20_000),
            direction = CarryDirection.Surplus,
            capacity = pesos(120_000),
            spent = pesos(30_000),
            chainStart = YearMonth.of(2026, 9),
            source = YearMonth.of(2026, 9)
        ),
        selectedHistory = "2026-10"
    )

    @Test fun carryLight() = shot("carry", carried, Look(), HISTORY_IN_VIEW)

    @Test
    fun carryDark() = shot("carry", carried, Look(dark = true), HISTORY_IN_VIEW)

    @Test fun suggestionLight() = shot("suggestion", ready, Look(), "resumen.suggestion")

    @Test
    fun suggestionDark() = shot("suggestion", ready, Look(dark = true), "resumen.suggestion")

    /** A dismissal that was not saved (#377): said under the suggestion, with Reintentar. */
    private val dismissFailed = ready.copy(dismissal = SuggestionDismissal.Failed)

    @Test
    fun suggestionDismissFailedLight() =
        shot("suggestion-dismiss-failed", dismissFailed, Look(), RESUMEN_DISMISS_FAILED_TAG)

    @Test
    fun suggestionDismissFailedDark() = shot(
        "suggestion-dismiss-failed",
        dismissFailed,
        Look(dark = true),
        RESUMEN_DISMISS_FAILED_TAG
    )

    /** Landscape (891 × 412): the summary slips left, the charts right. */
    @Test
    @Config(qualifiers = "w891dp-h412dp-mdpi")
    fun twoPanesLight() = shot("two-panes", ready, Look())

    @Test
    @Config(qualifiers = "w891dp-h412dp-mdpi")
    fun twoPanesDark() = shot("two-panes", ready, Look(dark = true))

    private companion object {
        /** The slip after Mes a mes: scrolled to it, the whole chart and its label show. */
        const val HISTORY_IN_VIEW = "resumen.weekdays.answer"

        /** The slip after the weekdays: scrolled to it, their chart shows whole. */
        const val WEEKDAYS_IN_VIEW = "resumen.suggestion"
    }
}
