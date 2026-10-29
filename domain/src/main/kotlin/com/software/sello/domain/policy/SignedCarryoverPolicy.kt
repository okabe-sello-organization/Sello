package com.software.sello.domain.policy

import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CarryEnableRefusal
import com.software.sello.domain.model.CarryEntry
import com.software.sello.domain.model.CarryMonth
import com.software.sello.domain.model.CarryReplay
import com.software.sello.domain.model.CarryVerdict
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.DropDecision
import com.software.sello.domain.model.DropPreview
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.MAX_CHAIN_BASE
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SettledBalance
import com.software.sello.domain.model.toCarryover
import java.time.YearMonth

/**
 * Signed budget carryover (ADR 0020, rules CP4–CP8, CP10, CP23–CP25): the one place a
 * chain of months is replayed. A month's carry-in is the remaining of the month before it
 * in the same chain, `K(m+1) = R(m)` with `R = C − S` and `C = B + K`, so an earlier
 * deficit always reaches later months. Everything is derived from the recorded spending,
 * the base history and the chains on every read; no clock is read here, the month in
 * progress comes in as [replay]'s `currentMonth`.
 *
 * Every figure is exact. `C`, `R` and each sum are checked against a signed `Long`; a
 * result that does not fit is [CarryoverFailure.Unrepresentable], never clipped, wrapped
 * or zeroed, and a month whose spending was not read, or that has no finite base, fails
 * instead of counting as zero.
 */
object SignedCarryoverPolicy {
    /**
     * Every month of [input]'s chains from its start up to [through], in month order,
     * with the balance each ended chain settled. [through] may not be after
     * [currentMonth]. [input]'s spending must cover every month it needs.
     */
    fun replay(
        input: CategoryCarryInput,
        currentMonth: YearMonth,
        through: YearMonth
    ): Outcome<CarryReplay, CarryoverFailure> {
        if (through > currentMonth) {
            return Outcome.Failure(CarryoverFailure.FutureMonth(currentMonth))
        }
        val chains = when (val checked = ordered(input)) {
            is Outcome.Success -> checked.value
            is Outcome.Failure -> return checked
        }
        val months = ArrayList<CarryMonth>()
        val settled = ArrayList<SettledBalance>()
        for (chain in chains) {
            if (chain.start > through) break
            val run = when (val worked = run(input, chain, currentMonth, through)) {
                is Outcome.Success -> worked.value
                is Outcome.Failure -> return worked
            }
            months += run
            val end = chain.end
            if (end != null && end <= through) {
                settled += SettledBalance(chain.start, end, run.last().remaining)
            }
        }
        return Outcome.Success(CarryReplay(months, settled))
    }

    /**
     * What [month] receives from a chain, or null when no chain covers it: then its
     * capacity is its base. Only the months before [month] are read, so the month in
     * progress needs none of its own spending to be known.
     */
    fun carryIn(
        input: CategoryCarryInput,
        month: YearMonth,
        currentMonth: YearMonth
    ): Outcome<CarryEntry?, CarryoverFailure> {
        if (month > currentMonth) {
            return Outcome.Failure(CarryoverFailure.FutureMonth(currentMonth))
        }
        val chains = when (val checked = ordered(input)) {
            is Outcome.Success -> checked.value
            is Outcome.Failure -> return checked
        }
        val chain = chains.firstOrNull { it.covers(month) } ?: return Outcome.Success(null)
        if (month == chain.start) return Outcome.Success(CarryEntry(ZERO, chain.start))
        // Only this chain: it starts at 0, so the months of earlier chains are not needed.
        val before = when (
            val worked = run(input, chain, currentMonth, month.minusMonths(1))
        ) {
            is Outcome.Success -> worked.value.last()
            is Outcome.Failure -> return worked
        }
        return Outcome.Success(CarryEntry(before.remaining, chain.start))
    }

    /**
     * Turning carry on in [month], the current financial month (CP2, CP3): the chains
     * with a new open one starting then, at carry-in 0 and counting the whole month. A
     * month's base needs a finite limit now and a finite default in force after it, and
     * neither may pass [MAX_CHAIN_BASE]. The refusal names the first reason and nothing changes.
     */
    fun enable(
        categoryId: CategoryId,
        archived: Boolean,
        month: YearMonth,
        chains: List<CarryChain>,
        defaults: List<DefaultLimit>,
        overrides: List<MonthLimit>
    ): Outcome<List<CarryChain>, CarryEnableRefusal> {
        if (chains.any { it.covers(month) }) return Outcome.Failure(CarryEnableRefusal.AlreadyOn)
        val own = defaults.filter { it.categoryId == categoryId }
        if (archived) return Outcome.Failure(CarryEnableRefusal.Archived)
        val state = CategoryBudgetPolicy.stateFor(
            month,
            own,
            overrides.firstOrNull { it.categoryId == categoryId && it.month == month }
        )
        val base = when (state) {
            MonthBudgetState.Paused, MonthBudgetState.Unconfigured ->
                return Outcome.Failure(CarryEnableRefusal.NoLimitThisMonth)

            is MonthBudgetState.Limited -> when (val limit = state.limit) {
                BudgetLimit.Unlimited ->
                    return Outcome.Failure(CarryEnableRefusal.UnlimitedThisMonth)

                is BudgetLimit.Finite -> limit.amount
            }
        }
        // Every later month takes its base from the default in force after this month.
        val after = (
            CategoryBudgetPolicy.inForce(month.plusMonths(1), own)?.budget
                as? AutomaticBudget.Limit
            )?.limit as? BudgetLimit.Finite
            ?: return Outcome.Failure(CarryEnableRefusal.DefaultAfterNotFinite)
        listOf(base, after.amount).firstOrNull { it.minorUnits > MAX_CHAIN_BASE }?.let {
            return Outcome.Failure(CarryEnableRefusal.BaseAboveLimit(it))
        }
        return Outcome.Success(chains + CarryChain(categoryId, month, end = null))
    }

