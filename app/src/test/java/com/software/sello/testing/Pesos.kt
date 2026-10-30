package com.software.sello.testing

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount

/** A whole-peso amount as a record carries it. */
fun pesos(amount: Long) = (TransactionAmount.of(Money.cop(amount)) as Outcome.Success).value

/** What a screen draws for [digits] pesos, typed by hand: grouped digits, an optional minus. */
fun pesos(digits: String, minus: Boolean = false) = MoneyTextValue(
    if (minus) MoneySign.Minus else MoneySign.None,
    "",
    digits,
    "$digits pesos"
)
