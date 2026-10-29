package com.software.sello.designsystem.component

/** What a screen draws for [digits] pesos, typed by hand: grouped digits, an optional minus. */
internal fun pesos(digits: String, minus: Boolean = false) = MoneyTextValue(
    if (minus) MoneySign.Minus else MoneySign.None,
    "",
    digits,
    "$digits pesos"
)
