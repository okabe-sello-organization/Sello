package com.software.sello.feature.income

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the income form looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IncomeFormScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 13)

    /** An amount typed and a source chosen: the form ready to record. */
    private val ready = IncomeFormState(
        loading = false,
        amount = MoneyTextValue(MoneySign.None, "", "5.000.000", "5.000.000 pesos"),
        sourceKeys = listOf("salary", "freelance", "other"),
        sourceKey = "salary",
        date = today,
        today = today,
        missing = null
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            IncomeFormScreen(ready, {})
        }
        rule.captureStill("src/test/screenshots/income-form-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    /** Opened where Anotar varios is reachable: the Varios pill in the bar. */
    private fun captureVarios(look: Look, label: String) {
        rule.showSello(look) { IncomeFormScreen(ready, {}, severalOffered = true) }
        rule.captureStill("src/test/screenshots/income-form-varios-$label.png")
    }

    @Test
    fun variosClaro() = captureVarios(Look(), "claro")

    @Test
    fun variosOscuro() = captureVarios(Look(dark = true), "oscuro")
}
