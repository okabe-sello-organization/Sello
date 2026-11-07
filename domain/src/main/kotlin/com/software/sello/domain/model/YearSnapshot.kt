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
 * anything recorded, or the current year when nothing is. [recordedDays] counts the days of
 * the year up to [asOf] with at least one expense or income.
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
    val recordedDays: Int,
    /** Always twelve, January first. */
    val months: List<YearMonthLine>,
    /** Null when the year before has nothing recorded on the days compared. */
    val comparison: YearComparison?,
    /** Every category with spending in the year, archived ones included, most first. */
    val categories: List<YearCategory>,
    val records: YearRecords
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

/**
 * Récords del año, from finished months only: the month in progress never counts. A month
 * counts when it has at least one expense; with fewer than two such months there are none.
 */
sealed interface YearRecords {
    /** Fewer than two finished months with expenses: "Con un mes más verás tus récords." */
    data object TooFew : YearRecords

    /**
     * [dearest] spent the most, [calmest] the least, [biggest] is the largest single expense
     * and [mostOver] the category with the most Excedido months, from two up. Ties go to the
     * earlier month, the earlier expense, or name order.
     */
    data class Found(
        val dearest: YearMonthRecord,
        val calmest: YearMonthRecord,
        val biggest: YearExpenseRecord,
        val mostOver: YearOverRecord?
    ) : YearRecords
}

data class YearMonthRecord(val month: YearMonth, val spent: Money)

/** The [expense] and its [category] as last seen, for a note it may not have. */
data class YearExpenseRecord(val expense: Expense, val category: Category)

/** [category] went over its own limit in [months] finished months. */
data class YearOverRecord(val category: Category, val months: Int)
