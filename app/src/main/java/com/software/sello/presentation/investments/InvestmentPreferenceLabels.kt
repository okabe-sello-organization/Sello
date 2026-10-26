package com.software.sello.presentation.investments

import androidx.annotation.StringRes
import com.software.sello.R
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InvestmentOrder

/** How Configuración and a restore name Inversiones' list orders. */
@StringRes
fun InvestmentOrder.label(): Int = when (this) {
    InvestmentOrder.LargestValue -> R.string.investment_order_largest_value
    InvestmentOrder.Return -> R.string.investment_order_return
    InvestmentOrder.Alphabetical -> R.string.investment_order_alphabetical
    InvestmentOrder.Newest -> R.string.investment_order_newest
    InvestmentOrder.LatestUpdate -> R.string.investment_order_latest_update
}

/** The currency a new investment starts in, by name: only pesos and dollars exist. */
@StringRes
fun Currency.newInvestmentLabel(): Int =
    if (this == Currency.USD) R.string.investment_currency_usd else R.string.investment_currency_cop
