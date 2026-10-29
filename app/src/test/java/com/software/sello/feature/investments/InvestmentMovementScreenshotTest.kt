package com.software.sello.feature.investments

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.software.sello.Look
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What Anotar movimiento looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+xhdpi")
class InvestmentMovementScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 25)

    private fun pesos(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

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
            InvestmentMovementScreen(aporte) {}
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/investment-movement-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDarkAtTwiceTheFontSize() = capture(Look(dark = true, fontScale = 2f))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
