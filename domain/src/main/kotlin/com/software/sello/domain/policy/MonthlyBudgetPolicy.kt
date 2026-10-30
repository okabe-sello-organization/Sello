package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.CarryBreakdown
import com.software.sello.domain.model.CarryEntry
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DailyAllowance
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Forecast
import com.software.sello.domain.model.LimitedBudget
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.ReservedPayment
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.port.FinancialDay
import java.math.BigInteger
import java.time.YearMonth

/** A category with its budget state in the month being read. */
data class CategoryInMonth(val category: Category, val state: MonthBudgetState)

/**
 * The one place a month's figures are worked out (decision D03). Screens, charts and
 * later alerts take them from the snapshot and never recompute them.
 *
 * With `S` spent, `L` the sum of finite limits, `d` today's day of the month and `N`
 * the month's length: the verdict compares `S` with `L` (ADR 0012 BG1); the forecast
 * `S × N / d` is only an estimate for Resumen, today counting towards the pace but
 * not towards the days still to come; the days available are `N − d + 1`, today
 * included. All arithmetic is exact: no floating point, and comparisons are made on
 * whole numbers before anything is rounded for display.
 */
object MonthlyBudgetPolicy {
    private val FIFTY = BigInteger.valueOf(50)

    // Not BigInteger.TWO: that constant does not exist on Android 11.
    private val TWO = BigInteger.valueOf(2)

    /** `S × N / d` exactly, with the half-up whole-peso figure that is shown. */
    fun forecast(
        spent: Money,
        dayOfMonth: Int,
        monthLength: Int
    ): Outcome<Forecast, SnapshotFailure> {
        val numerator = spent.minorUnits.toBigInteger() * monthLength.toBigInteger()
        val denominator = dayOfMonth.toBigInteger()
        // Half up for a value that is never negative: floor((2n + d) / 2d).
        val rounded = (numerator * TWO + denominator) / (denominator * TWO)
        if (rounded.bitLength() >= Long.SIZE_BITS) {
            return Outcome.Failure(SnapshotFailure.Unrepresentable)
        }
        return Outcome.Success(
            Forecast(numerator, dayOfMonth, Money(rounded.toLong(), spent.currency))
        )
    }

    /**
     * What was actually spent against the limit, whatever the pace. Exactly a positive
     * limit is at the limit; a zero limit with nothing spent is on track, so that an
     * untouched zero budget does not read as used up.
     */
    fun verdict(limit: Money, spent: Money): Verdict = when {
        spent.minorUnits > limit.minorUnits -> Verdict.Over
        spent.minorUnits == limit.minorUnits && limit.minorUnits > 0 -> Verdict.AtLimit
        else -> Verdict.OnTrack
    }

    fun pastVerdict(limit: Money, spent: Money): PastVerdict =
        if (spent.minorUnits > limit.minorUnits) PastVerdict.Over else PastVerdict.WithinLimit

    /** `50 × floor(max(remaining, 0) / (50 × availableDays))`: down, never to the nearest. */
    fun allowance(remaining: Money, availableDays: Int): DailyAllowance {
        val available = remaining.minorUnits.toBigInteger().max(BigInteger.ZERO)
        val perDay = available / (FIFTY * availableDays.toBigInteger()) * FIFTY
        // Never more than what remains, so it always fits.
        return DailyAllowance(Money(perDay.toLong(), remaining.currency), availableDays)
    }

