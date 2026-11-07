package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.domain.model.YearCategory
import com.software.sello.domain.model.YearComparison
import com.software.sello.domain.model.YearMonthLine
import com.software.sello.domain.model.YearMonthStatus
import com.software.sello.domain.model.YearSnapshot
import com.software.sello.domain.port.FinancialDay
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

/**
 * What Tu año reads for one year: each month shown ([YearPolicy.monthsShown]) as Resumen
 * reads a month, with its carry; the year's [incomes]; the year before's expenses through
 * [YearPolicy.previousThrough]; and the first day anything was recorded, if any.
 */
data class YearInput(
    val months: List<ResumenMonthInput>,
    val incomes: List<Income>,
    val previousExpenses: List<Expense>,
    val firstRecorded: LocalDate?
)

/**
 * Tu año. Every month is the same snapshot Resumen and Recibo work out, so a month's verdict
 * is its own past verdict from its limit and carry (ADR 0012 BG1); nothing here is a new
 * rule for a month. A year in progress counts up to today and is never projected.
 */
object YearPolicy {
    /** January to the month in progress in the current year; the whole of an earlier year. */
    fun monthsShown(year: Int, today: FinancialDay): List<YearMonth> {
        val last = if (year == today.date.year) today.date.monthValue else MONTHS
        return (1..last).map { YearMonth.of(year, it) }
    }

    /** The last day of [year] compared: today in the year in progress, else 31 December. */
    fun through(year: Int, today: FinancialDay): LocalDate =
        if (year == today.date.year) today.date else LocalDate.of(year, Month.DECEMBER, 31)

    /**
     * The same day of the year before, capped at its own month's length as Resumen caps
     * month ends: 29 February becomes 28 February.
     */
    fun previousThrough(year: Int, today: FinancialDay): LocalDate {
        val through = through(year, today)
        val month = YearMonth.of(year - 1, through.month)
        return month.atDay(minOf(through.dayOfMonth, month.lengthOfMonth()))
    }

