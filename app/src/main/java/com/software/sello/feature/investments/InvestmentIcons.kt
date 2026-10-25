package com.software.sello.feature.investments

import androidx.annotation.StringRes
import com.software.sello.R
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

    /** What a screen reader says for each icon in the strip. */
    private val labels: Map<String, Int> = mapOf(
        "account_balance" to R.string.investment_icon_bank,
        "trending_up" to R.string.investment_icon_shares,
        "insights" to R.string.investment_icon_fund,
        "home" to R.string.investment_icon_property,
        "shopping_cart" to R.string.investment_icon_business,
        "school" to R.string.investment_icon_education
    )

    val keys: Set<String> = options.map { it.first }.toSet()

    fun iconFor(key: String): SelloIcon =
        options.firstOrNull { it.first == key }?.second ?: SelloIcon.TrendingUp

    @StringRes
    fun labelFor(key: String): Int = labels[key] ?: R.string.investment_icon_shares
}
