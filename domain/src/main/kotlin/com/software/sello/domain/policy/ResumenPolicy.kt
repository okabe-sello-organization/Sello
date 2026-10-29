package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DailySpending
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.LimitAdjustment
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.NormalMonth
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.SpendingComparison
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.domain.model.SpendingMonth
import com.software.sello.domain.model.WeekdayPattern
import com.software.sello.domain.model.WeekdaySpending
import com.software.sello.domain.port.FinancialDay
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class ResumenMonthInput(
    val month: YearMonth,
    val categories: List<CategoryInMonth>,
    val expenses: List<Expense>,
    val carryover: List<CategoryCarryInput> = emptyList()
)

object ResumenPolicy {
    fun monthsEndingIn(month: YearMonth): List<YearMonth> =
        (11 downTo 0).map { month.minusMonths(it.toLong()) }.filter { it.year >= 0 }

    fun snapshot(
        month: YearMonth,
        today: FinancialDay,
        generation: Long,
        revision: Long,
        months: List<ResumenMonthInput>,
        incomes: List<Income>
    ): Outcome<ResumenSnapshot, SnapshotFailure> {
        val currentMonth = YearMonth.from(today.date)
        if (month > currentMonth) return Outcome.Failure(SnapshotFailure.FutureMonth(currentMonth))
        require(months.map { it.month } == monthsEndingIn(month))
        val snapshots = mutableListOf<MonthlySnapshot>()
        for (input in months) {
            when (
                val snapshot = MonthlyBudgetPolicy.snapshot(
                    input.month,
                    today,
                    generation,
                    revision,
                    input.categories,
                    input.expenses,
                    carryover = input.carryover
                )
            ) {
                is Outcome.Failure -> return snapshot
                is Outcome.Success -> snapshots += snapshot.value
            }
        }
        val budget = snapshots.last()
        val applicable = incomes.filter {
            YearMonth.from(it.date) == month && it.date <= today.date
        }
        val income = when (val sum = total(applicable.map { it.amount.money })) {
            is Outcome.Failure -> return sum
            is Outcome.Success -> sum.value
        }
        val limited = budget.overall as? OverallBudget.Limited
        // Por asignar is income minus the base limits: carry is never income (ADR 0020 CP17).
        val allocated = limited?.budget?.base ?: Money.cop(0)
        val netFlow = when (val difference = income - budget.spent) {
            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Unrepresentable)
            is Outcome.Success -> difference.value
        }
        val unallocated = when (val difference = income - allocated) {
            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Unrepresentable)
            is Outcome.Success -> difference.value
        }
        val byDate = months.last().expenses
            .filter { YearMonth.from(it.date) == month && it.date <= today.date }
            .groupBy { it.date }
        val dayCount = if (month == currentMonth) today.date.dayOfMonth else month.lengthOfMonth()
        val daily = mutableListOf<DailySpending>()
        var running = ExactTotal.zero(Currency.COP)
        for (day in 1..dayCount) {
            val date = month.atDay(day)
            val spent = when (val sum = total(byDate[date].orEmpty().map { it.amount.money })) {
                is Outcome.Failure -> return sum
                is Outcome.Success -> sum.value
            }
            running += spent
            val cumulative = when (val sum = running.toMoney()) {
                is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Unrepresentable)
                is Outcome.Success -> sum.value
            }
            daily += DailySpending(date, spent, cumulative)
        }
        return Outcome.Success(
            ResumenSnapshot(
                budget,
                income,
                netFlow,
                allocated,
                unallocated,
                limited?.let { MonthlyBudgetPolicy.pastVerdict(it.budget.limit, budget.spent) },
                daily,
                snapshots.map { snapshot ->
                    val overall = snapshot.overall as? OverallBudget.Limited
                    SpendingMonth(
                        snapshot.month,
                        snapshot.spent,
                        overall?.budget?.limit,
                        (overall?.budget?.standing as? BudgetStanding.Current)?.forecast,
                        snapshot.categories.sumOf { it.expenseCount }
                    )
                },
                incomes.count { YearMonth.from(it.date) == month && it.date > today.date },
                applicable.size,
                comparison = comparison(month, currentMonth, today, months, snapshots),
                normalMonth = normalMonth(currentMonth, snapshots),
                weekdays = weekdays(month, currentMonth, today, months),
                suggestion = if (month == currentMonth) suggestion(snapshots) else null
            )
        )
    }

    /**
     * Against the month before, only when it has something recorded. The month in
     * progress compares day 1 to today with the same days before, capped at the earlier
     * month's length; a finished month compares whole months.
     */
    private fun comparison(
        month: YearMonth,
        currentMonth: YearMonth,
        today: FinancialDay,
        months: List<ResumenMonthInput>,
        snapshots: List<MonthlySnapshot>
    ): SpendingComparison? {
        if (snapshots.size < 2) return null
        val before = snapshots[snapshots.size - 2]
        if (before.categories.sumOf { it.expenseCount } == 0) return null
        val previous = month.minusMonths(1)
        val inProgress = month == currentMonth
        val throughDay = if (inProgress) today.date.dayOfMonth else null
        val previousThroughDay = throughDay?.let { minOf(it, previous.lengthOfMonth()) }
        val spent = snapshots.last().spent.minorUnits.toBigInteger()
        val previousSpent = if (previousThroughDay == null) {
            before.spent.minorUnits.toBigInteger()
        } else {
            sum(
                months[months.size - 2].expenses.filter {
                    YearMonth.from(it.date) == previous && it.date.dayOfMonth <= previousThroughDay
                }
            )
        }
        val difference = (spent - previousSpent).abs()
        if (difference.bitLength() >= Long.SIZE_BITS) return null
        return SpendingComparison(
            previous,
            Money.cop(spent.toLong()),
            Money.cop(previousSpent.toLong()),
            throughDay,
            previousThroughDay,
            Money.cop(difference.toLong()),
            when (spent.compareTo(previousSpent)) {
                1 -> SpendingDirection.More
                -1 -> SpendingDirection.Less
                else -> SpendingDirection.Same
            },
            previousSpent.takeIf { it.signum() > 0 }?.let {
                BigDecimal(difference * THOUSAND).divide(BigDecimal(it), 0, RoundingMode.HALF_UP)
                    .toBigIntegerExact()
            }
        )
    }

    /**
     * The months shown run from the first with something recorded. Of those, the
     * complete ones with something recorded are averaged: a month with nothing recorded
     * has no data and is not a zero.
     */
    private fun normalMonth(
        currentMonth: YearMonth,
        snapshots: List<MonthlySnapshot>
    ): NormalMonth? {
        val complete = snapshots.filter {
            it.month < currentMonth && it.categories.sumOf { line -> line.expenseCount } > 0
        }
        if (complete.isEmpty()) return null
        return NormalMonth(
            average(
                complete.fold(BigInteger.ZERO) { total, it ->
                    total +
                        it.spent.minorUnits.toBigInteger()
                },
                complete.size
            ),
            complete.size
        )
    }

    /**
     * From the first day with something recorded, but no further back than the start of
     * the fifth month before (six months in all), to today or the end of a finished
     * month. Each weekday's spending over the number of times it came round; shown once
     * a week has passed.
     */
    private fun weekdays(
        month: YearMonth,
        currentMonth: YearMonth,
        today: FinancialDay,
        months: List<ResumenMonthInput>
    ): WeekdayPattern? {
        val through = if (month == currentMonth) today.date else month.atEndOfMonth()
        val earliest = month.minusMonths(WEEKDAY_MONTHS_BEFORE).atDay(1)
        val recorded = months.flatMap { input ->
            input.expenses.filter { YearMonth.from(it.date) == input.month && it.date <= through }
        }
        val first = recorded.minOfOrNull { it.date } ?: return null
        val from = maxOf(first, earliest)
        val counted = recorded.filter { it.date >= from }
        if (ChronoUnit.DAYS.between(from, through) + 1 < DAYS_IN_A_WEEK) return null
        val occurrences = generateSequence(from) { it.plusDays(1) }
            .takeWhile { it <= through }
            .groupingBy { it.dayOfWeek }
            .eachCount()
        val byDay = counted.groupBy { it.date.dayOfWeek }
        val days = DayOfWeek.entries.map { day ->
            WeekdaySpending(day, average(sum(byDay[day].orEmpty()), occurrences.getValue(day)))
        }
        val highest = days.maxOf { it.average.minorUnits }
        val busiest = days.singleOrNull { it.average.minorUnits == highest }
            ?.takeIf { highest > 0 }?.day
        return WeekdayPattern(from, through, days, busiest, lastSixMonths = from == earliest)
    }

    /**
     * A category still taking expenses, with a finite limit now, that went over its
     * limit in each of the two complete months before this one. When several do, the
     * one with the bigger limit now; equal limits go to the one listed first.
     */
    private fun suggestion(snapshots: List<MonthlySnapshot>): LimitAdjustment? {
        if (snapshots.size < 3) return null
        val now = snapshots.last()
        val before = snapshots.subList(snapshots.size - 3, snapshots.size - 1)
        return now.categories.mapNotNull { line ->
            if (line.category.archived) return@mapNotNull null
            val limit = line.budget?.limit ?: return@mapNotNull null
            val over = before.map { month ->
                month.categories.firstOrNull { it.category.id == line.category.id }
                    ?.takeIf {
                        it.budget != null && it.spent.minorUnits > it.budget.limit.minorUnits
                    }
                    ?: return@mapNotNull null
            }
            LimitAdjustment(
                line.category.id,
                line.category.name,
                before.map { it.month },
                average(
                    over.fold(BigInteger.ZERO) { total, it ->
                        total +
                            it.spent.minorUnits.toBigInteger()
                    },
                    over.size
                ),
                limit
            )
        }.maxByOrNull { it.limit.minorUnits }
    }

    private fun sum(expenses: List<Expense>): BigInteger =
        expenses.fold(BigInteger.ZERO) { total, it ->
            total +
                it.amount.money.minorUnits.toBigInteger()
        }

    /** Half up to whole pesos, as category detail's averages. */
    private fun average(total: BigInteger, count: Int): Money = Money.cop(
        BigDecimal(total).divide(BigDecimal(count), 0, RoundingMode.HALF_UP).longValueExact()
    )

    private val THOUSAND = BigInteger.valueOf(1000)
    private const val DAYS_IN_A_WEEK = 7L

    /** Six months in all, this one included. */
    private const val WEEKDAY_MONTHS_BEFORE = 5L

    private fun total(amounts: List<Money>): Outcome<Money, SnapshotFailure> =
        when (val sum = ExactTotal.of(Currency.COP, amounts).toMoney()) {
            is Outcome.Success -> sum
            is Outcome.Failure -> Outcome.Failure(SnapshotFailure.Unrepresentable)
        }
}
