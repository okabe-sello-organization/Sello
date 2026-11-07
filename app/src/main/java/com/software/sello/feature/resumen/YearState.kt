package com.software.sello.feature.resumen

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.SpendingDirection

/** Tu año, for [year]. Moving between years needs figures read: only [Ready] can. */
sealed interface YearState {
    val year: Int

    data class Loading(override val year: Int) : YearState

    /** Nothing was ever read for [year]. */
    data class Failed(override val year: Int) : YearState

    @Immutable
    data class Ready(
        override val year: Int,
        val canPrevious: Boolean,
        val canNext: Boolean,
        val hero: YearHero,
        /** Twelve, January first. */
        val months: List<YearColumn>,
        /** "noviembre", while the year is in progress. */
        val monthInProgress: String?,
        val withinMonths: Int,
        val overMonths: Int,
        val income: MoneyTextValue,
        val spent: MoneyTextValue,
        val netFlow: MoneyTextValue,
        val overspent: Boolean,
        /** Every category with spending, most first. */
        val categories: List<YearCategoryRow>,
        /** Nothing recorded in the year, neither expenses nor incomes. */
        val empty: Boolean,
        val stale: Boolean,
        /** The day the figures run to, as in a sentence: "6 de noviembre". */
        val asOf: String,
        val selectedMonth: String? = null,
        val allCategories: Boolean = false
    ) : YearState {
        /** The five largest unless all were asked for. */
        val shownCategories: List<YearCategoryRow>
            get() = if (allCategories) categories else categories.take(TOP_CATEGORIES)
    }

    companion object {
        const val TOP_CATEGORIES = 5
    }
}

/**
 * The year's expenses: from 1 January to [through] ("6 de noviembre") while [inProgress],
 * the whole year otherwise. Without [comparison], [spent] is shown alone.
 */
@Immutable
data class YearHero(
    val inProgress: Boolean,
    val spent: MoneyTextValue,
    val through: String,
    val comparison: YearHeroComparison?
)

/**
 * Against [previousYear]: [difference] is never negative, [direction] says which way.
 * [percent] is written "7,3". [previousThrough] is set only when the year before stops on
 * an earlier day (28 February for a 29th).
 */
@Immutable
data class YearHeroComparison(
    val previousYear: Int,
    val difference: MoneyTextValue,
    val direction: SpendingDirection,
    val percent: String?,
    val wholeYears: Boolean,
    val previousThrough: String?,
    val bars: List<ComparisonBar>
)

/** A month's own verdict: none without a limit or before it finishes. */
enum class YearMark { None, Within, Over }

/**
 * One column of Mes a mes. [amount] is null for a month not yet begun, which cannot be
 * selected. [label] is the month as said: "marzo de 2026".
 */
@Immutable
data class YearColumn(
    val chart: ChartPoint,
    val label: String,
    val amount: MoneyTextValue?,
    val mark: YearMark
)

/**
 * A category in ¿En qué se fue?: [percent] is its whole share of the year, [bar] how much
 * of its bar to fill against the largest, geometry only.
 */
@Immutable
data class YearCategoryRow(
    val id: String,
    val name: String,
    val iconKey: String,
    val amount: MoneyTextValue,
    val percent: Int,
    val bar: Float
)

sealed interface YearAction {
    data object Previous : YearAction

    data object Next : YearAction

    data object Retry : YearAction

    data class SelectMonth(val id: String) : YearAction

    data object ShowAllCategories : YearAction
}
