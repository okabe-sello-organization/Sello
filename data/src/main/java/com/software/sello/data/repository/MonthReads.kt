package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.CategoryEntity
import com.software.sello.data.local.entity.DefaultLimitEntity
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.local.entity.IncomeEntity
import com.software.sello.data.local.entity.MonthLimitEntity
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CategoryBudgetPolicy
import com.software.sello.domain.port.CategoryBudget
import com.software.sello.domain.port.MonthBudget
import com.software.sello.domain.port.StoredSchedule
import java.time.YearMonth

/*
 * Reads of one month. Each must be called inside a transaction, so that everything
 * it returns, and anything read alongside it, belongs to the same revision. Rows are
 * fetched a page at a time and one damaged row fails the whole read.
 */

internal const val PAGE = 500

/** Reads every row in key order, [PAGE] at a time, continuing after the last row seen. */
internal suspend fun <E> pages(page: suspend (after: E?) -> List<E>): List<E> {
    val all = mutableListOf<E>()
    while (true) {
        val rows = page(all.lastOrNull())
        all += rows
        if (rows.size < PAGE) return all
    }
}

/** Every category with its budget state in [month], and the revision this was read at. */
internal suspend fun SelloDatabase.monthBudget(
    month: YearMonth
): Outcome<MonthBudget, StorageFailure> {
    val profile = when (val stored = profileFrom(profileDao().rows())) {
        is Outcome.Success -> stored.value
        is Outcome.Failure -> return stored
    }
    val key = month.toString()
    val categories = when (
        val rows = pages<CategoryEntity> { after -> categoryDao().page(after?.id.orEmpty(), PAGE) }
            .decodeAll { it.toDomain() }
    ) {
        is Outcome.Success -> rows.value
        is Outcome.Failure -> return rows
    }
    val defaults = when (
        val rows = pages<DefaultLimitEntity> { after ->
            limitDao().inForce(key, after?.categoryId.orEmpty(), PAGE)
        }
            .decodeAll { it.toDomain() }
    ) {
        is Outcome.Success -> rows.value.associateBy { it.categoryId }
        is Outcome.Failure -> return rows
    }
    val overrides = when (
        val rows = pages<MonthLimitEntity> { after ->
            limitDao().forMonth(key, after?.categoryId.orEmpty(), PAGE)
        }
            .decodeAll { it.toDomain() }
    ) {
        is Outcome.Success -> rows.value.associateBy { it.categoryId }
        is Outcome.Failure -> return rows
    }
    return Outcome.Success(
        MonthBudget(
            month = month,
            generation = profile.generation,
            revision = profile.revision,
            categories = categories.map { category ->
                val state = CategoryBudgetPolicy.stateFor(
                    month,
                    listOfNotNull(defaults[category.id]),
                    overrides[category.id]
                )
                CategoryBudget(category, state)
            }
        )
    )
}

/** Every expense dated in [month], in date then sequence order. */
internal suspend fun SelloDatabase.monthExpenses(
    month: YearMonth
): Outcome<List<Expense>, StorageFailure.Integrity> {
    val from = month.atDay(1).toString()
    val until = month.plusMonths(1).atDay(1).toString()
    return pages<ExpenseEntity> { after ->
        expenseDao().page(from, until, after?.effectiveDate.orEmpty(), after?.sequence ?: 0, PAGE)
    }.decodeAll { it.toDomain() }
}

/** Every income dated in [month], in date then sequence order. */
internal suspend fun SelloDatabase.monthIncomes(
    month: YearMonth
): Outcome<List<Income>, StorageFailure.Integrity> {
    val from = month.atDay(1).toString()
    val until = month.plusMonths(1).atDay(1).toString()
    return pages<IncomeEntity> { after ->
        incomeDao().page(from, until, after?.effectiveDate.orEmpty(), after?.sequence ?: 0, PAGE)
    }.decodeAll { it.toDomain() }
}

/**
 * Every schedule with its pauses, in creation order. One that cannot be read fails the
 * read: it is never left out.
 */
internal suspend fun SelloDatabase.storedSchedules():
    Outcome<List<StoredSchedule>, StorageFailure.Integrity> {
    val pauses = scheduleDao().allPauses().groupBy { it.scheduleId }
    // A pause cannot outlive its schedule: the foreign key refuses it.
    val schedules = mutableListOf<StoredSchedule>()
    for (row in scheduleDao().all()) {
        when (val stored = row.toStored(pauses[row.id].orEmpty())) {
            is Outcome.Success -> schedules += stored.value
            is Outcome.Failure -> return stored
        }
    }
    return Outcome.Success(schedules)
}
