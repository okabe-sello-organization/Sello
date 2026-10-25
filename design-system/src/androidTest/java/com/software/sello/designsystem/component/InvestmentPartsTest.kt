package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Inversiones parts SELLO-109 extends (docs/design/v2/gaps/inversiones.md). */
class InvestmentPartsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun PixelMap.count(color: Color, tolerance: Float = 0.06f): Int {
        var found = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = this[x, y]
                if (abs(pixel.red - color.red) < tolerance &&
                    abs(pixel.green - color.green) < tolerance &&
                    abs(pixel.blue - color.blue) < tolerance
                ) {
                    found++
                }
            }
        }
        return found
    }

    @Test
    fun theReturnsCommaIsNotAsWideAsADigit() {
        rule.inEveryScheme(content = {
            Column {
                PerformanceLine("+5,6 %", "+2.066.000 de ganancia", loss = false)
                PerformanceLine("+556 %", "+2.066.000 de ganancia", loss = false)
            }
        }) { scheme ->
            val comma = rule.onNodeWithText("+5,6 %").getUnclippedBoundsInRoot().width
            val digit = rule.onNodeWithText("+556 %").getUnclippedBoundsInRoot().width
            assertTrue("$scheme: $comma vs $digit", comma < digit - 3.dp)
        }
    }

    @Test
    fun anEvenReturnHasNeitherGainNorLossInk() {
        var gain by mutableStateOf(Color.Unspecified)
        var loss by mutableStateOf(Color.Unspecified)
        rule.inEveryScheme(content = {
            gain = SelloTheme.colors.semantic.gain
            loss = SelloTheme.colors.semantic.loss
            Box(Modifier.testTag("even")) {
                PerformanceLine("0,0 %", "sin ganancia ni pérdida", loss = false, even = true)
            }
        }) { scheme ->
            val pixels = rule.onNodeWithTag("even").captureToImage().toPixelMap()
            assertEquals(scheme, 0, pixels.count(gain, 0.03f))
            assertEquals(scheme, 0, pixels.count(loss, 0.03f))
        }
    }

    @Test
    fun pierdeKeepsTheValueInInkWhileTheStampIsRed() {
        var loss by mutableStateOf(Color.Unspecified)
        rule.inEveryScheme(content = {
            loss = SelloTheme.colors.semantic.loss
            Column {
                Box(Modifier.testTag("value")) {
                    AnswerSlip(
                        "Tus inversiones valen",
                        MoneyTextValue(MoneySign.None, "", "8.292.000", "8.292.000 pesos"),
                        "Pierde",
                        StampInk.Over,
                        lossAmount = false
                    )
                }
                Box(Modifier.testTag("overrun")) {
                    AnswerSlip(
                        "Restante del presupuesto",
                        MoneyTextValue(MoneySign.None, "", "8.292.000", "8.292.000 pesos"),
                        "Pierde",
                        StampInk.Over
                    )
                }
            }
        }) { scheme ->
            val value = rule.onNodeWithTag("value").captureToImage().toPixelMap().count(loss)
            val overrun = rule.onNodeWithTag("overrun").captureToImage().toPixelMap().count(loss)
            assertTrue("$scheme: stamp only $value, with the number $overrun", value * 2 < overrun)
            assertTrue("$scheme: the stamp is still red", value > 50)
        }
    }

    @Test
    fun theShadesRepeatAfterTheFourth() {
        val shades = mutableListOf<Color>()
        rule.setContent {
            SelloTheme {
                shades.clear()
                (0..5).forEach { shades += investmentShade(it) }
            }
        }
        rule.waitForIdle()
        assertEquals(shades[0], shades[4])
        assertEquals(shades[1], shades[5])
        assertNotEquals(shades[0], shades[3])
    }

    @Test
    fun theBarLeavesAGapBetweenSegments() {
        var brand by mutableStateOf(Color.Unspecified)
        rule.inEveryScheme(content = {
            brand = SelloTheme.colors.brand
            Column {
                SplitBar(
                    listOf(SplitSegment(1f, "a", brand), SplitSegment(1f, "b", brand)),
                    Modifier.testTag("gap"),
                    gap = 2.dp
                )
                SplitBar(
                    listOf(SplitSegment(1f, "a", brand), SplitSegment(1f, "b", brand)),
                    Modifier.testTag("joined")
                )
            }
        }) { scheme ->
            fun middle(tag: String): Boolean {
                val pixels = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
                val pixel = pixels[pixels.width / 2, pixels.height / 2]
                return abs(pixel.red - brand.red) < 0.06f &&
                    abs(pixel.green - brand.green) < 0.06f &&
                    abs(pixel.blue - brand.blue) < 0.06f
            }
            assertFalse("$scheme: no gap in the middle", middle("gap"))
            assertTrue("$scheme: a joined bar is brand in the middle", middle("joined"))
        }
    }

    @Test
    fun aRowWithoutAValueSaysSoWhole() {
        rule.inEveryScheme(content = {
            InvestmentRow(
                name = "Cripto",
                detail = "Criptomoneda · sin valor anotado",
                icon = SelloIcon.Insights,
                shade = Color.Unspecified,
                value = null,
                missingLabel = "Sin valor",
                secondary = "aportaste 2.000.000",
                onOpen = {},
                modifier = Modifier.testTag("row")
            )
        }) { scheme ->
            val results = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText("Criptomoneda · sin valor anotado", useUnmergedTree = true)
                .fetchSemanticsNode().config
                .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val layout = results.single()
            val shortened = layout.multiParagraph.didExceedMaxLines ||
                (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
            assertFalse("$scheme: the caption is cut in ${layout.lineCount} lines", shortened)
        }
    }
}
