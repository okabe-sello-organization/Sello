package com.software.sello.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.recibo.ReciboAnswerLabel
import com.software.sello.feature.recibo.ReciboStamp
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The image to share and its preview sheet, kept as committed images (ADR 0022). The
 * image is always the light paper in the person's ink, the app in dark too.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareMonthScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val october = YearMonth.of(2026, 10)

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun line(
        name: String,
        icon: String,
        spent: String,
        limit: String?,
        fraction: Float?,
        over: Boolean = false
    ) = MonthShareCategory(name, icon, plain(spent), limit?.let(::plain), over, fraction, false)

    /** The mock's October: one category over its limit and one without a limit. */
    private val image = MonthShareImage(
        month = october,
        asOf = LocalDate.of(2026, 10, 28),
        closed = false,
        label = ReciboAnswerLabel.Remaining,
        amount = plain("63.100"),
        stamp = ReciboStamp.OnTrack,
        spent = plain("2.416.900"),
        limit = plain("2.480.000"),
        categories = listOf(
            line("Alimentación", "restaurant", "812.400", "900.000", 0.9f),
            line("Transporte", "directions_bus", "155.000", "200.000", 0.78f),
            line("Entretenimiento", "theaters", "221.500", "200.000", 1f, over = true),
            line("Café", "local_cafe", "48.000", "80.000", 0.6f),
            line("Hogar", "home", "1.100.000", "1.100.000", 1f),
            line("Arte", "redeem", "80.000", null, null)
        )
    )

    /** September, finished 85.000 over its limit. */
    private val september = MonthShareImage(
        month = YearMonth.of(2026, 9),
        asOf = LocalDate.of(2026, 10, 28),
        closed = true,
        label = ReciboAnswerLabel.Remaining,
        amount = MoneyTextValue(MoneySign.Minus, "", "85.000", "menos 85.000 pesos"),
        stamp = ReciboStamp.OverLimit,
        spent = plain("2.565.000"),
        limit = plain("2.480.000"),
        categories = listOf(
            line("Alimentación", "restaurant", "985.000", "900.000", 1f, over = true),
            line("Hogar", "home", "1.100.000", "1.100.000", 1f),
            line("Café", "local_cafe", "80.000", "80.000", 1f),
            line("Transporte", "directions_bus", "200.000", "200.000", 1f),
            line("Entretenimiento", "theaters", "200.000", "200.000", 1f)
        )
    )

    private fun captureImage(look: Look, shown: MonthShareImage = image, name: String) {
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk).padding(16.dp)) {
                MonthShareCard(shown, monthNames())
            }
        }
        rule.captureStill("src/test/screenshots/share-image-$name.png")
    }

    @Test
    fun imageCobalto() = captureImage(Look(), name = "cobalto")

    @Test
    fun imageVioleta() = captureImage(Look(ink = SelloInk.Violeta), name = "violeta")

    @Test
    fun imageTuya() = captureImage(Look(ink = HueInk(150)), name = "tuya150")

    /** The app in dark: the image keeps the light paper. */
    @Test
    fun imageInADarkApp() = captureImage(Look(dark = true), name = "cobalto-app-dark")

    @Test
    fun imageOfAPastMonth() = captureImage(Look(), september, "past-month")

    private fun captureSheet(name: String, state: ShareMonthState.Open, dark: Boolean = false) {
        rule.showSello(Look(dark = dark)) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                ShareMonthSheet(state, onAction = {})
            }
        }
        rule.captureStill("src/test/screenshots/share-sheet-$name-${if (dark) "oscuro" else "claro"}.png")
    }

    private val ready = ShareMonthState.Open(october, SharePreview.Ready(image))

    @Test
    fun sheetReadyClaro() = captureSheet("ready", ready)

    @Test
    fun sheetReadyOscuro() = captureSheet("ready", ready, dark = true)

    @Test
    fun sheetBusy() = captureSheet("busy", ready.copy(step = ShareStep.Placing))

    @Test
    fun sheetFailed() = captureSheet("failed", ready.copy(step = ShareStep.Failed))

    @Test
    fun sheetWaiting() = captureSheet("waiting", ShareMonthState.Open(october, SharePreview.Waiting))

    @Test
    fun sheetLoading() = captureSheet("loading", ShareMonthState.Open(october, SharePreview.Loading))

    @Test
    fun sheetReadFailed() =
        captureSheet("read-failed", ShareMonthState.Open(october, SharePreview.Failed))

    @Test
    fun sheetEmpty() = captureSheet("empty", ShareMonthState.Open(october, SharePreview.Empty))
}
