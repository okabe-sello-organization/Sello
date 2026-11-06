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
 * (ADR 0022): Cobalto light, Cobalto dark and Violeta light. See ExpenseEntryScreenshotTest.
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
        stale: Boolean = false,
        onRecordSeveral: (() -> Unit)? = null
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
                PaddingValues(12.dp),
                onRecordSeveral
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

    /** Anotar varios at the foot of the list. */
    @Test
    fun variosClaro() = capture(Look(), "income-history-varios", null, onRecordSeveral = {})

    @Test
    fun variosOscuro() =
        capture(Look(dark = true), "income-history-varios", null, onRecordSeveral = {})

    private fun shot(
        name: String,
        look: Look,
        shown: IncomeHistoryState = state,
        shownSplit: IncomeSplit? = split,
        plan: IncomePlanState = IncomePlanState.None
    ) {
        rule.showSello(look) {
            IncomeHistoryScreen(
                shown, shownSplit, plan, null, today, {}, {}, {}, {}, PaddingValues(12.dp)
            )
        }
        rule.captureStill("src/test/screenshots/income-history-$name-${look.label}.png")
    }

    @Test fun loadingLight() = shot("loading", Look(), IncomeHistoryState.Loading, null)

    @Test fun loadingDark() = shot("loading", Look(dark = true), IncomeHistoryState.Loading, null)

    @Test fun failedLight() = shot("failed", Look(), IncomeHistoryState.Failed, null)

    @Test fun failedDark() = shot("failed", Look(dark = true), IncomeHistoryState.Failed, null)

    private val empty = state.copy(total = pesos("0"), lines = emptyList())

    @Test fun emptyLight() = shot("empty", Look(), empty, null)

    @Test fun emptyDark() = shot("empty", Look(dark = true), empty, null)

    /** The list read, the month's split not yet: the hero waits. */
    @Test fun heroPendingLight() = shot("hero-pending", Look(), shownSplit = null)

    @Test fun heroPendingDark() = shot("hero-pending", Look(dark = true), shownSplit = null)

    private val pageFailed = state.copy(more = true, pageFailed = true)

    @Test fun pageFailedLight() = shot("page-failed", Look(), pageFailed)

    @Test fun pageFailedDark() = shot("page-failed", Look(dark = true), pageFailed)

    /** Spent more than came in: the bar over its basis. */
    private val over = IncomeSplit(
        income = pesos("150.000"),
        allocated = pesos("180.000"),
        unallocated = pesos("30.000", minus = true),
        over = true,
        allocatedShare = 1f
    )

    @Test fun overLight() = shot("over", Look(), shownSplit = over)

    @Test fun overDark() = shot("over", Look(dark = true), shownSplit = over)

    /** The month's plan: what came in, what is still expected from the schedules. */
    private val plan = IncomePlanState.Shown(
        IncomePlanView(
            recorded = pesos("150.000"),
            expected = pesos("4.200.000"),
            planned = pesos("4.350.000"),
            unassigned = pesos("50.000"),
            over = false,
            items = listOf(
                ExpectedLine("s1", "salary", null, LocalDate.of(2026, 10, 30), pesos("4.200.000"))
            )
        )
    )

    @Test fun planLight() = shot("plan", Look(), plan = plan)

    @Test fun planDark() = shot("plan", Look(dark = true), plan = plan)

    @Test fun planFailedLight() = shot("plan-failed", Look(), plan = IncomePlanState.Failed)

    @Test
    fun planFailedDark() = shot("plan-failed", Look(dark = true), plan = IncomePlanState.Failed)
}
