package com.software.sello.domain.port

import com.software.sello.domain.model.Category
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import java.time.LocalDate
import java.time.YearMonth

/** Which of a month's movements a list shows. */
enum class MovementFilter { All, Expenses, Incomes }

/** One expense or income of a month, as Día a día lists it. Goals and investments are not. */
sealed interface Movement {
    val date: LocalDate

    /** An expense with its [category], archived or not. */
    data class Spent(val expense: Expense, val category: Category) : Movement {
        override val date: LocalDate get() = expense.date
    }

    data class Received(val income: Income) : Movement {
        override val date: LocalDate get() = income.date
    }
}

/**
 * Some of a month's movements, newest day first and, within a day, the one added last
 * first, with whether [more] follow and how many there are in all ([totalCount]).
 * [days] is what was spent on every day of the month that has an expense, each sum over
 * all of that day's expenses, not only the ones on this page. Everything was read at one
 * [revision] of one [generation].
 */
data class MovementPage(
    val movements: List<Movement>,
    val more: Boolean,
    val totalCount: Long,
    val days: Map<LocalDate, Money>,
    val generation: Long,
    val revision: Long
)

/** Why a page of movements could not be read. Never shown as an empty month. */
sealed interface MovementReadFailure {
    data class Storage(val failure: StorageFailure) : MovementReadFailure

    /** A day's sum does not fit an exact amount. Reported, never clipped or wrapped. */
    data object Unrepresentable : MovementReadFailure
}

interface MonthMovements {
    /**
     * Up to [limit] of [month]'s movements that [filter] lets through, skipping the first
     * [offset] in list order. [limit] is 1 to [MAX_PAGE].
     */
    suspend fun read(
        month: YearMonth,
        filter: MovementFilter,
        offset: Int,
        limit: Int
    ): Outcome<MovementPage, MovementReadFailure>

    companion object {
        /** The most movements one read may ask for. */
        const val MAX_PAGE = 200
    }
}
