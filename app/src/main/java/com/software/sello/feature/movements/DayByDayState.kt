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
    TooLarge
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
        /** A page could not be read: what is listed stays, and Reintentar reads it again. */
        val pageFailed: Boolean = false,
        /** The month could not be read again: these are the rows as last read. */
        val stale: Boolean = false
    ) : DayByDayState {
        val listed: Int get() = days.sumOf { it.rows.size }
    }
}

sealed interface DayByDayAction {
    data class Filter(val filter: MovementFilter) : DayByDayAction

    /** List the next older movements. */
    data object More : DayByDayAction

    data object Retry : DayByDayAction
}
