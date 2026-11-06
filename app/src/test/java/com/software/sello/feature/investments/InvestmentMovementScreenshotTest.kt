package com.software.sello.feature.investments

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Anotar movimiento looks like, kept as committed images (ADR 0022)
 * in all four themes: Cobalto and Violeta, light and dark. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvestmentMovementScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 25)

    private val aporte = InvestmentMovementState(
        loading = false,
        amount = pesos("500.000"),
        investments = listOf(
            InvestmentChoice("p", "Parqueadero", "home", usd = false),
            InvestmentChoice("e", "Ecopetrol", "trending_up", usd = false),
            InvestmentChoice("u", "Acciones EE. UU.", "trending_up", usd = true)
        ),
        investmentId = "e",
        fact = InvestmentMovementFact.Contributed(pesos("3.000.000"), "Ecopetrol"),
        date = today,
        today = today
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            InvestmentMovementScreen(aporte, severalOffered = true) {}
        }
        rule.captureStill("src/test/screenshots/investment-movement-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    @Test
    fun violetaDark() = capture(Look(ink = SelloInk.Violeta, dark = true))

    private fun shot(name: String, state: InvestmentMovementState, look: Look) {
        rule.showSello(look) { InvestmentMovementScreen(state) {} }
        rule.captureStill("src/test/screenshots/investment-movement-$name-${look.label}.png")
    }

    private val loading = InvestmentMovementState(date = today, today = today)

    @Test fun loadingLight() = shot("loading", loading, Look())

    @Test fun loadingDark() = shot("loading", loading, Look(dark = true))

    private val failed = loading.copy(loading = false, loadFailed = true)

    @Test fun failedLight() = shot("failed", failed, Look())

    @Test fun failedDark() = shot("failed", failed, Look(dark = true))

    private val none = loading.copy(loading = false)

    @Test fun noInvestmentsLight() = shot("no-investments", none, Look())

    @Test fun noInvestmentsDark() = shot("no-investments", none, Look(dark = true))

    /** A retiro of more than the investment is worth. */
    private val over = aporte.copy(
        kind = InvestmentMovementKind.Withdrawal,
        amount = pesos("5.000.000"),
        fact = null,
        overValue = pesos("3.200.000")
    )

    @Test fun overLight() = shot("over", over, Look())

    @Test fun overDark() = shot("over", over, Look(dark = true))

    private val valuation = aporte.copy(
        kind = InvestmentMovementKind.Valuation,
        amount = pesos("3.200.000"),
        fact = null
    )

    @Test fun valuationLight() = shot("valuation", valuation, Look())

    @Test fun valuationDark() = shot("valuation", valuation, Look(dark = true))

    @Test fun busyLight() = shot("busy", aporte.copy(busy = true), Look())

    @Test fun busyDark() = shot("busy", aporte.copy(busy = true), Look(dark = true))

    private val unknown = aporte.copy(error = InvestmentMovementError.Unknown)

    @Test fun unknownLight() = shot("unknown", unknown, Look())

    @Test fun unknownDark() = shot("unknown", unknown, Look(dark = true))

    private val refused = aporte.copy(error = InvestmentMovementError.NotSaved)

    @Test fun refusedLight() = shot("refused", refused, Look())

    @Test fun refusedDark() = shot("refused", refused, Look(dark = true))

    /** Correcting a saved aporte: Borrar in the bar. */
    private val correcting = aporte.copy(editing = true, changed = true)

    @Test fun correctingLight() = shot("correcting", correcting, Look())

    @Test fun correctingDark() = shot("correcting", correcting, Look(dark = true))

    private val composing = aporte.copy(
        composerOpen = true,
        composerDraft = "Prima",
        composerLength = 5
    )

    @Test fun noteLight() = shot("note", composing, Look())

    @Test fun noteDark() = shot("note", composing, Look(dark = true))

    @Test fun datePickerLight() = shot("date-picker", aporte.copy(datePickerOpen = true), Look())

    @Test
    fun datePickerDark() =
        shot("date-picker", aporte.copy(datePickerOpen = true), Look(dark = true))

    @Test fun discardLight() = shot("discard", aporte.copy(confirmDiscard = true), Look())

    @Test
    fun discardDark() = shot("discard", aporte.copy(confirmDiscard = true), Look(dark = true))
}
