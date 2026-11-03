package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.ExpenseCursor
import com.software.sello.domain.port.ExpensePage
import com.software.sello.domain.port.ExpenseReads
import java.time.YearMonth

class RoomExpenseReads(private val database: SelloDatabase) : ExpenseReads {
    override suspend fun byId(id: ExpenseId): Outcome<Expense?, StorageFailure> = reading {
        when (val row = database.expenseDao().byId(id.value)) {
            null -> Outcome.Success(null)
            else -> row.toDomain()
        }
    }

    override suspend fun inCategory(
        categoryId: CategoryId,
        month: YearMonth,
        after: ExpenseCursor?,
        limit: Int
    ): Outcome<ExpensePage, StorageFailure> = reading {
        require(limit in 1..ExpenseReads.MAX_PAGE) {
            "a page holds 1 to ${ExpenseReads.MAX_PAGE} expenses"
        }
        // One more than asked for, only to learn whether another page exists.
        val rows = database.expenseDao().newestInCategory(
            categoryId = categoryId.value,
            from = month.firstDayText,
            through = month.lastDayText,
            beforeDate = after?.date?.let(EffectiveDates::storedText),
            beforeSequence = after?.sequence ?: Long.MAX_VALUE,
            limit = limit + 1
        )
        when (val expenses = rows.take(limit).decodeAll { it.toDomain() }) {
            is Outcome.Failure -> expenses

            is Outcome.Success -> {
                val last = expenses.value.lastOrNull()
                val next = last?.takeIf { rows.size > limit }
                    ?.let { ExpenseCursor(it.date, it.sequence) }
                Outcome.Success(ExpensePage(expenses.value, next))
            }
        }
    }

    /** Sequences only grow, so the highest one is the expense added last. */
    override suspend fun lastUsedCategory(): Outcome<CategoryId?, StorageFailure> = reading {
        when (val stored = database.expenseDao().lastAddedCategory()) {
            null -> Outcome.Success(null)

            else -> when (val id = CategoryId.of(stored)) {
                is Outcome.Success -> id

                is Outcome.Failure -> Outcome.Failure(
                    StorageFailure.Integrity(ExpenseEntity.TABLE, null, "category_id")
                )
            }
        }
    }
}