    /**
     * Dropping the balance in [currentMonth] when carry is turned off, the category is
     * archived or its limit becomes unlimited (CP9–CP11). The chain ends in that month, so
     * its capacity is the base from then on; a chain that started then is removed. With
     * the carry-in not zero, [confirmedCarryIn] must be exactly it, otherwise nothing
     * changes and the person is shown the real [DropPreview].
     */
    fun drop(
        input: CategoryCarryInput,
        currentMonth: YearMonth,
        confirmedCarryIn: Money?
    ): Outcome<DropDecision, CarryoverFailure> {
        val chains = when (val checked = ordered(input)) {
            is Outcome.Success -> checked.value
            is Outcome.Failure -> return checked
        }
        val chain = chains.firstOrNull { it.covers(currentMonth) }
            ?: return Outcome.Success(DropDecision.NotCarrying)
        val now = when (val worked = run(input, chain, currentMonth, currentMonth)) {
            is Outcome.Success -> worked.value.last()
            is Outcome.Failure -> return worked
        }
        val ended = if (chain.start == currentMonth) {
            chains - chain
        } else {
            chains.map { if (it == chain) it.copy(end = currentMonth) else it }
        }
        if (now.carryIn.minorUnits == 0L || confirmedCarryIn == now.carryIn) {
            return Outcome.Success(DropDecision.Dropped(ended, now.carryIn))
        }
        val after = when (val difference = now.base - now.spent) {
            is Outcome.Success -> difference.value
            is Outcome.Failure -> return Outcome.Failure(difference.error.toCarryover())
        }
        return Outcome.Success(
            DropDecision.ConfirmationRequired(
                DropPreview(
                    month = currentMonth,
                    carryIn = now.carryIn,
                    base = now.base,
                    capacityBefore = now.capacity,
                    capacityAfter = now.base,
                    remainingBefore = now.remaining,
                    remainingAfter = after
                )
            )
        )
    }

    private val ZERO = Money.cop(0)

    /**
     * The months of one [chain] from its start up to [through] or its last month, never empty:
     * [through] is not before the start. Reads the history of those months only.
     */
    private fun run(
        input: CategoryCarryInput,
        chain: CarryChain,
        currentMonth: YearMonth,
        through: YearMonth
    ): Outcome<List<CarryMonth>, CarryoverFailure> {
        val last = chain.end?.minusMonths(1)?.coerceAtMost(through) ?: through
        val months = ArrayList<CarryMonth>()
        var carry = ZERO
        var month = chain.start
        while (month <= last) {
            val figures = when (
                val worked = figures(
                    input,
                    chain.start,
                    month,
                    carry,
                    currentMonth
                )
            ) {
                is Outcome.Success -> worked.value
                is Outcome.Failure -> return worked
            }
            months += figures
            carry = figures.remaining
            month = month.plusMonths(1)
        }
        return Outcome.Success(months)
    }

    /** The chains of [input] in month order, or why they are damaged. */
    private fun ordered(input: CategoryCarryInput): Outcome<List<CarryChain>, CarryoverFailure> {
        input.chains.firstOrNull { it.categoryId != input.categoryId }?.let {
            return Outcome.Failure(CarryoverFailure.InvalidChain(it.categoryId, it.start, it.end))
        }
        val sorted = input.chains.sortedBy { it.start }
        sorted.zipWithNext().firstOrNull { (earlier, later) ->
            earlier.end == null || later.start < earlier.end
        }?.let { (_, later) ->
            return Outcome.Failure(
                CarryoverFailure.OverlappingChains(input.categoryId, later.start)
            )
        }
        return Outcome.Success(sorted)
    }

    private fun figures(
        input: CategoryCarryInput,
        chainStart: YearMonth,
        month: YearMonth,
        carryIn: Money,
        currentMonth: YearMonth
    ): Outcome<CarryMonth, CarryoverFailure> {
        val base = when (val found = base(input, month)) {
            is Outcome.Success -> found.value
            is Outcome.Failure -> return found
        }
        val spent = when (val read = input.spending.spent(month)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        val capacity = when (val sum = base + carryIn) {
            is Outcome.Success -> sum.value
            is Outcome.Failure -> return Outcome.Failure(sum.error.toCarryover())
        }
        val remaining = when (
            val difference = (ExactTotal.of(capacity.currency, listOf(capacity)) - spent).toMoney()
        ) {
            is Outcome.Success -> difference.value
            is Outcome.Failure -> return Outcome.Failure(difference.error.toCarryover())
        }
        val verdict = if (month < currentMonth) {
            CarryVerdict.Closed(MonthlyBudgetPolicy.pastVerdict(capacity, spent))
        } else {
            CarryVerdict.InProgress(MonthlyBudgetPolicy.verdict(capacity, spent))
        }
        return Outcome.Success(
            CarryMonth(month, chainStart, base, carryIn, capacity, spent, remaining, verdict)
        )
    }

    /** [month]'s finite base as for any month, or the month that has none. */
    private fun base(
        input: CategoryCarryInput,
        month: YearMonth
    ): Outcome<Money, CarryoverFailure> {
        val state = CategoryBudgetPolicy.stateFor(
            month,
            input.defaults.filter { it.categoryId == input.categoryId },
            input.overrides.firstOrNull { it.categoryId == input.categoryId && it.month == month }
        )
        val limit = (state as? MonthBudgetState.Limited)?.limit as? BudgetLimit.Finite
            ?: return Outcome.Failure(CarryoverFailure.NoFiniteBase(month))
        return Outcome.Success(limit.amount)
    }
}
