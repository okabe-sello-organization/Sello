package com.software.sello.data.repository

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.local.entity.ScheduleEntity
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CategoryBudgetPolicy
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.CategoryRemovalPreviews
import com.software.sello.domain.port.Disposal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.RemovalPreview
import com.software.sello.domain.port.RemovalPreviewFailure
import com.software.sello.domain.port.TargetPreview
import java.time.LocalDate
import java.time.YearMonth

/**
 * What deleting a category would remove or move (ADR 0024), read in one transaction so
 * every figure belongs to one revision. The delete command reads the same figures with
 * [categoryContents] inside its own transaction and is refused when they changed.
 */
class RoomCategoryRemovals(private val database: SelloDatabase, private val clock: FinancialClock) :
    CategoryRemovalPreviews {
    override suspend fun preview(
        categoryId: CategoryId,
        disposal: Disposal
    ): Outcome<RemovalPreview, RemovalPreviewFailure> {
        val today = clock.today.value.date
        val read = reading<Outcome<CategoryContents, RemovalPreviewFailure>> {
            database.withTransaction {
                Outcome.Success(database.categoryContents(categoryId, disposal, today))
            }
        }
        return when (read) {
            is Outcome.Failure -> Outcome.Failure(RemovalPreviewFailure.Storage(read.error))

            is Outcome.Success -> when (val contents = read.value) {
                is Outcome.Success -> Outcome.Success(contents.value.preview)
                is Outcome.Failure -> contents
            }
        }
    }
}

/** A fixed payment as stored and how many pauses it has. */
internal class ScheduleRow(val row: ScheduleEntity, val pauses: Int)

/**
 * Everything a category holds, as stored, with the [preview] it makes. [target] is the
 * category that receives it, present exactly for [Disposal.MoveTo].
 */
internal class CategoryContents(
    val preview: RemovalPreview,
    val category: Category,
    val target: Category?,
    val expenses: List<ExpenseEntity>,
    val schedules: List<ScheduleRow>
)

/**
 * What [categoryId] holds and what deleting it with [disposal] would do on [today]. Must be
 * called inside a transaction. Every row is decoded, so a damaged one fails the preview
 * instead of being left out of it.
 */
internal suspend fun SelloDatabase.categoryContents(
    categoryId: CategoryId,
    disposal: Disposal,
    today: LocalDate
): Outcome<CategoryContents, RemovalPreviewFailure> {
    val category = when (val read = category(categoryId)) {
        null -> return Outcome.Failure(RemovalPreviewFailure.CategoryMissing)
        is Outcome.Success -> read.value
        is Outcome.Failure -> return Outcome.Failure(RemovalPreviewFailure.Storage(read.error))
    }
    val target = when (disposal) {
        Disposal.DeleteAll -> null

        is Disposal.MoveTo -> {
            if (disposal.target == categoryId) {
                return Outcome.Failure(RemovalPreviewFailure.SameCategory)
            }
            when (val read = category(disposal.target)) {
                null -> return Outcome.Failure(RemovalPreviewFailure.TargetMissing)

                is Outcome.Failure ->
                    return Outcome.Failure(RemovalPreviewFailure.Storage(read.error))

                is Outcome.Success -> read.value.also {
                    if (it.archived) return Outcome.Failure(RemovalPreviewFailure.TargetArchived)
                }
            }
        }
    }
    val month = YearMonth.from(today)
    val rows = pages<ExpenseEntity> { after ->
        expenseDao().allInCategory(
            categoryId.value,
            after?.effectiveDate.orEmpty(),
            after?.sequence ?: 0,
            PAGE
        )
    }
    val figures = when (val decoded = rows.decodeAll { it.toDomain() }) {
        is Outcome.Failure -> return Outcome.Failure(RemovalPreviewFailure.Storage(decoded.error))
        is Outcome.Success -> removalFigures(decoded.value, today)
    } ?: return Outcome.Failure(RemovalPreviewFailure.TotalOverflow)
    val schedules = scheduleDao().ofCategory(categoryId.value).map { row ->
        val pauses = scheduleDao().pauses(row.id)
        when (val stored = row.toStored(pauses)) {
            is Outcome.Success -> ScheduleRow(row, pauses.size)

            is Outcome.Failure -> return Outcome.Failure(
                RemovalPreviewFailure.Storage(stored.error)
            )
        }
    }
    val limit = when (val read = limitIn(categoryId, month)) {
        is Outcome.Success -> read.value
        is Outcome.Failure -> return Outcome.Failure(RemovalPreviewFailure.Storage(read.error))
    }
    val targetPreview = target?.let {
        when (val read = monthFigures(it.id, today)) {
            is MonthRead.Read -> TargetPreview(it.id, read.spent, read.limit)
            is MonthRead.Failed -> return Outcome.Failure(read.failure)
        }
    }
    val preview = RemovalPreview(
        categoryId = categoryId,
        expenses = figures.count,
        total = figures.total,
        firstMonth = figures.firstMonth,
        lastMonth = figures.lastMonth,
        schedules = schedules.size,
        month = month,
        spentThisMonth = figures.spentThisMonth,
        limitThisMonth = limit,
        target = targetPreview
    )
    return Outcome.Success(CategoryContents(preview, category, target, rows, schedules))
}

