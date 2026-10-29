package com.software.sello.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * The largest base limit a category in a chain may have, 999.999.999.999 (ADR 0020 CP25):
 * the transaction cap, so months passing can never overflow a chain with no command to
 * refuse. The calculation itself does not apply it (damaged data must fail as
 * unrepresentable, SC40); commands that enable carry or edit a limit do.
 */
const val MAX_CHAIN_BASE: Long = 999_999_999_999L

/**
 * One run of months in which a category carries its balance (ADR 0020 CP4): the months
 * from [start] up to, not including, [end], or every month from [start] while [end] is
 * null. The balance arriving in [start] is 0. [end] is the first month that no longer
 * carries; a chain turned off in its start month does not exist.
 */
@ConsistentCopyVisibility
data class CarryChain internal constructor(
    val categoryId: CategoryId,
    val start: YearMonth,
    val end: YearMonth?
) {
    /** Whether [month] is one of the months this chain carries into. */
    fun covers(month: YearMonth): Boolean = month >= start && (end == null || month < end)

    companion object {
        fun of(
            categoryId: CategoryId,
            start: YearMonth,
            end: YearMonth? = null
        ): Outcome<CarryChain, CarryoverFailure> = if (end != null && end <= start) {
            Outcome.Failure(CarryoverFailure.InvalidChain(categoryId, start, end))
        } else {
            Outcome.Success(CarryChain(categoryId, start, end))
        }
    }
}

/**
 * Whether a category carries is judged on what was recorded, not on what is known:
 * the spending of every month from [from] to [through] was read successfully, so a month
 * in that range with no expenses is a genuine zero. Any other month is unknown, and
 * asking for it is a typed failure, never a zero (ADR 0020 CP6, CP8).
 */
class SpendingHistory private constructor(
    val from: YearMonth,
    val through: YearMonth,
    private val byMonth: Map<YearMonth, Money>
) {
    fun spent(month: YearMonth): Outcome<Money, CarryoverFailure> =
        if (month < from || month > through) {
            Outcome.Failure(CarryoverFailure.MissingHistory(month))
        } else {
            Outcome.Success(byMonth[month] ?: ZERO)
        }

    /**
     * The same history with one more expense of [amount] dated [date], as a correction
     * or a catch-up would add. A date outside the history is its own failure.
     */
    fun plus(date: LocalDate, amount: Money): Outcome<SpendingHistory, CarryoverFailure> {
        val month = YearMonth.from(date)
        val current = when (val known = spent(month)) {
            is Outcome.Success -> known.value
            is Outcome.Failure -> return known
        }
        val total = when (val sum = current + amount) {
            is Outcome.Success -> sum.value
            is Outcome.Failure -> return Outcome.Failure(sum.error.toCarryover())
        }
        return Outcome.Success(SpendingHistory(from, through, byMonth + (month to total)))
    }

    companion object {
        private val ZERO = Money.cop(0)

        /**
         * The history of [categoryId] from [from] to [through], read from [expenses]:
         * only that category's, only dated up to [asOf] (the financial day), the same
         * rule as the month's own `S` (D03).
         */
        fun of(
            categoryId: CategoryId,
            from: YearMonth,
            through: YearMonth,
            asOf: LocalDate,
            expenses: List<Expense>
        ): Outcome<SpendingHistory, CarryoverFailure> {
            if (through < from) {
                return Outcome.Failure(CarryoverFailure.InvalidHistoryRange(from, through))
            }
            val totals = HashMap<YearMonth, Money>()
            val grouped = expenses
                .filter { it.categoryId == categoryId && it.date <= asOf }
                .groupBy { YearMonth.from(it.date) }
                .filterKeys { it >= from && it <= through }
            for ((month, of) in grouped) {
                totals[month] = when (
                    val sum = ExactTotal.of(Currency.COP, of.map { it.amount.money }).toMoney()
                ) {
                    is Outcome.Success -> sum.value
                    is Outcome.Failure -> return Outcome.Failure(sum.error.toCarryover())
                }
            }
            return Outcome.Success(SpendingHistory(from, through, totals))
        }
    }
}

/**
 * Everything the calculation reads about one category: its [chains], its whole base
 * history ([defaults] and the [overrides] that win over them, as for any month) and its
 * [spending]. All of it is read together at one revision; nothing here is a clock.
 */
class CategoryCarryInput(
    val categoryId: CategoryId,
    val chains: List<CarryChain>,
    val defaults: List<DefaultLimit>,
    val overrides: List<MonthLimit>,
    val spending: SpendingHistory
)

/** Whether a month with carry is judged as a finished month or the month in progress. */
sealed interface CarryVerdict {
    data class Closed(val verdict: PastVerdict) : CarryVerdict

    data class InProgress(val verdict: Verdict) : CarryVerdict
}

