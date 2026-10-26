package com.software.sello.domain.model

import java.time.YearMonth

/** The three display modes a person can select. `System` is the first-run default. */
enum class ThemePreference { System, Light, Dark }

/** The two Sello brand-ink families. This is deliberately not a design-system enum. */
enum class InkPreference { Cobalto, Violeta }

/**
 * How the categories on Recibo are ranked. Stable storage values are owned by the
 * DataStore adapter; these names are not serialized UI enum names.
 */
enum class CategoryOrder { MostUsed, Alphabetical, AmountSpent, Limit, LatestExpenseAdded }

/** The direction of the selected category metric. */
enum class CategoryOrderDirection { Descending, Ascending }

/**
 * How Inversiones lists investments. Stable storage values are owned by the DataStore
 * adapter and the backup format; these names are not serialized.
 */
enum class InvestmentOrder { LargestValue, Return, Alphabetical, Newest, LatestUpdate }

/**
 * Inversiones' two settings: the currency Nueva inversión starts in, COP or USD (the
 * currencies an investment may hold), and the list's order.
 */
data class InvestmentPreference(
    val newCurrency: Currency = Currency.COP,
    val order: InvestmentOrder = InvestmentOrder.LargestValue
)

data class AppearancePreference(
    val theme: ThemePreference = ThemePreference.System,
    val ink: InkPreference = InkPreference.Cobalto
)

data class CategoryOrderingPreference(
    val order: CategoryOrder = CategoryOrder.MostUsed,
    val direction: CategoryOrderDirection = CategoryOrderDirection.Descending
)

/**
 * The Resumen suggestion about [categoryId] was dismissed in [month]. It may come back in
 * a later month while it still applies. Nonfinancial: it hides advice, never a figure.
 */
data class DismissedSuggestion(val month: YearMonth, val categoryId: CategoryId)

/** Dismissal is nonfinancial and must never change the always-available correction help. */
data class CorrectionAnnouncementPreference(
    val pastExpenseSeen: Boolean = false,
    val pastLimitSeen: Boolean = false
)
