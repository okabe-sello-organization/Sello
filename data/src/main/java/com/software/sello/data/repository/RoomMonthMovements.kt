package com.software.sello.data.repository

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.dao.MovementKind
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.DaySpendingPolicy
import com.software.sello.domain.port.MonthMovements
import com.software.sello.domain.port.Movement
import com.software.sello.domain.port.MovementFilter
import com.software.sello.domain.port.MovementPage
import com.software.sello.domain.port.MovementReadFailure
import java.time.YearMonth

/** A month's expenses and incomes as one list, each page and its day sums read together. */
class RoomMonthMovements(private val database: SelloDatabase) : MonthMovements {
    override suspend fun read(
        month: YearMonth,
        filter: MovementFilter,
        offset: Int,
        limit: Int
    ): Outcome<MovementPage, MovementReadFailure> {
        require(offset >= 0 && limit in 1..MonthMovements.MAX_PAGE)
        val read = reading {
            database.withTransaction { Outcome.Success(page(month, filter, offset, limit)) }
        }
        return when (read) {
            is Outcome.Failure -> Outcome.Failure(MovementReadFailure.Storage(read.error))
            is Outcome.Success -> read.value
        }
    }

    /** One page; a failure inside the transaction is said, never shown as an empty month. */
    private suspend fun page(
        month: YearMonth,
        filter: MovementFilter,
        offset: Int,
        limit: Int
    ): Outcome<MovementPage, MovementReadFailure> {
        val profile = when (val read = profileFrom(database.profileDao().rows())) {
            is Outcome.Failure -> return damaged(read.error)
            is Outcome.Success -> read.value
        }
        val dao = database.movementDao()
        val from = month.firstDayText
        val through = month.lastDayText
        val expenses = filter != MovementFilter.Incomes
        val incomes = filter != MovementFilter.Expenses
        val keys = dao.page(from, through, expenses, incomes, offset, limit + 1)
        val shown = keys.take(limit)
        val totalCount = dao.count(from, through, expenses, incomes)
        val amounts = when (
            val read = dao.expenseAmounts(from, through).decodeAll {
                it.toDomain()
            }
        ) {
            is Outcome.Failure -> return damaged(read.error)
            is Outcome.Success -> read.value
        }
        val days = when (val sums = DaySpendingPolicy.byDay(amounts)) {
            is Outcome.Failure -> return Outcome.Failure(MovementReadFailure.Unrepresentable)
            is Outcome.Success -> sums.value
        }
        val expenseIds = shown.filter { it.kind == MovementKind.EXPENSE }.map { it.id }
        val incomeIds = shown.filter { it.kind == MovementKind.INCOME }.map { it.id }
        val spent = when (
            val read = (if (expenseIds.isEmpty()) emptyList() else dao.expenses(expenseIds))
                .decodeAll { it.toDomain() }
        ) {
            is Outcome.Failure -> return damaged(read.error)
            is Outcome.Success -> read.value.associateBy { it.id.value }
        }
        val received = when (
            val read = (if (incomeIds.isEmpty()) emptyList() else dao.incomes(incomeIds))
                .decodeAll { it.toDomain() }
        ) {
            is Outcome.Failure -> return damaged(read.error)
            is Outcome.Success -> read.value.associateBy { it.id.value }
        }
        val categories = HashMap<CategoryId, Category>()
        for (expense in spent.values) {
            if (expense.categoryId in categories) continue
            // The foreign key keeps every expense's category; a missing one is damage.
            val row = database.categoryDao().byId(expense.categoryId.value)
                ?: return damaged(
                    StorageFailure.Integrity("expense", expense.id.value, "category_id")
                )
            when (val category = row.toDomain()) {
                is Outcome.Failure -> return damaged(category.error)
                is Outcome.Success -> categories[expense.categoryId] = category.value
            }
        }
        val movements = shown.map { key ->
            when (key.kind) {
                MovementKind.EXPENSE -> {
                    val expense = spent.getValue(key.id)
                    Movement.Spent(expense, categories.getValue(expense.categoryId))
                }

                else -> Movement.Received(received.getValue(key.id))
            }
        }
        return Outcome.Success(
            MovementPage(
                movements,
                keys.size > limit,
                totalCount,
                days,
                profile.generation,
                profile.revision
            )
        )
    }

    private fun damaged(failure: StorageFailure) =
        Outcome.Failure(MovementReadFailure.Storage(failure))
}