/** The category, decoded; null when there is none. */
private suspend fun SelloDatabase.category(
    id: CategoryId
): Outcome<Category, StorageFailure.Integrity>? = categoryDao().byId(id.value)?.toDomain()

private sealed interface MonthRead {
    data class Read(val spent: Money, val limit: BudgetLimit?) : MonthRead

    data class Failed(val failure: RemovalPreviewFailure) : MonthRead
}

/** What [categoryId] spent in [today]'s month by [today], and its own limit there. */
private suspend fun SelloDatabase.monthFigures(
    categoryId: CategoryId,
    today: LocalDate
): MonthRead {
    val month = YearMonth.from(today)
    val from = month.atDay(1).toString()
    val until = month.plusMonths(1).atDay(1).toString()
    val expenses = pages<ExpenseEntity> { after ->
        expenseDao().pageInCategory(
            categoryId.value,
            from,
            until,
            after?.effectiveDate.orEmpty(),
            after?.sequence ?: 0,
            PAGE
        )
    }.decodeAll { it.toDomain() }
    val figures = when (expenses) {
        is Outcome.Success -> removalFigures(expenses.value, today)
        is Outcome.Failure -> return MonthRead.Failed(RemovalPreviewFailure.Storage(expenses.error))
    } ?: return MonthRead.Failed(RemovalPreviewFailure.TotalOverflow)
    return when (val limit = limitIn(categoryId, month)) {
        is Outcome.Success -> MonthRead.Read(figures.spentThisMonth, limit.value)
        is Outcome.Failure -> MonthRead.Failed(RemovalPreviewFailure.Storage(limit.error))
    }
}

/**
 * The category's own limit in [month], as Recibo reads it: the month's own limit, else the
 * default in force then. Null when it is paused there or was never set by then.
 */
private suspend fun SelloDatabase.limitIn(
    categoryId: CategoryId,
    month: YearMonth
): Outcome<BudgetLimit?, StorageFailure.Integrity> {
    val defaults = when (val read = defaultHistory(categoryId.value)) {
        is Outcome.Success -> read.value
        is Outcome.Failure -> return read
    }
    val key = month.toString()
    val own = when (
        val read = limitDao().monthsOf(categoryId.value, key, key, "", 1)
            .decodeAll { it.toDomain() }
    ) {
        is Outcome.Success -> read.value.firstOrNull()
        is Outcome.Failure -> return read
    }
    return Outcome.Success(
        when (val state = CategoryBudgetPolicy.stateFor(month, defaults, own)) {
            is MonthBudgetState.Limited -> state.limit
            MonthBudgetState.Paused, MonthBudgetState.Unconfigured -> null
        }
    )
}

/** A category's expenses counted and summed, and the months of the oldest and newest. */
internal data class RemovalFigures(
    val count: Int,
    val total: Money,
    val firstMonth: YearMonth?,
    val lastMonth: YearMonth?,
    val spentThisMonth: Money
)

/**
 * The figures of [expenses] on [today]. The month's spending counts, as Recibo does, only
 * what is dated by [today]; the total counts every expense. Null when a sum does not fit in
 * `Money`: it is never clipped. Expenses are COP, as their mapper checks.
 */
internal fun removalFigures(expenses: List<Expense>, today: LocalDate): RemovalFigures? {
    val month = YearMonth.from(today)
    val total = ExactTotal.of(Currency.COP, expenses.map { it.amount.money }).toMoney()
    val spent = ExactTotal.of(
        Currency.COP,
        expenses.filter { YearMonth.from(it.date) == month && !it.date.isAfter(today) }
            .map { it.amount.money }
    ).toMoney()
    if (total !is Outcome.Success || spent !is Outcome.Success) return null
    return RemovalFigures(
        count = expenses.size,
        total = total.value,
        firstMonth = expenses.minOfOrNull { it.date }?.let(YearMonth::from),
        lastMonth = expenses.maxOfOrNull { it.date }?.let(YearMonth::from),
        spentThisMonth = spent.value
    )
}