    fun snapshot(
        year: Int,
        today: FinancialDay,
        generation: Long,
        revision: Long,
        input: YearInput
    ): Outcome<YearSnapshot, SnapshotFailure> {
        val currentMonth = YearMonth.from(today.date)
        if (year >
            today.date.year
        ) {
            return Outcome.Failure(SnapshotFailure.FutureMonth(currentMonth))
        }
        require(input.months.map { it.month } == monthsShown(year, today))
        val snapshots = mutableListOf<MonthlySnapshot>()
        for (month in input.months) {
            when (
                val snapshot = MonthlyBudgetPolicy.snapshot(
                    month.month,
                    today,
                    generation,
                    revision,
                    month.categories,
                    month.expenses,
                    carryover = month.carryover
                )
            ) {
                is Outcome.Failure -> return snapshot
                is Outcome.Success -> snapshots += snapshot.value
            }
        }
        val through = through(year, today)
        val spent = when (val sum = total(snapshots.map { it.spent })) {
            is Outcome.Failure -> return sum
            is Outcome.Success -> sum.value
        }
        val incomes = input.incomes.filter { it.date.year == year && it.date <= through }
        val income = when (val sum = total(incomes.map { it.amount.money })) {
            is Outcome.Failure -> return sum
            is Outcome.Success -> sum.value
        }
        val netFlow = when (val difference = income - spent) {
            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Unrepresentable)
            is Outcome.Success -> difference.value
        }
        val categories = when (val ranked = categories(snapshots, spent)) {
            is Outcome.Failure -> return ranked
            is Outcome.Success -> ranked.value
        }
        val comparison = when (
            val compared = comparison(
                year,
                today,
                spent,
                input.previousExpenses,
                input.firstRecorded
            )
        ) {
            is Outcome.Failure -> return compared
            is Outcome.Success -> compared.value
        }
        val firstYear = minOf(input.firstRecorded?.year ?: today.date.year, today.date.year)
        return Outcome.Success(
            YearSnapshot(
                year = year,
                asOf = today.date,
                generation = generation,
                revision = revision,
                firstYear = firstYear,
                spent = spent,
                income = income,
                netFlow = netFlow,
                incomeCount = incomes.size,
                expenseCount = snapshots.sumOf { month ->
                    month.categories.sumOf { it.expenseCount }
                },
                recordedDays = input.months
                    .flatMap { month -> month.expenses.map { it.date } }
                    .filter { it.year == year && it <= through }
                    .plus(incomes.map { it.date })
                    .toSet()
                    .size,
                months = (1..MONTHS).map { number ->
                    val month = YearMonth.of(year, number)
                    val snapshot = snapshots.getOrNull(number - 1)
                    when {
                        snapshot == null -> YearMonthLine(month, null, YearMonthStatus.Upcoming)

                        month == currentMonth ->
                            YearMonthLine(month, snapshot.spent, YearMonthStatus.InProgress)

                        else -> YearMonthLine(
                            month,
                            snapshot.spent,
                            YearMonthStatus.Finished(
                                (
                                    (snapshot.overall as? OverallBudget.Limited)?.budget?.standing
                                        as? BudgetStanding.Past
                                    )?.verdict
                            )
                        )
                    }
                },
                comparison = comparison,
                categories = categories
            )
        )
    }

    /**
     * Against the year before on the same days, only when that year has data for them: the
     * first record (expense or income) is on or before its 1 January, and it has an expense
     * on those days. A year begun part way, or with nothing then, is not a zero to compare
     * with (orchestrator ruling on PR 439). A total that does not fit is a typed failure.
     */
    private fun comparison(
        year: Int,
        today: FinancialDay,
        spent: Money,
        previousExpenses: List<Expense>,
        firstRecorded: LocalDate?
    ): Outcome<YearComparison?, SnapshotFailure> {
        val previousStart = LocalDate.of(year - 1, Month.JANUARY, 1)
        if (firstRecorded == null || firstRecorded > previousStart) return Outcome.Success(null)
        val previousThrough = previousThrough(year, today)
        val compared = previousExpenses.filter {
            it.date.year == year - 1 && it.date <= previousThrough
        }
        if (compared.isEmpty()) return Outcome.Success(null)
        val current = spent.minorUnits.toBigInteger()
        val previous = compared.fold(BigInteger.ZERO) { total, it ->
            total + it.amount.money.minorUnits.toBigInteger()
        }
        val difference = (current - previous).abs()
        if (previous.bitLength() >= Long.SIZE_BITS || difference.bitLength() >= Long.SIZE_BITS) {
            return Outcome.Failure(SnapshotFailure.Unrepresentable)
        }
        return Outcome.Success(comparisonOf(year, today, spent, previous, difference))
    }

    private fun comparisonOf(
        year: Int,
        today: FinancialDay,
        spent: Money,
        previous: BigInteger,
        difference: BigInteger
    ): YearComparison {
        val current = spent.minorUnits.toBigInteger()
        return YearComparison(
            previousYear = year - 1,
            spent = spent,
            previousSpent = Money.cop(previous.toLong()),
            through = through(year, today),
            previousThrough = previousThrough(year, today),
            wholeYears = year != today.date.year,
            difference = Money.cop(difference.toLong()),
            direction = when (current.compareTo(previous)) {
                1 -> SpendingDirection.More
                -1 -> SpendingDirection.Less
                else -> SpendingDirection.Same
            },
            percentTenths = ResumenPolicy.percentTenths(difference, previous)
        )
    }

    /**
     * Each category's months added up, archived ones included, most first; equal totals
     * keep the order the months list them in. Its name and icon are as last seen.
     */
    private fun categories(
        snapshots: List<MonthlySnapshot>,
        spent: Money
    ): Outcome<List<YearCategory>, SnapshotFailure> {
        val seen = LinkedHashMap<CategoryId, Category>()
        val amounts = LinkedHashMap<CategoryId, MutableList<Money>>()
        snapshots.forEach { month ->
            month.categories.filter { it.expenseCount > 0 }.forEach { line ->
                seen[line.category.id] = line.category
                amounts.getOrPut(line.category.id) { mutableListOf() } += line.spent
            }
        }
        val whole = spent.minorUnits.toBigInteger()
        val ranked = ArrayList<YearCategory>(seen.size)
        for ((id, category) in seen) {
            val total = when (val sum = total(amounts.getValue(id))) {
                is Outcome.Failure -> return sum
                is Outcome.Success -> sum.value
            }
            ranked += YearCategory(category, total, percent(total, whole))
        }
        return Outcome.Success(ranked.sortedByDescending { it.spent.minorUnits })
    }

    /** A whole per cent of [whole], half up. */
    private fun percent(part: Money, whole: BigInteger): Int = if (whole.signum() <= 0) {
        0
    } else {
        BigDecimal(part.minorUnits.toBigInteger() * HUNDRED)
            .divide(BigDecimal(whole), 0, RoundingMode.HALF_UP)
            .intValueExact()
    }

    private fun total(amounts: List<Money>): Outcome<Money, SnapshotFailure> =
        when (val sum = ExactTotal.of(Currency.COP, amounts).toMoney()) {
            is Outcome.Success -> sum
            is Outcome.Failure -> Outcome.Failure(SnapshotFailure.Unrepresentable)
        }

    private const val MONTHS = 12
    private val HUNDRED = BigInteger.valueOf(100)
}
