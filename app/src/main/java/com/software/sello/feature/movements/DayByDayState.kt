package com.software.sello.feature.movements

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.port.MovementFilter
import java.time.LocalDate
import java.time.YearMonth

/** One expense or income in Día a día's list. */
@Immutable
sealed interface DayRow {
    val id: String
    val note: String?
    val amount: MoneyTextValue

    /** [fixed] when a fixed payment recorded it; [archived] when its category is archived. */
    data class Spent(
        override val id: String,
        override val note: String?,
        override val amount: MoneyTextValue,
        val categoryName: String,
        val iconKey: String,
        val fixed: Boolean,
        val archived: Boolean
    ) : DayRow

    /** [amount] carries its plus; the source is said as the income form says it. */
    data class Received(
        override val id: String,
        override val note: String?,
        override val amount: MoneyTextValue,
        val sourceKey: String,
        val sourceName: String?
    ) : DayRow
}

/** A day of the list: its rows, newest first, and what was spent that day, if anything. */
@Immutable
data class DayGroup(val date: LocalDate, val spent: MoneyTextValue?, val rows: List<DayRow>)

/** Why nothing could be shown. */
enum class DayByDayProblem {
    /** The month or a page of it could not be read. */
    Unread,

    /** A day adds up to more than an exact amount can hold. Never wrapped or clipped. */
    TooLarge,

    /** A stored expense is damaged, such as a day mixing currencies: never added up. */
    Damaged
}

sealed interface DayByDayState {
    data object Loading : DayByDayState

    /** Nothing was ever shown for this month and chip; Reintentar reads again. */
    data class Failed(val problem: DayByDayProblem) : DayByDayState

    /**
     * [spent] and [income] are Recibo's and Resumen's figures for [month]; [days] what is
     * listed so far, out of [totalCount] that [filter] lets through.
     */
    @Immutable
    data class Ready(
        val month: YearMonth,
        val today: LocalDate,
        val filter: MovementFilter,
        val spent: MoneyTextValue,
        val income: MoneyTextValue,
        val days: List<DayGroup>,
        val totalCount: Long,
        val more: Boolean,
        /** The next page, or a fresh read of these rows, is being read. */
        val busy: Boolean = false,
        /**
         * Why a page could not be read: what is listed stays. Unread offers Reintentar; a day
         * too large to add up exactly is said as such, since reading again cannot fix it.
         */
        val pageFailure: DayByDayProblem? = null,
        /** The month could not be read again: these are the rows as last read. */
        val stale: Boolean = false
    ) : DayByDayState {
        val listed: Int get() = days.sumOf { it.rows.size }

        val pageFailed: Boolean get() = pageFailure != null
    }
}

/** How many movements a search found and what they add up to. */
@Immutable
data class FoundTotal(val count: Long, val total: MoneyTextValue)

/** One movement a search found, with its day, which its caption says. */
@Immutable
data class FoundRow(val date: LocalDate, val row: DayRow)

/**
 * A month of a search's results: its rows listed so far, how many the search found in that
 * month in all and what its matching expenses add up to ([spent], null without one).
 */
@Immutable
data class FoundMonth(
    val month: YearMonth,
    val count: Long,
    val spent: MoneyTextValue?,
    val rows: List<FoundRow>
)

/** Día a día's search over every month (SELLO-193). [words] is what was searched. */
sealed interface SearchState {
    /** Nothing is searched: the month's day list shows. */
    data object Off : SearchState

    data class Loading(val words: String, val filter: MovementFilter) : SearchState

    /** Nothing was ever shown for this search and chip; the query stays in the field. */
    data class Failed(val words: String, val filter: MovementFilter, val problem: DayByDayProblem) :
        SearchState

    /**
     * [months] is what is listed so far, newest first. [amount] is the query read as an
     * amount, when it is one. [expenses] and [incomes] are every match's count and sum,
     * each null when none matched or the chip leaves them out.
     */
    @Immutable
    data class Ready(
        val words: String,
        val amount: MoneyTextValue?,
        val today: LocalDate,
        val filter: MovementFilter,
        val expenses: FoundTotal?,
        val incomes: FoundTotal?,
        val months: List<FoundMonth>,
        val more: Boolean,
        val busy: Boolean = false,
        val pageFailure: DayByDayProblem? = null,
        /** The month could not be read again: these are the results as last read. */
        val stale: Boolean = false
    ) : SearchState {
        val listed: Int get() = months.sumOf { it.rows.size }
    }
}

sealed interface DayByDayAction {
    data class Filter(val filter: MovementFilter) : DayByDayAction

    /** List the next older movements. */
    data object More : DayByDayAction

    data object Retry : DayByDayAction

    /** What is typed in the search field; empty goes back to the month's days. */
    data class Search(val text: String) : DayByDayAction

    /** List the next older results of the search. */
    data object MoreFound : DayByDayAction
}
