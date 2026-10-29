package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.software.sello.Look
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Ajustar presupuesto looks like, kept as committed images (ADR 0022). The host
 * gate compares each capture with its baseline in `src/test/screenshots/`; a visual change
 * re-records them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdjustBudgetScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val october = YearMonth.of(2026, 10)

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private val cafe = AdjustRow(
        id = "cafe",
        name = "Café",
        iconKey = "local_cafe",
        spent = plain("90.000"),
        limit = plain("50.000"),
        remaining = minus("40.000"),
        overBy = plain("40.000"),
        fraction = 1f,
        over = true,
        archived = false
    )

    private val viejo = cafe.copy(
        id = "viejo",
        name = "Viejo",
        spent = plain("0"),
        limit = plain("30.000"),
        remaining = plain("30.000"),
        overBy = null,
        fraction = 0f,
        over = false,
        archived = true
    )

    private val regalos = cafe.copy(
        id = "regalos",
        name = "Regalos",
        spent = plain("10.000"),
        limit = null,
        remaining = null,
        overBy = null,
        fraction = null,
        over = false,
        unlimited = true
    )

    /** October 80.000 over; Café 90.000 of 50.000. */
    private val ready = AdjustBudgetState.Ready(
        month = october,
        summary = ReciboSummary(
            label = ReciboAnswerLabel.Remaining,
            amount = minus("80.000"),
            stamp = ReciboStamp.Over,
            adjustable = true
        ),
        limit = plain("1.000.000"),
        spent = plain("1.080.000"),
        limited = listOf(cafe),
        open = listOf(regalos),
        stale = false,
        archived = listOf(viejo)
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            AdjustBudgetScreen(
                state = ready,
                sheet = LimitSheetState(october),
                onAction = {},
                onSheet = {},
                onEditCategory = {},
                contentPadding = PaddingValues(12.dp)
            )
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/adjust-budget-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDarkAtTwiceTheFontSize() = capture(Look(dark = true, fontScale = 2f))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
