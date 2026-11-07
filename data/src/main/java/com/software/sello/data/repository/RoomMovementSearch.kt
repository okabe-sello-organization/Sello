package com.software.sello.data.repository

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.dao.MovementDao
import com.software.sello.data.local.dao.MovementKind
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.SearchKey
import com.software.sello.domain.policy.SearchTotalsPolicy
import com.software.sello.domain.port.Movement
import com.software.sello.domain.port.MovementFilter
import com.software.sello.domain.port.MovementQuery
import com.software.sello.domain.port.MovementReadFailure
import com.software.sello.domain.port.MovementSearch
import com.software.sello.domain.port.SearchPage

/**
 * Día a día's search over every month (SELLO-193), each page and the totals of every match
 * read together. Expenses are matched in SQL by their stored folded note, their category's
 * name key and their amount. Incomes have no stored search key, so they are read and
 * folded here by the same rule ([IncomeMatch]); that is why pages follow the last movement
 * listed rather than an offset.
 */
class RoomMovementSearch(private val database: SelloDatabase) : MovementSearch {
    override suspend fun read(
        query: MovementQuery,
        filter: MovementFilter,
        sourceNames: Map<String, String>,
        after: Movement?,
        limit: Int
    ): Outcome<SearchPage, MovementReadFailure> {
        require(limit in 1..MovementSearch.MAX_PAGE)
        val read = reading {
            database.withTransaction {
                Outcome.Success(search(query, filter, sourceNames, after, limit))
            }
        }
        return when (read) {
            is Outcome.Failure -> Outcome.Failure(MovementReadFailure.Storage(read.error))
            is Outcome.Success -> read.value
        }
    }

    private suspend fun search(
        query: MovementQuery,
        filter: MovementFilter,
        sourceNames: Map<String, String>,
        after: Movement?,
        limit: Int
    ): Outcome<SearchPage, MovementReadFailure> {
        val profile = when (val read = profileFrom(database.profileDao().rows())) {
            is Outcome.Failure -> return damaged(read.error)
            is Outcome.Success -> read.value
        }
        val dao = database.movementDao()
        val key = SearchKey.of(query.words)
        val nameKey = CategoryName.keyOf(query.words)
        // Text that folds to nothing, such as accent marks alone, is in every text: it finds
        // nothing instead of everything.
        val match = ExpenseMatch(
            key = key,
            nameKey = nameKey,
            byAmount = query.amount != null,
            amount = query.amount?.money?.minorUnits ?: 0L,
            currency = query.amount?.money?.currency?.code ?: Currency.COP.code
        )
        val expenses = filter != MovementFilter.Incomes
        val incomes = filter != MovementFilter.Expenses

        // Every income is read and checked: a damaged one fails the search, never hides.
        val found = if (incomes) {
            when (val read = dao.allIncomes().decodeAll { it.toDomain() }) {
                is Outcome.Failure -> return damaged(read.error)

                is Outcome.Success -> read.value.filter {
                    IncomeMatch.finds(it, key, query, sourceNames)
                }.map(Movement::Received).sortedWith(Movement.listOrder)
            }
        } else {
            emptyList()
        }
        val amounts = if (expenses) {
            when (val read = match.amounts(dao).decodeAll { it.toDomain() }) {
                is Outcome.Failure -> return damaged(read.error)
                is Outcome.Success -> read.value
            }
        } else {
            emptyList()
        }
        val totals = when (
            val sums = SearchTotalsPolicy.of(
                amounts,
                found.map {
                    it.date to it.income.amount.money
                }
            )
        ) {
            is Outcome.Failure -> return Outcome.Failure(
                when (sums.error) {
                    MoneyError.Overflow -> MovementReadFailure.Unrepresentable
                    is MoneyError.CurrencyMismatch -> MovementReadFailure.CurrencyMismatch
                }
            )

            is Outcome.Success -> sums.value
        }

        val spent = if (expenses) {
            when (val read = spentAfter(match, after, limit + 1)) {
                is Outcome.Failure -> return damaged(read.error)
                is Outcome.Success -> read.value
            }
        } else {
            emptyList()
        }
        val received = found.filter { after == null || Movement.listOrder.compare(it, after) > 0 }
            .take(limit + 1)
        val merged = (spent + received).sortedWith(Movement.listOrder)
        return Outcome.Success(
            SearchPage(
                merged.take(limit),
                merged.size > limit,
                totals,
                profile.generation,
                profile.revision
            )
        )
    }

