package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.CarryChainEntity
import com.software.sello.data.local.entity.DefaultLimitEntity
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.local.entity.MonthLimitEntity
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.SpendingHistory
import com.software.sello.domain.model.StorageFailure
import java.time.LocalDate
import java.time.YearMonth

/*
 * Signed carryover is derived on every read (ADR 0020 CP7): nothing below stores or caches
 * a carry figure. These read, inside the caller's transaction, the rows the domain
 * calculation needs for one category, so everything it is given belongs to one revision.
 */

/** One category's whole default history. It grows by at most one entry per month. */
internal suspend fun SelloDatabase.defaultHistory(
    categoryId: String
): Outcome<List<DefaultLimit>, StorageFailure.Integrity> {
    val rows = limitDao().defaults(categoryId, MAX_DEFAULT_HISTORY + 1)
    if (rows.size > MAX_DEFAULT_HISTORY) {
        return Outcome.Failure(
            StorageFailure.Integrity(DefaultLimitEntity.TABLE, categoryId, "effective_month")
        )
    }
    return rows.decodeAll { it.toDomain() }
}

/** A category's chains, oldest first. At most one starts in a month, as for defaults. */
internal suspend fun SelloDatabase.carryChains(
    categoryId: String
): Outcome<List<CarryChain>, StorageFailure.Integrity> {
    val rows = carryChainDao().ofCategory(categoryId, MAX_DEFAULT_HISTORY + 1)
    if (rows.size > MAX_DEFAULT_HISTORY) {
        return Outcome.Failure(
            StorageFailure.Integrity(CarryChainEntity.TABLE, categoryId, "start_month")
        )
    }
    return rows.decodeAll { it.toDomain() }
}

/** Every category's chains. A category with none is absent. */
internal suspend fun SelloDatabase.everyCarryChain():
    Outcome<Map<CategoryId, List<CarryChain>>, StorageFailure.Integrity> =
    when (
        val chains = pages<CarryChainEntity> { after ->
            carryChainDao().all(after?.categoryId.orEmpty(), after?.startMonth.orEmpty(), PAGE)
        }.decodeAll { it.toDomain() }
    ) {
        is Outcome.Success -> Outcome.Success(chains.value.groupBy { it.categoryId })
        is Outcome.Failure -> chains
    }

/**
 * What the calculation reads about one category, as stored: its [chains], its whole default
 * history, its own month limits and its expenses dated from [from], the start of its first
 * chain, to [through]. [input] turns them into the domain's input at a financial day.
 */
internal class CarryRows(
    val categoryId: CategoryId,
    val chains: List<CarryChain>,
    val defaults: List<DefaultLimit>,
    val overrides: List<MonthLimit>,
    val from: YearMonth,
    val through: YearMonth,
    val expenses: List<Expense>
) {
    /** The input as stored, or with any part replaced by what a change would make it. */
    fun input(
        asOf: LocalDate,
        chains: List<CarryChain> = this.chains,
        defaults: List<DefaultLimit> = this.defaults,
        overrides: List<MonthLimit> = this.overrides,
        expenses: List<Expense> = this.expenses
    ): Outcome<CategoryCarryInput, CarryoverFailure> =
        when (val spending = SpendingHistory.of(categoryId, from, through, asOf, expenses)) {
            is Outcome.Success -> Outcome.Success(
                CategoryCarryInput(categoryId, chains, defaults, overrides, spending.value)
            )

            is Outcome.Failure -> spending
        }
}

/**
 * [input] for a snapshot: a figure that does not fit is the snapshot's unrepresentable
 * failure; any other names this category, so the screens can point at the repair (CP8).
 */
internal fun CarryRows.snapshotInput(
    asOf: LocalDate
): Outcome<CategoryCarryInput, SnapshotFailure> = when (val read = input(asOf)) {
    is Outcome.Success -> read

    is Outcome.Failure -> Outcome.Failure(
        if (read.error == CarryoverFailure.Unrepresentable) {
            SnapshotFailure.Unrepresentable
        } else {
            SnapshotFailure.Carryover(read.error, categoryId)
        }
    )
}

/** The chain of these that carries into [month], or null when none does. */
internal fun List<CarryChain>.covering(month: YearMonth): CarryChain? =
    firstOrNull { it.covers(month) }

/**
 * The rows a carry-in of [month] needs (a snapshot): only the chain that covers it, from its
 * start through the month before (SELLO-038 consumer boundary, "history needed"). Earlier
 * chains start again from 0, so they are not read. Null when no chain covers [month].
 */
internal suspend fun SelloDatabase.carryInRows(
    categoryId: CategoryId,
    chains: List<CarryChain>,
    month: YearMonth
): Outcome<CarryRows?, StorageFailure.Integrity> {
    val chain = chains.covering(month) ?: return Outcome.Success(null)
    // A history covers at least one month: in its start month a chain reads that month alone.
    val through = maxOf(chain.start, month.minusMonths(1))
    return carryRows(categoryId, chains, chain.start, through)
}

/**
 * The rows [chains] of [categoryId] need from [from] through [through]: the whole default
 * history, the own month limits and the expenses of that range. The caller picks the range
 * the calculation it asks for needs, no wider. Must run inside a transaction.
 */
internal suspend fun SelloDatabase.carryRows(
    categoryId: CategoryId,
    chains: List<CarryChain>,
    from: YearMonth,
    through: YearMonth
): Outcome<CarryRows, StorageFailure.Integrity> {
    val defaults = when (val read = defaultHistory(categoryId.value)) {
        is Outcome.Success -> read.value
        is Outcome.Failure -> return read
    }
    val overrides = when (
        val read = pages<MonthLimitEntity> { after ->
            limitDao().monthsOf(
                categoryId.value,
                from.toString(),
                through.toString(),
                after?.month.orEmpty(),
                PAGE
            )
        }.decodeAll { it.toDomain() }
    ) {
        is Outcome.Success -> read.value
        is Outcome.Failure -> return read
    }
    val start = from.atDay(1).toString()
    val until = through.plusMonths(1).atDay(1).toString()
    val expenses = when (
        val read = pages<ExpenseEntity> { after ->
            expenseDao().pageInCategory(
                categoryId.value,
                start,
                until,
                after?.effectiveDate.orEmpty(),
                after?.sequence ?: 0,
                PAGE
            )
        }.decodeAll { it.toDomain() }
    ) {
        is Outcome.Success -> read.value
        is Outcome.Failure -> return read
    }
    return Outcome.Success(
        CarryRows(categoryId, chains, defaults, overrides, from, through, expenses)
    )
}
