package com.software.sello.feature.investments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.navigation.TitledFrame
import com.software.sello.presentation.date.dateLabels
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the dollar rate looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light; and Cambiar la tasa locked after an unknown
 * save. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UsdRateScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val ready = UsdRateState.Ready(
        current = RateModel("4.150,25", LocalDate.of(2026, 10, 20)),
        history = listOf(
            RateModel("4.010,00", LocalDate.of(2026, 10, 2)),
            RateModel("3.960,50", LocalDate.of(2025, 9, 15))
        ),
        today = LocalDate.of(2026, 10, 25)
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            UsdRateScreen(ready, {}, dateLabels(), PaddingValues(16.dp))
        }
        rule.captureStill("src/test/screenshots/usd-rate-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    /** Cambiar la tasa after an unknown save: the pad is locked, only asking again is offered. */
    private fun entryUnknownLocksThePad(look: Look) {
        val look = look
        val today = LocalDate.of(2026, 10, 25)
        rule.showSello(look) {
            UsdRateEntryScreen(
                UsdRateEntryState(
                    loading = false,
                    loadFailed = false,
                    rate = "4.000",
                    problem = null,
                    current = RateModel("4.150,25", LocalDate.of(2026, 10, 20)),
                    date = today,
                    today = today,
                    error = RateEntryError.Unknown
                ),
                {}
            )
        }
        rule.captureStill("src/test/screenshots/usd-rate-entry-unknown-${look.label}.png")
    }

    @Test
    fun entryUnknownLocksThePadLight() = entryUnknownLocksThePad(Look())

    @Test
    fun entryUnknownLocksThePadDark() = entryUnknownLocksThePad(Look(dark = true))

    @Test
    fun violetaDark() = capture(Look(ink = SelloInk.Violeta, dark = true))

    private fun shot(name: String, state: UsdRateState, look: Look) {
        rule.showSello(look) {
            if (state == UsdRateState.Loading) {
                TitledFrame("Dólar") { padding -> UsdRateScreen(state, {}, dateLabels(), padding) }
            } else {
                UsdRateScreen(state, {}, dateLabels(), PaddingValues(16.dp))
            }
        }
        rule.captureStill("src/test/screenshots/usd-rate-$name-${look.label}.png")
    }

    @Test fun loadingLight() = shot("loading", UsdRateState.Loading, Look())

    @Test fun loadingDark() = shot("loading", UsdRateState.Loading, Look(dark = true))

    @Test fun failedLight() = shot("failed", UsdRateState.Failed, Look())

    @Test fun failedDark() = shot("failed", UsdRateState.Failed, Look(dark = true))

    private val empty = ready.copy(current = null, history = emptyList())

    @Test fun emptyLight() = shot("empty", empty, Look())

    @Test fun emptyDark() = shot("empty", empty, Look(dark = true))

    @Test fun staleLight() = shot("stale", ready.copy(stale = true), Look())

    @Test fun staleDark() = shot("stale", ready.copy(stale = true), Look(dark = true))

    private val entry = UsdRateEntryState(
        loading = false,
        loadFailed = false,
        rate = "4.200",
        problem = null,
        current = RateModel("4.150,25", LocalDate.of(2026, 10, 20)),
        date = LocalDate.of(2026, 10, 25),
        today = LocalDate.of(2026, 10, 25)
    )

    private fun entryShot(name: String, state: UsdRateEntryState, look: Look) {
        rule.showSello(look) { UsdRateEntryScreen(state, {}) }
        rule.captureStill("src/test/screenshots/usd-rate-entry-$name-${look.label}.png")
    }

    @Test fun entryReadyLight() = entryShot("ready", entry, Look())

    @Test fun entryReadyDark() = entryShot("ready", entry, Look(dark = true))

    @Test fun entryBusyLight() = entryShot("busy", entry.copy(busy = true), Look())

    @Test fun entryBusyDark() = entryShot("busy", entry.copy(busy = true), Look(dark = true))

    /** Above 100.000 pesos a dollar: kept as typed, with why. */
    private val refused = entry.copy(rate = "150.000", problem = RateProblem.AboveMaximum)

    @Test fun entryRefusedLight() = entryShot("refused", refused, Look())

    @Test fun entryRefusedDark() = entryShot("refused", refused, Look(dark = true))

    private val notSaved = entry.copy(error = RateEntryError.NotSaved)

    @Test fun entryNotSavedLight() = entryShot("not-saved", notSaved, Look())

    @Test fun entryNotSavedDark() = entryShot("not-saved", notSaved, Look(dark = true))

    private val loadingEntry = entry.copy(loading = true, rate = null, current = null)

    @Test fun entryLoadingLight() = entryShot("loading", loadingEntry, Look())

    @Test fun entryLoadingDark() = entryShot("loading", loadingEntry, Look(dark = true))

    private val failedEntry = entry.copy(loadFailed = true, rate = null, current = null)

    @Test fun entryFailedLight() = entryShot("failed", failedEntry, Look())

    @Test fun entryFailedDark() = entryShot("failed", failedEntry, Look(dark = true))
}
