package com.software.sello.domain.policy

import com.software.sello.domain.model.CarryMonth
import com.software.sello.domain.model.CarryReplay
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ReservedPayment
import com.software.sello.domain.model.SettledBalance
import com.software.sello.domain.model.SpendingHistory
import java.math.BigInteger
import java.time.YearMonth

/** One month of one category's chain, as it is before and after a correction. */
data class CarryMonthChange(
    val categoryId: CategoryId,
    val month: YearMonth,
    val before: CarryMonth,
    val after: CarryMonth
)

/** A balance a chain settled when it ended (CP12) that a correction changes. */
data class SettledBalanceChange(
    val categoryId: CategoryId,
    val before: SettledBalance,
    val after: SettledBalance
)

/** One category's input as it is and as it would be with the correction applied. */
class CategoryCorrection(val before: CategoryCarryInput, val after: CategoryCarryInput)

/**
 * What a correction does to the chains it reaches (ADR 0020 CP20–CP22).
 *
 * [months] is the list shown before saving: each month whose carry-in changes, by month and
 * then category, with every figure and verdict before and after; a month whose carry-in does
 * not change is not in it. [edited] holds the months whose own figures change with the
 * carry-in unchanged, which is where the edit itself is. [settled] names the balances of
 * ended chains that change. [firstAffected] is the earliest month with any change: from there
 * to the end of each chain is the affected range. [overallCapacityChange] is, for each month,
 * the sum of the capacity changes of the categories corrected, left out where it is zero.
 */
data class CarryCorrection(
    val months: List<CarryMonthChange>,
    val edited: List<CarryMonthChange>,
    val settled: List<SettledBalanceChange>,
    val firstAffected: YearMonth?,
    val overallCapacityChange: Map<YearMonth, Money>
) {
    /** True when no later month and no settled balance changes: only the D04 confirmation is needed. */
    val isEmpty: Boolean get() = months.isEmpty() && settled.isEmpty()
}

/**
 * Corrections to recorded history on chains of months. The replay of the corrected input is
 * compared with the replay as it stands, so the list a person confirms and the figures saved
 * come from the same calculation. Reads and writes are the caller's; nothing here is stored.
 */
object CarryoverCorrectionReplay {
    /**
     * Each of [corrections] replayed up to [currentMonth] before and after. A move between
     * categories is two corrections. Any month the replays need and cannot get fails the
     * whole preview; nothing is listed for the months that could be read.
     */
    fun preview(
        currentMonth: YearMonth,
        corrections: List<CategoryCorrection>
    ): Outcome<CarryCorrection, CarryoverFailure> {
        val months = ArrayList<CarryMonthChange>()
        val edited = ArrayList<CarryMonthChange>()
        val settled = ArrayList<SettledBalanceChange>()
        val overall = HashMap<YearMonth, BigInteger>()
        for (correction in corrections) {
            val id = correction.before.categoryId
            val before = when (
                val read = SignedCarryoverPolicy.replay(
                    correction.before,
                    currentMonth,
                    currentMonth
                )
            ) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return read
            }
            val after = when (
                val read = SignedCarryoverPolicy.replay(
                    correction.after,
                    currentMonth,
                    currentMonth
                )
            ) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return read
            }
            compare(id, before, after, months, edited, overall)
            settled += settledChanges(id, before, after)
        }
        val byMonthThenCategory = compareBy<CarryMonthChange>({ it.month }, { it.categoryId.value })
        val capacity = HashMap<YearMonth, Money>()
        for ((month, delta) in overall) {
            if (delta.signum() == 0) continue
            if (delta.bitLength() >= Long.SIZE_BITS) {
                return Outcome.Failure(CarryoverFailure.Unrepresentable)
            }
            capacity[month] = Money.cop(delta.toLong())
        }
        val sortedMonths = months.sortedWith(byMonthThenCategory)
        val sortedEdited = edited.sortedWith(byMonthThenCategory)
        return Outcome.Success(
            CarryCorrection(
                months = sortedMonths,
                edited = sortedEdited,
                settled = settled.sortedWith(
                    compareBy({
                        it.before.endMonth
                    }, { it.categoryId.value })
                ),
                firstAffected = (sortedMonths + sortedEdited).minOfOrNull { it.month },
                overallCapacityChange = capacity.toSortedMap()
            )
        )
    }

    /**
     * What recording [payments] does when catch-up records them (CP18, SC37, SC47): the
     * occurrences of [before]'s category dated in an earlier month of one of its chains are
     * added to that month's spending, and the result is the notice's list. It is derived
     * from the listed occurrences, so an occurrence taken off the list leaves the notice.
     */
    fun catchUp(
        before: CategoryCarryInput,
        payments: List<ReservedPayment>,
        currentMonth: YearMonth
    ): Outcome<CarryCorrection, CarryoverFailure> {
        var spending: SpendingHistory = before.spending
        for (payment in payments) {
            val month = YearMonth.from(payment.occurrence.date)
            if (payment.categoryId != before.categoryId || month >= currentMonth) continue
            if (before.chains.none { it.covers(month) }) continue
            spending =
                when (val added = spending.plus(payment.occurrence.date, payment.amount.money)) {
                    is Outcome.Success -> added.value
                    is Outcome.Failure -> return added
                }
        }
        val after = CategoryCarryInput(
            before.categoryId,
            before.chains,
            before.defaults,
            before.overrides,
            spending
        )
        return preview(currentMonth, listOf(CategoryCorrection(before, after)))
    }

    private fun compare(
        id: CategoryId,
        before: CarryReplay,
        after: CarryReplay,
        months: MutableList<CarryMonthChange>,
        edited: MutableList<CarryMonthChange>,
        overall: MutableMap<YearMonth, BigInteger>
    ) {
        val later = after.months.associateBy { it.month }
        for (was in before.months) {
            val now = later[was.month] ?: continue
            if (was == now) continue
            val change = CarryMonthChange(id, was.month, was, now)
            if (was.carryIn != now.carryIn) months += change else edited += change
            val delta =
                now.capacity.minorUnits.toBigInteger() - was.capacity.minorUnits.toBigInteger()
            overall[was.month] = (overall[was.month] ?: BigInteger.ZERO) + delta
        }
    }

    private fun settledChanges(
        id: CategoryId,
        before: CarryReplay,
        after: CarryReplay
    ): List<SettledBalanceChange> {
        val later = after.settled.associateBy { it.chainStart }
        return before.settled.mapNotNull { was ->
            val now = later[was.chainStart]
            if (now == null || now == was) null else SettledBalanceChange(id, was, now)
        }
    }
}
