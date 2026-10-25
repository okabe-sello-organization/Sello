package com.software.sello.presentation.money

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Money

/** The words around a figure. Production reads them from resources. */
interface MoneyLabels {
    /** [digits] with its currency, such as `937.200 pesos`. [isOne] is exactly one unit. */
    fun amount(currency: Currency, digits: String, isOne: Boolean): String

    fun negative(amount: String): String

    fun positive(amount: String): String
}

/**
 * Turns exact [Money] into what the design system draws and a screen reader says, in
 * es-CO: thousands with a point, decimals with a comma, no decimals for COP. It only
 * moves digits as text, so no amount passes through a floating-point type and the
 * full figure is always kept; abbreviations belong to chart axes, not here.
 */
class MoneyFormatter(private val labels: MoneyLabels) {
    /** With [signed] a positive amount carries a plus; a negative always carries a minus. */
    fun format(money: Money, signed: Boolean = false): MoneyTextValue {
        val currency = money.currency
        // Text, not Math.abs: the lowest Long has no positive counterpart.
        val magnitude = money.minorUnits.toString().removePrefix("-")
        val digits = group(magnitude, currency.scale)
        val isOne = magnitude == "1" + "0".repeat(currency.scale)
        val amount = labels.amount(currency, digits, isOne)
        val sign = when {
            money.minorUnits < 0 -> MoneySign.Minus
            signed && money.minorUnits > 0 -> MoneySign.Plus
            else -> MoneySign.None
        }
        val spoken = when (sign) {
            MoneySign.Minus -> labels.negative(amount)
            MoneySign.Plus -> labels.positive(amount)
            MoneySign.None -> amount
        }
        return MoneyTextValue(sign, symbol(currency), digits, spoken)
    }

    /**
     * The size of [money] without its sign, for a sentence that already says which way
     * it goes, such as "Te pasaste por 21.500".
     */
    fun magnitude(money: Money): MoneyTextValue {
        val digits = money.minorUnits.toString().removePrefix("-")
        val grouped = group(digits, money.currency.scale)
        val isOne = digits == "1" + "0".repeat(money.currency.scale)
        return MoneyTextValue(
            MoneySign.None,
            symbol(money.currency),
            grouped,
            labels.amount(money.currency, grouped, isOne)
        )
    }

    /**
     * An exact decimal written as the app writes money, without a currency: 415025
     * hundredths are "4.150,25". For the USD rate, which is pesos per dollar, not money.
     */
    fun decimal(units: Long, scale: Int): String = group(units.toString(), scale)

    /** Pesos carry no mark (ADR 0007); any other currency is named by its code. */
    private fun symbol(currency: Currency) = if (currency == Currency.COP) "" else currency.code

    private fun group(magnitude: String, scale: Int): String {
        val padded = magnitude.padStart(scale + 1, '0')
        val whole = padded.dropLast(scale)
        val grouped = whole.reversed().chunked(GROUP_SIZE).joinToString(".").reversed()
        return if (scale == 0) grouped else grouped + "," + padded.takeLast(scale)
    }

    private companion object {
        const val GROUP_SIZE = 3
    }
}
