package com.software.sello.domain.model

import java.math.BigInteger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DailySpending(val date: LocalDate, val spent: Money, val cumulative: Money)

data class SpendingMonth(
    val month: YearMonth,
    val spent: Money,
    val limit: Money?,
    val forecast: Forecast?,
    val expenseCount: Int
)

data class ResumenSnapshot(
    val budget: MonthlySnapshot,
    val income: Money,
    val netFlow: Money,
    val allocated: Money,
    val unallocated: Money,
    val actualVerdict: PastVerdict?,
    val daily: List<DailySpending>,
    val history: List<SpendingMonth>,
    val futureDatedIncomes: Int,
    /** How many incomes make up [income]: the month's, dated up to today. */
    val incomeCount: Int,
    val destinations: IncomeDestinations,
    /** Null when the month before has nothing recorded: there is nothing to compare with. */
    val comparison: SpendingComparison? = null,
    /** Null until a month shown is complete and has something recorded. */
    val normalMonth: NormalMonth? = null,
    /** Null until a week has passed since the first day in its window. */
    val weekdays: WeekdayPattern? = null,
    /** At most one, and only for the month in progress. */
    val suggestion: LimitAdjustment? = null
)

/** What one category took in the month. */
data class CategorySpending(val category: Category, val spent: Money)

/**
 * Where the month's recorded income went, by spending only: goal and investment records
 * are not destinations (ADR 0012 BG4). [top] are the categories that took most, most
 * first, ties in category order; [rest] is what the [restCount] others took together.
 * With [top], it adds up to the month's spending.
 */
data class IncomeDestinations(val top: List<CategorySpending>, val rest: Money, val restCount: Int)

enum class SpendingDirection { More, Less, Same }

/**
 * The selected month's spending against [previousMonth]'s. In the month in progress both
 * run from day 1 to [throughDay] and [previousThroughDay], the earlier capped at its
 * own length; for a finished month both are whole months and the days are null.
 * [difference] is never negative: [direction] says which way. [percentTenths] is
 * [difference] over [previousSpent] in tenths of a per cent, half up, or null when
 * [previousSpent] is zero.
 */
data class SpendingComparison(
    val previousMonth: YearMonth,
    val spent: Money,
    val previousSpent: Money,
    val throughDay: Int?,
    val previousThroughDay: Int?,
    val difference: Money,
    val direction: SpendingDirection,
    val percentTenths: BigInteger?
)

/** The average of [months] complete months that have something recorded, half up. */
data class NormalMonth(val average: Money, val months: Int)

/**
 * Average spending per weekday from [from] to [through]: what was spent on that weekday
 * over how many times it came round, half up. [busiest] is the weekday whose average is
 * highest on its own, or null when none stands out. [lastSixMonths] says the window
 * reaches back the full six months; otherwise it starts at the first day recorded.
 */
data class WeekdayPattern(
    val from: LocalDate,
    val through: LocalDate,
    val days: List<WeekdaySpending>,
    val busiest: DayOfWeek?,
    val lastSixMonths: Boolean = false
)

data class WeekdaySpending(val day: DayOfWeek, val average: Money)

/**
 * A category over its limit in each of [months], the two complete months before this
 * one, that still has a limit: it spent [averageSpent] on average there, half up, and
 * its limit now is [limit].
 */
data class LimitAdjustment(
    val categoryId: CategoryId,
    val name: CategoryName,
    val months: List<YearMonth>,
    val averageSpent: Money,
    val limit: Money
)
