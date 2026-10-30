package com.software.sello.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the frame looks like, kept as committed images (ADR 0022): the month bar, the dock
 * and the ribbon over a past month, and the month picker open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun state(pickerOpen: Boolean) = ShellState(
        currentMonth = YearMonth.of(2026, 10),
        selectedMonth = YearMonth.of(2026, 9),
        monthPickerOpen = pickerOpen,
        pickerYear = 2026,
        pendingEntry = null
    )

    private fun capture(look: Look, name: String, pickerOpen: Boolean) {
        rule.showSello(look) {
            ShellScreen(
                state(pickerOpen),
                monthNames(),
                {},
                navigation = SelloNavigation(
                    listOf(
                        SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                        SelloDestination("resumen", "Resumen", SelloIcon.Insights)
                    ),
                    "recibo"
                ) {}
            ) { padding ->
                Text("contenido", Modifier.padding(padding), color = SelloTheme.colors.ink)
            }
        }
        rule.captureStill("src/test/screenshots/shell-$name-${look.label}.png")
    }

    @Test
    fun pastMonthCobaltoLight() = capture(Look(), "past-month", pickerOpen = false)

    @Test
    fun pastMonthCobaltoDark() = capture(Look(dark = true), "past-month", pickerOpen = false)

    @Test
    fun pastMonthVioletaLight() =
        capture(Look(ink = SelloInk.Violeta), "past-month", pickerOpen = false)

    @Test
    fun pickerCobaltoLight() = capture(Look(), "picker", pickerOpen = true)

    @Test
    fun pickerCobaltoDark() = capture(Look(dark = true), "picker", pickerOpen = true)

    @Test
    fun pickerVioletaLight() = capture(Look(ink = SelloInk.Violeta), "picker", pickerOpen = true)
}
