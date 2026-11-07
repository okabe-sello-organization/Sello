package com.software.sello.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.showSello
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Gastos hormiga's form (SELLO-194), kept as committed images (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmallExpenseLineScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    /** The mock's: 15.000 typed over a line of 20.000. */
    private val typed = SmallExpenseLineState(
        loading = false,
        loadFailed = false,
        amount = "15.000",
        problem = null,
        current = "20.000"
    )

    private fun shot(name: String, state: SmallExpenseLineState, look: Look) {
        rule.showSello(look) { SmallExpenseLineScreen(state) {} }
        rule.captureStill("src/test/screenshots/small-line-$name-${look.label}.png")
    }

    @Test fun typedLight() = shot("typed", typed, Look())

    @Test fun typedDark() = shot("typed", typed, Look(dark = true))

    @Test fun emptyLight() = shot("empty", typed.copy(amount = null), Look())

    @Test
    fun belowLight() =
        shot("below", typed.copy(amount = "999", problem = LineProblem.BelowMinimum), Look())

    @Test
    fun aboveLight() =
        shot("above", typed.copy(amount = "150.000", problem = LineProblem.AboveMaximum), Look())

    @Test
    fun aboveDark() = shot(
        "above",
        typed.copy(amount = "150.000", problem = LineProblem.AboveMaximum),
        Look(dark = true)
    )

    @Test fun savingLight() = shot("saving", typed.copy(busy = true), Look())

    @Test fun savingDark() = shot("saving", typed.copy(busy = true), Look(dark = true))

    @Test fun notSavedLight() = shot("not-saved", typed.copy(notSaved = true), Look())

    @Test fun loadingLight() = shot("loading", typed.copy(loading = true), Look())

    @Test fun failedLight() = shot("failed", typed.copy(loadFailed = true, current = null), Look())
}
