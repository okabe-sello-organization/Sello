package com.software.sello.domain.port

import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/** Where a page of income ended, to ask for the next one. */
data class IncomeCursor(val date: LocalDate, val sequence: Long)

/**
 * Some of a month's income, newest first. [next] is null when these are the last. A
 * page is never the whole answer unless [next] is null, and no total is ever worked
 * out from a page.
 */
data class IncomePage(val incomes: List<Income>, val next: IncomeCursor?)

/**
 * What came in during [month]: the exact [total] of every income dated in it and how
 * many there are, all read at one [revision] of one [generation]. A month with no
 * income is a total of zero and a count of zero, not a failure.
 */
data class IncomeMonth(
    val month: YearMonth,
    val generation: Long,
    val revision: Long,
    val total: Money,
    val count: Int
)

/** What a screen knows about a month's income at any moment. A failure is never shown as zero. */
typealias IncomeMonthState = ReadState<IncomeMonth, StorageFailure>

interface IncomeReads {
    /** The stored income, exactly as recorded, or null when there is none with that identifier. */
    suspend fun byId(id: IncomeId): Outcome<Income?, StorageFailure>

    /**
     * Up to [limit] incomes dated in [month], newest day first and, within a day, the
     * one added last first, starting after [after]. [limit] is 1 to [MAX_PAGE].
     */
    suspend fun inMonth(
        month: YearMonth,
        after: IncomeCursor?,
        limit: Int
    ): Outcome<IncomePage, StorageFailure>

    /** One consistent read of [month]'s total and count. */
    suspend fun month(month: YearMonth): Outcome<IncomeMonth, StorageFailure>

    /** Starts with [ReadState.Loading], then a fresh state after every committed change. */
    fun observeMonth(month: YearMonth): Flow<IncomeMonthState>

    companion object {
        /** The most incomes one read may ask for. */
        const val MAX_PAGE = 200
    }
}
