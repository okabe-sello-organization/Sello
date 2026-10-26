package com.software.sello.presentation.category

import androidx.annotation.StringRes
import com.software.sello.R
import com.software.sello.designsystem.icon.SelloIcon

/** An icon a category can have: its stored key, its drawing and what it is called. */
data class CategoryIconOption(val key: String, val icon: SelloIcon, @StringRes val label: Int)

/**
 * The icons offered for categories, in the order shown: the one set of 35 that goals and
 * investments choose from too. The key is what is stored, so it must never change once
 * released; the drawing and the name may.
 */
object CategoryIcons {
    val options: List<CategoryIconOption> = listOf(
        CategoryIconOption("restaurant", SelloIcon.Restaurant, R.string.category_icon_restaurant),
        CategoryIconOption("home", SelloIcon.Home, R.string.category_icon_home),
        CategoryIconOption(
            "directions_bus",
            SelloIcon.DirectionsBus,
            R.string.category_icon_directions_bus
        ),
        CategoryIconOption("theaters", SelloIcon.Theaters, R.string.category_icon_theaters),
        CategoryIconOption(
            "medical_services",
            SelloIcon.MedicalServices,
            R.string.category_icon_medical_services
        ),
        CategoryIconOption("checkroom", SelloIcon.Checkroom, R.string.category_icon_checkroom),
        CategoryIconOption("local_cafe", SelloIcon.LocalCafe, R.string.category_icon_local_cafe),
        CategoryIconOption("school", SelloIcon.School, R.string.category_icon_school),
        CategoryIconOption("pets", SelloIcon.Pets, R.string.category_icon_pets),
        CategoryIconOption(
            "fitness_center",
            SelloIcon.FitnessCenter,
            R.string.category_icon_fitness_center
        ),
        CategoryIconOption(
            "shopping_cart",
            SelloIcon.ShoppingCart,
            R.string.category_icon_shopping_cart
        ),
        CategoryIconOption("flight", SelloIcon.Flight, R.string.category_icon_flight),
        CategoryIconOption(
            "phone_iphone",
            SelloIcon.PhoneIphone,
            R.string.category_icon_phone_iphone
        ),
        CategoryIconOption("child_care", SelloIcon.ChildCare, R.string.category_icon_child_care),
        CategoryIconOption(
            "directions_car",
            SelloIcon.DirectionsCar,
            R.string.category_icon_directions_car
        ),
        CategoryIconOption(
            "local_gas_station",
            SelloIcon.LocalGasStation,
            R.string.category_icon_local_gas_station
        ),
        CategoryIconOption("bolt", SelloIcon.Bolt, R.string.category_icon_bolt),
        CategoryIconOption("wifi", SelloIcon.Wifi, R.string.category_icon_wifi),
        CategoryIconOption("savings", SelloIcon.Savings, R.string.category_icon_savings),
        CategoryIconOption(
            "credit_card",
            SelloIcon.CreditCard,
            R.string.category_icon_credit_card
        ),
        CategoryIconOption("redeem", SelloIcon.Redeem, R.string.category_icon_redeem),
        CategoryIconOption(
            "sports_esports",
            SelloIcon.SportsEsports,
            R.string.category_icon_sports_esports
        ),
        CategoryIconOption("spa", SelloIcon.Spa, R.string.category_icon_spa),
        CategoryIconOption("handyman", SelloIcon.Handyman, R.string.category_icon_handyman),
        // Money and investments: the eleven that round the shared set to 35 (owner, 2026-10-26).
        CategoryIconOption(
            "account_balance",
            SelloIcon.AccountBalance,
            R.string.category_icon_account_balance
        ),
        CategoryIconOption(
            "trending_up",
            SelloIcon.TrendingUp,
            R.string.category_icon_trending_up
        ),
        CategoryIconOption(
            "insights",
            SelloIcon.Insights,
            R.string.category_icon_insights
        ),
        CategoryIconOption(
            "payments",
            SelloIcon.Payments,
            R.string.category_icon_payments
        ),
        CategoryIconOption(
            "currency_exchange",
            SelloIcon.CurrencyExchange,
            R.string.category_icon_currency_exchange
        ),
        CategoryIconOption(
            "account_balance_wallet",
            SelloIcon.AccountBalanceWallet,
            R.string.category_icon_account_balance_wallet
        ),
        CategoryIconOption(
            "pie_chart",
            SelloIcon.PieChart,
            R.string.category_icon_pie_chart
        ),
        CategoryIconOption(
            "currency_bitcoin",
            SelloIcon.CurrencyBitcoin,
            R.string.category_icon_currency_bitcoin
        ),
        CategoryIconOption(
            "storefront",
            SelloIcon.Storefront,
            R.string.category_icon_storefront
        ),
        CategoryIconOption(
            "apartment",
            SelloIcon.Apartment,
            R.string.category_icon_apartment
        ),
        CategoryIconOption(
            "agriculture",
            SelloIcon.Agriculture,
            R.string.category_icon_agriculture
        )
    )

    val keys: List<String> = options.map { it.key }

    /** A key this version has no drawing for, such as one from a newer backup, still shows something. */
    fun iconFor(key: String): SelloIcon =
        options.firstOrNull { it.key == key }?.icon ?: SelloIcon.ReceiptLong
}
