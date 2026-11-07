package com.software.sello.domain.model

import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tu año: one calendar [year] as of the financial day [asOf], worked out from the same
 * month snapshots Resumen uses, read together at [revision] of [generation].
 *
 * [spent] and [income] count what is dated up to [asOf] only: a year in progress is never
 * projected. [netFlow] is Ingresos menos gastos, signed. [firstYear] is the first year with
 * anything recorded, or the current year when nothing is.
 */
data class YearSnapshot(
    val year: Int,
    val asOf: LocalDate,
    val generation: Long,
    val revision: Long,
    val firstYear: Int,
    val spent: Money,
    val income: Money,
    val netFlow: Money,
    val incomeCount: Int,
    val expenseCount: Int,
    /** Always twelve, January first. */
    val months: List<YearMonthLine>,
    /** Null when the year before has nothing recorded on the days compared. */
    val comparison: YearComparison?,
    /** Every category with spending in the year, archived ones included, most first. */
    val categories: List<YearCategory>
)

/** How one month of the year stands. */
sealed interface YearMonthStatus {
    /** Not yet begun. */
    data object Upcoming : YearMonthStatus

    /** The month in progress, up to today: it has no verdict yet. */
    data object InProgress : YearMonthStatus

    /**
     * A finished month, with its own past verdict (ADR 0012 BG1) from its limit and carry,
     * or null when it had no limit.
     */
    data class Finished(val verdict: PastVerdict?) : YearMonthStatus
}

/** [spent] is null for an [YearMonthStatus.Upcoming] month: nothing is projected into it. */
data class YearMonthLine(val month: YearMonth, val spent: Money?, val status: YearMonthStatus)

/**
 * The year's spending against [previousYear]'s on the same days: from 1 January to
 * [through] and to [previousThrough], the earlier day capped at its own month's length (29
 * February becomes 28 February), or whole years. [difference] is never negative:
 * [direction] says which way. [percentTenths] is as Resumen's, half up, or null when
 * [previousSpent] is zero.
 */
data class YearComparison(
    val previousYear: Int,
    val spent: Money,
    val previousSpent: Money,
    val through: LocalDate,
    val previousThrough: LocalDate,
    val wholeYears: Boolean,
    val difference: Money,
    val direction: SpendingDirection,
    val percentTenths: BigInteger?
)

/** A category's spending in the year and its [percent] of the year's, whole, half up. */
data class YearCategory(val category: Category, val spent: Money, val percent: Int)
