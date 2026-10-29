package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.showSello
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Recibo looks like in an overspent month, kept as committed images (ADR 0022). The host
 * gate compares each capture with its baseline in `src/test/screenshots/`; a visual change
 * re-records them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReciboScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private fun category(
        id: String,
        name: String,
        headline: ReciboHeadline,
        amount: MoneyTextValue,
        stamp: ReciboStamp,
        limit: String?,
        fraction: Float?,
        over: Boolean = false
    ) = ReciboCategory(
        id = id,
        name = name,
        iconKey = "restaurant",
        archived = false,
        figure = BudgetFigure(headline, amount, stamp),
        cellAmount = amount,
        spent = plain("1"),
        limit = limit?.let { plain(it) },
        fraction = fraction,
        over = over,
        expenseCount = 8
    )

    /** An overspent month: the answer in the red, a category in each of the main states. */
    private val overspent = ReciboState.Month(
        ReciboSummary(
            ReciboAnswerLabel.Remaining,
            minus("43.700"),
            ReciboStamp.Over,
            perDay = ReciboPerDay(plain("0"), 9),
            adjustable = true
        ),
        listOf(
            category(
                id = "a",
                name = "Alimentación",
                headline = ReciboHeadline.Remaining,
                amount = plain("287.600"),
                stamp = ReciboStamp.OnTrack,
                limit = "900.000",
                fraction = 0.68f
            ),
            category(
                id = "b",
                name = "Transporte",
                headline = ReciboHeadline.Remaining,
                amount = plain("55.000"),
                stamp = ReciboStamp.AtLimit,
                limit = "200.000",
                fraction = 1f
            ),
            category(
                id = "c",
                name = "Entretenimiento",
                headline = ReciboHeadline.Overspent,
                amount = minus("21.500"),
                stamp = ReciboStamp.Over,
                limit = "100.000",
                fraction = 1f,
                over = true
            ),
            category(
                id = "d",
                name = "Arte",
                headline = ReciboHeadline.Spent,
                amount = plain("80.000"),
                stamp = ReciboStamp.NoLimit,
                limit = null,
                fraction = null
            )
        ),
        stale = false
    )

    private fun capture(look: Look) {
        rule.showSello(look) { ReciboScreen(overspent, {}, PaddingValues(12.dp)) }
        rule.captureStill("src/test/screenshots/recibo-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDarkAtTwiceTheFontSize() = capture(Look(dark = true, fontScale = 2f))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
