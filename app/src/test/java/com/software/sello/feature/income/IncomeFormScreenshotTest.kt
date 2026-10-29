package com.software.sello.feature.income

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
 * What the income form looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+xhdpi")
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
        rule.onRoot().captureRoboImage("src/test/screenshots/income-form-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDarkAtTwiceTheFontSize() = capture(Look(dark = true, fontScale = 2f))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
