package com.software.sello.data.repository

import androidx.room.withTransaction
import com.software.sello.data.local.SearchKey
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CategoryExpenseSearch
import com.software.sello.domain.port.ExpenseOrder
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.ExpenseSearchPage
import java.time.YearMonth

class RoomCategoryExpenseSearch(private val database: SelloDatabase) : CategoryExpenseSearch {
    override suspend fun read(
        categoryId: CategoryId,
        month: YearMonth,
        order: ExpenseOrder,
        query: String,
        offset: Int,
        limit: Int
    ): Outcome<ExpenseSearchPage, StorageFailure> = reading {
        require(query.length <= 60 && offset >= 0 && limit in 1..ExpenseReads.MAX_PAGE)
        database.withTransaction {
            val profile = when (val read = profileFrom(database.profileDao().rows())) {
                is Outcome.Failure -> return@withTransaction read
                is Outcome.Success -> read.value
            }
            val sort = when (order) {
                ExpenseOrder.Date -> "date"
                ExpenseOrder.Amount -> "amount"
                ExpenseOrder.Name -> "name"
            }
            // Folded as every note is, so case and accents never keep an expense out.
            val key = SearchKey.of(query)
            val rows = database.expenseDao().searchInCategory(
                categoryId.value,
                month.firstDayText,
                month.lastDayText,
                sort,
                key,
                offset,
                limit + 1
            )
            val totalCount = database.expenseDao().countSearchInCategory(
                categoryId.value,
                month.firstDayText,
                month.lastDayText,
                key
            )
            when (val read = rows.take(limit).decodeAll { it.toDomain() }) {
                is Outcome.Failure -> read

                is Outcome.Success -> Outcome.Success(
                    ExpenseSearchPage(
                        read.value,
                        rows.size > limit,
                        profile.generation,
                        profile.revision,
                        totalCount
                    )
                )
            }
        }
    }
}
