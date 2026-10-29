package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.DropDecision
import com.software.sello.domain.model.MAX_CHAIN_BASE
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.CarryoverCorrectionReplay
import com.software.sello.domain.policy.CategoryCorrection
import com.software.sello.domain.policy.SignedCarryoverPolicy
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.ExpenseRejection
import java.time.LocalDate
import java.time.YearMonth

/*
 * The carry side of commands (ADR 0020). Each check runs inside the command's transaction,
 * before its first write, on rows read in that transaction, and asks the domain
 * calculation; nothing is calculated here. A refusal leaves everything as it was.
 */

/** What a carry check decided about one category command. */
internal sealed interface CarryDecision {
    /** Carry has nothing against the change and its chains stay as they are. */
    data object Unchanged : CarryDecision

    /** The change goes ahead and the category's chains become [chains]. */
    data class Chains(val chains: List<CarryChain>) : CarryDecision

    data class Refused(val reason: CategoryRejection) : CarryDecision
}

/**
 * The carry checks of one category command, for [categoryId] in the current financial
 * [month] on [today]. Its chains and rows are read once, on first use.
 */
internal class CategoryCarry(
    private val database: SelloDatabase,
    private val categoryId: CategoryId,
    private val month: YearMonth,
    private val today: LocalDate
) {
    private var loaded: Outcome<List<CarryChain>, CategoryRejection>? = null

    /** The category's chains, read once. */
    private suspend fun load(): Outcome<List<CarryChain>, CategoryRejection> =
        loaded ?: read().also { loaded = it }

    private suspend fun read(): Outcome<List<CarryChain>, CategoryRejection> {
        val chains = when (val stored = database.carryChains(categoryId.value)) {
            is Outcome.Success -> stored.value

            is Outcome.Failure -> return Outcome.Failure(
                CategoryRejection.StorageFailed(stored.error)
            )
        }
        // Only a clock moved back can leave a chain starting after this month (CP24): no
        // command may change carry until the month it starts in has come.
        if (chains.any { it.start > month }) {
            return Outcome.Failure(unavailable(CarryoverFailure.FutureMonth(month)))
        }
        return Outcome.Success(chains)
    }

    /**
     * The rows of [chains] from [from] through this month: only what the calculation asked
     * for reads (SELLO-038 consumer boundary, "history needed").
     */
    private suspend fun rows(
        chains: List<CarryChain>,
        from: YearMonth
    ): Outcome<CarryRows, CategoryRejection> =
        when (val stored = database.carryRows(categoryId, chains, from, month)) {
            is Outcome.Success -> stored
            is Outcome.Failure -> Outcome.Failure(CategoryRejection.StorageFailed(stored.error))
        }

    private fun unavailable(failure: CarryoverFailure) = CategoryRejection.CarryUnavailable(failure)

    private fun CarryRows.inputOrRefuse(
        defaults: List<DefaultLimit> = this.defaults,
        overrides: List<MonthLimit> = this.overrides
    ): Outcome<CategoryCarryInput, CategoryRejection> =
        when (val input = input(today, defaults = defaults, overrides = overrides)) {
            is Outcome.Success -> input
            is Outcome.Failure -> Outcome.Failure(unavailable(input.error))
        }

    /** Whether every month of [input]'s chains up to this one can still be calculated (CP8, CP23). */
    private fun replays(input: CategoryCarryInput): CategoryRejection? =
        when (val replayed = SignedCarryoverPolicy.replay(input, month, month)) {
            is Outcome.Success -> null
            is Outcome.Failure -> unavailable(replayed.error)
        }

    /**
     * Turning carry off, archiving, or making this month unlimited (CP9–CP11): the chain
     * covering this month ends in it, or is removed when it started in it. A carry-in that
     * is not zero must be exactly [confirmed]. [Unchanged] when no chain covers this month.
     */
    suspend fun drop(confirmed: Money?): CarryDecision {
        val chains = when (val read = load()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        // Only the covering chain is replayed; every chain is passed, as the drop returns them all.
        val covering = chains.covering(month) ?: return CarryDecision.Unchanged
        val rows = when (val read = rows(chains, covering.start)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        val input = when (val read = rows.inputOrRefuse()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        return when (val decided = SignedCarryoverPolicy.drop(input, month, confirmed)) {
            is Outcome.Failure -> CarryDecision.Refused(unavailable(decided.error))

            is Outcome.Success -> when (val decision = decided.value) {
                DropDecision.NotCarrying -> CarryDecision.Unchanged

                is DropDecision.ConfirmationRequired ->
                    CarryDecision.Refused(CategoryRejection.DropUnconfirmed(decision.preview))

                is DropDecision.Dropped -> CarryDecision.Chains(decision.chains)
            }
        }
    }

    /** Turning carry on in this month (CP2–CP4, CP25), for a category [archived] or not. */
    suspend fun enable(archived: Boolean): CarryDecision {
        val chains = when (val read = load()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        val defaults = when (val read = database.defaultHistory(categoryId.value)) {
            is Outcome.Success -> read.value

            is Outcome.Failure -> return CarryDecision.Refused(
                CategoryRejection.StorageFailed(read.error)
            )
        }
        val key = month.toString()
        val own = when (
            val read = database.limitDao().monthsOf(categoryId.value, key, key, "", 1)
                .decodeAll { it.toDomain() }
        ) {
            is Outcome.Success -> read.value

            is Outcome.Failure -> return CarryDecision.Refused(
                CategoryRejection.StorageFailed(read.error)
            )
        }
        return when (
            val decided = SignedCarryoverPolicy.enable(
                categoryId,
                archived,
                month,
                chains,
                defaults,
                own
            )
        ) {
            // The new chain starts now at 0 on a capped finite base, so its capacity fits;
            // the chains before it ended earlier and are not changed (CP4).
            is Outcome.Success -> CarryDecision.Chains(decided.value)

            is Outcome.Failure -> CarryDecision.Refused(
                CategoryRejection.CarryRefused(decided.error)
            )
        }
    }

    /**
     * A finite default from this month on (CP25, CP23): within the chain cap while a chain
     * is open, and every month must still calculate with it. This month's own limit gives
     * way to it, as the command does.
     */
    suspend fun finiteDefault(limit: BudgetLimit.Finite): CarryDecision {
        val chains = when (val read = load()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        // A default from this month on reaches only the chain open in it.
        val covering = chains.covering(month) ?: return CarryDecision.Unchanged
        if (limit.amount.minorUnits > MAX_CHAIN_BASE) {
            return CarryDecision.Refused(CategoryRejection.ChainBaseAboveLimit(limit.amount))
        }
        val rows = when (val read = rows(listOf(covering), covering.start)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        val default = DefaultLimit(categoryId, month, AutomaticBudget.Limit(limit))
        val input = when (
            val read = rows.inputOrRefuse(
                defaults = rows.defaults.filterNot { it.effectiveMonth == month } + default,
                overrides = rows.overrides.filterNot { it.month == month }
            )
        ) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        return replays(input)?.let { CarryDecision.Refused(it) } ?: CarryDecision.Unchanged
    }

    /**
     * [limit] as the own limit of [limitMonth], which is not this month made unlimited
     * (that is [drop]). A month outside every chain is no concern of carry. Inside one, a
     * past month cannot become unlimited (CP13) and no base may pass the cap (CP25). A
     * past month's finite correction that changes later months must have been confirmed
     * at the current [revision] (CP20–CP22); this month's needs no confirmation.
     */
    suspend fun monthLimit(
        limitMonth: YearMonth,
        limit: BudgetLimit,
        revision: Long,
        confirmedRevision: Long?
    ): CarryDecision {
        val chains = when (val read = load()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        // A month's base reaches only the chain it is in: earlier chains end before it and
        // later ones start again from 0 (CP4).
        val covering = chains.covering(limitMonth) ?: return CarryDecision.Unchanged
        val finite = when (limit) {
            BudgetLimit.Unlimited ->
                return CarryDecision.Refused(CategoryRejection.ChainMonthUnlimited(limitMonth))

            is BudgetLimit.Finite -> limit
        }
        if (finite.amount.minorUnits > MAX_CHAIN_BASE) {
            return CarryDecision.Refused(CategoryRejection.ChainBaseAboveLimit(finite.amount))
        }
        val rows = when (val read = rows(listOf(covering), covering.start)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        val before = when (val read = rows.inputOrRefuse()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        val after = when (
            val read = rows.inputOrRefuse(
                overrides = rows.overrides.filterNot { it.month == limitMonth } +
                    MonthLimit(categoryId, limitMonth, finite)
            )
        ) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return CarryDecision.Refused(read.error)
        }
        if (limitMonth == month) {
            return replays(after)?.let { CarryDecision.Refused(it) } ?: CarryDecision.Unchanged
        }
        val correction = when (
            val previewed = CarryoverCorrectionReplay.preview(
                month,
                listOf(CategoryCorrection(before, after))
            )
        ) {
            is Outcome.Success -> previewed.value
            is Outcome.Failure -> return CarryDecision.Refused(unavailable(previewed.error))
        }
        if (correction.isEmpty || confirmedRevision == revision) return CarryDecision.Unchanged
        return CarryDecision.Refused(CategoryRejection.CorrectionUnconfirmed(revision, correction))
    }

    /** Replaces the category's stored chains with [chains]. A write: call it after every check. */
    suspend fun write(chains: List<CarryChain>) {
        database.carryChainDao().deleteOfCategory(categoryId.value)
        database.carryChainDao().insertAll(chains.map { it.toEntity() })
    }
}

/**
 * Whether an expense change may commit (ADR 0020 CP20–CP23): [removed] is the stored row
 * that changes or goes away, [added] the row as it will be stored. A category with no
 * chain is not read further and neither row is decoded for it. A change that reaches a closed month of a chain and changes
 * later months must have been confirmed at the current [revision]; one that leaves any month
 * impossible to calculate is refused. Null when the change may go ahead.
 */
internal suspend fun SelloDatabase.expenseCarry(
    removed: ExpenseEntity?,
    added: ExpenseEntity?,
    month: YearMonth,
    today: LocalDate,
    revision: Long,
    confirmedRevision: Long?
): ExpenseRejection? {
    val categories = listOfNotNull(removed?.categoryId, added?.categoryId).distinct()
    val corrections = ArrayList<CategoryCorrection>(categories.size)
    for (category in categories) {
        val chains = when (val stored = carryChains(category)) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return ExpenseRejection.StorageFailed(stored.error)
        }
        if (chains.isEmpty()) continue
        if (chains.any { it.start > month }) {
            return ExpenseRejection.CarryUnavailable(CarryoverFailure.FutureMonth(month))
        }
        val id = chains.first().categoryId
        val (gone, put) = listOf(removed, added).map { candidate ->
            candidate?.takeIf { it.categoryId == category }?.let { row ->
                when (val decoded = row.toDomain()) {
                    is Outcome.Success -> decoded.value
                    is Outcome.Failure -> return ExpenseRejection.StorageFailed(decoded.error)
                }
            }
        }
        // Only the chains holding the months the change touches can change: from the
        // earliest of them through this month (SELLO-038 consumer boundary). A change in no
        // chain month, such as one before the first chain, reaches none (SC33).
        val touched = listOfNotNull(gone, put).map { YearMonth.from(it.date) }
        val reached = chains.filter { chain -> touched.any { chain.covers(it) } }
        val from = reached.minOfOrNull { it.start } ?: continue
        val rows = when (val stored = carryRows(id, reached, from, month)) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return ExpenseRejection.StorageFailed(stored.error)
        }
        val before = when (val input = rows.input(today)) {
            is Outcome.Success -> input.value
            is Outcome.Failure -> return ExpenseRejection.CarryUnavailable(input.error)
        }
        val expenses = rows.expenses.filterNot { it.id == gone?.id } + listOfNotNull(put)
        val after = when (val input = rows.input(today, expenses = expenses)) {
            is Outcome.Success -> input.value
            is Outcome.Failure -> return ExpenseRejection.CarryUnavailable(input.error)
        }
        corrections += CategoryCorrection(before, after)
    }
    if (corrections.isEmpty()) return null
    val correction = when (val previewed = CarryoverCorrectionReplay.preview(month, corrections)) {
        is Outcome.Success -> previewed.value
        is Outcome.Failure -> return ExpenseRejection.CarryUnavailable(previewed.error)
    }
    if (correction.isEmpty || confirmedRevision == revision) return null
    return ExpenseRejection.CorrectionUnconfirmed(revision, correction)
}