    /** Up to [count] matching expenses listed after [after], each with its category. */
    private suspend fun spentAfter(
        match: ExpenseMatch,
        after: Movement?,
        count: Int
    ): Outcome<List<Movement.Spent>, StorageFailure> {
        val dao = database.movementDao()
        val keys = dao.searchExpenses(
            byWords = match.key.isNotEmpty(),
            key = match.key,
            byName = match.nameKey.isNotEmpty(),
            nameKey = match.nameKey,
            byAmount = match.byAmount,
            amount = match.amount,
            currency = match.currency,
            hasCursor = after != null,
            date = after?.let { EffectiveDates.storedText(it.date) }.orEmpty(),
            createdAt = after?.createdAtMillis ?: 0L,
            cursorKind = if (after is Movement.Received) {
                MovementKind.INCOME
            } else {
                MovementKind.EXPENSE
            },
            sequence = after?.sequenceNumber ?: 0L,
            limit = count
        )
        if (keys.isEmpty()) return Outcome.Success(emptyList())
        val rows = when (val read = dao.expenses(keys.map { it.id }).decodeAll { it.toDomain() }) {
            is Outcome.Failure -> return read
            is Outcome.Success -> read.value.associateBy { it.id.value }
        }
        val categories = HashMap<CategoryId, Category>()
        val listed = ArrayList<Movement.Spent>(keys.size)
        for (key in keys) {
            // Read in this transaction: a row missing now would be damage, never skipped.
            val expense = rows[key.id]
                ?: return Outcome.Failure(StorageFailure.Integrity("expense", key.id, "id"))
            val category = categories[expense.categoryId] ?: run {
                // The foreign key keeps every expense's category; a missing one is damage.
                val row = database.categoryDao().byId(expense.categoryId.value)
                    ?: return Outcome.Failure(
                        StorageFailure.Integrity("expense", expense.id.value, "category_id")
                    )
                when (val read = row.toDomain()) {
                    is Outcome.Failure -> return read
                    is Outcome.Success -> read.value.also { categories[expense.categoryId] = it }
                }
            }
            listed += Movement.Spent(expense, category)
        }
        return Outcome.Success(listed)
    }

    private fun damaged(failure: StorageFailure) =
        Outcome.Failure(MovementReadFailure.Storage(failure))

    /** What an expense must hold to be found, as [MovementDao.searchExpenses] reads it. */
    private class ExpenseMatch(
        val key: String,
        val nameKey: String,
        val byAmount: Boolean,
        val amount: Long,
        val currency: String
    ) {
        suspend fun amounts(dao: MovementDao) = dao.searchedAmounts(
            byWords = key.isNotEmpty(),
            key = key,
            byName = nameKey.isNotEmpty(),
            nameKey = nameKey,
            byAmount = byAmount,
            amount = amount,
            currency = currency
        )
    }
}

/**
 * Whether Día a día's search finds an income: its folded note or source name holds [key]
 * (the query folded by [SearchKey]; an empty key holds no word), or it is exactly the
 * query's amount. A fixed source
 * is found by the name the screen gives it in [sourceNames]; "Otro" by its own name.
 */
internal object IncomeMatch {
    fun finds(
        income: Income,
        key: String,
        query: MovementQuery,
        sourceNames: Map<String, String>
    ): Boolean {
        val source = when (val source = income.source) {
            is IncomeSource.Other -> source.name.value
            else -> sourceNames[source.key]
        }
        val byWords = key.isNotEmpty() &&
            (SearchKey.of(income.note?.value).contains(key) || SearchKey.of(source).contains(key))
        return byWords || income.amount == query.amount
    }
}

private val Movement.createdAtMillis: Long
    get() = when (this) {
        is Movement.Spent -> expense.createdAt.toEpochMilli()
        is Movement.Received -> income.createdAt.toEpochMilli()
    }

private val Movement.sequenceNumber: Long
    get() = when (this) {
        is Movement.Spent -> expense.sequence
        is Movement.Received -> income.sequence
    }
