package com.software.sello.feature.resumen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.software.sello.Look
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.Currency
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.showSello
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Resumen looks like, kept as committed images (ADR 0022). The host
 * gate compares each capture with its baseline in `src/test/screenshots/`; a visual change
 * re-records them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResumenScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })
    private val state = resumenExample(money)

    private fun capture(look: Look) {
        rule.showSello(look) {
            ResumenScreen(state, {}, {}, {}, {}, PaddingValues(), {}, {})
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/resumen-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDarkAtTwiceTheFontSize() = capture(Look(dark = true, fontScale = 2f))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
