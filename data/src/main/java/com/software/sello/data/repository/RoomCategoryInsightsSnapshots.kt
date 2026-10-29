package com.software.sello.data.repository

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CategoryHistoryInput
import com.software.sello.domain.policy.CategoryInMonth
import com.software.sello.domain.policy.CategoryInsightsPolicy
import com.software.sello.domain.port.CategoryInsightsSnapshots
import com.software.sello.domain.port.CategoryInsightsState
import com.software.sello.domain.port.ExpenseCursor
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import java.time.YearMonth
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** Read-only, paged inputs in one Room transaction; financial arithmetic stays in domain. */
class RoomCategoryInsightsSnapshots(
    private val database: SelloDatabase,
    private val clock: FinancialClock,
    private val computation: CoroutineContext
) : CategoryInsightsSnapshots {
    private data class Rows(
        val generation: Long,
        val revision: Long,
        val months: List<CategoryHistoryInput>
    )

    override suspend fun read(
        categoryId: CategoryId,
        month: YearMonth
    ): Outcome<CategoryInsights, SnapshotFailure> = readAsOf(categoryId, month, clock.today.value)

    private suspend fun readAsOf(
        id: CategoryId,
        month: YearMonth,
        today: FinancialDay
    ): Outcome<CategoryInsights, SnapshotFailure> {
        if (month > YearMonth.from(today.date)) {
            return Outcome.Failure(SnapshotFailure.FutureMonth(YearMonth.from(today.date)))
        }
        val rows = when (val read = reading { database.withTransaction { rows(id, month) } }) {
            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Storage(read.error))
            is Outcome.Success -> read.value
        }
        return withContext(computation) {
            CategoryInsightsPolicy.snapshot(
                id,
                month,
                today,
                rows.generation,
                rows.revision,
                rows.months
            )
        }
    }

    private suspend fun rows(id: CategoryId, month: YearMonth): Outcome<Rows, StorageFailure> {
        val months = mutableListOf<CategoryHistoryInput>()
        var generation = 0L
        var revision = 0L
        val reads = RoomExpenseReads(database)
        for (period in CategoryInsightsPolicy.monthsEndingIn(month)) {
            val budget = when (val read = database.monthBudget(period)) {
                is Outcome.Failure -> return read
                is Outcome.Success -> read.value
            }
            generation = budget.generation
            revision = budget.revision
            val expenses = mutableListOf<Expense>()
            var cursor: ExpenseCursor? = null
            do {
                val page = when (
                    val read = reads.inCategory(
                        id,
                        period,
                        cursor,
                        ExpenseReads.MAX_PAGE
                    )
                ) {
                    is Outcome.Failure -> return read
                    is Outcome.Success -> read.value
                }
                expenses += page.expenses
                cursor = page.next
            } while (cursor != null)
            val category = budget.categories.firstOrNull { it.category.id == id }
                ?.let { CategoryInMonth(it.category, it.state) }
            months += CategoryHistoryInput(period, category, expenses)
        }
        return Outcome.Success(Rows(generation, revision, months))
    }

    override fun observe(categoryId: CategoryId, month: YearMonth): Flow<CategoryInsightsState> =
        observeOn(
            combine(clock.today, database.profileDao().changes()) { today, _ -> today },
            SnapshotFailure::Storage
        ) { today -> readAsOf(categoryId, month, today) }
}
