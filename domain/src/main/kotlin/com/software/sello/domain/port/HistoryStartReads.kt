package com.software.sello.domain.port

import com.software.sello.domain.model.StorageFailure
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * When the history began, as stored now: the date of the earliest expense, and of the
 * earliest movement of any kind (expense, income, goal aporte or retiro, investment
 * movement), as ADR 0027 counts "using Sello". Deleted ones do not count. Each is null
 * while there is none.
 */
data class HistoryStart(val firstExpense: LocalDate?, val firstMovement: LocalDate?)

/** What a screen knows about [HistoryStart]. A failure is never "no history". */
typealias HistoryStartState = ReadState<HistoryStart, StorageFailure>

/** Where the history begins, for guidance that waits on the first movement (E38). */
interface HistoryStartReads {
    /** Starts with [ReadState.Loading], then a fresh state after every committed change. */
    fun observe(): Flow<HistoryStartState>
}
