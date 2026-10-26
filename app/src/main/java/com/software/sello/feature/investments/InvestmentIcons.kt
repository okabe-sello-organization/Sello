package com.software.sello.feature.investments

import androidx.annotation.StringRes
import com.software.sello.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.presentation.category.CategoryIcons

/**
 * The icons an investment can have: the one shared set of 35, offered with the money and
 * investment ones first (v2 Agregar inversión). The six investments had before keep their
 * investment names ("Finca raíz" for the house); the rest are called as for a category.
 */
object InvestmentIcons {
    private val first = listOf(
        "account_balance", "trending_up", "insights", "payments", "currency_exchange",
        "account_balance_wallet", "pie_chart", "currency_bitcoin", "storefront", "apartment",
        "agriculture"
    )

    val options: List<Pair<String, SelloIcon>> = CategoryIcons.options
        .sortedBy { option -> first.indexOf(option.key).takeIf { it >= 0 } ?: first.size }
        .map { it.key to it.icon }

    /** What a screen reader says for each icon: an investment's own name where it has one. */
    private val labels: Map<String, Int> = CategoryIcons.options.associate { it.key to it.label } +
        mapOf(
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
