package com.software.sello.domain.model

import java.time.YearMonth

/** The three display modes a person can select. `System` is the first-run default. */
enum class ThemePreference { System, Light, Dark }

/**
 * The ink a person picked, in picker order: one of Sello's hand-made inks, or [Tuya], their
 * own hue. This is deliberately not a design-system type. [id] is what preferences and
 * backups write: never change or reuse one, since both MUST keep accepting every id ever
 * written.
 */
sealed interface InkPreference {
    val id: String

    data object Cobalto : InkPreference {
        override val id = "cobalto"
    }

    data object Violeta : InkPreference {
        override val id = "violeta"
    }

    /** The person's own ink: Sello's palette turned to [hue]. */
    data class Tuya(val hue: InkHue) : InkPreference {
        override val id get() = TUYA_ID
    }

    companion object {
        private const val TUYA_ID = "tuya"

        /**
         * The ink written as [id] with [hue], or null when they don't make one: an id Sello
         * never wrote, Tuya without a hue or with one outside [InkHue.RANGE], or a hand-made
         * ink with a hue. A bad hue is refused, never wrapped or clipped.
         */
        fun of(id: String, hue: Long?): InkPreference? = when (id) {
            Cobalto.id -> Cobalto.takeIf { hue == null }
            Violeta.id -> Violeta.takeIf { hue == null }
            TUYA_ID -> hue?.let(InkHue::of)?.let(::Tuya)
            else -> null
        }
    }
}

/** A Tuya ink's hue: a whole OKLCH degree, [RANGE]. Made only through [of]. */
@JvmInline
value class InkHue private constructor(val degrees: Int) {
    companion object {
        val RANGE = 0..359

        /** [degrees] as a hue, or null outside [RANGE]: never wrapped or clipped. */
        fun of(degrees: Long): InkHue? = if (degrees in
            RANGE.first.toLong()..RANGE.last.toLong()
        ) {
            InkHue(degrees.toInt())
        } else {
            null
        }
    }
}

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
