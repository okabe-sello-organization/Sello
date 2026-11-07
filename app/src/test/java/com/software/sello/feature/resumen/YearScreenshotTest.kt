package com.software.sello.feature.resumen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.YearRecords
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.month.monthNames
import com.software.sello.revealed
import com.software.sello.showSello
import com.software.sello.testing.testDateLabels
import com.software.sello.testing.testMoney
import com.software.sello.testing.testMonthNames
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** What Tu año looks like in each state, framed as ResumenDestinations.kt frames it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class YearScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val render = YearRenderModel(testMoney(), testMonthNames(), testDateLabels())
    private val ready = render.render(2026, ReadState.Ready(yearSnapshot())) as YearState.Ready

    private fun shot(name: String, shown: YearState, look: Look, reveal: String? = null) {
        rule.showSello(look) {
            ShellScreen(
                octoberShell,
                monthNames(),
                {},
                onBack = {},
                titleOnly = "Tu año",
                pastMonthRibbon = false,
                entryDock = false
            ) { padding -> YearScreen(shown, {}, padding) }
        }
        if (reveal != null) rule.onNodeWithTag(reveal).revealed()
        rule.captureStill("src/test/screenshots/year-$name-${look.label}.png")
    }

    @Test fun readyLight() = shot("ready", ready, Look())

    @Test fun readyDark() = shot("ready", ready, Look(dark = true))

    @Test fun readyViolet() = shot("ready", ready, Look(ink = SelloInk.Violeta))

    @Test fun categoriesLight() = shot("categories", ready, Look(), "year.categories")

    @Test fun categoriesDark() = shot("categories", ready, Look(dark = true), "year.categories")

    private val selected = ready.copy(selectedMonth = "2026-03")

    @Test fun selectedLight() = shot("selected", selected, Look(), "year.months")

    @Test fun selectedDark() = shot("selected", selected, Look(dark = true), "year.months")

    /** 2025, finished, with nothing recorded the year before. */
    private val closed = render.render(
        2025,
        ReadState.Ready(yearSnapshot(year = 2025, firstYear = 2025, comparison = false))
    )

    @Test fun closedLight() = shot("closed", closed, Look())

    @Test fun closedDark() = shot("closed", closed, Look(dark = true))

    @Test fun loadingLight() = shot("loading", YearState.Loading(2026), Look())

    @Test fun loadingDark() = shot("loading", YearState.Loading(2026), Look(dark = true))

    @Test fun failedLight() = shot("failed", YearState.Failed(2026), Look())

    @Test fun failedDark() = shot("failed", YearState.Failed(2026), Look(dark = true))

    @Test fun staleLight() = shot("stale", ready.copy(stale = true), Look())

    @Test fun staleDark() = shot("stale", ready.copy(stale = true), Look(dark = true))

    private val empty = render.render(2026, ReadState.Ready(yearSnapshot(empty = true)))

    @Test fun emptyLight() = shot("empty", empty, Look())

    @Test fun emptyDark() = shot("empty", empty, Look(dark = true))

    @Test fun recordsLight() = shot("records", ready, Look(), "year.records")

    @Test fun recordsDark() = shot("records", ready, Look(dark = true), "year.records")

    private val fewMonths = render.render(
        2026,
        ReadState.Ready(yearSnapshot(records = YearRecords.TooFew))
    )

    @Test fun recordsTooFewLight() = shot("records-too-few", fewMonths, Look(), "year.records")

    @Test fun recordsTooFewDark() = shot(
        "records-too-few",
        fewMonths,
        Look(dark = true),
        "year.records"
    )
}
