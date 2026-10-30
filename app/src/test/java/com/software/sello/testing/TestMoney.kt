package com.software.sello.testing

import com.software.sello.domain.model.Currency
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels

/** Words around figures as the tests read them: pesos or dólares, "menos" and "más". */
object TestMoneyLabels : MoneyLabels {
    override fun amount(currency: Currency, digits: String, isOne: Boolean) =
        if (currency == Currency.COP) "$digits pesos" else "$digits dólares"

    override fun negative(amount: String) = "menos $amount"

    override fun positive(amount: String) = "más $amount"
}

fun testMoney() = MoneyFormatter(TestMoneyLabels)
