package com.software.sello.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class SignedTextTest {
    private fun cop(sign: MoneySign, digits: String) = MoneyTextValue(sign, "", digits, "")

    @Test
    fun aChangeIsWrittenWithAPlusOrATrueMinus() {
        assertEquals("+80.000", cop(MoneySign.Plus, "80.000").signedText())
        assertEquals("−150.000", cop(MoneySign.Minus, "150.000").signedText())
        assertEquals("937.200", cop(MoneySign.None, "937.200").signedText())
    }

    @Test
    fun withoutThePlusOnlyANegativeFigureKeepsItsSign() {
        assertEquals("80.000", cop(MoneySign.Plus, "80.000").signedText(showPlus = false))
        assertEquals(
            "−150.000",
            cop(MoneySign.Minus, "150.000").signedText(showPlus = false)
        )
        assertEquals("400.000", cop(MoneySign.None, "400.000").signedText(showPlus = false))
    }

    @Test
    fun theCurrencyCodeIsLeftToTheSentence() {
        val usd = MoneyTextValue(MoneySign.Minus, "USD", "12,50", "menos 12,50 dólares")
        assertEquals("−12,50", usd.signedText())
    }

    @Test
    fun theMinusIsTheTrueMinusNotAHyphen() {
        val text = cop(MoneySign.Minus, "1").signedText()
        assertEquals(0x2212, text.codePointAt(0))
    }
}
