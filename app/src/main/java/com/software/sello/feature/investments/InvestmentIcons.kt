package com.software.sello.feature.investments

import com.software.sello.designsystem.icon.SelloIcon

/** The icons an investment can have, in the order offered (v2 Agregar inversión). */
object InvestmentIcons {
    val options: List<Pair<String, SelloIcon>> = listOf(
        "account_balance" to SelloIcon.AccountBalance,
        "trending_up" to SelloIcon.TrendingUp,
        "insights" to SelloIcon.Insights,
        "home" to SelloIcon.Home,
        "shopping_cart" to SelloIcon.ShoppingCart,
        "school" to SelloIcon.School
    )

    fun iconFor(key: String): SelloIcon =
        options.firstOrNull { it.first == key }?.second ?: SelloIcon.TrendingUp
}
