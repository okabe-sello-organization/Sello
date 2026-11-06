package com.software.sello.feature.resumen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.navigation.TitledFrame
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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

    private fun shot(name: String, shown: ResumenState, look: Look) {
        rule.showSello(look) {
            TitledFrame("Resumen") { padding ->
                ResumenScreen(shown, {}, {}, {}, {}, padding, {}, {})
            }
        }
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
}
