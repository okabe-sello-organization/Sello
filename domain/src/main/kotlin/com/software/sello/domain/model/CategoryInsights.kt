package com.software.sello.domain.model

import java.math.BigInteger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Actual spending in one day-of-month week; allowance is still available, not spent. */
data class CategoryWeek(
    val number: Int,
    val from: LocalDate,
    val until: LocalDate,
    val spent: Money,
    val current: Boolean,
    val remainingAllowance: Money?,
    val ceiling: Money
)

/**
 * One completed month of the category. [limit] is what the month is judged on: its capacity
 * `C = B + K` while a chain covers it, with [carry] splitting it (ADR 0020 CP14, CP19), else
 * the configured limit.
 */
data class CategoryHistoryMonth(
    val month: YearMonth,
    val spent: Money,
    val limit: Money?,
    val carry: CarryBreakdown? = null
)

/** A recorded comparison, absent when there is no known, positive previous baseline. */
data class CategoryComparison(val previousMonth: YearMonth, val tenthsPercent: BigInteger)

/** Every financial figure here belongs to the same budget snapshot and cutoff. */
data class CategoryInsights(
    val budget: MonthlySnapshot,
    val categoryId: CategoryId,
    val allowance: DailyAllowance?,
    val comparison: CategoryComparison?,
    val elapsedDays: Int,
    val averageExpense: Money?,
    val largestExpense: Expense?,
    val mostExpensiveWeekday: DayOfWeek?,
    val weeklyAverage: Money?,
    val weeks: List<CategoryWeek>,
    val historicalAverage: Money?,
    val history: List<CategoryHistoryMonth>
)