    /**
     * [categories] is every category with its state in [month] and [expenses] every
     * expense dated in [month], all read at the same [revision]. A category is listed
     * when it is not archived, or has a limit or spending that month, so an archived
     * one stays wherever it explains a figure and nowhere else.
     *
     * A category with a [carryover] input that has a chain covering [month] is judged on its
     * capacity `C = B + K` instead of its base (ADR 0020 CP14–CP19); the overall budget is
     * then `Σ B + Σ K`. A figure the carry needs and cannot get fails the snapshot. With no
     * input, or no chain over [month], the answer is exactly the one without carryover.
     */
    fun snapshot(
        month: YearMonth,
        today: FinancialDay,
        generation: Long,
        revision: Long,
        categories: List<CategoryInMonth>,
        expenses: List<Expense>,
        reserved: List<ReservedPayment> = emptyList(),
        carryover: List<CategoryCarryInput> = emptyList()
    ): Outcome<MonthlySnapshot, SnapshotFailure> {
        val currentMonth = YearMonth.from(today.date)
        if (month > currentMonth) return Outcome.Failure(SnapshotFailure.FutureMonth(currentMonth))
        val isCurrent = month == currentMonth
        val counted = expenses.filter { YearMonth.from(it.date) == month && it.date <= today.date }
        val future = expenses.count { YearMonth.from(it.date) == month && it.date > today.date }
        val byCategory = counted.groupBy { it.categoryId }

        val lines = ArrayList<CategoryMonth>(categories.size)
        var limits: ExactTotal? = null
        var carries: ExactTotal? = null
        for (line in categories) {
            val of = byCategory[line.category.id].orEmpty()
            val spent = when (val total = total(of.map { it.amount.money })) {
                is Outcome.Success -> total.value
                is Outcome.Failure -> return total
            }
            val limit =
                ((line.state as? MonthBudgetState.Limited)?.limit as? BudgetLimit.Finite)?.amount
            val entry = when (
                val found = carryEntry(carryover, line.category.id, month, currentMonth, limit)
            ) {
                is Outcome.Success -> found.value
                is Outcome.Failure -> return found
            }
            val carried = when (val worked = carried(limit, entry)) {
                is Outcome.Success -> worked.value
                is Outcome.Failure -> return worked
            }
            val budget = (carried?.capacity ?: limit)?.let { capacity ->
                when (val assessed = budget(capacity, spent, month, today, isCurrent)) {
                    is Outcome.Success -> assessed.value.copy(carry = carried)
                    is Outcome.Failure -> return assessed
                }
            }
            if (limit != null) limits = (limits ?: ExactTotal.zero(Currency.COP)) + limit
            if (entry != null) carries = (carries ?: ExactTotal.zero(Currency.COP)) + entry.carryIn
            val shown = !line.category.archived ||
                line.state is MonthBudgetState.Limited ||
                of.isNotEmpty()
            if (shown) {
                lines += CategoryMonth(
                    category = line.category,
                    state = line.state,
                    spent = spent,
                    budget = budget,
                    expenseCount = of.size,
                    newestExpenseAddition = of.maxOfOrNull { it.createdAt }
                )
            }
        }

        val spent = when (val total = total(counted.map { it.amount.money })) {
            is Outcome.Success -> total.value
            is Outcome.Failure -> return total
        }
        val overall = when (val base = limits?.toMoney()) {
            null -> OverallBudget.NoLimit

            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Unrepresentable)

            is Outcome.Success -> {
                val breakdown = when (val worked = overallCarry(base.value, carries)) {
                    is Outcome.Success -> worked.value
                    is Outcome.Failure -> return worked
                }
                val budget = when (
                    val assessed = budget(
                        breakdown?.capacity ?: base.value,
                        spent,
                        month,
                        today,
                        isCurrent
                    )
                ) {
                    is Outcome.Success -> assessed.value.copy(carry = breakdown)
                    is Outcome.Failure -> return assessed
                }
                val availableDays = month.lengthOfMonth() - today.date.dayOfMonth + 1
                // Payments still to come are reserved in the month in progress only; the
                // daily figure is then from the margin they leave (rule 12).
                val reservation = when (
                    val reserving = ReservationPolicy.reservation(
                        budget.remaining,
                        if (isCurrent) reserved else emptyList()
                    )
                ) {
                    is Outcome.Success -> reserving.value
                    is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Unrepresentable)
                }
                OverallBudget.Limited(
                    budget,
                    if (isCurrent) {
                        allowance(reservation?.margin ?: budget.remaining, availableDays)
                    } else {
                        null
                    },
                    reservation
                )
            }
        }
        return Outcome.Success(
            MonthlySnapshot(
                month = month,
                asOf = today.date,
                zone = today.zone,
                generation = generation,
                revision = revision,
                categories = lines,
                spent = spent,
                overall = overall,
                futureDatedExpenses = future
            )
        )
    }

    /**
     * The month's `L`, as [snapshot] adds it, were [categoryId]'s limit [limit]
     * instead; with a null [categoryId], [limit] is a new category's. A null [limit]
     * adds nothing. Null when no category would have a finite limit.
     */
    fun limitTotalWith(
        categories: List<CategoryInMonth>,
        categoryId: CategoryId?,
        limit: BudgetLimit?
    ): Outcome<Money?, SnapshotFailure> {
        val others = categories.filter { categoryId == null || it.category.id != categoryId }
            .mapNotNull { ((it.state as? MonthBudgetState.Limited)?.limit as? BudgetLimit.Finite) }
        val finite = others + listOfNotNull(limit as? BudgetLimit.Finite)
        if (finite.isEmpty()) return Outcome.Success(null)
        return total(finite.map { it.amount })
    }

    /** [categoryId]'s carry into [month], or null when it is not carrying then. */
    private fun carryEntry(
        carryover: List<CategoryCarryInput>,
        categoryId: CategoryId,
        month: YearMonth,
        currentMonth: YearMonth,
        base: Money?
    ): Outcome<CarryEntry?, SnapshotFailure> {
        val input = carryover.firstOrNull { it.categoryId == categoryId }
            ?: return Outcome.Success(null)
        val entry = when (val found = SignedCarryoverPolicy.carryIn(input, month, currentMonth)) {
            is Outcome.Success -> found.value
            is Outcome.Failure -> return Outcome.Failure(found.error.toSnapshot(categoryId))
        }
        if (entry != null && base == null) {
            return Outcome.Failure(
                SnapshotFailure.Carryover(CarryoverFailure.NoFiniteBase(month), categoryId)
            )
        }
        return Outcome.Success(entry)
    }

    /** [base] split from [entry]'s carry into `C = B + K`, or null when the category is not carrying. */
    private fun carried(
        base: Money?,
        entry: CarryEntry?
    ): Outcome<CarryBreakdown?, SnapshotFailure> {
        if (base == null || entry == null) return Outcome.Success(null)
        return when (val sum = base + entry.carryIn) {
            is Outcome.Success ->
                Outcome.Success(CarryBreakdown(base, entry.carryIn, sum.value, entry.chainStart))

            is Outcome.Failure -> Outcome.Failure(SnapshotFailure.Unrepresentable)
        }
    }

    /** The overall `Σ B`, `Σ K` and `C`, or null when no category carries. */
    private fun overallCarry(
        base: Money,
        carries: ExactTotal?
    ): Outcome<CarryBreakdown?, SnapshotFailure> {
        if (carries == null) return Outcome.Success(null)
        val carry = when (val sum = carries.toMoney()) {
            is Outcome.Success -> sum.value
            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Unrepresentable)
        }
        val capacity = when (val sum = base + carry) {
            is Outcome.Success -> sum.value
            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Unrepresentable)
        }
        return Outcome.Success(CarryBreakdown(base, carry, capacity, chainStart = null))
    }

    private fun CarryoverFailure.toSnapshot(categoryId: CategoryId): SnapshotFailure = when (this) {
        CarryoverFailure.Unrepresentable -> SnapshotFailure.Unrepresentable
        else -> SnapshotFailure.Carryover(this, categoryId)
    }

    private fun total(amounts: List<Money>): Outcome<Money, SnapshotFailure> =
        when (val total = ExactTotal.of(Currency.COP, amounts).toMoney()) {
            is Outcome.Success -> total
            is Outcome.Failure -> Outcome.Failure(SnapshotFailure.Unrepresentable)
        }

    private fun budget(
        limit: Money,
        spent: Money,
        month: YearMonth,
        today: FinancialDay,
        isCurrent: Boolean
    ): Outcome<LimitedBudget, SnapshotFailure> {
        val remaining = when (val difference = limit - spent) {
            is Outcome.Success -> difference.value
            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Unrepresentable)
        }
        if (!isCurrent) {
            val standing = BudgetStanding.Past(pastVerdict(limit, spent))
            return Outcome.Success(LimitedBudget(limit, spent, remaining, standing))
        }
        return when (val estimate = forecast(spent, today.date.dayOfMonth, month.lengthOfMonth())) {
            is Outcome.Failure -> estimate

            is Outcome.Success -> {
                val standing = BudgetStanding.Current(estimate.value, verdict(limit, spent))
                Outcome.Success(LimitedBudget(limit, spent, remaining, standing))
            }
        }
    }
}