/**
 * One month of a chain, every figure signed and exact (ADR 0020 "Figures"): the [base]
 * `B`, the [carryIn] `K`, the [capacity] `C = B + K`, what was [spent] `S` and the
 * [remaining] `R = C − S`. A closed month's [remaining] is the next month's [carryIn].
 * The carry is a planning figure: never income, never an expense.
 */
data class CarryMonth(
    val month: YearMonth,
    val chainStart: YearMonth,
    val base: Money,
    val carryIn: Money,
    val capacity: Money,
    val spent: Money,
    val remaining: Money,
    val verdict: CarryVerdict
)

/**
 * The balance a chain left behind when it ended in [endMonth]: the carry-in the next
 * month would have had (ADR 0020 CP12). Derived, so a correction shows up in it.
 */
data class SettledBalance(val chainStart: YearMonth, val endMonth: YearMonth, val balance: Money)

/** Every month of a category's chains up to the month asked for, in month order. */
data class CarryReplay(val months: List<CarryMonth>, val settled: List<SettledBalance>)

/**
 * What a month's carry-in is, with the chain it comes from. Only for a month inside a
 * chain; a month outside one has no [CarryEntry] and its capacity is its base.
 */
data class CarryEntry(val carryIn: Money, val chainStart: YearMonth)

/**
 * The parts of a limited budget that carry splits (ADR 0020 CP27), shown beside
 * [LimitedBudget.limit], which is then the [capacity]. The overall budget has no chain of
 * its own, so its [chainStart] is null and its parts are sums.
 */
data class CarryBreakdown(
    val base: Money,
    val carryIn: Money,
    val capacity: Money,
    val chainStart: YearMonth?
)

/** Why a carry figure could not be calculated. Never shown as zero or as the base alone. */
sealed interface CarryoverFailure {
    /** A month's spending was not read, which is not the same as nothing spent. */
    data class MissingHistory(val month: YearMonth) : CarryoverFailure

    /** A month of a chain has no finite base: unlimited, paused or never configured. */
    data class NoFiniteBase(val month: YearMonth) : CarryoverFailure

    /** A figure does not fit a signed `Long`. Nothing is clipped, saturated or zeroed. */
    data object Unrepresentable : CarryoverFailure

    data class CurrencyMismatch(val expected: Currency, val actual: Currency) : CarryoverFailure

    /** Two chains of the category share [month]. */
    data class OverlappingChains(val categoryId: CategoryId, val month: YearMonth) :
        CarryoverFailure

    /** A chain that ends in or before the month it starts, or belongs to another category. */
    data class InvalidChain(val categoryId: CategoryId, val start: YearMonth, val end: YearMonth?) :
        CarryoverFailure

    data class InvalidHistoryRange(val from: YearMonth, val through: YearMonth) : CarryoverFailure

    /** Months after the current financial month cannot be calculated. */
    data class FutureMonth(val currentMonth: YearMonth) : CarryoverFailure
}

internal fun MoneyError.toCarryover(): CarryoverFailure = when (this) {
    MoneyError.Overflow -> CarryoverFailure.Unrepresentable
    is MoneyError.CurrencyMismatch -> CarryoverFailure.CurrencyMismatch(expected, actual)
}

/** Why carry cannot be turned on (ADR 0020 CP3). Nothing changes. */
sealed interface CarryEnableRefusal {
    data object AlreadyOn : CarryEnableRefusal

    data object Archived : CarryEnableRefusal

    /** The category has an unlimited limit this month. */
    data object UnlimitedThisMonth : CarryEnableRefusal

    /** No limit this month: paused, or earlier than anything configured. */
    data object NoLimitThisMonth : CarryEnableRefusal

    /**
     * The default in force after this month is not finite, so the chain would reach a
     * month with no base through no command that could ask for the confirmation (SC46).
     */
    data object DefaultAfterNotFinite : CarryEnableRefusal

    /** A base above [MAX_CHAIN_BASE] (CP25). */
    data class BaseAboveLimit(val base: Money) : CarryEnableRefusal
}

/** What dropping the balance shows before it is confirmed (ADR 0020 CP10). */
data class DropPreview(
    val month: YearMonth,
    val carryIn: Money,
    val base: Money,
    val capacityBefore: Money,
    val capacityAfter: Money,
    val remainingBefore: Money,
    val remainingAfter: Money
)

/** The answer to turning carry off, archiving or making the limit unlimited (CP9–CP11). */
sealed interface DropDecision {
    /** No chain carries into the month: there is nothing to drop. */
    data object NotCarrying : DropDecision

    /**
     * The balance is not zero and was not confirmed, or the one confirmed is no longer
     * the balance: nothing changes and the person sees [preview].
     */
    data class ConfirmationRequired(val preview: DropPreview) : DropDecision

    /** The chains after the drop. [dropped] is the carry-in that stopped counting (maybe 0). */
    data class Dropped(val chains: List<CarryChain>, val dropped: Money) : DropDecision
}
