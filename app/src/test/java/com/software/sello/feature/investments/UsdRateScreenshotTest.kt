package com.software.sello.feature.investments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.software.sello.Look
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.presentation.date.dateLabels
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the dollar rate looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+xhdpi")
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
        rule.onRoot().captureRoboImage("src/test/screenshots/usd-rate-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDarkAtTwiceTheFontSize() = capture(Look(dark = true, fontScale = 2f))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
