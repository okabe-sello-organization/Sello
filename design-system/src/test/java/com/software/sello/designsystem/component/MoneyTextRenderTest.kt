package com.software.sello.designsystem.component

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTextRenderTest {
    private fun value(sign: MoneySign, digits: String, symbol: String = "") =
        MoneyTextValue(sign, symbol, digits, "spoken")

    private val style = TextStyle(fontSize = 60.sp, letterSpacing = (-0.04).em)

    private fun drawn(value: MoneyTextValue, style: MoneyStyle) =
        moneyAnnotatedString(value, style, this.style).text.replace("\u200B", "")

    @Test
    fun theFullFigureIsDrawnWithATrueMinusAndNoMarkForPesos() {
        assertEquals("937.200", drawn(value(MoneySign.None, "937.200"), MoneyStyle.Hero))
        assertEquals("+850.000", drawn(value(MoneySign.Plus, "850.000"), MoneyStyle.Total))
        assertEquals("\u221210.000", drawn(value(MoneySign.Minus, "10.000"), MoneyStyle.Card))
        assertEquals(
            "\u22129.223.372.036.854.775.808",
            drawn(value(MoneySign.Minus, "9.223.372.036.854.775.808"), MoneyStyle.Title)
        )
        assertEquals(
            "USD\u00A02.340,00",
            drawn(value(MoneySign.None, "2.340,00", "USD"), MoneyStyle.Title)
        )
    }

    @Test
    fun linesInsideASlipDropTheSymbolButKeepTheSign() {
        assertEquals("18.400", drawn(value(MoneySign.None, "18.400"), MoneyStyle.Line))
        assertEquals("\u221221.500", drawn(value(MoneySign.Minus, "21.500"), MoneyStyle.Line))
        assertEquals("+850.000", drawn(value(MoneySign.Plus, "850.000"), MoneyStyle.Line))
    }

    @Test
    fun plainTextMatchesWhatIsDrawn() {
        for (moneyStyle in MoneyStyle.entries) {
            val v = value(MoneySign.Minus, "1.234.567")
            assertEquals(drawn(v, moneyStyle), moneyPlainText(v, moneyStyle))
        }
    }

    @Test
    fun aLongFigureCanBreakOnlyAfterAThousandsPoint() {
        val text = moneyAnnotatedString(
            value(MoneySign.None, "9.223.372.036.854.775.807"),
            MoneyStyle.Hero,
            style
        ).text
        val pieces = text.split("\u200B")
        assertEquals(listOf("9.", "223.", "372.", "036.", "854.", "775.", "807"), pieces)
    }

    @Test
    fun thousandsPointsAreTightenedAndACurrencyCodeIsSmallAndRaised() {
        val pesos = moneyAnnotatedString(value(MoneySign.None, "1.234"), MoneyStyle.Hero, style)
        assertTrue(pesos.spanStyles.none { it.item.fontSize == 0.56.em })
        val text =
            moneyAnnotatedString(value(MoneySign.None, "1.234", "USD"), MoneyStyle.Hero, style)
        val symbol = text.spanStyles.single { it.item.fontSize == 0.56.em }
        assertEquals("USD", text.text.substring(symbol.start, symbol.end))
        assertEquals("USD\u00A01.234", text.text.replace("\u200B", ""))
        assertTrue(symbol.item.baselineShift!!.multiplier > 0f)
        // The style's own -0.04em tracking plus 0.13em of tightening.
        val tight = text.spanStyles.filter { abs(it.item.letterSpacing.value + 0.17f) < 1e-4f }
        assertEquals(listOf("1", "."), tight.map { text.text.substring(it.start, it.end) })
    }

    @Test
    fun aCurrencyCodeKeepsItsLettersTogetherAndTheDecimalCommaIsTightened() {
        val text = moneyAnnotatedString(
            value(MoneySign.None, "2.340,00", "USD"),
            MoneyStyle.Title,
            style
        )
        // The code is followed by a space that cannot become a line break.
        assertEquals("USD\u00A02.340,00", text.text.replace("\u200B", ""))
        val tight = text.spanStyles.filter { abs(it.item.letterSpacing.value + 0.17f) < 1e-4f }
        assertEquals(
            listOf("2", ".", "0", ","),
            tight.map {
                text.text.substring(it.start, it.end)
            }
        )
    }

    // ---- Pinked edge ----

    @Test
    fun aWholeNumberOfTeethAlwaysFitsTheWidth() {
        assertEquals(36, toothCount(360f, 10f))
        assertEquals(36, toothCount(364f, 10f)) // stretched, not a partial tooth
        assertEquals(37, toothCount(366f, 10f))
        assertEquals(1, toothCount(4f, 10f))
        assertEquals(1, toothCount(0f, 10f))
    }
}
